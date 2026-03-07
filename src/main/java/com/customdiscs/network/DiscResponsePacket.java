package com.customdiscs.network;

import com.customdiscs.client.screen.DiscRecorderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DiscResponsePacket {
    private final String message;

    public DiscResponsePacket(String message) {
        this.message = message;
    }

    public static void encode(DiscResponsePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.message, 256);
    }

    public static DiscResponsePacket decode(FriendlyByteBuf buf) {
        return new DiscResponsePacket(buf.readUtf(256));
    }

    public static void handle(DiscResponsePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    if (Minecraft.getInstance().screen instanceof DiscRecorderScreen screen) {
                        screen.handleServerResponse(pkt.message);
                    }
                })
        );
        ctx.get().setPacketHandled(true);
    }
}
