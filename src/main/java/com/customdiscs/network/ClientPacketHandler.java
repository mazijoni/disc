package com.customdiscs.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.api.distmarker.Dist;

/**
 * Client-only handler methods for network packets.
 * <p>
 * This class is NEVER loaded on a dedicated server because it is only ever
 * referenced from inside {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)}
 * lambdas, which the server JVM never evaluates.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler {

    private ClientPacketHandler() {}

    /* ---- DiscPlayPacket ---- */

    public static void handleDiscPlay(BlockPos pos, boolean play, String soundId, String title) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Always stop any currently playing RECORDS sound first
        mc.getSoundManager().stop(null, SoundSource.RECORDS);

        if (play) {
            // Show "Now Playing" toast regardless of whether we're playing sound here
            // (when SVC is active, soundId is empty but we still want the toast)
            if (!title.isEmpty()) {
                mc.gui.setNowPlaying(Component.literal(title));
            }

            // Only play Minecraft sound when soundId is set (non-SVC fallback path)
            if (!soundId.isEmpty()) {
                ResourceLocation loc = new ResourceLocation(soundId);

                // Do NOT use ForgeRegistries.SOUND_EVENTS.getValue(loc) here —
                // the client-side ForgeRegistry only contains sounds registered at
                // game startup. Runtime-added discs are absent from it.
                // The SoundManager resolves the actual audio via sounds.json;
                // we just need a SoundEvent that carries the correct location.
                SoundEvent sound = SoundEvent.createVariableRangeEvent(loc);

                Vec3 center = new Vec3(
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5);
                mc.getSoundManager().play(
                        SimpleSoundInstance.forRecord(sound, center));
            }
        }
    }

    /* ---- DiscResponsePacket ---- */

    public static void handleDiscResponse(String message) {
        if (Minecraft.getInstance().screen
                instanceof com.customdiscs.client.screen.DiscRecorderScreen screen) {
            screen.handleServerResponse(message);
        }
    }

    /* ---- AnnounceSpeakerPacket ---- */

    public static void handleAnnounce(String announcement) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Play a chime using the XP orb sound (clear ping, definitely valid in 1.20.1)
        mc.player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.5f);

        // Show styled announcement on the action bar
        // The announcement string already has § color codes applied by the server
        mc.player.displayClientMessage(Component.literal(announcement), true);
    }
}
