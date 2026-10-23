package com.openggf.sprites.playable;

import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameServices;
import com.openggf.game.mutators.LevelMutatorPolicy;
import com.openggf.game.mutators.LevelMutatorPolicyAccess;
import com.openggf.game.mutators.RingAcquisitionDomain;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2SuperStateController;
import com.openggf.game.sonic2.objects.SuperSonicStarsObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
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
            // Reuse only the mutable policy provider. The helper also allocates an
            // unused private world; this test exercises one active gameplay world.
            var policies = new LevelMutatorTestWorld(new Sonic2GameModule());
            var gameplay = SessionManager.openGameplaySession(policies.module());
            GameplaySessionFactory.attachManagers(gameplay, EngineServices.current());
            var world = gameplay.getWorldSession();
            var level = mock(LevelManager.class);
            when(level.getWorldSession()).thenReturn(world);
            var terrain = new TerrainCollisionManager();
            gameplay.attachLevelManagers(new WaterSystem(), new ParallaxManager(), terrain,
                    new CollisionSystem(terrain), gameplay.getSpriteManager(), level);
            var services = new DefaultObjectServices(gameplay, EngineServices.current());
            var manager = new ObjectManager(List.of(), null, 0, null, null, null, null, services);
            when(level.getObjectManager()).thenReturn(manager);
            var interior = mock(BonusStageProvider.class);
            when(interior.getActiveType()).thenReturn(BonusStageType.GUMBALL);
            gameplay.setActiveBonusStageProvider(interior);
            var player = spy(new TestablePlayableSprite("sonic", (short)160, (short)112));
            gameplay.getSpriteManager().addSprite(player);
            assertSame(level, GameServices.level());
            assertSame(level, player.currentLevelManager());
            assertSame(level, player.currentLevelManagerIfAvailable());
            assertSame(world, manager.getObjectServices().worldSession());
            assertSame(level, services.levelManager());
            assertSame(manager, services.objectManager());
            assertSame(interior, services.bonusStageProviderOrNull());
            assertEquals(RingAcquisitionDomain.STAGE_INTERIOR, LevelMutatorPolicyAccess.domain(services));
            policies.policy(new LevelMutatorPolicy(Set.of(), true, false, false, false));
            assertSame(policies.policy(), LevelMutatorPolicyAccess.policy(world));
            assertNotSame(policies.runtime(), LevelMutatorPolicyAccess.runtime(world));
            assertTrue(LevelMutatorPolicyAccess.ringsAllowed(services),
                    "the interior permits rings while the debug grant still uses the main domain");
            var controller = new Sonic2SuperStateController(player);
            controller.debugActivate();
            verify(player, never()).addRings(50);
            assertTrue(player.isSuperSonic());
            assertEquals(SuperState.TRANSFORMING, controller.getState());
            var starsObjects = manager.getActiveObjects().stream()
                    .filter(SuperSonicStarsObjectInstance.class::isInstance)
                    .map(SuperSonicStarsObjectInstance.class::cast).toList();
            assertEquals(1, starsObjects.size());
            var stars = starsObjects.getFirst();
            assertTrue(stars.isOwnedBy(player));
            assertEquals(player.getGameRules().powerUp().superStarsFixedSlotIndex(), stars.getSlotIndex());
            controller.reset();
            policies.policy(LevelMutatorPolicy.STOCK);
            assertSame(policies.policy(), LevelMutatorPolicyAccess.policy(world));
            assertNotSame(policies.runtime(), LevelMutatorPolicyAccess.runtime(world));
            controller.debugActivate();
            verify(player).addRings(50);
            assertEquals(List.of(stars), manager.getActiveObjects().stream()
                    .filter(SuperSonicStarsObjectInstance.class::isInstance).toList());
        } finally {
            SessionManager.clear();
        }
    }
}
