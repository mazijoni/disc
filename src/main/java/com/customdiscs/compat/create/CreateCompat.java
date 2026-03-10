package com.customdiscs.compat.create;

import com.customdiscs.DiscMod;
import com.customdiscs.registry.ModBlocks;
import com.simibubi.create.api.behaviour.display.DisplayTarget;

/**
 * Hooks into Create's Display Link system so the Train Speaker block
 * can receive station/schedule info directly from a Train Station (or any other source).
 *
 * <p>This class is ONLY loaded when Create is present on the classpath.
 * All Create imports are confined here to avoid ClassNotFoundExceptions.</p>
 */
public class CreateCompat {
    public static void register() {
        DisplayTarget.BY_BLOCK.register(ModBlocks.TRAIN_SPEAKER.get(), new TrainSpeakerDisplayTarget());
        DiscMod.LOGGER.info("[TrainSpeaker] Create Display Link target registered.");
    }
}
