package com.customdiscs.block;

import com.customdiscs.DiscMod;
import com.customdiscs.item.CustomDiscItem;
import com.customdiscs.menu.DiscRecorderMenu;
import com.customdiscs.registry.ModBlockEntities;
import com.customdiscs.registry.ModItems;
import com.customdiscs.util.SoundRegistryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class DiscRecorderBlockEntity extends BlockEntity implements MenuProvider {

    public DiscRecorderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISC_RECORDER_BE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.customdiscs.disc_recorder");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new DiscRecorderMenu(id, this.worldPosition);
    }

    /**
     * Called from the server-side packet handler.
     * Copies the OGG from filePath into the dynamic resource pack,
     * registers the SoundEvent, and gives a custom disc to the player.
     *
     * @return status message to send back to client
     */
    public String processDisc(Player player, String filePath, String title) {
        String result = SoundRegistryHelper.processNewDisc(filePath, title);
        if (result.startsWith("OK:")) {
            String soundId = result.substring(3); // everything after "OK:"
            ItemStack disc = new ItemStack(ModItems.CUSTOM_DISC.get());
            CompoundTag tag = new CompoundTag();
            tag.putString(CustomDiscItem.NBT_SOUND_ID, soundId);
            tag.putString(CustomDiscItem.NBT_TITLE, title);
            disc.setTag(tag);
            // Give to player, drop if inventory full
            if (!player.getInventory().add(disc)) {
                player.drop(disc, false);
            }
            return "success:" + soundId;
        }
        return result; // error message
    }
}
