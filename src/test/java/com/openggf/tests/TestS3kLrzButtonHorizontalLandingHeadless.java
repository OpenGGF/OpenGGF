package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.LrzButtonHorizontalObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code Obj_LRZButtonHorizontal} is a full solid, and a falling player lands on its top.
 *
 * <p>{@code loc_42D16} (sonic3k.asm:88236-88242) loads {@code d1 = $10}, {@code d2 = $F},
 * {@code d3 = $10} and {@code d4 = x_pos(a0)} and calls {@code SolidObjectFull}. There is no
 * {@code Sprite_OnScreen_Test} and no routine gate in front of it: the button is solid from the
 * frame it is loaded, and {@code d3} is the height a landing player is placed above
 * {@code y_pos(a0)}, so a player of {@code y_radius} 19 comes to rest at
 * {@code y_pos(a0) - $10 - 19}.
 *
 * <p>This is the cold act 1 route's own landing. The `lrz` fixture's Player 1 falls past the
 * placement at {@code ($10C2,$325)} with {@code player_stand_on_obj} already naming its slot
 * ({@code $0D}) and, on native row 3155, stops dead at {@code y $302} with {@code air 0} and
 * {@code y_speed 0} while {@code g_speed} takes the frame's {@code x_vel}. {@code $325 - $10 - 19}
 * is exactly {@code $302}. The route's first divergence was the engine falling straight through
 * that button, so the landing is asserted here at the placement's own coordinates.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzButtonHorizontalLandingHeadless {

    /** The act 1 placement the cold route falls onto: {@code ($10C2,$325)}. */
    private static final int BUTTON_X = 0x10C2;
    private static final int BUTTON_Y = 0x0325;
    /** {@code y_pos(a0) - d3 - y_radius} = {@code $325 - $10 - 19}, native row 3155's own y. */
    private static final int LANDING_Y = 0x0302;
    /** Native row 3154: the last airborne frame, five pixels above the rest position. */
    private static final int FALL_FROM_Y = 0x0302 - 5;
    private static final int FALL_FROM_X = 0x10B2;
    /** Native row 3154 {@code player_y_speed}. */
    private static final short FALL_Y_SPEED = (short) 0x0530;

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void aFallingPlayerComesToRestOnTheButtonTop() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0)
                .startPosition((short) FALL_FROM_X, (short) (BUTTON_Y - 0x80))
                .startPositionIsCentre()
                .withSkippedZoneIntro()
                .build();
        AbstractPlayableSprite player = fixture.sprite();
        fixture.stepFrame(false, false, false, false, false);

        LrzButtonHorizontalObjectInstance button = buttonAt(fixture);
        assertNotNull(button, "precondition: the placed button is live at ("
                + BUTTON_X + "," + BUTTON_Y + ")");

        player.setForcedAnimationId(-1);
        player.setCentreX((short) FALL_FROM_X);
        player.setCentreY((short) FALL_FROM_Y);
        player.setAir(true);
        player.setYSpeed(FALL_Y_SPEED);

        StringBuilder log = new StringBuilder();
        for (int frame = 0; frame < 6 && player.getAir(); frame++) {
            fixture.stepFrame(false, false, false, false, false);
            log.append("\n  f").append(frame)
                    .append(" pos=(").append(player.getCentreX()).append(",")
                    .append(player.getCentreY()).append(")")
                    .append(" yspd=").append(player.getYSpeed())
                    .append(" air=").append(player.getAir())
                    .append(" relX=").append(player.getCentreX() - button.getCollisionX() + 0x10)
                    .append(" relY=").append(player.getCentreY() - button.getCollisionY()
                            + 4 + 0x0F + player.getYRadius());
        }

        assertFalse(player.getAir(),
                "loc_42D16's SolidObjectFull lands the player on the button (sonic3k.asm:88236-88240)"
                        + log);
        assertEquals(LANDING_Y, player.getCentreY() & 0xFFFF,
                "d3 = $10 places a landing player at y_pos(a0) - $10 - y_radius" + log);
    }

    private static LrzButtonHorizontalObjectInstance buttonAt(HeadlessTestFixture fixture) {
        for (ObjectInstance o : fixture.runtime().getLevelManager().getObjectManager()
                .getActiveObjects()) {
            if (o instanceof LrzButtonHorizontalObjectInstance b && b.getSpawn() != null
                    && (b.getSpawn().x() & 0xFFFF) == BUTTON_X
                    && (b.getSpawn().y() & 0xFFFF) == BUTTON_Y) {
                return b;
            }
        }
        return null;
    }
}
