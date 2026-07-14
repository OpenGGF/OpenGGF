package com.openggf.tools.modsdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestGgfModCliCommands {
    @TempDir Path temp;

    @Test void initDispatchesWithPinnedFlags() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Path output = temp.resolve("starter");
        assertEquals(0, GgfModCli.run(new String[]{"init", output.toString(),
                "--id", "starter-mod", "--package", "example.starter"}, new PrintStream(bytes)));
        assertTrue(output.resolve("pom.xml").toFile().isFile());
    }

    @Test void runCommandUsesExplicitAbsoluteDevPropertyAndCurrentClasspath() {
        var command = GgfModCli.engineCommand(temp);
        assertTrue(command.stream().anyMatch(s -> s.equals("-Dggfmod.dev.modDir=" + temp.toAbsolutePath().normalize())));
        assertTrue(command.contains(System.getProperty("java.class.path")));
        assertEquals("com.openggf.Engine", command.get(command.size()-1));
    }

    @Test void malformedCommandsFailWithoutThrowing() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertEquals(1, GgfModCli.run(new String[]{"convert", "art", "--image"}, new PrintStream(bytes)));
        assertTrue(bytes.toString().contains("ERROR"));
    }

    @Test void malformedMusicFlagFailsWithExactlyOneColonMessage() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit = GgfModCli.run(new String[]{"convert", "level", "--from-tmx", "map.tmx",
                "--palette", "palette.gpal", "--music", "no-colon-here",
                "--out", temp.resolve("music-malformed").toString()}, new PrintStream(bytes));
        assertEquals(1, exit);
        assertTrue(bytes.toString().contains("ERROR COMMAND_FAILED Mod key must contain exactly one colon"),
                bytes.toString());
    }

    @Test void engineExitCodesAreNormalizedToCliSuccessOrFailure() {
        assertEquals(0,GgfModCli.normalizeProcessExit(0));
        assertEquals(1,GgfModCli.normalizeProcessExit(2));
        assertEquals(1,GgfModCli.normalizeProcessExit(-1));
    }
}
