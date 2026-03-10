package com.customdiscs.block;

import com.customdiscs.network.AnnounceSpeakerPacket;
import com.customdiscs.network.PacketHandler;
import com.customdiscs.registry.ModBlockEntities;
import com.customdiscs.menu.TrainSpeakerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TrainSpeakerBlockEntity extends BlockEntity implements MenuProvider {

    private String stationName = "Unnamed Station";
    private final List<String> stopList = new ArrayList<>();
    private int currentStopIndex = 0;
    private boolean wasPowered = false;

    public TrainSpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAIN_SPEAKER_BE.get(), pos, state);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public static void tick(Level level, BlockPos pos, BlockState state, TrainSpeakerBlockEntity be) {
        // tick is intentionally empty; rising-edge detection is done in onNeighborChanged
    }

    /** Called by {@link TrainSpeakerBlock#neighborChanged}. Detects rising redstone edge. */
    public void onNeighborChanged(Level level, BlockPos pos) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered && !wasPowered) {
            announce(level, pos);
        }
        wasPowered = powered;
    }

    private void announce(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        String nextStop = stopList.isEmpty() ? "" : stopList.get(currentStopIndex % stopList.size());
        currentStopIndex = (currentStopIndex + 1) % Math.max(1, stopList.size());
        setChanged();

        AnnounceSpeakerPacket packet = new AnnounceSpeakerPacket(stationName, nextStop);
        // Send to all players within 64 blocks
        serverLevel.players().stream()
                .filter(p -> p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64 * 64)
                .forEach(p -> PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet));
    }

    /** Triggered manually by the "Test Announce" button in the GUI (fired from UpdateSpeakerPacket handler). */
    public void triggerAnnounce(Level level, BlockPos pos) {
        announce(level, pos);
    }

    // ── Data accessors ────────────────────────────────────────────────────────

    public String getStationName() { return stationName; }
    public List<String> getStopList() { return stopList; }
    public int getCurrentStopIndex() { return currentStopIndex; }

    public void setData(String stationName, List<String> stops) {
        this.stationName = stationName;
        this.stopList.clear();
        this.stopList.addAll(stops);
        this.currentStopIndex = 0;
        setChanged();
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public void saveAdditional(CompoundTag tag) {
        tag.putString("StationName", stationName);
        tag.putInt("StopIndex", currentStopIndex);
        tag.putBoolean("WasPowered", wasPowered);
        ListTag stopsTag = new ListTag();
        for (String stop : stopList) stopsTag.add(StringTag.valueOf(stop));
        tag.put("Stops", stopsTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stationName = tag.getString("StationName");
        if (stationName.isEmpty()) stationName = "Unnamed Station";
        currentStopIndex = tag.getInt("StopIndex");
        wasPowered = tag.getBoolean("WasPowered");
        stopList.clear();
        ListTag stopsTag = tag.getList("Stops", Tag.TAG_STRING);
        for (int i = 0; i < stopsTag.size(); i++) {
            stopList.add(stopsTag.getString(i));
        }
    }

    // ── MenuProvider ─────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.customdiscs.train_speaker");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new TrainSpeakerMenu(id, this.worldPosition);
    }
}
