package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.recording.UserRecordingWriter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.objects.CollapsingBridgeObjectInstance;
import com.openggf.game.sonic3k.objects.CorkFloorObjectInstance;
import com.openggf.game.sonic3k.objects.IczIceCubeObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kButtonObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kPathSwapObjectInstance;
import com.openggf.level.Level;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.Sonic;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ordinary native320 solo Sonic/off ICZ2 partial route, task 2026-10-21.
 * Obj_PathSwap, Obj_CorkFloor, sub_8B3AA/loc_8B3D2, Obj_Button and loc_20B40
 * own the observed path/cork/cube/button2/bridge chain. Input indices are movie
 * observations, never runtime clocks. No boss, full-act or native-pixel claim.
 */
@Isolated
class TestIcz2ColdCubeBridgeRoute {
    private static final Path MOVIE = Path.of("src/test/resources/routes/s3k/icz2-sonic-cold-cube-button-bridge-320.bk2");
    private static final Path SCRIPT = Path.of("src/test/resources/routes/s3k/icz2-sonic-cold-cube-button-bridge-320.script");
    private static final int ROWS = 2292, WINDOW = 45;
    private static final Map<Integer, String> SPOTS = Map.of(
            1664, "before-path-and-corks", 1709, "path-switched-before-cork-break",
            1724, "first-cork-shattered", 1733, "both-corks-shattered", 1778, "after-cork-drop",
            2040, "before-cube-jump", 2095, "cube-shatter-bounce", 2131, "button2-standing",
            2183, "bridge-first-fall", 2246, "forward-through-grounded-crossing");

    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
        TestEnvironment.resetAll();
    }

    @Test void inputOnlyMovieRoundTripsEveryRowAndRetainsThe2040InputPrefix(@TempDir Path temporary) throws Exception {
        assertEquals("e176974a99e1c8d61746149765272c188759deaefbf8f8731d4b00535c58ed55", sha(Files.readAllBytes(MOVIE)));
        assertEquals("e44eaf90a6d327c1dbcaef557dabc87281eaad0ef1799f02691a4d33b6fdef96", sha(Files.readAllBytes(SCRIPT)));
        try (var archive = new ZipFile(MOVIE.toFile())) {
            assertEquals(2, archive.size());
            assertEquals(Set.of("Header.txt", "Input Log.txt"), archive.stream()
                    .map(java.util.zip.ZipEntry::getName).collect(java.util.stream.Collectors.toSet()));
        }
        var compiled = InputScriptCompiler.compile(Files.readString(SCRIPT));
        var inputLog = temporary.resolve("Input Log.txt");
        Files.writeString(inputLog, UserRecordingWriter.inputLogText(compiled));
        var loader = new Bk2MovieLoader();
        var movie = loader.loadMovieOrInputLog(MOVIE);
        var plain = loader.loadInputLog(inputLog);
        assertEquals(ROWS, compiled.size()); assertEquals(ROWS, movie.getFrameCount());
        assertEquals(ROWS, plain.getFrameCount());
        var prefix = new StringBuilder();
        for (int row = 0; row < ROWS; row++) {
            var input = movie.getFrame(row);
            assertEquals(input, plain.getFrame(row), "both-pad production script/loader inverse " + row);
            assertEquals(0, input.p2InputMask() | input.p2ActionMask());
            assertFalse(input.p1StartPressed() || input.p2StartPressed());
            assertFalse(input.debugModeTogglePressed() || input.debugShiftDown() || input.debugControlDown()
                    || input.debugAltDown() || input.debugSuperDown());
            if (row < 2040) prefix.append(input.rawLine()).append('\n');
        }
        // Independently compared with retained attempt09/variant-0, before this leaf.
        assertEquals("3135b2b423c4e5f7e1d9bc1482b1bc6509c20aa3fc08792c1013bf734393de4b",
                sha(prefix.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void coldSonicShattersThePlacedCubePressesButton2AndCrossesTheFallingBridgeWithWorldReplay() throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(MOVIE);
        assertEquals(ROWS, movie.getFrameCount());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, true, false, null, null, false, null, false);
        var drawing = new RouteFrameDrawing(true);
        var events = new LinkedHashMap<String, Integer>();
        var checked = new HashSet<Integer>();
        var lineage = new HashMap<Integer, ObjectRefId>();
        var rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        assertTrue(Path.of(System.getProperty("s3k.rom.path")).isAbsolute());
        assertEquals("fba0677fde9f76df93f3e98d6310d8af68b9847bde16e253d73cd4dd8134ed23", sha(Files.readAllBytes(rom)));
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 5, 1, settings);
            assertEquals(0x7E0, session.player().getCentreX()); assertEquals(0x370, session.player().getCentreY());
            var world = new World(SessionManager.getCurrentGameplayMode().getWorldSession(),
                    GameServices.level().getCurrentLevel(), GameServices.level().getObjectManager());
            placement(world, 157, 0x02, 0x2180, 0x0600, 0x91);
            placement(world, 162, 0xB6, 0x2240, 0x08F4, 0);
            placement(world, 163, 0x33, 0x2240, 0x08FC, 2);
            placement(world, 165, 0x0F, 0x22C0, 0x0911, 0x82);
            placement(world, 150, 0x2A, 0x2130, 0x0688, 0);
            placement(world, 151, 0x2A, 0x2130, 0x0690, 2);
            for (int row = 0; row < ROWS; row++) {
                var cork150 = placed(world, lineage, 150, CorkFloorObjectInstance.class);
                var cork151 = placed(world, lineage, 151, CorkFloorObjectInstance.class);
                var cube = placed(world, lineage, 162, IczIceCubeObjectInstance.class);
                var button = placed(world, lineage, 163, Sonic3kButtonObjectInstance.class);
                var bridge = placed(world, lineage, 165, CollapsingBridgeObjectInstance.class);
                boolean cubeAlive = cube != null && !cube.isDestroyed();
                boolean firstCorkIntact = cork150 != null && !cork150.isBroken();
                boolean secondCorkIntact = cork151 != null && !cork151.isBroken();
                int animation = session.player().getAnimationId(), ySpeed = session.player().getYSpeed();
                int top = session.player().getTopSolidBit(), lrb = session.player().getLrbSolidBit();
                int bridgeY = bridge == null ? -1 : bridge.getY();
                var path = row == 1709 ? placed(world, lineage, 157, Sonic3kPathSwapObjectInstance.class) : null;
                session.step(movie.getFrame(row)); drawing.draw(session);
                sameWorld(session, world, row);
                var afterButton = placed(world, lineage, 163, Sonic3kButtonObjectInstance.class);
                var afterBridge = placed(world, lineage, 165, CollapsingBridgeObjectInstance.class);
                if (button != null && afterButton != null) assertSame(button, afterButton, "normal button update " + row);
                if (bridge != null && afterBridge != null) assertSame(bridge, afterBridge, "normal bridge update " + row);
                if (row == 1709) {
                    assertNotNull(path);
                    assertSame(path, placed(world, lineage, 157, Sonic3kPathSwapObjectInstance.class));
                    assertEquals(0xC, top); assertEquals(0xD, lrb);
                    assertEquals(0xE, session.player().getTopSolidBit()); assertEquals(0xF, session.player().getLrbSolidBit());
                    events.put("path", row);
                }
                if (firstCorkIntact && cork150.isBroken()) event(events, "cork150", row);
                if (secondCorkIntact && cork151.isBroken()) event(events, "cork151", row);
                if (cubeAlive && cube.isDestroyed()) {
                    event(events, "cube", row);
                    assertEquals(2, animation, "sub_8B3AA saved roll animation");
                    assertTrue(ySpeed >= 0, "descending top contact");
                    assertTrue(Math.abs(session.player().getCentreX() - cube.getSpawn().x()) <= 0x18);
                    assertEquals(-0x300, session.player().getYSpeed(), "loc_8B3D2 bounce");
                    assertTrue(session.player().getAir());
                }
                if (row >= 2040 && Sonic3kLevelTriggerManager.testBit(2, 0) && !events.containsKey("button2")) {
                    event(events, "button2", row); assertTrue(events.containsKey("cube"));
                    assertNotNull(button); assertSame(button, afterButton);
                    assertSame(button, world.objects().getRidingObject(session.player()));
                    assertTrue(world.objects().hasObjectStandingBit(session.player(), button));
                }
                if (afterBridge != null && afterBridge.getY() > 0x911 && !events.containsKey("bridge")) {
                    event(events, "bridge", row); assertTrue(events.containsKey("button2"));
                    assertSame(bridge, afterBridge); assertEquals(0x911, bridgeY); assertEquals(0x912, afterBridge.getY());
                }
                if (events.containsKey("bridge") && !events.containsKey("crossing") && !session.player().getAir()
                        && session.player().getCentreX() > 0x22C0 + 0x50 + session.player().getXRadius()) {
                    event(events, "crossing", row);
                }
                if (SPOTS.containsKey(row)) {
                    replayTwice(session, movie, drawing, world, row, SPOTS.get(row)); checked.add(row);
                }
            }
            assertEquals(Map.of("path", 1709, "cork150", 1724, "cork151", 1732, "cube", 2095,
                    "button2", 2131, "bridge", 2183, "crossing", 2276), events);
            assertEquals(SPOTS.keySet(), checked);
            assertEquals(9023, session.player().getCentreX()); assertEquals(2544, session.player().getCentreY());
            assertEquals(713, session.player().getXSpeed()); assertEquals(0, session.player().getYSpeed());
            assertEquals(6, session.player().getRingCount());
            assertFalse(session.player().getAir() || session.player().isHurt() || session.player().isControlLocked()
                    || session.player().isObjectControlled());
            assertEquals(8863, GameServices.camera().getX()); assertEquals(2448, GameServices.camera().getY());
            assertEquals(ROWS + SPOTS.size() * WINDOW * 3L, drawing.drawnFrames()); assertEquals(0, drawing.skippedFrames());
            System.out.println("ICZ2 partial cold2292 events=" + events + " fullRegistryWindows=" + checked.size()
                    + " cycles=2 window=45 allKeysCompared=true endpoint=9023,2544 rings6 lives3 drawn=" + drawing.drawnFrames());
        }
    }

    private static void replayTwice(GameplayCaptureSession session, Bk2Movie movie, RouteFrameDrawing drawing,
                                    World world, int row, String role) {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        assertTrue(row + WINDOW < ROWS, role + " has 45 genuine future inputs");
        safeBoundary(session, role);
        var saved = registry.capture();
        GameplayInputBranchTool.requireRestorableCheckpoint(saved, GameServices.level().hasPendingFreshLevelTransitionBoundary());
        assertFalse(saved.entries().isEmpty());
        var identities = new HashSet<ObjectRefId>();
        var query = world.objects().objectQuery();
        for (var object : world.objects().getActiveObjects()) identities.add(query.identityOf(object).orElseThrow());
        for (int n = 1; n <= WINDOW; n++) forward(session, movie, drawing, world, row + n, role);
        var expected = registry.capture();
        GameplayInputBranchTool.requireRestorableCheckpoint(expected, GameServices.level().hasPendingFreshLevelTransitionBoundary());
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved); same(saved, registry.capture(), role + " restore " + cycle);
            for (var identity : identities) assertEquals(identity,
                    query.identityOf(query.resolve(identity).orElseThrow()).orElseThrow(), "restored registry lineage");
            session.restoreInputHistory(movie.getFrame(row));
            for (int n = 1; n <= WINDOW; n++) forward(session, movie, drawing, world, row + n, role);
            same(expected, registry.capture(), role + " replay " + cycle);
        }
        registry.restore(saved); same(saved, registry.capture(), role + " return main");
        session.restoreInputHistory(movie.getFrame(row));
        System.out.println("ICZ2 whole-world role=" + role + " row=" + row + " keys=" + saved.entries().size() + " window45 cycles2");
    }

    private static void forward(GameplayCaptureSession session, Bk2Movie movie, RouteFrameDrawing drawing,
                                World world, int row, String role) {
        session.step(movie.getFrame(row)); drawing.draw(session); sameWorld(session, world, row); safeBoundary(session, role);
    }

    private static void safeBoundary(GameplayCaptureSession session, String role) {
        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(), role);
        assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary(), role);
        assertFalse(GameServices.fade().hasPendingCompletion(), role);
    }

    private static void sameWorld(GameplayCaptureSession session, World world, int row) {
        assertSame(world.session(), SessionManager.getCurrentGameplayMode().getWorldSession());
        assertSame(world.level(), GameServices.level().getCurrentLevel()); assertSame(world.objects(), GameServices.level().getObjectManager());
        assertEquals(5, GameServices.level().getCurrentZone()); assertEquals(1, GameServices.level().getCurrentAct());
        assertInstanceOf(Sonic.class, session.player()); assertSame(session.player(), GameServices.sprites().getMainPlayable());
        assertSame(session.player(), GameServices.camera().getFocusedSprite());
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        assertFalse(CrossGameFeatureProvider.isActive(), "donor remains off");
        assertEquals(320, GameServices.camera().getWidth()); assertEquals(224, GameServices.camera().getHeight());
        assertEquals("NATIVE_4_3", GameServices.configuration().getString(SonicConfiguration.DISPLAY_ASPECT));
        assertFalse(session.player().getDead(), "ordinary death at " + row);
        assertEquals(3, GameServices.gameState().getLives(), "no life loss or hidden restart " + row);
        assertFalse(session.player().isDebugMode() || session.player().isSuperSonic());
    }

    private static <T extends ObjectInstance> T placed(World world, Map<Integer, ObjectRefId> lineage, int index, Class<T> type) {
        T found = null;
        var query = world.objects().objectQuery();
        for (var object : world.objects().getActiveObjects()) {
            if (object.getSpawn() == null || object.getSpawn().layoutIndex() != index || !type.isInstance(object)) continue;
            assertNull(found, "unique actual placement " + index); found = type.cast(object);
            assertEquals(world.level().getObjects().get(index), object.getSpawn());
            var identity = query.identityOf(object).orElseThrow();
            var previous = lineage.putIfAbsent(index, identity);
            if (previous != null) assertEquals(previous, identity, "same placed lineage " + index);
            assertSame(object, query.resolve(identity).orElseThrow());
        }
        return found;
    }

    private static void placement(World world, int index, int id, int x, int y, int subtype) {
        var spawn = world.level().getObjects().get(index);
        assertEquals(index, spawn.layoutIndex()); assertEquals(id, spawn.objectId()); assertEquals(subtype, spawn.subtype());
        assertEquals(x, spawn.x()); assertEquals(y, spawn.y());
    }

    private static void event(Map<String, Integer> events, String name, int row) {
        assertNull(events.putIfAbsent(name, row), "one actual " + name + " event");
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context + " complete keys");
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), context + " " + key + ": " + differences);
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record World(Object session, Level level, ObjectManager objects) { }
}
