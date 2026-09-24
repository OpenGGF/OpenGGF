package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.session.SessionManager;
import com.openggf.game.GameStateManager;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Hit contract and native six-routine loop for {@code Obj_LRZEndBoss}. */
class TestLrzEndBossInstance {
    private LrzEndBossInstance boss;

    @BeforeEach void setUp() {
        TestEnvironment.resetAll();
        SessionManager.clear();
        boss = new LrzEndBossInstance();
        boss.setServices(new TestObjectServices().withIsolatedObjectManager()
                .withGameState(mock(GameStateManager.class)));
    }

    @Test void startsWithFourteenHits() {
        assertEquals(14,boss.hitCountForTest());
        assertEquals(0,boss.routineForTest());
        assertEquals(14,boss.getCollisionProperty());
    }

    @Test void fourteenSeparatedHitsEnterDefeat() {
        int frame=0;
        for (int hit=0;hit<14;hit++) {
            boss.onPlayerAttack(null,null);
            for (int i=0;i<33;i++) boss.update(frame++,null);
        }
        assertEquals(0,boss.getCollisionProperty());
        assertTrue(boss.isDefeated());
        assertEquals(0,boss.getCollisionFlags());
    }
}
