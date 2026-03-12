package com.customdiscs.block;

import com.customdiscs.registry.ModBlockEntities;
import com.customdiscs.menu.TrainSpeakerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Block entity for the Train Announcement Speaker.
 * <p>
 * Stores:
 * <ul>
 *   <li>A global announcement format string with {@code {station}} and {@code {message}}
 *       placeholders and {@code §} color codes.</li>
 *   <li>Per-station custom message text (also supports {@code §} codes).</li>
 * </ul>
 * <p>
 * Default format: {@code §e§l[ {station} ]§r §f{message}}
 */
public class TrainSpeakerBlockEntity extends BlockEntity implements MenuProvider {

    /** Default action-bar format. Supports § codes and {station}/{message} placeholders. */
    public static final String DEFAULT_FORMAT = "§e§l[ {station} ]§r §f{message}";

    private String globalFormat = DEFAULT_FORMAT;
    private final Map<String, String> customAnnouncements = new HashMap<>();

    public TrainSpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAIN_SPEAKER_BE.get(), pos, state);
    }

    // ── Format ───────────────────────────────────────────────────────────────

    public String getGlobalFormat() { return globalFormat; }

    public void setGlobalFormat(String fmt) {
        this.globalFormat = (fmt == null || fmt.isBlank()) ? DEFAULT_FORMAT : fmt;
        setChanged();
    }

    /**
     * Builds the final announcement string for the given station.
     * Applies the global format template, substituting placeholders.
     */
    public String buildAnnouncement(String stationName) {
        String message = customAnnouncements.getOrDefault(stationName, "");
        if (message.isEmpty()) message = "Now arriving at " + stationName;
        return globalFormat
                .replace("{station}", stationName)
                .replace("{message}", message);
    }

    // ── Custom announcements ─────────────────────────────────────────────────

    public Map<String, String> getCustomAnnouncements() { return customAnnouncements; }

    public void setCustomAnnouncements(Map<String, String> map) {
        customAnnouncements.clear();
        customAnnouncements.putAll(map);
        setChanged();
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public void saveAdditional(CompoundTag tag) {
        tag.putString("GlobalFormat", globalFormat);
        CompoundTag annTag = new CompoundTag();
        for (var e : customAnnouncements.entrySet())
            annTag.putString(e.getKey(), e.getValue());
        tag.put("CustomAnnouncements", annTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("GlobalFormat"))
            globalFormat = tag.getString("GlobalFormat");
        customAnnouncements.clear();
        CompoundTag annTag = tag.getCompound("CustomAnnouncements");
        for (String key : annTag.getAllKeys())
            customAnnouncements.put(key, annTag.getString(key));
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
