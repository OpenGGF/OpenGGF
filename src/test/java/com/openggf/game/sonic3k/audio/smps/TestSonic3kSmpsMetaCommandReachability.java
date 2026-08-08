package com.openggf.game.sonic3k.audio.smps;

import com.openggf.audio.MusicRestoreSink;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSfxData;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM-backed reachability inventory for S3K FF meta coordination commands.
 *
 * <p>The three commands in scope are deliberately not inferred from arbitrary
 * bytes in the SFX bank. The bank contains voices and unrelated streams, so a
 * byte-pattern scan alone produces false positives. Each loaded stream is
 * sequenced with a recording handler; only FF bytes encountered at a live
 * track position count as reached. Music blobs also get a raw-pair guard since
 * their loader returns the complete song blob.</p>
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestSonic3kSmpsMetaCommandReachability {

    private static final Set<Integer> TARGET_META_SUBCOMMANDS = Set.of(0x01, 0x02, 0x03);
    private static final int SAMPLE_BUFFER_LENGTH = 512;
    private static final int MUSIC_MAX_READS = 512;
    private static final int SFX_MAX_READS = 4096;

    @Test
    void shippedMusicAndSfxDoNotReachSndCmdMusPauseOrCopyMem() {
        Rom rom = TestEnvironment.currentRom();
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(rom);
        DacData dacData = loader.loadDacData();
        RecordingHandler handler = new RecordingHandler();
        Reachability reachability = new Reachability();

        int skMusicCount = 0;
        for (int id = 0x01; id <= 0x33; id++) {
            AbstractSmpsData data = loader.loadMusic(id);
            assertNotNull(data, "Missing S&K music stream 0x" + Integer.toHexString(id));
            assertNoMusicMetaPair(data, id, "S&K");
            sequence(data, dacData, handler, reachability, "S&K music 0x" + hex(id));
            skMusicCount++;
        }

        int s3MusicCount = 0;
        for (int id = 0x01; id <= 0x32; id++) {
            AbstractSmpsData data = loader.loadS3Music(id);
            assertNotNull(data, "Missing S3 music stream 0x" + Integer.toHexString(id));
            assertNoMusicMetaPair(data, id, "S3");
            sequence(data, dacData, handler, reachability, "S3 music 0x" + hex(id));
            s3MusicCount++;
        }

        int sfxCount = 0;
        for (int id = Sonic3kSfx.ID_BASE; id <= Sonic3kSfx.ID_MAX; id++) {
            AbstractSmpsData data = loader.loadSfx(id);
            assertNotNull(data, "Missing SFX stream 0x" + Integer.toHexString(id));
            sequence(data, dacData, handler, reachability, "SFX 0x" + hex(id));
            sfxCount++;
        }

        assertEquals(0x33, skMusicCount);
        assertEquals(0x32, s3MusicCount);
        assertEquals(Sonic3kSfx.ID_MAX - Sonic3kSfx.ID_BASE + 1, sfxCount);
        assertTrue(reachability.metaSubcommands().contains(0x00),
                "The inventory must observe a live FF00 tempo command");
        assertTrue(reachability.metaSubcommands().contains(0x07),
                "The inventory must observe the live SFX FF07 command");
        assertFalse(reachability.metaSubcommands().stream()
                        .anyMatch(TARGET_META_SUBCOMMANDS::contains),
                () -> "Shipped ROM reached an unimplemented meta command: "
                        + reachability.metaSubcommands());
    }

    private static void sequence(AbstractSmpsData data, DacData dacData,
            RecordingHandler handler, Reachability reachability, String streamName) {
        handler.begin(reachability, streamName);
        SmpsSequencer sequencer = new SmpsSequencer(
                data, dacData, new VirtualSynthesizer(), (MusicRestoreSink) () -> {
                }, Sonic3kSmpsSequencerConfig.create(handler));
        short[] samples = new short[SAMPLE_BUFFER_LENGTH];
        int maxReads = data instanceof SmpsSfxData ? SFX_MAX_READS : MUSIC_MAX_READS;
        for (int read = 0; read < maxReads && !sequencer.isComplete(); read++) {
            sequencer.read(samples);
        }
    }

    private static void assertNoMusicMetaPair(AbstractSmpsData data, int id, String table) {
        byte[] bytes = data.getData();
        for (int pos = 0; pos + 1 < bytes.length; pos++) {
            if ((bytes[pos] & 0xFF) != 0xFF) {
                continue;
            }
            int sub = bytes[pos + 1] & 0xFF;
            int blobOffset = pos;
            assertFalse(TARGET_META_SUBCOMMANDS.contains(sub),
                    () -> table + " music 0x" + hex(id)
                            + " contains FF" + hex(sub) + " at blob offset 0x"
                            + Integer.toHexString(blobOffset));
        }
    }

    private static String hex(int value) {
        return String.format("%02X", value & 0xFF);
    }

    private static final class Reachability {
        private final Map<String, Set<Integer>> byStream = new HashMap<>();

        void record(String streamName, int subcommand) {
            byStream.computeIfAbsent(streamName, ignored -> new HashSet<>()).add(subcommand);
        }

        Set<Integer> metaSubcommands() {
            Set<Integer> all = new HashSet<>();
            byStream.values().forEach(all::addAll);
            return all;
        }
    }

    private static final class RecordingHandler extends Sonic3kCoordFlagHandler {
        private Reachability reachability;
        private String streamName;

        RecordingHandler() {
            super(new SmpsCoordFlagRuntimeState());
        }

        void begin(Reachability reachability, String streamName) {
            this.reachability = reachability;
            this.streamName = streamName;
        }

        @Override
        public boolean handleFlag(com.openggf.audio.smps.CoordFlagContext ctx,
                SmpsSequencer.Track track, int cmd) {
            byte[] data = ctx.getData();
            if (cmd == 0xE2) {
                // Avoid the global audio singleton while preserving the ROM's
                // one-operand track-pointer advance for this inventory run.
                if (track.pos < data.length) {
                    track.pos++;
                }
                return true;
            }
            if (cmd != 0xFF) {
                return super.handleFlag(ctx, track, cmd);
            }

            if (track.pos >= data.length) {
                return true;
            }
            int subcommand = data[track.pos++] & 0xFF;
            reachability.record(streamName, subcommand);
            int operandBytes = switch (subcommand) {
                case 0x00, 0x01, 0x02, 0x04 -> 1;
                case 0x03 -> 3;
                case 0x05 -> 4;
                case 0x06 -> 2;
                case 0x07 -> 0;
                default -> 0;
            };
            track.pos = Math.min(data.length, track.pos + operandBytes);
            return true;
        }
    }
}
