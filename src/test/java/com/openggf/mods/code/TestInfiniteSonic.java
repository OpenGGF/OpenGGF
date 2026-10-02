package com.openggf.mods.code;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.session.SessionManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.Level;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestInfiniteSonic {
    @TempDir static Path temp;
    static URLClassLoader loader;
    static Path jar;
    SharedLevel bootstrap;

    @BeforeAll static void compileAndValidate() throws Exception {
        Path project = Path.of("examples/infinite-sonic");
        Path classes = Files.createDirectory(temp.resolve("classes"));
        var args = new ArrayList<>(List.of("--release", "21", "-cp", System.getProperty("java.class.path"),
                "-d", classes.toString()));
        try (var files = Files.walk(project.resolve("src/main/java"))) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        Files.createDirectories(classes.resolve("META-INF"));
        Files.copy(project.resolve("src/main/resources/META-INF/openggf-mod.yaml"),
                classes.resolve("META-INF/openggf-mod.yaml"));
        jar = temp.resolve("infinite-sonic.jar");
        assertEquals(0, GgfModCli.run(new String[]{"package", "--input", classes.toString(),
                "--out", jar.toString()}, System.out));
        loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, TestInfiniteSonic.class.getClassLoader());
    }
    @AfterAll static void closeLoader() throws Exception { if (loader != null) loader.close(); }
    @AfterEach void closeSession() { if (bootstrap != null) bootstrap.dispose(); }

    private HeadlessTestFixture launch(WidescreenAspect aspect) throws Exception {
        return launch(aspect, 0, 0);
    }

    private HeadlessTestFixture launch(WidescreenAspect aspect, int zone, int act) throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_1, 0, 0);
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        GameModule base = GameServices.module();
        try (var assets = ModAssetRoot.jar(temp, jar, ModInputLimits.production())) {
            var context = new ModContext("infinite-sonic", "s1", assets);
            ((GgfMod) loader.loadClass("infinite.InfiniteSonicMod").getConstructor().newInstance()).register(context);
            var plan = context.freeze();
            GameModule effective = new ModBackedGamePatch(plan).apply(base, null);
            for (GamePatch patch : plan.explicitPatches()) effective = patch.apply(effective, null);
            SessionManager.clear();
            GameModuleRegistry.setCurrent(effective);
            TestEnvironment.activeGameplayMode();
        }
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
        GameServices.level().getObjectManager().setRewindClassResolver(new RewindClassResolver() {
            @Override public Optional<Class<?>> resolve(String owner, String name) {
                try { return Optional.of(name.startsWith("infinite.") ? loader.loadClass(name) : Class.forName(name)); }
                catch (ClassNotFoundException missing) { return Optional.empty(); }
            }
            @Override public Optional<String> ownerOf(Class<?> type) {
                return type.getName().startsWith("infinite.") ? Optional.of("infinite-sonic") : Optional.empty();
            }
        });
        return fixture;
    }


    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void protectedTraversalPreservesEncountersAcrossRebaseAndReplay(WidescreenAspect aspect) throws Exception {
        var fixture = launch(aspect);
        assertEquals(aspect.pixelWidth(), fixture.camera().getWidth());
        Level level = GameServices.level().getCurrentLevel();
        assertTrue(level.getBlockCount() <= 256, "byte layout block budget");
        assertEquals(1, level.getObjects().size());
        assertTrue(level.getRings().isEmpty());
        fixture.stepIdleFrames(2);
        // Explicit traversal-only protection: damage and attacks are tested separately below.
        fixture.sprite().setInvulnerableFrames(20000);
        int rebases = 0;
        int previous = fixture.sprite().getCentreX();
        for (int i = 0; i < 6000; i++) {
            stepTerrain(fixture, 1);
            int x = fixture.sprite().getCentreX();
            if (previous - x > 4000) rebases++;
            assertFalse(fixture.sprite().getDead(), "death at frame " + i + ", x=" + x + ", y=" + fixture.sprite().getCentreY() + ", cameraMaxY=" + fixture.camera().getMaxY());
            previous = x;
        }
        assertTrue(rebases >= 4, "must traverse several windows; got " + rebases + ", x=" + previous);
        // A full registry snapshot proves object origin, terrain and player restore together.
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        for (int i = 0; i < 800; i++) stepTerrain(fixture, 1);
        int expectedX = fixture.sprite().getCentreX();
        int expectedY = fixture.sprite().getCentreY();
        int expectedFraction = fixture.sprite().getXSubpixelRaw();
        short expectedSpeed = fixture.sprite().getGSpeed();
        byte[] expectedMap = GameServices.level().getCurrentLevel().getMap().getData().clone();
        var expectedObjects = registry.capture().entries().get("object-manager");
        registry.restore(before);
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", before.entries().get("object-manager"),
                registry.capture().entries().get("object-manager")), "immediate object restore");
        for (int i = 0; i < 800; i++) stepTerrain(fixture, 1);
        assertEquals(expectedX, fixture.sprite().getCentreX());
        assertEquals(expectedY, fixture.sprite().getCentreY());
        assertEquals(expectedFraction, fixture.sprite().getXSubpixelRaw());
        assertEquals(expectedSpeed, fixture.sprite().getGSpeed());
        assertArrayEquals(expectedMap, GameServices.level().getCurrentLevel().getMap().getData());
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", expectedObjects,
                registry.capture().entries().get("object-manager")));
        int backwardsRebases = 0;
        previous = fixture.sprite().getCentreX();
        for (int i = 0; i < 1800; i++) {
            stepTerrain(fixture, -1);
            int x = fixture.sprite().getCentreX();
            if (x - previous > 4000) backwardsRebases++;
            assertFalse(fixture.sprite().getDead());
            previous = x;
        }
        assertTrue(backwardsRebases > 0, "backtracking crosses the recycle boundary");
    }

    /** Registry zones 0-5 (GHZ, MZ, SYZ, LZ, SLZ, SBZ), every act, including SBZ3's LZ layout. */
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> courseActs() {
        var acts = new ArrayList<org.junit.jupiter.params.provider.Arguments>();
        for (int zone = 0; zone < 6; zone++) {
            for (int act = 0; act < 3; act++) acts.add(org.junit.jupiter.params.provider.Arguments.of(zone, act));
        }
        return acts.stream();
    }

    @ParameterizedTest(name = "zone {0} act {1}")
    @org.junit.jupiter.params.provider.MethodSource("courseActs")
    void everyZoneActBuildsATraversableDryCourse(int zone, int act) throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3, zone, act);
        Level level = GameServices.level().getCurrentLevel();
        assertTrue(level.getBlockCount() <= 256, "byte layout block budget");
        assertEquals(1, level.getObjects().size(), "the course controller replaces stock placement");
        assertTrue(level.getRings().isEmpty());
        assertFalse(GameServices.level().getZoneFeatureProvider().hasWater(level.getZoneIndex()));
        int seam = (int) terrain().getClass().getMethod("seam").invoke(terrain());
        assertEquals(80, fixture.sprite().getCentreX(), "course start replaces the stock start");
        // Start Y is seam - 19 (standing radius); spawn placement may settle it a pixel higher.
        assertTrue(Math.abs(fixture.sprite().getCentreY() - (seam - 19)) <= 1,
                "standing on the flat opening: y=" + fixture.sprite().getCentreY() + ", seam=" + seam);
        fixture.stepIdleFrames(2);
        assertFalse(fixture.sprite().getAir());
        fixture.sprite().setInvulnerableFrames(20000);
        int rebases = 0;
        int previous = fixture.sprite().getCentreX();
        for (int i = 0; i < 3000; i++) {
            stepTerrain(fixture, 1);
            int x = fixture.sprite().getCentreX();
            if (previous - x > 4000) rebases++;
            assertFalse(fixture.sprite().getDead(), "death at frame " + i + ", x=" + x
                    + ", y=" + fixture.sprite().getCentreY() + ", origin=" + originPixels());
            assertFalse(fixture.sprite().isInWater(), "frame " + i);
            previous = x;
        }
        assertTrue(rebases >= 2, "must traverse several windows; got " + rebases + ", x=" + previous);
    }

    @Test void freshReloadResetsTheCourseAndFinalZoneRemainsStock() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        byte[] initial = GameServices.level().getCurrentLevel().getMap().getData().clone();
        fixture.stepIdleFrames(2);
        assertTrue(GameServices.level().getLevelGamestate().isTimerPaused());
        GameServices.level().loadZoneAndAct(6, 0);
        assertTrue(GameServices.level().getCurrentLevel().getObjects().size() > 1);
        GameServices.level().loadZoneAndAct(0, 0);
        assertArrayEquals(initial, GameServices.level().getCurrentLevel().getMap().getData());
        assertEquals(1, GameServices.level().getCurrentLevel().getObjects().size());
    }

    @Test void patchOnlyActivatesForSoloSonic() throws Exception {
        GamePatch patch = (GamePatch) loader.loadClass("infinite.InfiniteSonicMod$Patch")
                .getConstructor().newInstance();
        assertTrue(patch.activatesFor(new GameplayLaunchRequest("s1", "sonic", List.of())));
        assertFalse(patch.activatesFor(new GameplayLaunchRequest("s1", "tails", List.of())));
        assertFalse(patch.activatesFor(new GameplayLaunchRequest("s1", "sonic", List.of("tails"))));
        assertFalse(patch.activatesFor(new GameplayLaunchRequest("s2", "sonic", List.of())));
    }


    @Test void titleWrapsStockScreenWithWordmarkAboveTheEmblem() throws Exception {
        launch(WidescreenAspect.values()[0]);
        TitleScreenProvider title = GameServices.module().getTitleScreenProvider();
        assertEquals("infinite.TitleWordmark", title.getClass().getName());
        assertSame(title, GameServices.module().getTitleScreenProvider(), "wrapper is cached");
        var base = title.getClass().getDeclaredMethod("base");
        base.setAccessible(true);
        Object stock = base.invoke(title);
        assertEquals("com.openggf.game.sonic1.titlescreen.Sonic1TitleScreenManager", stock.getClass().getName());
        assertEquals(((TitleScreenProvider) stock).getState(), title.getState());
        var topField = title.getClass().getDeclaredField("TOP");
        var edgeField = title.getClass().getDeclaredField("edge");
        topField.setAccessible(true);
        edgeField.setAccessible(true);
        int top = topField.getInt(null);
        boolean[][] edge = (boolean[][]) edgeField.get(title);
        // The emblem and TitleSonic's head begin at screen Y 30. The outline starts
        // 2 px above the letters and its shadow drops 3 px below it.
        assertTrue(top - 2 >= 0 && top + 1 + edge.length <= 30, "wordmark band " + top + "+" + edge.length);
        assertTrue(edge[0].length <= 320, "fits the native viewport");
    }

    @Test void titleZoneMenuChoosesStartZoneAndCourseCardsDropActNumber() throws Exception {
        launch(WidescreenAspect.values()[0]);
        GameModule module = GameServices.module();
        TitleScreenProvider title = module.getTitleScreenProvider();
        assertEquals(0, title.startZoneIndex(), "Green Hill is the default start");
        var menuAccessor = title.getClass().getDeclaredMethod("menu");
        menuAccessor.setAccessible(true);
        Object menu = menuAccessor.invoke(title);
        var update = menu.getClass().getDeclaredMethod("update", com.openggf.control.InputHandler.class, boolean.class);
        update.setAccessible(true);
        var input = new com.openggf.control.InputHandler();
        int left = com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_LEFT;
        int right = com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT;
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(left, left, 0, 0, false, false), null));
        update.invoke(menu, input, false);
        assertEquals(0, title.startZoneIndex(), "presses are ignored until the title is interactive");
        update.invoke(menu, input, true);
        assertEquals(5, title.startZoneIndex(), "left wraps from Green Hill to Scrap Brain");
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(right, right, 0, 0, false, false), null));
        update.invoke(menu, input, true);
        update.invoke(menu, input, true);
        assertEquals(1, title.startZoneIndex(), "right wraps back through Green Hill to Marble");
        title.reset();
        assertEquals(1, title.startZoneIndex(), "the choice survives a return to the title");

        for (int zone = 0; zone < 6; zone++) {
            assertFalse(module.showsTitleCardActNumber(zone, 0), "course zone " + zone + " drops ACT n");
        }
        assertTrue(module.showsTitleCardActNumber(6, 0), "Final Zone keeps stock card rules");

        // The S1 card omits the act element and tucks the oval 8 px after "ZONE", as Final Zone does.
        var card = module.getTitleCardProvider();
        card.initialize(1, 1);
        var elementsField = card.getClass().getDeclaredField("elements");
        elementsField.setAccessible(true);
        var elements = (List<?>) elementsField.get(card);
        var frame = com.openggf.game.titlecard.TitleCardElement.class.getMethod("getFrameIndex");
        var target = com.openggf.game.titlecard.TitleCardElement.class.getDeclaredField("targetX");
        target.setAccessible(true);
        var frames = new ArrayList<Integer>();
        for (Object element : elements) frames.add((Integer) frame.invoke(element));
        assertFalse(frames.contains(com.openggf.game.sonic1.titlecard.Sonic1TitleCardMappings.getActFrame(1)),
                "no ACT 2 element: " + frames);
        assertEquals(3, elements.size());
        int zoneText = target.getInt(elements.get(frames.indexOf(
                com.openggf.game.sonic1.titlecard.Sonic1TitleCardMappings.FRAME_ZONE)));
        int oval = target.getInt(elements.get(frames.indexOf(
                com.openggf.game.sonic1.titlecard.Sonic1TitleCardMappings.FRAME_OVAL)));
        assertEquals(zoneText + 8, oval);
    }

    private Object controller() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o.getClass().getName().equals("infinite.CourseController")).findFirst().orElseThrow();
    }
    private Object clock() throws Exception {
        return GameServices.module().getGameService(loader.loadClass("infinite.ChallengeClock"));
    }
    private Object clockSnapshot() throws Exception {
        return clock().getClass().getMethod("capture").invoke(clock());
    }
    private double speed() throws Exception {
        return (double) clock().getClass().getMethod("displayMultiplier").invoke(clock());
    }
    private int ticks() throws Exception {
        Object snapshot = clockSnapshot();
        return (int) snapshot.getClass().getMethod("stage").invoke(snapshot) * 1800
                + (int) Math.round((double) snapshot.getClass().getMethod("elapsed").invoke(snapshot));
    }
    private boolean gameOver() throws Exception {
        return (boolean) controller().getClass().getMethod("gameOver").invoke(controller());
    }
    private void setTicks(int value) throws Exception {
        Class<?> snapshot = loader.loadClass("infinite.ChallengeClock$Snapshot");
        Object state = snapshot.getConstructor(double.class, double.class, int.class, boolean.class)
                .newInstance((double) (value % 1800), 0.0, value / 1800, false);
        clock().getClass().getMethod("restore", snapshot).invoke(clock(), state);
    }

    @Test void lastFiveSecondsWarnAndAudioRateFollowsRestoredChallengeState() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        var warning = loader.loadClass("infinite.CourseHud").getMethod("warningText", controller().getClass());
        setTicks(1439);
        assertEquals("", warning.invoke(null, controller()));
        setTicks(1500);
        Object before = clockSnapshot();
        assertEquals("SPEED UP IN 5", warning.invoke(null, controller()));
        setTicks(1740);
        assertEquals("SPEED UP IN 1", warning.invoke(null, controller()));
        setTicks(1800);
        assertEquals("", warning.invoke(null, controller()));
        assertEquals(1.5, GameServices.module().gameplayAudioPlaybackRate());
        clock().getClass().getMethod("restore", before.getClass()).invoke(clock(), before);
        assertEquals("SPEED UP IN 5", warning.invoke(null, controller()));
        assertEquals(1.0, GameServices.module().gameplayAudioPlaybackRate());
        setTicks(9 * 1800 + 1500);
        assertEquals("", warning.invoke(null, controller()), "no warning at maximum speed");
        assertEquals(32.0, GameServices.module().gameplayAudioPlaybackRate());
        clock().getClass().getMethod("end").invoke(clock());
        assertEquals(1.0, GameServices.module().gameplayAudioPlaybackRate());
    }

    @Test void speedAndCountdownUseThirtySecondCompoundingIntervals() throws Exception {
        launch(WidescreenAspect.NATIVE_4_3);
        Object clock = clock();
        var tick = clock.getClass().getMethod("tick");
        var seconds = clock.getClass().getMethod("secondsRemaining");
        assertEquals(1.0, speed());
        assertEquals(30, seconds.invoke(clock));
        for (int i = 0; i < 1799; i++) tick.invoke(clock);
        assertEquals(1.0, speed());
        assertEquals(1, seconds.invoke(clock));
        tick.invoke(clock);
        assertEquals(1.5, speed());
        assertEquals(30, seconds.invoke(clock));
        // 30 real seconds at 1.5x is 2700 simulation ticks, not another 1800.
        for (int i = 0; i < 2699; i++) tick.invoke(clock);
        assertEquals(1.5, speed());
        tick.invoke(clock);
        assertEquals(2.25, speed());
        assertEquals(30, seconds.invoke(clock));
        setTicks(0);
        var palTick = clock.getClass().getMethod("tick", int.class);
        for (int i = 0; i < 1500; i++) palTick.invoke(clock, 50);
        assertEquals(1.5, speed(), "PAL still speeds up after 30 seconds");
        setTicks(1800 * 20);
        assertEquals(32.0, speed(), "bounded host pacing ceiling");
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void fallingBehindEndsRunDespiteRingsAndInvulnerability(WidescreenAspect aspect) throws Exception {
        var fixture = launch(aspect);
        fixture.stepIdleFrames(2);
        var player = fixture.sprite();
        GameServices.level().getLevelGamestate().setRings(20);
        player.setInvulnerableFrames(2000);
        NativePositionOps.writeXPosResetSubpixel(player, 1000);
        NativePositionOps.writeYPosResetSubpixel(player, floorAt(1000) - 19);
        player.setAir(false); player.setGSpeed((short) 0); player.setXSpeed((short) 0);
        fixture.camera().setX((short) (1000 + player.getXRadius() - 5));
        fixture.stepIdleFrames(1);
        assertFalse(player.getDead(), "a partly visible player is still in the run");
        fixture.stepIdleFrames(1);
        assertTrue(player.getDead());
        assertTrue(gameOver());
        assertEquals(0, GameServices.gameState().getLives());
        int score = GameServices.gameState().getScore();
        int elapsed = ticks();
        int cameraX = fixture.camera().getX();
        fixture.stepIdleFrames(10);
        assertEquals(score, GameServices.gameState().getScore());
        assertEquals(elapsed, ticks());
        assertEquals(cameraX, fixture.camera().getX());
    }

    @Test void gameOverSkipsStockCardAndJumpRestartsTheCourse() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        var player = fixture.sprite();
        setTicks(1800 * 2 + 100);
        GameServices.gameState().addScore(500);
        player.applyCrushDeath();
        fixture.stepIdleFrames(1);
        assertTrue(gameOver());
        var restartReady = controller().getClass().getMethod("restartReady");
        // Hold jump through the early frames: a press carried over from play must not restart.
        for (int i = 0; i < 300; i++) fixture.stepFrame(false, false, false, false, i < 30);
        assertTrue(player.getDead());
        assertEquals(0, GameServices.gameState().getLives());
        assertTrue((boolean) restartReady.invoke(controller()));
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .noneMatch(o -> o instanceof AbstractGameOverCardObjectInstance), "stock GAME OVER card replaced");
        assertFalse(GameServices.level().isRespawnRequestedForRewind(), "corpse is held until the player restarts");
        fixture.stepFrame(false, false, false, false, true);
        // GameLoop consumes the request, fades out and runs the death-restart load.
        assertTrue(GameServices.level().consumeRespawnRequest());

        Object oldController = controller();
        GameServices.level().restartCurrentLevelAfterDeath();
        assertNotSame(oldController, controller());
        // GameLoop runs the restart's title card before play resumes.
        assertTrue(GameServices.level().consumeTitleCardRequest());
        fixture.stepIdleFrames(2);
        assertFalse(fixture.sprite().getDead());
        assertFalse(gameOver());
        assertEquals(1.0, speed());
        assertTrue(ticks() < 10, "challenge clock restarts");
        assertEquals(3, GameServices.gameState().getLives());
        assertTrue(GameServices.gameState().getScore() < 100, "score restarts with the run");
    }

    @Test void minimumScrollAllowsFasterRunningAndSurvivalScoreScales() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        var player = fixture.sprite();
        for (int stage : new int[]{0, 1800, 3600}) {
            setTicks(stage);
            player.setInvulnerableFrames(10000);
            int score = GameServices.gameState().getScore();
            int startCamera = fixture.camera().getX();
            for (int frame = 0; frame < 60; frame++) {
              int steps = GameServices.module().gameplayStepsPerFrame();
              for (int step = 0; step < steps; step++) {
                // Timing/scroll setup independent of terrain: airborne, well ahead, stationary.
                NativePositionOps.writeXPosResetSubpixel(player, fixture.camera().getX() + 160);
                NativePositionOps.writeYPosResetSubpixel(player, 600);
                player.setAir(true); player.setXSpeed((short) 0); player.setYSpeed((short) 0);
                fixture.stepIdleFrames(1);
              }
            }
            int expected = (int) (270 * Math.pow(1.5, stage / 1800));
            assertEquals(expected, fixture.camera().getX() - startCamera, 1);
            assertEquals(expected, GameServices.gameState().getScore() - score, 1);
            assertFalse(player.getDead());
        }
        player.setXSpeed((short) 0x1000);
        int before = fixture.camera().getX();
        fixture.stepFrame(false, false, false, true, false);
        assertTrue(player.getXSpeed() > 1152);
        assertEquals(4.5, fixture.camera().getX() - before, 1, "speed alone must not pull the camera forward");
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void sonicGainsGroundAcrossSpeedupAndIsHeldAtRightMargin(WidescreenAspect aspect) throws Exception {
        var fixture = launch(aspect);
        fixture.stepIdleFrames(2);
        var player = fixture.sprite();
        setTicks(1790);
        int initialLead = 120;
        NativePositionOps.writeXPosResetSubpixel(player, 1000);
        fixture.camera().setX((short) (1000 - initialLead));
        int margin = fixture.camera().getWidth() * 60 / 100;
        boolean reachedMargin = false;
        // Real player integration and module pacing on each presentation frame.
        // Reset only height/vertical velocity to isolate scrolling from course obstacles.
        for (int frame = 0; frame < 600; frame++) {
            int steps = GameServices.module().gameplayStepsPerFrame();
            for (int step = 0; step < steps; step++) {
                NativePositionOps.writeYPosResetSubpixel(player, 600);
                player.setAir(true);
                player.setYSpeed((short) 0);
                player.setXSpeed((short) 0x600);
                fixture.stepFrame(false, false, false, true, false);
                int lead = player.getCentreX() - fixture.camera().getX();
                assertTrue(lead >= initialLead, "Sonic must gain, not lose, ground at 1.5x");
                // Controller precedes player integration: allow the current tick's 6px movement.
                assertTrue(lead <= margin + 6, "right edge remains bounded");
                reachedMargin |= lead >= margin;
                assertFalse(player.getDead());
            }
        }
        assertEquals(1.5, speed());
        assertTrue(reachedMargin, "Sonic reaches the right-hand margin at every viewport width");
        assertEquals(margin, player.getCentreX() - fixture.camera().getX(), 6);
    }

    @Test void speedupBoundaryRestoresAndReplaysWithScoreCameraAndHud() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        setTicks(1798);
        var player = fixture.sprite();
        NativePositionOps.writeXPosResetSubpixel(player, 1000);
        NativePositionOps.writeYPosResetSubpixel(player, 600);
        fixture.camera().setX((short) 840);
        player.setAir(true); player.setXSpeed((short) 0x600); player.setYSpeed((short) 0);
        fixture.stepFrame(false, false, false, true, false);
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        fixture.stepFrame(false, false, false, true, false);
        assertEquals(1800, ticks());
        assertEquals(0x600, player.getMax(), "whole-game pacing preserves native physics");
        assertEquals(1.5, speed());
        assertEquals("SPEED 1.50X", loader.loadClass("infinite.CourseHud")
                .getMethod("speedText", controller().getClass()).invoke(null, controller()));
        assertEquals("NEXT 30", loader.loadClass("infinite.CourseHud")
                .getMethod("countdownText", controller().getClass()).invoke(null, controller()));
        var after = registry.capture();
        int score = GameServices.gameState().getScore();
        int camera = fixture.camera().getX();
        registry.restore(before);
        assertEquals(0x600, player.getMax());
        fixture.stepFrame(false, false, false, true, false);
        assertEquals(0x600, player.getMax(), "whole-game pacing preserves native physics");
        assertEquals(1.5, speed());
        assertEquals(score, GameServices.gameState().getScore());
        assertEquals(camera, fixture.camera().getX());
        assertEquals(after.entries().get("infinite-sonic:clock"), registry.capture().entries().get("infinite-sonic:clock"));
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", after.entries().get("object-manager"),
                registry.capture().entries().get("object-manager")));
    }

    private long originPixels() throws Exception {
        Object controller = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o.getClass().getName().equals("infinite.CourseController")).findFirst().orElseThrow();
        return (long) controller.getClass().getMethod("originPixels").invoke(controller);
    }

    /** Terrain-only regression protection: keep 1x physics and recenter the scrolling camera.
     * Challenge progression and failure are tested separately with unmodified controller state. */
    private void stepTerrain(HeadlessTestFixture fixture, int direction) throws Exception {
        setTicks(0);
        fixture.camera().setX((short) Math.max(0, fixture.sprite().getCentreX() - 160));
        stepCourse(fixture, direction);
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void normalTraversalReachesFirstSpeedup(WidescreenAspect aspect) throws Exception {
        var fixture = launch(aspect);
        fixture.stepIdleFrames(2);
        fixture.sprite().setInvulnerableFrames(2400); // Isolate terrain/scroll from badnik hits.
        for (int frame = 0; frame < 1850; frame++) {
            stepCourse(fixture, 1);
            assertFalse(fixture.sprite().getDead(), "challenge traversal frame " + frame);
        }
        assertTrue(ticks() >= 1800);
        assertEquals(0x600, fixture.sprite().getMax());
        assertEquals(1.5, speed());
        assertTrue(originPixels() > 0, "survive recycling under real scroll pressure");
    }

    /** A deterministic player policy; terrain observations choose inputs, never set physics state. */
    private void stepCourse(HeadlessTestFixture fixture, int direction) throws Exception {
        long x = originPixels() + fixture.sprite().getCentreX();
        boolean approachingGap = false;
        for (int ahead = 0; ahead <= 48; ahead += 4) {
            if (floorAt(x + direction * ahead) < 0) approachingGap = true;
        }
        // A ledge wall (backtracking over a drop) rises more than any ROM slope in one pixel.
        boolean approachingWall = false;
        for (int ahead = 1; ahead <= 32; ahead++) {
            int before = floorAt(x + direction * (ahead - 1)), after = floorAt(x + direction * ahead);
            if (before >= 0 && after >= 0 && before - after > 16) approachingWall = true;
        }
        boolean jump = fixture.sprite().getAir() || approachingGap || approachingWall;
        fixture.stepFrame(false, false, direction < 0, direction > 0, jump);
    }

    private int gapWidth(long section) throws Exception {
        return (int) terrain().getClass().getMethod("gapWidth", long.class).invoke(terrain(), section);
    }
    private int stepHeight(long section) throws Exception {
        return (int) terrain().getClass().getMethod("stepHeight", long.class).invoke(terrain(), section);
    }
    private boolean isCorridor(long section) throws Exception {
        return (boolean) terrain().getClass().getMethod("isCorridor", long.class).invoke(terrain(), section);
    }

    @Test void ringRowsCollectThroughGameplayAndRestoreWithTheCourse() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        ObjectInstance ring = null;
        for (int frame = 0; frame < 300 && ring == null; frame++) {
            stepCourse(fixture, 1);
            ring = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(o -> o.getClass().getName().equals("infinite.CourseRing"))
                    .findFirst().orElse(null);
        }
        assertNotNull(ring, "opening runway introduces collectible rings");
        var player = fixture.sprite();
        NativePositionOps.writeXPosResetSubpixel(player, ring.getX());
        NativePositionOps.writeYPosResetSubpixel(player, ring.getY());
        player.setXSpeed((short) 0); player.setGSpeed((short) 0); player.setYSpeed((short) 0);
        player.setAir(true);
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        int count = GameServices.level().getLevelGamestate().getRings();
        fixture.stepIdleFrames(1);
        assertEquals(count + 1, GameServices.level().getLevelGamestate().getRings(), "real touch awards a ring");
        var expected = registry.capture().entries().get("object-manager");
        registry.restore(before);
        assertEquals(count, GameServices.level().getLevelGamestate().getRings());
        fixture.stepIdleFrames(1);
        assertEquals(count + 1, GameServices.level().getLevelGamestate().getRings());
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", expected,
                registry.capture().entries().get("object-manager")), "collection sparkle replays");
        fixture.stepIdleFrames(40);
        assertEquals(count + 1, GameServices.level().getLevelGamestate().getRings(), "a collected ring stays collected");
    }

    @Test void corridorsAreBoundedSeededAndHaveLevelRunways() throws Exception {
        launch(WidescreenAspect.NATIVE_4_3);
        Set<Integer> widths = new HashSet<>();
        Set<Integer> steps = new HashSet<>();
        Set<Integer> elevations = new HashSet<>();
        int gaps = 0, ledges = 0;
        for (long section = 0; section < 1000; section++) {
            int width = gapWidth(section);
            int step = stepHeight(section);
            assertEquals(width, gapWidth(section));
            elevations.add(floorAt(section * 512));
            assertEquals(floorAt(section * 512 - 1), floorAt(section * 512), "sections join without a seam step");
            if (section < 3) assertFalse(isCorridor(section), "safe opening runway");
            if (!isCorridor(section)) {
                assertEquals(0, width);
                assertEquals(0, step);
                continue;
            }
            assertNull(encounter(section), "jump sections have no badnik ambush");
            assertTrue(Math.abs(step) <= 64, "a held jump must clear every climb: " + step);
            // Climbs pair with shorter pits; the widest pits stay within one tier.
            assertTrue(width <= (Math.abs(step) > 32 ? 128 : Math.abs(step) > 0 ? 160 : 192), width + "/" + step);
            if (width == 0) {
                assertTrue(step > 0, "a pit-less corridor is a ledge drop, never a forward wall");
                ledges++;
            } else {
                gaps++;
                widths.add(width);
            }
            steps.add(step);
            long start = section * 512 + (512 - width) / 2;
            int left = floorAt(section * 512), right = floorAt(section * 512 + 511);
            assertEquals(step, right - left);
            for (int x = 0; x < 512; x++) {
                long world = section * 512 + x;
                assertEquals(world >= start && world < start + width ? -1 : x < 256 ? left : right, floorAt(world));
            }
        }
        assertEquals(Set.of(64, 96, 128, 160, 192), widths);
        assertEquals(Set.of(-64, -32, 0, 32, 64), steps);
        assertEquals(Set.of(896, 928, 960, 992), elevations, "four elevation tiers");
        assertTrue(gaps > 150 && gaps < 350, "occasional jumps with recovery sections: " + gaps);
        assertTrue(ledges > 10, "some drops are plain ledges: " + ledges);
    }

    @ParameterizedTest @ValueSource(ints = {64, 96, 128, 160, 192})
    void realPhysicsCanClearEachGapInBothDirections(int width) throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        // Exercise the largest elevation change paired with this width: one direction climbs it.
        long section = -1;
        for (long candidate = 3; candidate < 1000; candidate++) {
            if (gapWidth(candidate) == width
                    && (section < 0 || Math.abs(stepHeight(candidate)) > Math.abs(stepHeight(section)))) {
                section = candidate;
            }
        }
        assertTrue(section >= 0, "course must expose gap width " + width);
        long worldStart = section * 512 + (512 - width) / 2;
        while (worldStart - originPixels() > 7000) {
            NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), 8192);
            NativePositionOps.writeYPosResetSubpixel(fixture.sprite(), 600);
            fixture.stepIdleFrames(1);
        }
        int start = (int) (worldStart - originPixels());
        for (int direction : new int[]{1, -1}) {
            var player = fixture.sprite();
            int takeoff = direction > 0 ? start - 32 : start + width + 32;
            NativePositionOps.writeXPosResetSubpixel(player, takeoff);
            NativePositionOps.writeYPosResetSubpixel(player, floorAt(originPixels() + takeoff) - 19);
            player.setAir(false); player.setRolling(false);
            player.setXSpeed((short) (direction * 0x300));
            player.setGSpeed((short) (direction * 0x300));
            player.setYSpeed((short) 0);
            player.setInvulnerableFrames(300);
            fixture.camera().setX((short) (takeoff - 160));
            // Release first so the second direction has a fresh jump press.
            fixture.stepFrame(false, false, direction < 0, direction > 0, false);
            boolean airborne = false, landed = false;
            for (int frame = 0; frame < 100; frame++) {
                fixture.camera().setX((short) (player.getCentreX() - 160));
                fixture.stepFrame(false, false, direction < 0, direction > 0, true);
                airborne |= player.getAir();
                assertFalse(player.getDead(), "gap " + width + ", direction " + direction);
                if (airborne && !player.getAir()) { landed = true; break; }
            }
            assertTrue(airborne && landed, "jump must land on solid terrain");
            assertTrue(direction > 0 ? player.getCentreX() >= start + width : player.getCentreX() < start,
                    "must land beyond the gap at a 3px/frame approach speed");
        }
    }

    private Object terrain() throws Exception {
        return GameServices.module().getGameService(loader.loadClass("infinite.TerrainLibrary"));
    }
    private int floorAt(long x) throws Exception {
        return (int) terrain().getClass().getMethod("floorAt", long.class).invoke(terrain(), x);
    }
    private Object encounter(long section) throws Exception {
        return loader.loadClass("infinite.EncounterPlan").getMethod("at", terrain().getClass(), long.class)
                .invoke(null, terrain(), section);
    }
    private static long worldX(Object encounter) throws Exception {
        return (long) encounter.getClass().getMethod("worldX").invoke(encounter);
    }
    private static int encounterY(Object encounter) throws Exception {
        return (int) encounter.getClass().getMethod("y").invoke(encounter);
    }
    private static boolean flying(Object encounter) throws Exception {
        return (boolean) encounter.getClass().getMethod("flying").invoke(encounter);
    }
    private List<ObjectInstance> enemies() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && o.getClass().getName().equals("infinite.CourseBadnik")).toList();
    }

    @Test void encountersAreSeededSpacedAndFitTheirEntirePatrolCorridor() throws Exception {
        launch(WidescreenAspect.NATIVE_4_3);
        for (long section = 0; section < 3; section++) assertNull(encounter(section));
        int ground = 0, air = 0, empty = 0;
        long previous = -10000;
        for (long section = 3; section < 1000; section++) {
            Object plan = encounter(section);
            assertEquals(plan, encounter(section), "same seed and section, regardless of call order");
            if (plan == null) { empty++; continue; }
            long x = worldX(plan);
            assertTrue(x - previous >= 320, "reaction space between encounters");
            previous = x;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int dx = -88; dx <= 88; dx++) {
                int floor = floorAt(x + dx);
                min = Math.min(min, floor); max = Math.max(max, floor);
                if (flying(plan)) assertTrue(encounterY(plan) + 8 + 12 + 48 <= floor);
            }
            if (flying(plan)) air++;
            else {
                ground++;
                assertTrue(max - min <= 16);
                assertEquals(floorAt(x) - 14, encounterY(plan));
            }
        }
        assertTrue(ground > 50 && air > 50 && empty > 50, ground + "/" + air + "/" + empty);
        // Independent decoded collision scan verifies cached floor profiles against the actual map.
        Level level = GameServices.level().getCurrentLevel();
        // Sections keep CLEARANCE open above their floor; scenery above that may be solid.
        var scan = terrain().getClass().getMethod("floorBelow", Level.class, int.class, int.class, int.class);
        int clearance = terrain().getClass().getField("CLEARANCE").getInt(null);
        for (int x = 0; x < 16384; x += 3) {
            int floor = floorAt(x);
            assertEquals((int) scan.invoke(null, level, x / 256, x % 256, floor < 0 ? 0 : floor - clearance), floor);
        }
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void holdingRightCannotCompleteTheCourse(WidescreenAspect aspect) throws Exception {
        var fixture = launch(aspect);
        assertEquals(0, GameServices.level().getLevelGamestate().getRings());
        assertEquals(0, fixture.sprite().getInvulnerableFrames());
        int frames = 0;
        while (!fixture.sprite().getDead() && frames++ < 2400) {
            fixture.stepFrame(false, false, false, true, false);
            assertTrue(GameServices.level().getObjectManager().getAllocatedSlotCount() < 64,
                    "bounded encounter population");
        }
        assertTrue(fixture.sprite().getDead(), "holding Right must fail: x=" + fixture.sprite().getCentreX() + ", y=" + fixture.sprite().getCentreY() + ", speed=" + fixture.sprite().getGSpeed());
        assertTrue(fixture.sprite().getCentreX() > 1400, "safe opening runway");
        assertTrue(originPixels() + fixture.sprite().getCentreX() < 2560, "first gap must require jumping");
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void aPhysicalStompDestroysTheBadnikAwardsScoreAndDoesNotRespawnIt(int subtype) throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        // Reach a live encounter without damage, then set up a local descending jump.
        fixture.sprite().setInvulnerableFrames(2000);
        ObjectInstance enemy = null;
        for (int i = 0; i < 1000 && enemy == null; i++) {
            stepCourse(fixture, 1);
            enemy = enemies().stream().filter(o -> o.getSpawn().subtype() == subtype).findFirst().orElse(null);
        }
        assertNotNull(enemy);
        var player = fixture.sprite();
        player.setInvulnerableFrames(0);
        NativePositionOps.writeXPosResetSubpixel(player, enemy.getX());
        NativePositionOps.writeYPosResetSubpixel(player, enemy.getY() - 48);
        player.setXSpeed((short) 0); player.setGSpeed((short) 0);
        player.setYSpeed((short) 0x300); player.setAir(true); player.setRolling(true);
        fixture.camera().setX((short) Math.max(0, enemy.getX() - 160));
        int score = GameServices.gameState().getScore();
        for (int i = 0; i < 12 && !enemy.isDestroyed(); i++) fixture.stepIdleFrames(1);
        assertTrue(enemy.isDestroyed(), "real touch response must handle a descending spinning Sonic");
        assertFalse(player.getDead());
        assertTrue(GameServices.gameState().getScore() >= score + 100);
        long anchor = (long) enemy.getClass().getMethod("worldAnchor").invoke(enemy);
        var registry = fixture.runtime().getRewindRegistry();
        var burst = registry.capture();
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(o -> o.getClass().getName().equals("infinite.CourseBurst")));
        fixture.stepIdleFrames(8);
        var afterBurst = registry.capture().entries().get("object-manager");
        registry.restore(burst);
        fixture.stepIdleFrames(8);
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", afterBurst,
                registry.capture().entries().get("object-manager")), "explosion restore and replay");
        fixture.stepIdleFrames(60);
        for (ObjectInstance live : enemies()) {
            assertNotEquals(anchor, (long) live.getClass().getMethod("worldAnchor").invoke(live));
        }
    }
}
