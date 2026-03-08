package com.customdiscs.ponder;

import com.customdiscs.DiscMod;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers Ponder scenes for the Custom Discs mod.
 * Hover any of the mod's items / blocks and press W (the default Ponder key)
 * to open the interactive tutorial.
 */
public class DiscPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return DiscMod.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {

        // ── Crafting chain (unassembled disc → blank disc) ───────────────────
        // Shown when the player ponders either crafting ingredient/result
        helper.forComponents(
                new ResourceLocation("customdiscs", "unassembled_disc"),
                new ResourceLocation("customdiscs", "custom_disc")
        ).addStoryBoard("disc_crafting", DiscPonderScenes::discCrafting);

        // ── Using the Disc Recorder ───────────────────────────────────────────
        // Shown on the block and on the finished custom disc
        helper.forComponents(
                new ResourceLocation("customdiscs", "disc_recorder"),
                new ResourceLocation("customdiscs", "custom_disc")
        ).addStoryBoard("disc_recording", DiscPonderScenes::discRecording);
    }
}
