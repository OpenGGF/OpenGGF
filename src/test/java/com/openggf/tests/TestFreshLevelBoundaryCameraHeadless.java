package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.camera.DeadzoneGeometry;
import com.openggf.game.GameServices;
import com.openggf.game.internal.LevelStartCameraPosition;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestFreshLevelBoundaryCameraHeadless {
    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void preparedIntroCameraSurvivesTitlePublicationCompletionAndRewind() throws Exception {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1).build();
        var level = GameServices.level();
        var camera = GameServices.camera();

        level.loadZoneAndActWithTitleCard(Sonic3kZoneIds.ZONE_DEZ, 0);
        assertInstanceOf(LevelStartCameraPosition.class, level.getZoneFeatureProvider());
        short destinationX = camera.getX();
        short destinationY = camera.getY();
        int genericX = Math.max(camera.getMinX(), Math.min(camera.getMaxX(),
                fixture.sprite().getCentreX() - DeadzoneGeometry.rightEdge(camera.getWidth())));
        assertNotEquals(genericX, destinationX,
                "The actual DEZ1 profile must prepare its intro focus rather than generic centering");

        level.loadZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1);
        short sourceX = camera.getX();
        short sourceY = camera.getY();
        fixture.sprite().setRingCount(28);
        level.loadZoneAndActAtFreshTitleCardBoundary(Sonic3kZoneIds.ZONE_DEZ, 0);
        assertTrue(level.hasPendingFreshLevelTransitionBoundary());
        assertCamera(camera, sourceX, sourceY, "The held title retains the outgoing camera");
        assertEquals(0, fixture.sprite().getCentreX(), "Destination player assembly remains deferred");
        assertEquals(28, fixture.sprite().getRingCount());
        var registry = fixture.gameplayMode().getRewindRegistry();
        var held = registry.capture();

        level.publishFreshLevelTransitionInitialBoundary();
        assertCamera(camera, destinationX, destinationY,
                "Initial publication must retain the position prepared by the real load profile");
        assertEquals(28, fixture.sprite().getRingCount());
        level.completeFreshLevelTransitionBoundary();
        assertCamera(camera, destinationX, destinationY,
                "Title release must retain the prepared destination camera");
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());

        registry.restore(held);
        assertTrue(level.hasPendingFreshLevelTransitionBoundary());
        level.publishFreshLevelTransitionInitialBoundary();
        assertCamera(camera, destinationX, destinationY,
                "Rewinding the held title must preserve the same profile-owned position");
        level.completeFreshLevelTransitionBoundary();
        assertCamera(camera, destinationX, destinationY,
                "Replayed title release must preserve the same destination camera");
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void genericLoadStillPublishesItsCenteredCameraAfterTheSourceHold() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var level = GameServices.level();
        var camera = GameServices.camera();
        short sourceX = camera.getX();
        short sourceY = camera.getY();

        level.loadZoneAndActAtFreshTitleCardBoundary(1, 0);
        assertFalse(level.getZoneFeatureProvider() instanceof LevelStartCameraPosition);
        assertTrue(level.hasPendingFreshLevelTransitionBoundary());
        assertCamera(camera, sourceX, sourceY, "Generic loads also retain the outgoing title camera");
        level.publishFreshLevelTransitionInitialBoundary();
        short destinationX = (short) Math.max(camera.getMinX(), Math.min(camera.getMaxX(),
                fixture.sprite().getCentreX() - DeadzoneGeometry.rightEdge(camera.getWidth())));
        short destinationY = (short) Math.max(camera.getMinY(), Math.min(camera.getMaxY(),
                fixture.sprite().getCentreY() - 96));
        assertCamera(camera, destinationX, destinationY,
                "A profile without a camera-position owner retains generic centering");
        level.completeFreshLevelTransitionBoundary();
        assertCamera(camera, destinationX, destinationY,
                "Generic title release keeps the same centered camera");
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
    }

    private static void assertCamera(Camera camera, short expectedX, short expectedY, String message) {
        assertAll(message,
                () -> assertEquals(expectedX, camera.getX()),
                () -> assertEquals(expectedY, camera.getY()),
                () -> assertEquals(expectedX, camera.getXCopy()),
                () -> assertEquals(expectedY, camera.getYCopy()));
    }
}
