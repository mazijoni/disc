package com.customdiscs.menu;

import com.customdiscs.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrainSpeakerMenu extends AbstractContainerMenu {

    private final BlockPos blockPos;
    private final String stationName;
    private final List<String> stops;

    /** Server-side constructor */
    public TrainSpeakerMenu(int windowId, BlockPos pos) {
        super(ModMenuTypes.TRAIN_SPEAKER_MENU.get(), windowId);
        this.blockPos = pos;
        this.stationName = "Unnamed Station";
        this.stops = Collections.emptyList();
    }

    /** Client-side constructor — reads data from the network buffer */
    public TrainSpeakerMenu(int windowId, BlockPos pos, FriendlyByteBuf buf) {
        super(ModMenuTypes.TRAIN_SPEAKER_MENU.get(), windowId);
        this.blockPos = pos;
        this.stationName = buf.readUtf(256);
        int n = buf.readVarInt();
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readUtf(256));
        this.stops = list;
    }

    public BlockPos getBlockPos() { return blockPos; }
    public String getStationName() { return stationName; }
    public List<String> getStops() { return stops; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
                blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }
}
