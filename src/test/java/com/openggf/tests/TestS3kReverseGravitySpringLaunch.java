package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Sonic3kSpringObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The two spring launch rows of the reverse-gravity table: {@code sub_22F98}
 * (sonic3k.asm:47720-47726) and {@code sub_233CA} (:48093-48098). Each nudges the
 * launched player's {@code y_pos} by 8 px and reverses that nudge under
 * {@code Reverse_gravity_flag}.
 *
 * <p><strong>Which spring the inverted player actually meets.</strong> {@code Spring_Up}'s
 * init jumps to the {@code Obj_Spring_Down} body under the flag and {@code Spring_Down}'s to
 * {@code Obj_Spring_Up}'s (:47576-47637, already covered). Under reverse gravity the player
 * falls <em>up</em> the screen and stands on ceilings, so it is the authored <em>down</em>
 * spring that ends up underfoot there, running the up-spring body — and that body's launch
 * velocity, negative, integrates through {@code MoveSprite_TestGravity}'s negated copy into
 * down-screen motion, away from the ceiling. Testing the launch nudge therefore has to pair
 * each gravity with the authored subtype whose body it selects, or the init swap and the
 * launch mirror cancel and the assertion cannot disagree.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kReverseGravitySpringLaunch {

    /** {@code (subtype >> 3) & $E}: 0 selects the up spring, 4 the down spring. */
    private static final int SUBTYPE_UP = 0x00;
    private static final int SUBTYPE_DOWN = 0x20;

    private static final int START_X = 0x1ACC;
    private static final int START_Y = 0x0540;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /** {@code sub_22F98}: {@code addq.w #8,y_pos(a1)}, upright. */
    @Test
    void theUprightUpSpringPushesThePlayerEightPixelsDown() {
        assertEquals(8, launchDelta(false, SUBTYPE_UP, Contact.STANDING),
                "addq.w #8,y_pos(a1)");
    }

    /**
     * And inverted, on the spring an inverted player actually stands on: the authored down
     * spring, which the init swap turns into the up-spring body. {@code subi.w #2*8} makes
     * the same nudge −8.
     */
    @Test
    void theInvertedUpSpringBodyPushesThePlayerEightPixelsUp() {
        assertEquals(-8, launchDelta(true, SUBTYPE_DOWN, Contact.STANDING),
                "sub_22F98: subi.w #2*8 under Reverse_gravity_flag");
    }

    /** {@code sub_233CA}: {@code subq.w #8,y_pos(a1)}, upright. */
    @Test
    void theUprightDownSpringPushesThePlayerEightPixelsUp() {
        assertEquals(-8, launchDelta(false, SUBTYPE_DOWN, Contact.BOTTOM),
                "subq.w #8,y_pos(a1)");
    }

    /** And inverted, through the authored up spring the init swap turns into that body. */
    @Test
    void theInvertedDownSpringBodyPushesThePlayerEightPixelsDown() {
        assertEquals(8, launchDelta(true, SUBTYPE_UP, Contact.BOTTOM),
                "sub_233CA: addi.w #2*8 under Reverse_gravity_flag");
    }

    private enum Contact { STANDING, BOTTOM }

    private int launchDelta(boolean reverseGravity, int subtype, Contact contact) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(reverseGravity);

            var objectManager = GameServices.level().getObjectManager();
            Sonic3kSpringObjectInstance spring = objectManager.createDynamicObject(
                    () -> new Sonic3kSpringObjectInstance(
                            new ObjectSpawn(START_X, START_Y, 0x07, subtype, 0, false, 0)));

            // Obj_Spring's init runs in the object's own update, before any contact; it is
            // the init that chooses which launch body this placement uses.
            spring.update(0, sprite);

            sprite.setCentreY((short) START_Y);
            int before = sprite.getCentreY();
            spring.onSolidContact(sprite, contact == Contact.STANDING
                    ? new SolidContact(true, false, false, true, false)
                    : new SolidContact(false, false, true, false, false), 0);
            return sprite.getCentreY() - before;
        } finally {
            SessionManager.clear();
        }
    }
}
