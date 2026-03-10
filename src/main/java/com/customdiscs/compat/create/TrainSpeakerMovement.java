package com.customdiscs.compat.create;

import com.customdiscs.DiscMod;
import com.customdiscs.network.AnnounceSpeakerPacket;
import com.customdiscs.network.PacketHandler;
import com.simibubi.create.Create;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.entity.Carriage;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * MovementBehaviour for the Train Announcement Speaker.
 * <p>
 * When mounted on a train contraption, this ticks every game tick.
 * It watches the train's {@link ScheduleRuntime} state for transitions
 * to {@code POST_TRANSIT} (= the train arrived at a station) and then
 * sends an {@link AnnounceSpeakerPacket} to every player passenger on the train.
 */
public class TrainSpeakerMovement implements MovementBehaviour {

    private static final String KEY_LAST_STATE = "LastState";

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide()) return;

        // ── Find the Train ────────────────────────────────────────────────
        Train train = getTrain(context);
        if (train == null) return;

        ScheduleRuntime runtime = train.runtime;
        if (runtime == null || runtime.schedule == null) return;

        // ── Detect arrival (transition TO POST_TRANSIT) ───────────────────
        int currentState = runtime.state.ordinal();

        CompoundTag data = context.data;
        int lastState = data.getInt(KEY_LAST_STATE);
        data.putInt(KEY_LAST_STATE, currentState);

        boolean justArrived = (currentState == ScheduleRuntime.State.POST_TRANSIT.ordinal())
                && (lastState != ScheduleRuntime.State.POST_TRANSIT.ordinal());

        if (!justArrived) return;

        // ── Read station name from current schedule entry ─────────────────
        List<ScheduleEntry> entries = runtime.schedule.entries;
        int idx = runtime.currentEntry;
        if (idx < 0 || idx >= entries.size()) return;

        ScheduleEntry entry = entries.get(idx);
        if (!(entry.instruction instanceof DestinationInstruction dest)) return;

        String stationName = dest.getFilter();
        if (stationName == null || stationName.isEmpty()) return;

        // ── Read per-station custom announcement from BE NBT ──────────────
        String customMsg = "";
        if (context.blockEntityData != null) {
            CompoundTag annTags = context.blockEntityData.getCompound("CustomAnnouncements");
            if (annTags.contains(stationName)) {
                customMsg = annTags.getString(stationName);
            }
        }

        String announcement = (customMsg != null && !customMsg.isEmpty())
                ? customMsg
                : "Now arriving at " + stationName;

        // ── Send packet to all players: passengers AND nearby standing players ──
        AnnounceSpeakerPacket packet = new AnnounceSpeakerPacket(stationName, announcement);
        Set<UUID> notified = new HashSet<>();

        // 1. Passengers riding any carriage
        for (Carriage carriage : train.carriages) {
            carriage.forEachPresentEntity(cce -> {
                for (Entity passenger : cce.getPassengers()) {
                    if (passenger instanceof ServerPlayer sp && notified.add(sp.getUUID())) {
                        PacketHandler.CHANNEL.send(
                                PacketDistributor.PLAYER.with(() -> sp), packet);
                    }
                }
            });
        }

        // 2. Players standing near the train (within 32 blocks of the speaker position)
        if (context.world instanceof net.minecraft.server.level.ServerLevel serverLevel
                && context.position != null) {
            Vec3 pos = context.position;
            net.minecraft.world.phys.AABB range = net.minecraft.world.phys.AABB.ofSize(pos, 64, 32, 64);
            for (ServerPlayer sp : serverLevel.getPlayers(
                    p -> p.getBoundingBox().intersects(range))) {
                if (notified.add(sp.getUUID())) {
                    PacketHandler.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sp), packet);
                }
            }
        }

        DiscMod.LOGGER.debug("[TrainSpeaker] Announced '{}' to {} player(s).",
                announcement, notified.size());

    }

    /**
     * Get the Train from the contraption entity's trainId.
     */
    private Train getTrain(MovementContext context) {
        if (context.contraption == null) return null;
        AbstractContraptionEntity entity = context.contraption.entity;
        if (!(entity instanceof CarriageContraptionEntity cce)) return null;
        if (cce.trainId == null) return null;
        try {
            return Create.RAILWAYS.sided(context.world).trains.get(cce.trainId);
        } catch (Exception e) {
            return null;
        }
    }
}
