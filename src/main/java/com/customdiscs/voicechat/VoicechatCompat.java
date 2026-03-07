package com.customdiscs.voicechat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;

import java.io.File;

/**
 * Safe wrapper around JukeboxAudioManager.
 *
 * All calls to SVC classes are isolated here so the rest of the mod
 * never touches voicechat-api classes if the mod is absent at runtime.
 * Use VoicechatCompat.isLoaded() before calling play/stop.
 */
public class VoicechatCompat {

    private static Boolean loaded = null;

    /** Returns true if Simple Voice Chat is present in the mod list. */
    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("voicechat");
        }
        return loaded;
    }

    public static void play(ServerLevel level, BlockPos pos, File oggFile) {
        if (!isLoaded()) return;
        JukeboxAudioManager.play(level, pos, oggFile);
    }

    public static void stop(BlockPos pos) {
        if (!isLoaded()) return;
        JukeboxAudioManager.stop(pos);
    }
}
