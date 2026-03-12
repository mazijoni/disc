package com.customdiscs.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client: plays a bell chime and shows a styled action-bar announcement.
 * Text-to-speech has been removed. The announcement text supports Minecraft § color codes.
 */
public class AnnounceSpeakerPacket {

    private final String stationName;
    private final String announcement;

    public AnnounceSpeakerPacket(String stationName, String announcement) {
        this.stationName  = stationName;
        this.announcement = announcement;
    }

    public static AnnounceSpeakerPacket decode(FriendlyByteBuf buf) {
        return new AnnounceSpeakerPacket(buf.readUtf(256), buf.readUtf(512));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(stationName, 256);
        buf.writeUtf(announcement, 512);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(this::handleClient);
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private void handleClient() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Play a chime using the XP orb sound (clear ping, definitely valid in 1.20.1)
        mc.player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.5f);

        // Show styled announcement on the action bar
        // The announcement string already has § color codes applied by the server
        mc.player.displayClientMessage(
                Component.literal(announcement), true);
    }
}
