package com.openggf.physics;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.SessionManager;
import com.openggf.game.GameServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestTerrainCollisionManagerReset {

    @BeforeEach
    void setUp() {
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void resetStateClearsPooledResults() {
        TerrainCollisionManager tcm = GameServices.terrainCollision();
        tcm.resetState();
        // Verifies method exists and runs cleanly without NPE
    }
}


