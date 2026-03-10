package com.customdiscs.registry;

import com.customdiscs.DiscMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DiscMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> DISC_TAB = TABS.register("disc_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.customdiscs"))
                    .icon(() -> ModItems.CUSTOM_DISC.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ModItems.DISC_RECORDER_ITEM.get());
                        output.accept(ModItems.TRAIN_SPEAKER_ITEM.get());
                        output.accept(ModItems.UNASSEMBLED_DISC.get());
                        output.accept(ModItems.CUSTOM_DISC.get());
                    })
                    .build());
}
