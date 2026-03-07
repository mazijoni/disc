package com.customdiscs.voicechat;

import com.customdiscs.DiscMod;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages one LocationalAudioChannel + AudioPlayer per jukebox position.
 *
 * The SVC API expects audio as short[] at 48 kHz, 16-bit mono PCM.
 * We decode the OGG using the pure-Java OggDecoder (JOrbis) bundled in the
 * mod JAR — no external tools (ffmpeg etc.) required.
 *
 * All public methods are called from the server thread unless noted.
 */
public class JukeboxAudioManager {

    /**
     * Master multiplier applied on top of each disc's own volume setting.
     * 1.0 = no additional reduction. Lower if everything still feels too loud.
     */
    private static final float MASTER_VOLUME = 1.0f;

    @Nullable
    private static VoicechatServerApi api;

    /** Active players keyed by jukebox block position (packed as long). */
    private static final Map<Long, AudioPlayer> activePlayers = new ConcurrentHashMap<>();

    public static void setApi(@Nullable VoicechatServerApi serverApi) {
        api = serverApi;
    }

    /** Returns true when SVC is loaded and the server API is available. */
    public static boolean isAvailable() {
        return api != null;
    }

    /**
     * Starts playing {@code oggFile} through a LocationalAudioChannel centred on {@code pos}.
     * Stops any existing channel at that position first.
     *
     * @param level      the server level containing the jukebox
     * @param pos        jukebox block position
     * @param oggFile    OGG file from the dynamic resource pack
     * @param discVolume per-disc volume from NBT (0.0–1.0)
     */
    public static void play(ServerLevel level, BlockPos pos, File oggFile, float discVolume) {
        if (api == null) return;

        stop(pos); // stop any existing player at this position first

        UUID channelId = UUID.randomUUID();
        LocationalAudioChannel channel = api.createLocationalAudioChannel(
                channelId,
                api.fromServerLevel(level),
                api.createPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
        );
        if (channel == null) {
            DiscMod.LOGGER.warn("[CustomDiscs] SVC: could not create locational channel at {}", pos);
            return;
        }

        // Audible range: 65 blocks (matches vanilla jukebox)
        channel.setDistance(65F);

        // Decode OGG → PCM on a background thread so we don't block the server tick
        Thread decoderThread = new Thread(() -> {
            try {
                short[] pcm = OggDecoder.decode(oggFile);
                if (pcm == null || pcm.length == 0) {
                    DiscMod.LOGGER.warn("[CustomDiscs] SVC: decoded 0 samples from {}", oggFile.getName());
                    return;
                }
                applyVolume(pcm, discVolume * MASTER_VOLUME);

                AudioPlayer player = api.createAudioPlayer(channel, api.createEncoder(), pcm);
                activePlayers.put(pos.asLong(), player);
                player.setOnStopped(() -> activePlayers.remove(pos.asLong()));
                player.startPlaying();

                DiscMod.LOGGER.debug("[CustomDiscs] SVC: playing {} ({} samples) at {}",
                        oggFile.getName(), pcm.length, pos);
            } catch (Exception e) {
                DiscMod.LOGGER.error("[CustomDiscs] SVC: failed to start audio for {}", oggFile.getName(), e);
            }
        }, "CustomDiscs-SVC-Decoder");
        decoderThread.setDaemon(true);
        decoderThread.start();
    }

    /** Stops any active audio channel at the given jukebox position. */
    public static void stop(BlockPos pos) {
        AudioPlayer player = activePlayers.remove(pos.asLong());
        if (player != null && player.isPlaying()) {
            player.stopPlaying();
            DiscMod.LOGGER.debug("[CustomDiscs] SVC: stopped audio at {}", pos);
        }
    }

    /** Stops all active channels — called on server stop. */
    public static void clearAll() {
        activePlayers.values().forEach(p -> { if (p.isPlaying()) p.stopPlaying(); });
        activePlayers.clear();
    }

    /** Scales every sample by {@code scalar} in-place. */
    private static void applyVolume(short[] pcm, float scalar) {
        scalar = Math.max(0f, Math.min(1f, scalar)); // clamp just in case
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (pcm[i] * scalar);
        }
    }
}
