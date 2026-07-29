package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kZoneRuntimeState;
import com.openggf.level.BigRingReturnState;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Immutable identity copied from Obj_SSEntryRing into Obj_SSEntryFlash.
 *
 * <p>Saved2 values and routing are intentionally not stored here: the ROM
 * reads both at SSEntryFlash_GoSS, after the animation and $20-frame wait.
 */
record S3kBigRingTransitionIntent(int rawSubtype, ObjectRefId parentRingId) {

    int ringBit() {
        return rawSubtype & 0x1F;
    }

    void complete(ObjectServices services, AbstractPlayableSprite player) {
        int originZone = services.currentZone();
        int originAct = services.currentAct();
        var camera = services.camera();
        var runtimeState = services.zoneRuntimeState();
        int resizeRoutine = runtimeState instanceof S3kZoneRuntimeState s3kState
                ? s3kState.getDynamicResizeRoutine()
                : 0;
        int meanWaterLevel = 0;
        var water = services.waterSystem();
        if (water != null && water.hasWater(originZone, originAct)) {
            meanWaterLevel = water.getWaterLevelY(originZone, originAct);
        }
        var checkpoint = services.checkpointState();
        BigRingReturnState returnState = new BigRingReturnState(
                player.getCentreX(), player.getCentreY(),
                camera.getX(), camera.getY(), player.getRingCount(),
                player.getTopSolidBit(), player.getLrbSolidBit(),
                camera.getMaxY(), resizeRoutine, meanWaterLevel,
                originZone, originAct,
                checkpoint != null ? checkpoint.getLastCheckpointIndex() : -1,
                checkpoint != null ? checkpoint.getStarPostActivationMark() : -1,
                checkpoint != null ? checkpoint.getSavedX() : 0,
                checkpoint != null ? checkpoint.getSavedY() : 0,
                checkpoint != null ? checkpoint.getSavedCameraX() : 0,
                checkpoint != null ? checkpoint.getSavedCameraY() : 0);
        services.saveBigRingReturn(returnState);
        services.gameState().markSpecialRingCollected(ringBit());

        boolean forced = (rawSubtype & 0x80) != 0;
        boolean skSide = originZone >= Sonic3kZoneIds.ZONE_MHZ
                || originZone == Sonic3kZoneIds.ZONE_FBZ;
        boolean sanctuary = forced || (skSide && services.gameState().hasAllEmeralds());
        if (!sanctuary) {
            services.requestSpecialStageEntry();
            return;
        }
        if (checkpoint != null) {
            int activationMark = checkpoint.getStarPostActivationMark();
            checkpoint.restoreFromSaved(
                    checkpoint.getSavedX(), checkpoint.getSavedY(),
                    checkpoint.getSavedCameraX(), checkpoint.getSavedCameraY(), 0);
            checkpoint.restoreStarPostActivationMark(activationMark);
        }
        services.requestZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1, true);
    }
}
