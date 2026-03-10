package com.customdiscs.client;

import com.customdiscs.DiscMod;
import io.github.whitemagic2014.tts.TTS;
import io.github.whitemagic2014.tts.TTSVoice;
import io.github.whitemagic2014.tts.bean.Voice;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.openal.AL10;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-only helper that converts text to speech via Microsoft Edge TTS
 * (free, no API key, neural voices) and plays the result through OpenAL.
 *
 * The library (tts-edge-java) outputs MP3, which we decode to raw PCM using the
 * Java Sound SPI (Java 21 ships with mp3plugin.jar on most JVMs; Minecraft's JVM
 * also includes it). The decoded PCM is fed directly into an OpenAL buffer.
 *
 * Voice can be changed in {@link #VOICE_NAME}. Any voice listed at
 * https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list works.
 */
@OnlyIn(Dist.CLIENT)
public class EdgeTtsClient {

    /** Neural English voice — clear, natural, announcer-style. */
    private static final String VOICE_NAME = "en-US-AriaNeural";

    /** Single-thread pool so announcements are serialised and never overlap during synthesis. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "customdiscs-tts");
        t.setDaemon(true);
        return t;
    });

    /** Tracks the OpenAL source currently playing so we can stop it before a new one starts. */
    private static final AtomicInteger currentSource = new AtomicInteger(-1);

    /** Call this from the Minecraft main thread — synthesis runs on a background thread. */
    public static void speak(String text) {
        EXECUTOR.submit(() -> {
            try {
                synthesiseAndPlay(text);
            } catch (Exception ex) {
                DiscMod.LOGGER.warn("[TrainSpeaker] Edge TTS error: {}", ex.getMessage());
            }
        });
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static void synthesiseAndPlay(String text) throws Exception {
        // Resolve voice
        Optional<Voice> voiceOpt = TTSVoice.provides().stream()
                .filter(v -> VOICE_NAME.equals(v.getShortName()))
                .findFirst();
        if (voiceOpt.isEmpty()) {
            DiscMod.LOGGER.warn("[TrainSpeaker] Voice '{}' not found; skipping TTS.", VOICE_NAME);
            return;
        }

        // Call Edge TTS — returns MP3 bytes
        ByteArrayOutputStream mp3Out = new TTS(voiceOpt.get(), text)
                .isRateLimited(true)
                .formatMp3()
                .transToAudioStream();

        if (mp3Out == null || mp3Out.size() == 0) {
            DiscMod.LOGGER.warn("[TrainSpeaker] Edge TTS returned empty audio.");
            return;
        }

        // Decode MP3 → PCM_SIGNED 16-bit via Java Sound
        byte[] mp3Bytes = mp3Out.toByteArray();
        byte[] pcmBytes;
        int channels;
        int sampleRate;

        try (var mp3In = javax.sound.sampled.AudioSystem.getAudioInputStream(
                new ByteArrayInputStream(mp3Bytes))) {
            var srcFmt  = mp3In.getFormat();
            channels    = srcFmt.getChannels();
            sampleRate  = (int) srcFmt.getSampleRate();
            var pcmFmt  = new javax.sound.sampled.AudioFormat(
                    javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate, 16, channels, channels * 2, sampleRate, false);
            try (var pcmIn = javax.sound.sampled.AudioSystem.getAudioInputStream(pcmFmt, mp3In)) {
                pcmBytes = pcmIn.readAllBytes();
            }
        }

        playPcm(pcmBytes, channels, sampleRate);
    }

    private static void playPcm(byte[] pcmBytes, int channels, int sampleRate) {
        // Stop any currently playing announcement
        int old = currentSource.get();
        if (old != -1) {
            try { AL10.alSourceStop(old); } catch (Exception ignored) {}
        }

        int buffer = AL10.alGenBuffers();
        int source = AL10.alGenSources();
        currentSource.set(source);

        try {
            int alFormat = (channels == 1) ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            ByteBuffer data = ByteBuffer.allocateDirect(pcmBytes.length).order(ByteOrder.nativeOrder());
            data.put(pcmBytes).flip();

            AL10.alBufferData(buffer, alFormat, data, sampleRate);
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcef(source, AL10.AL_GAIN, 1.0f);
            AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE); // non-positional
            AL10.alSourcePlay(source);

            // Wait for playback to complete
            int state;
            do {
                Thread.sleep(50);
                state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            } while (state == AL10.AL_PLAYING);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            currentSource.compareAndSet(source, -1);
            AL10.alDeleteSources(source);
            AL10.alDeleteBuffers(buffer);
        }
    }
}
