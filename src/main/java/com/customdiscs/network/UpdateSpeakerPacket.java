package com.customdiscs.network;

import com.customdiscs.block.TrainSpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client → Server packet sent when the player saves settings in the
 * Train Speaker GUI.  Carries updated station name and stop list.
 * If {@code testAnnounce} is true the server also fires an immediate announcement.
 */
public class UpdateSpeakerPacket {

    private final BlockPos pos;
    private final String stationName;
    private final List<String> stops;
    private final boolean testAnnounce;

    public UpdateSpeakerPacket(BlockPos pos, String stationName, List<String> stops, boolean testAnnounce) {
        this.pos          = pos;
        this.stationName  = stationName;
        this.stops        = stops;
        this.testAnnounce = testAnnounce;
    }

    // ── Codec ─────────────────────────────────────────────────────────────────

    public static UpdateSpeakerPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String station = buf.readUtf(256);
        int n = buf.readVarInt();
        List<String> stops = new ArrayList<>(n);
        for (int i = 0; i < n; i++) stops.add(buf.readUtf(256));
        boolean test = buf.readBoolean();
        return new UpdateSpeakerPacket(pos, station, stops, test);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(stationName, 256);
        buf.writeVarInt(stops.size());
        for (String s : stops) buf.writeUtf(s, 256);
        buf.writeBoolean(testAnnounce);
    }

    // ── Handler ───────────────────────────────────────────────────────────────

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender();
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof TrainSpeakerBlockEntity speaker)) return;
            // Security: only allow if the player is close enough
            if (sender.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) return;

            speaker.setData(stationName, stops);
            if (testAnnounce) speaker.triggerAnnounce(level, pos);
        });
        ctx.setPacketHandled(true);
    }
}
