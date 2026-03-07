package com.customdiscs.registry;

import com.customdiscs.DiscMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, DiscMod.MOD_ID);

    // Placeholder sound — custom discs intercept jukebox play themselves
    public static final RegistryObject<SoundEvent> PLACEHOLDER =
            SOUNDS.register("placeholder",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(DiscMod.MOD_ID, "placeholder")));
}
