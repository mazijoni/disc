package com.customdiscs;

import com.customdiscs.client.screen.DiscRecorderScreen;
import com.customdiscs.client.screen.TrainSpeakerScreen;
import com.customdiscs.ponder.DiscPonderPlugin;
import com.customdiscs.registry.ModMenuTypes;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.gui.screens.MenuScreens;

public class ClientSetup {
    public static void register() {
        MenuScreens.register(ModMenuTypes.DISC_RECORDER_MENU.get(), DiscRecorderScreen::new);
        MenuScreens.register(ModMenuTypes.TRAIN_SPEAKER_MENU.get(), TrainSpeakerScreen::new);
        PonderIndex.addPlugin(new DiscPonderPlugin());
    }
}
