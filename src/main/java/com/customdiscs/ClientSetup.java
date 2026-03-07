package com.customdiscs;

import com.customdiscs.client.screen.DiscRecorderScreen;
import com.customdiscs.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;

public class ClientSetup {
    public static void register() {
        MenuScreens.register(ModMenuTypes.DISC_RECORDER_MENU.get(), DiscRecorderScreen::new);
    }
}
