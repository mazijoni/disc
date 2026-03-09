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
    public String processDisc(Player player, String filePath, String title, float volume) {
        // Require a blank (pressed) custom_disc in inventory
        int blankSlot = findBlankDisc(player);
        if (blankSlot == -1) {
            return "need_blank";  // client shows friendly message
        }

        String result = SoundRegistryHelper.processNewDisc(filePath, title);
        if (result.startsWith("OK:")) {
            String soundId = result.substring(3);

            // Encode the sound onto the blank disc in-place
            ItemStack disc = player.getInventory().getItem(blankSlot).split(1);
            CompoundTag tag = disc.getOrCreateTag();
            tag.putString(CustomDiscItem.NBT_SOUND_ID, soundId);
            tag.putString(CustomDiscItem.NBT_TITLE, title);
            tag.putFloat(CustomDiscItem.NBT_VOLUME, Math.max(0f, Math.min(1f, volume)));

            if (!player.getInventory().add(disc)) {
                player.drop(disc, false);
            }
            return "success:" + soundId;
        }
        return result;
    }

    /**
     * Called from the server-side OggUploadPacket handler.
     * Processes a complete OGG file that was uploaded by the client in chunks.
     *
     * @return status message to send back to client
     */
    public String processDiscFromBytes(Player player, byte[] oggBytes, String title, float volume) {
        int blankSlot = findBlankDisc(player);
        if (blankSlot == -1) {
            return "need_blank";
        }

        String result = SoundRegistryHelper.processNewDiscFromBytes(oggBytes, title);
        if (result.startsWith("OK:")) {
            String soundId = result.substring(3);

            ItemStack disc = player.getInventory().getItem(blankSlot).split(1);
            CompoundTag tag = disc.getOrCreateTag();
            tag.putString(CustomDiscItem.NBT_SOUND_ID, soundId);
            tag.putString(CustomDiscItem.NBT_TITLE, title);
            tag.putFloat(CustomDiscItem.NBT_VOLUME, Math.max(0f, Math.min(1f, volume)));

            if (!player.getInventory().add(disc)) {
                player.drop(disc, false);
            }
            return "success:" + soundId;
        }
        return result;
    }

    /**
     * Returns the first inventory slot that holds a blank custom_disc
     * (a pressed disc with no sound_id NBT yet), or -1 if none found.
     */
    private int findBlankDisc(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(ModItems.CUSTOM_DISC.get())) continue;
            CompoundTag tag = stack.getTag();
            // Blank = no tag, or tag without a sound_id
            if (tag == null || !tag.contains(CustomDiscItem.NBT_SOUND_ID)) return i;
        }
        return -1;
    }
}
