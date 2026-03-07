package com.customdiscs.network;

import com.customdiscs.block.DiscRecorderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DiscCraftPacket {
    private final BlockPos pos;
    private final String filePath;
    private final String title;
    private final float volume;  // 0.0 – 1.0

    public DiscCraftPacket(BlockPos pos, String filePath, String title, float volume) {
        this.pos = pos;
        this.filePath = filePath;
        this.title = title;
        this.volume = volume;
    }

    public static void encode(DiscCraftPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.filePath, 512);
        buf.writeUtf(pkt.title, 64);
        buf.writeFloat(pkt.volume);
    }

    public static DiscCraftPacket decode(FriendlyByteBuf buf) {
        BlockPos pos   = buf.readBlockPos();
        String path    = buf.readUtf(512);
        String title   = buf.readUtf(64);
        float  volume  = buf.readFloat();
        return new DiscCraftPacket(pos, path, title, volume);
    }

    public static void handle(DiscCraftPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(pkt.pos);
            if (!(be instanceof DiscRecorderBlockEntity discBE)) return;

            String result = discBE.processDisc(player, pkt.filePath, pkt.title, pkt.volume);
            PacketHandler.CHANNEL.sendTo(
                    new DiscResponsePacket(result),
                    player.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        });
        ctx.get().setPacketHandled(true);
    }
}
