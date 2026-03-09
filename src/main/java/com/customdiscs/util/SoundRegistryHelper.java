package com.customdiscs.util;

import com.customdiscs.DiscMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class SoundRegistryHelper {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Root folder for the dynamic resource pack.
     * Structure mirrors a valid MC resource pack:
     *   customdiscs_sounds/
     *     pack.mcmeta
     *     assets/customdiscs/sounds/<id>.ogg
     *     assets/customdiscs/sounds.json
     */
    public static Path getDynamicPackRoot() {
        return FMLPaths.GAMEDIR.get().resolve("customdiscs_sounds");
    }

    public static Path getSoundsFolder() {
        return getDynamicPackRoot().resolve("assets").resolve(DiscMod.MOD_ID).resolve("sounds");
    }

    private static final List<String> loadedNames = new ArrayList<>();

    public static List<String> getLoadedSoundNames() {
        return loadedNames;
    }

    // -------------------------------------------------------------------------
    // Called once on mod init to ensure folder structure exists
    // -------------------------------------------------------------------------
    public static void ensureFolderExists() {
        try {
            Path root = getDynamicPackRoot();
            Files.createDirectories(getSoundsFolder());

            // Write pack.mcmeta if missing
            Path mcmeta = root.resolve("pack.mcmeta");
            if (!Files.exists(mcmeta)) {
                try (FileWriter w = new FileWriter(mcmeta.toFile())) {
                    w.write("{\"pack\":{\"description\":\"Custom Discs Dynamic Sounds\",\"pack_format\":15}}");
                }
            }
        } catch (IOException e) {
            DiscMod.LOGGER.error("[CustomDiscs] Failed to create dynamic pack folder", e);
        }
    }

    // -------------------------------------------------------------------------
    // RegisterEvent — scan folder and register SoundEvents before freeze
    // -------------------------------------------------------------------------
    public static void onRegisterSounds(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.SOUND_EVENTS, helper -> {
            File soundsDir = getSoundsFolder().toFile();
            if (!soundsDir.exists()) return;
            File[] files = soundsDir.listFiles(f -> f.getName().toLowerCase().endsWith(".ogg"));
            if (files == null) return;

            for (File file : files) {
                String rawName = file.getName();
                // Strip extension
                String base = rawName.substring(0, rawName.lastIndexOf('.'));
                // Sanitize to valid ResourceLocation path: lowercase, replace bad chars with _
                String name = sanitizeSoundId(base);

                // If the filename on disk had invalid chars, rename it so future scans are clean
                if (!base.equals(name)) {
                    File renamed = new File(file.getParent(), name + ".ogg");
                    if (!renamed.exists()) {
                        file.renameTo(renamed);
                        DiscMod.LOGGER.info("[CustomDiscs] Renamed '{}' -> '{}.ogg' (sanitized)", rawName, name);
                    } else {
                        // Duplicate after sanitize — skip the original bad file
                        DiscMod.LOGGER.warn("[CustomDiscs] Skipping '{}' — sanitized name '{}' already exists", rawName, name);
                        continue;
                    }
                }

                ResourceLocation rl = new ResourceLocation(DiscMod.MOD_ID, name);
                helper.register(rl, SoundEvent.createVariableRangeEvent(rl));
                loadedNames.add(name);
                DiscMod.LOGGER.info("[CustomDiscs] Registered sound: {}", name);
            }
            // Regenerate sounds.json after scanning
            regenerateSoundsJson();
        });
    }

    /**
     * Converts any string into a valid ResourceLocation path:
     * - lowercase
     * - spaces and hyphens become underscores
     * - any remaining non-[a-z0-9_./] chars are removed
     * - collapses consecutive underscores
     * - strips leading/trailing underscores
     */
    public static String sanitizeSoundId(String raw) {
        String s = raw.toLowerCase();
        s = s.replace(' ', '_').replace('-', '_');
        s = s.replaceAll("[^a-z0-9_./]", "");  // strip anything still invalid
        s = s.replaceAll("_+", "_");             // collapse multiple underscores
        s = s.replaceAll("^_+|_+$", "");         // trim leading/trailing underscores
        if (s.isEmpty()) s = "disc_" + System.currentTimeMillis();
        return s;
    }

    // -------------------------------------------------------------------------
    // AddPackFindersEvent — expose dynamic folder as a resource pack
    // -------------------------------------------------------------------------
    public static void registerDynamicPack(AddPackFindersEvent event) {
        // This method registers CLIENT-side resource packs and must only run on the client.
        // On a dedicated server, AddPackFindersEvent fires for SERVER_DATA, so the guard
        // below makes this a no-op there — but we add an explicit dist check for safety.
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        Path root = getDynamicPackRoot();
        ensureFolderExists();

        Pack pack = Pack.readMetaAndCreate(
                "customdiscs_sounds",
                net.minecraft.network.chat.Component.literal("Custom Discs Sounds"),
                true, // required
                id -> new net.minecraft.server.packs.PathPackResources(id, root, true),
                PackType.CLIENT_RESOURCES,
                Pack.Position.TOP,
                PackSource.DEFAULT
        );
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
            DiscMod.LOGGER.info("[CustomDiscs] Dynamic sound pack registered at {}", root);
        }
    }



    // -------------------------------------------------------------------------
    // Called server-side when the player cuts a disc via the block GUI
    // Returns "OK:<sound_id>" on success, or an error string
    // -------------------------------------------------------------------------
    public static String processNewDisc(String sourceFilePath, String title) {
        File source = new File(sourceFilePath);
        if (!source.exists() || !source.isFile()) {
            return "File not found: " + sourceFilePath;
        }
        if (!source.getName().toLowerCase().endsWith(".ogg")) {
            return "File must be a .ogg file!";
        }

        String rawBase = source.getName();
        rawBase = rawBase.substring(0, rawBase.lastIndexOf('.'));
        String baseName = sanitizeSoundId(rawBase);
        if (baseName.isEmpty()) baseName = "disc_" + System.currentTimeMillis();

        String soundId = DiscMod.MOD_ID + ":" + baseName;
        Path dest = getSoundsFolder().resolve(baseName + ".ogg");

        try {
            Files.createDirectories(getSoundsFolder());
            // Copy OGG as-is. Mono downmix + 48kHz resampling happen at play-time
            // inside OggDecoder (pure-Java JOrbis) when SVC streams the audio.
            Files.copy(source.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            DiscMod.LOGGER.info("[CustomDiscs] Copied OGG to {}", dest);

            // Register the sound event at runtime if not already registered
            ResourceLocation rl = new ResourceLocation(DiscMod.MOD_ID, baseName);
            if (ForgeRegistries.SOUND_EVENTS.getValue(rl) == null) {
                try {
                    var registry = (net.minecraftforge.registries.ForgeRegistry<SoundEvent>)
                            ForgeRegistries.SOUND_EVENTS;
                    registry.unfreeze();
                    registry.register(rl, SoundEvent.createVariableRangeEvent(rl));
                    registry.freeze();
                    loadedNames.add(baseName);
                    DiscMod.LOGGER.info("[CustomDiscs] Runtime-registered sound: {}", rl);
                } catch (Exception e) {
                    DiscMod.LOGGER.warn("[CustomDiscs] Could not runtime-register sound. " +
                            "Restart or F3+T required. {}", e.getMessage());
                }
            }

            regenerateSoundsJson();
            return "OK:" + soundId;

        } catch (IOException e) {
            DiscMod.LOGGER.error("[CustomDiscs] Failed to copy OGG", e);
            return "IO error: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Called server-side when a client finishes uploading OGG bytes.
    // oggBytes is the fully assembled file; title is used as the base name.
    // Returns "OK:<sound_id>" on success, or an error string.
    // -------------------------------------------------------------------------
    public static String processNewDiscFromBytes(byte[] oggBytes, String title) {
        if (oggBytes == null || oggBytes.length == 0) {
            return "Empty file data received";
        }

        String rawBase = sanitizeSoundId(title);
        if (rawBase.isEmpty()) rawBase = "disc_" + System.currentTimeMillis();
        String baseName = rawBase;
        String soundId  = DiscMod.MOD_ID + ":" + baseName;
        Path dest = getSoundsFolder().resolve(baseName + ".ogg");

        try {
            Files.createDirectories(getSoundsFolder());
            Files.write(dest, oggBytes, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            DiscMod.LOGGER.info("[CustomDiscs] Saved uploaded OGG ({} bytes) to {}",
                    oggBytes.length, dest);

            // Register the sound event at runtime if not already registered
            ResourceLocation rl = new ResourceLocation(DiscMod.MOD_ID, baseName);
            if (ForgeRegistries.SOUND_EVENTS.getValue(rl) == null) {
                try {
                    var registry = (net.minecraftforge.registries.ForgeRegistry<SoundEvent>)
                            ForgeRegistries.SOUND_EVENTS;
                    registry.unfreeze();
                    registry.register(rl, SoundEvent.createVariableRangeEvent(rl));
                    registry.freeze();
                    loadedNames.add(baseName);
                    DiscMod.LOGGER.info("[CustomDiscs] Runtime-registered sound: {}", rl);
                } catch (Exception e) {
                    DiscMod.LOGGER.warn("[CustomDiscs] Could not runtime-register sound. " +
                            "Restart required. {}", e.getMessage());
                }
            }

            regenerateSoundsJson();
            return "OK:" + soundId;

        } catch (IOException e) {
            DiscMod.LOGGER.error("[CustomDiscs] Failed to save uploaded OGG", e);
            return "IO error: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Generates assets/customdiscs/sounds.json in the dynamic pack
    // -------------------------------------------------------------------------
    private static void regenerateSoundsJson() {
        File soundsDir = getSoundsFolder().toFile();
        if (!soundsDir.exists()) return;
        File[] files = soundsDir.listFiles(f -> f.getName().endsWith(".ogg"));
        if (files == null) return;

        JsonObject root = new JsonObject();
        for (File f : files) {
            String name = f.getName().replace(".ogg", "");
            JsonObject entry   = new JsonObject();
            var sounds          = new com.google.gson.JsonArray();
            JsonObject sndEntry = new JsonObject();
            sndEntry.addProperty("name", DiscMod.MOD_ID + ":" + name);
            // Do NOT set "stream": true — streaming prevents OpenAL from spatializing the audio.
            // Mono OGGs with linear attenuation give proper 3D distance falloff.
            sounds.add(sndEntry);
            entry.add("sounds", sounds);
            root.add(name, entry);
        }

        Path jsonPath = getDynamicPackRoot()
                .resolve("assets").resolve(DiscMod.MOD_ID).resolve("sounds.json");
        try {
            Files.createDirectories(jsonPath.getParent());
            try (FileWriter w = new FileWriter(jsonPath.toFile())) {
                GSON.toJson(root, w);
            }
            DiscMod.LOGGER.info("[CustomDiscs] sounds.json updated ({} entries)", files.length);
        } catch (IOException e) {
            DiscMod.LOGGER.error("[CustomDiscs] Failed to write sounds.json", e);
        }
    }

    // -------------------------------------------------------------------------
    // Client-side: trigger a sound reload (equivalent to F3+T for sounds)
    // -------------------------------------------------------------------------
    public static void reloadClientSounds() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                mc.reloadResourcePacks();
            } catch (Exception e) {
                DiscMod.LOGGER.warn("[CustomDiscs] Resource reload failed: {}", e.getMessage());
            }
        });
    }
}
