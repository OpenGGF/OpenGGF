package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code loc_78AA8} and the two camera releases behind it (sonic3k.asm:160505-160545).
 *
 * <p>The ordering is the whole point. Nothing happens until {@code End_of_level_flag} is set,
 * which in Lava Reef is after the seamless act change, so both thresholds are read against act 2's
 * rebased camera and never against act 1's {@code $2C00}. The sibling at {@code $2C0} therefore
 * fires long before the waiter at {@code $940}.
 */
class TestLrzPostDefeatCameraRelease {

    private TestObjectServices services;
    private Camera camera;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        SessionManager.clear();
        camera = TestEnvironment.activeGameplayMode().getCamera();
        camera.resetState();
        services = new TestObjectServices().withIsolatedObjectManager().withCamera(camera)
                .withGameState(com.openggf.game.GameServices.gameState());
    }

    @Test
    void centeredActTwoViewReleasesAtNativeThresholds() {
        var config = org.mockito.Mockito.mock(com.openggf.configuration.SonicConfigurationService.class);
        org.mockito.Mockito.when(config.getShort(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS))
                .thenReturn((short)800);
        org.mockito.Mockito.when(config.getString(com.openggf.configuration.SonicConfiguration.WIDESCREEN_DEADZONE_MODE))
                .thenReturn("CENTER_SCALED");
        camera = new Camera(config);
        var state = new com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState(9,1,
                com.openggf.game.PlayerCharacter.SONIC_ALONE);
        state.setCenterNativeArenaCamera(true);
        services.withCamera(camera).zoneRuntimeRegistry().install(state);
        services.gameState().setEndOfLevelFlag(true);
        for (var gate : LrzPostDefeatCameraReleaseInstance.Gate.values()) {
            int threshold = gate == LrzPostDefeatCameraReleaseInstance.Gate.WAITER ? 0x940 : 0x2C0;
            var release = new LrzPostDefeatCameraReleaseInstance(gate);
            release.setServices(services);
            camera.setMinX((short)0);
            camera.setX((short)(threshold-240-1));
            release.update(0,null);
            assertFalse(release.isDestroyed());
            assertEquals(0,camera.getMinX());
            camera.setX((short)(threshold-240));
            release.update(1,null);
            assertTrue(release.isDestroyed());
            assertEquals(threshold,camera.getMinX(),"write the native boundary, not the visible left edge");
        }
    }

    private LrzPostDefeatCameraReleaseInstance waiter() {
        LrzPostDefeatCameraReleaseInstance object = new LrzPostDefeatCameraReleaseInstance(
                LrzPostDefeatCameraReleaseInstance.Gate.WAITER);
        object.setServices(services);
        return object;
    }

    /** Only slots the object manager holds: the waiter itself is constructed directly here. */
    private List<LrzPostDefeatCameraReleaseInstance> live() {
        return services.objectManager().getActiveObjects().stream()
                .filter(LrzPostDefeatCameraReleaseInstance.class::isInstance)
                .map(LrzPostDefeatCameraReleaseInstance.class::cast)
                .filter(o -> !o.isDestroyed())
                .toList();
    }

    /** {@code tst.b (End_of_level_flag).w / beq.w locret_78536} (sonic3k.asm:160506-160507). */
    @Test
    void theWaiterDoesNothingAndAllocatesNothingUntilTheLevelHasEnded() {
        LrzPostDefeatCameraReleaseInstance waiter = waiter();
        services.gameState().setEndOfLevelFlag(false);
        // The act 1 arena camera, which is past BOTH thresholds: if the flag were not the gate,
        // this frame would write Camera_min_X_pos and drag the arena bound backwards.
        camera.setX((short) 0x2C00);
        camera.setMinX((short) 0x2C00);

        for (int frame = 0; frame < 120; frame++) {
            waiter.update(frame, null);
        }

        assertTrue(waiter.isAwaitingEndOfLevel(), "the flag has not been set");
        assertFalse(waiter.isDestroyed(), "nothing has released");
        assertEquals(0x2C00, camera.getMinX() & 0xFFFF,
                "Camera_min_X_pos must still be the arena's");
        assertEquals(0, live().size(), "loc_78B08 is not allocated before the flag");
    }

    /**
     * {@code loc_78AA8} on the frame the flag passes: it allocates {@code loc_78B08} and falls
     * straight into {@code loc_78AE6}, whose own test fails at act 2's starting camera.
     */
    @Test
    void theFlagAllocatesTheSiblingAndBothWaitForTheirOwnCameraX() {
        LrzPostDefeatCameraReleaseInstance waiter = waiter();
        // The act 2 camera the seamless change leaves: x 0, min 0.
        camera.setX((short) 0);
        camera.setMinX((short) 0);
        services.gameState().setEndOfLevelFlag(true);

        waiter.update(0, null);

        assertFalse(waiter.isAwaitingEndOfLevel(), "the flag passed");
        assertFalse(waiter.isDestroyed(), "camera x 0 is short of $940");
        assertEquals(1, live().size(), "jsr (AllocateObject) / move.l #loc_78B08,(a1)");
        assertEquals(0, camera.getMinX() & 0xFFFF, "neither threshold has been reached");

        // A second dispatch must not allocate a third slot.
        waiter.update(1, null);
        assertEquals(1, live().size(), "the allocation happens once");
    }

    /**
     * {@code loc_78B08} (sonic3k.asm:160536-160539): the sibling is the one that fires first,
     * because {@code $2C0} is the nearer threshold, and it is the one that carries act 2's
     * palette.
     */
    @Test
    void theSiblingReleasesAtItsOwnThresholdAndTheWaiterKeepsWaiting() {
        LrzPostDefeatCameraReleaseInstance waiter = waiter();
        camera.setX((short) 0);
        camera.setMinX((short) 0);
        services.gameState().setEndOfLevelFlag(true);
        waiter.update(0, null);

        camera.setX((short) LrzPostDefeatCameraReleaseInstance.SIBLING_CAMERA_X);
        for (ObjectInstance object : services.objectManager().getActiveObjects()) {
            if (object instanceof LrzPostDefeatCameraReleaseInstance release
                    && release.gate() == LrzPostDefeatCameraReleaseInstance.Gate.SIBLING) {
                release.update(1, null);
                assertTrue(release.isDestroyed(), "jmp (Delete_Current_Sprite) (sonic3k.asm:160547)");
            }
        }
        assertEquals(LrzPostDefeatCameraReleaseInstance.SIBLING_CAMERA_X,
                camera.getMinX() & 0xFFFF, "move.w #$2C0,(Camera_min_X_pos).w");

        waiter.update(2, null);
        assertFalse(waiter.isDestroyed(), "$2C0 is well short of the waiter's own $940");

        camera.setX((short) LrzPostDefeatCameraReleaseInstance.WAITER_CAMERA_X);
        waiter.update(3, null);
        assertTrue(waiter.isDestroyed(), "loc_78AE6 deletes once Camera_X_pos reaches $940");
        assertEquals(LrzPostDefeatCameraReleaseInstance.WAITER_CAMERA_X,
                camera.getMinX() & 0xFFFF, "move.w #$940,(Camera_min_X_pos).w");
    }
}
