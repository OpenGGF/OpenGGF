package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.HPZSSEntryControlObjectInstance;
import com.openggf.game.sonic3k.objects.HPZSanctuaryFallingCrystalObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production-lifecycle coverage for the Hidden Palace Super Emerald sanctuary.
 *
 * <p>Unlike the object-local tests, this boots the ROM-backed HPZ mini-level
 * and advances the real placement, object-manager, camera and player pipeline.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kHpzSanctuaryHeadless {

    @Test
    void sanctuaryWithoutChaosEmeraldsUnlocksWithoutConversionPan() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        fixture.stepIdleFrames(180);

        assertFalse(sonic.isObjectControlled(),
                "loc_90A94 goes directly to loc_90C16 when no state-1 emerald exists");
        assertFalse(GameServices.level().getObjectManager().getActiveObjects().stream()
                        .anyMatch(object -> object.getClass().getSimpleName()
                                .contains("SanctuarySmallEmerald")),
                "the seven-small-Emerald ceremony only exists for state-1 conversion");
    }

    @Test
    void freshSanctuaryStartsItsCeremonyAtTheSpawnCameraAndRestoresSonic() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();
        ObjectManager objects = GameServices.level().getObjectManager();
        HPZSSEntryControlObjectInstance controller = objects.getActiveObjects().stream()
                .filter(HPZSSEntryControlObjectInstance.class::isInstance)
                .map(HPZSSEntryControlObjectInstance.class::cast)
                .findFirst().orElseThrow();
        GameServices.gameState().restoreS3kEmeraldProgress(
                List.of(1, 1, 1, 1, 1, 1, 1), false);

        assertTrue(objects.getActiveObjects().stream()
                        .anyMatch(HPZSSEntryControlObjectInstance.class::isInstance),
                "the ROM HPZ mini-level controller must be active at the initial camera");

        fixture.stepIdleFrames(40);
        assertTrue(objects.getActiveObjects().stream()
                        .anyMatch(HPZSanctuaryFallingCrystalObjectInstance.class::isInstance),
                "the intro crystal must begin without moving Sonic or the camera");

        fixture.stepIdleFrames(100);
        assertTrue(objects.getActiveObjects().stream()
                        .anyMatch(object -> object.getClass().getSimpleName()
                                .contains("SanctuarySmallEmerald")),
                "the ROM immediately starts the seven-Chaos-Emerald orbit ceremony");

        int completionFrame = -1;
        for (int frame = 140; frame < 2_400; frame++) {
            fixture.stepIdleFrames(1);
            if (!sonic.isObjectControlled()) {
                completionFrame = frame;
                break;
            }
        }
        assertTrue(completionFrame >= 0,
                "the complete fresh ceremony must restore player control within its ROM sequence; "
                        + controller.ceremonyStateForTest());
        assertTrue(completionFrame <= 1_100,
                "loc_90C2A counts the $1F inter-drop timer while the previous "
                        + "crystal remains active; ceremony completed at " + completionFrame);
        assertFalse(sonic.isObjectControlled(),
                "the complete fresh ceremony must restore player control");
        assertFalse(sonic.isHidden(),
                "the complete fresh ceremony must leave Sonic visible");
        NativePositionOps.writeXPosPreserveSubpixel(sonic, 0x1600);
        fixture.stepIdleFrames(4);
        assertFalse(sonic.isObjectControlled(),
                "loc_90C16 permanently advances to loc_90C34; fresh-entry control "
                        + "must not be reapplied after unlock");
        assertFalse(sonic.isObjectMappingFrameControl(),
                "ceremony mapping ownership must remain released");
        assertTrue((sonic.getCentreX() & 0xFFFF) < 0x1640,
                "post-unlock frames must not teleport Sonic back to the sanctuary centre");
        var frameBounds = sonic.getSpriteRenderer().getFrameBounds(
                sonic.getMappingFrame(), sonic.getRenderHFlip(), sonic.getRenderVFlip());
        assertTrue(frameBounds.maxX() >= frameBounds.minX()
                        && frameBounds.maxY() >= frameBounds.minY(),
                "the restored Sonic mapping/DPLC frame must contain drawable pieces");
    }
}
