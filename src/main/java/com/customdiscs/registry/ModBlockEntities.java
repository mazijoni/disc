package com.customdiscs.registry;

import com.customdiscs.DiscMod;
import com.customdiscs.block.DiscRecorderBlockEntity;
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
}
