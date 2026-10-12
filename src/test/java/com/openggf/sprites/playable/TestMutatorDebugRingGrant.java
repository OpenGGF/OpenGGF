package com.openggf.sprites.playable;

import com.openggf.game.BonusStageProvider;
import com.openggf.game.mutators.LevelMutatorPolicy;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2SuperStateController;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.LevelMutatorTestWorld;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestMutatorDebugRingGrant {
    @Test void debugTransformationPreservesNativeStateButGrantUsesMainDomain() {
        TestEnvironment.resetAll();
        try {
            var services = new LevelMutatorTestWorld(new Sonic2GameModule()) {
                private final BonusStageProvider interior = mock(BonusStageProvider.class);
                { when(interior.getActiveType()).thenReturn(com.openggf.game.BonusStageType.GUMBALL); }
                @Override public BonusStageProvider bonusStageProviderOrNull() { return interior; }
            };
            services.policy(new LevelMutatorPolicy(Set.of(), true, false, false, false));
            var manager = new ObjectManager(List.of(), null, 0, null, null, null, null, services);
            var level = mock(LevelManager.class);
            when(level.getObjectManager()).thenReturn(manager);
            var player = spy(new TestablePlayableSprite("sonic", (short)160, (short)112));
            doReturn(level).when(player).currentLevelManagerIfAvailable();
            var controller = new Sonic2SuperStateController(player);
            controller.debugActivate();
            verify(player, never()).addRings(50);
            assertTrue(player.isSuperSonic());
            assertEquals(SuperState.TRANSFORMING, controller.getState());
            controller.reset();
            services.policy(LevelMutatorPolicy.STOCK);
            controller.debugActivate();
            verify(player).addRings(50);
        } finally {
            com.openggf.game.session.SessionManager.clear();
        }
    }
}
