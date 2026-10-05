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
        // The leaderboard is saved under the save root; keep the tests' scores out of the real one.
        System.setProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY, temp.resolve("saves").toString());
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
    @AfterAll static void closeLoader() throws Exception {
        System.clearProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY);
        if (loader != null) loader.close();
    }
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
        // The camera never scrolls back, so the window never shifts back either.
        long origin = originPixels();
        for (int i = 0; i < 600; i++) {
            stepTerrain(fixture, -1);
            assertFalse(fixture.sprite().getDead());
        }
        assertEquals(origin, originPixels(), "backtracking does not recycle the window backwards");
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

    @Test void titleShowsThePickedZonesOwnBackground() throws Exception {
        launch(WidescreenAspect.WIDE_16_9);
        TitleScreenProvider title = GameServices.module().getTitleScreenProvider();
        var base = title.getClass().getDeclaredMethod("base");
        var menuAccessor = title.getClass().getDeclaredMethod("menu");
        var backgroundAccessor = title.getClass().getDeclaredMethod("background");
        for (var method : List.of(base, menuAccessor, backgroundAccessor)) method.setAccessible(true);
        var stock = (com.openggf.game.sonic1.titlescreen.Sonic1TitleScreenManager) base.invoke(title);
        var overrideField = stock.getClass().getDeclaredField("backgroundOverride");
        overrideField.setAccessible(true);
        Object background = backgroundAccessor.invoke(title);
        assertSame(background, overrideField.get(stock), "the wrapper installs its background on the stock title");
        Object menu = menuAccessor.invoke(title);
        var select = menu.getClass().getDeclaredMethod("select", int.class, int.class);
        var setActive = background.getClass().getDeclaredMethod("setActive", boolean.class);
        var showing = background.getClass().getDeclaredMethod("showing");
        var update = background.getClass().getDeclaredMethod("update", int.class);
        var lines = background.getClass().getDeclaredMethod("lines");
        var vscroll = background.getClass().getDeclaredMethod("vscroll");
        var tileWord = background.getClass().getDeclaredMethod("tileWord", Level.class, int.class, int.class);
        for (var method : List.of(select, setActive, showing, update, lines, vscroll, tileWord)) method.setAccessible(true);
        var override = (com.openggf.game.sonic1.titlescreen.Sonic1TitleScreenManager.BackgroundOverride) background;
        var none = com.openggf.game.titlescreen.SegaPaletteFade.Mode.NONE;

        select.invoke(menu, 2, 1);
        assertNull(override.backdrop(none, 0), "outside the wrapper's own calls the stock title is untouched");
        setActive.invoke(background, true);
        select.invoke(menu, 0, -1);
        update.invoke(background, 1);
        assertEquals(-1, showing.invoke(background), "Green Hill keeps the stock title background");
        assertNull(override.backdrop(none, 0));
        for (int zone = 1; zone < 6; zone++) {
            select.invoke(menu, zone, 1);
            for (int frame = 0; frame < 20; frame++) update.invoke(background, frame);
            assertEquals(zone, showing.invoke(background));
            var level = (Level) ((java.util.function.IntFunction<?>) fieldValue(background, "levels")).apply(zone);
            assertNotNull(level, "zone " + zone + " act 1 is read from the ROM");
            var expected = level.getPalette(2).getColor(0);
            var backdrop = override.backdrop(none, 0);
            assertEquals(expected.rFloat(), backdrop.rFloat(), 1e-6, "backdrop is the zone's line 2 colour 0 once faded in");
            // The screen is mostly covered by the zone's own background tiles.
            int[] scroll = (int[]) lines.invoke(background);
            int v = (int) vscroll.invoke(background), drawn = 0;
            for (int row = 0; row < 28; row++) {
                int bgX = -(short) scroll[row * 8];
                for (int column = 0; column < 50; column++) {
                    if ((int) tileWord.invoke(null, level, bgX + column * 8, v + row * 8) != 0) drawn++;
                }
            }
            assertTrue(drawn > 28 * 50 / 3, "zone " + zone + " draws its background: " + drawn + " tiles");
            int[] before = scroll.clone();
            for (int frame = 21; frame < 29; frame++) update.invoke(background, frame);
            assertFalse(Arrays.equals(before, (int[]) lines.invoke(background)), "zone " + zone + " background scrolls");
        }
        setActive.invoke(background, false);
        assertEquals(-1, showing.invoke(background));
    }

    private static Object fieldValue(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
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

    private static Path leaderboardFile() {
        return temp.resolve("saves").resolve("infinite-sonic").resolve("leaderboard.txt");
    }
    private Object leaderboard() throws Exception {
        return GameServices.module().getGameService(loader.loadClass("infinite.Leaderboard"));
    }
    @SuppressWarnings("unchecked")
    private List<Object> top(int zone) throws Exception {
        return (List<Object>) leaderboard().getClass().getMethod("top", int.class).invoke(leaderboard(), zone);
    }
    private static int entryScore(Object entry) throws Exception {
        return (int) entry.getClass().getMethod("score").invoke(entry);
    }

    @Test void leaderboardRecordsEachRunOnceAndCongratulatesANewTopScore() throws Exception {
        Files.createDirectories(leaderboardFile().getParent());
        Files.writeString(leaderboardFile(), "# earlier runs\n0 5000 125\n0 3000 100\n2 9999 150\n");
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        var hud = loader.loadClass("infinite.CourseHud");
        assertTrue((float) controller().getClass().getMethod("openingBoardAlpha").invoke(controller()) > 0,
                "the zone's top 10 shows as the run starts");
        assertEquals(" 1    5000  1.25X", hud.getMethod("boardLine", int.class, loader.loadClass("infinite.Leaderboard$Entry"))
                .invoke(null, 1, top(0).get(0)));
        assertEquals(List.of(5000, 3000), top(0).stream().map(e -> {
            try { return entryScore(e); } catch (Exception x) { throw new AssertionError(x); } }).toList(),
                "only Green Hill's scores, highest first");
        // Overtaking the zone's top score cheers mid-run.
        assertFalse(controllerFlag("celebrating"));
        GameServices.gameState().addScore(5000);
        fixture.stepIdleFrames(1);
        assertTrue(controllerFlag("celebrating"), "a banner as the run passes 5000");
        fixture.sprite().setInvulnerableFrames(20000);
        for (int i = 0; i < 400; i++) stepTerrain(fixture, 1);
        assertFalse((float) controller().getClass().getMethod("openingBoardAlpha").invoke(controller()) > 0,
                "the opening board has gone");
        fixture.sprite().setInvulnerableFrames(0);

        // A death records the run; CONTINUE and a later death replace that entry rather than adding one.
        dieAndWaitForMenu(fixture, 1);
        assertEquals(1, controllerInt("rank"));
        assertEquals("NEW TOP SCORE!", hud.getMethod("rankText", controller().getClass()).invoke(null, controller()));
        int first = GameServices.gameState().getScore();
        assertEquals(first, entryScore(top(0).get(0)));
        fixture.stepFrame(false, false, false, false, true);
        assertFalse(gameOver(), "continued");
        finishContinue(fixture);
        dieAndWaitForMenu(fixture, 0);
        int second = GameServices.gameState().getScore();
        assertTrue(second > first);
        assertEquals(3, top(0).size(), "one entry per run");
        assertEquals(second, entryScore(top(0).get(0)));
        assertEquals(1, top(2).size(), "other zones are untouched");
        // Saved to disk, and read back by a fresh table.
        var reread = loader.loadClass("infinite.Leaderboard").getConstructor(Path.class).newInstance(leaderboardFile());
        var rereadTop = (List<?>) reread.getClass().getMethod("top", int.class).invoke(reread, 0);
        assertEquals(second, entryScore(rereadTop.get(0)));

        // Each zone keeps only its top 10; a run outside them is not ranked.
        var submit = leaderboard().getClass().getMethod("submit", int.class, long.class, int.class, double.class);
        for (int run = 1; run <= 12; run++) submit.invoke(leaderboard(), 0, (long) run, 100_000 + run, 2.0);
        assertEquals(10, top(0).size());
        assertEquals(100_012, entryScore(top(0).get(0)));
        assertEquals(0, submit.invoke(leaderboard(), 0, 99L, 1, 1.0), "too low to place");
        assertEquals(5, submit.invoke(leaderboard(), 0, 100L, 100_009, 1.0), "a tie places after the earlier run");
    }

    @Test void sonicSweatsAndTheRingCountFlashesWhileHeCannotPayTheToll() throws Exception {
        Files.createDirectories(leaderboardFile().getParent());
        Files.writeString(leaderboardFile(), "0 5000 125\n");
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        var player = fixture.sprite();
        player.setInvulnerableFrames(20000);
        var hud = loader.loadClass("infinite.CourseHud");
        var flashing = hud.getMethod("ringsFlashing", controller().getClass());
        var rings = GameServices.level().getLevelGamestate();
        assertEquals("TOP 5000", hud.getMethod("topText", controller().getClass()).invoke(null, controller()),
                "the zone's top score heads the HUD");
        rings.setRings(0);
        assertTrue(controllerFlag("inDanger"), "no rings, no shield: one hit away");
        boolean on = false, off = false;
        for (int i = 0; i < 40; i++) {
            stepTerrain(fixture, 1);
            boolean flash = (boolean) flashing.invoke(null, controller());
            on |= flash;
            off |= !flash;
        }
        assertTrue(controllerInt("dangerFrames") >= 40, "the sweat clock runs");
        assertTrue(on && off, "the ring count flashes");
        rings.setRings(19);
        stepTerrain(fixture, 1);
        assertTrue(controllerFlag("inDanger"), "19 rings cannot pay the 20-ring toll");
        rings.setRings(20);
        stepTerrain(fixture, 1);
        assertFalse(controllerFlag("inDanger"), "20 rings can");
        assertEquals(0, controllerInt("dangerFrames"));
        assertFalse((boolean) flashing.invoke(null, controller()));
        rings.setRings(3);
        player.giveShield();
        stepTerrain(fixture, 1);
        assertFalse(controllerFlag("inDanger"), "a shield takes the next hit");
        GameServices.gameState().addScore(10_000);
        assertEquals("TOP " + GameServices.gameState().getScore(),
                hud.getMethod("topText", controller().getClass()).invoke(null, controller()), "a run ahead is the top");
    }

    @Test void idleTitleShowsTheZoneLeaderboardsThenReturns() throws Exception {
        Files.createDirectories(leaderboardFile().getParent());
        Files.writeString(leaderboardFile(), "0 5000 125\n4 700 100\n");
        launch(WidescreenAspect.NATIVE_4_3);
        TitleScreenProvider title = GameServices.module().getTitleScreenProvider();
        var boardAccessor = title.getClass().getDeclaredMethod("board");
        boardAccessor.setAccessible(true);
        Object board = boardAccessor.invoke(title);
        var update = board.getClass().getDeclaredMethod("update", com.openggf.control.InputHandler.class, boolean.class);
        var showing = board.getClass().getDeclaredMethod("showing");
        var pageZone = board.getClass().getDeclaredMethod("pageZone");
        var pageCount = board.getClass().getDeclaredMethod("pageCount");
        for (var method : List.of(update, showing, pageZone, pageCount)) method.setAccessible(true);
        var idleField = board.getClass().getDeclaredField("IDLE_FRAMES");
        var pageField = board.getClass().getDeclaredField("PAGE_FRAMES");
        idleField.setAccessible(true);
        pageField.setAccessible(true);
        int idle = idleField.getInt(null);
        int page = pageField.getInt(null);
        var input = new com.openggf.control.InputHandler();
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.neutral());
        for (int i = 0; i < idle - 1; i++) assertFalse((boolean) update.invoke(board, input, true));
        assertFalse((boolean) showing.invoke(board), "the title shows until it has been idle");
        update.invoke(board, input, false);
        for (int i = 0; i < idle - 1; i++) update.invoke(board, input, true);
        assertFalse((boolean) showing.invoke(board), "leaving the active title restarts the countdown");
        update.invoke(board, input, true);
        assertTrue((boolean) showing.invoke(board));
        assertEquals(3, pageCount.invoke(board), "ZONE LEADERS, then Green Hill and Star Light");
        assertEquals(-1, pageZone.invoke(board));
        for (int i = 0; i < page; i++) update.invoke(board, input, true);
        assertEquals(0, pageZone.invoke(board));
        for (int i = 0; i < page; i++) update.invoke(board, input, true);
        assertEquals(4, pageZone.invoke(board));
        for (int i = 0; i < page; i++) update.invoke(board, input, true);
        assertFalse((boolean) showing.invoke(board), "back to the title after the last page");
        for (int i = 0; i < idle; i++) update.invoke(board, input, true);
        assertTrue((boolean) showing.invoke(board), "and back again after another idle spell");
        int left = com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_LEFT;
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(left, left, 0, 0, false, false), null));
        assertTrue((boolean) update.invoke(board, input, true), "the dismissing press is kept from the title");
        assertFalse((boolean) showing.invoke(board));
        assertEquals(0, title.startZoneIndex(), "the press did not move the zone picker");
    }

    @ParameterizedTest(name = "zone {0}")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void backgroundStaysContinuousAcrossWindowShifts(int zone) throws Exception {
        var fixture = launch(WidescreenAspect.WIDE_16_9, zone, 0);
        fixture.stepIdleFrames(2);
        fixture.sprite().setInvulnerableFrames(20000);
        var parallax = GameServices.parallaxOrNull();
        int[] previous = null;
        int previousCamera = 0;
        int shifts = 0;
        for (int frame = 0; frame < 3000 && shifts < 2; frame++) {
            long origin = originPixels();
            stepTerrain(fixture, 1);
            boolean shifted = originPixels() != origin;
            if (shifted) shifts++;
            // Re-derive this frame's scroll from the final camera (an idempotent repeat for the stock routines).
            GameServices.level().recomputeParallaxAfterRewindRestore();
            int[] lines = parallax.getHScroll().clone();
            int camera = fixture.camera().getX();
            for (int line = 0; line < 224; line++) {
                assertEquals((short) -camera, (short) (lines[line] >> 16), "FG stays on the local camera");
            }
            if (previous != null && shifted) {
                // Across a shift, bands move no further than the camera itself (plus GHZ's 1px cloud
                // drift). Band boundaries follow camera Y, so a few lines may change band (GHZ's water
                // lines re-slice whenever camera Y crosses 32px, as in the stock level); a shift that
                // moved the bands would move nearly every line.
                int step = Math.abs(camera + (shifted ? 4096 : 0) - previousCamera);
                int jumped = 0;
                for (int line = 0; line < 224; line++) {
                    if (Math.abs((short) ((short) lines[line] - (short) previous[line])) > step + 2) jumped++;
                }
                assertTrue(jumped <= 24, "zone " + zone + ": " + jumped + " background lines jumped at frame "
                        + frame + " (camera moved " + step + (shifted ? ", window shifted" : "") + ")");
            }
            previous = lines;
            previousCamera = camera;
        }
        assertEquals(2, shifts, "crossed two window shifts");
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
        // The live rate glides up to the new stage (as the rewind tape coast does) rather than snapping.
        var steps = clock.getClass().getMethod("nextFrameSteps");
        var rate = clock.getClass().getMethod("multiplier");
        double last = (double) rate.invoke(clock);
        assertEquals(1.0, last, "the step rate has not moved yet");
        for (int frame = 0; frame < 60; frame++) {
            steps.invoke(clock);
            double now = (double) rate.invoke(clock);
            assertTrue(now > last && now - last < 0.005, "smooth ramp at frame " + frame + ": " + last + " -> " + now);
            last = now;
        }
        assertEquals(1.25, last, 1e-9, "one 0.25x stage ramps over 60 frames");
        steps.invoke(clock);
        assertEquals(1.25, (double) rate.invoke(clock), 1e-9, "and holds at the stage");
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
        assertEquals(List.of("> CONTINUE", "  RESTART", "  EXIT"), menu.invoke(null, controller()));
        int deathTicks = ticks();
        fixture.stepFrame(false, false, false, false, true);
        // No reload: the same controller and terrain carry on.
        assertFalse(GameServices.level().isRespawnRequestedForRewind(), "CONTINUE does not reload the level");
        assertSame(controller, controller());
        assertFalse(gameOver());
        var player = fixture.sprite();
        assertFalse(player.getDead());
        long revived = originPixels() + player.getCentreX();
        assertTrue(revived <= safe, "back at or behind the last safe spot");
        assertRunway(revived);
        assertEquals(floorAt(revived) - 19, player.getCentreY());
        assertEquals(1, GameServices.gameState().getLives(), "CONTINUE spends one spare life");
        assertEquals(1, controllerInt("displayLives"));
        assertEquals(30, GameServices.level().getLevelGamestate().getRings(), "CONTINUE keeps the rings");
        assertTrue(GameServices.gameState().getScore() >= score, "score carries over");

        // A ghost of Sonic glides back from where he died while the real Sonic waits, hidden.
        assertEquals(1, controllerInt("resumePhase"), "the glide");
        assertTrue(player.isHidden() && player.isObjectControlled());
        int restartX = player.getCentreX();
        int previousGhost = controllerInt("ghostX"), previousCamera = fixture.camera().getX(), glide = 0;
        int frozenScore = GameServices.gameState().getScore();
        while (controllerInt("resumePhase") == 1 && glide++ < 120) {
            fixture.stepFrame(false, false, false, true, true); // input does not move him yet
            int ghost = controllerInt("ghostX");
            assertTrue(Math.abs(ghost - restartX) <= Math.abs(previousGhost - restartX), "the ghost closes in");
            assertTrue(Math.abs(fixture.camera().getX() - previousCamera) <= 12, "the camera glides, no cut");
            previousGhost = ghost;
            previousCamera = fixture.camera().getX();
            assertEquals(restartX, player.getCentreX());
        }
        assertTrue(glide >= 45 && glide <= 91, "a 45-90 frame glide: " + glide);
        assertEquals(restartX, controllerInt("ghostX"), "the ghost lands on the restart spot");

        // READY: Sonic appears and waits; nothing runs until the player goes.
        assertEquals(2, controllerInt("resumePhase"));
        assertFalse(player.isHidden());
        for (int i = 0; i < 10; i++) fixture.stepIdleFrames(1);
        assertEquals(2, controllerInt("resumePhase"), "still waiting");
        assertEquals(restartX, player.getCentreX());
        assertEquals(deathTicks, ticks(), "the clock waits through the glide and READY");
        assertEquals(frozenScore, GameServices.gameState().getScore(), "so does the score");
        assertEquals(1.5, speed(), "the run keeps the speed stage it died at");
        for (int i = 0; i < 20 && controllerInt("resumePhase") == 2; i++) fixture.stepFrame(false, false, false, true, false);
        assertEquals(0, controllerInt("resumePhase"), "right sets off");
        assertFalse(player.isObjectControlled());
        assertTrue(controllerInt("goFrames") > 0, "GO!");
        assertTrue(player.getInvulnerableFrames() > 60, "post-continue blink");
        assertEquals(0x540, player.getGSpeed(), "a running start");
        // The pace sets off at 1x and eases back up to the stage within 90 frames.
        var clock = clock();
        var rate = clock.getClass().getMethod("multiplier");
        assertEquals(1.0, (double) rate.invoke(clock), 1e-9);
        for (int i = 0; i < 90; i++) clock.getClass().getMethod("nextFrameSteps").invoke(clock);
        assertEquals(1.5, (double) rate.invoke(clock), 1e-9);
        assertTrue(fixture.camera().getX() < player.getCentreX() - player.getXRadius(), "Sonic is on screen");
        int cameraX = fixture.camera().getX();
        for (int i = 0; i < 60; i++) fixture.stepFrame(false, false, false, true, false);
        assertFalse(player.getDead());
        assertTrue(fixture.camera().getX() > cameraX, "the course scrolls again");
        assertTrue(ticks() > deathTicks, "and the clock runs");
        // The resumed run can die again and continue with its last spare life.
        dieAndWaitForMenu(fixture, 1);
        assertTrue(controllerFlag("canContinue"));
        fixture.stepFrame(false, false, false, false, true);
        assertFalse(fixture.sprite().getDead());
        assertEquals(0, GameServices.gameState().getLives());
        finishContinue(fixture);
        assertFalse(fixture.sprite().getDead());
    }

    /** A restart spot has pit-free floor for the 448px runway ahead. */
    private void assertRunway(long worldX) throws Exception {
        for (int dx = 0; dx <= 448; dx += 8) {
            assertTrue(floorAt(worldX + dx) >= 0, "pit " + dx + "px ahead of the restart spot " + worldX);
        }
    }

    /** Holds right through the CONTINUE glide and READY until the run sets off. */
    private void finishContinue(HeadlessTestFixture fixture) throws Exception {
        for (int i = 0; i < 400 && controllerInt("resumePhase") != 0; i++) {
            fixture.stepFrame(false, false, false, true, false);
        }
        assertEquals(0, controllerInt("resumePhase"));
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
        assertTrue(revived <= safe, "back at or behind the last safe spot");
        assertTrue(revived + 448 < pit, "far enough back to see the pit coming: " + (pit - revived) + "px");
        assertRunway(revived);
        finishContinue(fixture);
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
        assertEquals(List.of("  CONTINUE", "> RESTART", "  EXIT"), menu.invoke(null, controller()));
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
                // Timing/scroll setup independent of terrain: airborne, stationary, and left of
                // the 45% follow point (144px at 4:3) so only the minimum scroll moves the camera.
                NativePositionOps.writeXPosResetSubpixel(player, fixture.camera().getX() + 64);
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
    void sonicGainsGroundAcrossSpeedupAndIsHeldAtTheFollowPoint(WidescreenAspect aspect) throws Exception {
        var fixture = launch(aspect);
        fixture.stepIdleFrames(2);
        var player = fixture.sprite();
        setTicks(1790);
        int initialLead = 40;
        NativePositionOps.writeXPosResetSubpixel(player, 1000);
        fixture.camera().setX((short) (1000 - initialLead));
        int margin = fixture.camera().getWidth() * 45 / 100;
        boolean reachedMargin = false;
        // Real player integration and module pacing on each presentation frame.
        // Reset only height/vertical velocity to isolate scrolling from course obstacles.
        for (int frame = 0; frame < 600; frame++) {
            int steps = GameServices.module().gameplayStepsPerFrame();
            for (int step = 0; step < steps; step++) {
                NativePositionOps.writeYPosResetSubpixel(player, 600);
                player.setAir(true);
                player.setYSpeed((short) 0);
                player.setXSpeed((short) 0x540); // The course top speed.
                fixture.stepFrame(false, false, false, true, false);
                int lead = player.getCentreX() - fixture.camera().getX();
                assertTrue(lead >= initialLead, "Sonic must gain, not lose, ground at 1.25x");
                // Controller precedes player integration: allow the current tick's 6px movement.
                assertTrue(lead <= margin + 6, "the follow point bounds his lead");
                reachedMargin |= lead >= margin;
                assertFalse(player.getDead());
            }
        }
        assertEquals(1.25, speed());
        assertTrue(reachedMargin, "Sonic reaches the follow point at every viewport width");
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
        assertEquals(0x540, player.getMax(), "whole-game pacing keeps the course top speed");
        assertEquals(1.25, speed());
        assertEquals("SPEED 1.25X", loader.loadClass("infinite.CourseHud")
                .getMethod("speedText", controller().getClass()).invoke(null, controller()));
        assertEquals("NEXT 30", loader.loadClass("infinite.CourseHud")
                .getMethod("countdownText", controller().getClass()).invoke(null, controller()));
        var after = registry.capture();
        int score = GameServices.gameState().getScore();
        int camera = fixture.camera().getX();
        registry.restore(before);
        assertEquals(0x540, player.getMax());
        fixture.stepFrame(false, false, false, true, false);
        assertEquals(0x540, player.getMax(), "whole-game pacing keeps the course top speed");
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
        assertEquals(0x540, fixture.sprite().getMax(), "the course lowers the 0x600 stock top speed");
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
        long spilled = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof com.openggf.level.rings.LostRingObjectInstance && !o.isDestroyed()).count();
        assertEquals(guard.equals("toll") ? 20 : 0, spilled, "the toll's rings spill out of Sonic");
        // 25 - 20 for the toll; a shield keeps all 5.
        assertEquals(5, rings.getRings(), guard.equals("toll") ? "the toll costs exactly 20 rings"
                : "a shield keeps every ring");
        fixture.stepIdleFrames(4);
        assertFalse(player.isHurt(), "the blink keeps the same badnik from hitting again");
        assertEquals(5, rings.getRings());
    }

    @Test void monitorsAreSeededOnLevelGroundWithAMixOfKinds() throws Exception {
        launch(WidescreenAspect.NATIVE_4_3);
        int monitors = 0;
        var kinds = new TreeMap<Integer, Integer>();
        for (long section = 0; section < 1000; section++) {
            Object monitor = monitor(section);
            assertEquals(monitor, monitor(section), "seeded");
            if (monitor == null) continue;
            monitors++;
            kinds.merge((int) call(monitor, "kind"), 1, Integer::sum);
            assertTrue(section >= 6, "no monitor on the opening runway");
            assertFalse(isCorridor(section) || platformRun(section), "monitors stand on ordinary ground");
            assertNull(hazard(section), "no monitor in a hazard section");
            long x = (long) call(monitor, "worldX");
            Object road = roadOf(section);
            if (road != null) {
                Object[] all = (Object[]) call(road, "stones");
                Object last = all[all.length - 1];
                assertEquals((long) call(last, "worldX"), x, "a high road's monitor stands on its last platform");
                assertEquals((int) call(last, "surface") - 15, (int) call(monitor, "y"));
                continue;
            }
            int floor = floorAt(x);
            assertEquals(floor - 15, (int) call(monitor, "y"), "standing on the floor");
            for (long dx = -16; dx <= 16; dx++) assertTrue(Math.abs(floorAt(x + dx) - floor) <= 4, "level ground");
        }
        // High-road sections hold at most one monitor up top, so fewer stand on the ground.
        assertTrue(monitors >= 25 && monitors <= 140, "about one in eight open sections: " + monitors);
        // S1 monitor subtypes: 4 shield, 6 Super Ring (no Invincibility).
        assertEquals(Set.of(4, 6), kinds.keySet());
        int shields = kinds.get(4), total = kinds.get(4) + kinds.get(6);
        assertTrue(shields * 100 >= total * 35 && shields * 100 <= total * 85, "about three in five shields: " + kinds);
    }

    @ParameterizedTest @ValueSource(ints = {4, 6})
    void touchingAMonitorBreaksItGivesItsRewardAndReplays(int kind) throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        fixture.sprite().setInvulnerableFrames(20000);
        ObjectInstance monitor = null;
        for (int frame = 0; frame < 20000 && monitor == null; frame++) {
            stepTerrain(fixture, 1);
            // Leave the other kinds unbroken: their rewards (a shield, stars) would carry into the check.
            for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
                if (object.getClass().getName().equals("infinite.CourseMonitor") && (int) call(object, "kind") != kind
                        && object instanceof com.openggf.level.objects.AbstractObjectInstance other) other.setDestroyed(true);
            }
            monitor = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(o -> !o.isDestroyed() && o.getClass().getName().equals("infinite.CourseMonitor"))
                    .filter(o -> { try { return (int) call(o, "kind") == kind && !(boolean) call(o, "isBroken"); } catch (Exception e) { throw new AssertionError(e); } })
                    .findFirst().orElse(null);
        }
        assertNotNull(monitor, "the course places monitors of kind " + kind);
        var player = fixture.sprite();
        player.setInvulnerableFrames(0);
        var rings = GameServices.level().getLevelGamestate();
        rings.setRings(3);
        // Monitors and patrols share open sections; the touch pass handles only the first overlap.
        for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (object.getClass().getName().equals("infinite.CourseBadnik") && Math.abs(object.getX() - monitor.getX()) < 160
                    && object instanceof com.openggf.level.objects.AbstractObjectInstance badnik) badnik.setDestroyed(true);
        }
        NativePositionOps.writeXPosResetSubpixel(player, monitor.getX());
        NativePositionOps.writeYPosResetSubpixel(player, monitor.getY());
        player.setXSpeed((short) 0); player.setGSpeed((short) 0); player.setYSpeed((short) 0);
        player.setAir(true);
        fixture.camera().setX((short) Math.max(0, monitor.getX() - 160));
        // The search leaves the camera wherever the policy ran; put the box in its touch window.
        fixture.camera().setY((short) Math.max(0, monitor.getY() - 112));
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        fixture.stepIdleFrames(1);
        assertTrue((boolean) call(monitor, "isBroken"), "any touch breaks the box, even without rolling");
        switch (kind) {
            case 4 -> assertTrue(player.hasShield(), "shield");
            default -> assertEquals(13, rings.getRings(), "Super Ring: ten rings");
        }
        fixture.stepIdleFrames(8);
        var expected = registry.capture().entries().get("object-manager");
        registry.restore(before);
        fixture.stepIdleFrames(9);
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", expected,
                registry.capture().entries().get("object-manager")), "break and burst replay");
    }
    private Object monitor(long section) throws Exception {
        return loader.loadClass("infinite.MonitorPlan").getMethod("at", terrain().getClass(), long.class)
                .invoke(null, terrain(), section);
    }

    private Object[] route(long section) throws Exception {
        return (Object[]) loader.loadClass("infinite.RoutePlan").getMethod("at", terrain().getClass(), long.class)
                .invoke(null, terrain(), section);
    }
    private Object hazard(long section) throws Exception {
        return loader.loadClass("infinite.HazardPlan").getMethod("at", terrain().getClass(), long.class)
                .invoke(null, terrain(), section);
    }
    private static final int SPIKES = 0, FIREBALL = 1, BIG_BALL = 2, CHAIN = 3, FLAME = 4, WRECKING_BALL = 5;
    /** Registry zones 0-5 are GHZ, MZ, SYZ, LZ, SLZ, SBZ; each one's ground signature hazard. */
    private static int signature(int zone) {
        return switch (zone) { case 0 -> WRECKING_BALL; case 2 -> BIG_BALL; case 3 -> CHAIN; case 5 -> FLAME; default -> -1; };
    }

    /** The high road over this section's stretch, or null (none over corridors). */
    private Object roadOf(long section) throws Exception {
        if (Math.floorMod(section, 4) == 3) return null;
        return loader.loadClass("infinite.RoutePlan").getMethod("route", terrain().getClass(), long.class)
                .invoke(null, terrain(), Math.floorDiv(section, 4));
    }

    @ParameterizedTest(name = "zone {0} act {1}")
    @org.junit.jupiter.params.provider.MethodSource("courseActs")
    void highRoadsClimbOnStockPlatformsToASeparatePathAboveTheGround(int zone, int act) throws Exception {
        launch(WidescreenAspect.NATIVE_4_3, zone, act);
        var stationary = new TreeSet<Integer>();
        for (Object kind : (List<?>) call(terrain(), "platformKinds")) {
            if (!(boolean) call(kind, "falls")) stationary.add((int) call(kind, "objectId"));
        }
        int roads = 0, guarded = 0, sections = 0;
        for (long stretch = 0; stretch < 250; stretch++) {
            Object road = roadOf(stretch * 4);
            if (road == null) continue;
            roads++;
            assertTrue(stretch >= 2, "the first stretches stay on the ground");
            Object[] stones = (Object[]) call(road, "stones");
            int first = (int) call(road, "road");
            int surface = (int) call(road, "roadSurface");
            assertTrue(stones.length - first >= 3, "a road of at least three platforms");
            long start = (long) call(stones[0], "worldX") - (int) call(stones[0], "halfWidth");
            long end = (long) call(road, "roadEnd");
            int high = Integer.MAX_VALUE, low = Integer.MIN_VALUE;
            for (long x = start - 160; x <= end; x++) {
                high = Math.min(high, floorAt(x));
                low = Math.max(low, floorAt(x));
            }
            assertEquals(high - 128, surface, "the road runs 128px above the highest floor beneath it");
            assertTrue(end <= (stretch * 4 + 2) * 512 + 320, "the road ends with ground left before the next corridor");
            int previous = low;
            long previousRight = start - 160;
            for (int i = 0; i < stones.length; i++) {
                int top = (int) call(stones[i], "surface");
                long left = (long) call(stones[i], "worldX") - (int) call(stones[i], "halfWidth");
                assertTrue(stationary.contains((int) call(stones[i], "objectId")), "a stationary stock platform the act places");
                assertTrue(previous - top >= 0 && previous - top <= 48, "each climb is at most 48px: " + (previous - top));
                if (i >= first) {
                    assertEquals(surface, top, "one level road");
                    if (i > first) assertEquals(8, left - previousRight, "close enough to run across");
                } else if (i > 0 && top != previous) {
                    assertEquals(120, left - previousRight, "a step a held jump reaches");
                }
                previous = top;
                previousRight = left + 2L * (int) call(stones[i], "halfWidth");
            }
            for (long section = stretch * 4; section < stretch * 4 + 3; section++) {
                sections++;
                assertNull(hazard(section), "no hazard under a road");
                Object below = encounter(section);
                if (below != null) {
                    guarded++;
                    assertFalse((boolean) call(below, "flying"), "patrols beneath are on the ground");
                }
                Object[] here = stones(section);
                assertTrue(here.length <= 8, "fits the controller's eight stone slots per section");
                for (Object stone : here) {
                    assertEquals(section, Math.floorDiv((long) call(stone, "worldX"), 512), "spawned by its own section");
                }
            }
        }
        if (stationary.isEmpty()) assertEquals(0, roads, "acts without wide stationary platforms have no high roads");
        else {
            assertTrue(roads >= 5 && roads <= 120, "high roads: " + roads);
            assertTrue(guarded * 10 >= sections * 6, "the low path is mostly guarded: " + guarded + "/" + sections);
        }
    }

    /**
     * Real physics: a held-jump policy that only chooses when to jump (from the ground, then
     * from each step) climbs onto the act's first high road and runs it to the end.
     */
    @ParameterizedTest(name = "zone {0}")
    @ValueSource(ints = {0, 1, 2, 4, 5})
    void sonicClimbsOntoAHighRoadAndRunsItsLength(int zone) throws Exception {
        var fixture = launch(WidescreenAspect.WIDE_16_9, zone, 0);
        long stretch = 2;
        while (roadOf(stretch * 4) == null) stretch++;
        Object road = roadOf(stretch * 4);
        Object[] stones = (Object[]) call(road, "stones");
        int first = (int) call(road, "road");
        int surface = (int) call(road, "roadSurface");
        long start = (long) call(stones[0], "worldX") - (int) call(stones[0], "halfWidth");
        long end = (long) call(road, "roadEnd");
        fixture.stepIdleFrames(2);
        var player = fixture.sprite();
        player.setInvulnerableFrames(20000);
        // The policy crosses the corridor pit before the stretch; the climb starts on its far bank.
        for (int frame = 0; frame < 20000 && originPixels() + player.getCentreX() < start - 250; frame++) {
            stepTerrain(fixture, 1);
        }
        Set<Long> rode = new TreeSet<>();
        boolean onRoad = false;
        String lastState = "";
        boolean heldJump = false;
        var trail = new StringBuilder("plan");
        for (Object stone : stones) {
            trail.append(String.format(" %d..%d@%d", (long) call(stone, "worldX") - (int) call(stone, "halfWidth"),
                    (long) call(stone, "worldX") + (int) call(stone, "halfWidth"), (int) call(stone, "surface")));
        }
        trail.append(" |");
        for (int frame = 0; frame < 900 && originPixels() + player.getCentreX() < end - 8; frame++) {
            long x = originPixels() + player.getCentreX();
            long[] level = nextLevel(player, x, stones);
            boolean want = level != null && landing(player, x, level, true) >= level[0] + 8
                    && landing(player, x, level, true) <= level[1] - 8;
            // Hold through a jump; a new jump needs a fresh press, so release for a frame after landing.
            boolean jump = player.getAir() ? heldJump : want && !heldJump;
            heldJump = jump;
            int steer = 1;
            if (player.getAir() && level != null) {
                // Brake toward the front of the level, leaving runway for the next jump, as a player would.
                long land = landing(player, x, level, false);
                if (land != Long.MIN_VALUE && land > level[0] + 40) steer = -1;
                else if (land != Long.MIN_VALUE && land > level[0] + 16) steer = 0;
            }
            setTicks(0);
            fixture.camera().setX((short) Math.max(0, player.getCentreX() - 160));
            fixture.stepFrame(false, false, steer < 0, steer > 0, jump);
            x = originPixels() + player.getCentreX();
            String state = player.getAir() ? "air" : player.isOnObject() ? "obj" : "gnd";
            if (!state.equals(lastState)) {
                trail.append(String.format(" %s@%d,%d(vx%d)", state, x, player.getCentreY(), (int) player.getXSpeed()));
                lastState = state;
            }
            assertFalse(player.getDead());
            if (!player.getAir() && player.isOnObject()) {
                for (int i = 0; i < stones.length; i++) {
                    if (Math.abs(x - (long) call(stones[i], "worldX")) <= (int) call(stones[i], "halfWidth")) {
                        rode.add((long) call(stones[i], "worldX"));
                        onRoad |= i >= first;
                    }
                }
            }
            if (onRoad) assertTrue(player.getCentreY() < surface, "stayed on the high road:" + trail);
        }
        assertTrue(onRoad, "reached the road:" + trail);
        assertTrue(rode.contains((long) call(stones[stones.length - 1], "worldX")), "ran it to the end:" + trail);
        assertTrue(originPixels() + player.getCentreX() >= end - 8);
    }

    /** {left, right, top} of the next platform level above Sonic's feet ahead of him, or null. */
    private long[] nextLevel(com.openggf.sprites.playable.AbstractPlayableSprite player, long x, Object[] stones)
            throws Exception {
        int feet = player.getCentreY() + (player.getAir() ? 14 : 19);
        long[] level = null;
        for (Object stone : stones) {
            int top = (int) call(stone, "surface");
            long centre = (long) call(stone, "worldX");
            int half = (int) call(stone, "halfWidth");
            if (level == null) {
                if (centre + half <= x || top >= feet - 4) continue;
                level = new long[]{centre - half, centre + half, top};
            } else if (top == level[2]) {
                level[1] = centre + half;
            } else {
                break;
            }
        }
        return level;
    }

    /** Where Sonic's feet come down to the level's top: from a fresh held jump, or on his current arc. */
    private long landing(com.openggf.sprites.playable.AbstractPlayableSprite player, long x, long[] level, boolean fresh) {
        double g = 0x38 / 256.0;
        double vx = (fresh ? player.getGSpeed() : player.getXSpeed()) / 256.0;
        double vy = fresh ? -0x680 / 256.0 : player.getYSpeed() / 256.0;
        double feet = player.getCentreY() + (fresh ? 19 : 14);
        double drop = level[2] - feet; // negative: the top is above the feet
        double disc = vy * vy + 2 * g * drop;
        if (disc < 0) return Long.MIN_VALUE;
        double t = (-vy + Math.sqrt(disc)) / g;
        return t <= 0 ? Long.MIN_VALUE : x + Math.round(vx * t);
    }

    @ParameterizedTest(name = "zone {0} act {1}")
    @org.junit.jupiter.params.provider.MethodSource("courseActs")
    void hazardsAreTheZonesOwnOnLevelGroundOrInPits(int registryZone, int act) throws Exception {
        launch(WidescreenAspect.NATIVE_4_3, registryZone, act);
        // The hazard follows the ROM layout's zone (SBZ3 reuses Labyrinth's), in registry numbering.
        int zone = switch ((int) call(terrain(), "romZone")) { case 0 -> 0; case 1 -> 3; case 2 -> 1; case 3 -> 4; case 4 -> 2; default -> 5; };
        var counts = new TreeMap<Integer, Integer>();
        for (long section = 0; section < 1000; section++) {
            Object hazard = hazard(section);
            assertEquals(hazard, hazard(section), "seeded");
            if (hazard == null) continue;
            int kind = (int) call(hazard, "kind");
            counts.merge(kind, 1, Integer::sum);
            assertTrue(section >= 5, "the opening is hazard-free");
            assertFalse(platformRun(section));
            long x = (long) call(hazard, "worldX");
            if (kind == FIREBALL) {
                assertTrue(zone == 1 || zone == 4, "fireballs in Marble and Star Light only");
                assertTrue(isCorridor(section) && gapWidth(section) >= 96, "fireballs leap from corridor pits");
                assertEquals(-1, floorAt(x), "from the middle of the pit");
                continue;
            }
            assertFalse(isCorridor(section), "ground hazards stand on open sections");
            assertTrue(kind == SPIKES || kind == signature(zone), "the zone's own hazard: " + kind);
            assertEquals(floorAt(x), (int) call(hazard, "floor"));
            assertNull(encounter(section), "no badnik shares a hazard section");
            assertNull(monitor(section));
            assertNull(roadOf(section), "no hazard under a high road");
        }
        assertTrue(counts.getOrDefault(SPIKES, 0) >= 10, "spike beds in every zone: " + counts);
        if (signature(zone) >= 0) assertTrue(counts.getOrDefault(signature(zone), 0) >= 20, "signature: " + counts);
        if (zone == 1 || zone == 4) assertTrue(counts.getOrDefault(FIREBALL, 0) >= 20, "pit fireballs: " + counts);
    }

    /**
     * Real physics and timing: from one snapshot before the act's first signature hazard (fireball
     * pits in Marble and Star Light), several approaches are replayed: coasting for a while first,
     * then running or jumping at different distances. At least one passes cleanly, at least one is
     * hit, and a hit takes the 20-ring toll without knockback, as a badnik hit does.
     */
    @ParameterizedTest(name = "zone {0}")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void eachZoneHazardNeedsTimingAndHitsThroughTheRingToll(int zone) throws Exception {
        var fixture = launch(WidescreenAspect.WIDE_16_9, zone, 0);
        int wanted = signature(zone) >= 0 ? signature(zone) : FIREBALL;
        long section = 5;
        while (hazard(section) == null || (int) call(hazard(section), "kind") != wanted) section++;
        Object plan = hazard(section);
        long hx = (long) call(plan, "worldX");
        int reach = switch (wanted) {
            case FIREBALL -> gapWidth(section) / 2 + 16;
            case BIG_BALL -> 130;
            case CHAIN -> 100;
            case WRECKING_BALL -> 130;
            default -> 32;
        };
        fixture.stepIdleFrames(2);
        var player = fixture.sprite();
        player.setInvulnerableFrames(20000);
        for (int frame = 0; frame < 20000 && originPixels() + player.getCentreX() < hx - reach - 360; frame++) {
            stepTerrain(fixture, 1);
            clearPickups(hx);
        }
        player.setInvulnerableFrames(0);
        player.setInvincibleFrames(0);
        if (player.hasShield()) player.removeShield();
        GameServices.level().getLevelGamestate().setRings(50);
        fixture.stepIdleFrames(1);
        var registry = fixture.runtime().getRewindRegistry();
        var start = registry.capture();
        int passes = 0, hits = 0;
        var outcomes = new StringBuilder();
        // Coasting brakes to a stop just short of the hazard, once it is on screen and running (pit
        // fireballs only launch while visible), and waits there, so arrivals span a whole hazard
        // cycle (the flame's is 126 frames) as a player slowing down would.
        for (int coast = 0; coast <= 120; coast += 12) {
            for (int lead = -1; lead <= 200; lead += lead < 0 ? 41 : 20) {
                registry.restore(start);
                boolean jumped = false, hit = false;
                int waitFrom = -1;
                for (int frame = 0; frame < 900; frame++) {
                    long x = originPixels() + player.getCentreX();
                    if (x > hx + reach + 48 && !player.getAir()) break;
                    boolean jump = lead >= 0 && (player.getAir() ? jumped : x >= hx - reach - lead && !jumped);
                    if (jump) jumped = true;
                    setTicks(0);
                    fixture.camera().setX((short) Math.max(0, player.getCentreX() - 160));
                    if (waitFrom < 0 && x >= hx - reach - 120) waitFrom = frame;
                    boolean waiting = waitFrom >= 0 && frame < waitFrom + coast;
                    boolean brake = waiting && player.getGSpeed() > 0 && !player.getAir();
                    fixture.stepFrame(false, false, brake, !waiting, jump);
                    if (player.getDead()) { hit = true; break; }
                    if (GameServices.level().getLevelGamestate().getRings() < 50) {
                        hit = true;
                        assertEquals(30, GameServices.level().getLevelGamestate().getRings(), "the 20-ring toll");
                        assertFalse(player.isHurt(), "no knockback");
                        assertTrue(player.getInvulnerableFrames() > 0, "post-hit blink");
                        break;
                    }
                }
                outcomes.append(String.format(" coast%d/lead%d:%s", coast, lead, hit ? "hit" : "pass"));
                if (hit) hits++; else passes++;
            }
        }
        assertTrue(passes > 0, "timing can beat it:" + outcomes);
        assertTrue(hits > 0, "it is a real hazard:" + outcomes);
        // The hazard replays exactly from the snapshot.
        registry.restore(start);
        for (int i = 0; i < 90; i++) { setTicks(0); fixture.stepFrame(false, false, false, true, false); }
        var expected = registry.capture().entries().get("object-manager");
        registry.restore(start);
        for (int i = 0; i < 90; i++) { setTicks(0); fixture.stepFrame(false, false, false, true, false); }
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", expected,
                registry.capture().entries().get("object-manager")), "hazard motion replays");
    }

    /** Leaves only the hazard ahead: badniks and monitors near it would muddy the timing trials. */
    private void clearPickups(long hazardX) throws Exception {
        for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
            String name = object.getClass().getName();
            if ((name.equals("infinite.CourseBadnik") || name.equals("infinite.CourseMonitor"))
                    && Math.abs(object.getX() + originPixels() - hazardX) < 700
                    && object instanceof com.openggf.level.objects.AbstractObjectInstance other) other.setDestroyed(true);
        }
    }

    @Test void deathMenuExitLeavesForTheTitleScreen() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        dieAndWaitForMenu(fixture, 0);
        var menu = loader.loadClass("infinite.CourseHud").getMethod("menuLines", controller().getClass());
        assertEquals(List.of("> RESTART", "  EXIT"), menu.invoke(null, controller()));
        for (int i = 0; i < 10; i++) fixture.stepFrame(false, true, false, false, false);
        assertEquals(List.of("  RESTART", "> EXIT"), menu.invoke(null, controller()), "down stops at the last option");
        fixture.stepFrame(false, false, false, false, true);
        assertTrue(controllerFlag("exitRequested"));
        assertEquals(GameOverExit.TITLE_SCREEN, GameServices.level().getGameOverExitRequested(),
                "the engine's GAME OVER exit fades to the title screen");
        assertFalse(GameServices.level().isRespawnRequestedForRewind(), "no restart is queued");
        assertEquals(1.0, GameServices.module().gameplayAudioPlaybackRate(), "the challenge releases the audio rate");
    }

    @Test void deathMenuWithASpareLifeOffersContinueRestartAndExit() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        dieAndWaitForMenu(fixture, 1);
        var menu = loader.loadClass("infinite.CourseHud").getMethod("menuLines", controller().getClass());
        fixture.stepFrame(false, true, false, false, false);
        fixture.stepIdleFrames(1);
        fixture.stepFrame(false, true, false, false, false);
        assertEquals(List.of("  CONTINUE", "  RESTART", "> EXIT"), menu.invoke(null, controller()));
        fixture.stepFrame(false, false, false, false, true);
        assertEquals(GameOverExit.TITLE_SCREEN, GameServices.level().getGameOverExitRequested());
    }

    @Test void escapeLeavesTheCourseForTheTitleScreen() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        // The module reads Escape from the live input its title screen last received.
        var title = GameServices.module().getTitleScreenProvider();
        var input = new com.openggf.control.InputHandler();
        var field = title.getClass().getDeclaredField("input");
        field.setAccessible(true);
        field.set(title, input);
        fixture.stepIdleFrames(30);
        assertFalse(controllerFlag("exitRequested"));
        assertNull(GameServices.level().getGameOverExitRequested());
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        fixture.stepIdleFrames(1);
        assertTrue(controllerFlag("exitRequested"), "a tap of Escape leaves mid-run");
        assertEquals(GameOverExit.TITLE_SCREEN, GameServices.level().getGameOverExitRequested());
        assertFalse(fixture.sprite().getDead(), "leaving is not a death");
    }

    @Test void cruisingSonicShowsTheFullSpeedRunFrames() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        var player = fixture.sprite();
        player.setInvulnerableFrames(20000);
        var profile = (com.openggf.sprites.animation.ScriptedVelocityAnimationProfile) player.getAnimationProfile();
        assertEquals(0x500, profile.getRunSpeedThreshold(), "the course lowers the stock 0x600 Run threshold");
        var run = new HashSet<>(player.getAnimationSet().getScript(
                com.openggf.game.sonic1.constants.Sonic1AnimationIds.RUN.id()).frames());
        int runFrames = 0, fastest = 0;
        for (int frame = 0; frame < 600; frame++) {
            stepTerrain(fixture, 1);
            if (player.getAir() || player.getAngle() != 0) continue;
            int speed = Math.abs(player.getGSpeed());
            fastest = Math.max(fastest, speed);
            if (speed >= 0x520 && run.contains(player.getMappingFrame())) runFrames++;
        }
        assertTrue(fastest < 0x600, "flat cruising never reaches the stock Run speed: " + fastest);
        assertTrue(runFrames > 60, "but shows the Run frames: " + runFrames);
    }

    private boolean underRoute(ObjectInstance enemy) {
        try {
            long anchor = (long) enemy.getClass().getMethod("worldAnchor").invoke(enemy);
            return roadOf(Math.floorDiv(anchor, 512)) != null;
        } catch (Exception e) { throw new AssertionError(e); }
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
        fixture.sprite().setInvulnerableFrames(20000);
        ObjectInstance enemy = null;
        for (int i = 0; i < 4000 && enemy == null; i++) {
            stepTerrain(fixture, 1);
            // 0 = a ground badnik, 1 = a flyer (whichever species the act's line-up chose).
            // A high route's ledge would catch the falling Sonic above the patrol beneath it.
            enemy = enemies().stream().filter(o -> flyingEnemy(o) == (subtype == 1) && !underRoute(o)).findFirst().orElse(null);
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
