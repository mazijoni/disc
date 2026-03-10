package com.customdiscs.network;

import com.customdiscs.DiscMod;
import com.customdiscs.client.EdgeTtsClient;
import com.customdiscs.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client: plays a ding, shows a subtitle, and triggers TTS.
 * Now receives station name + full announcement text.
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
        ctxSupplier.get().enqueueWork(this::handleClient);
        ctxSupplier.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private void handleClient() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Play ding chime
        mc.player.playNotifySound(
                ModSounds.DING.get(), SoundSource.BLOCKS, 1.0f, 1.0f);

        // Show action-bar subtitle with station name
        mc.player.displayClientMessage(
                Component.literal("§e§l[" + stationName + "] §r§f" + announcement), true);

        // Trigger TTS
        EdgeTtsClient.speak(announcement);
    }
}
