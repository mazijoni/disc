package com.customdiscs.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A music disc that stores its sound and title in NBT.
 *
 * Extends RecordItem so vanilla JukeboxBlock.use() accepts it via:
 *   if (itemstack.getItem() instanceof RecordItem) { jukebox.setRecord(...); }
 * setRecord() properly initialises JukeboxSongPlayer.songItemStack so the
 * ticker fires levelEvent(1010, Item.getId(disc)) on the next server tick.
 * Our PlaySoundEvent handler intercepts that and redirects MUSIC_DISC_13
 * → the real custom sound with proper 3D LINEAR attenuation.
 */
public class CustomDiscItem extends RecordItem {

    public static final String NBT_SOUND_ID = "sound_id";
    public static final String NBT_TITLE    = "title";

    public CustomDiscItem(Properties properties) {
        // comparatorOutput=1 same as vanilla discs; dummy sound required by RecordItem ctor
        super(1, () -> SoundEvents.MUSIC_DISC_13, properties, 0);
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(NBT_TITLE)) {
            return Component.literal(tag.getString(NBT_TITLE));
        }
        return Component.translatable(this.getDescriptionId());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> components, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            if (tag.contains(NBT_TITLE)) {
                components.add(Component.literal(tag.getString(NBT_TITLE))
                        .withStyle(ChatFormatting.YELLOW));
            }
            if (tag.contains(NBT_SOUND_ID)) {
                components.add(Component.literal("♪ " + tag.getString(NBT_SOUND_ID))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        components.add(Component.translatable("item.customdiscs.custom_disc.desc")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    // -------------------------------------------------------------------------
    // Sound lookup
    // -------------------------------------------------------------------------

    /** Returns the registered SoundEvent for this disc, or null if not found. */
    @Nullable
    public SoundEvent getSoundForStack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_SOUND_ID)) return null;
        ResourceLocation loc = new ResourceLocation(tag.getString(NBT_SOUND_ID));
        return ForgeRegistries.SOUND_EVENTS.getValue(loc);
    }

    public boolean hasSound(ItemStack stack) {
        return getSoundForStack(stack) != null;
    }
}
