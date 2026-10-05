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
            long section = Math.floorDiv(originPixels() + x, 512);
            assertFalse(fixture.sprite().getDead(), "death at frame " + i + ", x=" + x
                    + ", y=" + fixture.sprite().getCentreY() + ", origin=" + originPixels()
                    + ", section " + section + (platformRun(section) ? " (platform stretch)" : ""));
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

    @Test void sessionPinsWidescreenAndHidesLevelSelect() throws Exception {
        launch(WidescreenAspect.NATIVE_4_3);
        assertEquals("WIDE_16_9", GameServices.module().requiredDisplayAspect());
        assertTrue(GameServices.module().suppressesLevelSelect(), "the title zone picker replaces level select");
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
        assertEquals(1.25, GameServices.module().gameplayAudioPlaybackRate());
        clock().getClass().getMethod("restore", before.getClass()).invoke(clock(), before);
        assertEquals("SPEED UP IN 5", warning.invoke(null, controller()));
        assertEquals(1.0, GameServices.module().gameplayAudioPlaybackRate());
        setTicks(124 * 1800 + 1500);
        assertEquals("", warning.invoke(null, controller()), "no warning at maximum speed");
        assertEquals(32.0, GameServices.module().gameplayAudioPlaybackRate());
        clock().getClass().getMethod("end").invoke(clock());
        assertEquals(1.0, GameServices.module().gameplayAudioPlaybackRate());
    }

    @Test void speedAndCountdownUseThirtySecondLinearIntervals() throws Exception {
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
        assertEquals(1.25, speed());
        assertEquals(30, seconds.invoke(clock));
        // 30 real seconds at 1.25x is 2250 simulation ticks, not another 1800.
        for (int i = 0; i < 2249; i++) tick.invoke(clock);
        assertEquals(1.25, speed());
        tick.invoke(clock);
        assertEquals(1.5, speed());
        assertEquals(30, seconds.invoke(clock));
        setTicks(0);
        var palTick = clock.getClass().getMethod("tick", int.class);
        for (int i = 0; i < 1500; i++) palTick.invoke(clock, 50);
        assertEquals(1.25, speed(), "PAL still speeds up after 30 seconds");
        setTicks(1800 * 130);
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
        assertEquals(0, controllerInt("displayLives"), "the session starts with no spare lives");
        assertFalse(controllerFlag("canContinue"));
        int score = GameServices.gameState().getScore();
        int elapsed = ticks();
        int cameraX = fixture.camera().getX();
        fixture.stepIdleFrames(10);
        assertEquals(score, GameServices.gameState().getScore());
        assertEquals(elapsed, ticks());
        assertEquals(cameraX, fixture.camera().getX());
    }

    private boolean controllerFlag(String name) throws Exception {
        return (boolean) controller().getClass().getMethod(name).invoke(controller());
    }
    private int controllerInt(String name) throws Exception {
        return (int) controller().getClass().getMethod(name).invoke(controller());
    }

    private void setSpareLives(int lives) {
        while (GameServices.gameState().getLives() > lives) GameServices.gameState().loseLife();
        while (GameServices.gameState().getLives() < lives) GameServices.gameState().addLife();
    }

    /** Dies with {@code spare} lives, waits out the menu delay and returns with the corpse held. */
    private void dieAndWaitForMenu(HeadlessTestFixture fixture, int spare) throws Exception {
        setSpareLives(spare);
        setTicks(1800 * 2 + 100);
        GameServices.gameState().addScore(500);
        fixture.sprite().applyCrushDeath();
        fixture.stepIdleFrames(1);
        assertTrue(gameOver());
        assertEquals(spare, controllerInt("displayLives"));
        // Hold jump through the early frames: a press carried over from play must not choose.
        for (int i = 0; i < 300; i++) fixture.stepFrame(false, false, false, false, i < 30);
        assertTrue(fixture.sprite().getDead());
        assertEquals(0, GameServices.gameState().getLives(), "the death routine held the corpse");
        assertTrue(controllerFlag("restartReady"));
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .noneMatch(o -> o instanceof AbstractGameOverCardObjectInstance), "stock GAME OVER card replaced");
        assertFalse(GameServices.level().isRespawnRequestedForRewind(), "corpse is held until the player chooses");
    }

    /** GameLoop consumes the request, fades out, runs the death-restart load and its title card. */
    private void reloadAfterChoice(HeadlessTestFixture fixture) throws Exception {
        assertTrue(GameServices.level().consumeRespawnRequest());
        Object oldController = controller();
        GameServices.level().restartCurrentLevelAfterDeath();
        assertNotSame(oldController, controller());
        assertTrue(GameServices.level().consumeTitleCardRequest());
        fixture.stepIdleFrames(2);
        assertFalse(fixture.sprite().getDead());
        assertFalse(gameOver());
    }

    /** Runs some real course so the controller has recorded a safe spot well past the start. */
    private void runCourse(HeadlessTestFixture fixture, int frames) throws Exception {
        fixture.stepIdleFrames(2);
        fixture.sprite().setInvulnerableFrames(20000);
        for (int i = 0; i < frames; i++) stepTerrain(fixture, 1);
        fixture.sprite().setInvulnerableFrames(0);
    }

    @Test void lastLifeGameOverSkipsStockCardAndJumpRestartsTheCourse() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        dieAndWaitForMenu(fixture, 0);
        assertFalse(controllerFlag("canContinue"), "no spare lives: GAME OVER offers only a restart");
        fixture.stepFrame(false, false, false, false, true);
        reloadAfterChoice(fixture);
        assertEquals(1.0, speed());
        assertTrue(ticks() < 10, "challenge clock restarts");
        assertEquals(0, GameServices.gameState().getLives(), "a fresh session has no spare lives");
        assertTrue(GameServices.gameState().getScore() < 100, "score restarts with the run");
    }

    @Test void deathMenuContinueRevivesInPlaceAtTheLastSafeSpot() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        runCourse(fixture, 900);
        long safe = (long) controller().getClass().getMethod("safeWorldX").invoke(controller());
        assertTrue(safe > 1536, "a safe spot past the opening: " + safe);
        GameServices.level().getLevelGamestate().setRings(30);
        Object controller = controller();
        dieAndWaitForMenu(fixture, 2);
        int score = GameServices.gameState().getScore();
        assertTrue(controllerFlag("canContinue"));
        var menu = loader.loadClass("infinite.CourseHud").getMethod("menuLines", controller().getClass());
        assertEquals(List.of("> CONTINUE", "  RESTART"), menu.invoke(null, controller()));
        fixture.stepFrame(false, false, false, false, true);
        // No reload: the same controller and terrain carry on.
        assertFalse(GameServices.level().isRespawnRequestedForRewind(), "CONTINUE does not reload the level");
        assertSame(controller, controller());
        assertFalse(gameOver());
        var player = fixture.sprite();
        assertFalse(player.getDead());
        assertEquals(safe, originPixels() + player.getCentreX(), "revived at the last safe spot");
        assertEquals(floorAt(safe) - 19, player.getCentreY());
        assertTrue(player.getInvulnerableFrames() > 0, "post-continue blink");
        assertEquals(1, GameServices.gameState().getLives(), "CONTINUE spends one spare life");
        assertEquals(1, controllerInt("displayLives"));
        assertEquals(0, GameServices.level().getLevelGamestate().getRings());
        assertTrue(GameServices.gameState().getScore() >= score, "score carries over");
        assertEquals(1.5, speed(), "the run resumes at the speed it died at");
        assertTrue(Math.abs(ticks() - (1800 * 2 + 100)) < 10, "and partway through that interval: " + ticks());
        assertTrue(fixture.camera().getX() < player.getCentreX() - player.getXRadius(), "Sonic is on screen");
        for (int i = 0; i < 4; i++) fixture.stepFrame(false, false, false, true, false);
        assertFalse(player.getAir(), "standing on the floor");
        int cameraX = fixture.camera().getX();
        for (int i = 0; i < 60; i++) fixture.stepFrame(false, false, false, true, false);
        assertFalse(player.getDead());
        assertTrue(fixture.camera().getX() > cameraX, "the course scrolls again");
        // The resumed run can die again and continue with its last spare life.
        dieAndWaitForMenu(fixture, 1);
        assertTrue(controllerFlag("canContinue"));
        fixture.stepFrame(false, false, false, false, true);
        assertFalse(fixture.sprite().getDead());
        assertEquals(0, GameServices.gameState().getLives());
    }

    @Test void continueAfterAPitDeathRevivesBeforeThePit() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        runCourse(fixture, 600);
        var player = fixture.sprite();
        long x = originPixels() + player.getCentreX();
        while (floorAt(x) >= 0) x += 4; // the next pit ahead
        long pit = x;
        long safe = (long) controller().getClass().getMethod("safeWorldX").invoke(controller());
        assertTrue(safe < pit);
        setSpareLives(1);
        int local = (int) (pit + 32 - originPixels());
        NativePositionOps.writeXPosResetSubpixel(player, local);
        NativePositionOps.writeYPosResetSubpixel(player, floorAt(safe) - 40);
        player.setAir(true); player.setXSpeed((short) 0); player.setGSpeed((short) 0);
        fixture.camera().setX((short) (local - 160));
        for (int i = 0; i < 240 && !player.getDead(); i++) fixture.stepIdleFrames(1);
        assertTrue(player.getDead(), "fell into the pit");
        assertTrue(gameOver());
        for (int i = 0; i < 300; i++) fixture.stepIdleFrames(1);
        assertTrue(controllerFlag("canContinue"));
        fixture.stepFrame(false, false, false, false, true);
        assertFalse(player.getDead());
        long revived = originPixels() + player.getCentreX();
        assertEquals(safe, revived, "back at the last safe spot");
        assertTrue(revived < pit);
        for (int i = 0; i < 4; i++) fixture.stepIdleFrames(1);
        assertFalse(player.getAir(), "standing on solid floor");
        assertFalse(player.getDead());
    }

    @Test void deathMenuRestartBeginsAFreshSession() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        dieAndWaitForMenu(fixture, 2);
        // Down moves the cursor once however long it is held; up moves it back.
        for (int i = 0; i < 10; i++) fixture.stepFrame(false, true, false, false, false);
        assertTrue(controllerFlag("restartSelected"));
        fixture.stepIdleFrames(1);
        fixture.stepFrame(true, false, false, false, false);
        assertFalse(controllerFlag("restartSelected"));
        fixture.stepIdleFrames(1);
        fixture.stepFrame(false, true, false, false, false);
        var menu = loader.loadClass("infinite.CourseHud").getMethod("menuLines", controller().getClass());
        assertEquals(List.of("  CONTINUE", "> RESTART"), menu.invoke(null, controller()));
        fixture.stepFrame(false, false, false, false, true);
        reloadAfterChoice(fixture);
        assertEquals(1.0, speed());
        assertTrue(ticks() < 10);
        assertEquals(0, GameServices.gameState().getLives());
        assertTrue(GameServices.gameState().getScore() < 100);
    }

    @Test void livesComeOnlyFromEveryHundredRings() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        fixture.sprite().setInvulnerableFrames(2000);
        var rings = GameServices.level().getLevelGamestate();
        assertEquals(0, GameServices.gameState().getLives(), "the session starts with no spare lives");
        GameServices.gameState().addScore(120_000);
        stepTerrain(fixture, 1);
        assertEquals(0, GameServices.gameState().getLives(), "score never awards lives");
        rings.setRings(99);
        stepTerrain(fixture, 1);
        assertEquals(0, GameServices.gameState().getLives());
        rings.addRings(1); // the ordinary collection path, including the stock 100-ring check
        stepTerrain(fixture, 1);
        assertEquals(1, GameServices.gameState().getLives(), "one life at 100, not a second stock one");
        String hud = (String) loader.loadClass("infinite.CourseHud")
                .getMethod("livesText", ObjectServices.class, controller().getClass())
                .invoke(null, GameServices.level().getObjectManager().getObjectServices(), controller());
        assertTrue(hud.endsWith("  LIVES 1"), hud);
        rings.addRings(100);
        stepTerrain(fixture, 1);
        assertEquals(2, GameServices.gameState().getLives(), "200");
        rings.addRings(100);
        stepTerrain(fixture, 1);
        assertEquals(3, GameServices.gameState().getLives(), "every further 100, beyond the stock 200");
        rings.resetRingsForLoss();
        stepTerrain(fixture, 1);
        rings.addRings(100);
        stepTerrain(fixture, 1);
        assertEquals(4, GameServices.gameState().getLives(), "reaching 100 again after losing rings");
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
            // Two thirds of the 0x600 run speed: 4px per tick, 240 points per second at 1x.
            int expected = (int) (240 * (1.0 + 0.25 * (stage / 1800)));
            assertEquals(expected, fixture.camera().getX() - startCamera, 1);
            assertEquals(expected, GameServices.gameState().getScore() - score, 1);
            assertFalse(player.getDead());
        }
        player.setXSpeed((short) 0x1000);
        int before = fixture.camera().getX();
        fixture.stepFrame(false, false, false, true, false);
        assertTrue(player.getXSpeed() > 0x400);
        assertEquals(4, fixture.camera().getX() - before, 1, "speed alone must not pull the camera forward");
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
                assertTrue(lead >= initialLead, "Sonic must gain, not lose, ground at 1.25x");
                // Controller precedes player integration: allow the current tick's 6px movement.
                assertTrue(lead <= margin + 6, "right edge remains bounded");
                reachedMargin |= lead >= margin;
                assertFalse(player.getDead());
            }
        }
        assertEquals(1.25, speed());
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
        assertEquals(1.25, speed());
        assertEquals("SPEED 1.25X", loader.loadClass("infinite.CourseHud")
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
        assertEquals(1.25, speed());
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
        assertEquals(1.25, speed());
        assertTrue(originPixels() > 0, "survive recycling under real scroll pressure");
    }

    /** A deterministic player policy; terrain observations choose inputs, never set physics state. */
    private void stepCourse(HeadlessTestFixture fixture, int direction) throws Exception {
        var player = fixture.sprite();
        long x = originPixels() + player.getCentreX();
        boolean approachingGap = false;
        for (int ahead = 0; ahead <= 48; ahead += 4) {
            if (surfaceAt(x + direction * ahead) < 0) approachingGap = true;
        }
        // A ledge wall (backtracking over a drop) rises more than any ROM slope in one pixel.
        boolean approachingWall = false;
        for (int ahead = 1; ahead <= 32; ahead++) {
            int before = floorAt(x + direction * (ahead - 1)), after = floorAt(x + direction * ahead);
            if (before >= 0 && after >= 0 && before - after > 16) approachingWall = true;
        }
        boolean left = direction < 0, right = direction > 0;
        boolean jump;
        if (player.getAir()) {
            jump = true;
            int steer = steerTowardLanding(player, x, direction);
            if (steer != KEEP_RUNNING) { left = steer * direction < 0; right = steer * direction > 0; }
        } else {
            // Re-press on even level frames: a jump still held from the air cannot start a new
            // one, and parity of the rewound frame counter keeps replays deterministic.
            jump = (approachingGap || approachingWall) && GameServices.level().getFrameCounter() % 2 == 0;
        }
        fixture.stepFrame(false, false, left, right, jump);
    }

    /** Terrain floor, else the surface of a planned stepping stone, else -1. */
    private int surfaceAt(long x) throws Exception {
        int floor = floorAt(x);
        if (floor >= 0) return floor;
        long section = Math.floorDiv(x, 512);
        for (long s = section - 1; s <= section + 1; s++) {
            for (Object stone : stones(s)) {
                if (Math.abs(x - (long) call(stone, "worldX")) <= (int) call(stone, "halfWidth") - 4) {
                    return (int) call(stone, "surface");
                }
            }
        }
        return -1;
    }

    private static final int KEEP_RUNNING = 2;

    /**
     * Air steering toward a surface: predicts the ballistic landing on each surface ahead
     * (held jump, ROM gravity $38). A landing on a long surface keeps running; a landing on a
     * short one (a stone) aims for its middle; a miss leans toward the nearest surface.
     * Returns -1 (lean back), 0 (coast), +1 (lean forward) or {@link #KEEP_RUNNING}.
     */
    private int steerTowardLanding(com.openggf.sprites.playable.AbstractPlayableSprite player, long x,
            int direction) throws Exception {
        double vx = player.getXSpeed() / 256.0, vy = player.getYSpeed() / 256.0, g = 0x38 / 256.0;
        java.util.List<long[]> intervals = new ArrayList<>();
        long start = -1;
        for (int d = -64; d <= 640; d += 4) {
            long at = x + (long) direction * d;
            boolean solid = surfaceAt(at) >= 0;
            if (solid && start < 0) start = at;
            if ((!solid || d == 640) && start >= 0) {
                intervals.add(new long[]{Math.min(start, at), Math.max(start, at)});
                start = -1;
            }
        }
        long bestDistance = Long.MAX_VALUE;
        int steer = 0;
        for (long[] interval : intervals) {
            long lo = interval[0], hi = interval[1];
            int surface = surfaceAt((lo + hi) / 2) - 19;
            double c = player.getCentreY() - surface, disc = vy * vy - 2 * g * c;
            if (disc < 0) continue;
            double t = (-vy + Math.sqrt(disc)) / g;
            if (t <= 0) continue;
            long landing = x + Math.round(vx * t);
            if (landing >= lo + 8 && landing <= hi - 8) {
                if (hi - lo > 160) return KEEP_RUNNING;
                long centre = (lo + hi) / 2;
                if (Math.abs(landing - centre) <= 8) return 0;
                return (landing < centre) == (direction > 0) ? 1 : -1;
            }
            long distance = landing < lo + 8 ? lo + 8 - landing : landing - (hi - 8);
            if (distance < bestDistance) {
                bestDistance = distance;
                steer = (landing < lo + 8) == (direction > 0) ? 1 : -1;
            }
        }
        return steer;
    }

    private Object[] stones(long section) throws Exception {
        return (Object[]) loader.loadClass("infinite.PlatformPlan").getMethod("at", terrain().getClass(), long.class)
                .invoke(null, terrain(), section);
    }
    private static Object call(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }

    private int gapWidth(long section) throws Exception {
        return (int) terrain().getClass().getMethod("gapWidth", long.class).invoke(terrain(), section);
    }
    private int stepHeight(long section) throws Exception {
        return (int) terrain().getClass().getMethod("stepHeight", long.class).invoke(terrain(), section);
    }
    private boolean platformRun(long section) throws Exception {
        return (boolean) terrain().getClass().getMethod("platformRun", long.class).invoke(terrain(), section);
    }
    private long pitStart(long stretch) throws Exception {
        return (long) terrain().getClass().getMethod("platformPitStart", long.class).invoke(terrain(), stretch);
    }
    private long pitEnd(long stretch) throws Exception {
        return (long) terrain().getClass().getMethod("platformPitEnd", long.class).invoke(terrain(), stretch);
    }

    /** Stock object ids the act places that the course lends to platform stretches. */
    private Set<Integer> platformKindIds() throws Exception {
        var ids = new TreeSet<Integer>();
        for (Object kind : (List<?>) call(terrain(), "platformKinds")) ids.add((int) call(kind, "objectId"));
        return ids;
    }

    @ParameterizedTest(name = "zone {0} act {1}")
    @org.junit.jupiter.params.provider.MethodSource("courseActs")
    void platformStretchesBridgeTheirPitWithTheActsStockPlatforms(int zone, int act) throws Exception {
        launch(WidescreenAspect.NATIVE_4_3, zone, act);
        var stock = new HashSet<Integer>();
        for (var spawn : new com.openggf.game.sonic1.Sonic1GameModule().createGame(GameServices.rom().getRom())
                .loadLevel(levelIndex(zone, act)).getObjects()) stock.add(spawn.objectId());
        Set<Integer> kinds = platformKindIds();
        for (int id : kinds) assertTrue(stock.contains(id), "only platforms the act itself places: " + id);
        int runs = 0;
        for (long stretch = 0; stretch < 250; stretch++) {
            long near = stretch * 4 + 2, far = stretch * 4 + 3;
            assertEquals(platformRun(near), platformRun(far), "a platform stretch spans two sections");
            if (!platformRun(far)) {
                assertEquals(0, stones(far).length);
                continue;
            }
            runs++;
            assertTrue(stretch >= 2, "the first two corridors teach ordinary jumps");
            assertTrue(Math.abs(stepHeight(far)) <= 32, "banks differ by at most a tier");
            assertNull(encounter(near));
            assertNull(encounter(far));
            long start = pitStart(stretch), end = pitEnd(stretch);
            assertTrue(end - start >= 320 && end - start <= 448, "pit width " + (end - start));
            int bank = floorAt(start - 1);
            for (long x = near * 512; x < start; x++) assertEquals(bank, floorAt(x), "flat approach bank");
            assertTrue(start - near * 512 >= 352, "approach runway");
            int farBank = floorAt(end);
            for (long x = end; x < (far + 1) * 512; x++) assertEquals(farBank, floorAt(x), "flat far bank");
            assertTrue((far + 1) * 512 - end >= 160, "far runway");
            for (long x = start; x < end; x++) assertEquals(-1, floorAt(x), "bottomless pit");
            Object[] stones = stones(far);
            assertTrue(stones.length >= 1 && stones.length <= 3);
            long edge = start;
            for (Object stone : stones) {
                long centre = (long) call(stone, "worldX");
                int half = (int) call(stone, "halfWidth");
                assertTrue(kinds.contains((int) call(stone, "objectId")));
                assertEquals(Math.max(bank, farBank), (int) call(stone, "surface"), "level with the lower bank");
                long span = centre - half - edge;
                assertTrue(span >= 16 && span <= 144, "jumpable span " + span);
                edge = centre + half;
            }
            assertTrue(end - edge >= 16 && end - edge <= 144, "final span " + (end - edge));
        }
        if (kinds.isEmpty()) assertEquals(0, runs, "acts without stock platforms keep ordinary corridors");
        else assertTrue(runs >= 25 && runs <= 120, "a share of corridors becomes a platform stretch: " + runs);
    }

    private static int levelIndex(int zone, int act) {
        return GameServices.module().getZoneRegistry().getLevelDataForZone(zone).get(act).levelIndex();
    }

    /**
     * Real physics and ROM objects: the input policy crosses the act's first platform stretch on
     * the stock platforms the controller spawns at their planned positions, through the
     * engine's own solid-object riding.
     */
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> courseActsAtBothAspects() {
        return courseActs().flatMap(a -> java.util.stream.Stream.of(WidescreenAspect.NATIVE_4_3, WidescreenAspect.WIDE_16_9)
                .map(aspect -> org.junit.jupiter.params.provider.Arguments.of(a.get()[0], a.get()[1], aspect)));
    }

    @ParameterizedTest(name = "zone {0} act {1} {2}")
    @org.junit.jupiter.params.provider.MethodSource("courseActsAtBothAspects")
    void sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int zone, int act, WidescreenAspect aspect) throws Exception {
        var fixture = launch(aspect, zone, act);
        org.junit.jupiter.api.Assumptions.assumeFalse(platformKindIds().isEmpty(), "act places no platforms");
        long stretch = 2;
        while (!platformRun(stretch * 4 + 3)) stretch++;
        long start = pitStart(stretch), end = pitEnd(stretch);
        Object[] planned = stones(stretch * 4 + 3);
        fixture.stepIdleFrames(2);
        fixture.sprite().setInvulnerableFrames(20000);
        Set<Long> seen = new HashSet<>();
        var trail = new StringBuilder();
        boolean rode = false;
        for (int frame = 0; frame < 6000 && originPixels() + fixture.sprite().getCentreX() < end + 64; frame++) {
            stepTerrain(fixture, 1);
            var player = fixture.sprite();
            trail.append(String.format(" [%d,%d air=%b obj=%b vx=%d]", originPixels() + player.getCentreX(),
                    player.getCentreY(), player.getAir(), player.isOnObject(), (int) player.getXSpeed()));
            if (trail.length() > 6000) trail.delete(0, trail.length() - 6000);
            if (player.getDead()) {
                var desc = new StringBuilder("pit " + start + ".." + end + " bank " + floorAt(start - 1) + "/" + floorAt(end));
                for (Object stone : planned) desc.append(" stone ").append(stone);
                for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
                    if (!object.getClass().getName().startsWith("infinite.")) desc.append(" live ")
                            .append(object.getClass().getSimpleName()).append('@').append(object.getX() + originPixels())
                            .append(',').append(object.getY()).append(object.isDestroyed() ? "(x)" : "");
                }
                fail("death at world x=" + (originPixels() + player.getCentreX()) + " " + desc + "\n" + trail);
            }
            for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
                if (object.getClass().getName().startsWith("infinite.") || object.isDestroyed()
                        || object.getSpawn() == null) continue;
                for (Object stone : planned) {
                    if (object.getSpawn().objectId() == (int) call(stone, "objectId")
                            && object.getSpawn().x() + originPixels() == (long) call(stone, "worldX")) {
                        // The reported position follows the stock sink when stood on; check it on arrival.
                        if (seen.add((long) call(stone, "worldX"))) {
                            assertEquals((int) call(stone, "y"), object.getSpawn().y(), "planned height");
                        }
                    }
                }
            }
            long worldX = originPixels() + player.getCentreX();
            rode |= player.isOnObject() && !player.getAir() && worldX > start && worldX < end;
        }
        assertTrue(originPixels() + fixture.sprite().getCentreX() >= end, "crossed the pit");
        assertEquals(planned.length, seen.size(), "every planned stone spawned as a stock object");
        assertTrue(rode, "Sonic stood on a stock platform over the pit");
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
            if (floorAt(section * 512) >= 0) elevations.add(floorAt(section * 512));
            assertEquals(floorAt(section * 512 - 1), floorAt(section * 512), "sections join without a seam step");
            if (section < 3) assertFalse(isCorridor(section), "safe opening runway");
            // Platform stretches have their own test below.
            if (platformRun(section)) continue;
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
    private static int species(Object encounter) throws Exception {
        return (int) encounter.getClass().getMethod("species").invoke(encounter);
    }
    /** A {@code CourseSpecies.Traits} component for a species id. */
    private static Object trait(int species, String component) throws Exception {
        Object traits = loader.loadClass("infinite.CourseSpecies").getMethod("of", int.class).invoke(null, species);
        return traits.getClass().getMethod(component).invoke(traits);
    }
    private static int depth(Object encounter) throws Exception {
        return (int) trait(species(encounter), "depth");
    }
    private static Set<Integer> ids(int[] species) {
        return Arrays.stream(species).boxed().collect(java.util.stream.Collectors.toSet());
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
                if (flying(plan)) assertTrue(encounterY(plan) + 8 + depth(plan) + 48 <= floor);
            }
            if (flying(plan)) air++;
            else {
                ground++;
                assertTrue(max - min <= 16);
                assertEquals(floorAt(x) - depth(plan), encounterY(plan));
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

    /** Registry zones GHZ, MZ, SYZ, LZ, SLZ, SBZ act 1, plus SBZ3 on Labyrinth's layout and art. */
    @ParameterizedTest(name = "zone {0} act {1}")
    @org.junit.jupiter.params.provider.CsvSource({"0,0", "1,0", "2,0", "3,0", "4,0", "5,0", "5,2"})
    void encountersUseTheZonesOwnBadniksWithLoadedRomArt(int zone, int act) throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3, zone, act);
        int romZone = GameServices.level().getCurrentLevel().getZoneIndex();
        var speciesType = loader.loadClass("infinite.CourseSpecies");
        // The act's own stock placement chooses the line-up.
        var ground = ids((int[]) terrain().getClass().getMethod("groundSpecies").invoke(terrain()));
        var air = ids((int[]) terrain().getClass().getMethod("airSpecies").invoke(terrain()));
        assertFalse(ground.isEmpty() || air.isEmpty());
        var seen = new HashSet<Integer>();
        for (long section = 3; section < 600; section++) {
            Object plan = encounter(section);
            if (plan == null) continue;
            int species = species(plan);
            assertTrue((flying(plan) ? air : ground).contains(species), species + " in zone " + romZone);
            assertEquals(flying(plan), trait(species, "flying"));
            seen.add(species);
        }
        var lineUp = new HashSet<Integer>(ground);
        lineUp.addAll(air);
        assertEquals(lineUp, seen, "every species in the zone's line-up appears");
        for (int species : lineUp) {
            String key = (String) trait(species, "artKey");
            assertNotNull(GameServices.level().getObjectRenderManager().getRenderer(key),
                    trait(species, "name") + " art is loaded for zone " + romZone);
        }
        if (romZone != 0) assertFalse(seen.contains(speciesType.getField("MOTOBUG").getInt(null)));
        // Live spawns carry the planned species through the spawn subtype.
        fixture.sprite().setInvulnerableFrames(20000);
        for (int i = 0; i < 1500 && enemies().isEmpty(); i++) stepTerrain(fixture, 1);
        assertFalse(enemies().isEmpty());
        for (ObjectInstance enemy : enemies()) {
            assertTrue(lineUp.contains(enemy.getClass().getMethod("species").invoke(enemy)));
        }
    }

    @Test void walkingBombsHurtInsteadOfBreaking() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3, 4, 0); // Star Light: Bombs on the ground.
        fixture.sprite().setInvulnerableFrames(2000);
        ObjectInstance bomb = null;
        for (int i = 0; i < 1500 && bomb == null; i++) {
            stepCourse(fixture, 1);
            bomb = enemies().stream().filter(o -> !flyingEnemy(o)).findFirst().orElse(null);
        }
        assertNotNull(bomb);
        assertEquals("Walking Bomb", trait((int) bomb.getClass().getMethod("species").invoke(bomb), "name"));
        var player = fixture.sprite();
        player.setInvulnerableFrames(0);
        GameServices.level().getLevelGamestate().setRings(5);
        NativePositionOps.writeXPosResetSubpixel(player, bomb.getX());
        NativePositionOps.writeYPosResetSubpixel(player, bomb.getY() - 48);
        player.setXSpeed((short) 0); player.setGSpeed((short) 0);
        player.setYSpeed((short) 0x300); player.setAir(true); player.setRolling(true);
        fixture.camera().setX((short) Math.max(0, bomb.getX() - 160));
        for (int i = 0; i < 12 && GameServices.level().getLevelGamestate().getRings() > 0; i++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(0, GameServices.level().getLevelGamestate().getRings(), "a rolling hit still hurts");
        assertFalse(bomb.isDestroyed(), "col_hurt bombs cannot be destroyed");
        assertFalse(player.getDead());
    }
    /**
     * A real running (not rolling) touch into a Green Hill ground badnik: 20 rings, or else a
     * shield, absorb the hit with no knockback; with neither the stock hurt applies.
     */
    @ParameterizedTest @ValueSource(strings = {"toll", "shield", "few"})
    void ringTollOrShieldAbsorbsAnEnemyHitWithoutKnockback(String guard) throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.sprite().setInvulnerableFrames(2000);
        ObjectInstance enemy = null;
        for (int i = 0; i < 1500 && enemy == null; i++) {
            stepCourse(fixture, 1);
            enemy = enemies().stream().filter(o -> !flyingEnemy(o)).findFirst().orElse(null);
        }
        assertNotNull(enemy);
        var player = fixture.sprite();
        var rings = GameServices.level().getLevelGamestate();
        rings.setRings(guard.equals("toll") ? 25 : 5);
        if (guard.equals("shield")) player.giveShield();
        player.setInvulnerableFrames(0);
        NativePositionOps.writeXPosResetSubpixel(player, enemy.getX());
        NativePositionOps.writeYPosResetSubpixel(player, enemy.getY());
        player.setRolling(false); player.setAir(true);
        player.setXSpeed((short) 0x400); player.setGSpeed((short) 0x400); player.setYSpeed((short) 0);
        fixture.camera().setX((short) Math.max(0, enemy.getX() - 160));
        fixture.stepIdleFrames(1);
        assertFalse(player.getDead());
        assertFalse(enemy.isDestroyed(), "a running touch is not an attack");
        if (guard.equals("few")) {
            assertTrue(player.isHurt(), "fewer than 20 rings and no shield: stock knockback");
            assertEquals(0, rings.getRings());
            return;
        }
        assertFalse(player.isHurt(), "no knockback");
        assertEquals(0x400, player.getXSpeed(), "Sonic keeps running");
        assertTrue(player.getInvulnerableFrames() > 0x70, "post-hit blink");
        assertFalse(player.hasShield());
        // 25 - 20 for the toll; a shield keeps all 5.
        assertEquals(5, rings.getRings(), guard.equals("toll") ? "the toll costs exactly 20 rings"
                : "a shield keeps every ring");
        fixture.stepIdleFrames(4);
        assertFalse(player.isHurt(), "the blink keeps the same badnik from hitting again");
        assertEquals(5, rings.getRings());
    }

    @Test void shieldMonitorsAreSeededOnLevelGround() throws Exception {
        launch(WidescreenAspect.NATIVE_4_3);
        int monitors = 0;
        for (long section = 0; section < 1000; section++) {
            Object monitor = shieldMonitor(section);
            assertEquals(monitor, shieldMonitor(section), "seeded");
            if (monitor == null) continue;
            monitors++;
            assertTrue(section >= 6, "no monitor on the opening runway");
            assertFalse(isCorridor(section) || platformRun(section), "monitors stand on ordinary ground");
            long x = (long) call(monitor, "worldX");
            int floor = floorAt(x);
            assertEquals(floor - 15, (int) call(monitor, "y"), "standing on the floor");
            for (long dx = -16; dx <= 16; dx++) assertTrue(Math.abs(floorAt(x + dx) - floor) <= 4, "level ground");
        }
        assertTrue(monitors >= 30 && monitors <= 120, "about one in ten open sections: " + monitors);
    }

    @Test void touchingAShieldMonitorBreaksItGivesAShieldAndReplays() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        fixture.sprite().setInvulnerableFrames(20000);
        ObjectInstance monitor = null;
        for (int frame = 0; frame < 8000 && monitor == null; frame++) {
            stepTerrain(fixture, 1);
            monitor = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(o -> !o.isDestroyed() && o.getClass().getName().equals("infinite.CourseMonitor"))
                    .findFirst().orElse(null);
        }
        assertNotNull(monitor, "the course places shield monitors");
        var player = fixture.sprite();
        player.setInvulnerableFrames(0);
        assertFalse(player.hasShield());
        NativePositionOps.writeXPosResetSubpixel(player, monitor.getX());
        NativePositionOps.writeYPosResetSubpixel(player, monitor.getY());
        player.setXSpeed((short) 0); player.setGSpeed((short) 0); player.setYSpeed((short) 0);
        player.setAir(true);
        fixture.camera().setX((short) Math.max(0, monitor.getX() - 160));
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        fixture.stepIdleFrames(1);
        assertTrue(player.hasShield(), "any touch breaks the box, even without rolling");
        assertTrue((boolean) call(monitor, "isBroken"));
        fixture.stepIdleFrames(8);
        var expected = registry.capture().entries().get("object-manager");
        registry.restore(before);
        fixture.stepIdleFrames(9);
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", expected,
                registry.capture().entries().get("object-manager")), "break and burst replay");
    }
    private Object shieldMonitor(long section) throws Exception {
        return loader.loadClass("infinite.ShieldPlan").getMethod("at", terrain().getClass(), long.class)
                .invoke(null, terrain(), section);
    }

    private static boolean flyingEnemy(ObjectInstance enemy) {
        try { return (boolean) enemy.getClass().getMethod("flying").invoke(enemy); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
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
            // 0 = a ground badnik, 1 = a flyer (whichever species the act's line-up chose).
            enemy = enemies().stream().filter(o -> flyingEnemy(o) == (subtype == 1)).findFirst().orElse(null);
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
