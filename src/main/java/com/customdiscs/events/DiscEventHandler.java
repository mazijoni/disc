package com.customdiscs.events;

import com.customdiscs.DiscMod;
import com.customdiscs.item.CustomDiscItem;
import com.customdiscs.network.DiscPlayPacket;
import com.customdiscs.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.customdiscs.voicechat.VoicechatCompat;
import net.minecraftforge.network.PacketDistributor;

public class DiscEventHandler {

    /**
     * CLIENT — suppresses the dummy MUSIC_DISC_13 that vanilla plays when our
     * disc is inserted. The real sound is delivered by the server via DiscPlayPacket.
     *
     * IMPORTANT: We must ONLY suppress MUSIC_DISC_13, not every RECORDS-source sound.
     * When DiscPlayPacket plays the real custom sound it also triggers PlaySoundEvent
     * (source=RECORDS). If we suppress all RECORDS sounds near a jukebox we would
     * kill our own custom audio right after starting it.
     */
    @Mod.EventBusSubscriber(value = Dist.CLIENT)
    public static class ClientHandler {

        private static final net.minecraft.resources.ResourceLocation DISC_13_RL =
                new net.minecraft.resources.ResourceLocation("minecraft", "music_disc.13");

        @SubscribeEvent
        @OnlyIn(Dist.CLIENT)
        public static void onPlaySound(PlaySoundEvent event) {
            SoundInstance incoming = event.getSound();
            if (incoming == null || incoming.getSource() != SoundSource.RECORDS) return;

            // Only intercept the dummy MUSIC_DISC_13 placeholder.
            // Custom sounds played by DiscPlayPacket must NOT be suppressed here.
            if (!DISC_13_RL.equals(incoming.getLocation())) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            double sx = incoming.getX(), sy = incoming.getY(), sz = incoming.getZ();
            BlockPos pos = BlockPos.containing(sx, sy, sz);

            // Only suppress if the jukebox actually holds our disc
            // (avoids muting disc 13 playing from a real vanilla jukebox nearby).
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (!(be instanceof JukeboxBlockEntity jukebox)) return;

            ItemStack disc = jukebox.getItem(0);
            if (!disc.isEmpty() && disc.getItem() instanceof CustomDiscItem) {
                event.setSound(null); // suppress dummy; DiscPlayPacket will play the real sound
            }
        }
    }

    /**
     * SERVER — listens for jukebox level events and eject interactions.
     *
     * Level event 1010 fires when a jukebox STARTS playing (data = Item ID of disc).
     * Level event 1011 fires when a jukebox STOPS / ejects.
     * We use these to send DiscPlayPacket(play) and DiscPlayPacket(stop) to clients.
     */
    @Mod.EventBusSubscriber
    public static class ServerHandler {

        /**
         * Fires on the SERVER when any player right-clicks a block.
         * We use this to detect BOTH:
         *  - Disc insertion: jukebox is empty and held item is our disc → send play packet.
         *  - Disc ejection: jukebox has our disc and is being clicked → send stop packet.
         */
        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (event.getLevel().isClientSide()) return;

            BlockPos pos = event.getHitVec().getBlockPos();
            BlockEntity be = event.getLevel().getBlockEntity(pos);
            if (!(be instanceof JukeboxBlockEntity jukebox)) return;

            ItemStack currentDisc = jukebox.getItem(0);
            ItemStack heldItem    = event.getItemStack();

            boolean jukeboxHasOurDisc = !currentDisc.isEmpty()
                    && currentDisc.getItem() instanceof CustomDiscItem;
            boolean playerHoldsOurDisc = !heldItem.isEmpty()
                    && heldItem.getItem() instanceof CustomDiscItem;

            PacketDistributor.TargetPoint area = new PacketDistributor.TargetPoint(
                    pos.getX(), pos.getY(), pos.getZ(), 64,
                    ((ServerLevel) event.getLevel()).dimension());

            if (jukeboxHasOurDisc) {
                // Jukebox already has our disc — player is ejecting it.
                // Stop SVC proximity channel (if SVC is loaded)
                VoicechatCompat.stop(pos);
                // Also send a DiscPlayPacket stop for the vanilla Minecraft sound path
                PacketHandler.CHANNEL.send(
                        PacketDistributor.NEAR.with(() -> area),
                        new DiscPlayPacket(pos));  // stop packet

            } else if (playerHoldsOurDisc && currentDisc.isEmpty()) {
                // Player is inserting our disc into an empty jukebox.
                // Vanilla will process the insertion AFTER this event, so we schedule
                // the play packet on the next server tick when the BE is updated.
                final net.minecraft.server.level.ServerLevel serverLevel =
                        (ServerLevel) event.getLevel();
                final ItemStack discToPlay = heldItem.copy();

                // Use a one-shot delayed task via the server's tick scheduler
                serverLevel.getServer().execute(() -> {
                    // Re-check: vanilla should have inserted the disc by now
                    BlockEntity fresh = serverLevel.getBlockEntity(pos);
                    if (!(fresh instanceof JukeboxBlockEntity freshJukebox)) return;

                    ItemStack inserted = freshJukebox.getItem(0);
                    if (inserted.isEmpty()) {
                        // Still empty — vanilla might not have placed it yet;
                        // use the copy we took of the held item instead
                        inserted = discToPlay;
                    }

                    if (!(inserted.getItem() instanceof CustomDiscItem discItem)) return;

                    CompoundTag tag = inserted.getTag();
                    if (tag == null) return;
                    String soundId = tag.getString(CustomDiscItem.NBT_SOUND_ID);
                    String title   = tag.getString(CustomDiscItem.NBT_TITLE);
                    // Default 0.35 for discs created before per-disc volume was added
                    float  volume  = tag.contains(CustomDiscItem.NBT_VOLUME)
                            ? tag.getFloat(CustomDiscItem.NBT_VOLUME) : 0.35f;
                    if (soundId.isEmpty()) return;

                    DiscMod.LOGGER.debug("[CustomDiscs] Disc inserted: {} @ {} (vol {})", soundId, pos, volume);

                    if (VoicechatCompat.isLoaded()) {
                        // SVC path: play through a LocationalAudioChannel for true 3D proximity audio.
                        // soundId is e.g. "customdiscs:mysong" — the OGG filename is the part after ":"
                        String baseName = soundId.contains(":") ? soundId.substring(soundId.indexOf(':') + 1) : soundId;
                        java.io.File oggFile = com.customdiscs.util.SoundRegistryHelper.getSoundsFolder()
                                .resolve(baseName + ".ogg").toFile();
                        if (oggFile.exists()) {
                            VoicechatCompat.play(serverLevel, pos, oggFile, volume);
                        } else {
                            DiscMod.LOGGER.warn("[CustomDiscs] SVC: OGG file not found: {}", oggFile);
                        }
                        // Also send DiscPlayPacket so the vanilla sound system suppresses MUSIC_DISC_13
                        // and shows the "Now Playing" toast — but with an empty soundId so no sound plays.
                        PacketHandler.CHANNEL.send(
                                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                                        pos.getX(), pos.getY(), pos.getZ(), 64,
                                        serverLevel.dimension())),
                                new DiscPlayPacket(pos, "", title)); // empty soundId = toast only
                    } else {
                        // Fallback: vanilla Minecraft sound via DiscPlayPacket (no 3D attenuation on stereo files)
                        PacketHandler.CHANNEL.send(
                                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                                        pos.getX(), pos.getY(), pos.getZ(), 64,
                                        serverLevel.dimension())),
                                new DiscPlayPacket(pos, soundId, title));
                    }
                });
            }
        }
    }
}
