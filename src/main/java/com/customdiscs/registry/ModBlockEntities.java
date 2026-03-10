package com.customdiscs.registry;

import com.customdiscs.DiscMod;
import com.customdiscs.block.DiscRecorderBlockEntity;
import com.customdiscs.block.TrainSpeakerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, DiscMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<DiscRecorderBlockEntity>> DISC_RECORDER_BE =
            BLOCK_ENTITIES.register("disc_recorder",
                    () -> BlockEntityType.Builder
                            .of(DiscRecorderBlockEntity::new, ModBlocks.DISC_RECORDER.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<TrainSpeakerBlockEntity>> TRAIN_SPEAKER_BE =
            BLOCK_ENTITIES.register("train_speaker",
                    () -> BlockEntityType.Builder
                            .of(TrainSpeakerBlockEntity::new, ModBlocks.TRAIN_SPEAKER.get())
                            .build(null));
}
