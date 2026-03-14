package com.customdiscs.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client packet that tells the client to play (or stop) a custom disc.
 *
 * Using a dedicated packet (instead of level.playSound() or level event data)
 * gives us:
 *  - Correct 3D positional audio via SimpleSoundInstance.forRecord()
 *  - Immediate stop on eject (separate stop packet)
 *  - No dependency on block entity sync timing for sound_id
 */
public class DiscPlayPacket {

    private final BlockPos pos;
    private final String soundId;   // e.g. "customdiscs:espresso_sabrina_carpenter"
    private final String title;     // display name for "Now Playing" toast
    private final boolean play;     // true = play, false = stop

    /** Play constructor */
    public DiscPlayPacket(BlockPos pos, String soundId, String title) {
        this(pos, soundId, title, true);
    }

    /** Stop constructor */
    public DiscPlayPacket(BlockPos pos) {
        this(pos, "", "", false);
    }

    private DiscPlayPacket(BlockPos pos, String soundId, String title, boolean play) {
        this.pos     = pos;
        this.soundId = soundId;
        this.title   = title;
        this.play    = play;
    }

    public static void encode(DiscPlayPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeBoolean(pkt.play);
        if (pkt.play) {
            buf.writeUtf(pkt.soundId, 256);
            buf.writeUtf(pkt.title,   128);
        }
    }

    public static DiscPlayPacket decode(FriendlyByteBuf buf) {
        BlockPos pos  = buf.readBlockPos();
        boolean play  = buf.readBoolean();
        if (play) {
            String soundId = buf.readUtf(256);
            String title   = buf.readUtf(128);
            return new DiscPlayPacket(pos, soundId, title, true);
        }
        return new DiscPlayPacket(pos);
    }

    public static void handle(DiscPlayPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientPacketHandler.handleDiscPlay(pkt.pos, pkt.play, pkt.soundId, pkt.title))
        );
        ctx.get().setPacketHandled(true);
    }
}
