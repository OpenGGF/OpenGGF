package com.openggf.mods.code;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.session.SessionManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.objects.*;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import javax.tools.ToolProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Sonic Survivors (examples/sonic-survivors): packages the mod through {@code ggfmod}, then
 * drives its arenas headlessly. The mod's classes live in their own loader, so its state is
 * read and nudged through reflection.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestSonicSurvivors {
    @TempDir static Path temp;
    static final String SEED_PROPERTY = "sonic-survivors.seed";
    static URLClassLoader loader;
    static Path jar;
    SharedLevel bootstrap;

    static final int CAMP = 0, INTRO = 1, FIGHT = 2, BOSS = 3, CLEAR_WAIT = 4, CLEAR = 5, DEAD = 6;

    @BeforeAll static void compileAndPackage() throws Exception {
        System.setProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY, temp.resolve("saves").toString());
        System.setProperty(SEED_PROPERTY, "0x534F4E4943");
        Path project = Path.of("examples/sonic-survivors");
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
        jar = temp.resolve("sonic-survivors.jar");
        assertEquals(0, GgfModCli.run(new String[]{"package", "--input", classes.toString(),
                "--out", jar.toString()}, System.out), "ggfmod package validates the mod");
        loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, TestSonicSurvivors.class.getClassLoader());
    }

    @AfterAll static void closeLoader() throws Exception {
        System.clearProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY);
        System.clearProperty(SEED_PROPERTY);
        if (loader != null) loader.close();
    }

    @BeforeEach void freshProfile() throws Exception {
        Path saves = temp.resolve("saves");
        if (Files.exists(saves)) {
            try (var walk = Files.walk(saves)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @AfterEach void closeSession() { if (bootstrap != null) bootstrap.dispose(); }

    private HeadlessTestFixture launch(int zone, int act) throws Exception {
        return launch(zone, act, "sonic", "");
    }

    private HeadlessTestFixture launch(int zone, int act, String main, String sidekick) throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekick);
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.WIDE_16_9.name());
        config.resolveDisplayAspect();
        GameModule base = GameServices.module();
        try (var assets = ModAssetRoot.jar(temp, jar, ModInputLimits.production())) {
            var context = new ModContext("sonic-survivors", "s2", assets);
            ((GgfMod) loader.loadClass("survivors.SurvivorsMod").getConstructor().newInstance()).register(context);
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
                try { return Optional.of(name.startsWith("survivors.") ? loader.loadClass(name) : Class.forName(name)); }
                catch (ClassNotFoundException missing) { return Optional.empty(); }
            }
            @Override public Optional<String> ownerOf(Class<?> type) {
                return type.getName().startsWith("survivors.") ? Optional.of("sonic-survivors") : Optional.empty();
            }
        });
        return fixture;
    }

    @Test void menuFontPreservesEveryPixelWithFewerPresentationAllocations() throws Exception {
        launch(0, 0);
        ObjectServices services = (ObjectServices) call(stage(), "services");
        Class<?> draw = loader.loadClass("survivors.Draw");
        String chars = (String) field(draw, "CHARS").get(null);
        String pixels = (String) field(draw, "GLYPHS").get(null);
        Method text = draw.getDeclaredMethod("text", ObjectServices.class, String.class,
                int.class, int.class, int.class, int.class, float.class);
        text.setAccessible(true);
        int oldSpans = 0;
        for (int glyph = 0; glyph < chars.length(); glyph++) {
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 5; col++) {
                    int p = glyph * 35 + row * 5 + col;
                    if (pixels.charAt(p) == '1' && (col == 0 || pixels.charAt(p - 1) == '0')) oldSpans++;
                }
            }
        }
        for (int scale = 1; scale <= 3; scale++) {
            int size = scale;
            var camera = services.camera();
            var frame = com.openggf.graphics.SpritePresentation.prepare(services.graphicsManager(),
                    camera.getX(), camera.getY(), () -> {
                        try { text.invoke(null, services, chars, 9, 13, size, 0x60E8FF, 0.5f); }
                        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    }, ignored -> { throw new AssertionError("font must not submit ROM tiles"); });
            boolean[][] actual = new boolean[7 * size][chars.length() * 6 * size];
            for (var primitive : frame.primitives()) {
                var geometry = com.openggf.graphics.SpritePresentation.geometry(primitive);
                for (var rect : geometry.vertices()) {
                    assertEquals(0x8060E8FF, rect.argb());
                    for (int y = rect.y1() - 13; y < rect.y2() - 13; y++) {
                        for (int x = rect.x1() - 9; x < rect.x2() - 9; x++) {
                            assertFalse(actual[y][x], "overlap would change translucent text");
                            actual[y][x] = true;
                        }
                    }
                }
            }
            for (int y = 0; y < actual.length; y++) {
                for (int x = 0; x < actual[y].length; x++) {
                    int glyph = x / (6 * size), col = x / size % 6;
                    boolean expected = col < 5 && pixels.charAt(glyph * 35 + y / size * 5 + col) == '1';
                    assertEquals(expected, actual[y][x], "pixel at " + x + "," + y);
                }
            }
            assertTrue(frame.primitives().size() < oldSpans * 0.7,
                    "font must remove at least 30% of commands and presentation records");
            if (size == 1) System.out.println("Survivors font primitives: " + oldSpans
                    + " -> " + frame.primitives().size() + " per complete alphabet");
        }
    }

    @Test void cachedLevelUpDescriptionsCoverEveryUpgradeRank() throws Exception {
        launch(0, 0);
        Class<?> hud = loader.loadClass("survivors.Hud");
        Class<?> upgrades = loader.loadClass("survivors.Upgrades");
        Object[][] cards = (Object[][]) get(service("survivors.MenuArt"), "cards");
        Method describe = upgrades.getDeclaredMethod("describe", int.class, int.class);
        describe.setAccessible(true);
        for (int id = 0; id < cards.length; id++) {
            for (int rank = 1; rank < cards[id].length; rank++) {
                String[] expected = (String[]) describe.invoke(null, id, rank);
                assertEquals(expected[0], call(cards[id][rank], "first"));
                assertEquals(expected[1], call(cards[id][rank], "second"));
                assertEquals(rank == 1 ? "NEW!" : "LV " + (rank - 1) + ">" + rank,
                        call(cards[id][rank], "level"));
            }
        }
        Method clock = hud.getDeclaredMethod("clock", int.class);
        clock.setAccessible(true);
        for (int frames : new int[]{0, 1, 59, 60, 599, 600, 3599, 3600, 3601, 36000}) {
            int seconds = (frames + 59) / 60;
            assertEquals(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60),
                    clock.invoke(null, frames));
        }
    }

    // ---- Reflection helpers ----
    static Field field(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new AssertionError("No field " + name + " on " + type);
    }
    static Object get(Object target, String name) throws Exception { return field(target.getClass(), name).get(target); }
    static int getInt(Object target, String name) throws Exception { return field(target.getClass(), name).getInt(target); }
    static void set(Object target, String name, Object value) throws Exception { field(target.getClass(), name).set(target, value); }
    static Object call(Object target, String name, Object... args) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                    m.setAccessible(true);
                    return m.invoke(target, args);
                }
            }
        }
        throw new AssertionError("No method " + name + "/" + args.length + " on " + target.getClass());
    }
    static Object service(String className) throws Exception {
        return GameServices.module().getGameService(loader.loadClass(className));
    }
    static Object run() throws Exception { return service("survivors.RunState"); }
    static Object profile() throws Exception { return service("survivors.Profile"); }
    static Object arena() throws Exception { return service("survivors.Arena"); }
    static int arenaValue(String accessor) throws Exception { return (int) call(arena(), accessor); }

    static List<AbstractObjectInstance> objects(String simpleName) {
        var found = new ArrayList<AbstractObjectInstance>();
        for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (object.getClass().getName().equals("survivors." + simpleName)
                    && object instanceof AbstractObjectInstance instance && !instance.isDestroyed()) {
                found.add(instance);
            }
        }
        return found;
    }
    static Object stage() {
        var stages = objects("Stage");
        assertEquals(1, stages.size(), "one arena controller");
        return stages.get(0);
    }
    static int phase() throws Exception { return getInt(stage(), "phase"); }

    /** Traversal checks are about terrain and walls: tough badniks in the way would block Sonic. */
    static void clearEnemies() {
        for (var enemy : objects("Enemy")) enemy.setDestroyed(true);
    }

    /** Enter is separate from gameplay jump and is dispatched before host pause handling. */
    static void tapEnter(HeadlessTestFixture fixture) {
        var input = new com.openggf.control.InputHandler();
        var overlay = GameServices.module().getGameService(LevelInputOverlay.class);
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        assertTrue(overlay.handleInput(input), "a modal menu owns Enter before the host pause toggle");
        fixture.stepFrame(false, false, false, false, false);
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        fixture.stepFrame(false, false, false, false, false);
    }
    static void tapDown(HeadlessTestFixture fixture) {
        fixture.stepFrame(false, true, false, false, false);
        fixture.stepFrame(false, false, false, false, false);
    }

    /** From a fresh load: wait out camp's input delay, start the run, and skip the intro. */
    static void startRun(HeadlessTestFixture fixture) throws Exception {
        fixture.stepIdleFrames(30);
        assertEquals(CAMP, phase(), "a new arena opens in camp");
        tapEnter(fixture);
        assertEquals(INTRO, phase());
        // Later zones grant catch-up level-ups; skip their cards here.
        set(run(), "pendingLevels", 0);
        fixture.stepIdleFrames(160);
        // The Death Egg is a straight boss fight.
        assertEquals(arenaValue("stage") == 9 ? BOSS : FIGHT, phase());
    }

    // =========================================================================================

    @Test void campStartsARunAndTheArenaHoldsSonic() throws Exception {
        var fixture = launch(0, 0);
        var level = GameServices.level().getCurrentLevel();
        assertEquals(1, level.getObjects().size(), "the controller replaces the stock layout");
        assertTrue(level.getRings().isEmpty());
        fixture.stepIdleFrames(30);
        assertEquals(CAMP, phase());
        assertFalse((boolean) get(run(), "active"));
        assertTrue(fixture.sprite().isObjectControlled(), "camp holds Sonic still");
        tapEnter(fixture);
        assertTrue((boolean) get(run(), "active"));
        assertEquals(30, fixture.sprite().getRingCount(), "runs start with three ring tolls");
        assertEquals(1, getInt(profile(), "runs"));
        fixture.stepIdleFrames(160);
        assertEquals(FIGHT, phase());
        assertFalse(fixture.sprite().isObjectControlled());
        int left = arenaValue("left"), right = arenaValue("right");
        fixture.sprite().setInvulnerableFrames(100000);
        int maxX = Integer.MIN_VALUE, minX = Integer.MAX_VALUE, enemiesSeen = 0;
        for (int frame = 0; frame < 1200; frame++) {
            boolean goRight = frame / 300 % 2 == 0;
            fixture.stepFrame(false, false, !goRight, goRight, frame % 40 == 0);
            enemiesSeen += objects("Enemy").size();
            clearEnemies();
            maxX = Math.max(maxX, fixture.sprite().getCentreX());
            minX = Math.min(minX, fixture.sprite().getCentreX());
            assertFalse(fixture.sprite().getDead(), "frame " + frame);
            if (getInt(run(), "pendingLevels") > 0) set(run(), "pendingLevels", 0);
        }
        assertTrue(maxX <= right + 48 && maxX >= right - 64, "the right wall holds Sonic: maxX=" + maxX + " right=" + right);
        assertTrue(minX >= left && minX <= left + 48, "the left wall holds Sonic: minX=" + minX + " left=" + left);
        assertTrue(enemiesSeen >= 3, "waves spawn badniks: " + enemiesSeen);
        assertTrue(fixture.camera().getX() >= left && fixture.camera().getX() <= right - 320);
    }

    static Stream<Arguments> routeActs() {
        int[][] acts = {{0, 0}, {0, 1}, {1, 0}, {1, 1}, {2, 0}, {2, 1}, {3, 0}, {3, 1}, {4, 0}, {4, 1}, {5, 0}, {5, 1},
                {6, 0}, {6, 1}, {7, 0}, {7, 1}, {7, 2}, {9, 0}, {10, 0}};
        return Arrays.stream(acts).map(a -> Arguments.of(a[0], a[1]));
    }

    static Stream<Arguments> routeTeams() {
        return routeActs().flatMap(args -> Stream.of("sonic", "tails").map(main ->
                Arguments.of(args.get()[0], args.get()[1], main)));
    }

    @ParameterizedTest(name = "zone {0} act {1} as {2}")
    @MethodSource("routeTeams")
    void everyRouteActIsAWalledArenaSonicCanCross(int zone, int act, String main) throws Exception {
        var fixture = launch(zone, act, main, "");
        startRun(fixture);
        int left = arenaValue("left"), right = arenaValue("right");
        int floorTop = arenaValue("floorTop"), floorBottom = arenaValue("floorBottom");
        assertFalse(fixture.sprite().getAir(), "Sonic lands on the arena floor");
        int x = fixture.sprite().getCentreX();
        assertTrue(x > left && x < right, "starts inside: " + x);
        fixture.sprite().setInvulnerableFrames(100000);
        int maxX = x, minX = x;
        for (int frame = 0; frame < 900; frame++) {
            boolean goRight = frame < 450;
            fixture.stepFrame(false, false, !goRight, goRight, frame % 50 == 25);
            clearEnemies();
            int px = fixture.sprite().getCentreX(), py = fixture.sprite().getCentreY();
            maxX = Math.max(maxX, px);
            minX = Math.min(minX, px);
            assertFalse(fixture.sprite().getDead(), "death at frame " + frame + " x=" + px + " y=" + py);
            assertTrue(py < floorBottom + 64, "fell below the arena floor at x=" + px + " y=" + py);
            if (getInt(run(), "pendingLevels") > 0) set(run(), "pendingLevels", 0);
        }
        // On the Death Egg Silver Sonic is already fighting, and his rebound blocks the way.
        if (zone != 10) {
            assertTrue(maxX >= right - 120, "reaches the right side: maxX=" + maxX + " right=" + right);
            assertTrue(minX <= left + 120, "reaches the left side: minX=" + minX + " left=" + left);
        }
        assertTrue(maxX <= right + 48 && minX >= left, "stays walled in: " + minX + ".." + maxX);
        assertTrue(floorTop - 400 < fixture.sprite().getCentreY());
    }

    @Test void levelUpsOfferCardsAndEveryWeaponFires() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        call(run(), "gainXp", 1000);
        assertTrue(getInt(run(), "pendingLevels") > 1);
        fixture.stepIdleFrames(2);
        assertEquals(1, getInt(stage(), "overlay"), "the level-up cards open");
        assertTrue((boolean) get(run(), "paused"), "play pauses behind them");
        int pending = getInt(run(), "pendingLevels");
        tapDown(fixture);
        tapEnter(fixture);
        assertEquals(pending - 1, getInt(run(), "pendingLevels"));
        int[] levels = (int[]) get(run(), "levels");
        assertEquals(1, Arrays.stream(levels).sum(), "one upgrade taken");
        // Max every upgrade, clear the queue, and fight: nothing may throw, and things must die.
        for (int i = 0; i < levels.length; i++) levels[i] = i == 7 || i == 8 || i == 9 || i == 16 || i == 17 ? 3 : 5;
        set(run(), "pendingLevels", 0);
        fixture.stepIdleFrames(2);
        assertEquals(0, getInt(stage(), "overlay"));
        fixture.sprite().setInvulnerableFrames(100000);
        int pickupsSeen = 0;
        for (int frame = 0; frame < 1800; frame++) {
            boolean goRight = frame / 200 % 2 == 0;
            fixture.stepFrame(false, frame % 60 == 30, !goRight, goRight, frame % 20 == 0 || frame % 20 == 8);
            pickupsSeen = Math.max(pickupsSeen, objects("Pickup").size());
            if (getInt(run(), "pendingLevels") > 0) set(run(), "pendingLevels", 0);
        }
        assertTrue(getInt(run(), "kills") >= 10, "the arsenal clears badniks: " + getInt(run(), "kills"));
        assertTrue(pickupsSeen > 0, "defeated badniks drop rings");
    }

    @Test void bossClearAwardsTheEmeraldAndTheRouteCarriesTheRun() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        set(stage(), "stageFrames", 1);
        fixture.stepIdleFrames(2);
        assertEquals(BOSS, phase());
        var bosses = objects("Boss");
        assertEquals(1, bosses.size(), "the boss arrives when the clock runs out");
        fixture.stepIdleFrames(120);
        fixture.sprite().setInvulnerableFrames(100000);
        Object boss = bosses.get(0);
        for (int i = 0; i < 40 && (boolean) call(boss, "alive"); i++) {
            call(boss, "hurt", 1000);
            fixture.stepIdleFrames(12);
        }
        assertFalse((boolean) call(boss, "alive"));
        for (int i = 0; i < 700 && phase() != CLEAR; i++) fixture.stepIdleFrames(1);
        assertEquals(CLEAR, phase(), "the clear screen follows the boss");
        assertTrue((boolean) call(profile(), "hasEmerald", 0), "Emerald Hill's emerald is kept");
        assertEquals(1, getInt(profile(), "unlocked"), "Chemical Plant can now start a run");
        assertTrue((boolean) call(profile(), "extendedModesUnlocked"), "the first clear unlocks longer modes");
        int rings = fixture.sprite().getRingCount();
        // Choose act 2 of Chemical Plant.
        fixture.stepIdleFrames(30);
        tapDown(fixture);
        tapEnter(fixture);
        var levelManager = GameServices.level();
        assertEquals(1, levelManager.getRequestedZone(), "Chemical Plant was requested");
        assertEquals(1, levelManager.getRequestedAct());
        levelManager.loadZoneAndAct(1, 1);
        fixture.stepIdleFrames(2);
        assertEquals(INTRO, phase(), "the run carries on with no camp");
        assertEquals(1, getInt(run(), "stage"));
        assertEquals(1, getInt(run(), "stagesCleared"));
        assertEquals(rings, fixture.sprite().getRingCount(), "rings carry over");
    }

    @Test void stompsDamageReboundAndChainTheCombo() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        clearEnemies();
        int[] levels = (int[]) get(run(), "levels");
        levels[0] = 1; // Shockwave: a bounce weapon fires from each rebound.
        var player = fixture.sprite();
        player.setInvulnerableFrames(100000);
        // A tough Crawl hovering in mid-air is not a Crawl's habitat, so use an elite Buzzer.
        Class<?> enemyType = loader.loadClass("survivors.Enemy");
        Method spawnAt = enemyType.getDeclaredMethod("spawnAt", int.class, int.class, int.class, boolean.class);
        spawnAt.setAccessible(true);
        int ex = player.getCentreX() + 40, ey = player.getCentreY() - 64;
        var spawn = (ObjectSpawn) spawnAt.invoke(null, ex, ey, 0, true);
        var ctor = enemyType.getDeclaredConstructor(ObjectSpawn.class, int.class);
        ctor.setAccessible(true);
        var enemy = (AbstractObjectInstance) ctor.newInstance(spawn, 40);
        GameServices.level().getObjectManager().addDynamicObject(enemy);
        int before = (int) call(enemy, "hp");
        for (int bounce = 0; bounce < 3; bounce++) {
            com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player, enemy.getX());
            com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player, enemy.getY() - 28);
            player.setAir(true);
            player.setRolling(true);
            player.setAnimationId(2);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0x300);
            int hp = (int) call(enemy, "hp");
            for (int i = 0; i < 12 && (int) call(enemy, "hp") == hp; i++) fixture.stepIdleFrames(1);
            assertTrue((int) call(enemy, "hp") < hp, "bounce " + bounce + " damages the badnik");
            assertTrue(player.getYSpeed() < 0, "Sonic rebounds upward");
            fixture.stepIdleFrames(10);
        }
        assertEquals(3, getInt(run(), "combo"), "three airborne stomps chain a combo");
        assertTrue(before - (int) call(enemy, "hp") > 6, "the combo multiplier grows the damage");
        var stage = stage();
        assertTrue(Arrays.stream((int[]) get(stage, "eKind")).anyMatch(k -> k == 2), "the shockwave fired");
    }

    @Test void deathEggFinaleIsSilverSonicThenAFleeingEggman() throws Exception {
        var fixture = launch(10, 0);
        startRun(fixture);
        assertEquals(BOSS, phase(), "the Death Egg opens with its boss");
        fixture.sprite().setInvulnerableFrames(100000);
        Object silver = objects("Boss").get(0);
        for (int i = 0; i < 60 && (boolean) call(silver, "alive"); i++) {
            call(silver, "hurt", 1000);
            fixture.stepIdleFrames(12);
        }
        for (int i = 0; i < 300 && objects("Boss").stream().noneMatch(b -> runner(b)); i++) fixture.stepIdleFrames(1);
        Object eggman = objects("Boss").stream().filter(b -> runner(b)).findFirst().orElseThrow();
        assertEquals(BOSS, phase(), "the run is not won until Eggman is caught");
        var player = fixture.sprite();
        for (int hit = 0; hit < 3; hit++) {
            fixture.stepIdleFrames(70);
            var e = (AbstractObjectInstance) eggman;
            // Drop Sonic onto him in a ball.
            // Just right of him, so he flees left, and keeping pace.
            com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player, e.getX() + 12);
            com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player, e.getY() - 40);
            player.setAir(true);
            player.setRolling(true);
            player.setAnimationId(2);
            player.setXSpeed((short) ((int) call(eggman, "hp") == 1 ? -0x500 : -0x440));
            player.setYSpeed((short) 0x400);
            int before = (int) call(eggman, "hp");
            for (int i = 0; i < 20 && (int) call(eggman, "hp") == before; i++) fixture.stepIdleFrames(1);
            assertEquals(before - 1, (int) call(eggman, "hp"), "a stomp lands on Eggman");
        }
        for (int i = 0; i < 700 && phase() != 7; i++) fixture.stepIdleFrames(1);
        assertEquals(7, phase(), "catching Eggman wins the run");
        assertEquals(1, getInt(profile(), "wins"));
    }

    static boolean runner(Object boss) {
        try { return (boolean) call(boss, "runner"); } catch (Exception e) { throw new AssertionError(e); }
    }

    @Test void deathEndsTheRunBanksRingsAndReturnsToCamp() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        set(run(), "ringsCollected", 200);
        set(run(), "kills", 50);
        fixture.sprite().setRingCount(0);
        fixture.sprite().applyCrushDeath();
        for (int i = 0; i < 200 && phase() != DEAD; i++) fixture.stepIdleFrames(1);
        assertEquals(DEAD, phase());
        assertFalse((boolean) get(run(), "active"));
        assertEquals(100 + 10, getInt(profile(), "bank"), "half the rings collected plus a ring per five badniks");
        fixture.stepIdleFrames(80);
        tapEnter(fixture);
        var levelManager = GameServices.level();
        assertEquals(0, levelManager.getRequestedZone(), "TRY AGAIN reloads the start zone");
        assertEquals(0, levelManager.getRequestedAct());
        levelManager.loadZoneAndAct(0, 0);
        fixture.stepIdleFrames(2);
        assertEquals(CAMP, phase(), "a new run starts in camp");
        // Profile survives a reload from disk.
        var constructor = loader.loadClass("survivors.Profile").getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        Object reloaded = constructor.newInstance(
                com.openggf.game.save.SavePaths.root().resolve("sonic-survivors").resolve("profile.txt"));
        var ctor = reloaded.getClass().getDeclaredMethod("load");
        ctor.setAccessible(true);
        ctor.invoke(reloaded);
        assertEquals(110, getInt(reloaded, "bank"));
    }

    @Test void campShopSpendsTheBank() throws Exception {
        var fixture = launch(0, 0);
        fixture.stepIdleFrames(30);
        set(profile(), "bank", 1000);
        tapDown(fixture); // POWER UP
        tapEnter(fixture);
        int[] shop = (int[]) get(profile(), "shop");
        assertEquals(1, shop[0]);
        assertEquals(940, getInt(profile(), "bank"));
        tapDown(fixture); // RING START
        tapEnter(fixture);
        assertEquals(1, shop[1]);
        fixture.stepFrame(true, false, false, false, false);
        fixture.stepIdleFrames(1);
        fixture.stepFrame(true, false, false, false, false);
        fixture.stepIdleFrames(1);
        tapEnter(fixture);
        assertEquals(INTRO, phase());
        assertEquals(40, fixture.sprite().getRingCount(), "Ring Start adds ten");
    }

    @Test void allSevenEmeraldsKeepTheirBonusWithoutEnablingSuperSonic() throws Exception {
        var fixture = launch(0, 0);
        fixture.stepIdleFrames(30);
        set(profile(), "emeralds", 0x7F);
        assertFalse(GameServices.gameState().hasAllEmeralds());
        tapEnter(fixture);
        assertFalse(GameServices.gameState().hasAllEmeralds(), "relics do not unlock the native transformation");
        assertEquals(0x7F, getInt(run(), "relics"));
        assertEquals(1.25, (double) call(run(), "damageMultiplier"), 0.0001);
        assertNull(fixture.sprite().getSuperStateController(), "Survivors disables native and debug transformations");
        set(run(), "pendingLevels", 0);
        fixture.stepIdleFrames(160);
        assertEquals(FIGHT, phase());
        var level = GameServices.level().getLevelGamestate();
        assertFalse(level.isTimerPaused());
        assertEquals(0, level.getElapsedSeconds(), "the timer is held at zero instead");
    }

    @Test void floatingRingFormationsAppearAroundTheArena() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        fixture.sprite().setInvulnerableFrames(100000);
        int left = arenaValue("left"), right = arenaValue("right");
        for (int i = 0; i < 605; i++) {
            fixture.stepIdleFrames(1);
            clearEnemies();
            // Only the formation should be left: clear drops from anything defeated earlier.
            if (i < 560) for (var pickup : objects("Pickup")) pickup.setDestroyed(true);
            if (getInt(run(), "pendingLevels") > 0) set(run(), "pendingLevels", 0);
        }
        var rings = objects("Pickup");
        assertEquals(5, rings.size(), "ten seconds in, five rings float somewhere in the arena");
        for (var ring : rings) {
            assertTrue(ring.getX() > left && ring.getX() < right);
            assertTrue(Math.abs(ring.getX() - fixture.sprite().getCentreX()) >= 60, "away from Sonic");
        }
        int y = rings.get(0).getY();
        fixture.stepIdleFrames(60);
        assertEquals(y, rings.get(0).getY(), "they hang in the air until collected");
    }

    @Test void ringsAreHealthAndTheLastHitIsLethal() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        Class<?> guard = loader.loadClass("survivors.Guard");
        Method takeHit = guard.getDeclaredMethod("takeHit", ObjectServices.class,
                com.openggf.sprites.playable.AbstractPlayableSprite.class);
        takeHit.setAccessible(true);
        ObjectServices services = (ObjectServices) call(stage(), "services");
        var player = fixture.sprite();
        assertTrue((boolean) takeHit.invoke(null, services, player));
        assertEquals(20, player.getRingCount(), "a hit costs the ten-ring toll");
        assertTrue(player.getInvulnerableFrames() > 0);
        player.setInvulnerableFrames(0);
        player.setRingCount(0);
        assertFalse((boolean) takeHit.invoke(null, services, player), "no rings, no revive: lethal");
        set(run(), "revives", 1);
        assertTrue((boolean) takeHit.invoke(null, services, player), "a revive saves the lethal hit");
        assertEquals(20, player.getRingCount());
        assertEquals(0, getInt(run(), "revives"));
    }

    @Test void fightRewindsAndReplaysIdentically() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        int[] levels = (int[]) get(run(), "levels");
        levels[0] = 3; levels[1] = 3; levels[4] = 3; levels[5] = 2;
        fixture.sprite().setInvulnerableFrames(100000);
        for (int frame = 0; frame < 400; frame++) fixture.stepFrame(false, false, false, frame % 200 < 100, frame % 30 == 0);
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        for (int frame = 0; frame < 300; frame++) fixture.stepFrame(false, false, frame % 100 < 50, false, frame % 25 == 0);
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        int kills = getInt(run(), "kills"), enemies = objects("Enemy").size();
        registry.restore(before);
        for (int frame = 0; frame < 300; frame++) fixture.stepFrame(false, false, frame % 100 < 50, false, frame % 25 == 0);
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
        assertEquals(kills, getInt(run(), "kills"));
        assertEquals(enemies, objects("Enemy").size());
    }

    @Test void jumpCannotSelectCardsAndOpeningFrameFreezesImmediately() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        call(run(), "gainXp", 10);
        fixture.stepFrame(false, false, false, false, true);
        int pending = getInt(run(), "pendingLevels");
        assertTrue((boolean) get(run(), "paused"));
        assertTrue(fixture.sprite().isObjectControlled());
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        int remaining = getInt(stage(), "stageFrames");
        for (int i = 0; i < 15; i++) fixture.stepFrame(false, false, false, true, i % 2 == 0);
        assertEquals(pending, getInt(run(), "pendingLevels"), "jump must never confirm a card");
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
        assertEquals(remaining, getInt(stage(), "stageFrames"));
        tapEnter(fixture);
        assertEquals(pending - 1, getInt(run(), "pendingLevels"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"sonic", "tails"})
    void bothLeadersAreSoloAndCannotEscapeTheAquaticRuinCeiling(String main) throws Exception {
        var patch = (GamePatch) loader.loadClass("survivors.SurvivorsMod$Patch").getConstructor().newInstance();
        assertTrue(patch.activatesFor(new GameplayLaunchRequest("s2", main, List.of("tails"))));
        var fixture = launch(2, 0, main, "tails");
        startRun(fixture);
        assertTrue(GameServices.sprites().getSidekicks().isEmpty(), "configured followers must not spawn");
        assertFalse(GameServices.module().supportsSidekick());
        assertTrue(GameServices.module().isSidekickSuppressedForZone(2));
        assertEquals(main, fixture.sprite().getCode());
        var player = fixture.sprite();
        for (int i = 0; i < 40; i++) {
            com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player, -8);
            player.setYSpeed((short) -0x900);
            player.setAir(false); // Even a collision with terrain above the arena cannot strand the player there.
            fixture.stepIdleFrames(1);
            assertTrue(player.getCentreY() >= GameServices.camera().getMinY() + 24);
            assertTrue(player.getAir());
            assertTrue(player.getYSpeed() >= 0);
        }
    }

    @Test void lostRingsIgnoreBothMagnetsAndDoNotFarmExperienceAcrossRewind() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        var player = fixture.sprite();
        call(stage(), "spillRings", player.getCentreX() + 100, player.getCentreY(), 1);
        var ring = objects("Pickup").stream().filter(o -> {
            try { return (boolean) call(o, "lostRing"); } catch (Exception e) { throw new RuntimeException(e); }
        }).findFirst().orElseThrow();
        var registry = fixture.runtime().getRewindRegistry();
        var snapshot = registry.capture();
        ((int[]) get(run(), "levels"))[11] = 5; // Magnet
        call(stage(), "magnetSweep");
        call(ring, "homeIn");
        fixture.stepIdleFrames(30);
        assertFalse((boolean) get(ring, "homing"));
        registry.restore(snapshot);
        ring = objects("Pickup").stream().filter(o -> {
            try { return (boolean) call(o, "lostRing"); } catch (Exception e) { throw new RuntimeException(e); }
        }).findFirst().orElseThrow();
        int xp = getInt(run(), "xp"), earned = getInt(run(), "ringsCollected"), rings = player.getRingCount();
        call(ring, "collect", player);
        assertEquals(rings + 1, player.getRingCount());
        assertEquals(xp, getInt(run(), "xp"));
        assertEquals(earned, getInt(run(), "ringsCollected"));
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void hostEnterConfirmsCampWithoutPausingThenResumesNormalPauseControl(boolean remappedStart) throws Exception {
        var fixture = launch(0, 0);
        fixture.stepIdleFrames(30);
        var input = new com.openggf.control.InputHandler();
        var loop = new com.openggf.GameLoop(input);
        loop.setGameMode(GameMode.LEVEL);
        var config = SonicConfigurationService.getInstance();
        int oldPause = config.getInt(SonicConfiguration.PAUSE_KEY), oldStart = config.getInt(SonicConfiguration.START);
        try {
            SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.PAUSE_KEY,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            if (remappedStart) SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.START,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_PRESS);
            loop.step();
            assertEquals(INTRO, phase());
            assertFalse(GameServices.gameState().isGamePaused(), "modal Enter must not enter ROM Pause_Loop");
            assertFalse(loop.isUserPaused());
            input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
            loop.step();
            input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_PRESS);
            loop.step();
            assertTrue(loop.isUserPaused(), "the host still pauses normal play");
            loop.toggleUserPause();
        } finally {
            config.setConfigValue(SonicConfiguration.PAUSE_KEY, oldPause);
            config.setConfigValue(SonicConfiguration.START, oldStart);
        }
    }

    private Object reloadProfile() throws Exception {
        var constructor = loader.loadClass("survivors.Profile").getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        return call(constructor.newInstance(com.openggf.game.save.SavePaths.root()
                .resolve("sonic-survivors/profile.txt")), "load");
    }

    @Test void campModesUnlockAfterClearPersistAndCycleBackToStandard() throws Exception {
        var fixture = launch(0, 0);
        fixture.stepIdleFrames(30);
        for (int i = 0; i < 8; i++) tapDown(fixture);
        tapEnter(fixture);
        assertEquals(0, getInt(profile(), "mode"));
        assertFalse((boolean) call(profile(), "extendedModesUnlocked"));
        // Existing profiles with a completed boss must unlock without replaying that boss.
        set(profile(), "unlocked", 1);
        tapEnter(fixture);
        assertEquals(1, getInt(reloadProfile(), "mode"));
        tapEnter(fixture);
        assertEquals(2, getInt(reloadProfile(), "mode"));
        tapEnter(fixture);
        assertEquals(0, getInt(reloadProfile(), "mode"));
    }

    @Test void oldAndInvalidProfilesKeepTheTwoMinuteDefaultAndCannotBypassTheUnlock() throws Exception {
        launch(0, 0);
        Path file = com.openggf.game.save.SavePaths.root().resolve("sonic-survivors/profile.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "unlocked=1\nbank=123\n");
        assertEquals(0, call(reloadProfile(), "selectedMode"));
        Files.writeString(file, "unlocked=1\nmode=99\n");
        assertEquals(0, call(reloadProfile(), "selectedMode"));
        Files.writeString(file, "mode=2\n");
        assertEquals(0, call(reloadProfile(), "selectedMode"));
        Files.writeString(file, "emeralds=1\nmode=2\n");
        assertEquals(2, call(reloadProfile(), "selectedMode"));
    }

    @Test void fiveMinuteClockPassesTwoMinutesThenSpawnsBossAndReplaysItsBoundary() throws Exception {
        var fixture = launch(0, 0);
        set(profile(), "unlocked", 1);
        set(profile(), "mode", 1);
        startRun(fixture);
        assertEquals(300, call(stage(), "survivalSeconds"));
        assertTrue(getInt(stage(), "stageFrames") > 17900);
        // Changing a saved preference cannot change a run in progress.
        set(profile(), "mode", 2);
        set(stage(), "fightFrames", 7199);
        set(stage(), "stageFrames", 10801);
        fixture.stepIdleFrames(2);
        assertEquals(FIGHT, phase());
        assertTrue(objects("Boss").isEmpty());
        set(stage(), "stageFrames", 1);
        var registry = fixture.runtime().getRewindRegistry();
        var snapshot = registry.capture();
        fixture.stepIdleFrames(1);
        assertEquals(BOSS, phase());
        assertEquals(1, objects("Boss").size());
        registry.restore(snapshot);
        assertEquals(1, getInt(run(), "mode"));
        fixture.stepIdleFrames(1);
        assertEquals(BOSS, phase());
        assertEquals(1, objects("Boss").size());
    }

    @Test void endlessHasNoBossDeadlinePausesRewindsAndBanksOnRetirement() throws Exception {
        var fixture = launch(0, 0);
        set(profile(), "unlocked", 1);
        set(profile(), "mode", 2);
        startRun(fixture);
        assertTrue((boolean) call(stage(), "endless"));
        set(stage(), "fightFrames", 18000);
        fixture.stepIdleFrames(2);
        assertEquals(FIGHT, phase());
        assertTrue(objects("Boss").isEmpty());
        assertEquals(0, getInt(stage(), "stageFrames"));
        var registry = fixture.runtime().getRewindRegistry();
        var snapshot = registry.capture();
        int elapsed = getInt(stage(), "fightFrames");
        fixture.stepIdleFrames(3);
        registry.restore(snapshot);
        assertEquals(elapsed, getInt(stage(), "fightFrames"));
        assertEquals(2, getInt(run(), "mode"));
        call(run(), "gainXp", 5);
        fixture.stepIdleFrames(10);
        assertEquals(elapsed, getInt(stage(), "fightFrames"), "the card menu freezes elapsed time");
        set(run(), "ringsCollected", 100);
        fixture.sprite().setRingCount(40);
        call(stage(), "retire", true);
        assertEquals(90, getInt(profile(), "bank"));
        assertFalse((boolean) get(run(), "active"));
        assertTrue((boolean) get(stage(), "exiting"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2})
    void deathEggKeepsItsBossFinaleInLongAndEndlessModes(int mode) throws Exception {
        var fixture = launch(10, 0);
        set(profile(), "unlocked", 9);
        set(profile(), "mode", mode);
        startRun(fixture);
        assertEquals(BOSS, phase());
        assertFalse((boolean) call(stage(), "endless"));
        assertEquals(1, objects("Boss").size());
    }

    @Test void ringShowersBatchSoundWithoutLosingRewardsAndRestoreTheirCooldown() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        var player = fixture.sprite();
        call(stage(), "dropRings", player.getCentreX(), player.getCentreY(), 30);
        var rings = objects("Pickup");
        assertEquals(3, rings.size());
        var audio = com.openggf.audio.AudioManager.getInstance();
        var requests = new ArrayList<Integer>();
        audio.setRequestObserver((kind, id) -> requests.add(id));
        try {
            int before = player.getRingCount();
            for (var ring : rings) call(ring, "collect", player);
            assertEquals(before + 30, player.getRingCount());
            assertEquals(30, getInt(run(), "ringsCollected"));
            assertEquals(3, getInt(run(), "level"), "30 XP pays the 5, 8 and 12 XP level costs");
            assertEquals(5, getInt(run(), "xp"));
            assertEquals(3, getInt(run(), "pendingLevels"));
            assertEquals(1, requests.size(), "a whole shower in one frame chimes only once");
            Object snapshot = call(run(), "capture");
            set(run(), "frames", getInt(run(), "frames") + 11);
            assertFalse((boolean) call(run(), "ringSound", 10));
            set(run(), "frames", getInt(run(), "frames") + 1);
            assertTrue((boolean) call(run(), "ringSound", 1));
            call(run(), "restore", snapshot);
            assertFalse((boolean) call(run(), "ringSound", 1), "rewind restores the cooldown");
            set(run(), "frames", getInt(run(), "frames") + 30);
            assertTrue((boolean) call(run(), "ringSound", 1), "an isolated pickup remains audible");
            call(run(), "begin", 0, profile(), 123L);
            assertTrue((boolean) call(run(), "ringSound", 1), "a new run resets audio grouping");
        } finally {
            audio.setRequestObserver(null);
        }
    }


    private AbstractObjectInstance spawnPickup(int kind, int value, int x, int y) throws Exception {
        Class<?> type = loader.loadClass("survivors.Pickup");
        Method spawnAt = type.getDeclaredMethod("spawnAt", int.class, int.class, int.class);
        spawnAt.setAccessible(true);
        ObjectSpawn spawn = (ObjectSpawn) spawnAt.invoke(null, x, y, kind);
        var constructor = type.getConstructor(ObjectSpawn.class, int.class, int.class, int.class);
        var pickup = (AbstractObjectInstance) call(stage(), "spawnFreeChild",
                (java.util.function.Supplier<AbstractObjectInstance>) () -> {
                    try { return (AbstractObjectInstance) constructor.newInstance(spawn, 0, 0, value); }
                    catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
                });
        set(pickup, "resting", true);
        return pickup;
    }

    private AbstractObjectInstance matureReward(int value, int x, int y) throws Exception {
        var ring = spawnPickup(0, value, x, y);
        set(ring, "age", 45);
        return ring;
    }

    private void mergeRewards() throws Exception {
        call(service("survivors.RingClusters"), "merge", call(stage(), "services"));
    }

    @Test void nearbyRewardsPromoteThroughFiveTwentyFiveAndOneHundredTwentyFive() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        int x = fixture.sprite().getCentreX() + 200, y = fixture.sprite().getCentreY();
        // Five spatially separate clusters become five cyan rings, then one purple ring.
        for (int group = 0; group < 5; group++) {
            for (int n = 0; n < 5; n++) matureReward(1, x + group * 100 + n, y);
        }
        mergeRewards();
        assertEquals(5, objects("Pickup").size());
        for (var ring : objects("Pickup")) {
            assertEquals(5, call(ring, "value"));
            assertEquals(1, call(ring, "ringTier"));
            set(ring, "x", x);
        }
        mergeRewards();
        assertEquals(1, objects("Pickup").size());
        assertEquals(25, call(objects("Pickup").getFirst(), "value"));
        assertEquals(2, call(objects("Pickup").getFirst(), "ringTier"));
        for (int i = 0; i < 4; i++) matureReward(25, x, y);
        mergeRewards();
        assertEquals(1, objects("Pickup").size());
        var red = objects("Pickup").getFirst();
        assertEquals(125, call(red, "value"));
        assertEquals(3, call(red, "ringTier"));
        matureReward(125, x, y);
        mergeRewards();
        assertEquals(1, objects("Pickup").size(), "top-tier piles continue consolidating");
        int rings = fixture.sprite().getRingCount();
        call(red, "collect", fixture.sprite());
        assertEquals(rings + 250, fixture.sprite().getRingCount());
        assertEquals(250, getInt(run(), "ringsCollected"));
        int earnedXp = getInt(run(), "xp");
        Class<?> state = loader.loadClass("survivors.RunState");
        Method cost = state.getDeclaredMethod("xpToNext", int.class);
        cost.setAccessible(true);
        for (int level = 0; level < getInt(run(), "level"); level++) earnedXp += (int) cost.invoke(null, level);
        assertEquals(250, earnedXp, "consolidation preserves the entire base XP reward");
    }

    @Test void consolidationRespectsDistanceEligibilityThresholdsAndOverflow() throws Exception {
        launch(0, 0);
        // Straddle a cell boundary, including negative coordinates.
        var a = matureReward(2, 0, 0);
        set(a, "x", -1); set(a, "y", -1); // Spawn records normalize to unsigned ROM words.
        var b = matureReward(2, 1, 1);
        mergeRewards();
        assertFalse(a.isDestroyed());
        assertFalse(b.isDestroyed());
        var fresh = matureReward(1, 0, 0); set(fresh, "age", 44);
        var lost = matureReward(1, 0, 0); set(lost, "lostRing", true);
        var homing = matureReward(1, 0, 0); call(homing, "homeIn");
        var collected = matureReward(1, 0, 0); set(collected, "collected", 0);
        var far = matureReward(1, 100, 0);
        var monitor = spawnPickup(1, 5, 0, 0); set(monitor, "age", 60);
        mergeRewards();
        assertEquals(2, call(a, "value"));
        assertEquals(2, call(b, "value"));
        var c = matureReward(1, 0, 0);
        mergeRewards();
        assertEquals(5, call(a, "value"));
        assertTrue(b.isDestroyed());
        assertTrue(c.isDestroyed());
        for (var untouched : List.of(fresh, lost, homing, collected, far, monitor)) assertFalse(untouched.isDestroyed());
        var huge = matureReward(Integer.MAX_VALUE, 1000, 1000);
        var extra = matureReward(125, 1000, 1000);
        mergeRewards();
        assertFalse(huge.isDestroyed());
        assertFalse(extra.isDestroyed());
        assertEquals(Integer.MAX_VALUE, call(huge, "value"));
    }

    @Test void denseRewardDropsUseGrowingScratchAndRewindWithoutLosingValue() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        int x = fixture.sprite().getCentreX() + 180, y = fixture.sprite().getCentreY();
        for (int i = 0; i < 300; i++) call(stage(), "dropRings", x, y, 1);
        assertEquals(300, objects("Pickup").size());
        for (var ring : objects("Pickup")) { set(ring, "age", 45); set(ring, "resting", true); }
        var registry = fixture.runtime().getRewindRegistry();
        var snapshot = registry.capture();
        mergeRewards();
        assertEquals(1, objects("Pickup").size());
        assertEquals(300, call(objects("Pickup").getFirst(), "value"));
        registry.restore(snapshot);
        assertEquals(300, objects("Pickup").size());
        mergeRewards();
        assertEquals(1, objects("Pickup").size());
        assertEquals(300, call(objects("Pickup").getFirst(), "value"));
    }

    @Test void clusterMergePausesAndReplaysWithExactValuesAndRemainingLifetime() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        int x = fixture.sprite().getCentreX() + 180, y = fixture.sprite().getCentreY();
        for (int i = 0; i < 5; i++) {
            var ring = matureReward(1, x + i, y);
            set(ring, "rewardAge", 3500 + i);
        }
        set(stage(), "ringMergeFrames", 14);
        call(run(), "gainXp", 5);
        fixture.stepIdleFrames(20);
        assertEquals(5, objects("Pickup").size(), "menus pause consolidation");
        assertEquals(14, getInt(stage(), "ringMergeFrames"));
        var registry = fixture.runtime().getRewindRegistry();
        var snapshot = registry.capture();
        tapEnter(fixture);
        assertEquals(1, objects("Pickup").size());
        int age = getInt(objects("Pickup").getFirst(), "rewardAge");
        assertTrue(age >= 3500 && age < 3510, "merging does not refresh old rewards");
        assertEquals(5, call(objects("Pickup").getFirst(), "value"));
        registry.restore(snapshot);
        assertEquals(5, objects("Pickup").size());
        tapEnter(fixture);
        assertEquals(1, objects("Pickup").size());
        assertEquals(age, getInt(objects("Pickup").getFirst(), "rewardAge"));
        assertEquals(5, call(objects("Pickup").getFirst(), "value"));
    }

    @Test void rewardExpiryPausesRewindsAndNeverDeletesTheEmerald() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        for (int kind = 0; kind < 3; kind++) {
            var pickup = spawnPickup(kind, kind == 1 ? 5 : 1,
                    fixture.sprite().getCentreX() + 160, fixture.sprite().getCentreY());
            set(pickup, "rewardAge", 3599);
        }
        call(run(), "gainXp", 5);
        fixture.stepIdleFrames(10);
        assertEquals(3, objects("Pickup").size(), "the card menu freezes reward expiry");
        var registry = fixture.runtime().getRewindRegistry();
        var snapshot = registry.capture();
        tapEnter(fixture);
        assertEquals(1, objects("Pickup").size());
        assertEquals(2, call(objects("Pickup").get(0), "kind"), "emeralds never expire");
        registry.restore(snapshot);
        assertEquals(3, objects("Pickup").size(), "rewind recreates the expired rewards");
        tapEnter(fixture);
        assertEquals(1, objects("Pickup").size(), "forward replay expires them at the same boundary");
    }

    @Test void freshMergedRewardsRefreshTheirLifetimeAndLostRingsStillExpireInFiveSeconds() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        var reward = spawnPickup(0, 2, fixture.sprite().getCentreX() + 160, fixture.sprite().getCentreY());
        set(reward, "rewardAge", 3599);
        call(reward, "addValue", 10);
        var lost = spawnPickup(0, 1, fixture.sprite().getCentreX() + 160, fixture.sprite().getCentreY());
        set(lost, "lostRing", true);
        set(lost, "age", 299);
        fixture.stepIdleFrames(1);
        assertFalse(reward.isDestroyed());
        assertEquals(12, call(reward, "value"));
        assertEquals(1, getInt(reward, "rewardAge"));
        assertTrue(lost.isDestroyed());
    }

    @Test void hordesExceedBothOldCapsAndRestoreWithoutConsumingNativeSlots() throws Exception {
        var fixture = launch(2, 0);
        startRun(fixture);
        int whisp = field(loader.loadClass("survivors.Species"), "WHISP").getInt(null);
        var manager = GameServices.level().getObjectManager();
        int freeSlot = manager.firstFreeDynamicSlot();
        for (int i = 0; i < 100; i++) call(stage(), "spawnEnemy", new int[0], new int[]{whisp}, 2, false);
        assertEquals(300, objects("Enemy").size());
        assertEquals(freeSlot, manager.firstFreeDynamicSlot(), "hordes cannot starve native power-up visuals");
        set(stage(), "eliteTimer", 1);
        call(stage(), "spawnWaves");
        assertEquals("ELITE INCOMING!", get(stage(), "banner"));
        int count = objects("Enemy").size();
        var registry = fixture.runtime().getRewindRegistry();
        var snapshot = registry.capture();
        var first = objects("Enemy").get(0);
        int x = first.getX();
        fixture.sprite().setInvulnerableFrames(100000);
        fixture.stepIdleFrames(20);
        assertNotEquals(x, first.getX(), "slotless enemies participate in gameplay updates");
        registry.restore(snapshot);
        assertEquals(count, objects("Enemy").size());
        assertTrue(objects("Enemy").stream().allMatch(e -> e.getSlotIndex() == -1));
        assertEquals(freeSlot, manager.firstFreeDynamicSlot());
    }

    @Test void encountersWarnSurgeRecoverAndKeepEscalating() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        set(stage(), "fightFrames", 900);
        set(stage(), "spawnTimer", 0);
        call(stage(), "spawnWaves");
        assertEquals("SURGE IN 3...", get(stage(), "banner"));
        int assault = objects("Enemy").size();
        set(stage(), "fightFrames", 1080);
        set(stage(), "spawnTimer", 0);
        call(stage(), "spawnWaves");
        assertTrue(objects("Enemy").size() - assault >= 3);
        int surge = objects("Enemy").size();
        set(stage(), "fightFrames", 1500);
        set(stage(), "spawnTimer", 0);
        call(stage(), "spawnWaves");
        assertEquals(surge, objects("Enemy").size(), "regroup gives a real reinforcement break");
        assertEquals("REGROUP", call(stage(), "encounterName"));
        set(stage(), "fightFrames", 36000);
        set(stage(), "spawnTimer", 0);
        call(stage(), "spawnWaves");
        assertTrue(objects("Enemy").size() - surge >= 21, "endless pressure has no old plateau");
        assertTrue((int) call(objects("Enemy").get(objects("Enemy").size() - 1), "maxHp") >
                (int) call(objects("Enemy").get(0), "maxHp"));
    }

    @Test void feverIsEarnedCannotRefreshAndRewindsWithItsRecovery() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        var player = fixture.sprite();
        for (int i = 0; i < 9; i++) call(stage(), "onBounce", player.getCentreX(), player.getCentreY());
        assertEquals(0, player.getInvincibleFrames());
        var registry = fixture.runtime().getRewindRegistry();
        var snapshot = registry.capture();
        call(stage(), "onBounce", player.getCentreX(), player.getCentreY());
        assertEquals(480, player.getInvincibleFrames());
        assertEquals(480, getInt(stage(), "feverFrames"));
        fixture.stepIdleFrames(30);
        int left = player.getInvincibleFrames();
        for (int i = 0; i < 30; i++) call(stage(), "onBounce", player.getCentreX(), player.getCentreY());
        assertEquals(left, player.getInvincibleFrames(), "bounces during Fever cannot refresh it");
        assertEquals(0, getInt(stage(), "feverCharge"));
        registry.restore(snapshot);
        assertEquals(9, getInt(stage(), "feverCharge"));
        assertEquals(0, getInt(stage(), "feverFrames"));
        call(stage(), "onBounce", player.getCentreX(), player.getCentreY());
        set(stage(), "feverFrames", 1);
        player.setInvincibleFrames(1);
        fixture.stepIdleFrames(1);
        assertEquals(900, getInt(stage(), "feverCooldown"));
        call(stage(), "onBounce", player.getCentreX(), player.getCentreY());
        assertEquals(0, getInt(stage(), "feverCharge"));
        call(run(), "gainXp", 10);
        fixture.stepIdleFrames(10);
        int recovery = getInt(stage(), "feverCooldown");
        fixture.stepIdleFrames(30);
        assertEquals(recovery, getInt(stage(), "feverCooldown"), "card menus preserve recovery time");
    }

    @Test void feverProtectionDoesNotExpireBehindLevelUpCards() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        var player = fixture.sprite();
        for (int i = 0; i < 10; i++) call(stage(), "onBounce", player.getCentreX(), player.getCentreY());
        call(run(), "gainXp", 5);
        fixture.stepIdleFrames(2);
        int protection = player.getInvincibleFrames();
        int remaining = getInt(stage(), "feverFrames");
        fixture.stepIdleFrames(120);
        assertEquals(protection, player.getInvincibleFrames());
        assertEquals(remaining, getInt(stage(), "feverFrames"));
        tapEnter(fixture);
        fixture.stepIdleFrames(5);
        assertTrue(player.getInvincibleFrames() > protection - 10);
    }

    @Test void weaponProjectilesGrowAndRewindWithoutDroppingAttacks() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        for (int i = 0; i < 200; i++) {
            call(stage(), "projectile", 1, 100, 100, 0, 0, 60, 2, 1);
        }
        assertEquals(200, Arrays.stream((int[]) get(stage(), "pKind")).filter(k -> k != 0).count());
        var expanded = registry.capture();
        registry.restore(before);
        assertEquals(48, ((int[]) get(stage(), "pKind")).length);
        registry.restore(expanded);
        assertEquals(200, Arrays.stream((int[]) get(stage(), "pKind")).filter(k -> k != 0).count());
        fixture.stepIdleFrames(1);
        assertEquals(59, ((int[]) get(stage(), "pLife"))[199]);
    }

    @Test void largeRingBanksAndArmorRetainMeaningfulRisk() throws Exception {
        var fixture = launch(0, 0);
        startRun(fixture);
        assertEquals(10, call(run(), "toll", 30));
        assertEquals(50, call(run(), "toll", 600));
        int[] levels = (int[]) get(run(), "levels");
        levels[12] = 5;
        assertEquals(6, call(run(), "toll", 30));
        assertEquals(30, call(run(), "toll", 600));
        set(run(), "relics", 1 << 4);
        assertEquals(5, call(run(), "toll", 30));
        assertEquals(23, call(run(), "toll", 600));
    }

    @Test void endlessRunsPastFiveMinutesWithoutFillingTheArenaWithOldPickups() throws Exception {
        var fixture = launch(0, 0);
        set(profile(), "unlocked", 1);
        set(profile(), "mode", 2);
        startRun(fixture);
        fixture.sprite().setInvulnerableFrames(100000);
        fixture.stepIdleFrames(20000);
        assertEquals(FIGHT, phase());
        assertTrue(getInt(stage(), "fightFrames") > 18000);
        assertTrue(objects("Boss").isEmpty());
        assertTrue(objects("Enemy").size() > 100, "endless keeps admitting enemies beyond the old caps");
        assertTrue(objects("Pickup").size() <= 30, "old formations make room for continuing waves");
    }

}
