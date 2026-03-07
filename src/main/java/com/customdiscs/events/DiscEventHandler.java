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
import net.minecraftforge.network.PacketDistributor;

public class DiscEventHandler {

    /**
     * CLIENT — suppresses the dummy MUSIC_DISC_13 that vanilla plays when our
     * disc is inserted. The real sound is delivered by the server via DiscPlayPacket.
     */
    @Mod.EventBusSubscriber(value = Dist.CLIENT)
    public static class ClientHandler {

        @SubscribeEvent
        @OnlyIn(Dist.CLIENT)
        public static void onPlaySound(PlaySoundEvent event) {
            SoundInstance incoming = event.getSound();
            if (incoming == null || incoming.getSource() != SoundSource.RECORDS) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            double sx = incoming.getX(), sy = incoming.getY(), sz = incoming.getZ();
            BlockPos pos = BlockPos.containing(sx, sy, sz);

            // If our disc is in this jukebox, suppress the vanilla dummy sound.
            // The server will send us a DiscPlayPacket with the real sound.
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (!(be instanceof JukeboxBlockEntity jukebox)) return;

            ItemStack disc = jukebox.getItem(0);
            if (!disc.isEmpty() && disc.getItem() instanceof CustomDiscItem) {
                event.setSound(null); // suppress MUSIC_DISC_13
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
                // Jukebox already has our disc — player is ejecting it
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
                    if (soundId.isEmpty()) return;

                    DiscMod.LOGGER.debug("[CustomDiscs] Sending DiscPlayPacket play: {} @ {}", soundId, pos);
                    PacketHandler.CHANNEL.send(
                            PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                                    pos.getX(), pos.getY(), pos.getZ(), 64,
                                    serverLevel.dimension())),
                            new DiscPlayPacket(pos, soundId, title));
                });
            }
        }
    }
}
