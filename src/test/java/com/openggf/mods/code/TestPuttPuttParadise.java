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

    @Test void hostFocusAndKeyboardPauseFreezeControlledRowsAndFrameStepAdmitsOne() throws Exception {
        var fixture = launch("sonic", 0); fixture.stepIdleFrames(120);
        var input = new com.openggf.control.InputHandler();
        var loop = new com.openggf.GameLoop(input);
        loop.setGameplayMode(fixture.runtime()); loop.setGameMode(GameMode.LEVEL);
        var aimCourse = checkpointCourse(fixture).playerState(); var aimMeter = shotState();
        loop.pause();
        for (int row = 0; row < 20; row++) loop.step();
        assertEquals(aimCourse, checkpointCourse(fixture).playerState());
        assertEquals(aimMeter, shotState(), "focus pause freezes creator AIM rows too");
        loop.resume();
        commit(fixture, 60); fixture.stepIdleFrames(32);
        assertEquals("WATCH", value(shotState(), "stage").toString());
        var pausedCourse = checkpointCourse(fixture).playerState(); var pausedMeter = shotState();
        loop.pause();
        for (int row = 0; row < 20; row++) loop.step();
        assertEquals(pausedCourse, checkpointCourse(fixture).playerState());
        assertEquals(pausedMeter, shotState());
        loop.resume();
        int pauseKey = GameServices.configuration().getInt(SonicConfiguration.PAUSE_KEY);
        input.handleKeyEvent(pauseKey, org.lwjgl.glfw.GLFW.GLFW_PRESS); loop.step();
        input.handleKeyEvent(pauseKey, org.lwjgl.glfw.GLFW.GLFW_RELEASE); loop.step();
        assertTrue(loop.isUserPaused(), "configured keyboard pause remains available in controlled modes");
        assertEquals(pausedCourse, checkpointCourse(fixture).playerState());
        int frameKey = GameServices.configuration().getInt(SonicConfiguration.FRAME_STEP_KEY);
        input.handleKeyEvent(frameKey, org.lwjgl.glfw.GLFW.GLFW_PRESS); loop.step();
        input.handleKeyEvent(frameKey, org.lwjgl.glfw.GLFW.GLFW_RELEASE); loop.step();
        var stepped = checkpointCourse(fixture).playerState();
        assertNotEquals(pausedCourse, stepped, "frame step admits one native WATCH frame");
        for (int row = 0; row < 20; row++) loop.step();
        assertEquals(stepped, checkpointCourse(fixture).playerState(), "released frame key cannot keep advancing");
        loop.toggleUserPause(); loop.step();
        assertNotEquals(stepped, checkpointCourse(fixture).playerState());
    }

    @Test void escapeReturnFadeCompletesWhileTheCreatorHoldsAim() throws Exception {
        var fixture = launch("sonic", 0); fixture.stepIdleFrames(120);
        var input = new com.openggf.control.InputHandler();
        var loop = new com.openggf.GameLoop(input);
        loop.setGameplayMode(fixture.runtime()); loop.setGameMode(GameMode.LEVEL);
        var before = checkpointCourse(fixture).playerState(); var meter = shotState();
        var returned = new java.util.concurrent.atomic.AtomicInteger();
        var handler = com.openggf.GameLoop.class.getDeclaredMethod("setReturnToMasterTitleHandler", Runnable.class);
        handler.setAccessible(true); handler.invoke(loop, (Runnable) returned::incrementAndGet);
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        for (int row = 0; row < 240 && returned.get() == 0; row++) loop.step();
        assertEquals(1, returned.get(), "host fade completes independently of creator HOLD");
        assertEquals(before, checkpointCourse(fixture).playerState());
        assertEquals(meter, shotState(), "return fade cannot commit a shot or advance its meter");
    }

    @Test void duckEntryUsesItsOwnClockAndPauseFreezesThePose() throws Exception {
        var fixture = launch("sonic", 0); fixture.stepIdleFrames(180);
        var poseMethod = mode().getClass().getDeclaredMethod("pose"); poseMethod.setAccessible(true);
        fixture.stepFrame(false, false, false, false, true);
        var entry = (com.openggf.game.presentation.PlayerPresentationPose) poseMethod.invoke(mode());
        assertEquals(com.openggf.game.presentation.PlayerPresentationPose.Kind.DUCK, entry.kind());
        assertEquals(0, entry.tick(), "a fresh duck starts with the native entry frame, not course age");
        fixture.stepIdleFrames(4);
        var playing = (com.openggf.game.presentation.PlayerPresentationPose) poseMethod.invoke(mode());
        assertEquals(4, playing.tick());
        var input = new com.openggf.control.InputHandler();
        var start = com.openggf.control.LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(0, 0, 0, 0, true, true),
                com.openggf.control.PlayerInputState.neutral());
        input.setLogicalOverride(start);
        com.openggf.game.mode.ControlledFrameRuntime.step(fixture.runtime(), input, start);
        var paused = (com.openggf.game.presentation.PlayerPresentationPose) poseMethod.invoke(mode());
        fixture.stepIdleFrames(20);
        assertEquals(paused, poseMethod.invoke(mode()), "pause holds the native pose animation");
    }

    @Test void artifactPassesNormalSdkPackaging() throws Exception {
        assertEquals(0, GgfModCli.run(new String[]{"package", "--input", temp.resolve("classes").toString(),
                "--out", temp.resolve("validated.jar").toString()}, System.out));
    }

    private record TitleTile(int id, int palette, float x, float y, float width, float height) { }
    private static final class TitleGraphics extends com.openggf.graphics.GraphicsManager {
        final int width;
        final Map<Integer, com.openggf.level.Pattern> patterns = new HashMap<>();
        final Map<Integer, com.openggf.level.Palette> palettes = new HashMap<>();
        final List<TitleTile> tiles = new ArrayList<>();
        int paletteUploads;
        TitleGraphics(int width) { this.width = width; }
        @Override public boolean isHeadlessMode() { return false; }
        @Override public int getProjectionWidth() { return width; }
        @Override public void cachePatternTexture(com.openggf.level.Pattern pattern, int id) { patterns.put(id, pattern); }
        @Override public void cachePaletteTexture(com.openggf.level.Palette palette, int id) {
            palettes.put(id, palette); paletteUploads++;
        }
        @Override public void renderPatternWithId(int id, com.openggf.level.PatternDesc desc, int x, int y) {
            renderPatternWithIdScaled(id, desc, x, y, 8, 8);
        }
        @Override public void renderPatternWithIdScaled(int id, com.openggf.level.PatternDesc desc,
                                                       float x, float y, float w, float h) {
            tiles.add(new TitleTile(id, desc.getPaletteIndex(), x, y, w, h));
        }
        @Override public void registerCommand(com.openggf.graphics.GLCommandable command) { }
    }

    @ParameterizedTest @CsvSource({"320", "352", "400", "528", "800"})
    void titleRemixesRomArtAcrossWidthsAndRestoresPalettesOnReturn(int width) throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        var graphics = new TitleGraphics(width);
        try (var services = org.mockito.Mockito.mockStatic(GameServices.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            services.when(GameServices::graphics).thenReturn(graphics);
            var module = (GameModule) loader.loadClass("paradise.GolfModule").getConstructor(GameModule.class)
                    .newInstance(new com.openggf.game.sonic2.Sonic2GameModule());
            var title = module.getTitleScreenProvider();
            title.initialize(); title.draw();
            assertEquals(Set.of(0, 1, 2, 3), graphics.palettes.keySet(), "all four original title palettes");
            assertTrue(graphics.patterns.size() > 700, "ROM title/background and character banks are decoded");
            assertTrue(graphics.tiles.stream().anyMatch(t -> t.id() >= 0x70000 && t.palette() == 0), "Sonic portrait");
            assertTrue(graphics.tiles.stream().anyMatch(t -> t.id() >= 0x70000 && t.palette() == 1), "Tails portrait");
            assertTrue(graphics.tiles.stream().anyMatch(t -> t.id() < 0x70000 && t.palette() == 3), "winged emblem");
            assertTrue(graphics.tiles.stream().anyMatch(t -> t.id() < 0x70000 && t.palette() == 2 && t.x() == width - 8),
                    "ROM landscape covers the full viewport");
            for (var tile : graphics.tiles) {
                assertTrue(graphics.patterns.containsKey(tile.id()), "drawn tile has ROM-backed cached art");
                assertTrue(tile.x() >= 0 && tile.y() >= 0 && tile.x() + tile.width() <= width
                        && tile.y() + tile.height() <= 224, "title tiles remain inside the viewport");
            }
            assertEquals(4, graphics.paletteUploads);
            title.draw();
            assertEquals(4, graphics.paletteUploads, "steady menu does not re-upload palettes every frame");
            // A course replaces palette contents; another visit must upload the original title set again.
            graphics.palettes.clear(); title.reset(); title.initialize(); title.draw();
            assertEquals(8, graphics.paletteUploads);
            assertEquals(Set.of(0, 1, 2, 3), graphics.palettes.keySet());
            assertEquals(TitleScreenProvider.State.ACTIVE, title.getState());
            assertNull(title.getClass().getMethod("getSelection").invoke(title), "rendering never launches a game");
        }
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
        var original = new com.openggf.game.sonic2.Sonic2GameModule()
                .createGame(GameServices.rom().getRom()).loadLevel(act);
        var expected = original.getObjects().stream()
                .filter(s -> s.objectId() != 0x0D && s.objectId() != 0x3E).toList();
        var actual = GameServices.level().getCurrentLevel().getObjects();
        assertEquals(expected.size(), actual.size(), "only ROM end markers are removed");
        int tagged = 0;
        for (int index = 0; index < expected.size(); index++) {
            var nativeSpawn = expected.get(index); var golfSpawn = actual.get(index);
            var nativeFields = new com.openggf.level.objects.ObjectSpawn(golfSpawn.x(), golfSpawn.y(),
                    golfSpawn.objectId(), golfSpawn.subtype(), golfSpawn.renderFlags(), golfSpawn.respawnTracked(),
                    golfSpawn.rawYWord(), golfSpawn.layoutIndex());
            assertEquals(nativeSpawn, nativeFields, "preserve ROM placement fields and table order at " + index);
            if (nativeSpawn.objectId() == 0x41 && ((nativeSpawn.subtype() >> 3) & 0xE) == 0) {
                assertEquals("putt-putt-paradise", golfSpawn.ownerModId());
                assertEquals("putt-putt-paradise:up-spring", golfSpawn.objectKey()); tagged++;
            } else assertEquals(nativeSpawn, golfSpawn, "other native objects remain untagged");
        }
        assertTrue(tagged > 0, "each EHZ act includes upward ROM springs");
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
        var plan = registrations();
        var boundary = new ModFaultBoundary(Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> { });
        effective = new ModBackedGamePatch(plan, boundary).apply(effective, null);
        for (var patch : plan.explicitPatches()) effective = patch.apply(effective, null);
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, act).build();
        // Existing route/parity fixtures deliberately retain automatic turn completion.
        configureRewinds("PRACTICE", character, "tails", act, aspect, 0, 1);
        return fixture;
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
    @CsvSource({"sonic,0,1,0,NATIVE_4_3,36,5", "sonic,45,1,0,NATIVE_4_3,25,-19",
            "tails,75,-1,0,SUPER_32_9,-9,-33", "sonic,0,1,224,NATIVE_4_3,29,-21",
            "tails,45,-1,224,SUPER_32_9,-35,2", "sonic,0,-1,32,NATIVE_4_3,-29,-21",
            "tails,15,1,0,WIDE_16_9,35,-7", "sonic,90,1,0,NATIVE_4_3,2,-30",
            "tails,90,-1,0,SUPER_32_9,-2,-34"})
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
    @ParameterizedTest @CsvSource({"sonic,100,1", "sonic,-100,1", "sonic,100,-1", "sonic,-100,-1",
            "tails,100,1", "tails,-100,1", "tails,100,-1", "tails,-100,-1"})
    void hitPointPreviewAndReleasedChipShareSpinVelocity(String character, int spin, int facing) throws Exception {
        var f=launch(character,0,"SUPER_32_9"); f.stepIdleFrames(120);
        for(int i=0;i<45;i++) f.stepFrame(true,false,false,false,false);
        f.stepFrame(false,false,facing<0,facing>0,false);
        int x=f.sprite().getCentreX()-f.camera().getX(), y=f.sprite().getCentreY()-f.camera().getY();
        int radius=f.sprite().getYRadius()-f.sprite().getRollYRadius();
        pressA(f); assertEquals("SPIN",value(shotState(),"stage").toString());
        for(int i=0;i<20;i++) f.stepFrame(spin>0,spin<0,false,false,false);
        assertEquals(spin,value(shotState(),"targetSpin"));
        var targetDots=guideDots(); assertFalse(targetDots.isEmpty());
        int tangent=spin>0?3072:1086;
        assertEquals(x+(int)Math.round(facing*tangent/256.0*3),value(targetDots.getFirst(),"x"));
        assertEquals(y+radius-24,value(targetDots.getFirst(),"y"));
        f.stepIdleFrames(spin>0?10:70); pressA(f);
        assertEquals(spin,value(shotState(),"spin"));
        f.stepIdleFrames(60);
        var powerDots=guideDots();
        assertEquals(value(targetDots.getFirst(),"x"),value(powerDots.getFirst(),"x"));
        assertEquals(value(targetDots.getFirst(),"y"),value(powerDots.getFirst(),"y"));
        pressA(f); f.stepIdleFrames(30);
        assertEquals(tangent*facing,(int)f.sprite().getXSpeed(),"release uses the same spin-adjusted tangent");
        assertEquals(spin,value(value(shotState(),"shot"),"spin"));
    }

    @ParameterizedTest @CsvSource({"sonic,100", "sonic,-100", "tails,100", "tails,-100"})
    void firstLandingSpinIsAppliedOnceAndReplaysFromFlight(String character, int spin) throws Exception {
        var f=launch(character,0); f.stepIdleFrames(120);
        for(int i=0;i<45;i++) f.stepFrame(true,false,false,false,false);
        pressA(f); f.stepIdleFrames(spin>0?30:90); pressA(f); f.stepIdleFrames(30); pressA(f);
        f.stepIdleFrames(30); assertTrue(f.sprite().getAir());
        var registry=f.runtime().getRewindRegistry(); var before=registry.capture();
        var rows=new ArrayList<com.openggf.game.mode.CourseControl.PlayerState>();
        int landing=-1;
        for(int i=0;i<100;i++) {
            f.stepIdleFrames(1); var ball=checkpointCourse(f).playerState(); rows.add(ball);
            if(ball.floorSupport()&&!ball.airborne()) { landing=i; break; }
        }
        assertTrue(landing>=0,"chip reaches real EHZ floor");
        assertFalse((boolean)value(((com.openggf.game.rewind.RewindSnapshottable<?>)mode()).capture(),"landingSpinPending"));
        if(spin<0) assertTrue(f.sprite().getGSpeed()<0,"backspin reverses the first landing carry");
        else assertTrue(f.sprite().getGSpeed()>0x800,"topspin adds forward landing carry");
        f.stepIdleFrames(1); rows.add(checkpointCourse(f).playerState());
        if(spin>0) assertTrue(Math.abs(f.sprite().getGSpeed())<0xC00,"next row is native rolling, without another impulse");
        var after=registry.capture(); registry.restore(before);
        for(var expected:rows) { f.stepIdleFrames(1); assertEquals(expected,checkpointCourse(f).playerState(),"spin flight/landing replay"); }
        var replay=registry.capture();
        for(var key:after.entries().keySet()) assertEquals(List.of(),
                com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,after.get(key),replay.get(key)),key);
    }

    private void pressA(HeadlessTestFixture fixture) { fixture.stepFrame(false, false, false, false, true); }
    /** Incoming-golfer confirmation at a competition handoff, then the release it requires. */
    private void ready(HeadlessTestFixture fixture) throws Exception {
        assertEquals("WAITING", value(value(mode(), "readinessState"), "phase").toString(), "turn is held for readiness");
        pressA(fixture); fixture.stepIdleFrames(1);
        assertEquals("NONE", value(value(mode(), "readinessState"), "phase").toString(), "fresh A confirms");
    }
    private Object readiness(String accessor) throws Exception { return value(value(mode(), "readinessState"), accessor); }
    private void input(HeadlessTestFixture fixture, int held, int actions, int pressed) {
        var player = com.openggf.control.PlayerInputState.of(held, 0, actions, pressed, false, false);
        com.openggf.game.mode.ControlledFrameRuntime.step(fixture.runtime(), new com.openggf.control.InputHandler(),
                com.openggf.control.LogicalInputSnapshot.ofPlayers(player, com.openggf.control.PlayerInputState.neutral()));
    }
    private static void assertCourseUnchanged(com.openggf.game.rewind.CompositeSnapshot before,
                                              com.openggf.game.rewind.CompositeSnapshot after, String message) {
        for (var entry : before.entries().entrySet())
            assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(),
                    after.get(entry.getKey())), message + " " + entry.getKey());
    }

    @ParameterizedTest @CsvSource({"sonic,sonic", "tails,sonic"})
    void handoffHoldsTheCourseUntilTheIncomingGolfersOwnFreshPress(String one, String two) throws Exception {
        final int a = com.openggf.control.InputActionMasks.ACTION_A;
        var f = launch(one, 0); local(one, two); f.stepIdleFrames(120);
        var registry = f.runtime().getRewindRegistry();
        assertEquals("WAITING", readiness("phase").toString(), "a match introduces its first golfer");
        assertEquals(0, readiness("owner"));
        assertEquals("HANDOFF", value(value(mode(), "hudShotView"), "stage"));
        assertTrue(value(value(mode(), "hudShotView"), "hint").toString().startsWith("SPACE"), "P1's own A binding");
        var before = registry.captureCourse();
        f.stepIdleFrames(30);
        assertCourseUnchanged(before, registry.captureCourse(), "a waiting golfer holds the course");
        // Start's menu cannot confirm: its Resume press must be released first.
        tick(f, 0, 0, true); tick(f, 0, 0, false); input(f, 0, a, a);
        assertEquals("WAITING", readiness("phase").toString(), "the menu's A is not a ready press");
        input(f, 0, 0, 0); input(f, 0, a, a);
        assertEquals("NONE", readiness("phase").toString());
        input(f, 0, 0, 0);
        commit(f, 1);
        int rows = 0;
        // Hold A through the shot and its automatic settlement into P2's turn.
        while ((int) value(matchState(), "activePlayer") == 0 && rows++ < 400) input(f, 0, a, 0);
        assertEquals(1, value(matchState(), "activePlayer"), "settlement still passes the turn automatically");
        assertEquals("WAITING", readiness("phase").toString());
        assertEquals(1, readiness("owner"));
        assertEquals(two, GameServices.camera().getFocusedSprite().getCode());
        String hint = value(value(mode(), "hudShotView"), "hint").toString();
        assertTrue(hint.startsWith("RIGHT SHIFT"), "P2's prompt names P2's A binding: " + hint);
        var waiting = registry.captureCourse();
        for (int i = 0; i < 10; i++) input(f, 0, a, a);
        assertEquals("WAITING", readiness("phase").toString(), "an A held across the handoff cannot confirm");
        assertCourseUnchanged(waiting, registry.captureCourse(), "held handoff");
        input(f, 0, 0, 0); input(f, 0, a, a);
        assertEquals("NONE", readiness("phase").toString(), "a released then fresh A confirms");
        for (int i = 0; i < 10; i++) input(f, 0, a, 0);
        assertEquals("AIM", value(shotState(), "stage").toString(), "the confirming press cannot also start the shot");
        assertNull(value(matchState(), "pending"));
        input(f, 0, 0, 0); input(f, 0, a, a);
        assertEquals("POWER", value(shotState(), "stage").toString(), "a fresh press after release starts P2's putt");
    }

    @Test void zoneMusicStartsAtCourseEntryAndNeverRestartsDuringShotsOrRestores() throws Exception {
        var f = launch("sonic", 0); configureRewinds("PRACTICE", "sonic", "tails", 0, "NATIVE_4_3", 3, 1);
        var profile = f.runtime().getWorldSession().getGameModule().getLevelInitProfile();
        var audio = GameServices.audio(); int loaded = audio.commandTimeline().entryCount();
        assertTrue(profile.isLevelMusicPublicationPending(), "S2 level load arms the Level_PlayBgm countdown");
        f.stepIdleFrames(120);
        assertFalse(profile.isLevelMusicPublicationPending(), "course entry completes the ROM-timed level music request");
        var entryMusic = commandsSince(loaded, com.openggf.audio.rewind.AudioCommand.PlayMusic.class);
        // The lie-setup restore once requested the music at row 0 and the countdown then restarted
        // it: an audible burst, a gap, and the intro again. The countdown owns the only request.
        assertEquals(1, entryMusic.size(), "course entry requests the zone music exactly once: " + entryMusic);
        int entries = audio.commandTimeline().entryCount();
        commit(f, 55); f.stepIdleFrames(45);
        assertEquals("WATCH", value(shotState(), "stage").toString());
        tick(f, 0, 0, true); tick(f, 0, 0, false);
        tick(f, 2, 0, false); tick(f, 0, com.openggf.control.InputActionMasks.ACTION_A, false);
        f.stepIdleFrames(100);
        assertNull(value(matchState(), "pending"), "whole-shot restore completed");
        commit(f, 55); f.stepIdleFrames(60);
        var music = commandsSince(entries, com.openggf.audio.rewind.AudioCommand.PlayMusic.class);
        assertEquals(List.of(), music, "shots and course restores must not restart the zone music");
        var stops = commandsSince(entries, com.openggf.audio.rewind.AudioCommand.StopAllSfx.class);
        // A driver-wide SFX stop after any sound effect gated the S2 presentation output, music
        // included, until the next SFX: every turn switch and rewind went silent while aiming.
        assertEquals(List.of(), stops, "course restores and whole-shot rewinds leave the sound driver running");
    }

    /** Recorded commands of one kind from an absolute timeline index (indices survive pruning). */
    private static List<com.openggf.audio.rewind.AudioTimelineEntry> commandsSince(int from, Class<?> kind) {
        var timeline = GameServices.audio().commandTimeline();
        return java.util.stream.IntStream.range(Math.max(from, timeline.firstRetainedEntryIndex()), timeline.entryCount())
                .mapToObj(timeline::entryAt).filter(e -> kind.isInstance(e.command())).toList();
    }

    @Test void rewindRetryKeepsTheConfirmedTurnWithoutAnotherHandoff() throws Exception {
        var f = launch("sonic", 0); configureRewinds("LOCAL", "sonic", "tails", 0, "NATIVE_4_3", 3, 1);
        f.stepIdleFrames(120); ready(f);
        var saved = registry(f).capture();
        commit(f, 55); f.stepIdleFrames(45);
        assertEquals("WATCH", value(shotState(), "stage").toString());
        tick(f, 0, 0, true); tick(f, 0, 0, false);
        tick(f, 2, 0, false); tick(f, 0, com.openggf.control.InputActionMasks.ACTION_A, false);
        f.stepIdleFrames(100);
        assertNull(value(matchState(), "pending"), "rewound shot refunded");
        assertEquals(0, value(matchState(), "activePlayer"), "the retry keeps the turn");
        assertEquals("NONE", readiness("phase").toString(), "a retry is not a handoff");
        registry(f).restore(saved);
        assertEquals("NONE", readiness("phase").toString(), "readiness is captured with the mode");
    }
    private static com.openggf.game.rewind.RewindRegistry registry(HeadlessTestFixture f) { return f.runtime().getRewindRegistry(); }
    private void commit(HeadlessTestFixture fixture, int meterTicks) throws Exception {
        pressA(fixture);
        if (value(shotState(), "stage").toString().equals("SPIN")) {
            fixture.stepIdleFrames(60); // Neutral hit point; preserves existing route departure.
            pressA(fixture);
        }
        fixture.stepIdleFrames(meterTicks);
        pressA(fixture);
    }

    @ParameterizedTest @CsvSource({"sonic,0", "tails,0", "sonic,45", "tails,45",
            "sonic,85", "tails,85", "sonic,90", "tails,90"})
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
        if (elevation > 75) {
            assertTrue(fixture.sprite().getCentreX() >= x);
            assertTrue(fixture.sprite().getXSpeed() > 0, "steep chip retains a subpixel forward component");
        } else assertTrue(fixture.sprite().getCentreX() > x);
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
        configureRewinds("LOCAL", one, two, 0, "NATIVE_4_3", 0, 1);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void configureRewinds(String modeName, String one, String two, int act, String aspect, int perHole, int perTurn) throws Exception {
        var selectionClass = loader.loadClass("paradise.ui.GolfMenu$Selection");
        Class modeClass = loader.loadClass("paradise.ui.GolfMenu$Mode");
        Class charClass = loader.loadClass("paradise.ui.GolfMenu$CharacterChoice");
        Class viewportClass = loader.loadClass("paradise.ui.GolfMenu$Viewport");
        Class rulesClass = loader.loadClass("paradise.model.RewindAllowance$Rules");
        Object choice = selectionClass.getConstructor(modeClass, charClass, charClass, int.class, String.class,
                int.class, viewportClass, rulesClass).newInstance(Enum.valueOf(modeClass, modeName),
                Enum.valueOf(charClass, one.toUpperCase(Locale.ROOT)), Enum.valueOf(charClass, two.toUpperCase(Locale.ROOT)),
                act, "127.0.0.1", 20502, Enum.valueOf(viewportClass, aspect), rulesClass.getConstructor(int.class, int.class).newInstance(perHole, perTurn));
        mode().getClass().getMethod("configure", selectionClass).invoke(mode(), choice);
    }

    @ParameterizedTest @CsvSource({"sonic,0", "sonic,1", "tails,0", "tails,1"})
    void shotRewindRestoresEveryCourseOwnerAndRefundsStrokeOnce(String character, int act) throws Exception {
        var f = launch(character, act); configureRewinds("PRACTICE", character, "tails", act, "NATIVE_4_3", 3, 1);
        f.stepIdleFrames(120); var registry = f.runtime().getRewindRegistry();
        var before = registry.captureCourse(); var start = checkpointCourse(f).playerState();
        commit(f, 55); f.stepIdleFrames(45);
        assertNotEquals(start.x(), checkpointCourse(f).playerState().x(), "shot really travelled");
        long retired = (long) value(value(value(matchState(), "pending"), "id"), "shotSequence");
        var input = new com.openggf.control.InputHandler();
        input.handleKeyEvent(GameServices.configuration().getInt(SonicConfiguration.LIVE_REWIND_KEY), org.lwjgl.glfw.GLFW.GLFW_PRESS);
        var loop = new com.openggf.GameLoop(input); loop.setGameplayMode(f.runtime()); loop.setGameMode(GameMode.LEVEL);
        int rows = 0;
        do {
            loop.step(); rows++;
            if (rows == 1) {
                assertTrue(loop.liveRewindEffectIntensity() > 0, "creator shot rewind must request the production VHS effect");
                assertTrue(loop.liveRewindEffectSpeed() > 0, "creator rewind supplies its visual playback speed");
                var ambient = new com.openggf.GameLoop(new com.openggf.control.InputHandler());
                ambient.setGameMode(GameMode.LEVEL);
                assertTrue(ambient.liveRewindEffectIntensity() > 0, "ambient gameplay binding requests the same effect");
                loop.pause(); assertEquals(0, loop.liveRewindEffectIntensity(), "window pause holds the creator effect");
                loop.resume(); assertTrue(loop.liveRewindEffectIntensity() > 0);
                loop.toggleUserPause(); assertEquals(0, loop.liveRewindEffectIntensity(), "host keyboard pause holds the creator effect");
                loop.toggleUserPause();
            }
        } while (value(matchState(), "pending") != null && rows < 100);
        assertTrue(rows < 100, "rewind completion is bounded");
        assertEquals(0, loop.liveRewindEffectIntensity(), "restored lie must stop the creator effect");
        assertEquals("AIM", value(shotState(), "stage").toString());
        assertEquals(start, checkpointCourse(f).playerState());
        for (var entry : before.entries().entrySet()) assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                entry.getKey(), entry.getValue(), registry.captureCourse().get(entry.getKey())), "restored " + entry.getKey());
        Object saved = ((com.openggf.game.rewind.RewindSnapshottable<?>) mode()).capture();
        assertEquals(1, ((List<?>) value(value(saved, "allowance"), "usedPerHole")).get(act * 2));
        assertEquals(0, ((List<?>) value(matchState(), "golfers")).stream().mapToInt(g -> {
            try { return (int) value(g, "total"); } catch (Exception e) { throw new RuntimeException(e); }
        }).sum());
        assertEquals(1L, value(matchState(), "turnSequence"));
        assertTrue((long) value(matchState(), "nextShotSequence") > retired);
        loop.step(); assertEquals(value(saved, "allowance"), value(((com.openggf.game.rewind.RewindSnapshottable<?>)mode()).capture(), "allowance"), "held R does not consume twice");
        input.handleKeyEvent(GameServices.configuration().getInt(SonicConfiguration.LIVE_REWIND_KEY), org.lwjgl.glfw.GLFW.GLFW_RELEASE); loop.step();
        commit(f, 55); f.stepIdleFrames(40);
        input.handleKeyEvent(GameServices.configuration().getInt(SonicConfiguration.LIVE_REWIND_KEY), org.lwjgl.glfw.GLFW.GLFW_PRESS); loop.step();
        assertNotNull(value(matchState(), "pending"), "one rewind per turn survives retry");
    }

    @Test void reverseViewsStayBoundedAndAdvanceThroughOldFramesWithFreshRevisions() throws Exception {
        Class<?> replayClass = loader.loadClass("paradise.presentation.ShotReplay"); Object history = replayClass.getConstructor().newInstance();
        var record = replayClass.getMethod("record", int.class, com.openggf.game.presentation.ScenePresentationFrame.class);
        var wants = replayClass.getMethod("wantsSample", int.class);
        com.openggf.game.presentation.ScenePresentationFrame last = null;
        for (int tick = 0; tick <= 3600; tick++) {
            last = new com.openggf.game.presentation.ScenePresentationFrame(0, 0, 320, 224, tick, 0, 0,
                    new int[16], List.of(), List.of());
            if ((boolean) wants.invoke(history, tick)) record.invoke(history, tick, last);
        }
        replayClass.getMethod("begin", int.class, com.openggf.game.presentation.ScenePresentationFrame.class).invoke(history, 3600, last);
        var saved = replayClass.getMethod("snapshot").invoke(history);
        var samples = (List<?>) value(saved, "samples"); assertTrue(samples.size() <= 128);
        assertEquals(0, value(samples.getFirst(), "tick")); assertEquals(3600, value(samples.getLast(), "tick"));
        long bytes = 0; for (var sample : samples) bytes += ((byte[]) value(sample, "scene")).length;
        assertTrue(bytes <= 8 * 1024 * 1024);
        int previous = 3601, rows = 0;
        boolean done;
        do {
            done = (boolean) replayClass.getMethod("step").invoke(history);
            var frame = (com.openggf.game.presentation.ScenePresentationFrame) replayClass.getMethod("frame", long.class).invoke(history, (long) ++rows);
            assertTrue(frame.cameraX() <= previous, "source view reverses while revisions advance");
            assertEquals(rows, frame.revision()); previous = frame.cameraX();
        } while (!done && rows < 100);
        assertEquals(0, previous); assertEquals(90, rows);
        replayClass.getMethod("restore", saved.getClass()).invoke(history, saved);
        assertEquals(saved, replayClass.getMethod("snapshot").invoke(history), "history restore retains immutable recording and cursor");
        replayClass.getMethod("clear").invoke(history);
        var tile = new com.openggf.game.presentation.ScenePresentationFrame.Tile(
                com.openggf.game.presentation.ScenePresentationFrame.Layer.FOREGROUND,
                new com.openggf.game.presentation.ScenePresentationFrame.ArtReference("terrain",0),
                0,false,false,false,0,0,8,8,0,8,0,255);
        var dense = new com.openggf.game.presentation.ScenePresentationFrame(0,0,800,224,0,0,0,
                new int[16],Collections.nCopies(8192,tile),List.of());
        for(int tick=0;tick<300;tick++) if((boolean)wants.invoke(history,tick))record.invoke(history,tick,dense);
        Object denseSaved=replayClass.getMethod("snapshot").invoke(history);
        long denseBytes=0;for(var sample:(List<?>)value(denseSaved,"samples"))denseBytes+=((byte[])value(sample,"scene")).length;
        assertTrue(denseBytes<=8*1024*1024,"dense views exercise actual byte budget");
        assertTrue((int)value(denseSaved,"stride")>3,"byte pressure coarsens sampling before sample-count cap");
    }

    @Test void recordedSceneHistoryAndModeAllowanceRestoreForForwardReplay() throws Exception {
        var f=launch("sonic",0);configureRewinds("PRACTICE","sonic","tails",0,"NATIVE_4_3",3,3);f.stepIdleFrames(120);
        commit(f,55);f.stepIdleFrames(36);var registry=f.runtime().getRewindRegistry();
        var saved=registry.capture();f.stepIdleFrames(8);var expected=registry.capture();
        registry.restore(saved);f.stepIdleFrames(8);var replayed=registry.capture();
        for(var entry:expected.entries().entrySet())assertEquals(List.of(),com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                entry.getKey(),entry.getValue(),replayed.get(entry.getKey())),"forward history "+entry.getKey());
    }

    @ParameterizedTest @CsvSource({"0", "1"})
    void finishingShotPassesDirectlyToResultsWithRewindsRemaining(int act) throws Exception {
        var f = launch("sonic", act); configureRewinds("PRACTICE", "sonic", "tails", act, "NATIVE_4_3", 3, 3);
        f.stepIdleFrames(120);
        for (var shot : route(act, "NATIVE_4_3")) {
            shoot(f, shot[0], shot[1], shot[2]);
            if (value(matchState(), "status").toString().equals("COMPLETE")) break;
        }
        assertEquals("COMPLETE", value(matchState(), "status").toString(), "finish needs no acceptance press");
        assertNull(value(matchState(), "pending"));
    }

    @Test void settledAndPenaltyShotsPassTurnsAutomaticallyAndRejectLateRewind() throws Exception {
        var f = launch("sonic", 0); configureRewinds("LOCAL", "sonic", "tails", 0, "NATIVE_4_3", 5, 3);
        f.stepIdleFrames(120); ready(f); commit(f, 1); f.stepIdleFrames(100);
        assertEquals(1, value(matchState(), "activePlayer"), "settlement automatically opens player two's turn");
        assertNull(value(matchState(), "pending"));
        var before = ((com.openggf.game.rewind.RewindSnapshottable<?>) mode()).capture();
        var input = new com.openggf.control.InputHandler();
        input.handleKeyEvent(GameServices.configuration().getInt(SonicConfiguration.LIVE_REWIND_KEY), org.lwjgl.glfw.GLFW.GLFW_PRESS);
        var loop = new com.openggf.GameLoop(input); loop.setGameplayMode(f.runtime()); loop.setGameMode(GameMode.LEVEL);
        loop.step();
        assertEquals(1, value(matchState(), "activePlayer"), "late rewind cannot undo the preceding turn");
        assertEquals(value(before, "allowance"), value(((com.openggf.game.rewind.RewindSnapshottable<?>) mode()).capture(), "allowance"));
        f.stepIdleFrames(1); ready(f); commit(f, 55); f.stepIdleFrames(32); GameServices.camera().getFocusedSprite().setHurt(true); f.stepIdleFrames(1);
        assertEquals(0, value(matchState(), "activePlayer"), "penalty automatically passes the turn");
        var golfer = ((List<?>) value(matchState(), "golfers")).get(1);
        assertEquals(2, value(golfer, "total"), "one stroke plus one penalty");
    }

    @Test void pauseMenuCanRewindAnInFlightShotWithGenesisInputs() throws Exception {
        var f = launch("sonic", 0); configureRewinds("PRACTICE", "sonic", "tails", 0, "NATIVE_4_3", 3, 1);
        f.stepIdleFrames(120); var start = checkpointCourse(f).playerState();
        commit(f, 55); f.stepIdleFrames(45);
        assertEquals("WATCH", value(shotState(), "stage").toString());
        tick(f, 0, 0, true); tick(f, 0, 0, false);
        tick(f, 2, 0, false); tick(f, 0, com.openggf.control.InputActionMasks.ACTION_A, false);
        f.stepIdleFrames(100);
        assertEquals(start, checkpointCourse(f).playerState());
        assertNull(value(matchState(), "pending"));
        assertEquals(1L, value(matchState(), "turnSequence"));
    }
    @ParameterizedTest @CsvSource({"sonic,tails", "tails,sonic", "sonic,sonic", "tails,tails"})
    void alternateIndependentGolferWorldsAndRewindRoster(String one, String two) throws Exception {
        var fixture = launch(one, 0); local(one, two); fixture.stepIdleFrames(120);
        var registry = fixture.runtime().getRewindRegistry();
        var firstNeutral = registry.captureCourse(); var rewind = registry.capture();
        ready(fixture); commit(fixture, 55); fixture.stepIdleFrames(32);
        GameServices.camera().getFocusedSprite().setHurt(true); fixture.stepIdleFrames(1);
        assertEquals(1, value(matchState(), "activePlayer"));
        assertEquals(two, GameServices.camera().getFocusedSprite().getCode());
        assertEquals(firstNeutral.get("camera"), GameServices.camera().capture(),
                "the next golfer keeps the prepared view instead of disappearing under the HUD");
        assertHeldGolferVisible(fixture);
        fixture.stepIdleFrames(1); ready(fixture); commit(fixture, 55); fixture.stepIdleFrames(32);
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

    private void assertHeldGolferVisible(HeadlessTestFixture fixture) throws Exception {
        var before = fixture.runtime().getRewindRegistry().captureCourse();
        var clock = value(value(mode(), "capture"), "poseClock");
        var pose = new com.openggf.game.presentation.PlayerPresentationPose(
                (com.openggf.game.presentation.PlayerPresentationPose.Kind) value(clock, "kind"),
                (long) value(clock, "elapsed"), (int) value(shotState(), "direction"));
        var frame = GameServices.level().captureScene(12345, pose);
        assertTrue(frame.tiles().stream().anyMatch(t -> t.layer() ==
                com.openggf.game.presentation.ScenePresentationFrame.Layer.PLAYER),
                "A held turn must display the newly selected golfer without advancing physics");
        if (GameServices.camera().getFocusedSprite().getCode().equals("tails"))
            assertTrue(frame.tiles().stream().anyMatch(t -> t.art().recipe().equals("tail/tails")),
                    "Standing Tails must include his independently rendered tails");
        for (var entry : before.entries().entrySet())
            assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(),
                    fixture.runtime().getRewindRegistry().captureCourse().get(entry.getKey())),
                    "display does not advance the held world " + entry.getKey());
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
            if (readiness("phase").toString().equals("WAITING")) ready(f);
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
                "shot-panel transitions and release have distinct mode row stamps");
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
            titleTap(title, input, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN);
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
            GameServices.level().setRewindClassResolver(new ModClassResolver(runtime, getClass().getClassLoader()));
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
            // Materialize a real ROM spring through the production registry, then
            // force reconstruction to cover the ownership path used by rewind.
            var manager = GameServices.level().getObjectManager();
            var springSpawn = GameServices.level().getCurrentLevel().getObjects().stream()
                    .filter(s -> s.objectId() == 0x41 && ((s.subtype() >> 3) & 0xE) == 0)
                    .findFirst().orElseThrow();
            manager.reset(Math.max(0, springSpawn.x() - 160));
            var spring = manager.getActiveObjectForRewind(springSpawn);
            assertNotNull(spring);
            assertEquals("paradise.objects.GolfUpSpring", spring.getClass().getName());
            assertGolfCallbackOwner(manager, spring);
            var adapter = manager.rewindSnapshottable();
            var snapshot = adapter.capture();
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            try {
                adapter.restore(snapshot);
                var recreated = manager.getActiveObjectForRewind(springSpawn);
                assertNotSame(spring, recreated, "exercise fresh placed-object reconstruction");
                assertEquals(spring.getClass(), recreated.getClass());
                assertGolfCallbackOwner(manager, recreated);
            } finally { manager.setRewindInPlaceRestoreEnabledForTest(true); }
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

    private static void assertGolfCallbackOwner(com.openggf.level.objects.ObjectManager manager,
                                               com.openggf.level.objects.ObjectInstance spring) throws Exception {
        var dispatch = com.openggf.level.objects.ObjectManager.class.getDeclaredMethod("callObjectCallback",
                com.openggf.level.objects.ObjectInstance.class, java.util.function.Supplier.class);
        dispatch.setAccessible(true);
        assertNull(OwnerCallbackScope.current(), "ambient creator scope is clear");
        assertEquals("putt-putt-paradise", dispatch.invoke(manager, spring,
                (java.util.function.Supplier<String>) OwnerCallbackScope::current),
                "a placed golf spring dispatches through its owner fault boundary");
        assertNull(OwnerCallbackScope.current(), "creator scope does not leak after dispatch");
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
                    // The third A is now an extra WATCH press: both paths must ignore it.
                    aimRow == 1 || aimRow == 32 || aimRow == 63 ? 1 : 0,
                    aimRow == 12 || aimRow == 20, ""));
        }
        record Observed(com.openggf.game.mode.CourseControl.PlayerState ball, Object meter, Object ledger) { }
        var expected = new ArrayList<Observed>();
        var f = launch("sonic", 0);
        var input = new com.openggf.control.InputHandler();
        var loop = new com.openggf.GameLoop(input);
        loop.setGameplayMode(f.runtime()); loop.setGameMode(GameMode.LEVEL);
        com.openggf.debug.playback.Bk2FrameInput previous = null;
        for (var row : rows) {
            input.setLogicalOverride(com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(row, previous));
            loop.step(); previous = row;
            expected.add(new Observed(checkpointCourse(f).playerState(), shotState(), matchState()));
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
            var observed = new Observed(checkpointCourse(f).playerState(), shotState(), matchState());
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
                // Neutral chip contact after 60 ticks, then the original half-power shot.
                int actions = row == 621 || row == 682 || row == 713 ? com.openggf.control.InputActionMasks.ACTION_A : 0;
                var player = com.openggf.control.PlayerInputState.of(directions, directions, actions, actions, false, false);
                input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(player,
                        com.openggf.control.PlayerInputState.neutral()));
                var result = com.openggf.game.mode.ControlledFrameRuntime.step(f.runtime(), input, input.logical());
                assertNotEquals(com.openggf.LevelFrameResult.SETUP_ONLY, result);
                manager.recordExternalFrame(GameMode.LEVEL, false, input);
                input.update();
                if (row == 303 || row == 620 || row == 633 || row == 664 || row == 700 || row == 721 || row == 752)
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
        course.loadLevel(0, 1);
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
        var ball = course.playerState();
        var level = GameServices.level().getCurrentLevel();
        var before = fixture.runtime().getRewindRegistry().captureCourse();
        int audioEntries = GameServices.audio().commandTimeline().entryCount();
        assertThrows(IllegalArgumentException.class, () -> course.restore(checkpoint));
        assertSame(focused, GameServices.camera().getFocusedSprite(), "reject before roster replacement");
        assertSame(level, GameServices.level().getCurrentLevel(), "reject before level replacement");
        assertEquals(ball, course.playerState(), "position, velocity, character and camera remain unchanged");
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
        assertTrue(course.playerState().floorSupport(), "weak flat putt starts on the verified spawn floor");
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
        assertTrue(course.playerState().floorSupport());
        assertTrue(course.playerState().rolling());
        assertTrue(rows >= 19, "settlement requires actual supported dwell");
    }

    @Test void realChipApexCannotSpendGroundSettlementDwell() throws Exception {
        var fixture = launch("sonic", 0); fixture.stepIdleFrames(120);
        var course = checkpointCourse(fixture);
        for (int i = 0; i < 75; i++) fixture.stepFrame(true, false, false, false, false);
        commit(fixture, 60); fixture.stepIdleFrames(30);
        boolean nearApex = false;
        for (int row = 0; row < 180 && value(shotState(), "stage").toString().equals("WATCH"); row++) {
            var before = course.playerState();
            fixture.stepIdleFrames(1);
            var after = course.playerState();
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

    @ParameterizedTest @CsvSource({"sonic,1,false", "sonic,-1,false", "tails,1,false", "tails,-1,false",
            "sonic,1,true", "sonic,-1,true", "tails,1,true", "tails,-1,true"})
    void upwardRomSpringFiresFromEitherSideAndReplaysAfterRestore(String character, int facing,
            boolean arrivingFromAnotherSpring) throws Exception {
        var fixture = launch(character, 0); fixture.stepIdleFrames(120);
        var spring = GameServices.level().getCurrentLevel().getObjects().stream()
                .filter(spawn -> spawn.objectId() == 0x41 && ((spawn.subtype() >> 3) & 0xE) == 0)
                .findFirst().orElseThrow();
        commit(fixture, 1); fixture.stepIdleFrames(30);
        var sprite = fixture.sprite();
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(sprite, spring.x() - facing * 31);
        com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(sprite, spring.y() - 8);
        sprite.setAir(true); sprite.setOnObject(false); sprite.setPushing(false);
        if (arrivingFromAnotherSpring) sprite.setSpringing(15);
        sprite.setXSpeed((short) (facing * 0x400)); sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) (facing * 0x400)); sprite.updateSensors(sprite.getX(), sprite.getY());
        fixture.camera().setX((short) Math.max(0, spring.x() - 160));
        fixture.camera().setY((short) Math.max(0, spring.y() - 112));
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        var expected = new ArrayList<com.openggf.game.mode.CourseControl.PlayerState>();
        com.openggf.game.rewind.CompositeSnapshot duringBounce = null;
        int impulses = 0, previousYSpeed = sprite.getYSpeed();
        for (int row = 0; row < 8; row++) {
            fixture.stepIdleFrames(1);
            var ball = checkpointCourse(fixture).playerState(); expected.add(ball);
            if (ball.ySpeed() < previousYSpeed) impulses++;
            previousYSpeed = ball.ySpeed();
            if (row == 1) duringBounce = registry.capture();
        }
        assertEquals(1, impulses, "leaving the housing must not fire repeated upward impulses");
        int strength = (spring.subtype() & 2) == 0 ? -0x1000 : -0xA00;
        assertTrue(expected.stream().anyMatch(ball -> Math.abs(ball.ySpeed() - strength) <= 0x40),
                "a rolling SIDE contact must fire the native spring strength");
        assertTrue(sprite.getAir());
        assertTrue(expected.getLast().rolling());
        assertTrue(expected.getLast().xSpeed() * facing > 0,
                () -> "side entry keeps forward momentum: " + spring + " / " + expected);
        assertFalse(sprite.getPushing(), "release the native object pushing latch");
        var after = registry.capture();
        registry.restore(before);
        for (int row = 0; row < expected.size(); row++) {
            fixture.stepIdleFrames(1);
            assertEquals(expected.get(row), checkpointCourse(fixture).playerState(), "side spring replay row " + row);
        }
        var replay = registry.capture();
        for (String key : after.entries().keySet()) assertEquals(List.of(),
                com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, after.get(key), replay.get(key)), key);
        registry.restore(duringBounce);
        for (int row = 2; row < expected.size(); row++) {
            fixture.stepIdleFrames(1);
            assertEquals(expected.get(row), checkpointCourse(fixture).playerState(), "in-contact restore row " + row);
        }
    }

    @ParameterizedTest @CsvSource({"sonic,1", "sonic,-1", "tails,1", "tails,-1"})
    void verticalChipClearsSolidWallBeforeMovingOverItsEdge(String character, int facing) throws Exception {
        var fixture = launch(character, 0); fixture.stepIdleFrames(120);
        var sprite = fixture.sprite();
        int startX = sprite.getCentreX();
        int floorY = sprite.getCentreY() + sprite.getYRadius();
        // Controlled solid fixture exercises the production collision owner in a real ROM course.
        // It is not evidence of a particular EHZ terrain wall or an authored route.
        int wallX = startX + facing * 29, wallTop = floorY - 64;
        GameServices.level().getObjectManager().addDynamicObject(new GolfWallFixture(
                new com.openggf.level.objects.ObjectSpawn(wallX, floorY - 32, 0, 0, 0, false, 0)));
        for (int i = 0; i < 90; i++) fixture.stepFrame(true, false, false, false, false);
        fixture.stepFrame(false, false, facing < 0, facing > 0, false);
        assertEquals(90, value(shotState(), "elevationDegrees"));
        commit(fixture, 55); fixture.stepIdleFrames(29);
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        var observed = new ArrayList<com.openggf.game.mode.CourseControl.PlayerState>();
        boolean blocked = false, advanced = false;
        for (int row = 0; row < 50; row++) {
            fixture.stepIdleFrames(1);
            var ball = checkpointCourse(fixture).playerState(); observed.add(ball);
            if (ball.y() + sprite.getYRadius() > wallTop && ball.xSpeed() == 0) blocked = true;
            if ((ball.x() - startX) * facing > 2) {
                assertTrue(ball.y() + sprite.getYRadius() <= wallTop,
                        "forward movement must wait until the ball clears the solid wall");
                advanced = true; break;
            }
        }
        assertTrue(blocked, "native side collision must first stop the shot");
        assertTrue(advanced, "vertical chip must resume forward movement above the wall");
        registry.restore(before);
        for (int row = 0; row < observed.size(); row++) {
            fixture.stepIdleFrames(1);
            assertEquals(observed.get(row), checkpointCourse(fixture).playerState(), "vertical chip replay row " + row);
        }
    }

    public static final class GolfWallFixture extends com.openggf.level.objects.BoxObjectInstance
            implements com.openggf.level.objects.SolidObjectProvider {
        public GolfWallFixture(com.openggf.level.objects.ObjectSpawn spawn) {
            super(spawn, "golf test wall", 16, 32, 1, 1, 1, false);
        }
        @Override public com.openggf.level.objects.SolidObjectParams getSolidParams() {
            return com.openggf.level.objects.SolidObjectParams.of(29, 32, 32);
        }
        @Override public com.openggf.level.objects.AbstractObjectInstance recreateForRewind(
                com.openggf.level.objects.RewindRecreateContext context) {
            return new GolfWallFixture(context.spawn());
        }
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
            var before = course.playerState();
            fixture.stepIdleFrames(1);
            var after = course.playerState();
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
