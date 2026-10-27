package com.openggf.mods.code;

import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Reusing compiled input must never reuse a creator runtime or its mutable owners. */
class TestExampleModHarnessIsolation {
    @TempDir Path work;

    @AfterEach void reset() {
        TestEnvironment.resetAll();
    }

    @Test void compiledPackageOpensIndependentRuntimesAndSurvivesPeerClose() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_1);
        var compiled = ExampleModHarness.compile(Path.of("examples/infinite-sonic"), work.resolve("package"));
        Path jar = compiled.repository().resolve(compiled.manifest().id() + ".jar");
        byte[] original = Files.readAllBytes(jar);
        try (var first = compiled.open(work.resolve("first"));
             var second = compiled.open(work.resolve("second"))) {
            Class<?> firstType = first.loader().loadClass("infinite.InfiniteSonicMod");
            Class<?> secondType = second.loader().loadClass("infinite.InfiniteSonicMod");
            assertNotSame(firstType, secondType);
            assertNotSame(first.loader(), second.loader());
            assertNotSame(first.plan(), second.plan());
            assertNotSame(first.host(), second.host());
            assertNotSame(first.input(), second.input());
            first.close();
            assertSame(secondType, second.loader().loadClass("infinite.InfiniteSonicMod"));
            assertNotNull(second.apply(com.openggf.game.GameServices.module()));
            assertTrue(second.findings().isEmpty());
            assertArrayEquals(original, Files.readAllBytes(jar));
        }
        assertTrue(Files.isDirectory(work.resolve("first")));
        assertTrue(Files.isDirectory(work.resolve("second")));
    }
}
