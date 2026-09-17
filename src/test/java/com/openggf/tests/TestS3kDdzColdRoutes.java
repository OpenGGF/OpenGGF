package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.level.Palette;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sonic Doomsday route driven only by the controller input of the committed Sonic + Tails
 * complete-run movie from movie frame 514214 (segment {@code zone0c} row 0), from the level's
 * ordinary cold start through transformation, both boss phases, two level wraps, the defeat and
 * the {@code StartNewLevel} request for {@code $D01}.
 *
 * <p>The movie entered DDZ from the ending of Sky Sanctuary with two clocks that a level-select
 * entry does not inherit; both are declared engine setup observed natively at movie frame 514214
 * (native pass 1 of the DDZ bring-up), not per-row trace data:
 * {@code V_int_run_count} 512489 (turret aim reads its low nibble) and the camera X fraction
 * {@code $2700} ({@code sub_82920} keeps it across frames). The segment's physics rows are read
 * for comparison only. Without the two seeds the route diverges at frame 4178 and dies in phase 1.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDdzColdRoutes {
    static final Path BK2 = Path.of(
            "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2");
    static final Path PHYSICS = Path.of(
            "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/zone0c/physics.csv.gz");
    static final int FIRST_LEVEL_FRAME = 514214;
    static final int NATIVE_V_INT_RUN_COUNT = 512489;
    static final int NATIVE_CAMERA_X_FRACTION = 0x2700;
    /** Row 10057 is the last gameplay row; the loop leaves the level after {@code loc_81CA4}. */
    static final int LAST_GAMEPLAY_FRAME = 10058;
    static final int DIVERGENT_FRAMES = 45;
    static final int BOMBS_ACROSS_WRAP_FRAME = 7470;
    static final int IDLE_PLANE_FRAME = 480;
    static final int ORPHAN_BURST_FRAME = 5495;
    static final int PHASE_TWO_PALETTE_FRAME = 7000;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test
    void seededNativeRouteMatchesPositionsCameraAndRingsThroughTheExitRequest() throws Exception {
        runSeededRoute(Set.of());
    }

    /**
     * Rewind spots on the same route: the phase-1 fight (turrets, missiles and body hits live), the
     * first {@code $7400} wrap (seek, ring table wipe, object shift) and the exit fade (explosions,
     * palette fade, {@code _unkFAB8}). Each capture is followed by {@value #DIVERGENT_FRAMES} frames of
     * idle input that change the route, then a restore; the recording continues from the capture
     * and native parity must still hold to the exit request.
     */
    @Test
    void rewindAtBossWrapAndExitRestoresTheNativeRoute() throws Exception {
        runSeededRoute(Set.of(4000, 7420, 9990));
    }

    private static void runSeededRoute(Set<Integer> rewindFrames) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DDZ, 0)
                .withFreshLevelStartLifecycle()
                .withRecording(BK2)
                .withRecordingStartFrame(FIRST_LEVEL_FRAME)
                .build();
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(3, 3, 3, 3, 3, 3, 3), true);
        var player = fixture.sprite();
        var ddz = S3kRuntimeStates.currentDdz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        GameServices.level().getObjectManager().initVblaCounter(NATIVE_V_INT_RUN_COUNT);
        ddz.setCameraXFraction(ddz.cameraXFraction() + NATIVE_CAMERA_X_FRACTION);
        assertTrue(GameServices.sprites().getSidekicks().isEmpty(), "loc_81554 clears Player_2");

        List<String[]> rows = readRows();
        String firstMismatch = null;
        int wraps = 0;
        int lastCameraX = GameServices.camera().getX() & 0xFFFF;
        boolean becameHyper = false;
        for (int frame = 1; frame <= LAST_GAMEPLAY_FRAME; frame++) {
            fixture.stepFrameFromRecording();
            if (rewindFrames.contains(frame)) {
                RewindRegistry registry = fixture.gameplayMode().getRewindRegistry();
                String before = objectSummary();
                CompositeSnapshot snapshot = registry.capture();
                fixture.stepIdleFrames(DIVERGENT_FRAMES);
                assertNotEquals(before, objectSummary(), "idle frames must change the state at " + frame);
                registry.restore(snapshot);
                assertEquals(before, objectSummary(), "restored DDZ object graph at frame " + frame);
            }
            String[] row = rows.get(frame - 1);
            int cameraX = GameServices.camera().getX() & 0xFFFF;
            String actual = String.format("x=%04X y=%04X cam=%04X,%04X rings=%d",
                    player.getCentreX() & 0xFFFF, player.getCentreY() & 0xFFFF,
                    cameraX, GameServices.camera().getY() & 0xFFFF, player.getRingCount());
            String expected = String.format("x=%04X y=%04X cam=%04X,%04X rings=%d",
                    Integer.parseInt(row[9], 16), Integer.parseInt(row[10], 16),
                    Integer.parseInt(row[2], 16), Integer.parseInt(row[3], 16), Integer.parseInt(row[4], 16));
            if (firstMismatch == null && !actual.equals(expected)) {
                firstMismatch = "frame " + frame + " expected " + expected + " actual " + actual;
            }
            if (cameraX + 0x1000 < lastCameraX) {
                wraps++;
            }
            lastCameraX = cameraX;
            becameHyper |= player.getSuperStateController() != null
                    && player.getSuperStateController().isHyperFormActive();
            assertFalse(player.getDead(), "died at frame " + frame);
            if (frame == IDLE_PLANE_FRAME) {
                // Native row 480: Events_routine_fg 0, _unkEE98 $9B8. Plane A still holds only
                // DDZ_ScreenInit's Refresh_PlaneFull draw at (0,0), wrapped at 512x256.
                assertEquals(0, ddz.foregroundRoutine(), "DDZ_ScreenEvent routine before the boss window");
                // Layout (0,0)-(512,256) is empty in the stock DDZ layout, so the shown window is that blank
                // draw rather than the boss chunks at X $200 that the unwrapped words would address.
                assertEquals(0, ddz.displayedForegroundX(), "plane A shows the blank initial draw");
                assertEquals(0, ddz.displayedForegroundY(), "plane A shows the blank initial draw");
            }
            if (frame == PHASE_TWO_PALETTE_FRAME) {
                // Native palette dump: loc_819CE reloads Pal_DDZ+$20 into line 3 before the Target copy, so the
                // phase-2 flash does not fade back to the three DecColor_Obj passes of the fall ($888).
                Palette.Color restored = new Palette.Color();
                restored.fromSegaFormat(new byte[]{0x0E, (byte) 0xEE}, 0);
                Palette.Color line3 = GameServices.level().getCurrentLevel().getPalette(2).getColor(1);
                assertEquals(List.of(restored.r, restored.g, restored.b), List.of(line3.r, line3.g, line3.b),
                        "Normal_palette_line_3 colour 1 in phase 2");
            }
            if (frame == ORPHAN_BURST_FRAME) {
                // Native slot history rows 5477-5500: once the defeat spawner is deleted its cleared slot reads
                // (0,0), so the remaining loc_82F78 bursts appear near the level origin, off-screen.
                assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                        .filter(o -> o.getClass().getSimpleName().equals("DdzBossExplosionObjectInstance"))
                        .anyMatch(o -> (o.getX() & 0xFFFF) < 0x100), "orphaned defeat bursts spawn from (0,0)");
            }
            if (frame == BOMBS_ACROSS_WRAP_FRAME) {
                // Native slot history (probe-slots1): the two bombs launched before the first wrap stay
                // live through rows 7453-7492 because loc_81726 re-latches Camera_X_pos_coarse_back.
                assertEquals(2, GameServices.level().getObjectManager().getActiveObjects().stream()
                        .filter(o -> o.getClass().getSimpleName().equals("DdzEndBossBombObjectInstance")).count(),
                        "bombs survive the first wrap");
            }
        }
        assertNull(firstMismatch);
        assertTrue(becameHyper, "all Super Emeralds: the controller's transformation ends in Hyper form");
        assertEquals(2, wraps, "phase 2 wraps $7400 -> $5400 twice on this route");
        assertEquals(0x0D, GameServices.level().getRequestedZone(), "loc_81CA4 StartNewLevel zone");
        assertEquals(1, GameServices.level().getRequestedAct(), "loc_81CA4 StartNewLevel act");
    }

    /** Slot, class and position of every live object plus the player and camera. */
    private static String objectSummary() {
        StringBuilder out = new StringBuilder(String.format("p=%X,%X c=%X,%X r=%d",
                GameServices.sprites().getMainPlayable().getCentreX(), GameServices.sprites().getMainPlayable().getCentreY(),
                GameServices.camera().getX(), GameServices.camera().getY(),
                GameServices.sprites().getMainPlayable().getRingCount()));
        GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof AbstractObjectInstance)
                .map(o -> (AbstractObjectInstance) o)
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                .forEach(o -> out.append(o.getSpawn() == null
                        ? String.format(" %d:%s", o.getSlotIndex(), o.getClass().getSimpleName())
                        : String.format(" %d:%s@%X,%X", o.getSlotIndex(), o.getClass().getSimpleName(),
                        o.getX(), o.getY())));
        return out.toString();
    }

    private static List<String[]> readRows() throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (var in = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(PHYSICS))))) {
            in.readLine();
            for (String line; (line = in.readLine()) != null; ) {
                rows.add(line.split(","));
            }
        }
        return rows;
    }
}
