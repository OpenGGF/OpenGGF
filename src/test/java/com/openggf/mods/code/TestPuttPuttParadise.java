package com.openggf.mods.code;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.session.SessionManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

/** Compiles and packages the external creator source, then loads the actual S2 ROM. */
@RequiresRom(SonicGame.SONIC_2)
class TestPuttPuttParadise {
    @TempDir static Path temp;
    static URLClassLoader loader;
    static Path jar;
    SharedLevel bootstrap;

    @BeforeAll static void compileAndPackage() throws Exception {
        Path project = Path.of("examples/putt-putt-paradise");
        assertTrue(Files.isDirectory(project.resolve("src/main/java")), "missing golf code mod");
        Path classes = Files.createDirectory(temp.resolve("classes"));
        var args = new ArrayList<>(List.of("--release", "21", "-cp", System.getProperty("java.class.path"),
                "-d", classes.toString()));
        try (var sources = Files.walk(project.resolve("src/main/java"))) {
            sources.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        Files.createDirectories(classes.resolve("META-INF"));
        Files.copy(project.resolve("src/main/resources/META-INF/openggf-mod.yaml"),
                classes.resolve("META-INF/openggf-mod.yaml"));
        jar = temp.resolve("putt-putt-paradise.jar");
        // Keep SDK validation an independent delivery gate: a validator failure must
        // not prevent the ROM-backed behavior tests from diagnosing another layer.
        try (var archive = new java.util.jar.JarOutputStream(Files.newOutputStream(jar));
             var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                archive.putNextEntry(new java.util.jar.JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, archive); archive.closeEntry();
            }
        }
        loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, TestPuttPuttParadise.class.getClassLoader());
    }

    @AfterAll static void closeLoader() throws Exception { if (loader != null) loader.close(); }
    @AfterEach void closeSession() { if (bootstrap != null) bootstrap.dispose(); }

    @Test void artifactPassesNormalSdkPackaging() throws Exception {
        assertEquals(0, GgfModCli.run(new String[]{"package", "--input", temp.resolve("classes").toString(),
                "--out", temp.resolve("validated.jar").toString()}, System.out));
    }

    private ModRegistrationPlan registrations() throws Exception {
        try (var assets = ModAssetRoot.jar(temp, jar, ModInputLimits.production())) {
            var context = new ModContext("putt-putt-paradise", "s2", assets);
            ((GgfMod) loader.loadClass("paradise.PuttPuttParadiseMod").getConstructor().newInstance()).register(context);
            return context.freeze();
        }
    }

    @Test void packagedPatchUsesOnlySonicAndTailsWithoutBundlingRomAssets() throws Exception {
        var patches = registrations().explicitPatches();
        assertEquals(1, patches.size());
        var patch = patches.getFirst();
        assertEquals("putt-putt-paradise:golf", patch.id());
        assertEquals("Putt Putt Paradise", patch.displayName());
        assertEquals(Set.of(LogicalRom.S2), patch.romPrerequisites());
        for (String character : List.of("sonic", "tails"))
            assertTrue(patch.activatesFor(new GameplayLaunchRequest("s2", character, List.of())));
        assertFalse(patch.activatesFor(new GameplayLaunchRequest("s2", "knuckles", List.of())));
        assertTrue(patch.activatesFor(new GameplayLaunchRequest("s2", "sonic", List.of("tails"))), "stock S2 profile must reach the golf menu");
        assertFalse(patch.activatesFor(new GameplayLaunchRequest("s1", "sonic", List.of())));
        try (var archive = new JarFile(jar.toFile())) {
            assertFalse(archive.stream().anyMatch(e -> e.getName().endsWith(".gen") || e.getName().endsWith(".bin")));
        }
    }

    @ParameterizedTest @CsvSource({"sonic,0", "tails,0", "sonic,1", "tails,1"})
    void launchesFullRomEmeraldHillWithoutSidekick(String character, int act) throws Exception {
        var fixture = launch(character, act);
        assertEquals(character, fixture.sprite().getCode());
        assertEquals(0, GameServices.level().getCurrentLevel().getZoneIndex());
        assertEquals(act, GameServices.level().getCurrentAct());
        assertFalse(GameServices.level().getCurrentLevel().getObjects().isEmpty(), "keep ROM course objects");
    }

    private HeadlessTestFixture launch(String character, int act) throws Exception {
        return launch(character, act, "NATIVE_4_3");
    }
    private HeadlessTestFixture launch(String character, int act, String aspect) throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect);
        config.resolveDisplayAspect();
        GameServices.graphics().setProjectionWidth(config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
        GameModule effective = GameServices.module();
        for (var patch : registrations().explicitPatches()) effective = patch.apply(effective, null);
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder().withZoneAndAct(0, act).build();
    }

    @Test void creatorFinishFlagSurvivesWireWithoutChangingNativeViewOrCourse() throws Exception {
        var fixture = launch("sonic", 0); fixture.stepIdleFrames(120);
        int finishX = (int) value(mode(), "finishX"), finishY = (int) value(mode(), "finishY");
        fixture.camera().setX((short) (finishX - 160)); fixture.camera().setY((short) (finishY - 160));
        var level = GameServices.level();
        GameServices.parallax().update(0, 0, fixture.camera(), level.getFrameCounter(), level.getCurrentLevel());
        var before = fixture.runtime().getRewindRegistry().capture();
        var frame = level.captureScene(1, com.openggf.game.presentation.PlayerPresentationPose.nativePose(), 160, 112);
        int primitives = frame.primitives().size();
        var helper = loader.loadClass("paradise.presentation.GolfScene");
        var decorated = (com.openggf.game.presentation.ScenePresentationFrame) helper.getMethod("withFinishFlag",
                com.openggf.game.presentation.ScenePresentationFrame.class, int.class, int.class)
                .invoke(null, frame, finishX, finishY);
        assertEquals(primitives, frame.primitives().size(), "source frame remains immutable");
        assertEquals(primitives + 1, decorated.primitives().size());
        assertEquals(frame.tiles(), decorated.tiles(), "preserve native ROM art and tile values");
        assertArrayEquals(frame.paletteArgb(), decorated.paletteArgb());
        var decoded = com.openggf.game.presentation.SceneFrameCodec.decode(
                com.openggf.game.presentation.SceneFrameCodec.encode(decorated));
        assertEquals(decorated.primitives(), decoded.primitives());
        try (var original = level.createScenePresenter(); var received = level.createScenePresenter()) {
            original.accept(frame); received.accept(decoded);
            var a = original.image(0, 0); var b = received.image(0, 0);
            assertFalse(Arrays.equals(a.argb(), b.argb()), "wire frame displays the gold finish flag");
            int poleX = finishX - frame.cameraX() - 1, poleY = finishY + 32 - frame.cameraY() - 40;
            assertEquals(0xFFFFE8A1, b.argb()[poleY * b.width() + poleX], "visible gold pole");
        }
        var after = fixture.runtime().getRewindRegistry().capture();
        for (String key : before.entries().keySet()) assertEquals(List.of(),
                com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, before.get(key), after.get(key)), key);
    }

    @Test void aimingHoldsEveryCourseSubsystem() throws Exception {
        var fixture = launch("sonic", 0);
        fixture.stepIdleFrames(120); // Let the native spawn settle, then open a neutral lie.
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        fixture.stepIdleFrames(60);
        var after = registry.capture();
        for (var entry : before.entries().entrySet()) {
            if (!entry.getKey().startsWith("mode:"))
                assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                        entry.getKey(), entry.getValue(), after.entries().get(entry.getKey())), entry.getKey());
        }
    }

    private Object mode() { return GameServices.module().gameplayFrameController(); }
    private Object value(Object record, String accessor) throws Exception {
        return record.getClass().getMethod(accessor).invoke(record);
    }
    private Object shotState() throws Exception { return value(mode(), "shotState"); }
    private Object matchState() throws Exception { return value(mode(), "matchState"); }

    @ParameterizedTest
    @CsvSource({"sonic,0,1,0,NATIVE_4_3,19,5", "sonic,45,1,0,NATIVE_4_3,13,-7",
            "tails,75,-1,0,SUPER_32_9,-5,-16", "sonic,0,1,224,NATIVE_4_3,17,-9",
            "tails,45,-1,224,SUPER_32_9,-18,2", "sonic,0,-1,32,NATIVE_4_3,-17,-9",
            "tails,15,1,0,WIDE_16_9,18,-3"})
    void aimingGuideMatchesLaunchOriginAtReferencePowerAndSelectedAngle(String character, int elevation,
            int facing, int surfaceAngle, String aspect, int firstDx, int firstDy) throws Exception {
        var fixture = launch(character, 0, aspect); fixture.stepIdleFrames(120);
        for (int i = 0; i < elevation; i++) fixture.stepFrame(true, false, false, false, false);
        fixture.stepFrame(false, false, facing < 0, facing > 0, false);
        fixture.sprite().setAngle((byte) surfaceAngle);
        int x = fixture.sprite().getCentreX() - fixture.camera().getX();
        int y = fixture.sprite().getCentreY() - fixture.camera().getY();
        var before = fixture.runtime().getRewindRegistry().captureCourse();
        var dots = guideDots();
        assertFalse(dots.isEmpty());
        assertEquals(x + firstDx, value(dots.getFirst(), "x"));
        assertEquals(y + firstDy, value(dots.getFirst(), "y"));
        for (var dot : dots) {
            int dx = (int) value(dot, "x"), dy = (int) value(dot, "y");
            assertTrue(dx >= 0 && dx + 2 <= fixture.camera().getWidth() && dy >= 0 && dy + 2 <= 224);
        }
        var after = fixture.runtime().getRewindRegistry().captureCourse();
        for (String key : before.entries().keySet()) assertEquals(List.of(),
                com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, before.get(key), after.get(key)), key);
    }

    @ParameterizedTest @CsvSource({"NATIVE_4_3,320", "SUPER_32_9,800"})
    void guideDoesNotQueuePartiallyClippedDots(String aspect, int width) throws Exception {
        var fixture = launch("sonic", 0, aspect); fixture.stepIdleFrames(120);
        // The first reference-power dot would start at the viewport's final column.
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(fixture.sprite(),
                fixture.camera().getX() + width - 20);
        assertTrue(guideDots().isEmpty(), "a two-pixel dot must fit completely inside the viewport");
    }

    private List<com.openggf.graphics.GLCommandable> guideDots() {
        var graphics = new GuideGraphics();
        try (var services = org.mockito.Mockito.mockStatic(GameServices.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            services.when(GameServices::graphics).thenReturn(graphics);
            ((com.openggf.game.mode.GameplayFrameController) mode()).drawOverlay();
        }
        return graphics.commands.stream().filter(command -> {
            try { return (int) value(command, "width") == 2 && (int) value(command, "height") == 2
                    && (float) value(command, "alpha") == 0.8f; }
            catch (Exception failure) { throw new AssertionError(failure); }
        }).toList();
    }

    private static final class GuideGraphics extends com.openggf.graphics.GraphicsManager {
        final List<com.openggf.graphics.GLCommandable> commands = new ArrayList<>();
        @Override public void registerCommand(com.openggf.graphics.GLCommandable command) { commands.add(command); }
    }
    private void pressA(HeadlessTestFixture fixture) { fixture.stepFrame(false, false, false, false, true); }
    private void commit(HeadlessTestFixture fixture, int meterTicks) {
        pressA(fixture);
        fixture.stepIdleFrames(meterTicks);
        pressA(fixture);
        fixture.stepIdleFrames(meterTicks);
        pressA(fixture);
    }

    @ParameterizedTest @CsvSource({"sonic,0", "tails,0", "sonic,45", "tails,45"})
    void chargesCommitOnceAndReleaseIntoNativeRolling(String character, int elevation) throws Exception {
        var fixture = launch(character, 0);
        fixture.stepIdleFrames(120);
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        for (int i = 0; i < elevation; i++) fixture.stepFrame(true, false, false, false, false);
        assertEquals(elevation, value(shotState(), "elevationDegrees"));
        assertEquals(x, fixture.sprite().getCentreX(), "aim does not walk");
        assertEquals(y, fixture.sprite().getCentreY(), "aim does not alter the neutral lie");
        commit(fixture, 55);
        assertEquals("FEEDBACK", value(shotState(), "stage").toString());
        Object pending = value(matchState(), "pending");
        assertNotNull(pending);
        var registry = fixture.runtime().getRewindRegistry();
        var beforeRelease = registry.captureCourse();
        fixture.stepIdleFrames(29);
        for (var e : beforeRelease.entries().entrySet())
            assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(e.getKey(), e.getValue(),
                    registry.captureCourse().get(e.getKey())), "feedback holds " + e.getKey());
        fixture.stepIdleFrames(1);
        assertEquals("WATCH", value(shotState(), "stage").toString());
        assertTrue(fixture.sprite().getRolling());
        assertTrue(fixture.sprite().getCentreX() > x);
        if (elevation > 0) assertTrue(fixture.sprite().getAir());
        assertFalse(fixture.sprite().getPinballMode(), "golf must not use native pinball speed boosts");
    }

    @Test void damageRestoresWholeNeutralCourseButRetainsStrokeAndPenalty() throws Exception {
        var fixture = launch("sonic", 0);
        fixture.stepIdleFrames(120);
        var registry = fixture.runtime().getRewindRegistry();
        var neutral = registry.captureCourse();
        commit(fixture, 55);
        fixture.stepIdleFrames(32);
        fixture.sprite().setHurt(true);
        fixture.stepIdleFrames(1);
        assertEquals("AIM", value(shotState(), "stage").toString());
        var golfer = ((List<?>) value(matchState(), "golfers")).getFirst();
        var score = ((List<?>) value(golfer, "holes")).getFirst();
        assertEquals(1, value(score, "strokes"));
        assertEquals(1, value(score, "penalties"));
        for (var entry : neutral.entries().entrySet())
            assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(),
                    registry.captureCourse().get(entry.getKey())), "penalty restores " + entry.getKey());
    }
    private void local(String one, String two) throws Exception {
        var selectionClass = loader.loadClass("paradise.ui.GolfMenu$Selection");
        Class modeClass = loader.loadClass("paradise.ui.GolfMenu$Mode");
        Class charClass = loader.loadClass("paradise.ui.GolfMenu$CharacterChoice");
        Class viewportClass = loader.loadClass("paradise.ui.GolfMenu$Viewport");
        Object choice = selectionClass.getConstructor(modeClass, charClass, charClass, int.class, String.class,
                int.class, viewportClass).newInstance(Enum.valueOf(modeClass, "LOCAL"),
                Enum.valueOf(charClass, one.toUpperCase(Locale.ROOT)), Enum.valueOf(charClass, two.toUpperCase(Locale.ROOT)),
                0, "127.0.0.1", 20502, Enum.valueOf(viewportClass, "NATIVE_4_3"));
        mode().getClass().getMethod("configure", selectionClass).invoke(mode(), choice);
    }
    @ParameterizedTest @CsvSource({"sonic,tails", "tails,sonic", "sonic,sonic", "tails,tails"})
    void alternateIndependentGolferWorldsAndRewindRoster(String one, String two) throws Exception {
        var fixture = launch(one, 0); local(one, two); fixture.stepIdleFrames(120);
        var registry = fixture.runtime().getRewindRegistry();
        var firstNeutral = registry.captureCourse(); var rewind = registry.capture();
        commit(fixture, 55); fixture.stepIdleFrames(32);
        GameServices.camera().getFocusedSprite().setHurt(true); fixture.stepIdleFrames(1);
        assertEquals(1, value(matchState(), "activePlayer"));
        assertEquals(two, GameServices.camera().getFocusedSprite().getCode());
        fixture.stepIdleFrames(1); commit(fixture, 55); fixture.stepIdleFrames(32);
        GameServices.camera().getFocusedSprite().setHurt(true); fixture.stepIdleFrames(1);
        assertEquals(0, value(matchState(), "activePlayer"));
        assertEquals(one, GameServices.camera().getFocusedSprite().getCode());
        for (var entry : firstNeutral.entries().entrySet())
            assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(),
                    registry.captureCourse().get(entry.getKey())), "independent neutral world " + entry.getKey());
        registry.restore(rewind);
        assertEquals(one, GameServices.camera().getFocusedSprite().getCode());
        assertEquals(0, value(matchState(), "activePlayer"));
        assertNull(value(matchState(), "pending"));
        assertEquals("AIM", value(shotState(), "stage").toString());
        for (var entry : firstNeutral.entries().entrySet())
            assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(),
                    registry.captureCourse().get(entry.getKey())), "debug rewind " + entry.getKey());
    }

    @Test void exactSweptGateRejectsDiagonalBoundingBoxFalsePositive() throws Exception {
        var method = loader.loadClass("paradise.GolfMode").getMethod("crossesGate", int.class, int.class,
                int.class, int.class, int.class, int.class);
        assertEquals(true, method.invoke(null, 0, 100, 200, 100, 100, 100));
        assertEquals(false, method.invoke(null, 0, 0, 200, 300, 100, 0));
        assertEquals(false, method.invoke(null, 0, -200, 200, -200, 100, 100));
    }

    private void tick(HeadlessTestFixture fixture, int held, int actions, boolean start) {
        var controls = new com.openggf.control.InputHandler();
        var player = com.openggf.control.PlayerInputState.of(held, held, actions, actions, start, start);
        com.openggf.game.mode.ControlledFrameRuntime.step(fixture.runtime(), controls,
                com.openggf.control.LogicalInputSnapshot.ofPlayers(player, com.openggf.control.PlayerInputState.neutral()));
    }
    @Test void pauseFreezesFeedbackAndCancelIsFreeBeforeCommit() throws Exception {
        var f = launch("sonic", 0); f.stepIdleFrames(120);
        pressA(f); f.stepIdleFrames(10);
        tick(f, 0, com.openggf.control.InputActionMasks.ACTION_B, false);
        assertEquals("AIM", value(shotState(), "stage").toString());
        assertNull(value(matchState(), "pending"));
        f.stepIdleFrames(1); commit(f, 60);
        var before = f.runtime().getRewindRegistry().captureCourse(); var meterBefore = shotState();
        tick(f, 0, 0, true); f.stepIdleFrames(600);
        assertEquals(meterBefore, shotState(), "pause freezes the release countdown");
        for (var e : before.entries().entrySet())
            assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(e.getKey(), e.getValue(),
                    f.runtime().getRewindRegistry().captureCourse().get(e.getKey())), "pause " + e.getKey());
        tick(f, 0, 0, true); f.stepIdleFrames(32);
        assertEquals("WATCH", value(shotState(), "stage").toString());
    }

    // Canonical native-input routes: fresh lies, no teleports or exploratory checkpoint restores.
    private static int[][] route(int act) {
        return act == 0 ? new int[][] {
                {1,0,30},{1,40,60},{1,0,45},{1,0,60},{1,0,60},{1,0,60},
                {-1,0,2},{1,60,60},{1,20,60},{1,60,60}
        } : new int[][] {
                {1,40,60},{1,0,60},{-1,0,15},{1,20,60},{1,60,30},{1,60,30},
                {-1,0,15},{1,75,60},{1,20,60},{1,20,30},{1,0,30},{1,40,60},
                {1,0,60},{1,60,60},{1,20,45},{-1,0,2},{1,0,30},{-1,0,2},
                {1,60,45},{1,75,45},{-1,0,4},{-1,0,4},{1,75,30},{1,40,60},
                {1,0,60},{1,0,60}
        };
    }
    private static int[][] route(int act, String aspect) {
        var nativeShots = route(act);
        if (act == 0) {
            if (!aspect.equals("NATIVE_4_3") && !aspect.equals("SUPER_32_9")) nativeShots[8][2] = 45;
            return nativeShots;
        }
        // Wider admission changes the real object world. These are fresh input choices,
        // not a physics override or restored exploration checkpoint.
        if (aspect.equals("SUPER_32_9")) return new int[][] {
                {1,40,60},{1,0,60},{-1,0,15},{1,20,60},{1,60,30},{1,60,30},
                {-1,0,15},{-1,0,2},{1,75,60},{1,40,60},{1,20,30},{1,0,30},
                {1,0,45},{-1,0,2},{1,75,60},{1,0,45}
        };
        if (aspect.equals("WIDE_16_9")) return new int[][] {
                {1,40,60},{-1,0,2},{1,0,60},{-1,0,15},{1,20,60},{1,60,30},
                {1,60,30},{-1,0,13},{1,0,5},{1,75,60},{1,40,60},{1,20,30},
                {1,75,60},{1,40,60},{1,0,60},{1,60,60},{1,20,45},{-1,0,2},
                {1,0,30},{-1,0,2},{1,60,45},{1,75,45},{-1,0,4},{-1,0,4},
                {1,75,30},{1,40,60},{1,0,60},{1,0,60},{1,0,30},{-1,75,15},
                {1,20,45},{-1,0,2},{1,0,30},{-1,0,2},{1,60,45},{1,75,45},
                {-1,0,4},{-1,0,4},{1,75,30},{1,40,60},{1,0,60},
                {-1,0,2},{1,40,60},{-1,20,6},{1,40,45},{-1,20,45},
                {1,60,45},{-1,0,4},{1,40,30},{1,60,45},{1,20,15},{1,0,4},
                {1,75,45},{1,0,45}
        };
        if (!aspect.equals("WIDE_16_10") && !aspect.equals("ULTRA_21_9")) return nativeShots;
        var shots = new ArrayList<int[]>();
        for (int i = 0; i < nativeShots.length; i++) {
            if (i == 7) shots.add(new int[]{-1,0,2});
            if (i == 8) shots.add(new int[]{-1,0,6});
            shots.add(i == 10 ? new int[]{1,75,60} : nativeShots[i]);
        }
        shots.addAll(List.of(new int[]{1,0,30},new int[]{-1,75,15},new int[]{1,60,45},
                new int[]{1,75,45},new int[]{-1,0,4},new int[]{-1,0,4},new int[]{1,75,30},
                new int[]{1,40,60},new int[]{1,0,60}));
        return shots.toArray(int[][]::new);
    }
    private void shoot(HeadlessTestFixture f, int direction, int elevation, int powerTicks) throws Exception {
        f.stepIdleFrames(1);
        f.stepFrame(false, false, direction < 0, direction > 0, false);
        int old = (int) value(shotState(), "elevationDegrees");
        for (int i = old; i < elevation; i++) f.stepFrame(true, false, false, false, false);
        for (int i = old; i > elevation; i--) f.stepFrame(false, true, false, false, false);
        f.stepIdleFrames(1); commit(f, powerTicks);
        int frames = 0;
        do { f.stepIdleFrames(1); frames++; } while (!value(shotState(), "stage").toString().equals("AIM")
                && value(matchState(), "status").toString().equals("PLAYING") && frames < 3700);
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> routeCases() {
        return java.util.stream.Stream.of("NATIVE_4_3", "WIDE_16_10", "WIDE_16_9", "ULTRA_21_9", "SUPER_32_9")
                .flatMap(aspect -> java.util.stream.Stream.of("sonic", "tails").flatMap(character ->
                        java.util.stream.IntStream.range(0,2).mapToObj(act ->
                                org.junit.jupiter.params.provider.Arguments.of(character, act, aspect))));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("routeCases")
    void completeFreshRomRoutes(String character, int act, String aspect) throws Exception {
        var f = launch(character, act, aspect); f.stepIdleFrames(120);
        int index = 0;
        var shots = route(act, aspect);
        for (int[] shot : shots) {
            shoot(f, shot[0], shot[1], shot[2]); index++;
            String outcome = value(value(matchState(), "lastResolved"), "outcome").toString();
            assertTrue(outcome.equals("SETTLED") || outcome.equals("FINISH"),
                    character + " EHZ" + (act + 1) + " shot " + index + " " + outcome + " at "
                            + GameServices.camera().getFocusedSprite().getCentreX() + ","
                            + GameServices.camera().getFocusedSprite().getCentreY());
            if (value(matchState(), "status").toString().equals("COMPLETE")) break;
        }
        assertEquals("COMPLETE", value(matchState(), "status").toString(), character + " EHZ" + (act + 1));
        var golfer = ((List<?>) value(matchState(), "golfers")).getFirst();
        assertEquals(0, value(((List<?>) value(golfer, "holes")).get(act), "penalties"));
    }

    @ParameterizedTest @CsvSource({"sonic,tails", "tails,sonic", "sonic,sonic", "tails,tails"})
    void bothFullActsCompleteWithAlternatingIndependentGolferRoutes(String one, String two) throws Exception {
        var f = launch(one, 0); local(one,two); f.stepIdleFrames(120);
        int[][] next = new int[2][2];
        int previousAct = 0;
        for (int shots=0;shots<100 && value(matchState(),"status").toString().equals("PLAYING");shots++) {
            int act = (int) value(matchState(),"actIndex"), owner = (int) value(matchState(),"activePlayer");
            if (act != previousAct) {
                assertEquals(1,owner,"P2 starts the second act"); previousAct=act;
                f.stepIdleFrames(120);
                assertEquals(0,f.runtime().getRewindController() == null ? 0 : f.runtime().getRewindController().currentFrame(),
                        "actual act load resets debug timeline");
            }
            var route = route(act);
            assertTrue(next[act][owner] < route.length,"fresh route exhausted for P"+(owner+1)+" EHZ"+(act+1));
            var shot = route[next[act][owner]++];
            shoot(f,shot[0],shot[1],shot[2]);
            String outcome = value(value(matchState(),"lastResolved"),"outcome").toString();
            assertTrue(outcome.equals("SETTLED") || outcome.equals("FINISH"),one+"/"+two+" P"+(owner+1)+" EHZ"+(act+1)+" "+outcome);
        }
        assertEquals("COMPLETE",value(matchState(),"status").toString());
        assertEquals(-1,value(matchState(),"winner"),"identical complete routes draw");
        for (var golfer : (List<?>)value(matchState(),"golfers")) for (var hole : (List<?>)value(golfer,"holes")) {
            assertEquals(true,value(hole,"finished")); assertEquals(0,value(hole,"penalties"));
        }
    }

    @Test void runningRowPreservesLogicalInputAndDistinctChargeAudioStamps() throws Exception {
        var f = launch("sonic", 0); f.stepIdleFrames(120);
        var audio = GameServices.audio(); int before = audio.commandTimeline().entryCount();
        commit(f, 60); f.stepIdleFrames(31);
        var input = new com.openggf.control.InputHandler();
        var snapshot = com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(0, 0, com.openggf.control.InputActionMasks.ACTION_A, 0, false, false),
                com.openggf.control.PlayerInputState.neutral());
        input.setLogicalOverride(snapshot);
        com.openggf.game.mode.ControlledFrameRuntime.step(f.runtime(), input, snapshot);
        assertEquals(snapshot, input.logical(), "RUN must not replace recorded player input with native neutral input");
        // Every direct HOLD/RUN row has its own audio command cursor; presentation still owns the native pitch ladder.
        assertTrue(audio.commandTimeline().currentFrame() > 120);
        var requests = audio.commandTimeline().entries().stream().skip(before)
                .filter(e -> e.command() instanceof com.openggf.audio.rewind.AudioCommand.PlaySfx)
                .toList();
        assertTrue(requests.size() >= 3, requests.toString());
        assertTrue(requests.stream().map(com.openggf.audio.rewind.AudioTimelineEntry::frame).distinct().count() >= 3,
                "two charges and release have distinct mode row stamps");
        for (var request : requests)
            assertEquals(1.0f, ((com.openggf.audio.rewind.AudioCommand.PlaySfx) request.command()).pitch(),
                    "native driver alone owns innate spindash pitch");
    }

    @Test void normalDevelopmentBootUsesStockProfileAndTitleSelectionBeforeEntryFade() throws Exception {
        // Bootstrap only the user's real S2 ROM/catalogue. Resolve the creator patch
        // independently through the normal validated development loader below.
        bootstrap = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        var previousServices = com.openggf.game.session.EngineServices.current();
        String previousDev = System.getProperty(com.openggf.mods.DevelopmentModSource.PROPERTY);
        int previousWidth = previousServices.graphics().getProjectionWidth();
        var config = SonicConfigurationService.createStandalone(temp.resolve("boot-config"));
        var entry = MasterTitleScreen.GameEntry.SONIC_2;
        new com.openggf.game.launch.LaunchProfileApplier(config).apply(
                com.openggf.game.launch.LaunchProfile.stockFor(entry), entry);
        assertEquals("sonic", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("tails", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        com.openggf.ModSubsystem subsystem = null;
        ModRuntime runtime = null;
        try {
            System.setProperty(com.openggf.mods.DevelopmentModSource.PROPERTY,
                    temp.resolve("classes").toAbsolutePath().toString());
            Path modRoot = Files.createDirectories(temp.resolve("normal-boot-mods"));
            subsystem = com.openggf.ModSubsystem.normalBootLoader(() -> modRoot,
                    ModInputLimits.production(), (game, id) -> true,
                    new com.openggf.ModSubsystem.SessionAudioBoundary() {
                        public void install(com.openggf.audio.StreamedMusicPort port) { }
                        public void clear() { }
                    }).get();
            var effective = subsystem.processCatalog().effective();
            assertEquals(List.of("putt-putt-paradise"), effective.orderedEnabled().stream()
                    .map(d -> d.manifest().id()).toList());
            assertEquals(Set.of("putt-putt-paradise"), subsystem.trustedCodeOwners());
            subsystem.transferDevelopmentSourceOwnership();
            runtime = new ModClassLoaderFactory(getClass().getClassLoader()).create(
                    effective, subsystem.trustedCodeOwners(), true);
            runtime.installFaultBoundary(subsystem.createFaultBoundary(runtime));
            var resolver = new ModuleResolutionService(List.of(), PatchEnablement.ALL_ENABLED,
                    LogicalRomResolver.fromRomManager(previousServices.roms()), config);
            ModRuntime bootRuntime = runtime;
            resolver.installModPlanSource(new EffectiveCatalogPatchEnablement(effective),
                    ignored -> bootRuntime.newRegistrationPlan());
            var services = new com.openggf.game.session.EngineContext(config,
                    previousServices.graphics(), previousServices.audio(), previousServices.roms(),
                    previousServices.profiler(), previousServices.debugOverlay(), previousServices.playbackDebug(),
                    previousServices.romDetection(), previousServices.crossGameFeatures(), resolver);
            com.openggf.game.session.EngineServices.configure(services);
            var root = new com.openggf.game.sonic2.Sonic2GameModule();
            var session = com.openggf.tools.HeadlessGameBoot.openResolvedSessionForBoot(
                    services, root, ModuleResolutionService.LaunchPolicy.STANDARD);
            com.openggf.game.session.GameplaySessionFactory.attachManagers(session, services);
            services.graphics().initHeadless();
            GameModule module = session.getWorldSession().getGameModule();
            assertNotSame(root, module);
            assertTrue(module.suppressesLevelSelect());
            assertTrue(runtime.registrationFailures().isEmpty(), runtime.registrationFailures().toString());
            TitleScreenProvider title = module.getTitleScreenProvider();
            assertNotNull(title);
            title.initialize();
            var input = new com.openggf.control.InputHandler();
            // Practice -> Tails -> EHZ2 -> WIDE_16_9 -> Ready: real menu input,
            // never constructing Selection or invoking the consumer directly.
            titleTap(title, input, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            titleTap(title, input, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
            titleTap(title, input, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            titleTap(title, input, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
            titleTap(title, input, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            titleTap(title, input, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
            titleTap(title, input, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
            titleTap(title, input, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
            titleTap(title, input, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            assertTrue(title.isExiting());
            assertEquals(TitleScreenProvider.TitleScreenAction.ONE_PLAYER, title.consumeExitAction());
            assertEquals(1, title.startActIndex());
            assertEquals("WIDE_16_9", module.requiredDisplayAspect());
            assertEquals("tails", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            assertEquals("", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
            // Simulate the persisted profile being re-read at launch: the title's
            // session choice must still win and suppress the stock CPU sidekick.
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
            assertEquals("tails", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            assertEquals("", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
            // The interactive Engine applies requiredDisplayAspect on its
            // synchronous LEVEL transition before the destination load.
            config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, module.requiredDisplayAspect());
            config.resolveDisplayAspect();
            GameServices.camera().refreshViewportDimensions(config);
            services.graphics().setProjectionWidth(config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
            var team = com.openggf.game.session.GameplayTeamBootstrap.registerActiveTeam(
                    module, GameServices.sprites(), config);
            assertEquals("tails", team.mainSprite().getCode());
            assertTrue(team.sidekicks().isEmpty());
            GameServices.camera().setFocusedSprite(team.mainSprite());
            GameServices.level().loadZoneAndAct(title.startZoneIndex(), title.startActIndex());
            com.openggf.physics.GroundSensor.setLevelManager(GameServices.level());
            session.getFadeManager().startFadeFromBlack(null);
            assertTrue(session.getFadeManager().isActive());
            for (int tick = 0; tick < 240; tick++) {
                com.openggf.game.mode.ControlledFrameRuntime.step(session, input,
                        com.openggf.control.LogicalInputSnapshot.neutral());
                input.update();
            }
            assertFalse(session.getFadeManager().isActive(), "entry fade must not remain behind held aiming");
            Object state = module.rewindAdapters().stream()
                    .filter(adapter -> adapter.key().startsWith("mode:"))
                    .findFirst().orElseThrow().capture();
            Object meter = value(state, "meter");
            assertEquals("AIM", value(meter, "stage").toString());
            assertNotNull(value(state, "match"), "neutral lie opened");
            assertNotNull(value(state, "neutralLie"), "setup completed before aim holds the world");
            assertEquals(1, GameServices.level().getCurrentAct());
            assertEquals(400, GameServices.camera().getWidth());
            var scene = GameServices.level().captureScene(1, com.openggf.game.presentation.PlayerPresentationPose.nativePose());
            assertEquals(400, scene.width());
            try (var presenter = GameServices.level().createScenePresenter()) {
                presenter.accept(scene); assertEquals(400,presenter.image(0,0).width());
            }
            assertEquals("tails", GameServices.camera().getFocusedSprite().getCode());
            assertEquals(1, GameServices.sprites().getAllSprites().stream()
                    .filter(com.openggf.sprites.playable.AbstractPlayableSprite.class::isInstance).count());
        } finally {
            SessionManager.clear();
            if (runtime != null) runtime.close();
            if (subsystem != null) subsystem.close();
            if (previousDev == null) System.clearProperty(com.openggf.mods.DevelopmentModSource.PROPERTY);
            else System.setProperty(com.openggf.mods.DevelopmentModSource.PROPERTY, previousDev);
            previousServices.graphics().setProjectionWidth(previousWidth);
            com.openggf.game.session.EngineServices.configure(previousServices);
        }
    }

    private void titleTap(TitleScreenProvider title, com.openggf.control.InputHandler input, int key) {
        input.handleKeyEvent(key, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        input.refreshLogicalSnapshot(); title.update(input); input.update();
        input.handleKeyEvent(key, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        input.refreshLogicalSnapshot(); title.update(input); input.update();
    }

    private record RewindExpected(com.openggf.game.rewind.CompositeSnapshot course, Object meter,
                                  Object match, long tick) { }

    @Test void liveGameLoopAndBk2DriverConsumeTheSameHeldChargePauseAndReleaseRows() throws Exception {
        var rows = new ArrayList<com.openggf.debug.playback.Bk2FrameInput>();
        for (int row = 0; row < 360; row++) {
            int aimRow = row - 120;
            rows.add(new com.openggf.debug.playback.Bk2FrameInput(row, 0,
                    aimRow == 1 || aimRow == 32 || aimRow == 63 ? 1 : 0,
                    aimRow == 12 || aimRow == 20, ""));
        }
        record Observed(com.openggf.game.mode.CourseControl.Ball ball, Object meter, Object ledger) { }
        var expected = new ArrayList<Observed>();
        var f = launch("sonic", 0);
        var input = new com.openggf.control.InputHandler();
        var loop = new com.openggf.GameLoop(input);
        loop.setGameplayMode(f.runtime()); loop.setGameMode(GameMode.LEVEL);
        com.openggf.debug.playback.Bk2FrameInput previous = null;
        for (var row : rows) {
            input.setLogicalOverride(com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(row, previous));
            loop.step(); previous = row;
            expected.add(new Observed(checkpointCourse(f).ball(), shotState(), matchState()));
        }
        assertEquals("WATCH", value(expected.get(220).meter(), "stage").toString());
        assertTrue(expected.get(220).ball().rolling());
        assertTrue(expected.get(220).ball().x() > expected.get(120).ball().x());
        bootstrap.dispose(); bootstrap = null;

        f = launch("sonic", 0);
        var driver = new com.openggf.tools.RecordingFrameDriver(f.sprite());
        driver.setBk2Movie(new com.openggf.debug.playback.Bk2Movie(
                Path.of("golf-held-rows.bk2"), "logkey", Map.of(), rows, 1), 0);
        for (int row = 0; row < rows.size(); row++) {
            driver.stepFrameFromRecording();
            var observed = new Observed(checkpointCourse(f).ball(), shotState(), matchState());
            assertEquals(expected.get(row), observed, "live/BK2 mode row " + row);
            if (row == 0) assertEquals(com.openggf.LevelFrameResult.GAMEPLAY_FRAME, driver.getLastFrameResult());
            if (row == 1) assertEquals(com.openggf.LevelFrameResult.HELD, driver.getLastFrameResult());
            if (row == 140) assertEquals(com.openggf.LevelFrameResult.HELD, driver.getLastFrameResult());
        }
    }

    @Test void liveRewindReplaysNonKeyframeAimChargeFlightAndNativePenalty() throws Exception {
        var f = launch("sonic", 0); f.stepIdleFrames(120);
        var config = GameServices.configuration();
        Object oldEnabled = config.getConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        var manager = new com.openggf.game.rewind.LiveRewindManager(config);
        var input = new com.openggf.control.InputHandler();
        var expected = new TreeMap<Integer, RewindExpected>();
        try {
            input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.neutral());
            assertFalse(manager.handleRealtimeRewindInput(GameMode.LEVEL, false, input));
            for (int row = 1; row <= 1200; row++) {
                int directions = row <= 20 ? 1 : 0;
                int actions = row == 621 || row == 652 || row == 683 ? com.openggf.control.InputActionMasks.ACTION_A : 0;
                var player = com.openggf.control.PlayerInputState.of(directions, directions, actions, actions, false, false);
                input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(player,
                        com.openggf.control.PlayerInputState.neutral()));
                var result = com.openggf.game.mode.ControlledFrameRuntime.step(f.runtime(), input, input.logical());
                assertNotEquals(com.openggf.LevelFrameResult.SETUP_ONLY, result);
                manager.recordExternalFrame(GameMode.LEVEL, false, input);
                input.update();
                if (row == 303 || row == 620 || row == 633 || row == 664 || row == 700 || row == 721)
                    expected.put(row, rewindExpected(f));
                if (value(matchState(), "lastResolved") != null) {
                    assertEquals("DAMAGE", value(value(matchState(), "lastResolved"), "outcome").toString());
                    expected.put(row, rewindExpected(f)); break;
                }
            }
            var rewind = f.runtime().getRewindController();
            int penaltyRow = rewind.currentFrame();
            assertTrue(penaltyRow > 721 && penaltyRow < 1200, "real native damage must resolve the shot");
            assertEquals(0, rewind.earliestAvailableFrame(), "course rollback preserves rewindable past");
            for (var target : expected.descendingMap().entrySet()) {
                rewind.seekTo(target.getKey());
                assertRewindExpected(f, target.getValue(), target.getKey());
            }
            while (rewind.currentFrame() < penaltyRow) rewind.step();
            assertRewindExpected(f, expected.get(penaltyRow), penaltyRow);
            assertTrue(rewind.stepBackward(), "segment replay across the penalty remains available");
            rewind.step();
            assertRewindExpected(f, expected.get(penaltyRow), penaltyRow);
        } finally {
            manager.markBoundary(com.openggf.game.rewind.RewindBoundary.MODE_EXIT_TO_NON_REWINDABLE);
            config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, oldEnabled);
        }
    }
    private RewindExpected rewindExpected(HeadlessTestFixture f) throws Exception {
        return new RewindExpected(f.runtime().getRewindRegistry().captureCourse(), shotState(), matchState(),
                (long) value(((com.openggf.game.rewind.RewindSnapshottable<?>) mode()).capture(), "tick"));
    }
    private void assertRewindExpected(HeadlessTestFixture f, RewindExpected expected, int frame) throws Exception {
        assertEquals(expected.meter(), shotState(), "meter row " + frame);
        assertEquals(expected.match(), matchState(), "ledger row " + frame);
        assertEquals(expected.tick(), value(((com.openggf.game.rewind.RewindSnapshottable<?>) mode()).capture(), "tick"));
        var actual = f.runtime().getRewindRegistry().captureCourse();
        for (var entry : expected.course().entries().entrySet())
            assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(),
                    actual.get(entry.getKey())), "rewind row " + frame + " " + entry.getKey());
    }

    private com.openggf.game.mode.CourseControl checkpointCourse(HeadlessTestFixture fixture) throws Exception {
        var constructor = com.openggf.game.mode.CourseControl.class.getDeclaredConstructor(
                com.openggf.game.session.GameplayModeContext.class);
        constructor.setAccessible(true); // Engine-internal constructor, test only; checkpoint stays opaque.
        return constructor.newInstance(fixture.runtime());
    }

    @Test void checkpointFromDisposedSessionCannotReplaceNewGolferOrCourse() throws Exception {
        var old = launch("sonic", 0); old.stepIdleFrames(120);
        var checkpoint = checkpointCourse(old).capture();
        bootstrap.dispose(); bootstrap = null;
        var current = launch("tails", 0); current.stepIdleFrames(120);
        assertRejectedCheckpointLeavesCourseUntouched(current, checkpointCourse(current), checkpoint);
    }

    @Test void checkpointFromPreviousActCannotRewindNewActOrChangeCharacter() throws Exception {
        var fixture = launch("sonic", 0); fixture.stepIdleFrames(120);
        var course = checkpointCourse(fixture);
        var checkpoint = course.capture();
        course.selectCharacter("tails");
        course.loadAct(1);
        assertEquals(1, GameServices.level().getCurrentAct());
        assertRejectedCheckpointLeavesCourseUntouched(fixture, course, checkpoint);
    }

    @Test void sameKeyAdapterReplacementInvalidatesCheckpointBeforeAnyRestore() throws Exception {
        var fixture = launch("sonic", 0); fixture.stepIdleFrames(120);
        var course = checkpointCourse(fixture);
        var registry = fixture.runtime().getRewindRegistry();
        var first = new CheckpointSentinel(); registry.register(first);
        try {
            var checkpoint = course.capture();
            course.selectCharacter("tails"); // Wrong-order restore would rebuild Sonic before validation.
            registry.deregister(first.key());
            var replacement = new CheckpointSentinel(); registry.register(replacement);
            assertRejectedCheckpointLeavesCourseUntouched(fixture, course, checkpoint);
            assertEquals(0, first.restores);
            assertEquals(0, replacement.restores, "equal keys do not confer old adapter ownership");
        } finally { registry.deregister(first.key()); }
    }

    private static final class CheckpointSentinel
            implements com.openggf.game.rewind.RewindSnapshottable<Integer> {
        int restores;
        public String key() { return "checkpoint-test:sentinel"; }
        public Integer capture() { return 7; }
        public void restore(Integer value) { restores++; }
    }

    private void assertRejectedCheckpointLeavesCourseUntouched(HeadlessTestFixture fixture,
            com.openggf.game.mode.CourseControl course, com.openggf.game.mode.CourseCheckpoint checkpoint) {
        var focused = GameServices.camera().getFocusedSprite();
        var ball = course.ball();
        var level = GameServices.level().getCurrentLevel();
        var before = fixture.runtime().getRewindRegistry().captureCourse();
        int audioEntries = GameServices.audio().commandTimeline().entryCount();
        assertThrows(IllegalArgumentException.class, () -> course.restore(checkpoint));
        assertSame(focused, GameServices.camera().getFocusedSprite(), "reject before roster replacement");
        assertSame(level, GameServices.level().getCurrentLevel(), "reject before level replacement");
        assertEquals(ball, course.ball(), "position, velocity, character and camera remain unchanged");
        var after = fixture.runtime().getRewindRegistry().captureCourse();
        assertEquals(before.entries().keySet(), after.entries().keySet());
        for (var entry : before.entries().entrySet())
            assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    entry.getKey(), entry.getValue(), after.entries().get(entry.getKey())), entry.getKey());
        assertEquals(audioEntries, GameServices.audio().commandTimeline().entryCount(), "reject before audio commands");
    }

    @Test void opaqueCourseCheckpointGraphDoesNotRetainMutableSessionOwners() throws Exception {
        var fixture = launch("sonic", 0); fixture.stepIdleFrames(120);
        var checkpoint = checkpointCourse(fixture).capture();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var pending = new ArrayDeque<Object>(); pending.add(checkpoint);
        while (!pending.isEmpty()) {
            Object value = pending.removeFirst();
            if (!seen.add(value)) continue;
            assertFalse(value instanceof com.openggf.game.session.GameplayModeContext,
                    "checkpoint retains GameplayModeContext and its mode/checkpoint cycle");
            assertFalse(value instanceof com.openggf.game.session.WorldSession,
                    "checkpoint retains mutable WorldSession instead of opaque session identity");
            Class<?> type = value.getClass();
            if (type.isArray()) {
                if (!type.getComponentType().isPrimitive())
                    for (Object child : (Object[]) value) if (child != null) pending.add(child);
            } else if (value instanceof Map<?, ?> map) {
                map.forEach((key, child) -> { if (key != null) pending.add(key); if (child != null) pending.add(child); });
            } else if (value instanceof Iterable<?> iterable) {
                for (Object child : iterable) if (child != null) pending.add(child);
            } else if (value instanceof Optional<?> optional) {
                optional.ifPresent(pending::add);
            } else if (!type.isEnum() && !type.getName().startsWith("java.")
                    && !type.getName().startsWith("javax.") && !type.getName().startsWith("org.")) {
                for (Class<?> owner = type; owner != null && owner != Object.class; owner = owner.getSuperclass()) {
                    if (owner.getName().startsWith("java.")) break;
                    for (var field : owner.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                        field.setAccessible(true);
                        Object child = field.get(value);
                        if (child != null) pending.add(child);
                    }
                }
            }
            assertTrue(seen.size() <= 100_000, "checkpoint object graph unexpectedly unbounded");
        }
    }

    @Test void zeroSpeedSupportedCurlRemainsRolledUntilSettlement() throws Exception {
        var fixture = launch("sonic", 0); fixture.stepIdleFrames(120);
        var course = checkpointCourse(fixture);
        commit(fixture, 1); fixture.stepIdleFrames(30);
        assertEquals("WATCH", value(shotState(), "stage").toString());
        assertTrue(course.ball().floorSupport(), "weak flat putt starts on the verified spawn floor");
        var sprite = GameServices.camera().getFocusedSprite();
        // Arrange the exact zero-speed boundary once; production rows must preserve
        // curl and earn their own support dwell, not a manually supplied result.
        sprite.setGSpeed((short) 0); sprite.setXSpeed((short) 0); sprite.setYSpeed((short) 0);
        int rows = 0;
        while (value(shotState(), "stage").toString().equals("WATCH") && rows++ < 60) {
            fixture.stepIdleFrames(1);
            assertTrue(GameServices.camera().getFocusedSprite().getRolling(), "zero-speed native physics must not unroll golf");
        }
        assertEquals("AIM", value(shotState(), "stage").toString());
        assertEquals("SETTLED", value(value(matchState(), "lastResolved"), "outcome").toString());
        assertTrue(course.ball().floorSupport());
        assertTrue(course.ball().rolling());
        assertTrue(rows >= 19, "settlement requires actual supported dwell");
    }

    @Test void realChipApexCannotSpendGroundSettlementDwell() throws Exception {
        var fixture = launch("sonic", 0); fixture.stepIdleFrames(120);
        var course = checkpointCourse(fixture);
        for (int i = 0; i < 75; i++) fixture.stepFrame(true, false, false, false, false);
        commit(fixture, 60); fixture.stepIdleFrames(30);
        boolean nearApex = false;
        for (int row = 0; row < 180 && value(shotState(), "stage").toString().equals("WATCH"); row++) {
            var before = course.ball();
            fixture.stepIdleFrames(1);
            var after = course.ball();
            if (after.airborne() && Math.abs(after.ySpeed()) <= 0x80) {
                nearApex = true;
                assertFalse(after.floorSupport());
                assertEquals("WATCH", value(shotState(), "stage").toString(), "airborne low speed is not settlement");
                assertNotNull(value(matchState(), "pending"));
                assertTrue(after.rolling(), "chip remains curled at apex");
            }
            if (before.airborne() && after.airborne()
                    && !value(shotState(), "stage").toString().equals("WATCH"))
                assertNotEquals("SETTLED", value(value(matchState(), "lastResolved"), "outcome").toString(),
                        "unsupported row cannot resolve as settled; damage/loss remains a valid penalty");
        }
        assertTrue(nearApex, "real ROM chip must cross its apex within the bounded probe");
    }

    @Test void actualEmeraldHillSpringKeepsGolfCurledAcrossTwoNativeBounces() throws Exception {
        var fixture = launch("sonic", 0); fixture.stepIdleFrames(120);
        var course = checkpointCourse(fixture);
        // Obj41 type extraction is shipped S2: (subtype >> 3) & $E.
        // Select a real upward ROM placement, not a fabricated dynamic spring.
        var spring = GameServices.level().getCurrentLevel().getObjects().stream()
                .filter(spawn -> spawn.objectId() == 0x41 && ((spawn.subtype() >> 3) & 0xE) == 0)
                .findFirst().orElseThrow(() -> new AssertionError("EHZ1 ROM has no upward spring"));
        commit(fixture, 1); fixture.stepIdleFrames(30);
        var sprite = GameServices.camera().getFocusedSprite();
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(sprite, spring.x());
        com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(sprite, spring.y() - 48);
        sprite.setAir(true); sprite.setOnObject(false);
        sprite.setXSpeed((short) 0); sprite.setYSpeed((short) 0x100); sprite.setGSpeed((short) 0);
        sprite.updateSensors(sprite.getX(), sprite.getY());
        GameServices.camera().setX((short) Math.max(0, spring.x() - 160));
        GameServices.camera().setY((short) Math.max(0, spring.y() - 112));
        int impulses = 0; boolean descending = false;
        int expectedImpulse = (spring.subtype() & 2) == 0 ? -0x1000 : -0xA00;
        for (int row = 0; row < 420 && impulses < 2; row++) {
            var before = course.ball();
            fixture.stepIdleFrames(1);
            var after = course.ball();
            assertEquals("WATCH", value(shotState(), "stage").toString(), "spring flight/contact must not prematurely settle");
            assertTrue(after.rolling(), "native spring and landing must preserve curl");
            if (after.ySpeed() < -0x800 && before.ySpeed() >= 0) {
                assertTrue(Math.abs(after.ySpeed() - expectedImpulse) <= 0x40,
                        "observe actual red/yellow native spring impulse, not a generic falling bounce");
                if (impulses == 1) assertTrue(descending, "second impulse follows ascent, apex and native return contact");
                impulses++;
            }
            if (impulses == 1 && after.ySpeed() > 0) descending = true;
        }
        assertEquals(2, impulses, "ROM placement must trigger and re-trigger within bounded native rows");
    }

}
