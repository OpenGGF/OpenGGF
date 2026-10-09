package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSceneRunLauncher {
    private GameLoop loop;
    private final List<RunSpec> started = new ArrayList<>();
    private final List<RunEndReason> returned = new ArrayList<>();
    private int guarded;
    private SceneRunLauncher launcher;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        loop = new GameLoop(new InputHandler());
        launcher = new SceneRunLauncher(loop, host -> { guarded++; return host; }, List.of("s2"),
                started::add, returned::add);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    private static RunSpec spec(String game, String character) {
        return new RunSpec(game, 0, 0, character, GameplayRunPolicy.isolatedAct());
    }

    @Test
    void launchesOnlyAvailableGamesWithStockCharacters() {
        assertThrows(IllegalArgumentException.class, () -> launcher.launch(spec("s1", "sonic"), new RunHost() { }));
        assertThrows(IllegalArgumentException.class, () -> launcher.launch(spec("s2", "knuckles"), new RunHost() { }));
        assertFalse(loop.hostedRuns.isActive());
    }

    @Test
    void launchGuardsTheHostAndDefersTheSessionToAFrameBoundary() {
        RunHandle handle = launcher.launch(spec("s2", "tails"), new RunHost() { });
        assertTrue(handle.isActive());
        assertTrue(loop.hostedRuns.isActive());
        assertEquals(1, guarded, "the creator's host runs inside the owner's fault boundary");
        assertTrue(started.isEmpty(), "the session opens after the scene callback, behind a fade");
        assertThrows(IllegalStateException.class, () -> launcher.launch(spec("s2", "sonic"), new RunHost() { }));
    }

    @Test
    void fingerprintsAreOfferedOnlyForAvailableGames() {
        SceneRunLauncher withFingerprints = new SceneRunLauncher(loop, host -> host, List.of("s2"),
                started::add, returned::add, game -> java.util.Optional.of("0.8:" + game));
        assertEquals(java.util.Optional.of("0.8:s2"), withFingerprints.determinismFingerprint("s2"));
        assertEquals(java.util.Optional.empty(), withFingerprints.determinismFingerprint("s3k"),
                "a game whose ROM is not available has no fingerprint");
    }

    @Test
    void aFailedLoadReturnsToTheSceneImmediately() {
        launcher.launch(spec("s2", "sonic"), new RunHost() { });
        loop.endHostedRunAfterFailedLaunch(true);
        assertEquals(List.of(RunEndReason.LOAD_FAILED), returned);
        assertFalse(loop.hostedRuns.isActive());
    }
}
