package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.CheckpointState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameStateManager;
import com.openggf.game.RespawnState;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression for the time-attack star-post softlock companion bug: unlike the
 * S3K entry-ring gate (which stops a state change that would otherwise strand
 * the player), a S3K star-post touch during a time attack must still record
 * the checkpoint (respawn + split tracking depend on it), still play its SFX,
 * and still spawn the cosmetic orbiting star that decorates the post itself
 * (no player collision, never requests any stage entry) — only the 4 bonus
 * stars, which would request a bonus-stage entry that GameLoop's chokepoint
 * silently swallows, must not spawn at all.
 * See {@link Sonic3kStarPostObjectInstance#shouldSpawnBonusStars}.
 */
class TestSonic3kStarPostObjectInstanceTimeAttackGate {

    private static final ObjectSpawn STAR_POST_SPAWN =
            new ObjectSpawn(0x0140, 0x0180, Sonic3kObjectIds.STAR_POST, 0, 0, false, 0);
    private static final int BONUS_STAR_RING_THRESHOLD = 20;

    private com.openggf.tests.LevelMutatorTestWorld policies;
    private GameStateManager gameState;
    private RecordingCheckpointState checkpointState;
    private ObjectManager objectManager;

    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();
        policies = new com.openggf.tests.LevelMutatorTestWorld(new com.openggf.game.sonic3k.Sonic3kGameModule());
        gameState = new GameStateManager();
        gameState.resetSession();
        checkpointState = new RecordingCheckpointState();

        Camera camera = mockCameraAtOrigin();
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override public com.openggf.game.session.WorldSession worldSession() { return policies.worldSession(); }
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GameStateManager gameState() { return gameState; }
            @Override public RespawnState checkpointState() { return checkpointState; }
            @Override public com.openggf.game.session.GameplayRunPolicy runPolicy() { return runPolicy; }
            @Override public void requestBonusStageEntry(BonusStageType type) { entries.add(type); }
        };
        objectManager = new ObjectManager(
                List.of(),
                new Sonic3kObjectRegistry(),
                0, null, null,
                GraphicsManager.getInstance(),
                camera,
                services);
        holder[0] = objectManager;
        objectManager.reset(0);
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void bonusStarsSuppressedButCheckpointStillRecordedDuringTimeAttack() {
        runPolicy = com.openggf.game.session.GameplayRunPolicy.isolatedAct();

        Sonic3kStarPostObjectInstance starPost = new Sonic3kStarPostObjectInstance(STAR_POST_SPAWN);
        starPost.setServices(newServices());
        AbstractPlayableSprite player =
                mockPlayerAt(STAR_POST_SPAWN.x(), STAR_POST_SPAWN.y(), BONUS_STAR_RING_THRESHOLD);

        starPost.update(1, player);

        assertTrue(checkpointState.saveCalled,
                "star post must still record the checkpoint (respawn + time-attack split tracking depend on it)");
        assertEquals(starPost.getCheckpointIndex(), checkpointState.recordedIndex,
                "recorded checkpoint index must match the touched star post");
        assertEquals(0, liveObjects(Sonic3kStarPostBonusStarChild.class).size(),
                "bonus stars must not spawn at all while a time attack is active");
        assertEquals(1, liveObjects(Sonic3kStarPostStarChild.class).size(),
                "cosmetic orbiting post star must be unaffected by the bonus-star gate");
    }

    @Test
    void bonusStarsSpawnNormallyWhenTimeAttackInactive() {
        Sonic3kStarPostObjectInstance starPost = new Sonic3kStarPostObjectInstance(STAR_POST_SPAWN);
        starPost.setServices(newServices());
        AbstractPlayableSprite player =
                mockPlayerAt(STAR_POST_SPAWN.x(), STAR_POST_SPAWN.y(), BONUS_STAR_RING_THRESHOLD);

        starPost.update(1, player);

        assertTrue(checkpointState.saveCalled, "precondition: checkpoint recording must still occur");
        assertEquals(4, liveObjects(Sonic3kStarPostBonusStarChild.class).size(),
                "precondition: normal touch (no time attack) still spawns the 4 bonus stars");
    }

    @Test
    void mutatorDeniesBonusStarsButKeepsCheckpointBankAndNativeAnimation() {
        policies.policy(new com.openggf.game.mutators.LevelMutatorPolicy(java.util.Set.of(), false, true, false, true));
        Sonic3kStarPostObjectInstance checkpoint = new Sonic3kStarPostObjectInstance(STAR_POST_SPAWN);
        checkpoint.setServices(newServices());
        checkpoint.update(1, mockPlayerAt(STAR_POST_SPAWN.x(), STAR_POST_SPAWN.y(), 20));
        assertTrue(checkpointState.saveCalled);
        assertEquals(0, liveObjects(Sonic3kStarPostBonusStarChild.class).size());
    }

    @Test
    void invitationUsesSelectedNativeFamilyAtZeroRingsAndIsConsumedOnce() {
        var post = (Sonic3kStarPostObjectInstance) Sonic3kStarPostFactory.invitation(
                STAR_POST_SPAWN, BonusStageType.GUMBALL);
        post.setServices(newServices());
        var player = mockPlayerAt(STAR_POST_SPAWN.x(), STAR_POST_SPAWN.y(), 0);
        when(player.getYRadius()).thenReturn((short) 19);
        post.update(1, player);
        assertTrue(checkpointState.saveCalled);
        var stars = liveObjects(Sonic3kStarPostBonusStarChild.class);
        assertEquals(4, stars.size(), "marked post earns its native invitation without injecting rings");
        assertTrue(post.hasEarnedInvitation());
        var childServices = new StubObjectServices() {
            @Override public com.openggf.game.session.WorldSession worldSession() { return policies.worldSession(); }
            @Override public RespawnState checkpointState() { return checkpointState; }
            @Override public void requestBonusStageEntry(BonusStageType type) { entries.add(type); }
        };
        var star = stars.getFirst();
        star.setServices(childServices);
        // Reach the native collision-enabled orbit through updates. Position is
        // observed from its captured scalar state, never forced on the actor.
        for (int tick = 0; tick < 128; tick++) star.update(tick, null);
        var active = star.captureRewindState();
        int sx = scalar(star, "currentX"), sy = scalar(star, "currentY");
        when(player.getCentreX()).thenReturn((short) sx);
        when(player.getCentreY()).thenReturn((short) sy);
        star.update(129, player);
        assertEquals(List.of(BonusStageType.GUMBALL), entries);
        assertTrue(checkpointState.isUsedForSpecialStage());
        for (var sibling : stars) { sibling.setServices(childServices); sibling.update(130, player); }
        assertEquals(1, entries.size(), "siblings cannot publish another entry while return is pending");
        assertTrue(stars.stream().allMatch(ObjectInstance::isDestroyed));

        // Rewind restores invitation consumption alongside the native object
        // state, so a legitimate replay publishes exactly once in that timeline.
        var consumed = checkpointState.captureRewindState();
        checkpointState.restoreRewindState(null);
        star.restoreRewindState(active);
        star.update(129, player);
        assertEquals(2, entries.size());
        checkpointState.restoreRewindState(consumed);
        var reloaded = (Sonic3kStarPostObjectInstance) Sonic3kStarPostFactory.invitation(
                STAR_POST_SPAWN, BonusStageType.GUMBALL);
        reloaded.setServices(newServices());
        reloaded.update(131, player);
        assertTrue(liveObjects(Sonic3kStarPostBonusStarChild.class).isEmpty(),
                "native activation mark/consumption prohibit invitation farming after return");
        org.mockito.Mockito.verify(player, org.mockito.Mockito.never()).addRings(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void markedPostKeepsNativeEntryGatesAndSpawnRecreationRestoresItsRecipe() {
        assertThrows(IllegalArgumentException.class, () -> Sonic3kStarPostFactory.invitation(
                STAR_POST_SPAWN, BonusStageType.NONE));
        assertThrows(IllegalArgumentException.class, () -> Sonic3kStarPostFactory.invitation(
                new ObjectSpawn(10, 10, 1, 0, 0, false, 10), BonusStageType.GUMBALL));
        var post = (Sonic3kStarPostObjectInstance) Sonic3kStarPostFactory.invitation(
                STAR_POST_SPAWN, BonusStageType.GLOWING_SPHERE);
        post.setServices(newServices());
        var before = post.captureRewindState();
        var recreated = (Sonic3kStarPostObjectInstance) post.recreateForRewind(
                new com.openggf.level.objects.RewindRecreateContext(STAR_POST_SPAWN, before, newServices()));
        recreated.setServices(newServices());
        recreated.restoreRewindState(before);
        runPolicy = com.openggf.game.session.GameplayRunPolicy.isolatedAct();
        recreated.update(1, mockPlayerAt(STAR_POST_SPAWN.x(), STAR_POST_SPAWN.y(), 0));
        assertTrue(checkpointState.saveCalled);
        assertEquals(0, liveObjects(Sonic3kStarPostBonusStarChild.class).size());
    }

    @Test
    void invitationGraphRecreatesSelectedFamilyAndRelinksThenReplaysConsumption() throws Exception {
        var post = (Sonic3kStarPostObjectInstance) Sonic3kStarPostFactory.invitation(
                STAR_POST_SPAWN, BonusStageType.GLOWING_SPHERE);
        post.setServices(newServices());
        objectManager.addDynamicObject(post);
        var player = mockPlayerAt(STAR_POST_SPAWN.x(), STAR_POST_SPAWN.y(), 0);
        when(player.getYRadius()).thenReturn((short) 19);
        post.update(1, player);
        var stars = liveObjects(Sonic3kStarPostBonusStarChild.class);
        for (var star : stars) for (int tick = 0; tick < 128; tick++) star.update(tick, null);
        var adapter = objectManager.rewindSnapshottable();
        var saved = adapter.capture();
        var checkpoint = checkpointState.captureRewindState();
        int sx = scalar(stars.getFirst(), "currentX"), sy = scalar(stars.getFirst(), "currentY");
        when(player.getCentreX()).thenReturn((short) sx);
        when(player.getCentreY()).thenReturn((short) sy);
        stars.getFirst().update(129, player);
        assertEquals(List.of(BonusStageType.GLOWING_SPHERE), entries);
        objectManager.reset(0);
        assertTrue(objectManager.getActiveObjects().isEmpty());
        checkpointState.restoreRewindState(checkpoint);
        adapter.restore(saved);
        var restoredPost = liveObjects(Sonic3kStarPostObjectInstance.class).getFirst();
        var restoredStars = liveObjects(Sonic3kStarPostBonusStarChild.class);
        assertEquals(4, restoredStars.size());
        assertTrue(restoredPost.hasEarnedInvitation());
        var parent = Sonic3kStarPostBonusStarChild.class.getDeclaredField("parentStarPost");
        parent.setAccessible(true);
        for (var star : restoredStars) assertSame(restoredPost, parent.get(star));
        restoredStars.getFirst().update(129, player);
        assertEquals(List.of(BonusStageType.GLOWING_SPHERE, BonusStageType.GLOWING_SPHERE), entries);
        for (var star : restoredStars) star.update(130, player);
        assertEquals(2, entries.size());
        // A second destructive restore catches retained links/one-shot state.
        objectManager.reset(0);
        checkpointState.restoreRewindState(checkpoint);
        adapter.restore(saved);
        liveObjects(Sonic3kStarPostBonusStarChild.class).getFirst().update(129, player);
        assertEquals(3, entries.size());
    }

    @Test
    void invitationExpiresNativelyAcrossDeathAndReturnsButNextActGetsFreshOpportunity() {
        var player = mockPlayerAt(STAR_POST_SPAWN.x(), STAR_POST_SPAWN.y(), 0);
        var first = (Sonic3kStarPostObjectInstance) Sonic3kStarPostFactory.invitation(
                STAR_POST_SPAWN, BonusStageType.GUMBALL);
        first.setServices(newServices()); first.update(1, player);
        assertEquals(4, liveObjects(Sonic3kStarPostBonusStarChild.class).size());
        // Native death reload retains the activated post mark even before entry.
        var deathState = checkpointState.captureRewindState();
        objectManager.reset(0);
        checkpointState.restoreRewindState(deathState);
        var retry = (Sonic3kStarPostObjectInstance) Sonic3kStarPostFactory.invitation(
                STAR_POST_SPAWN, BonusStageType.GUMBALL);
        retry.setServices(newServices()); retry.update(2, player);
        assertTrue(liveObjects(Sonic3kStarPostBonusStarChild.class).isEmpty());
        checkpointState.markUsedForSpecialStage();
        objectManager.reset(0);
        var returned = (Sonic3kStarPostObjectInstance) Sonic3kStarPostFactory.invitation(
                STAR_POST_SPAWN, BonusStageType.GUMBALL);
        returned.setServices(newServices()); returned.update(3, player);
        assertTrue(liveObjects(Sonic3kStarPostBonusStarChild.class).isEmpty());
        // The ordinary next-act checkpoint reset clears the invitation lifetime.
        checkpointState.clear();
        var nextAct = (Sonic3kStarPostObjectInstance) Sonic3kStarPostFactory.invitation(
                STAR_POST_SPAWN, BonusStageType.GUMBALL);
        nextAct.setServices(newServices()); nextAct.update(4, player);
        assertEquals(4, liveObjects(Sonic3kStarPostBonusStarChild.class).size());
        assertTrue(entries.isEmpty(), "activation itself neither enters nor awards a bonus");
    }

    private final java.util.List<BonusStageType> entries = new java.util.ArrayList<>();

    private static int scalar(Object actor, String name) {
        try {
            var field = actor.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(actor);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private ObjectServices newServices() {
        return new StubObjectServices() {
            @Override public com.openggf.game.session.WorldSession worldSession() { return policies.worldSession(); }
            @Override public ObjectManager objectManager() { return objectManager; }
            @Override public GameStateManager gameState() { return gameState; }
            @Override public RespawnState checkpointState() { return checkpointState; }
            @Override public com.openggf.game.session.GameplayRunPolicy runPolicy() { return runPolicy; }
            @Override public void requestBonusStageEntry(BonusStageType type) { entries.add(type); }
        };
    }

    private com.openggf.game.session.GameplayRunPolicy runPolicy =
            com.openggf.game.session.GameplayRunPolicy.stock();

    private <T extends ObjectInstance> List<T> liveObjects(Class<T> type) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .toList();
    }

    private AbstractPlayableSprite mockPlayerAt(int x, int y, int ringCount) {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) x);
        when(player.getCentreY()).thenReturn((short) y);
        when(player.getRingCount()).thenReturn(ringCount);
        return player;
    }

    private static Camera mockCameraAtOrigin() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }

    private static final class RecordingCheckpointState extends CheckpointState {
        private boolean saveCalled;
        private int recordedIndex = -1;

        @Override
        public void saveCheckpoint(int checkpointIndex, int x, int y, boolean cameraLockFlag) {
            // Skip the real implementation's GameServices.camera() dependency — this
            // test has no active gameplay session. Track only what the bonus-star
            // gate under test needs to observe: that saving still happens, unaffected.
            this.saveCalled = true;
            this.recordedIndex = checkpointIndex;
            restoreStarPostActivationMark(checkpointIndex);
        }

        @Override
        public int getLastCheckpointIndex() {
            return recordedIndex;
        }
    }
}
