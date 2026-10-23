package com.openggf.level;

import com.openggf.game.rewind.RewindBoundary;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestLevelManagerRewindBoundary {

    @BeforeEach
    void setUp() {
        com.openggf.tests.TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void levelBoundaryReportsThroughGameplayContext() {
        GameplayModeContext context = SessionManager.openGameplaySession(new Sonic2GameModule());
        AtomicReference<RewindBoundary> boundary = new AtomicReference<>();
        context.setRewindBoundaryReporter(boundary::set);

        com.openggf.game.session.GameplaySessionFactory.attachManagers(
                context, com.openggf.game.session.EngineServices.current());
        context.getLevelManager().markRewindLevelLoadBoundary();

        assertEquals(RewindBoundary.LEVEL_LOAD, boundary.get());
    }

    @Test
    void loadingManagersReportToTheirAttachedOwnerWhileAnotherContextIsAmbient() {
        GameplayModeContext ambient = com.openggf.tests.TestEnvironment.activeGameplayMode();
        GameplayModeContext owner = new GameplayModeContext(
                new com.openggf.game.session.WorldSession(new Sonic2GameModule()));
        com.openggf.game.session.GameplaySessionFactory.attachManagers(
                owner, com.openggf.game.session.EngineServices.current());
        AtomicReference<RewindBoundary> ownerBoundary = new AtomicReference<>();
        AtomicReference<RewindBoundary> ambientBoundary = new AtomicReference<>();
        owner.setRewindBoundaryReporter(ownerBoundary::set);
        ambient.setRewindBoundaryReporter(ambientBoundary::set);
        try {
            owner.getLevelManager().markRewindLevelLoadBoundary();

            assertEquals(RewindBoundary.LEVEL_LOAD, ownerBoundary.get());
            org.junit.jupiter.api.Assertions.assertNull(ambientBoundary.get());
        } finally {
            owner.destroy();
        }
    }
}
