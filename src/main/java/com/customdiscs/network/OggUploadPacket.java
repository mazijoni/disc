package com.customdiscs.network;

import com.customdiscs.block.DiscRecorderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Client → Server chunked OGG upload packet.
 *
 * The client reads the OGG file locally, splits it into CHUNK_SIZE byte
 * chunks, and sends one packet per chunk.  The server accumulates them in
 * PENDING, then processes the complete audio once all chunks have arrived.
 *
 * Maximum supported file size: MAX_FILE_BYTES (10 MB).
 */
public class OggUploadPacket {

    /** Maximum bytes per chunk sent over the wire (~30 KB). */
    public static final int CHUNK_SIZE = 30_720;

    /** Hard limit on total file size the server will accept. */
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024; // 10 MB

    // -------------------------------------------------------------------------

    private final BlockPos pos;
    private final String   title;
    private final float    volume;
    private final int      chunkIndex;
    private final int      totalChunks;
    private final byte[]   data;

    public OggUploadPacket(BlockPos pos, String title, float volume,
                           int chunkIndex, int totalChunks, byte[] data) {
        this.pos         = pos;
        this.title       = title;
        this.volume      = volume;
        this.chunkIndex  = chunkIndex;
        this.totalChunks = totalChunks;
        this.data        = data;
    }

    // ── Codec ─────────────────────────────────────────────────────────────────

    public static void encode(OggUploadPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.title, 64);
        buf.writeFloat(pkt.volume);
        buf.writeInt(pkt.chunkIndex);
        buf.writeInt(pkt.totalChunks);
        buf.writeByteArray(pkt.data);
    }

    public static OggUploadPacket decode(FriendlyByteBuf buf) {
        BlockPos pos         = buf.readBlockPos();
        String   title       = buf.readUtf(64);
        float    volume      = buf.readFloat();
        int      chunkIndex  = buf.readInt();
        int      totalChunks = buf.readInt();
        byte[]   data        = buf.readByteArray(CHUNK_SIZE + 64); // slight headroom
        return new OggUploadPacket(pos, title, volume, chunkIndex, totalChunks, data);
    }

    // ── Server-side state ─────────────────────────────────────────────────────

    private static class UploadState {
        final BlockPos pos;
        final String   title;
        final float    volume;
        final int      totalChunks;
        final byte[][] chunks;
        int            received = 0;

        UploadState(BlockPos pos, String title, float volume, int totalChunks) {
            this.pos         = pos;
            this.title       = title;
            this.volume      = volume;
            this.totalChunks = totalChunks;
            this.chunks      = new byte[totalChunks][];
        }
    }

    /** Active uploads keyed by player UUID. */
    private static final Map<UUID, UploadState> PENDING = new ConcurrentHashMap<>();

    // ── Handler (runs on server main thread via enqueueWork) ──────────────────

    public static void handle(OggUploadPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            UUID id = player.getUUID();

            // ── Sanity checks ────────────────────────────────────────────────
            if (pkt.chunkIndex < 0 || pkt.totalChunks <= 0 || pkt.chunkIndex >= pkt.totalChunks) {
                respond(player, "error:Invalid chunk metadata");
                PENDING.remove(id);
                return;
            }
            long maxBytes = (long) pkt.totalChunks * CHUNK_SIZE;
            if (maxBytes > MAX_FILE_BYTES + CHUNK_SIZE) {  // +CHUNK_SIZE for last chunk slack
                respond(player, "error:File too large (max 10 MB)");
                PENDING.remove(id);
                return;
            }

            // ── Find or create pending state for this player ─────────────────
            UploadState state = PENDING.get(id);
            if (state == null || state.totalChunks != pkt.totalChunks
                    || !state.title.equals(pkt.title)) {
                // New upload (or client retrying) — reset
                state = new UploadState(pkt.pos, pkt.title, pkt.volume, pkt.totalChunks);
                PENDING.put(id, state);
            }

            // Store this chunk (idempotent if resent)
            if (state.chunks[pkt.chunkIndex] == null) {
                state.chunks[pkt.chunkIndex] = pkt.data;
                state.received++;
            }

            // ── If all chunks are in, assemble and process ───────────────────
            if (state.received >= state.totalChunks) {
                PENDING.remove(id);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for (byte[] chunk : state.chunks) {
                    if (chunk != null) {
                        try { baos.write(chunk); } catch (Exception ignored) {}
                    }
                }
                byte[] oggBytes = baos.toByteArray();

                BlockEntity be = player.level().getBlockEntity(state.pos);
                if (!(be instanceof DiscRecorderBlockEntity discBE)) {
                    respond(player, "error:Disc Recorder block not found");
                    return;
                }

                String result = discBE.processDiscFromBytes(player, oggBytes,
                        state.title, state.volume);
                respond(player, result.startsWith("OK:")
                        ? "success:" + result.substring(3)
                        : result);
            }
            // If not complete yet: no response — client tracks progress via chunk count
        });
        ctx.get().setPacketHandled(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void respond(ServerPlayer player, String msg) {
        PacketHandler.CHANNEL.sendTo(
                new DiscResponsePacket(msg),
                player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }
}
