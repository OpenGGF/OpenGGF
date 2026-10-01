package com.openggf.mods.code;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.session.SessionManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.Level;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
        return HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void runsAcrossSeveralWindowsAndCanReplayARebase(WidescreenAspect aspect) throws Exception {
        var fixture = launch(aspect);
        assertEquals(aspect.pixelWidth(), fixture.camera().getWidth());
        Level level = GameServices.level().getCurrentLevel();
        assertTrue(level.getBlockCount() <= 256, "byte layout block budget");
        assertEquals(1, level.getObjects().size());
        assertTrue(level.getRings().isEmpty());
        fixture.stepIdleFrames(2);
        int rebases = 0;
        int previous = fixture.sprite().getCentreX();
        for (int i = 0; i < 6000; i++) {
            fixture.stepFrame(false, false, false, true, false);
            int x = fixture.sprite().getCentreX();
            if (previous - x > 4000) rebases++;
            assertFalse(fixture.sprite().getDead(), "death at frame " + i + ", x=" + x + ", y=" + fixture.sprite().getCentreY() + ", cameraMaxY=" + fixture.camera().getMaxY());
            previous = x;
        }
        assertTrue(rebases >= 4, "must traverse several windows; got " + rebases + ", x=" + previous);
        // A full registry snapshot proves object origin, terrain and player restore together.
        var registry = fixture.runtime().getRewindRegistry();
        var before = registry.capture();
        for (int i = 0; i < 800; i++) fixture.stepFrame(false, false, false, true, false);
        int expectedX = fixture.sprite().getCentreX();
        int expectedY = fixture.sprite().getCentreY();
        int expectedFraction = fixture.sprite().getXSubpixelRaw();
        short expectedSpeed = fixture.sprite().getGSpeed();
        byte[] expectedMap = GameServices.level().getCurrentLevel().getMap().getData().clone();
        registry.restore(before);
        for (int i = 0; i < 800; i++) fixture.stepFrame(false, false, false, true, false);
        assertEquals(expectedX, fixture.sprite().getCentreX());
        assertEquals(expectedY, fixture.sprite().getCentreY());
        assertEquals(expectedFraction, fixture.sprite().getXSubpixelRaw());
        assertEquals(expectedSpeed, fixture.sprite().getGSpeed());
        assertArrayEquals(expectedMap, GameServices.level().getCurrentLevel().getMap().getData());
        int backwardsRebases = 0;
        previous = fixture.sprite().getCentreX();
        for (int i = 0; i < 1800; i++) {
            fixture.stepFrame(false, false, true, false, false);
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
}
