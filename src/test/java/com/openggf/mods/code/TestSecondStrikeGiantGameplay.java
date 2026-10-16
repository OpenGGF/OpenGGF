package com.openggf.mods.code;

import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.*;
import com.openggf.game.save.SavePaths;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.Sonic3kSSEntryRingObjectInstance;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageComparisonState;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageManager;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.BigRingReturnState;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.InputLogAuthorTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Positioned native giant-ring/played Blue Sphere/emerald-return witness from the
 * 2026-10-15 Second Strike AIZ M3 task. Uses an actual compiled candidate, original
 * ROM and ordinary pad input; never invokes entry, debug completion or reward owners.
 * The controller reads live board cells and emits only ordinary pad input.
 */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "secondstrike.giant.jar", matches = ".+")
class TestSecondStrikeGiantGameplay {
    private static final String CANDIDATE_SHA256 = "aac1f34d8e3c0b511e4efdd8c942823e3933e6314eacd8ab342d7a6870db22e5";
    private static final String ROM_SHA1 = "cfbf98c36c776677290a872547ac47c53d2761d6";
    private static final int RING_X = 0x1bc8, RING_Y = 0x4c0, RING_LAYOUT = 25, RING_BIT = 1;
    @TempDir Path temporary;

    @Test void originalGiantPlaysModifiedBlueSphereAndReturnsWithOneEmerald() throws Exception {
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        Path rom = Path.of(System.getProperty("s3k.rom.path")).toAbsolutePath();
        Path jar = Path.of(System.getProperty("secondstrike.giant.jar")).toAbsolutePath();
        assertEquals(ROM_SHA1, digest(rom, "SHA-1"));
        assertEquals(CANDIDATE_SHA256, digest(jar, "SHA-256"));
        byte[] romBytes = Files.readAllBytes(rom);
        byte[] nativeBoard = java.util.Arrays.copyOfRange(romBytes, 0x26a0e0, 0x26a4e8);
        Path output = Path.of(System.getProperty("secondstrike.giant.output",
                temporary.resolve("positioned-giant").toString())).toAbsolutePath();
        Files.createDirectories(output);
        assertFalse(Files.exists(output.resolve("state.csv")), "preserve previous attempts");
        Path saves = Files.createDirectories(output.resolve("saves"));
        Path preferences = Files.createDirectories(output.resolve("preferences"));
        String oldDirectory = System.getProperty("user.dir"), oldSaveRoot = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty("user.dir", preferences.toString());
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        // Declared cave approach beside the original giant, above ScreenInit's
        // $0390+$E0 bottom plane. Native gravity/events own all subsequent descent
        // and boundary growth; this is not proof of cold access to the cave.
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null,
                RING_X - 32, 0x450);
        try (var session = new GameplayCaptureSession(settings); var driver = new Driver(session, output)) {
            session.applyMod(jar); session.boot(rom, 0, 0, settings);
            var state = GameServices.gameState();
            assertEquals(0, state.getEmeraldCount()); assertEquals(0, state.getCurrentSpecialStageIndex());
            assertEquals(0, player().getRingCount()); assertFalse(state.isSpecialRingCollected(RING_BIT));
            var ring = GameServices.level().getCurrentLevel().getObjects().stream()
                    .filter(spawn -> spawn.layoutIndex() == RING_LAYOUT).findFirst().orElseThrow();
            assertEquals(RING_X, ring.x()); assertEquals(RING_Y, ring.y());
            assertEquals(0x85, ring.objectId()); assertEquals(1, ring.subtype()); assertNull(ring.ownerModId());
            boolean admitted = false;
            driver.record("positioned-start", "-");
            for (int tick = 0; tick < 64 && session.loop().getCurrentGameMode() == GameMode.LEVEL; tick++) {
                driver.step("positioned-cave-descent", 0, false);
                admitted |= giantIsAdmitted();
            }
            driver.picture("native-cave-floor");
            for (int tick = 0; tick < 720 && session.loop().getCurrentGameMode() == GameMode.LEVEL; tick++) {
                if (!admitted && giantIsAdmitted()) { admitted = true; driver.picture("native-giant-admitted"); }
                driver.step("ordinary-giant-contact", steer(RING_X),
                        !player().getAir() && tick % 32 < 20);
            }
            assertTrue(admitted, "ordinary descent/approach must actually admit native giant25");
            if (session.loop().getCurrentGameMode() != GameMode.SPECIAL_STAGE) driver.picture("failed-native-entry");
            assertEquals(GameMode.SPECIAL_STAGE, session.loop().getCurrentGameMode(), "ordinary contact must actually enter the native stage");
            var provider = assertInstanceOf(Sonic3kSpecialStageProvider.class, session.loop().getActiveSpecialStageProvider());
            assertEquals(0, provider.getCurrentStage()); assertEquals(1, state.getCurrentSpecialStageIndex());
            assertTrue(state.isSpecialRingCollected(RING_BIT));
            BigRingReturnState saved = GameServices.level().getBigRingReturn();
            assertNotNull(saved); assertEquals(RING_X, saved.playerX()); assertEquals(RING_Y, saved.playerY());
            assertEquals(0, saved.originZone()); assertEquals(0, saved.originAct()); assertEquals(0, saved.rings());
            boolean boardObserved = false, playedPicture = false, awarded = false;
            int entryEmeralds = state.getEmeraldCount();
            for (int played = 0; played < 9000 && session.loop().getCurrentGameMode() == GameMode.SPECIAL_STAGE; played++) {
                var manager = provider.getManager();
                if (!boardObserved && provider.isInitialized() && manager.getSpheresLeft() > 0) {
                    assertEquals(102, manager.getSpheresLeft());
                    int changes = 0;
                    try (var board = Files.newBufferedWriter(output.resolve("initial-board.csv"))) {
                        board.write("x,y,native,candidate\n");
                        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                            int original = nativeBoard[y * 32 + x] & 0xff, actual = manager.getGrid().getCell(x, y);
                            board.write(x + "," + y + "," + original + "," + actual + "\n");
                            if (original != actual) { assertEquals(0, original); assertEquals(1, actual); changes++; }
                        }
                    }
                    assertEquals(12, changes, "actual candidate board0 must contain the authored red gates");
                    boardObserved = true;
                }
                int direction = provider.isInitialized() ? ordinaryBoardDirection(manager) : 0;
                driver.step("played-modified-blue-sphere", direction, false);
                if (!playedPicture && played >= 240 && provider.isInitialized()) {
                    driver.picture("native-blue-sphere-active"); playedPicture = true;
                }
                if (!awarded && state.getEmeraldCount() > entryEmeralds) {
                    assertEquals(entryEmeralds + 1, state.getEmeraldCount());
                    assertTrue(state.hasEmerald(0)); assertTrue(provider.isEmeraldCollected());
                    assertEquals(0, provider.getManager().getSpheresLeft());
                    driver.picture("native-earned-emerald"); awarded = true;
                }
            }
            assertTrue(boardObserved, "candidate board must be admitted through the native provider");
            assertTrue(awarded, "ordinary play must earn the native emerald; inspect state.csv");
            assertEquals(GameMode.SPECIAL_STAGE_RESULTS, session.loop().getCurrentGameMode());
            boolean resultsPicture = false;
            for (int tick = 0; tick < 2400 && session.loop().getCurrentGameMode() != GameMode.LEVEL; tick++) {
                driver.step("native-results-return", 0, false);
                if (!resultsPicture && tick >= 120 && session.loop().getCurrentGameMode() == GameMode.SPECIAL_STAGE_RESULTS) {
                    driver.picture("native-results"); resultsPicture = true;
                }
            }
            assertTrue(resultsPicture); assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertEquals(0, GameServices.level().getCurrentZone()); assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(0, GameServices.level().getApparentAct());
            assertEquals(saved.playerX(), player().getCentreX()); assertEquals(saved.playerY(), player().getCentreY());
            assertEquals(saved.rings(), player().getRingCount(), "native Saved2 restores the ordinary-level ring total");
            assertEquals(saved.checkpointIndex(), GameServices.level().getCheckpointState().getLastCheckpointIndex());
            assertNull(GameServices.level().getBigRingReturn(), "Saved2 return must be consumed");
            assertEquals(entryEmeralds + 1, state.getEmeraldCount()); assertEquals(1, state.getCurrentSpecialStageIndex());
            assertTrue(state.isSpecialRingCollected(RING_BIT));
            assertTrue(giants().stream().noneMatch(object -> object.getSpawn().layoutIndex() == RING_LAYOUT));
            int score = state.getScore(), lives = state.getLives(), rings = player().getRingCount();
            for (int tick = 0; tick < 150; tick++) {
                driver.step("return-stability", 0, false);
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
                assertEquals(entryEmeralds + 1, state.getEmeraldCount()); assertEquals(1, state.getCurrentSpecialStageIndex());
                assertEquals(score, state.getScore()); assertEquals(lives, state.getLives()); assertEquals(rings, player().getRingCount());
                assertTrue(state.isSpecialRingCollected(RING_BIT));
                assertTrue(giants().stream().noneMatch(object -> object.getSpawn().layoutIndex() == RING_LAYOUT));
            }
            driver.picture("native-return-stable");
            var saveSession = SessionManager.getCurrentWorldSession().getSaveSessionContext();
            assertTrue(saveSession == null || saveSession.activeSlot().isEmpty(), "No Save does not prove durable-slot progression");
        } finally { restore("user.dir", oldDirectory); restore(SavePaths.ROOT_PROPERTY, oldSaveRoot); }
    }

    /** Fresh native slot creation, earned progression and fresh-process menu reload. */
    @Test void nativeSlotPersistsPlayedGiantEmeraldAcrossFreshMenuSession() throws Exception {
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        Path rom = Path.of(System.getProperty("s3k.rom.path")).toRealPath();
        Path jar = Path.of(System.getProperty("secondstrike.giant.jar")).toRealPath();
        assertEquals(ROM_SHA1, digest(rom, "SHA-1")); assertEquals(CANDIDATE_SHA256, digest(jar, "SHA-256"));
        Path task = jar.getParent(); // Attempt artifacts stay beside the explicit immutable candidate.
        Path output = Path.of(System.getProperty("secondstrike.giant.durable.output")).toAbsolutePath().normalize();
        assertEquals(task.toRealPath(), output.getParent().toRealPath());
        assertFalse(Files.exists(output), "each durable attempt requires a new isolated process directory");
        Files.createDirectory(output);
        Files.createDirectory(output.resolve("saves"));
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        for (String entry : classpath.split(java.io.File.pathSeparator)) assertTrue(Path.of(entry).isAbsolute());
        for (String phase : List.of("earn", "reload")) {
            Path phaseOutput = Files.createDirectory(output.resolve(phase));
            Path scratch = Files.createDirectory(phaseOutput.resolve("tmp"));
            var command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Xmx3g", "-Xshare:off", "--enable-native-access=ALL-UNNAMED",
                    "-Duser.dir=" + output, "-Dopenggf.saveRoot=" + output.resolve("saves"),
                    "-Djava.io.tmpdir=" + scratch, "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + scratch.resolve("lwjgl"),
                    "-Dopenggf.headless.gl=surfaceless-egl", "-Ds3k.rom.path=" + rom,
                    "-Dsecondstrike.giant.jar=" + jar, "-cp", classpath,
                    TestSecondStrikeGiantGameplay.class.getName(), phase, output.toString());
            Process child = new ProcessBuilder(command).directory(output.toFile()).redirectErrorStream(true).start();
            var tail = new java.util.ArrayDeque<String>();
            var readFailure = new java.util.concurrent.atomic.AtomicReference<java.io.IOException>();
            var warnings = new java.util.concurrent.atomic.AtomicInteger();
            Thread reader = Thread.ofPlatform().name("owned-native-save-output").start(() -> {
                try (var lines = new java.io.BufferedReader(new java.io.InputStreamReader(child.getInputStream()))) {
                    for (String line; (line = lines.readLine()) != null;) {
                        if (line.contains("WARNING:")) warnings.incrementAndGet();
                        synchronized (tail) { if (tail.size() == 32) tail.removeFirst(); tail.addLast(line); }
                    }
                } catch (java.io.IOException failure) { readFailure.set(failure); }
            });
            try {
                assertTrue(child.waitFor(90, java.util.concurrent.TimeUnit.SECONDS), "native durable " + phase + " JVM timed out");
                reader.join(5000); assertFalse(reader.isAlive()); assertNull(readFailure.get());
                String diagnostic; synchronized (tail) { diagnostic = String.join("\n", tail); }
                assertEquals(0, child.exitValue(), "native durable " + phase + " JVM failed; inspect its state.csv\n" + diagnostic);
                System.err.println("Native durable " + phase + " completed with " + warnings.get()
                        + " warning lines; native load-time profiles use deterministic estimates.");
            } finally {
                if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS); }
                reader.join(5000);
            }
        }
    }

    /** Called only by the queued test's two owned, isolated fresh JVMs. */
    public static void main(String[] arguments) throws Exception {
        assertEquals(2, arguments.length);
        String phase = arguments[0]; assertTrue(List.of("earn", "reload").contains(phase));
        Path root = Path.of(arguments[1]).toAbsolutePath().normalize();
        assertEquals(root, root.toRealPath(), "no linked task directory");
        assertEquals(root, Path.of("/proc/self/cwd").toRealPath(), "actual process cwd must be isolated before Engine");
        assertEquals(root, Path.of(System.getProperty("user.dir")).toRealPath());
        assertEquals(root.resolve("saves"), SavePaths.root().toRealPath());
        assertFalse(Files.isSymbolicLink(root.resolve("saves")));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        Path rom = Path.of(System.getProperty("s3k.rom.path")).toRealPath();
        Path jar = Path.of(System.getProperty("secondstrike.giant.jar")).toRealPath();
        assertEquals(ROM_SHA1, digest(rom, "SHA-1")); assertEquals(CANDIDATE_SHA256, digest(jar, "SHA-256"));
        Path output = root.resolve(phase);
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings); var driver = new Driver(session, output)) {
            session.applyMod(jar); session.startAtTitle(); session.boot(rom, 0, 0, settings);
            driver.record("native-title-start", "-");
            for (int tick = 0; tick < 1800 && session.loop().getCurrentGameMode() == GameMode.TITLE_SCREEN; tick++)
                driver.step("ordinary-title-confirm", 0, tick % 30 == 0);
            assertEquals(GameMode.DATA_SELECT, session.loop().getCurrentGameMode());
            for (int tick = 0; tick < 60; tick++) driver.step("native-menu-ready", 0, false);
            var menu = assertInstanceOf(com.openggf.game.dataselect.DataSelectPresentationProvider.class,
                    session.loop().getDataSelectProvider()).controller();
            assertEquals(0, menu.model().getSelectedRow());
            var before = menu.slotSummaries().getFirst();
            assertEquals(phase.equals("earn") ? com.openggf.game.save.SaveSlotState.EMPTY
                    : com.openggf.game.save.SaveSlotState.VALID, before.state());
            driver.step("ordinary-select-slot-one", AbstractPlayableSprite.INPUT_RIGHT, false);
            for (int tick = 0; tick < 60; tick++) driver.step("native-slot-selector-movement", 0, false);
            assertEquals(1, menu.model().getSelectedRow());
            if (phase.equals("earn")) {
                driver.step("ordinary-select-sonic-solo", AbstractPlayableSprite.INPUT_UP, false);
                driver.step("ordinary-release-team-choice", 0, false);
            }
            if (phase.equals("earn")) {
                assertEquals("sonic", menu.currentTeam().mainCharacter()); assertTrue(menu.currentTeam().sidekicks().isEmpty());
            } else {
                // Loaded slots use the payload team in confirmSelection(); currentTeam()
                // still describes the menu's fresh-game team index, not that payload.
                assertEquals("sonic", before.payload().get("mainCharacter"));
                assertEquals(List.of(), before.payload().get("sidekicks"));
            }
            driver.picture("native-slot-one-menu");
            driver.step("ordinary-native-slot-confirm", 0, true);
            for (int tick = 0; tick < 1200 && session.loop().getCurrentGameMode() == GameMode.DATA_SELECT; tick++)
                driver.step("native-menu-launch", 0, false);
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            var save = SessionManager.getCurrentWorldSession().getSaveSessionContext();
            assertNotNull(save); assertEquals(1, save.activeSlot().orElseThrow());
            assertEquals("sonic", save.selectedTeam().mainCharacter()); assertTrue(save.selectedTeam().sidekicks().isEmpty());
            for (int tick = 0; tick < 240; tick++) driver.step("native-intro-control-clear", 0, false);
            assertFalse(player().isControlLocked()); assertFalse(player().isObjectControlled()); assertFalse(player().getAir());
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            var state = GameServices.gameState();
            if (phase.equals("reload")) {
                assertEquals(1, state.getEmeraldCount()); assertTrue(state.hasEmerald(0));
                assertEquals(0, GameServices.level().getCurrentZone()); assertEquals(0, GameServices.level().getCurrentAct());
                assertEquals(before.payload().get("lives"), state.getLives());
                for (int tick = 0; tick < 120; tick++) {
                    driver.step("fresh-loaded-native-play", 0, false);
                    assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
                    assertEquals(1, state.getEmeraldCount()); assertTrue(state.hasEmerald(0));
                }
                observeNativeSlot(output, "fresh-native-menu-load");
                driver.picture("native-loaded-emerald-stable");
                return;
            }
            assertEquals(0, state.getEmeraldCount()); assertEquals(0, player().getRingCount());
            assertEquals(0, player().getXSpeed()); assertEquals(0, player().getYSpeed());
            int lives = state.getLives();
            // The one declared positioned setup occurs only after native NEW_SLOT_START
            // and intro control release. No pose, phase, save or reward writes follow.
            com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(player(), RING_X - 32);
            com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(player(), 0x450);
            GameServices.camera().updatePosition(true);
            driver.record("declared-post-menu-position", "-");
            boolean admitted = false;
            for (int tick = 0; tick < 64; tick++) {
                driver.step("positioned-cave-descent", 0, false); admitted |= giantIsAdmitted();
            }
            driver.picture("native-cave-floor");
            for (int tick = 0; tick < 720 && session.loop().getCurrentGameMode() == GameMode.LEVEL; tick++) {
                admitted |= giantIsAdmitted();
                driver.step("ordinary-giant-contact", steer(RING_X), !player().getAir() && tick % 32 < 20);
            }
            assertTrue(admitted); assertEquals(GameMode.SPECIAL_STAGE, session.loop().getCurrentGameMode());
            var provider = assertInstanceOf(Sonic3kSpecialStageProvider.class, session.loop().getActiveSpecialStageProvider());
            assertEquals(0, provider.getCurrentStage()); assertTrue(state.isSpecialRingCollected(RING_BIT));
            BigRingReturnState saved = GameServices.level().getBigRingReturn();
            assertNotNull(saved); assertEquals(RING_X, saved.playerX()); assertEquals(RING_Y, saved.playerY());
            assertEquals(0, saved.rings());
            boolean boardObserved = false, awarded = false;
            byte[] romBytes = Files.readAllBytes(rom);
            for (int tick = 0; tick < 9000 && session.loop().getCurrentGameMode() == GameMode.SPECIAL_STAGE; tick++) {
                var manager = provider.getManager();
                if (!boardObserved && provider.isInitialized() && manager.getSpheresLeft() > 0) {
                    assertEquals(102, manager.getSpheresLeft()); int changes = 0;
                    try (var board = Files.newBufferedWriter(output.resolve("initial-board.csv"))) {
                        board.write("x,y,native,candidate\n");
                        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                            int original = romBytes[0x26a0e0 + y * 32 + x] & 0xff, actual = manager.getGrid().getCell(x, y);
                            board.write(x + "," + y + "," + original + "," + actual + "\n");
                            if (original != actual) { assertEquals(0, original); assertEquals(1, actual); changes++; }
                        }
                    }
                    assertEquals(12, changes); boardObserved = true;
                }
                driver.step("played-modified-blue-sphere", provider.isInitialized() ? ordinaryBoardDirection(manager) : 0, false);
                if (!awarded && state.getEmeraldCount() > 0) {
                    assertEquals(1, state.getEmeraldCount()); assertTrue(state.hasEmerald(0));
                    assertTrue(provider.isEmeraldCollected()); assertEquals(0, provider.getManager().getSpheresLeft());
                    driver.picture("native-earned-emerald"); awarded = true;
                }
            }
            assertTrue(boardObserved); assertTrue(awarded, "native play must earn the emerald before save/return");
            assertEquals(GameMode.SPECIAL_STAGE_RESULTS, session.loop().getCurrentGameMode());
            boolean resultsPicture = false;
            for (int tick = 0; tick < 2400 && session.loop().getCurrentGameMode() != GameMode.LEVEL; tick++) {
                driver.step("native-results-return", 0, false);
                if (!resultsPicture && tick >= 120 && session.loop().getCurrentGameMode() == GameMode.SPECIAL_STAGE_RESULTS) {
                    driver.picture("native-results"); resultsPicture = true;
                }
            }
            assertTrue(resultsPicture); assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertEquals(saved.playerX(), player().getCentreX()); assertEquals(saved.playerY(), player().getCentreY());
            assertEquals(saved.rings(), player().getRingCount()); assertEquals(lives, state.getLives());
            assertEquals(saved.checkpointIndex(), GameServices.level().getCheckpointState().getLastCheckpointIndex());
            assertNull(GameServices.level().getBigRingReturn());
            int score = state.getScore();
            for (int tick = 0; tick < 150; tick++) {
                driver.step("return-stability", 0, false);
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
                assertEquals(1, state.getEmeraldCount()); assertTrue(state.hasEmerald(0));
                assertEquals(1, state.getCurrentSpecialStageIndex()); assertTrue(state.isSpecialRingCollected(RING_BIT));
                assertEquals(score, state.getScore()); assertEquals(lives, state.getLives()); assertEquals(saved.rings(), player().getRingCount());
                assertTrue(giants().stream().noneMatch(object -> object.getSpawn().layoutIndex() == RING_LAYOUT));
            }
            assertEquals(0, GameServices.level().getCurrentZone()); assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(0, GameServices.level().getApparentAct());
            observeNativeSlot(output, "native-earned-return-save");
            driver.picture("native-durable-return-stable");
        }
    }

    /** Ordered native reader only: no explicit save request or payload creation. */
    private static void observeNativeSlot(Path output, String phase) throws Exception {
        var summary = new com.openggf.game.save.SaveManager(SavePaths.root()).readSlotSummary("s3k", 1);
        assertEquals(com.openggf.game.save.SaveSlotState.VALID, summary.state());
        var payload = summary.payload();
        assertEquals("sonic", payload.get("mainCharacter")); assertEquals(List.of(), payload.get("sidekicks"));
        assertEquals(0, ((Number) payload.get("zone")).intValue()); assertEquals(0, ((Number) payload.get("act")).intValue());
        var emeralds = assertInstanceOf(List.class, payload.get("emeraldStates"));
        assertEquals(7, emeralds.size()); assertEquals(1, ((Number) emeralds.getFirst()).intValue());
        assertEquals(GameServices.gameState().getLives(), ((Number) payload.get("lives")).intValue());
        Files.writeString(output.resolve("native-slot-observation.csv"), "phase,slot,state,zone,act,main,lives,emeraldStates\n"
                + phase + ",1," + summary.state() + ",0,0,sonic," + payload.get("lives") + ","
                + emeralds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("|")) + "\n");
    }

    /**
     * Controller authoring from the visible live board, not an engine predictor.
     * Queue a pad turn toward the next blue sphere at the upcoming grid crossing;
     * the native owner alone decides turn admission, speed, collisions and prizes.
     * Red/touched spheres and bumpers are obstacles. No turn is requested inside
     * the current crossing's $00..$1f alignment window (sub_9580).
     */
    private static int ordinaryBoardDirection(Sonic3kSpecialStageManager manager) {
        var player = manager.getPlayer();
        if (manager.getClearRoutine() != 0 || player.isFailed()) return 0;
        if (player.getVelocity() == 0) return AbstractPlayableSprite.INPUT_UP;
        if (player.getTurning() != 0 || player.getJumping() != 0) return 0;
        int heading = player.getAngle() >> 6;
        int axis = (heading & 1) == 0 ? player.getYPos() : player.getXPos();
        if ((axis & 0xff) <= 0x1f) return 0;
        int x = (player.getXPos() >> 8) & 31, y = (player.getYPos() >> 8) & 31;
        if (heading == 2) y = (y + 1) & 31;
        if (heading == 3) x = (x + 1) & 31;
        var grid = manager.getGrid();
        int root = ((y * 32 + x) << 2) | heading;
        int[] queue = new int[4096], first = new int[4096];
        java.util.Arrays.fill(first, -1);
        queue[0] = root; first[root] = 0;
        int[] dx = {0, -1, 0, 1}, dy = {-1, 0, 1, 0};
        for (int read = 0, written = 1; read < written; read++) {
            int node = queue[read], cell = node >> 2, direction = node & 3;
            int nx = cell & 31, ny = cell >> 5;
            if (node != root && grid.getCell(nx, ny) == 2) return first[node];
            // Prefer straight motion, then left/right. The graph has no reverse
            // edge: ordinary native controls need space for two separate turns.
            for (int turn : new int[]{0, 1, 3}) {
                int nextHeading = (direction + turn) & 3;
                int tx = (nx + dx[nextHeading]) & 31, ty = (ny + dy[nextHeading]) & 31;
                int type = grid.getCell(tx, ty);
                if (type != 0 && type != 2 && type != 4 && (type < 6 || type > 9)) continue;
                int next = ((ty * 32 + tx) << 2) | nextHeading;
                if (first[next] >= 0) continue;
                first[next] = node == root ? turn == 1 ? AbstractPlayableSprite.INPUT_LEFT
                        : turn == 3 ? AbstractPlayableSprite.INPUT_RIGHT : 0 : first[node];
                queue[written++] = next;
            }
        }
        throw new AssertionError("no ordinary non-reversing route to remaining blue spheres from " + x + "," + y);
    }
    private static boolean giantIsAdmitted() {
        for (var object : giants()) if (object.getSpawn().layoutIndex() == RING_LAYOUT) {
            assertEquals(RING_X, object.getSpawn().x()); assertEquals(RING_Y, object.getSpawn().y());
            assertEquals(1, object.getSpawn().subtype()); assertNull(object.getSpawn().ownerModId());
            return true;
        }
        return false;
    }
    private static int steer(int target) {
        int projected = target - player().getCentreX() - player().getXSpeed() / 48;
        return projected < -2 ? AbstractPlayableSprite.INPUT_LEFT : projected > 2 ? AbstractPlayableSprite.INPUT_RIGHT : 0;
    }
    private static AbstractPlayableSprite player() { return GameServices.camera().getFocusedSprite(); }
    private static List<Sonic3kSSEntryRingObjectInstance> giants() {
        return GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kSSEntryRingObjectInstance.class);
    }
    private static String digest(Path path, String algorithm) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(path)));
    }
    private static void restore(String key, String previous) {
        if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
    }

    private static final class Driver implements AutoCloseable {
        final GameplayCaptureSession session; final Path output; final BufferedWriter csv;
        final List<String> recipe = new ArrayList<>(); int frame;
        Driver(GameplayCaptureSession session, Path output) throws Exception {
            this.session = session; this.output = output;
            csv = Files.newBufferedWriter(output.resolve("state.csv"));
            csv.write("frame,phase,mode,zone,act,apparent,x,y,rings,score,lives,emeralds,ring_bit,next_stage,stage,ss_x,ss_y,angle,velocity,turning,jumping,spheres,ss_rings,rings_left,clear,finished,earned,cam_x,cam_y,max_y,max_y_target,dead,hurt,xvel,yvel,input\n");
        }
        void step(String phase, int direction, boolean jump) throws Exception {
            var held = new ArrayList<String>();
            if ((direction & AbstractPlayableSprite.INPUT_UP) != 0) held.add("U");
            if ((direction & AbstractPlayableSprite.INPUT_DOWN) != 0) held.add("D");
            if ((direction & AbstractPlayableSprite.INPUT_LEFT) != 0) held.add("L");
            if ((direction & AbstractPlayableSprite.INPUT_RIGHT) != 0) held.add("R");
            if (jump) held.add("A");
            String input = held.isEmpty() ? "-" : String.join("+", held); recipe.add(input);
            session.step(new Bk2FrameInput(frame, direction | (jump ? AbstractPlayableSprite.INPUT_JUMP : 0),
                    jump ? InputActionMasks.ACTION_A : 0, false, input));
            render(); frame++; record(phase, input);
            if (player().getDead()) picture("native-death");
            assertFalse(player().getDead(), "unexpected native death; inspect state.csv");
        }
        void render() {
            var provider = session.loop().getActiveSpecialStageProvider();
            if (session.loop().getCurrentGameMode() == GameMode.SPECIAL_STAGE
                    && provider.isEntryPresentationReady() && !provider.isEntryFadeToWhiteActive()) {
                // Engine.drawSpecialStage's native producer; screen-space flush supplies
                // presentation coordinates without changing the surviving world camera.
                var graphics = GameServices.graphics(); graphics.runPendingRenderThreadTasks();
                glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                provider.draw(); graphics.flushScreenSpace();
                if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
                glFinish();
            } else session.renderFrame();
        }
        void record(String phase, String input) throws Exception {
            var level = GameServices.level(); var state = GameServices.gameState();
            var provider = session.loop().getActiveSpecialStageProvider();
            Sonic3kSpecialStageComparisonState ss = provider instanceof Sonic3kSpecialStageProvider nativeProvider
                    && provider.isInitialized() ? nativeProvider.getManager().captureComparisonState() : null;
            csv.write(frame + "," + phase + "," + session.loop().getCurrentGameMode() + "," + level.getCurrentZone()
                    + "," + level.getCurrentAct() + "," + level.getApparentAct() + "," + player().getCentreX() + "," + player().getCentreY()
                    + "," + player().getRingCount() + "," + state.getScore() + "," + state.getLives() + "," + state.getEmeraldCount()
                    + "," + state.isSpecialRingCollected(RING_BIT) + "," + state.getCurrentSpecialStageIndex()
                    + "," + session.loop().recordedSpecialStageIdentity(session.loop().getCurrentGameMode())
                    + (ss == null ? ",-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,false,false" : "," + ss.playerX() + "," + ss.playerY()
                    + "," + ss.angle() + "," + ss.velocity() + "," + ss.turning() + "," + ss.jumping() + "," + ss.spheresLeft()
                    + "," + ss.ringsCollected() + "," + ss.ringsLeft() + "," + ss.clearRoutine() + "," + ss.finished() + "," + ss.emeraldCollected())
                    + "," + GameServices.camera().getX() + "," + GameServices.camera().getY()
                    + "," + GameServices.camera().getMaxY() + "," + GameServices.camera().getMaxYTarget()
                    + "," + player().getDead() + "," + player().isHurt() + "," + player().getXSpeed() + "," + player().getYSpeed()
                    + "," + input + "\n"); csv.flush();
        }
        void picture(String milestone) throws Exception {
            render(); ScreenshotCapture.savePNG(ScreenshotCapture.captureFramebuffer(320, 224),
                    output.resolve(String.format("%05d-%s.png", frame, milestone)));
        }
        @Override public void close() throws Exception {
            csv.close();
            if (recipe.isEmpty()) return;
            var script = new StringBuilder("# Positioned original giant25; not cold-route, fourteen-board or durable-save proof.\n# Candidate SHA256 ")
                    .append(CANDIDATE_SHA256).append("\n# ROM SHA1 ").append(ROM_SHA1)
                    .append("\n# Live board direction planning; ordinary pad only, no gameplay state hydration.\n");
            for (int start = 0; start < recipe.size();) {
                int end = start + 1;
                while (end < recipe.size() && recipe.get(start).equals(recipe.get(end))) end++;
                script.append(end - start).append(' ').append(recipe.get(start)).append('\n'); start = end;
            }
            Files.writeString(output.resolve("ordinary-input.script"), script);
            InputLogAuthorTool.author(script.toString(), output.resolve("ordinary-input.bk2"), "Sonic 3 & Knuckles");
        }
    }
}
