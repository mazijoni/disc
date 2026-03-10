package com.customdiscs.registry;

import com.customdiscs.DiscMod;
import com.customdiscs.item.CustomDiscItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DiscMod.MOD_ID);

    public static final RegistryObject<Item> DISC_RECORDER_ITEM = ITEMS.register("disc_recorder",
            () -> new BlockItem(ModBlocks.DISC_RECORDER.get(),
                    new Item.Properties().stacksTo(64)));

    public static final RegistryObject<CustomDiscItem> CUSTOM_DISC = ITEMS.register("custom_disc",
            () -> new CustomDiscItem(new Item.Properties().stacksTo(1)));

    /** Blank disc form — crafted from sheets, pressed into a custom_disc in the Disc Recorder. */
    public static final RegistryObject<Item> UNASSEMBLED_DISC = ITEMS.register("unassembled_disc",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> TRAIN_SPEAKER_ITEM = ITEMS.register("train_speaker",
            () -> new BlockItem(ModBlocks.TRAIN_SPEAKER.get(),
                    new Item.Properties().stacksTo(64)));
}
