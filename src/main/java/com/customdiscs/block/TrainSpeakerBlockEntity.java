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
 * Stores a map of station-name → custom announcement text.
 * The {@link com.customdiscs.compat.create.TrainSpeakerMovement} reads
 * this data from the block entity's NBT during contraption ticking.
 */
public class TrainSpeakerBlockEntity extends BlockEntity implements MenuProvider {

    /**
     * Per-station custom announcements. Key = station name, value = custom text.
     * Empty value = use default "Now arriving at …" message.
     */
    private final Map<String, String> customAnnouncements = new HashMap<>();

    public TrainSpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAIN_SPEAKER_BE.get(), pos, state);
    }

    public Map<String, String> getCustomAnnouncements() { return customAnnouncements; }

    public void setCustomAnnouncements(Map<String, String> map) {
        customAnnouncements.clear();
        customAnnouncements.putAll(map);
        setChanged();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        CompoundTag annTag = new CompoundTag();
        for (var e : customAnnouncements.entrySet())
            annTag.putString(e.getKey(), e.getValue());
        tag.put("CustomAnnouncements", annTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        customAnnouncements.clear();
        CompoundTag annTag = tag.getCompound("CustomAnnouncements");
        for (String key : annTag.getAllKeys())
            customAnnouncements.put(key, annTag.getString(key));
    }

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
