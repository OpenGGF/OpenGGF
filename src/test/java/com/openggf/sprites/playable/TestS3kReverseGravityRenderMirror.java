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
            // Test draw composition without another physics step: reversed
            // terrain contact can legitimately change the animator's own flip.
            assertTrue(sprite.renderVFlipForDraw(), "loc_10C62 sets render_flags bit 1");
            assertFalse(sprite.getRenderVFlip() != storedWhileUpright,
                    "the animator's own flip must be untouched — it carries mapping orientation");

            GameServices.gameState().setReverseGravityActive(false);
            assertFalse(sprite.renderVFlipForDraw(), "and the mirror goes away with the flag");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * A player under object mapping-frame control is still mirrored.
     *
     * <p>This is the case Tails' carry needs. {@code loc_14492} (sonic3k.asm:27290-27299) and
     * {@code sub_1459E} (:27395-27411) set {@code object_control} on the carried player — so
     * {@code Animate_Sonic} does not run for it — and then do the mirroring themselves, with
     * the same {@code andi.b #$FC,render_flags} / {@code or.b d0} / {@code eori.b #2} shape
     * the animators use. The net is unchanged: the carried player's Y-flip equals the flag.
     * {@code renderVFlipForDraw} composes the flag for every draw rather than only the ones
     * the animator reached, which is what makes those two rows fall out of the same owner.
     */
    @Test
    void theMirrorSurvivesObjectMappingFrameControl() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            sprite.setObjectMappingFrameControl(true);

            GameServices.gameState().setReverseGravityActive(true);
            fixture.stepIdleFrames(1);
            assertTrue(sprite.isObjectMappingFrameControl(),
                    "the fixture must still be in the carried/scripted mapping state");
            assertTrue(sprite.renderVFlipForDraw(),
                    "loc_14492 / sub_1459E mirror the carried player even though the animator "
                            + "is skipped");

            GameServices.gameState().setReverseGravityActive(false);
            fixture.stepIdleFrames(1);
            assertFalse(sprite.renderVFlipForDraw(),
                    "and a carried player is upright again with the flag clear");
        } finally {
            SessionManager.clear();
        }
    }
    @Test
    void separateTailsMirrorStandardAnimationsButPreserveDirectionalFlips() {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1).build();
        var sprite = fixture.sprite();
        var renderer = org.mockito.Mockito.mock(com.openggf.sprites.render.PlayerSpriteRenderer.class);
        var tails = new com.openggf.sprites.managers.TailsTailsController(sprite, renderer, true);
        for (int animation : new int[] {5, 9, 0x20, 2}) {
            sprite.setAnimationId(animation);
            GameServices.gameState().setReverseGravityActive(false);
            tails.update();
            org.mockito.Mockito.clearInvocations(renderer);
            tails.draw();
            var upright = org.mockito.Mockito.mockingDetails(renderer).getInvocations()
                    .stream().filter(i -> i.getMethod().getName().equals("drawFrame")).findFirst().orElseThrow();
            boolean nativeFlip = (boolean) upright.getArgument(4);
            var saved = tails.captureRewindState();
            GameServices.gameState().setReverseGravityActive(true);
            org.mockito.Mockito.clearInvocations(renderer);
            tails.draw();
            org.mockito.Mockito.verify(renderer).drawFrame(
                    org.mockito.ArgumentMatchers.eq((int) upright.getArgument(0)),
                    org.mockito.ArgumentMatchers.eq((int) sprite.getRenderCentreX()),
                    org.mockito.ArgumentMatchers.eq((int) sprite.getRenderCentreY()),
                    org.mockito.ArgumentMatchers.eq((boolean) upright.getArgument(3)),
                    org.mockito.ArgumentMatchers.eq(animation == 2 ? nativeFlip : !nativeFlip));
            org.junit.jupiter.api.Assertions.assertEquals(saved, tails.captureRewindState(),
                    "drawing must not mutate the animation or accumulate the gravity XOR");
            GameServices.gameState().setReverseGravityActive(false);
            org.mockito.Mockito.clearInvocations(renderer);
            tails.draw();
            org.mockito.Mockito.verify(renderer).drawFrame(
                    org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean(),
                    org.mockito.ArgumentMatchers.eq(nativeFlip));
        }
    }

    @Test
    void reverseGravityMirrorsSpindashDustAndMovesSkidDustToTheContactSide() {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1).build();
        for (var player : new AbstractPlayableSprite[] {
                fixture.sprite(), new Tails("tails", (short) 320, (short) 940)}) {
            var renderer = org.mockito.Mockito.mock(com.openggf.sprites.render.PlayerSpriteRenderer.class);
            var dust = new com.openggf.sprites.managers.SpindashDustController(player, renderer);
            player.setSpindashDustController(dust);
            player.setSpindash(true);
            player.setAir(false);
            for (boolean reversed : new boolean[] {false, true, false}) {
                GameServices.gameState().setReverseGravityActive(reversed);
                org.mockito.Mockito.clearInvocations(renderer);
                dust.update();
                dust.draw();
                int tailAdjustment = player instanceof Tails ? (reversed ? 4 : -4) : 0;
                org.mockito.Mockito.verify(renderer).drawFrame(
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.eq((int) player.getRenderCentreX()),
                        org.mockito.ArgumentMatchers.eq(player.getRenderCentreY() + tailAdjustment),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.eq(reversed));
                var skid = com.openggf.level.objects.SkidDustObjectInstance.create(player);
                org.junit.jupiter.api.Assertions.assertNotNull(skid);
                int offset = player instanceof Tails ? 12 : 16;
                org.junit.jupiter.api.Assertions.assertEquals(
                        player.getCentreY() + (reversed ? -offset : offset), skid.getY(),
                        "loc_18D14 negates the complete per-character foot offset");
            }
        }
    }

}
