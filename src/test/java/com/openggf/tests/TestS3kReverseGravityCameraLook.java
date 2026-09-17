package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The look-up and look-down camera pans reverse direction and change their limits under
 * {@code Reverse_gravity_flag}: {@code loc_11276}/{@code loc_112A6} (sonic3k.asm:22615-22637)
 * and {@code loc_112B0}/{@code loc_112E0} (:22638-22660), repeated verbatim for Tails
 * ({@code loc_14AA0} :27868, {@code loc_14ADA} :27891) and Knuckles ({@code loc_172A8} :31896,
 * {@code loc_172E2} :31919). The engine has one camera, so one owner covers all six rows.
 *
 * <p>Both the direction and the target change: upright the bias walks from its $60 default
 * down to 8 (look down) or up to $C8 (look up); inverted it walks up to $D8 or down to $18.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kReverseGravityCameraLook {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void uprightLookDownWalksTheBiasDownToEight() {
        assertEquals(8, panned(false, Look.DOWN), "upright look-down target is 8");
    }

    @Test
    void uprightLookUpWalksTheBiasUpToC8() {
        assertEquals(0xC8, panned(false, Look.UP), "upright look-up target is $C8");
    }

    /** loc_112A6: {@code cmpi.w #$D8,(a5)} / {@code addq.w #2,(a5)}. */
    @Test
    void invertedLookDownWalksTheBiasUpToD8() {
        assertEquals(0xD8, panned(true, Look.DOWN), "loc_112A6 pans the other way, to $D8");
    }

    /** loc_112E0: {@code cmpi.w #$18,(a5)} / {@code subq.w #2,(a5)}. */
    @Test
    void invertedLookUpWalksTheBiasDownTo18() {
        assertEquals(0x18, panned(true, Look.UP), "loc_112E0 pans the other way, to $18");
    }

    private enum Look { UP, DOWN }

    private int panned(boolean reverseGravity, Look look) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
        try {
            Camera camera = fixture.camera();
            GameServices.gameState().setReverseGravityActive(reverseGravity);
            // The pan steps 2 px a frame from the $60 default; 120 frames is well past
            // either limit, so the result is the target the ROM branch selected.
            for (int frame = 0; frame < 120; frame++) {
                if (look == Look.UP) {
                    camera.incrementLookUpBias();
                } else {
                    camera.decrementLookDownBias();
                }
            }
            return camera.getYPosBias() & 0xFFFF;
        } finally {
            SessionManager.clear();
        }
    }
}
