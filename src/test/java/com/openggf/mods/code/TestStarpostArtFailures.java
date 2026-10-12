package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.mods.scene.host.SceneServices;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Actual packaged Starpost callbacks: borrowed canonical ROM art, injected load failure only. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStarpostArtFailures {
    @TempDir Path work;
    private SharedLevel level;
    @AfterEach void closeLevel() { if (level != null) level.dispose(); }

    @Test void expectedPreparationFailureRetriesAndProperHostCloseRetiresArt() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (var harness = ExampleModHarness.build(Path.of("examples/starpost-valley"), work.resolve("build"))) {
            var module = harness.apply(GameServices.module());
            try (var real = new SceneRomLibrary(module, GameServices.rom().getRom(), GameServices.rom())) {
                SceneRomArt s1 = real.rom("s1"); assumeTrue(s1 != null, "verified S1 is required");
                AtomicBoolean damaged = new AtomicBoolean(true);
                AtomicInteger attempts = new AtomicInteger();
                SceneRomArt injected = wrap(s1, () -> {
                    attempts.incrementAndGet();
                    if (damaged.get()) throw new IllegalArgumentException("fixture invalid kit");
                });
                var library = mock(SceneRomLibrary.class);
                when(library.rom("s1")).thenReturn(injected);
                open(harness, module, real.rom("s3k"), library);
                assertEquals(1, attempts.get());
                assertFalse(draw(harness).isEmpty());
                for (int i = 0; i < 119; i++) harness.tick();
                assertEquals(1, attempts.get());
                damaged.set(false); harness.tick();
                assertEquals(2, attempts.get());
                assertTrue(harness.debugJump("new sonic"), "full source scene recovers, not just a fake success flag");
                harness.tick(); assertFalse(draw(harness).isEmpty());
                assertTrue(harness.findings().isEmpty());
                Object scene = harness.scene();
                var shellField = scene.getClass().getDeclaredField("shell"); shellField.setAccessible(true);
                Object shell = shellField.get(scene);
                Object art = shell.getClass().getField("art").get(shell);
                harness.host().close();
                var closed = assertThrows(InvocationTargetException.class,
                        () -> art.getClass().getMethod("farmer", String.class).invoke(art, "sonic"));
                assertInstanceOf(IllegalStateException.class, closed.getCause());
            }
        }
    }

    @Test void missingOptionalRomHasInspectableStatusAndNoCreatorFault() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (var harness = ExampleModHarness.build(Path.of("examples/starpost-valley"), work.resolve("build"))) {
            var module = harness.apply(GameServices.module());
            var library = mock(SceneRomLibrary.class);
            open(harness, module, null, library);
            harness.tick(); assertFalse(draw(harness).isEmpty());
            List<?> failures = (List<?>) harness.scene().getClass().getMethod("failures").invoke(harness.scene());
            assertEquals("OPTIONAL_ROM_MISSING", failures.getFirst().getClass().getMethod("kind").invoke(failures.getFirst()).toString());
            assertTrue(harness.findings().isEmpty());
            harness.host().close(); assertNull(harness.scene());
        }
    }

    @Test void preparationProgrammingFaultReachesVerifiedOwnerBoundary() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (var harness = ExampleModHarness.build(Path.of("examples/starpost-valley"), work.resolve("build"))) {
            var module = harness.apply(GameServices.module());
            try (var real = new SceneRomLibrary(module, GameServices.rom().getRom(), GameServices.rom())) {
                SceneRomArt s1 = real.rom("s1"); assumeTrue(s1 != null, "verified S1 is required");
                var library = mock(SceneRomLibrary.class);
                when(library.rom("s1")).thenReturn(wrap(s1, () -> { throw new NullPointerException("fixture preparation bug"); }));
                var fault = assertThrows(ModFaultBoundary.CallbackAborted.class,
                        () -> open(harness, module, real.rom("s3k"), library));
                assertInstanceOf(NullPointerException.class, fault.getCause());
                assertTrue(harness.findings().get("starpost-valley").stream().anyMatch(f -> f.code().equals("MOD_CALLBACK_FAILED")));
                harness.host().close();
            }
        }
    }

    private void open(ExampleModHarness harness, com.openggf.game.GameModule module,
            SceneRomArt running, SceneRomLibrary library) {
        harness.host().open(module.getGameService(OwnedSceneFactory.class),
                new SceneServices(null, running, work.resolve("saves"), null, null, null, library), 400, 224);
    }
    private static List<?> draw(ExampleModHarness harness) {
        harness.host().draw(null, null);
        return harness.host().recordedFrame();
    }
    private static SceneRomArt wrap(SceneRomArt source, Runnable beforeKit) {
        return (SceneRomArt) Proxy.newProxyInstance(SceneRomArt.class.getClassLoader(), new Class<?>[] {SceneRomArt.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("levelKit")) beforeKit.run();
                    try { return method.invoke(source, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
    }
}
