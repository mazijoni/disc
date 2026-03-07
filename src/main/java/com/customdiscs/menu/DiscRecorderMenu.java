package com.customdiscs.menu;

import com.customdiscs.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class DiscRecorderMenu extends AbstractContainerMenu {

    private final BlockPos blockPos;

    /** Server-side constructor */
    public DiscRecorderMenu(int windowId, BlockPos pos) {
        super(ModMenuTypes.DISC_RECORDER_MENU.get(), windowId);
        this.blockPos = pos;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

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
