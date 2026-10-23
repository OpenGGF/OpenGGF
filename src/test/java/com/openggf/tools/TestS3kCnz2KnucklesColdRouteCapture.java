package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.recording.UserRecordingWriter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.objects.CnzEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterInstance;
import com.openggf.game.sonic3k.objects.IczSnowboardIntroInstance;
import com.openggf.game.sonic3k.objects.IczTeleporterInstance;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.TeleporterBeamObjectInstance;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R6 cold native320/off solo Knuckles: CNZ2 ROM0301 -> ordinary released ICZ1.
 * Input rows/spots are observed movie data, not production timing constants.
 * Source owners: CNZ2_ScreenEvent, upright capsule/results, Obj_CNZTeleporter,
 * fresh title/Process_Sprites, ICZ1_ScreenInit and Obj_ICZTeleporterMain/CheckDel.
 * No position/clock/RNG/checkpoint/phase seed or raw restore across a load.
 */
@Isolated
class TestS3kCnz2KnucklesColdRouteCapture {
    private static final Path MOVIE = Path.of("src/test/resources/routes/s3k/cnz2-knuckles-cold-released-icz-320.bk2");
    private static final Path SCRIPT = Path.of("src/test/resources/routes/s3k/cnz2-knuckles-cold-released-icz-320.script");
    private static final int ROWS = 7164;
    private static final int PREFIX_ROWS = 6989;
    private static final int WINDOW = 45;
    private static final String MOVIE_SHA = "413f2fc551f8b4d69933fb1ec75d50ea4df31bba2efce309942e947ff73fdf9f";
    private static final String SCRIPT_SHA = "cea508947a54a64e008c7f42b9a431cf8e19de370aace68ceebef47f35ef76be";
    private static final String PREFIX_ROWS_SHA = "bac5bd0fd4b2d585d94f3c2d83cc27c5693e6ce09387b4eb1a4fa24372fbca4f";
    // Each entry is a reached role with a safe 45-input forward window in its current world.
    private static final Map<Integer, String> SPOTS = Map.ofEntries(
            Map.entry(4139, "before-fresh-jump-glide-grab"),
            Map.entry(4163, "retained-wall-grab"),
            Map.entry(4174, "ordinary-wall-jump"),
            Map.entry(4223, "grounded-after-wall-jump"),
            Map.entry(4700, "before-P1-late-admission"),
            Map.entry(4732, "actual-P1-late-admission"),
            Map.entry(4835, "upper-floor-approach"),
            Map.entry(5412, "capsule-body-before-button"),
            Map.entry(5444, "natural-button-standing"),
            Map.entry(5480, "before-results-start"),
            Map.entry(5510, "actual-results-running"),
            Map.entry(6025, "before-results-control-release"),
            Map.entry(6047, "actual-results-control-release"),
            Map.entry(6645, "airborne-teleporter-lock"),
            Map.entry(6663, "actual-CNZ-beam"),
            Map.entry(6749, "beam-player-control"),
            Map.entry(6795, "beam-hide-and-camera-rise"),
            Map.entry(6987, "fresh-ICZ-assembly-and-arrival"),
            Map.entry(7050, "arrival-control-release"),
            Map.entry(7074, "released-pad-support"),
            Map.entry(7118, "settled-incoming-world"));

    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
        TestEnvironment.resetAll();
    }

    /** Cheap input contract, independent of the long route and of ROM availability. */
    @Test void inputOnlyMovieRoundTripsTheScriptAndRetainsTheExactR5Prefix(@TempDir Path temporary) throws Exception {
        assertEquals(MOVIE_SHA, sha(Files.readAllBytes(MOVIE)));
        assertEquals(SCRIPT_SHA, sha(Files.readAllBytes(SCRIPT)));
        try (var archive = new ZipFile(MOVIE.toFile())) {
            assertEquals(Set.of("Header.txt", "Input Log.txt"),
                    archive.stream().map(java.util.zip.ZipEntry::getName).collect(java.util.stream.Collectors.toSet()));
            assertEquals(2, archive.size(), "minimal input-only archive contains no saved state");
        }
        var compiled = InputScriptCompiler.compile(Files.readString(SCRIPT));
        var text = temporary.resolve("Input Log.txt");
        Files.writeString(text, UserRecordingWriter.inputLogText(compiled));
        var loader = new Bk2MovieLoader();
        var archive = loader.loadMovieOrInputLog(MOVIE);
        var plain = loader.loadInputLog(text);
        assertEquals(ROWS, compiled.size()); assertEquals(ROWS, archive.getFrameCount());
        assertEquals(ROWS, plain.getFrameCount());
        var prefix = new StringBuilder();
        for (int frame = 0; frame < ROWS; frame++) {
            var actual = archive.getFrame(frame);
            assertEquals(actual, plain.getFrame(frame), "script/loader inverse row " + frame);
            assertEquals(0, actual.p2InputMask()); assertEquals(0, actual.p2ActionMask());
            assertFalse(actual.p2StartPressed());
            if (frame < PREFIX_ROWS) prefix.append(actual.rawLine()).append('\n');
            else {
                assertEquals(0, actual.p1InputMask(), "chosen arrival continuation is neutral");
                assertEquals(0, actual.p1ActionMask()); assertFalse(actual.p1StartPressed());
            }
        }
        assertEquals(PREFIX_ROWS_SHA, sha(prefix.toString().getBytes(StandardCharsets.UTF_8)),
                "literal inverse preserves every original R5 row, including held/edge history");
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void coldSoloKnucklesTraversesCapsuleAndReplaysTheReleasedIczHandoff() throws Exception {
        var rom = RomTestUtils.ensureSonic3kRomAvailable().toPath().toAbsolutePath().normalize();
        assertTrue(Path.of(System.getProperty("s3k.rom.path")).isAbsolute(), "explicit absolute ROM is required");
        assertEquals("fba0677fde9f76df93f3e98d6310d8af68b9847bde16e253d73cd4dd8134ed23", sha(Files.readAllBytes(rom)));
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(MOVIE);
        assertEquals(ROWS, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, true, false, null, null, false, null, false);
        var drawing = new RouteFrameDrawing(true);
        var checked = new HashSet<Integer>();
        var events = new LinkedHashMap<String, Integer>();
        int hurtSamples = 0, levelChanges = 0, outgoingHistory = 0;
        boolean sawTitle = false, sawPendingAssembly = false, historyIsolated = false;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 3, 1, settings);
            var sourceLevel = GameServices.level().getCurrentLevel();
            var currentLevel = sourceLevel;
            var sourceP1 = main();
            var sourceMovement = sourceP1.getMovementManager();
            var worldSession = SessionManager.getCurrentWorldSession();
            assertEquals(0x2c0, sourceP1.getCentreX() & 0xffff);
            assertEquals(0x6b0, sourceP1.getCentreY() & 0xffff);
            for (int frame = 0; frame < ROWS; frame++) {
                // All source raw replay windows finish before this independent live-history tail.
                if (frame == 6815) GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
                session.step(movie.getFrame(frame)); drawing.afterStep(session);
                assertPlayable(session, frame);
                assertSame(sourceP1, main(), "production retains actual P1 through this handoff");
                assertSame(sourceMovement, main().getMovementManager());
                assertSame(worldSession, SessionManager.getCurrentWorldSession());
                var level = GameServices.level();
                var controller = SessionManager.getCurrentGameplayMode().getRewindController();
                if (main().isHurt()) hurtSamples++;
                if (level.getCurrentLevel() != currentLevel) {
                    levelChanges++; assertEquals(1, levelChanges);
                    assertEquals(5, level.getCurrentZone()); assertEquals(0, level.getCurrentAct());
                    assertNotSame(sourceLevel, level.getCurrentLevel());
                    assertTrue(outgoingHistory > 10, "nonempty source history exists before the actual load");
                    assertNull(controller,
                            "pending title-card entry has no admitted live rewind floor");
                    assertTrue(level.hasPendingFreshLevelTransitionBoundary());
                    currentLevel = level.getCurrentLevel();
                    event(events, "load", frame);
                }
                if (frame >= 6815 && level.getCurrentZone() == 3 && controller != null)
                    outgoingHistory = Math.max(outgoingHistory, controller.currentFrame());
                if (level.getCurrentZone() == 3) {
                    var cnz = cnz();
                    if (cnz.isKnucklesTeleporterRouteActive()) event(events, "admission", frame);
                    if (capsulePiece() == 1) event(events, "button", frame);
                    if (results() > 0) event(events, "results", frame);
                    if (cnz.getForegroundRoutine() == 8 && GameServices.gameState().isEndOfLevelFlag()) event(events, "completion", frame);
                    if (beams() > 0) event(events, "beam", frame);
                    if ((main().getCentreY() & 0xffff) == 0 && main().isObjectControlled()) event(events, "hide", frame);
                } else {
                    assertEquals(5, level.getCurrentZone()); assertEquals(0, level.getCurrentAct());
                    if (!historyIsolated && controller != null) {
                        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(),
                                "the destination floor belongs to admitted level play");
                        assertFalse(level.isLevelInactiveForTransition());
                        assertTrue(controller.currentFrame() < outgoingHistory,
                                "the admitted destination floor has retired the outgoing timeline");
                        assertEquals(0, controller.earliestAvailableFrame(),
                                "new timeline has its own frame-zero floor");
                        historyIsolated = true;
                        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
                    }
                    sawTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
                    sawPendingAssembly |= level.hasPendingFreshLevelTransitionBoundary();
                    assertTrue(level.getObjectManager().activeObjectsOfType(CnzTeleporterInstance.class).isEmpty());
                    assertTrue(level.getObjectManager().activeObjectsOfType(IczSnowboardIntroInstance.class).isEmpty());
                    var arrival = arrival();
                    if (arrival != null && arrival.initialized() && !level.hasPendingFreshLevelTransitionBoundary()) event(events, "assembly", frame);
                    if (arrival != null && arrival.released() && !main().isObjectControlled()) event(events, "release", frame);
                }
                if (SPOTS.containsKey(frame)) {
                    assertRole(frame);
                    replayTwice(session, movie, drawing, frame, SPOTS.get(frame));
                    checked.add(frame);
                }
            }
            assertEquals(SPOTS.keySet(), checked, "every independently named role reached and replayed");
            assertEquals(Map.of("admission",4732,"button",5444,"results",5510,"completion",6047,
                    "beam",6663,"hide",6813,"load",6870,"assembly",6987,"release",7050), events);
            assertEquals(1, levelChanges); assertTrue(historyIsolated); assertTrue(sawTitle && sawPendingAssembly);
            assertReleasedFloor(session);
            assertEquals(ROWS + SPOTS.size() * WINDOW * 3L, drawing.drawnFrames(), "normal drawing for every main/forward/replay input");
            assertEquals(0, drawing.skippedFrames());
            System.out.println("CNZ2 Knuckles cold7164: events=" + events + " world45Windows=" + checked.size()
                    + " replayCycles=2 outgoingHistory=" + outgoingHistory + " allKeysCompared=true"
                    + " hurtStateSamples=" + hurtSamples + " noDeathLifeLoss=true drawn=" + drawing.drawnFrames());
        }
    }

    private static void replayTwice(GameplayCaptureSession session, Bk2Movie movie,
                                    RouteFrameDrawing drawing, int frame, String role) {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var level = GameServices.level(); var world = level.getCurrentLevel();
        assertFalse(level.hasPendingFreshLevelTransitionBoundary(), role);
        assertFalse(GameServices.fade().hasPendingCompletion(), role);
        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(), role);
        assertTrue(frame + WINDOW < movie.getFrameCount(), role);
        var saved = registry.capture();
        GameplayInputBranchTool.requireRestorableCheckpoint(saved, level.hasPendingFreshLevelTransitionBoundary());
        assertFalse(saved.entries().isEmpty());
        for (int input = 1; input <= WINDOW; input++) forward(session, movie, drawing, frame + input, world, role);
        var expected = registry.capture();
        GameplayInputBranchTool.requireRestorableCheckpoint(expected, level.hasPendingFreshLevelTransitionBoundary());
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved); same(saved, registry.capture(), role + " restore" + cycle);
            session.restoreInputHistory(movie.getFrame(frame));
            for (int input = 1; input <= WINDOW; input++) forward(session, movie, drawing, frame + input, world, role);
            same(expected, registry.capture(), role + " forward" + cycle);
        }
        registry.restore(saved); same(saved, registry.capture(), role + " return-main");
        session.restoreInputHistory(movie.getFrame(frame));
        System.out.println("CNZ2 whole-world role=" + role + " row=" + frame + " keys=" + saved.entries().size() + " window45 cycles2");
    }

    private static void forward(GameplayCaptureSession session, Bk2Movie movie, RouteFrameDrawing drawing,
                                int frame, Object world, String role) {
        session.step(movie.getFrame(frame)); drawing.draw(session);
        assertSame(world, GameServices.level().getCurrentLevel(), role + " must not cross a load");
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary(), role);
        assertFalse(GameServices.fade().hasPendingCompletion(), role + " must not enter a callback-bearing fade");
        assertPlayable(session, frame);
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context + " complete registry keys");
        for (String key : expected.entries().keySet()) assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                () -> context + " " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
    }

    private static void assertPlayable(GameplayCaptureSession session, int row) {
        var p = main();
        assertSame(p, session.player(), "actual/capture P1 at " + row);
        assertSame(p, GameServices.camera().getFocusedSprite(), "focus at " + row);
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty(), "solo at " + row);
        assertEquals(320, GameServices.camera().getWidth());
        assertEquals("NATIVE_4_3", GameServices.configuration().getString(SonicConfiguration.DISPLAY_ASPECT));
        assertFalse(p.getDead(), "death at " + row); assertEquals(3, GameServices.gameState().getLives(), "life loss/hidden respawn at " + row);
    }

    private static void assertRole(int frame) {
        var p = main();
        switch (frame) {
            case 4139,4223 -> assertFalse(p.getAir());
            case 4163 -> { assertEquals(4,p.getDoubleJumpFlag()); assertEquals(0x36f5,p.getCentreX() & 0xffff); }
            case 4174 -> { assertEquals(0,p.getDoubleJumpFlag()); assertEquals(Direction.LEFT,p.getDirection()); assertEquals(-0x400,p.getXSpeed()); assertEquals(-0x380,p.getYSpeed()); }
            case 4700 -> assertFalse(cnz().isKnucklesTeleporterRouteActive());
            case 4732 -> { assertTrue(cnz().isKnucklesTeleporterRouteActive()); assertTrue((p.getCentreX() & 0xffff)>=0x4880); assertTrue((p.getCentreY() & 0xffff)>=0xb00); }
            case 4835 -> { assertFalse(p.getAir()); assertEquals(4,cnz().getForegroundRoutine()); }
            case 5412 -> assertEquals(0,capsulePiece());
            case 5444,5480 -> assertEquals(1,capsulePiece());
            case 5510,6025 -> { assertEquals(1,results()); assertTrue(p.isObjectControlled()); assertFalse(GameServices.gameState().isEndOfLevelFlag()); }
            case 6047 -> { assertEquals(0,results()); assertTrue(GameServices.gameState().isEndOfLevelFlag()); assertEquals(8,cnz().getForegroundRoutine()); assertFalse(p.isObjectControlled()); assertFalse(p.isControlLocked()); }
            case 6645 -> { assertEquals(0x4a40,p.getCentreX() & 0xffff); assertTrue(p.getAir()); assertTrue(p.isControlLocked()); assertEquals(0,beams()); }
            case 6663 -> { assertEquals(1,beams()); assertFalse(p.getAir()); }
            case 6749,6795 -> { assertEquals(1,beams()); assertTrue(p.isObjectControlled()); assertTrue((p.getCentreY() & 0xffff)>0); }
            case 6987 -> { assertEquals(5,GameServices.level().getCurrentZone()); assertTrue(arrival().initialized()); assertFalse(arrival().released()); assertTrue(p.isObjectControlled()); }
            case 7050 -> { assertTrue(arrival().released()); assertFalse(p.isObjectControlled()); }
            case 7074,7118 -> { assertTrue(arrival().released()); assertFalse(p.getAir()); assertTrue(GameServices.level().getObjectManager().isRidingObject(p,arrival())); }
            default -> fail("unaccounted role " + frame);
        }
    }

    private static void assertReleasedFloor(GameplayCaptureSession session) {
        assertPlayable(session, ROWS-1);
        assertEquals(5,GameServices.level().getCurrentZone()); assertEquals(0,GameServices.level().getCurrentAct());
        assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
        assertFalse(GameServices.fade().isActive()); assertFalse(GameServices.fade().hasPendingCompletion());
        var p=main(); assertNotNull(arrival()); assertTrue(arrival().released());
        assertTrue(GameServices.level().getObjectManager().isRidingObject(p,arrival()));
        assertFalse(p.getAir()); assertFalse(p.isObjectControlled()); assertFalse(p.isObjectControlSuppressesMovement()); assertFalse(p.isControlLocked()); assertFalse(GameServices.camera().getFrozen());
        assertEquals(0x3640,p.getCentreX() & 0xffff); assertEquals(0x65c,p.getCentreY() & 0xffff);
        assertEquals(0,p.getXSpeed()); assertEquals(0,p.getYSpeed()); assertEquals(0,p.getGSpeed()); assertEquals(0,p.getRingCount());
        assertEquals(0x35a0,GameServices.camera().getX() & 0xffff); assertEquals(0x5fc,GameServices.camera().getY() & 0xffff);
    }

    private static Knuckles main() { return assertInstanceOf(Knuckles.class,GameServices.sprites().getMainPlayable()); }
    private static com.openggf.game.sonic3k.events.Sonic3kCNZEvents cnz() { return ((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).getCnzEvents(); }
    private static int capsulePiece() { var objects=GameServices.level().getObjectManager();for(var capsule:objects.activeObjectsOfType(CnzEggCapsuleInstance.class))if(objects.isRidingObject(main(),capsule))return objects.getRidingPieceIndex(main());return -1; }
    private static int results() { return GameServices.level().getObjectManager().activeObjectsOfType(S3kResultsScreenObjectInstance.class).size(); }
    private static int beams() { return GameServices.level().getObjectManager().activeObjectsOfType(TeleporterBeamObjectInstance.class).size(); }
    private static IczTeleporterInstance arrival() { return GameServices.level().getObjectManager().activeObjectsOfType(IczTeleporterInstance.class).stream().findFirst().orElse(null); }
    private static void event(Map<String,Integer> events,String name,int frame) { events.putIfAbsent(name,frame); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
