package com.customdiscs.network;

import com.customdiscs.block.TrainSpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client → Server: saves per-station custom announcements from the GUI.
 */
public class UpdateSpeakerPacket {

    private final BlockPos pos;
    private final Map<String, String> customAnnouncements;

    public UpdateSpeakerPacket(BlockPos pos, Map<String, String> customAnnouncements) {
        this.pos = pos;
        this.customAnnouncements = customAnnouncements;
    }

    public static UpdateSpeakerPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int n = buf.readVarInt();
        Map<String, String> map = new HashMap<>(n);
        for (int i = 0; i < n; i++) map.put(buf.readUtf(256), buf.readUtf(512));
        return new UpdateSpeakerPacket(pos, map);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(customAnnouncements.size());
        for (var e : customAnnouncements.entrySet()) {
            buf.writeUtf(e.getKey(), 256);
            buf.writeUtf(e.getValue(), 512);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender();
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof TrainSpeakerBlockEntity speaker)) return;
            if (sender.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) return;
            speaker.setCustomAnnouncements(customAnnouncements);
        });
        ctx.setPacketHandled(true);
    }
}
