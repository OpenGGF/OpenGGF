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
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
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
            stepCourse(fixture, 1);
            int x = fixture.sprite().getCentreX();
            if (previous - x > 4000) rebases++;
            assertFalse(fixture.sprite().getDead(), "death at frame " + i + ", x=" + x + ", y=" + fixture.sprite().getCentreY() + ", cameraMaxY=" + fixture.camera().getMaxY());
            previous = x;
        }
        assertTrue(rebases >= 4, "must traverse several windows; got " + rebases + ", x=" + previous);
        // A full registry snapshot proves object origin, terrain and player restore together.
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        for (int i = 0; i < 800; i++) stepCourse(fixture, 1);
        int expectedX = fixture.sprite().getCentreX();
        int expectedY = fixture.sprite().getCentreY();
        int expectedFraction = fixture.sprite().getXSubpixelRaw();
        short expectedSpeed = fixture.sprite().getGSpeed();
        byte[] expectedMap = GameServices.level().getCurrentLevel().getMap().getData().clone();
        var expectedObjects = registry.capture().entries().get("object-manager");
        registry.restore(before);
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", before.entries().get("object-manager"),
                registry.capture().entries().get("object-manager")), "immediate object restore");
        for (int i = 0; i < 800; i++) stepCourse(fixture, 1);
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
            stepCourse(fixture, -1);
            int x = fixture.sprite().getCentreX();
            if (x - previous > 4000) backwardsRebases++;
            assertFalse(fixture.sprite().getDead());
            previous = x;
        }
        assertTrue(backwardsRebases > 0, "backtracking crosses the recycle boundary");
    }

    @Test void freshReloadResetsTheCourseAndOtherActsRemainStock() throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        byte[] initial = GameServices.level().getCurrentLevel().getMap().getData().clone();
        fixture.stepIdleFrames(2);
        assertTrue(GameServices.level().getLevelGamestate().isTimerPaused());
        GameServices.level().loadZoneAndAct(0, 1);
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

    private long originPixels() throws Exception {
        Object controller = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o.getClass().getName().equals("infinite.CourseController")).findFirst().orElseThrow();
        return (long) controller.getClass().getMethod("originPixels").invoke(controller);
    }

    /** A deterministic player policy; terrain observations choose inputs, never set physics state. */
    private void stepCourse(HeadlessTestFixture fixture, int direction) throws Exception {
        long x = originPixels() + fixture.sprite().getCentreX();
        boolean approachingGap = false;
        for (int ahead = 0; ahead <= 48; ahead += 4) {
            if (floorAt(x + direction * ahead) < 0) approachingGap = true;
        }
        boolean jump = fixture.sprite().getAir() || approachingGap;
        fixture.stepFrame(false, false, direction < 0, direction > 0, jump);
    }

    private int gapWidth(long section) throws Exception {
        return (int) terrain().getClass().getMethod("gapWidth", long.class).invoke(terrain(), section);
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

    @Test void gapsAreBoundedSeededAndHaveLevelRunways() throws Exception {
        launch(WidescreenAspect.NATIVE_4_3);
        Set<Integer> widths = new HashSet<>();
        int gaps = 0;
        for (long section = 0; section < 1000; section++) {
            int width = gapWidth(section);
            assertEquals(width, gapWidth(section));
            if (section < 3) assertEquals(0, width, "safe opening runway");
            if (width == 0) continue;
            gaps++;
            widths.add(width);
            assertNull(encounter(section), "jump sections have no badnik ambush");
            long start = section * 512 + (512 - width) / 2;
            int bank = floorAt(section * 512);
            for (int x = 0; x < 512; x++) {
                long world = section * 512 + x;
                assertEquals(world >= start && world < start + width ? -1 : bank, floorAt(world));
            }
        }
        assertEquals(Set.of(64, 96, 128), widths);
        assertTrue(gaps > 150 && gaps < 350, "occasional jumps with recovery sections: " + gaps);
    }

    @ParameterizedTest @ValueSource(ints = {64, 96, 128})
    void realPhysicsCanClearEachGapInBothDirections(int width) throws Exception {
        var fixture = launch(WidescreenAspect.NATIVE_4_3);
        fixture.stepIdleFrames(2);
        long section = -1;
        for (long candidate = 3; candidate < 1000; candidate++) {
            if (gapWidth(candidate) == width) { section = candidate; break; }
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
        var scan = terrain().getClass().getMethod("floor", Level.class, int.class, int.class);
        for (int x = 0; x < 16384; x += 3) {
            assertEquals((int) scan.invoke(null, level, x / 256, x % 256), floorAt(x));
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
