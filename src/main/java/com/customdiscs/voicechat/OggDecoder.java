package com.customdiscs.voicechat;

import com.customdiscs.DiscMod;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.Packet;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java OGG Vorbis decoder using JOrbis (bundled in the mod JAR).
 *
 * Decodes any OGG Vorbis file to 48 kHz 16-bit mono PCM (short[]),
 * which is the format required by Simple Voice Chat's AudioPlayer API.
 *
 * Features:
 *  - Multi-channel files are downmixed to mono (average of all channels)
 *  - Non-48kHz files are resampled to 48000 Hz via linear interpolation
 *  - No external tools (ffmpeg etc.) required — fully self-contained
 */
public class OggDecoder {

    private static final int TARGET_RATE = 48000;

    /**
     * Decodes {@code oggFile} to 48 kHz mono PCM.
     *
     * @return short[] with 48kHz mono samples, or null on failure.
     */
    @Nullable
    public static short[] decode(File oggFile) {
        byte[] data;
        try {
            data = Files.readAllBytes(oggFile.toPath());
        } catch (IOException e) {
            DiscMod.LOGGER.error("[CustomDiscs] OggDecoder: cannot read file {}", oggFile, e);
            return null;
        }

        SyncState   oy = new SyncState();
        StreamState os = new StreamState();
        Page        og = new Page();
        Packet      op = new Packet();
        Info        vi = new Info();
        Comment     vc = new Comment();
        DspState    vd = new DspState();
        Block       vb;

        oy.init();

        // Feed the entire file into the OGG sync layer at once
        int idx = oy.buffer(data.length);
        System.arraycopy(data, 0, oy.data, idx, data.length);
        oy.wrote(data.length);

        // ── Read the three Vorbis header packets ─────────────────────────────
        if (oy.pageout(og) != 1) {
            DiscMod.LOGGER.warn("[CustomDiscs] OggDecoder: no OGG pages in {}", oggFile.getName());
            return null;
        }

        os.init(og.serialno());
        os.reset();
        vi.init();
        vc.init();

        if (os.pagein(og) < 0 || os.packetout(op) != 1
                || vi.synthesis_headerin(vc, op) < 0) {
            DiscMod.LOGGER.warn("[CustomDiscs] OggDecoder: not a valid Vorbis stream in {}", oggFile.getName());
            return null;
        }

        // Vorbis uses 3 header packets in total; read the remaining 2
        int headersLeft = 2;
        headerLoop:
        while (headersLeft > 0) {
            // First try to drain another packet from the current page
            int pr = os.packetout(op);
            if (pr > 0) {
                vi.synthesis_headerin(vc, op);
                headersLeft--;
                continue;
            }
            // Otherwise pull in another OGG page
            int pgr = oy.pageout(og);
            if (pgr == 0) {
                // We've fed all data already — if headers are incomplete the file is broken
                DiscMod.LOGGER.warn("[CustomDiscs] OggDecoder: truncated headers in {}", oggFile.getName());
                break headerLoop;
            }
            if (pgr > 0) os.pagein(og);
        }

        if (headersLeft > 0) return null;

        // ── Initialise the DSP decoder ────────────────────────────────────────
        vd.synthesis_init(vi);
        vb = new Block(vd);

        int channels  = vi.channels;
        int sourceRate = vi.rate;

        List<short[]> chunks      = new ArrayList<>();
        int           totalFrames = 0;

        // ── Main decode loop ─────────────────────────────────────────────────
        outerLoop:
        while (true) {
            int res = oy.pageout(og);
            if (res == 0) break;      // exhausted all data we fed
            if (res < 0) continue;   // corrupt page — skip

            os.pagein(og);

            while (true) {
                int pr = os.packetout(op);
                if (pr == 0) break;  // need another page
                if (pr < 0) continue; // bad packet — skip

                if (vb.synthesis(op) == 0) {
                    vd.synthesis_blockin(vb);
                }

                float[][][] pcmRaw   = new float[1][][];
                int[]       offsets  = new int[channels];
                int         samples;

                while ((samples = vd.synthesis_pcmout(pcmRaw, offsets)) > 0) {
                    float[][] ch = pcmRaw[0];
                    short[]   chunk = new short[samples];

                    for (int i = 0; i < samples; i++) {
                        // Downmix: average all channels
                        float s = 0f;
                        for (int c = 0; c < channels; c++) {
                            s += ch[c][offsets[c] + i];
                        }
                        s /= channels;

                        // Clamp to [-1, 1] then convert to 16-bit signed
                        if      (s >  1f) s =  1f;
                        else if (s < -1f) s = -1f;
                        chunk[i] = (short) (s * 32767f);
                    }

                    chunks.add(chunk);
                    totalFrames += samples;
                    vd.synthesis_read(samples);
                }
            }

            if (og.eos() != 0) break outerLoop;
        }

        if (totalFrames == 0) {
            DiscMod.LOGGER.warn("[CustomDiscs] OggDecoder: decoded 0 samples from {}", oggFile.getName());
            return null;
        }

        // ── Concatenate chunks ────────────────────────────────────────────────
        short[] pcm = new short[totalFrames];
        int pos = 0;
        for (short[] c : chunks) {
            System.arraycopy(c, 0, pcm, pos, c.length);
            pos += c.length;
        }

        // ── Resample to 48 kHz if the file uses a different rate ─────────────
        if (sourceRate != TARGET_RATE) {
            DiscMod.LOGGER.debug("[CustomDiscs] OggDecoder: resampling {} Hz → {} Hz for {}",
                    sourceRate, TARGET_RATE, oggFile.getName());
            pcm = resampleLinear(pcm, sourceRate, TARGET_RATE);
        }

        DiscMod.LOGGER.debug("[CustomDiscs] OggDecoder: decoded {} → {} samples ({} ch, {} Hz → 48kHz mono)",
                oggFile.getName(), pcm.length, channels, sourceRate);
        return pcm;
    }

    /**
     * Linear-interpolation resampler.
     * Accurate enough for game audio; no dependencies required.
     */
    private static short[] resampleLinear(short[] input, int srcRate, int dstRate) {
        if (srcRate == dstRate) return input;
        double ratio   = (double) srcRate / dstRate;
        int    outLen  = (int) Math.ceil(input.length / ratio);
        short[] output = new short[outLen];

        for (int i = 0; i < outLen; i++) {
            double srcPos = i * ratio;
            int    lo     = (int) srcPos;
            double frac   = srcPos - lo;
            short  s0     = input[lo];
            short  s1     = (lo + 1 < input.length) ? input[lo + 1] : s0;
            output[i] = (short) (s0 + frac * (s1 - s0));
        }
        return output;
    }
}
