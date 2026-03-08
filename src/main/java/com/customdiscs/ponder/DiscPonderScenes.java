package com.customdiscs.ponder;

import com.customdiscs.registry.ModBlocks;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Ponder storyboard scenes for the Custom Discs mod.
 *
 * All blocks are placed dynamically with scene.world().setBlock() so the NBT
 * schematic files can be minimal empty shells.
 *
 * Press W (default Ponder key) while hovering any of the mod's items or blocks.
 */
public class DiscPonderScenes {

    // ─── Scene 1: Crafting chain ──────────────────────────────────────────────

    /**
     * Explains how to go from raw materials to a blank custom disc.
     * Registered for: unassembled_disc, custom_disc
     */
    public static void discCrafting(SceneBuilder scene, SceneBuildingUtil util) {

        scene.title("disc_crafting", "Crafting a Blank Custom Disc");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        // ── Step 1: Show crafting table ────────────────────────────────────────
        scene.world().setBlock(util.grid().at(2, 1, 2),
                Blocks.CRAFTING_TABLE.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("Combine a Brass Sheet and a Sturdy Sheet in a crafting table")
                .pointAt(util.vector().topOf(2, 1, 2))
                .attachKeyFrame();
        scene.idle(80);

        // ── Step 2: Explain unassembled disc ──────────────────────────────────
        scene.overlay().showText(70)
                .text("The result is an Unassembled Disc — it needs to be pressed into shape")
                .pointAt(util.vector().topOf(2, 1, 2))
                .attachKeyFrame();
        scene.idle(80);

        // ── Step 3: Show mechanical press setup ───────────────────────────────
        // Look up Create blocks at runtime so we don't need Create as a compile dep.
        // Falls back to vanilla stand-ins if Create isn't installed.
        Block depot = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("create", "depot"));
        Block press = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("create", "mechanical_press"));
        if (depot == null || depot == Blocks.AIR) depot = Blocks.IRON_BLOCK;
        if (press == null || press == Blocks.AIR) press = Blocks.ANVIL;

        scene.world().setBlock(util.grid().at(2, 1, 2), depot.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 3, 2), press.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 2, 3, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("Place the Unassembled Disc under a Create Mechanical Press and power it")
                .pointAt(util.vector().topOf(2, 2, 2))
                .attachKeyFrame();
        scene.idle(80);

        // ── Step 4: Blank disc ────────────────────────────────────────────────
        scene.overlay().showText(70)
                .text("The press shapes it into a blank Custom Disc — ready to be encoded with music")
                .pointAt(util.vector().topOf(2, 1, 2))
                .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }

    // ─── Scene 2: Recording music ─────────────────────────────────────────────

    /**
     * Explains how to use the Disc Recorder to encode audio onto a blank disc.
     * Registered for: disc_recorder, custom_disc
     */
    public static void discRecording(SceneBuilder scene, SceneBuildingUtil util) {

        scene.title("disc_recording", "Recording Music with the Disc Recorder");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        // ── Step 1: Place the disc recorder ───────────────────────────────────
        scene.world().setBlock(util.grid().at(2, 1, 2),
                ModBlocks.DISC_RECORDER.get().defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(60)
                .text("Place a Disc Recorder and right-click to open its interface")
                .pointAt(util.vector().topOf(2, 1, 2))
                .attachKeyFrame();
        scene.idle(70);

        // ── Step 2: Blank disc requirement ────────────────────────────────────
        scene.overlay().showText(70)
                .text("Make sure you have a blank Custom Disc in your inventory before recording")
                .pointAt(util.vector().topOf(2, 1, 2))
                .attachKeyFrame();
        scene.idle(80);

        // ── Step 3: OGG file ──────────────────────────────────────────────────
        scene.overlay().showText(70)
                .text("Enter the path to an OGG audio file on your computer, or use the Browse button")
                .pointAt(util.vector().topOf(2, 1, 2))
                .attachKeyFrame();
        scene.idle(80);

        // ── Step 4: Title and cut ──────────────────────────────────────────────
        scene.overlay().showText(70)
                .text("Give the track a title, optionally adjust volume, then click Cut Disc")
                .pointAt(util.vector().topOf(2, 1, 2))
                .attachKeyFrame();
        scene.idle(80);

        // ── Step 5: Play in jukebox ───────────────────────────────────────────
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.AIR.defaultBlockState(), false); // remove from old spot
        scene.world().setBlock(util.grid().at(1, 1, 2), Blocks.JUKEBOX.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), ModBlocks.DISC_RECORDER.get().defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Insert the encoded disc into a Jukebox — music plays with 3D proximity audio via Simple Voice Chat!")
                .pointAt(util.vector().topOf(1, 1, 2))
                .attachKeyFrame();
        scene.idle(90);

        scene.markAsFinished();
    }
}
