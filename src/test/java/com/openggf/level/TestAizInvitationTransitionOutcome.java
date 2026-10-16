package com.openggf.level;

import com.openggf.game.BonusStageType;
import com.openggf.game.mutators.LevelMutatorPolicy;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.tests.LevelMutatorTestWorld;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameStateManager;
import com.openggf.game.RespawnState;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostBonusStarChild;
import com.openggf.game.sonic3k.objects.Sonic3kStarPostFactory;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native void producer semantics; invocation alone does not prove an accepted invitation entry. */
class TestAizInvitationTransitionOutcome {
    @Test void actualInvitationChildKeepsDeniedOpportunityAndPublishesTheNativePendingFamilyOnce() {
        GraphicsManager graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        try {
            var policies = new LevelMutatorTestWorld(new Sonic3kGameModule());
            var transitions = new LevelTransitionCoordinator(policies.worldSession());
            var checkpoint = new CheckpointState() {
                @Override public void saveCheckpoint(int index, int x, int y, boolean locked) {
                    restoreStarPostActivationMark(index);
                }
            };
            ObjectManager[] holder = new ObjectManager[1];
            var services = new StubObjectServices() {
                @Override public com.openggf.game.session.WorldSession worldSession() { return policies.worldSession(); }
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public RespawnState checkpointState() { return checkpoint; }
                @Override public GameStateManager gameState() { return state; }
                @Override public void requestBonusStageEntry(BonusStageType type) { transitions.requestBonusStageEntry(type); }
                private final GameStateManager state = new GameStateManager();
            };
            holder[0] = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    graphics, null, services);
            var spawn = new ObjectSpawn(0x140, 0x180, Sonic3kObjectIds.STAR_POST, 1, 0, false, 0);
            var post = Sonic3kStarPostFactory.invitation(spawn, BonusStageType.GUMBALL);
            holder[0].addDynamicObject(post);
            var player = mock(AbstractPlayableSprite.class);
            when(player.getCentreX()).thenReturn((short) spawn.x());
            when(player.getCentreY()).thenReturn((short) spawn.y());
            when(player.getYRadius()).thenReturn((short) 19);
            post.update(1, player);
            var stars = holder[0].getActiveObjects().stream()
                    .filter(Sonic3kStarPostBonusStarChild.class::isInstance)
                    .map(Sonic3kStarPostBonusStarChild.class::cast).toList();
            assertEquals(4, stars.size(), "actual marked-post activation at zero rings");
            var star = stars.getFirst();
            for (int tick = 0; tick < 128; tick++) star.update(tick, null);
            transitions.requestBonusStageEntry(BonusStageType.SLOT_MACHINE);
            policies.policy(new LevelMutatorPolicy(java.util.Set.of(), false, true, false, true));
            contact(star, player, 129);
            assertFalse(checkpoint.isUsedForSpecialStage(), "denied child admission keeps the invitation unconsumed");
            assertFalse(star.isDestroyed());
            assertEquals(BonusStageType.SLOT_MACHINE, transitions.peekBonusStageRequest());
            policies.policy(LevelMutatorPolicy.STOCK);
            contact(star, player, 130);
            // The stock void producer replaces an already pending family; a
            // successful method call is not an independent enqueue receipt.
            assertEquals(BonusStageType.GUMBALL, transitions.peekBonusStageRequest());
            assertTrue(checkpoint.isUsedForSpecialStage());
            for (var sibling : stars) sibling.update(131, player);
            assertTrue(stars.stream().allMatch(Sonic3kStarPostBonusStarChild::isDestroyed));
            assertEquals(BonusStageType.GUMBALL, transitions.consumeBonusStageRequest());
            assertNull(transitions.consumeBonusStageRequest());
            verify(player, never()).addRings(anyInt());
        } finally {
            graphics.resetState();
        }
    }

    private static void contact(Sonic3kStarPostBonusStarChild star, AbstractPlayableSprite player, int tick) {
        // Observe the actual progressed orbit position; seed only local player
        // contact, never child state, entry outcome or a production route.
        var captured = star.captureRewindState();
        try {
            var x = star.getClass().getDeclaredField("currentX");
            var y = star.getClass().getDeclaredField("currentY");
            x.setAccessible(true); y.setAccessible(true);
            when(player.getCentreX()).thenReturn((short) x.getInt(star));
            when(player.getCentreY()).thenReturn((short) y.getInt(star));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        assertNotNull(captured, "contact follows actual native star progression");
        star.update(tick, player);
    }

    @Test void rejectedVoidProducerDoesNotEnqueueAndExistingPendingValueRemainsNative() {
        var policies = new LevelMutatorTestWorld(new Sonic3kGameModule());
        var transitions = new LevelTransitionCoordinator(policies.worldSession());
        transitions.requestBonusStageEntry(BonusStageType.SLOT_MACHINE);
        assertEquals(BonusStageType.SLOT_MACHINE, transitions.peekBonusStageRequest());
        policies.policy(new LevelMutatorPolicy(java.util.Set.of(), false, true, false, true));
        transitions.requestBonusStageEntry(BonusStageType.GUMBALL);
        assertEquals(BonusStageType.SLOT_MACHINE, transitions.peekBonusStageRequest());
        assertEquals(BonusStageType.SLOT_MACHINE, transitions.consumeBonusStageRequest());
        transitions.requestBonusStageEntry(BonusStageType.GUMBALL);
        assertNull(transitions.peekBonusStageRequest());
        policies.policy(LevelMutatorPolicy.STOCK);
        transitions.requestBonusStageEntry(BonusStageType.GUMBALL);
        assertEquals(BonusStageType.GUMBALL, transitions.consumeBonusStageRequest());
        assertNull(transitions.consumeBonusStageRequest(), "consumer receives an accepted request once");
    }
}
