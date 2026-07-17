package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.objects.FbzOutdoorBgMotionObjectInstance;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestFbzAct2RomRuntimeLifecycle {
    @Test
    void act2LevelEventInitializationClaimsNativeFirstDynamicSlotBeforePlacement() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        GraphicsManager.getInstance().initHeadless();
        Sonic sonic = new Sonic(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE),
                (short) 0xD80, (short) 0xA40);
        GameServices.sprites().addSprite(sonic);
        Camera camera = GameServices.camera();
        camera.setFocusedSprite(sonic);
        camera.setFrozen(false);

        LevelManager levels = GameServices.level();
        levels.loadZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1);

        var motions = levels.getObjectManager().getActiveObjects().stream()
                .filter(FbzOutdoorBgMotionObjectInstance.class::isInstance)
                .map(FbzOutdoorBgMotionObjectInstance.class::cast)
                .toList();
        assertEquals(1, motions.size(),
                "Act 2 runtime installation must create the persistent outdoor motion controller");
        FbzOutdoorBgMotionObjectInstance motion = motions.getFirst();
        assertEquals(4, motion.getSlotIndex(),
                "Obj_FBZOutdoorBGMotion must occupy native Dynamic_object_RAM slot 4 before ObjPosLoad");
    }

    @Test
    void priorAuthoritativePlaneDoesNotLeakOwnershipIntoFreshAct2EventState() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        GraphicsManager.getInstance().initHeadless();
        Sonic sonic = new Sonic(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE),
                (short) 0xD80, (short) 0xA40);
        GameServices.sprites().addSprite(sonic);
        Camera camera = GameServices.camera();
        camera.setFocusedSprite(sonic);
        camera.setFrozen(false);
        LevelManager levels = GameServices.level();
        levels.loadZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1);
        GroundSensor.setLevelManager(levels);
        camera.updatePosition(true);

        levels.seedBackgroundVdpPlaneFromWorld(0x200);
        assertTrue(levels.getTilemapManager().isRetainedBackgroundPlaneAuthoritative());

        Sonic3kFBZEvents freshEvents = new Sonic3kFBZEvents();
        freshEvents.init(1);
        FbzZoneRuntimeState freshRuntime = new FbzZoneRuntimeState(
                1, PlayerCharacter.SONIC_ALONE, freshEvents);
        byte[] state = freshRuntime.captureBytes();
        freshRuntime.restoreBytes(state);

        assertEquals(0, freshEvents.captureRetainedPlaneSnapshot().length,
                "a fresh event instance must not adopt another owner's retained plane");
    }

    @Test
    void activeRedrawRestoresExactRetainedPlaneAndProgressThroughProductionReconcile() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        GraphicsManager.getInstance().initHeadless();
        Sonic sonic = new Sonic(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE),
                (short) 0xD80, (short) 0xA40);
        GameServices.sprites().addSprite(sonic);
        Camera camera = GameServices.camera();
        camera.setFocusedSprite(sonic);
        camera.setFrozen(false);
        LevelManager levels = GameServices.level();
        levels.loadZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1);
        GroundSensor.setLevelManager(levels);
        camera.updatePosition(true);

        Sonic3kLevelEventManager manager = (Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider();
        Sonic3kFBZEvents events = manager.getFbzEvents();
        events.initializeAct2Runtime();
        events.setForegroundLayoutRegion(4);
        events.updateAct2BackgroundEvent(0xD80, 0xA40, false);
        assertEquals(1, events.getBackgroundRedrawProgress());

        FbzZoneRuntimeState runtime = (FbzZoneRuntimeState)
                GameServices.zoneRuntimeRegistry().current();
        byte[] expectedPlane = levels.captureBackgroundVdpPlane();
        byte[] rewindState = runtime.captureBytes();

        for (int i = 0; i < 6; i++) {
            events.updateAct2BackgroundEvent(0xD80, 0xA41, false);
        }
        assertFalse(Arrays.equals(expectedPlane, levels.captureBackgroundVdpPlane()),
                "advancing the real staged redraw must change retained Plane-B bytes");

        runtime.restoreBytes(rewindState);
        manager.reconcileAfterRewindRestore();
        // This is the remaining half of the production post-restore callback.
        levels.getTilemapManager().resetTilemapsForRewindRestore();

        assertEquals(1, events.getBackgroundRedrawProgress());
        assertArrayEquals(expectedPlane, levels.captureBackgroundVdpPlane(),
                "the next real ensure must retain the exact rewound 64x32 Plane-B image");
    }
}
