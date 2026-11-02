package com.openggf.tests.trace.s3k;

import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.SszCutsceneBridgeObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.trace.TraceCharacterState;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native SSZ1 for the cold Sonic+Tails route, comparison only.
 *
 * <p>The movie is {@code tools/traces/compose_route_on_movie_prefix.py}'s splice of the
 * complete-run movie's rows {@code [0,396719)} with the first 2000 rows of
 * {@code ssz1-sonic-tails-cold-complete-320} as committed at {@code fa313dfa3}: route row k
 * is movie row {@code 396719 + k}. Native arms SSZ at movie row 396720, so trace row r holds
 * the state after route input {@code r + 1}. The engine boots SSZ1 cold, exactly as the route
 * tests do, and plays route row k on its step k; nothing from the trace reaches the engine.
 *
 * <p>Per row it compares both players' position, subpixel, speeds, angle, status and routine,
 * the camera and rings, and, while the recorder observes {@code Obj_SSZCutsceneBridge}, the
 * bridge's {@code p1_standing_bit} and {@code p2_standing_bit} against the engine's latches.
 *
 * <p>The comparison ends before the first native lag frame (trace row 1956, route input
 * 1957): a lag frame does not poll the pad, so native consumes the next row one frame late
 * from there on.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kSsz1ColdRouteNativeTrace {
    static final Path TRACE_DIR = Path.of("src/test/resources/traces/s3k/ssz1_cold_route_native");
    static final Path MOVIE = Path.of(
            "src/test/resources/traces/s3k/_movies/s3k-ssz1-cold-route-composite.bk2");
    /** {@code metadata.json}: {@code bk2_frame_offset}, the movie row of route input 1. */
    static final int FIRST_TRACE_MOVIE_ROW = 396_720;
    /** Trace row of the first native lag frame ({@code lag_counter} 0 to 1). */
    static final int FIRST_NATIVE_LAG_ROW = 1956;
    /** {@code Obj_SSZCutsceneBridge}'s two installed routines, {@code loc_44FA2} and {@code loc_4501A}. */
    private static final List<String> BRIDGE_CODES = List.of("0x00044FA2", "0x0004501A");

    @Test
    void fixtureIsTheFrozenNativeCapture() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata metadata = trace.metadata();
        assertEquals("s3k", metadata.game());
        assertEquals(10, metadata.zoneId(), "zone_id 10 is SSZ (the recorder labels it hpz)");
        assertEquals(1, metadata.act());
        assertEquals(FIRST_TRACE_MOVIE_ROW, metadata.bk2FrameOffset());
        assertEquals(1998, metadata.traceFrameCount());
        assertEquals(1998, trace.frameCount());
        assertEquals("s3k-ssz1-cold-route-composite.bk2", metadata.sourceBk2());
        assertEquals(List.of("sonic", "tails"), metadata.recordedCharacters());
        assertEquals(5, metadata.traceSchema());
        assertEquals("complete_run", metadata.traceProfile());
        assertEquals("2.11", metadata.bizhawkVersion());
        assertEquals("Genplus-gx", metadata.genesisCore());
        assertEquals("C5B1C655C19F462ADE0AC4E17A844D10", metadata.romChecksum());

        assertTrue(Files.isRegularFile(TRACE_DIR.resolve("physics.csv.gz")));
        assertTrue(Files.isRegularFile(TRACE_DIR.resolve("aux_state.jsonl.gz")));
        assertFalse(Files.exists(TRACE_DIR.resolve("physics.csv")));
        assertFalse(Files.exists(TRACE_DIR.resolve("aux_state.jsonl")));
        assertEquals(398_719, new Bk2MovieLoader().loadMovieOrInputLog(MOVIE).getFrameCount(),
                "396719 complete-run rows and 2000 route rows");

        int baseLag = trace.getFrame(0).lagCounter();
        for (int row = 0; row < FIRST_NATIVE_LAG_ROW; row++) {
            assertEquals(baseLag, trace.getFrame(row).lagCounter(), "no native lag before row " + row);
        }
        assertEquals(baseLag + 1, trace.getFrame(FIRST_NATIVE_LAG_ROW).lagCounter(),
                "the comparison window ends at the first native lag frame");
    }

    @Test
    void coldRouteMatchesNativeForBothPlayersUntilTheFirstLagFrame() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        Bk2Movie movie = new Bk2MovieLoader().loadMovieOrInputLog(MOVIE);
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null,
                null, null, null, false, false, null, null, false, null, false);
        List<String> mismatches = new ArrayList<>();
        int bridgeRows = 0;
        int[] lastNativeBits = {-1, -1};
        List<String> nativeClears = new ArrayList<>();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            for (int input = 0; input <= FIRST_NATIVE_LAG_ROW; input++) {
                session.step(movie.getFrame(FIRST_TRACE_MOVIE_ROW - 1 + input));
                if (input == 0) {
                    continue; // the engine's pre-arm step; native row 0 follows route input 1
                }
                int row = input - 1;
                TraceFrame expected = trace.getFrame(row);
                AbstractPlayableSprite p1 = session.player();
                AbstractPlayableSprite p2 = GameServices.sprites().getRegisteredSidekicks().getFirst();
                compare(mismatches, input, "p1", primary(expected), p1);
                compare(mismatches, input, "p2", expected.sidekick(), p2);
                put(mismatches, input, "camera_x", expected.cameraX(), GameServices.camera().getX() & 0xFFFF);
                put(mismatches, input, "camera_y", expected.cameraY(), GameServices.camera().getY() & 0xFFFF);
                put(mismatches, input, "rings", expected.rings(), p1.getRingCount());
                Integer nativeBridgeStatus = bridgeStatus(trace, row);
                if (nativeBridgeStatus != null) {
                    bridgeRows++;
                    var manager = GameServices.level().getObjectManager();
                    var bridge = manager.activeObjectsOfType(SszCutsceneBridgeObjectInstance.class);
                    boolean p1Bit = bridge.stream().anyMatch(b -> manager.hasObjectStandingBit(p1, b));
                    boolean p2Bit = bridge.stream().anyMatch(b -> manager.hasObjectStandingBit(p2, b));
                    // sub_45026 -> SolidObjectTop: p1_standing_bit 3, p2_standing_bit 4.
                    int[] nativeBits = {(nativeBridgeStatus >> 3) & 1, (nativeBridgeStatus >> 4) & 1};
                    put(mismatches, input, "bridge.p1_standing", nativeBits[0], p1Bit ? 1 : 0);
                    put(mismatches, input, "bridge.p2_standing", nativeBits[1], p2Bit ? 1 : 0);
                    for (int who = 0; who < 2; who++) {
                        if (lastNativeBits[who] == 1 && nativeBits[who] == 0) nativeClears.add("p" + (who + 1) + "@" + input);
                        lastNativeBits[who] = nativeBits[who];
                    }
                }
                if (mismatches.size() >= 12) {
                    break;
                }
            }
        }
        assertEquals(List.of(), mismatches, "engine versus native SSZ1, first mismatches");
        // The recorder samples objects near the player: the bridge is observed on route inputs
        // 1333-1437. SolidObjectTop_1P's loc_1E2E0 clears P1's bit for the 1366 jump (airborne
        // release) and both bits on the walk-offs at 1416 and 1430.
        assertEquals(105, bridgeRows, "rows observing Obj_SSZCutsceneBridge");
        assertEquals(List.of("p1@1366", "p1@1416", "p2@1430"), nativeClears, "native bit clears");
    }

    private static TraceCharacterState primary(TraceFrame frame) {
        return new TraceCharacterState(true, frame.x(), frame.y(), frame.xSpeed(), frame.ySpeed(),
                frame.gSpeed(), frame.angle(), frame.air(), frame.rolling(), frame.groundMode(),
                frame.xSub(), frame.ySub(), frame.routine(), frame.statusByte(), frame.standOnObj(),
                frame.animationId(), frame.mappingFrame());
    }

    private static void compare(List<String> out, int input, String who, TraceCharacterState expected,
                                AbstractPlayableSprite actual) {
        Map<String, int[]> fields = new LinkedHashMap<>();
        fields.put("x", new int[] {expected.x() & 0xFFFF, actual.getCentreX() & 0xFFFF});
        fields.put("x_sub", new int[] {expected.xSub(), actual.getXSubpixelRaw()});
        fields.put("y", new int[] {expected.y() & 0xFFFF, actual.getCentreY() & 0xFFFF});
        fields.put("y_sub", new int[] {expected.ySub(), actual.getYSubpixelRaw()});
        fields.put("x_speed", new int[] {expected.xSpeed(), actual.getXSpeed()});
        fields.put("y_speed", new int[] {expected.ySpeed(), actual.getYSpeed()});
        fields.put("g_speed", new int[] {expected.gSpeed(), actual.getGSpeed()});
        fields.put("angle", new int[] {expected.angle() & 0xFF, actual.getAngle() & 0xFF});
        fields.put("status", new int[] {expected.statusByte() & 0xFF,
                TraceCharacterState.statusByteFromSprite(actual) & 0xFF});
        fields.put("routine", new int[] {expected.routine(), TraceCharacterState.routineFromSprite(actual)});
        fields.forEach((name, pair) -> put(out, input, who + "." + name, pair[0], pair[1]));
    }

    private static void put(List<String> out, int input, String field, int expected, int actual) {
        if (expected != actual && out.size() < 12) {
            out.add("input " + input + " " + field + " native=" + expected + " engine=" + actual);
        }
    }

    private static Integer bridgeStatus(TraceData trace, int row) {
        for (TraceEvent.StateSnapshot snapshot : trace.stateSnapshotsForFrame(row)) {
            Map<String, Object> f = snapshot.fields();
            if ("object_state".equals(f.get("event")) && BRIDGE_CODES.contains(f.get("object_code"))) {
                return Integer.decode(String.valueOf(f.get("status")));
            }
        }
        return null;
    }
}
