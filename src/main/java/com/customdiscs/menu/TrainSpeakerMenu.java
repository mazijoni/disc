package com.customdiscs.menu;

import com.customdiscs.block.TrainSpeakerBlockEntity;
import com.customdiscs.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TrainSpeakerMenu extends AbstractContainerMenu {

    private final BlockPos blockPos;
    private final String globalFormat;
    private final Map<String, String> customAnnouncements;

    /** Server-side constructor */
    public TrainSpeakerMenu(int windowId, BlockPos pos) {
        super(ModMenuTypes.TRAIN_SPEAKER_MENU.get(), windowId);
        this.blockPos = pos;
        this.globalFormat = TrainSpeakerBlockEntity.DEFAULT_FORMAT;
        this.customAnnouncements = Collections.emptyMap();
    }

    /** Client-side constructor — reads from the network buffer */
    public TrainSpeakerMenu(int windowId, BlockPos pos, FriendlyByteBuf buf) {
        super(ModMenuTypes.TRAIN_SPEAKER_MENU.get(), windowId);
        this.blockPos = pos;
        this.globalFormat = buf.readUtf(512);
        int n = buf.readVarInt();
        Map<String, String> map = new HashMap<>(n);
        for (int i = 0; i < n; i++) map.put(buf.readUtf(256), buf.readUtf(512));
        this.customAnnouncements = map;
    }

    public BlockPos getBlockPos()                       { return blockPos; }
    public String getGlobalFormat()                     { return globalFormat; }
    public Map<String, String> getCustomAnnouncements() { return customAnnouncements; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
                blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }
}
