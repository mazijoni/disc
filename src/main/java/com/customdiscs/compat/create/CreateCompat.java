package com.customdiscs.compat.create;

import com.customdiscs.DiscMod;
import com.customdiscs.registry.ModBlocks;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;

/**
 * Hooks into Create's contraption system so the Train Speaker block
 * can function when assembled onto a train.
 *
 * <p>This class is ONLY loaded when Create is present on the classpath.
 * All Create imports are confined here to avoid ClassNotFoundExceptions.</p>
 */
public class CreateCompat {
    public static void register() {
        // Register as a MovementBehaviour so the block ticks while on a moving train
        MovementBehaviour.REGISTRY.register(ModBlocks.TRAIN_SPEAKER.get(), new TrainSpeakerMovement());
        DiscMod.LOGGER.info("[TrainSpeaker] Create integration registered (MovementBehaviour).");
    }
}
