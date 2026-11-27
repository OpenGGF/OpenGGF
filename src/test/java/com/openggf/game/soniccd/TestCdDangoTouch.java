package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.common.CdExplosion;
import com.openggf.game.soniccd.objects.shared.ObjDango;
import com.openggf.game.soniccd.objects.shared.SharedObjectCatalogue;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.game.soniccd.timetravel.CdTimeWarp;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.FullReset;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@FullReset
class TestCdDangoTouch {
    @TempDir
    Path tempDir;

    @ParameterizedTest
    @EnumSource(value = CdStagePayload.class, names = {"R71A", "R82A"})
    void standingSonicTakesEnemyDamage(CdStagePayload stage) throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var contact = approachCurledDango(disc, stage);
            contact.objects.runTouchResponsesForPlayer(contact.sonic, 1);

            assertTrue(contact.sonic.isHurt(), "A standing player takes the ordinary enemy hit");
            // LostRingInit runs in the object pass after HurtPlayer allocates the $11 owner.
            contact.objects.update(GameServices.camera().getX(), contact.sonic, List.of(), 1, false);
            assertEquals(0, contact.sonic.getRingCount());
            assertEquals(3, GameServices.level().getRingManager().getActiveLostRings().size());
            assertFalse(contact.dango.isDestroyed());
        }
    }

    @ParameterizedTest
    @EnumSource(value = CdStagePayload.class, names = {"R71A", "R82A"})
    void rollingSonicTriggersTheBumpOnDangosTurn(CdStagePayload stage) throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var contact = approachCurledDango(disc, stage);
            contact.sonic.setRolling(true);
            contact.sonic.setCentreY((short) contact.dango.getY());
            contact.sonic.setAnimationId(CdSonicAnimation.ROLL.id());
            contact.objects.runTouchResponsesForPlayer(contact.sonic, 1);

            assertEquals(1, contact.dango.getCollisionProperty(), "The player pass increments Dango's hit counter");
            assertEquals(0, contact.sonic.getXSpeed(), "Dango has yet to take its object turn");
            assertFalse(contact.sonic.isHurt());
            assertFalse(contact.dango.isDestroyed());
            contact.dango.update(1, contact.sonic);
            // loc_20F38E (objects/r7/dango.asm:169-197) and loc_20EE84 (objects/r8/dango.asm:163-191).
            assertEquals(-0x700, contact.sonic.getXSpeed(), "Dango launches Sonic straight back on its own turn");
            assertEquals(0, contact.dango.getCollisionProperty(), "Dango clears the hit after the bump");
            assertTrue(contact.sonic.getAir());
        }
    }

    @ParameterizedTest
    @EnumSource(value = CdStagePayload.class, names = {"R71A", "R82A"})
    void warpStarsTurnAStandingTouchIntoAnEnemyDefeat(CdStagePayload stage) throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var contact = approachCurledDango(disc, stage);
            CdTimeTravel timeTravel = CdTimeTravel.of(contact.sonic.currentGameModule());
            timeTravel.touchTimePost(true);
            contact.sonic.setGSpeed((short) 0x600);
            CdTimeWarp.run(timeTravel, contact.sonic, false, null);
            assertTrue(timeTravel.starsActive());
            int slot = contact.dango.getSlotIndex();
            int score = GameServices.gameState().getScore();
            contact.sonic.setYSpeed((short) -0x500);

            contact.objects.runTouchResponsesForPlayer(contact.sonic, 1);

            // loc_206E1C (r7/player_object_collide.asm:184-186) and loc_206A94 (r8/player_object_collide.asm:220-222).
            assertEquals(-0x400, contact.sonic.getYSpeed(), "The enemy defeat adds $100 while Sonic rises");
            assertTrue(contact.dango.isDestroyed(), "The enemy routine accepts warp stars");
            assertEquals(score + 100, GameServices.gameState().getScore());
            assertTrue(contact.objects.getActiveObjects().stream().anyMatch(
                    object -> object instanceof CdExplosion explosion && explosion.getSlotIndex() == slot));
            assertFalse(contact.sonic.isHurt());
        }
    }

    private Contact approachCurledDango(CdDiscDataSource disc, CdStagePayload stage) throws Exception {
        AbstractPlayableSprite sonic = CdFixtureReplay.boot(tempDir, disc, stage);
        ObjectManager objects = GameServices.level().getObjectManager();
        int dangoId = SharedObjectCatalogue.offset(stage, stage == CdStagePayload.R71A ? "dangoId" : "mmzDangoId");
        var placement = objects.getAllSpawns().stream()
                .filter(spawn -> spawn.objectId() == dangoId).findFirst().orElseThrow();
        int approachX = placement.x() + ((placement.renderFlags() & 1) == 0 ? -64 : 64);
        int cameraX = placement.x() - 160;
        GameServices.camera().setX((short) cameraX);
        GameServices.camera().setY((short) (placement.y() - 112));
        sonic.setCentreX((short) approachX);
        sonic.setCentreY((short) placement.y());
        objects.reset(cameraX);
        objects.update(cameraX, sonic, List.of(), 0, false);
        objects.refreshPostCameraRenderState();
        ObjDango dango = (ObjDango) objects.getActiveObjectForRewind(placement);
        for (int frame = 1; frame < 180 && dango.getCollisionFlags() != 0xED; frame++) {
            objects.update(cameraX, sonic, List.of(), frame, false);
            objects.refreshPostCameraRenderState();
        }
        assertEquals(0xED, dango.getCollisionFlags(), "The disc placement curls before contact");
        assertTrue(dango.isOnScreenForTouch());
        sonic.setCentreX((short) (dango.getX() - 16));
        sonic.setCentreY((short) dango.getY());
        sonic.setRingCount(3);
        sonic.setAnimationId(CdSonicAnimation.WALK.id());
        return new Contact(sonic, objects, dango);
    }

    private record Contact(AbstractPlayableSprite sonic, ObjectManager objects, ObjDango dango) {}
}
