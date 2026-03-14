package com.customdiscs.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client: plays a bell chime and shows a styled action-bar announcement.
 * The announcement text supports Minecraft § color codes.
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
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientPacketHandler.handleAnnounce(announcement))
        );
        ctx.setPacketHandled(true);
    }
}
