package com.openggf.sprites.playable;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The player is drawn upside down while {@code Reverse_gravity_flag} is set.
 *
 * <p>{@code loc_10C62} (sonic3k.asm:22007-22013), {@code loc_138C8} (:26251-26257) and
 * {@code loc_16614} (:30450-30456) apply {@code eori.b #2,render_flags(a0)} after the
 * animator, and {@code sub_125E0} (:24711-24718), {@code sub_15842} (:29336),
 * {@code sub_17D1E} (:33017) and {@code loc_15A7A} (:29591-29597) repeat it in the hurt,
 * dead and rotation paths.
 *
 * <p>The mirror is composed at the draw rather than written into the stored flip, and this
 * test asserts <em>both</em> halves of that. The engine's {@code renderVFlip} is not the
 * ROM's {@code render_flags} bit 1: it also carries native mapping orientation — the flipped
 * fourth slope bank and the negative-flip-type tumble — which an earlier version of this
 * change erased, failing seven cases in {@code TestPlayableSpriteAnimation} and
 * {@code TestHeadlessTestFixture}. This class lives in {@code com.openggf.sprites.playable}
 * because {@code renderVFlipForDraw()} is package-private, which is what keeps it off the
 * {@code @ModApi} surface {@code AbstractPlayableSprite} pins.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kReverseGravityRenderMirror {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void theFlagMirrorsTheDrawWithoutTouchingTheAnimatorsOwnFlip() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();

            fixture.stepIdleFrames(1);
            assertFalse(sprite.renderVFlipForDraw(), "no flag, no mirror");
            boolean storedWhileUpright = sprite.getRenderVFlip();

            GameServices.gameState().setReverseGravityActive(true);
            fixture.stepIdleFrames(1);
            assertTrue(sprite.renderVFlipForDraw(), "loc_10C62 sets render_flags bit 1");
            assertFalse(sprite.getRenderVFlip() != storedWhileUpright,
                    "the animator's own flip must be untouched — it carries mapping orientation");

            GameServices.gameState().setReverseGravityActive(false);
            fixture.stepIdleFrames(1);
            assertFalse(sprite.renderVFlipForDraw(), "and the mirror goes away with the flag");
        } finally {
            SessionManager.clear();
        }
    }
}
