package com.openggf.tools;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioNativePcmCaptureAccess;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.NativeCompatiblePcmOutput;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * {@code GameplayCaptureTool --native-pcm}: records the production session's
 * opt-in native-compatible PCM stage
 * ({@link NativeCompatiblePcmOutput}) and the ordered logical chip writes of
 * each presented frame, for comparison with BizHawk's own final PCM and
 * per-service write ledgers. The stage's settings come from the BK2's own
 * {@code SyncSettings.json}; only BizHawk's {@code Nuked_YM2612} FM core has an
 * engine output path, so any other recorded core is refused, never
 * substituted. Diagnostics only: nothing here feeds gameplay or ordinary audio.
 *
 * <p>Outputs: {@code native-pcm.s16} (interleaved stereo s16 LE, 44.1 kHz, NTSC
 * frames in the order the stage completes them), {@code native-pcm-frames.tsv}
 * (presented frame, stereo frames drained after it, cumulative, presentation
 * sessions built with the stage) and {@code chip-writes.jsonl} (per presented
 * frame, the logical YM/PSG writes in order). Originating task: round-2 lane
 * pcm-output, 2026-10-16.
 */
final class NativePcmCapture implements AutoCloseable {

    private static final Pattern FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*(-?[0-9.]+)");
    /** BizHawk {@code GenesisFMSoundChipType.Nuked_YM2612} (LibGPGX.cs; MAME_YM2612 = 0 .. Nuked_YM3438 = 4). */
    private static final int NUKED_YM2612 = 3;

    private final AudioManager audio;
    private final OutputStream pcm;
    private final BufferedWriter frames;
    private final BufferedWriter writes;
    private final StringBuilder frameWrites = new StringBuilder();
    private final short[] drain = new short[2 * 4096];
    private long cumulative;

    NativePcmCapture(Path outDir, AudioManager audio) throws IOException {
        this.audio = audio;
        pcm = Files.newOutputStream(outDir.resolve("native-pcm.s16"));
        frames = Files.newBufferedWriter(outDir.resolve("native-pcm-frames.tsv"), StandardCharsets.UTF_8);
        writes = Files.newBufferedWriter(outDir.resolve("chip-writes.jsonl"), StandardCharsets.UTF_8);
        frames.write("frame\tstereo_frames\tcumulative_stereo_frames\tsessions\n");
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                separate();
                frameWrites.append("[\"ym\",").append(port).append(',').append(register).append(',')
                        .append(value).append(']');
            }

            @Override
            public void onPsgWrite(int value) {
                separate();
                frameWrites.append("[\"psg\",").append(value).append(']');
            }
        });
    }

    private void separate() {
        if (!frameWrites.isEmpty()) {
            frameWrites.append(',');
        }
    }

    /** Drains every completed native-compatible frame after presented frame {@code frame}. */
    void endFrame(int frame) throws IOException {
        int drained = 0;
        int count;
        while ((count = AudioNativePcmCaptureAccess.drain(audio, drain, 0, drain.length / 2)) > 0) {
            for (int i = 0; i < count * 2; i++) {
                pcm.write(drain[i] & 255);
                pcm.write((drain[i] >>> 8) & 255);
            }
            drained += count;
        }
        cumulative += drained;
        frames.write(frame + "\t" + drained + "\t" + cumulative + "\t" + AudioNativePcmCaptureAccess.sessions(audio) + "\n");
        writes.write("{\"frame\":" + frame + ",\"writes\":[" + frameWrites + "]}\n");
        frameWrites.setLength(0);
    }

    @Override
    public void close() throws IOException {
        audio.setChipWriteObserver(null);
        // Later presentation sessions in this process return to the default of no stage.
        AudioNativePcmCaptureAccess.enable(audio, null);
        try (pcm; frames; writes) {
            pcm.flush();
        }
    }

    /**
     * The stage configuration a BK2's recorded SyncSettings select for {@code rom}.
     * The stage models NTSC timing only, so the BK2's region must resolve to an
     * NTSC console and the engine must not be configured for PAL.
     */
    static NativeCompatiblePcmOutput.Config configFromBk2(Path bk2, Path rom, String engineRegion) throws IOException {
        if ("PAL".equalsIgnoreCase(engineRegion)) {
            throw new IllegalArgumentException("native-compatible PCM models NTSC only; the engine region is PAL");
        }
        byte[] country = new byte[16];
        try (InputStream in = Files.newInputStream(rom)) {
            in.skipNBytes(0x1F0);
            if (in.readNBytes(country, 0, 16) != 16) {
                throw new IllegalArgumentException("ROM too short for a header region field: " + rom);
            }
        }
        return configFromBk2(bk2, country);
    }

    /** As {@link #configFromBk2(Path, Path, String)} with the ROM header's region field ($1F0-$1FF) supplied. */
    static NativeCompatiblePcmOutput.Config configFromBk2(Path bk2, byte[] headerCountry) throws IOException {
        String json;
        try (ZipFile zip = new ZipFile(bk2.toFile())) {
            ZipEntry entry = zip.getEntry("SyncSettings.json");
            if (entry == null) {
                throw new IllegalArgumentException("BK2 has no SyncSettings.json: " + bk2);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        java.util.Map<String, String> values = new java.util.HashMap<>();
        Matcher matcher = FIELD.matcher(json);
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2));
        }
        int region = Integer.parseInt(required(values, "Region"));
        if (!resolvesToNtsc(region, headerCountry)) {
            throw new IllegalArgumentException("BizHawk Region " + region
                    + " resolves to a PAL console; native-compatible PCM models NTSC only");
        }
        int chip = Integer.parseInt(required(values, "GenesisFMSoundChip"));
        if (chip != NUKED_YM2612) {
            throw new IllegalArgumentException("BizHawk GenesisFMSoundChip " + chip
                    + " has no engine output path (only Nuked_YM2612 = 3); not substituted");
        }
        return new NativeCompatiblePcmOutput.Config(
                NativeCompatiblePcmOutput.Filter.fromConfigValue(Integer.parseInt(required(values, "Filter"))),
                Integer.parseInt(required(values, "LowPassRange")),
                Integer.parseInt(required(values, "LowFreq")),
                Integer.parseInt(required(values, "HighFreq")),
                gainPercent(required(values, "LowGain")),
                gainPercent(required(values, "MidGain")),
                gainPercent(required(values, "HighGain")));
    }

    /**
     * GPGX's console region for BizHawk's {@code Region} SyncSetting
     * ({@code config.region_detect}, cinterface.c:911): 1 USA and 3 Japan NTSC
     * are NTSC, 2 Europe and 4 Japan PAL are PAL, and 0 autodetects from the
     * ROM header's country characters with GPGX's default order USA, Japan,
     * Europe, Japan PAL (loadrom.c {@code get_region}: 'U' sets 4, 'J' and 'K'
     * set 1, 'E' sets 8, hex digits set their value; USA when nothing is set).
     * GPGX's per-title PAL overrides name no Sonic title, so they are not modelled.
     */
    static boolean resolvesToNtsc(int region, byte[] headerCountry) {
        switch (region) {
            case 1, 3 -> {
                return true;
            }
            case 2, 4 -> {
                return false;
            }
            case 0 -> {
                int country = 0;
                for (byte raw : headerCountry) {
                    int c = Character.toUpperCase(raw & 0xFF);
                    if (c == 'U') country |= 4;
                    else if (c == 'J' || c == 'K') country |= 1;
                    else if (c == 'E') country |= 8;
                    else if (c < 16) country |= c;
                    else if (c >= '0' && c <= '9') country |= c - '0';
                    else if (c >= 'A' && c <= 'F') country |= c - 'A' + 10;
                }
                return (country & 4) != 0 || (country & 1) != 0 || ((country & 8) == 0 && (country & 2) == 0);
            }
            default -> throw new IllegalArgumentException("unknown BizHawk Region " + region);
        }
    }

    /** BizHawk {@code (short)(gain * 100)} on a C# float (GPGX.ISettable.cs). */
    private static int gainPercent(String value) {
        return (short) (Float.parseFloat(value) * 100f);
    }

    private static String required(java.util.Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("SyncSettings.json lacks " + key.toLowerCase(Locale.ROOT));
        }
        return value;
    }
}
