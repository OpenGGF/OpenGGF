package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.InitialProcessSpritesLifecycle;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the deferred native assembly, rather than only comparing snapshot values. */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestFreshLevelBoundaryRewindHeadless {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void restoredBoundaryRetainsPublicationPhaseAndDispatchesDeferredAssembly(boolean published)
            throws Exception {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1).build();
        var level = GameServices.level();
        var camera = GameServices.camera();
        level.loadZoneAndActAtFreshTitleCardBoundary(Sonic3kZoneIds.ZONE_SOZ, 0);
        var player = fixture.sprite();
        assertTrue(level.hasPendingFreshLevelTransitionBoundary());
        assertEquals(0, player.getCentreX(), "fresh title holds the destination player slot");
        assertEquals(InitialProcessSpritesLifecycle.NONE,
                level.capturePendingInitialProcessSpritesLifecycleForRewind(),
                "the boundary retains the initial pass until real completion");
        RewindSnapshottable adapter = level.levelTransitionRewindSnapshottable();
        Object unpublished = adapter.capture();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var unpublishedOwners = registry.capture();
        level.publishFreshLevelTransitionInitialBoundary();
        short initialX = player.getCentreX();
        short initialY = player.getCentreY();
        short destinationCameraX = camera.getX();
        short destinationCameraY = camera.getY();
        assertNotEquals(0, initialX, "publication assembles the ROM destination player");
        Object publishedState = adapter.capture();
        var publishedOwners = registry.capture();
        level.completeFreshLevelTransitionBoundary();
        short assembledX = player.getCentreX();
        short assembledY = player.getCentreY();
        short assembledXSpeed = player.getXSpeed();
        short assembledYSpeed = player.getYSpeed();
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());

        // The actual initial object pass needs its captured object/native-slot
        // owners too. Replaying only the boundary against already-initialized
        // objects would exercise a different Process_Sprites dispatch.
        registry.restore(published ? publishedOwners : unpublishedOwners);
        player.setCentreX((short) 0x222);
        player.setCentreY((short) 0x333);
        player.setXSpeed((short) 0x123);
        player.setYSpeed((short) 0x234);
        player.setAnimationId(7);
        camera.setX((short) 0x444);
        camera.setY((short) 0x555);
        adapter.restore(published ? publishedState : unpublished);
        assertTrue(level.hasPendingFreshLevelTransitionBoundary(),
                "restoring a captured deferred boundary must restore its real continuation");
        assertEquals(0x222, player.getCentreX(), "restore itself must not dispatch assembly");
        assertEquals(0x123, player.getXSpeed());
        assertEquals(0x444, camera.getX(), "restore itself must not write the camera");

        level.publishFreshLevelTransitionInitialBoundary();
        if (published) {
            assertEquals(0x222, player.getCentreX(), "already-published boundary is idempotent");
            assertEquals(0x123, player.getXSpeed());
            assertEquals(0x444, camera.getX());
        } else {
            assertEquals(initialX, player.getCentreX());
            assertEquals(initialY, player.getCentreY());
            assertEquals(0, player.getXSpeed());
            assertEquals(0, player.getYSpeed());
            assertEquals(0, player.getAnimationId(), "native initial boundary publishes Wait");
            assertEquals(destinationCameraX, camera.getX());
            assertEquals(destinationCameraY, camera.getY());
        }
        int objectPasses = level.getObjectManager().getFrameCounter();
        level.completeFreshLevelTransitionBoundary();
        assertEquals(assembledX, player.getCentreX());
        assertEquals(assembledY, player.getCentreY());
        assertEquals(assembledXSpeed, player.getXSpeed());
        assertEquals(assembledYSpeed, player.getYSpeed());
        assertEquals(destinationCameraX, camera.getX());
        assertEquals(destinationCameraY, camera.getY());
        assertEquals(objectPasses + 1, level.getObjectManager().getFrameCounter(),
                "completion must dispatch the deferred initial Process_Sprites exactly once");
        assertEquals(InitialProcessSpritesLifecycle.NONE,
                level.capturePendingInitialProcessSpritesLifecycleForRewind());
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
        level.completeFreshLevelTransitionBoundary();
        assertEquals(objectPasses + 1, level.getObjectManager().getFrameCounter(),
                "a completed boundary cannot dispatch the initial pass again");
    }
}
