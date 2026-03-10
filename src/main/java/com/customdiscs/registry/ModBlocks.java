package com.customdiscs.registry;

import com.customdiscs.DiscMod;
import com.customdiscs.block.DiscRecorderBlock;
import com.customdiscs.block.TrainSpeakerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, DiscMod.MOD_ID);

    public static final RegistryObject<Block> DISC_RECORDER = BLOCKS.register("disc_recorder",
            () -> new DiscRecorderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> TRAIN_SPEAKER = BLOCKS.register("train_speaker",
            () -> new TrainSpeakerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));
}
