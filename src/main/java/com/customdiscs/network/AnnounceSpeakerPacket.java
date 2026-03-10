package com.customdiscs.network;

import com.customdiscs.client.EdgeTtsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import com.customdiscs.registry.ModSounds;

/**
 * Server → Client packet sent when a Train Speaker speaker fires an announcement.
 * On the client it plays the chime sound, shows a subtitle, then calls
 * {@link EdgeTtsClient} to synthesise and play the spoken announcement.
 */
public class AnnounceSpeakerPacket {

    private final String station;
    private final String nextStop;

    public AnnounceSpeakerPacket(String station, String nextStop) {
        this.station  = station;
        this.nextStop = nextStop;
    }

    // ── Codec ─────────────────────────────────────────────────────────────────

    public static AnnounceSpeakerPacket decode(FriendlyByteBuf buf) {
        return new AnnounceSpeakerPacket(buf.readUtf(256), buf.readUtf(256));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(station,  256);
        buf.writeUtf(nextStop, 256);
    }

    // ── Handler ───────────────────────────────────────────────────────────────

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(station, nextStop))
        );
        ctx.setPacketHandled(true);
    }

    private static void handleClient(String station, String nextStop) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. Play the chime sound
        mc.player.playSound(ModSounds.DING.get(), 1.0f, 1.0f);

        // 2. Build announcement text and show as subtitle
        String announcementText = nextStop.isEmpty()
                ? "Now arriving at " + station + "."
                : "Now arriving at " + station + ". Next stop: " + nextStop + ".";
        mc.player.displayClientMessage(
                Component.literal("§l[" + station + "]§r §e" + (nextStop.isEmpty() ? "Terminal station." : "Next: §f" + nextStop)),
                true  // show as action bar overlay
        );

        // 3. Speak asynchronously via Edge TTS (neural voice, no API key)
        EdgeTtsClient.speak(announcementText);
    }
}
