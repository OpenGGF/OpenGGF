package com.openggf;

import com.openggf.game.EndingProvider;
import com.openggf.game.DemoLamppostState;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Immediate demo assembly for callers without a native production phase plan. */
final class EndingDemoLevelAssembly {
    private EndingDemoLevelAssembly() { }

    static void prepareIfUnowned(GameLoopEndingSession.Host host, EndingProvider provider, int startX, int startY) {
        if (provider instanceof com.openggf.game.internal.NativeEndingDemoAssembly nativeDemo
                && nativeDemo.ownsInitialDemoAssembly()) return;
        // Position the player at the demo start position (ROM uses center coordinates)
        DemoLamppostState lamppost = provider.getDemoLamppostState();
        String mainCode = host.mainCharacterCode();
        var sprite = host.sprites().getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite player) {
            // The immediate demo caller owns its fresh playable state.
            player.setRingCount(0);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
            // Don't set controlLocked — ROM doesn't use obj_control during demos.
            // PlayableSpriteMovement re-reads obj_control and would block all input.
            player.setControlLocked(false);
            player.setForcedInputMask(0);

            if (lamppost != null) {
                // Demo starts at a lamppost — restore saved position/camera/water
                player.setCentreX((short) lamppost.playerX());
                player.setCentreY((short) lamppost.playerY());
                player.setRingCount(lamppost.rings());
                host.camera().setX((short) lamppost.cameraX());
                host.camera().setY((short) lamppost.cameraY());
                host.camera().setMaxY((short) lamppost.cameraMaxY());

                // Restore lamppost water state
                int featureZone = host.level().getFeatureZoneId();
                int featureAct = host.level().getFeatureActId();
                WaterSystem localWater = host.water();
                localWater.setWaterLevelDirect(featureZone, featureAct,
                        lamppost.waterHeight());
                localWater.setWaterLevelTarget(featureZone, featureAct,
                        lamppost.waterHeight());
                ZoneFeatureProvider zfp = host.level().getZoneFeatureProvider();
                if (zfp != null) {
                    zfp.setWaterRoutine(lamppost.waterRoutine());
                }
            } else {
                // All other demos: use startpos (center coordinates)
                player.setCentreX((short) startX);
                player.setCentreY((short) startY);
            }
        }

        // Snap camera to player position (unless lamppost demo has explicit camera coords)
        if (lamppost == null) {
            host.camera().updatePosition(true);
        }

        // Prepare the viewport and native player initialization before the fade.
        host.level().prepareEndingDemoScene();
    }
}
