package com.openggf.tools.audio.parity.s2;

import com.openggf.game.sonic2.audio.Sonic2Music;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The second S2 driver-state recording: a different movie, a different zone
 * and a different song from the widened EHZ span.
 *
 * <p>The reference is captured from {@code s2-lvl-select-CPZ.bk2} over movie
 * rows [2700,3450), which contain the CPZ level load: the driver's
 * {@code zCurSong} moves from {@code 91h} to {@code 8Eh} at the service ending
 * on row 2725, and the loaded song's own header tempo reaches
 * {@code zAbsVar.TempoMod} on the next one (s2.sounddriver.asm:1817-1826). The
 * Saxman decompression overruns its frame, which is why the load spans two
 * services here where the EHZ window's fits inside one.
 *
 * <p>The engine side is the same headless pattern the EHZ v1 oracle uses: the
 * real driver plays the song from a constant and nothing is read back from the
 * reference. The engine music id is settled by that stored header tempo rather
 * than by assuming a fixed distance from the ROM request id, because there is
 * none: EHZ is driver {@code 82h} against engine {@code 81h}, CPZ is driver
 * {@code 8Eh} against engine {@code 8Ch}.
 */
class TestS2CpzDriverStateOracle {

    static final String RESOURCE =
            "/audio/parity/s2/s2-driver-state-cpz-w2700-3450.reference-v2.jsonl.gz";
    static final String GZIP_SHA256 =
            "8b70ba61c9727323a0470169c14f915aa205f1f04802a2fbb228f1f12893a90a";
    static final String RAW_SHA256 =
            "af5ec3e65137d3cc1670433b368247dcf25440cc8ea2a7ebf7478c6ddc680bd7";
    static final int FIRST_ROW = 2_700;
    static final int EXCLUSIVE_END = 3_450;
    static final int TICKS = 744;
    static final int ZERO_SERVICE_FRAMES = 6;
    /** The ROM driver request id this window's level load consumes. */
    static final int LOAD_ROM_MUSIC_ID = 0x8E;
    /** The engine id for that song, identified by its stored header tempo. */
    static final int LOAD_ENGINE_MUSIC_ID = Sonic2Music.CHEMICAL_PLANT.id;
    /** The committed request-window sidecar for this same movie and span. */
    static final String REQUEST_SIDECAR_RESOURCE =
            "/audio/parity/s2/s2-request-window-cpz-w2700-3450.raw-v2.jsonl.gz";

    @Test
    void committedReferenceMatchesItsPinnedDigestAndShape() throws Exception {
        assertEquals(GZIP_SHA256, S2DriverStateReference.gzipDigest(RESOURCE));
        S2DriverStateReference.Result reference = read();
        assertEquals(TICKS, reference.ticks().size());
        assertEquals(EXCLUSIVE_END - FIRST_ROW, reference.frames());
        assertEquals(ZERO_SERVICE_FRAMES, reference.zeroServiceFrames());
        assertEquals(0, reference.multiServiceFrames());
        assertEquals(reference.ticks().size() + reference.zeroServiceFrames(),
                reference.frames(),
                "every frame either completed a service or was run past by one");
    }

    /** The window really does contain the level load this oracle anchors on. */
    @Test
    void theWindowContainsTheCpzLevelLoad() throws Exception {
        S2DriverStateReference.Result reference = read();
        int loadTick = -1;
        for (S2DriverStateReference.Tick tick : reference.ticks()) {
            S2OracleDriverState state = S2OracleDriverState.decode(
                    S2DriverStateReference.rebase(tick.state()));
            if (state.globals().curSong() == LOAD_ROM_MUSIC_ID) {
                loadTick = tick.index();
                break;
            }
        }
        assertNotEquals(-1, loadTick, "the window must contain the CPZ load");
        S2OracleDriverState before = S2OracleDriverState.decode(
                S2DriverStateReference.rebase(
                        reference.ticks().get(loadTick - 1).state()));
        assertNotEquals(LOAD_ROM_MUSIC_ID, before.globals().curSong(),
                "the load must happen inside the window, not before it");
    }

    @Test
    void driverStateComparesAcrossTheCpzWindow() throws Exception {
        String romProperty = System.getProperty("sonic2.rom.path");
        assumeTrue(romProperty != null, "an explicit ROM path is required");

        List<S2AudioOracleComparator.ReferenceTick> reference = anchoredTicks();
        List<S2OracleEngineCapture.EngineTick> engine =
                S2OracleEngineCapture.capture(Path.of(romProperty),
                        reference.size(), reference.size(),
                        requestStimuli(reference), LOAD_ENGINE_MUSIC_ID);

        // The write comparison starts one service later than the state one.
        // This window's load spans two services, because the Saxman
        // decompression overruns its frame, so the anchored service still
        // carries the tail of zBGMLoad's own writes; the engine capture emits
        // its load burst as one block and drains it, by design. Where the two
        // halves of a split load land is a capture-window property, not driver
        // behaviour, so the writes are compared from the first wholly
        // post-load service onwards and the state is compared from the anchor.
        S2AudioOracleComparator.Report withWrites =
                S2AudioOracleComparator.compareWithEngine(
                        reindex(reference.subList(1, reference.size())),
                        reindexEngine(engine.subList(1, engine.size())));
        System.out.println("MEASUREMENT_ONLY s2-driver-state-cpz-w2700-3450 "
                + "state and writes: " + withWrites.describe());

        List<S2AudioOracleComparator.ReferenceTick> stateOnlyReference =
                new ArrayList<>();
        for (S2AudioOracleComparator.ReferenceTick tick : reference) {
            stateOnlyReference.add(new S2AudioOracleComparator.ReferenceTick(
                    tick.ordinal(), tick.row(), tick.state(), List.of()));
        }
        List<S2OracleEngineCapture.EngineTick> stateOnlyEngine = new ArrayList<>();
        for (S2OracleEngineCapture.EngineTick tick : engine) {
            stateOnlyEngine.add(new S2OracleEngineCapture.EngineTick(
                    tick.ordinal(), tick.currentTempo(), tick.tempoTimeout(),
                    tick.musicSlots(), List.of()));
        }
        S2AudioOracleComparator.Report stateOnly =
                S2AudioOracleComparator.compareWithEngine(
                        stateOnlyReference, stateOnlyEngine);
        System.out.println("MEASUREMENT_ONLY s2-driver-state-cpz-w2700-3450 "
                + "state only: " + stateOnly.describe());

        assertNotEquals(S2AudioOracleComparator.Kind.INVALID, withWrites.kind(),
                withWrites.describe());
        assertNotEquals(S2AudioOracleComparator.Kind.INVALID, stateOnly.kind(),
                stateOnly.describe());
    }

    /**
     * The comparison must be able to fail. One corrupted RAM byte moves the
     * verdict; without this a span that never compared would read exactly like
     * a span that agreed.
     */
    @Test
    void aCorruptedReferenceByteBreaksTheComparison() throws Exception {
        List<S2AudioOracleComparator.ReferenceTick> clean = anchoredTicks();
        List<S2OracleEngineCapture.EngineTick> mirror = mirrorOf(clean);
        assertEquals(S2AudioOracleComparator.Kind.MATCH,
                S2AudioOracleComparator.compareWithEngine(clean, mirror).kind(),
                "the reference must agree with itself before the break is meaningful");

        List<S2AudioOracleComparator.ReferenceTick> corrupted =
                new ArrayList<>(clean);
        S2AudioOracleComparator.ReferenceTick victim = corrupted.get(10);
        byte[] state = victim.state();
        int tempo = 0x1b80 + 2;
        state[tempo] = (byte) (state[tempo] ^ 0xff);
        corrupted.set(10, new S2AudioOracleComparator.ReferenceTick(
                victim.ordinal(), victim.row(), state, victim.writes()));

        S2AudioOracleComparator.Report report =
                S2AudioOracleComparator.compareWithEngine(corrupted, mirror);
        assertEquals(S2AudioOracleComparator.Kind.DIVERGENCE, report.kind(),
                report.describe());
        assertEquals(10, report.firstDivergenceTick(), report.describe());
    }

    /**
     * The window's recorded sound requests as engine stimuli, from the
     * committed request-window sidecar for this same movie and span. Requests
     * are inputs the recording's own 68k made; nothing compared is read from
     * them, and the driver-state payload cannot supply them because its
     * pre-consumption markers carry no id.
     */
    private static List<S2OracleEngineCapture.DriverRequest> requestStimuli(
            List<S2AudioOracleComparator.ReferenceTick> anchored)
            throws IOException {
        java.util.Map<Integer, Integer> tickForRow = new java.util.HashMap<>();
        for (S2AudioOracleComparator.ReferenceTick tick : anchored) {
            tickForRow.putIfAbsent(tick.row(), tick.ordinal());
        }
        List<S2OracleEngineCapture.DriverRequest> stimuli = new ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper json =
                new com.fasterxml.jackson.databind.ObjectMapper();
        try (java.io.InputStream raw = TestS2CpzDriverStateOracle.class
                .getResourceAsStream(REQUEST_SIDECAR_RESOURCE);
             java.util.zip.GZIPInputStream gz =
                     new java.util.zip.GZIPInputStream(
                             java.util.Objects.requireNonNull(raw,
                                     "committed CPZ request sidecar is absent"));
             java.io.BufferedReader reader = new java.io.BufferedReader(
                     new java.io.InputStreamReader(gz,
                             java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                com.fasterxml.jackson.databind.JsonNode node = json.readTree(line);
                com.fasterxml.jackson.databind.JsonNode transfers =
                        node.get("request_transfers");
                if (transfers == null || !transfers.isArray()) {
                    continue;
                }
                for (com.fasterxml.jackson.databind.JsonNode transfer : transfers) {
                    Integer tick = tickForRow.get(transfer.get("row").asInt());
                    if (tick == null) {
                        continue;
                    }
                    stimuli.add(new S2OracleEngineCapture.DriverRequest(
                            tick, transfer.get("request").asInt()));
                }
            }
        }
        return stimuli;
    }

    private static List<S2AudioOracleComparator.ReferenceTick> reindex(
            List<S2AudioOracleComparator.ReferenceTick> ticks) {
        List<S2AudioOracleComparator.ReferenceTick> out = new ArrayList<>();
        for (S2AudioOracleComparator.ReferenceTick tick : ticks) {
            out.add(new S2AudioOracleComparator.ReferenceTick(out.size(),
                    tick.row(), tick.state(), tick.writes()));
        }
        return out;
    }

    private static List<S2OracleEngineCapture.EngineTick> reindexEngine(
            List<S2OracleEngineCapture.EngineTick> ticks) {
        List<S2OracleEngineCapture.EngineTick> out = new ArrayList<>();
        for (S2OracleEngineCapture.EngineTick tick : ticks) {
            out.add(new S2OracleEngineCapture.EngineTick(out.size(),
                    tick.currentTempo(), tick.tempoTimeout(),
                    tick.musicSlots(), tick.writes()));
        }
        return out;
    }

    private static S2DriverStateReference.Result read() throws IOException {
        return S2DriverStateReference.read(
                S2DriverStateReference.open(RESOURCE), false);
    }

    /**
     * The reference from the CPZ load onwards. The anchor is driver state,
     * never a row: the first service whose {@code zCurSong} is this window's
     * load id and whose music tracks are playing. Both halves are needed. The
     * window opens with the level-select music still running, so "any music
     * playing" would anchor on the previous song; and {@code zCurSong} is set
     * one service before the load finishes here, because the Saxman
     * decompression overruns its frame, so that service's return still shows
     * an unloaded {@code TempoMod}. The engine's own tick 0 is its load burst
     * plus first update, which is the service this picks.
     */
    private static List<S2AudioOracleComparator.ReferenceTick> anchoredTicks()
            throws IOException {
        List<S2AudioOracleComparator.ReferenceTick> anchored = new ArrayList<>();
        boolean started = false;
        for (S2DriverStateReference.Tick tick : read().ticks()) {
            byte[] state = S2DriverStateReference.rebase(tick.state());
            if (!started
                    && (S2OracleDriverState.decode(state).globals().curSong()
                            != LOAD_ROM_MUSIC_ID
                    || !hasPlayingMusicTrack(state))) {
                continue;
            }
            started = true;
            anchored.add(new S2AudioOracleComparator.ReferenceTick(
                    anchored.size(), tick.frame(), state, tick.writes()));
        }
        return anchored;
    }

    private static boolean hasPlayingMusicTrack(byte[] state) {
        for (S2OracleDriverState.TrackState track
                : S2OracleDriverState.decode(state).musicTracks()) {
            if ((track.playbackControl() & 0x80) != 0) {
                return true;
            }
        }
        return false;
    }

    /** The reference decoded back into engine-shaped ticks. */
    private static List<S2OracleEngineCapture.EngineTick> mirrorOf(
            List<S2AudioOracleComparator.ReferenceTick> ticks) {
        List<S2OracleEngineCapture.EngineTick> engine = new ArrayList<>();
        for (S2AudioOracleComparator.ReferenceTick tick : ticks) {
            S2OracleDriverState state = S2OracleDriverState.decode(tick.state());
            List<S2OracleComparison.MappedTrack> slots = new ArrayList<>();
            List<S2OracleDriverState.TrackState> tracks = state.musicTracks();
            for (int slot = 0; slot < tracks.size(); slot++) {
                String name = S2OracleDriverState.MUSIC_SLOTS.get(slot);
                slots.add(S2OracleComparison.MappedTrack.fromReference(
                        tracks.get(slot), name.startsWith("PSG"), slot == 0));
            }
            engine.add(new S2OracleEngineCapture.EngineTick(tick.ordinal(),
                    state.globals().currentTempo(), state.globals().tempoTimeout(),
                    slots, tick.writes()));
        }
        return engine;
    }
}
