package com.customdiscs.registry;

import com.customdiscs.DiscMod;
import com.customdiscs.menu.DiscRecorderMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, DiscMod.MOD_ID);

    public static final RegistryObject<MenuType<DiscRecorderMenu>> DISC_RECORDER_MENU =
            MENUS.register("disc_recorder",
                    () -> IForgeMenuType.create((windowId, inv, data) ->
                            new DiscRecorderMenu(windowId, data.readBlockPos())));
}
