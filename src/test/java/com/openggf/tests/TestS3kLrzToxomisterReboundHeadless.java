package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.badniks.ToxomisterBadnikInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Obj_Toxomister}'s body is an ordinary enemy, and a rolling player destroys it.
 *
 * <p>{@code ObjDat_Toxomister} (sonic3k.asm:196950-196954) ends {@code dc.b 8,8,1,$18}: the last
 * byte is {@code collision_flags}. {@code Touch_ChkValue} (sonic3k.asm:20774-20776) takes only
 * bits 6-7 as the type, and {@code $18 & $C0} is zero -- the {@code Touch_Enemy} type. The low
 * six bits are the {@code Touch_Sizes} index, and entry {@code $18} (sonic3k.asm:20713+, the
 * 25th pair) is {@code dc.b 4,4}, an 8x8 box. That is a much smaller box than the {@code 8,8}
 * {@code width_pixels}/{@code height_pixels} in the same record, which are the sprite's size and
 * not its touch box.
 *
 * <p>{@code Touch_Enemy} (sonic3k.asm:20880-20886) sends a player whose {@code anim} is
 * {@code 2} (rolling) to {@code .checkhurtenemy}, and with {@code boss_hitcount2} zero that falls
 * straight into {@code Touch_EnemyNormal} (sonic3k.asm:20945-20990): the badnik takes
 * {@code status} bit 7 and is rewritten to {@code Obj_Explosion}, and the player is rebounded.
 * Which rebound depends on where the player is: {@code tst.w y_vel(a0) / bmi} bounces a rising
 * player down by {@code +$100}; otherwise {@code cmp.w y_pos(a1),d0 / bhs} bounces a player at or
 * below the enemy up by {@code -$100}; and a player <b>above</b> a falling player's enemy takes
 * the third branch, a plain {@code neg.w y_vel(a0)} with no constant at all.
 *
 * <p>That third branch is the one the cold act 1 route takes. On the fixture's own recorded
 * native input the player is rolling, airborne and falling at {@code y_vel $448} when they reach
 * the toxomister at {@code (4292,408)}; native row 2322 negates {@code player_y_speed} to
 * {@code -$450} (the frame's gravity first, then the negate) with no input, and the route's first
 * divergence was the engine falling straight through instead.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzToxomisterReboundHeadless {

    /** The act 1 body the cold route reaches, at its placed position. */
    private static final int BODY_X = 4292;
    private static final int BODY_Y = 408;

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void aRollingPlayerDestroysTheBodyAndHasYSpeedNegated() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0)
                .startPosition((short) BODY_X, (short) (BODY_Y - 68))
                .startPositionIsCentre()
                // Lava Reef act 1's cold start drops the player in the falling intro pose, whose
                // animation is not the roll Touch_Enemy reads. This case is about the touch.
                .withSkippedZoneIntro()
                .build();
        AbstractPlayableSprite player = fixture.sprite();
        fixture.stepFrame(false, false, false, false, false);

        ToxomisterBadnikInstance body = bodyAt(fixture);
        assertNotNull(body, "precondition: the placed body is live at (" + BODY_X + "," + BODY_Y + ")");
        assertFalse(body.isDestroyed(), "precondition: the body is alive");

        // The route's own state on the divergent frame: rolling, airborne, falling.
        player.setCentreX((short) (BODY_X + 1));
        player.setCentreY((short) (BODY_Y - 22));
        player.setForcedAnimationId(-1);
        player.setAir(true);
        player.setRolling(true);
        player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
        player.setYSpeed((short) 0x0418);

        StringBuilder log = new StringBuilder();
        short reboundedTo = 0;
        for (int frame = 0; frame < 8 && reboundedTo == 0; frame++) {
            // Obj_Toxomister's own act 1 neighbourhood is lethal, so the roll is re-published
            // each frame: what this case is about is the touch, not how the roll is sustained.
            // The act's own intro forces HURT_FALL; the route reaches this toxomister rolling.
            player.setForcedAnimationId(-1);
            player.setRolling(true);
            player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
            fixture.stepFrame(false, false, false, false, false);
            log.append("\n  f").append(frame)
                    .append(" pos=(").append(player.getCentreX()).append(",")
                    .append(player.getCentreY()).append(")")
                    .append(" yspd=").append(player.getYSpeed())
                    .append(" anim=").append(player.getAnimationId())
                    .append(" rolling=").append(player.getRolling())
                    .append(" air=").append(player.getAir())
                    .append(" dead=").append(player.getDead())
                    .append(" bodyAwake=").append(body.awake())
                    .append(" bodyDestroyed=").append(body.isDestroyed());
            if (player.getYSpeed() < 0) {
                reboundedTo = player.getYSpeed();
            }
        }

        assertTrue(body.isDestroyed(),
                "Touch_EnemyNormal rewrites the badnik to Obj_Explosion (sonic3k.asm:20978)" + log);
        assertTrue(reboundedTo < 0,
                "Touch_EnemyNormal's third branch negates y_vel for a player above the enemy "
                        + "(sonic3k.asm:20985-20987); y_speed stayed " + player.getYSpeed() + log);
    }

    @Test
    void cloudContactIsDeferredWithoutDamageOrBossBounceAndRepeatsWhileOverlapping() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0)
                .startPosition((short) BODY_X, (short) (BODY_Y - 68))
                .startPositionIsCentre().withSkippedZoneIntro().build();
        fixture.stepFrame(false, false, false, false, false);
        ToxomisterBadnikInstance body = bodyAt(fixture);
        assertNotNull(body);
        var cloud = body.cloud();
        assertNotNull(cloud);
        var player = fixture.sprite();
        var manager = fixture.runtime().getLevelManager().getObjectManager();
        assertEquals(0, player.getRingCount(), "zero-ring contact distinguishes attachment from damage");
        // Isolate the cloud's touch box from the neighbouring body. Exercise the actual
        // controller, without advancing player physics between the contact and its checks.
        player.setCentreX((short) (cloud.getCentreX() - 4));
        player.setCentreY((short) (cloud.getCentreY() + 12));
        player.setForcedAnimationId(-1);
        player.setAir(true);
        player.setRolling(true);
        player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
        player.setYSpeed((short) -0x400);
        manager.runTouchResponsesForPlayer(player, 2);
        assertEquals(-0x400, player.getYSpeed(), "Touch_Special never applies a boss rebound");
        assertEquals(1, cloud.getCollisionProperty(), "Touch_Special publishes P1 contact");
        assertEquals(2, cloud.routine(), "sub_8FF8C consumes contact on the object pass");
        cloud.update(2, player);
        assertEquals(2, cloud.routine(), "rolling contact is discarded");
        assertEquals(0, cloud.getCollisionProperty());

        // Unroll without leaving the cloud: every touch pass must publish contact anew.
        player.setRolling(false);
        player.setAnimationId(Sonic3kAnimationIds.WALK.id());
        manager.runTouchResponsesForPlayer(player, 3);
        assertFalse(player.getDead(), "a cloud must not kill a zero-ring player on contact");
        assertEquals(-0x400, player.getYSpeed(), "contact must not apply ordinary hurt either");
        assertEquals(1, cloud.getCollisionProperty());
        cloud.update(3, player);
        assertEquals(8, cloud.routine());
        assertEquals(1, cloud.attachedPlayerSlot());
        assertEquals(58, cloud.timer(), "the attaching hover pass still executes Obj_Wait");
    }

    private static ToxomisterBadnikInstance bodyAt(HeadlessTestFixture fixture) {
        for (ObjectInstance o : fixture.runtime().getLevelManager().getObjectManager()
                .getActiveObjects()) {
            if (o instanceof ToxomisterBadnikInstance b
                    && b.getCentreX() == BODY_X && b.getCentreY() == BODY_Y) {
                return b;
            }
        }
        return null;
    }
}
