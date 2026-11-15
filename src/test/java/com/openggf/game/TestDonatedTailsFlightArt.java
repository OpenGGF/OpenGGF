package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tails' flight and the carry arrive in Sonic 1 by donation from Sonic 3 &amp; Knuckles. Both players
 * wear the donor's art, so the poses those routines publish have to index the donor's tables.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestDonatedTailsFlightArt {
    private static final Map<CanonicalAnimation, Sonic3kAnimationIds> FLIGHT_POSES = Map.of(
            CanonicalAnimation.TAILS_FLY, Sonic3kAnimationIds.TAILS_FLY,
            CanonicalAnimation.TAILS_FLY_ASCEND, Sonic3kAnimationIds.TAILS_FLY_ASCEND,
            CanonicalAnimation.TAILS_FLY_CARRY, Sonic3kAnimationIds.TAILS_FLY_CARRY,
            CanonicalAnimation.TAILS_FLY_CARRY_ASCEND, Sonic3kAnimationIds.TAILS_FLY_CARRY_ASCEND,
            CanonicalAnimation.TAILS_FLY_TIRED, Sonic3kAnimationIds.TAILS_FLY_TIRED,
            CanonicalAnimation.TAILS_SWIM, Sonic3kAnimationIds.TAILS_SWIM,
            CanonicalAnimation.TAILS_SWIM_ASCEND, Sonic3kAnimationIds.TAILS_SWIM_ASCEND,
            CanonicalAnimation.TAILS_SWIM_CARRY, Sonic3kAnimationIds.TAILS_SWIM_CARRY,
            CanonicalAnimation.TAILS_SWIM_TIRED, Sonic3kAnimationIds.TAILS_SWIM_TIRED);

    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    private static HeadlessTestFixture bootGreenHillWithDonatedTails() {
        var donorRom = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(donorRom != null, "Sonic 3 & Knuckles donor ROM is not available in this environment");
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setSessionOverride(SonicConfiguration.SONIC_3K_ROM, donorRom.getAbsolutePath());
        return HeadlessTestFixture.builder().withCrossGameDonation("s3k").withZoneAndAct(0, 0).build();
    }

    private static AbstractPlayableSprite tails() {
        return GameServices.sprites().getRegisteredSidekicks().getFirst();
    }

    @Test
    void donatedTailsResolvesFlightPosesInTheDonorsTable() {
        bootGreenHillWithDonatedTails();
        var tails = tails();
        assertNotNull(tails.getAnimationSet(), "donated Tails wears the donor's scripts");
        FLIGHT_POSES.forEach((pose, donorId) -> {
            assertEquals(donorId.id(), tails.resolveAnimationId(pose), pose + " in the donor's numbering");
            assertNotNull(tails.getAnimationSet().getScript(donorId.id()), pose + " script on the art he wears");
        });
    }

    @Test
    void cpuTailsCachesTheDonorsFlightPosesAlthoughHisArtIsBoundLater() throws Exception {
        bootGreenHillWithDonatedTails();
        var cpu = tails().getCpuController();
        assertNotNull(cpu);
        // The controller is built with the team, before any art exists to check a script against.
        assertEquals(Sonic3kAnimationIds.TAILS_FLY.id(), cachedPose(cpu, "flyAnimId"), "fly-in pose");
        assertEquals(Sonic3kAnimationIds.TAILS_FLY_ASCEND.id(), cachedPose(cpu, "flyAscendAnimId"));
        assertEquals(Sonic3kAnimationIds.TAILS_FLY_TIRED.id(), cachedPose(cpu, "flyTiredAnimId"));
        assertEquals(Sonic3kAnimationIds.TAILS_SWIM.id(), cachedPose(cpu, "swimAnimId"));
    }

    private static int cachedPose(Object controller, String name) throws Exception {
        var field = controller.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(controller);
    }

    @Test
    void takingFlightPublishesTheFlyingPoseInPlaceOfTheJumpBall() {
        var fixture = bootGreenHillWithDonatedTails();
        fixture.stepIdleFrames(2);
        var tails = tails();
        int roll = tails.resolveAnimationId(CanonicalAnimation.ROLL);
        tails.setAir(true);
        tails.setJumping(true);
        tails.setRolling(true);
        tails.setYSpeed((short) 0x100);
        tails.setAnimationId(roll);

        tails.getTailsFlightController().activate();

        assertEquals(Sonic3kAnimationIds.TAILS_FLY.id(), tails.getAnimationId(),
                "a falling flight shows the flying pose, not the ball he jumped in");
        tails.setYSpeed((short) -0x80);
        tails.getTailsFlightController().updateVertical(false, false, 0);
        assertEquals(Sonic3kAnimationIds.TAILS_FLY_ASCEND.id(), tails.getAnimationId());
    }

    @Test
    void sonic1FramesAnimateTheTailsTheDonorDrawsApartFromHisBody() {
        var fixture = bootGreenHillWithDonatedTails();
        var tails = tails();
        var tail = tails.getTailsTailsController();
        assertNotNull(tail, "the donor's separate tail art is bound");

        // Sonic 1 has no slot for the tails; they wait for the pass that follows its object walk.
        boolean drawn = false;
        for (int frame = 0; frame < 90 && !drawn; frame++) {
            fixture.stepIdleFrames(1);
            drawn = tail.presentation() != null;
        }
        assertTrue(drawn, "a waiting Tails swishes his tails within a second and a half of Sonic 1 frames");

        tails.setAir(true);
        tails.getTailsFlightController().activate();
        tail.update();
        var pose = tail.presentation();
        assertNotNull(pose, "the flying body has no tails of its own");
        // AniTails_Tail0B / 0C: the two propeller frames of Map_Tails_Tail.
        assertTrue(pose.mappingFrame() == 0x27 || pose.mappingFrame() == 0x28,
                "propeller frame, was 0x" + Integer.toHexString(pose.mappingFrame()));
    }

    @Test
    void carriedSonicKeepsTheDonorArtThatHoldsTheCarriedFrames() throws Exception {
        var fixture = bootGreenHillWithDonatedTails();
        fixture.stepIdleFrames(2);
        var sonic = GameServices.sprites().getMainPlayable();
        var tails = tails();
        var donorArt = CrossGameFeatureProvider.getInstance().loadPlayerSpriteArt("sonic");
        assertSameArt(donorArt, sonic.getSpriteRenderer(), "before the carry");

        tails.setAir(true);
        tails.setCentreX(sonic.getCentreX());
        tails.setCentreY((short) (sonic.getCentreY() - 0x28));
        assertTrue(tails.getTailsCarryController().tryEncounterCarry(), "Tails takes hold of Sonic");

        assertFalse(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), sonic),
                "Sonic 1's own art has no carried frames to lend");
        assertSameArt(donorArt, sonic.getSpriteRenderer(), "while carried");
        tails.getTailsCarryController().updateAfterTailsCollision(0);
        int frame = sonic.getMappingFrame();
        assertTrue(frame >= 0x90 && frame <= 0x92, "AniRaw_Tails_Carry frame, was 0x" + Integer.toHexString(frame));
        assertFalse(art(sonic.getSpriteRenderer()).mappingFrames().get(frame).pieces().isEmpty(),
                "the carried frame has sprite pieces to draw");
    }

    private static SpriteArtSet art(PlayerSpriteRenderer renderer) throws Exception {
        assertNotNull(renderer);
        var field = PlayerSpriteRenderer.class.getDeclaredField("artSet");
        field.setAccessible(true);
        return (SpriteArtSet) field.get(renderer);
    }

    private static void assertSameArt(SpriteArtSet expected, PlayerSpriteRenderer renderer, String when) throws Exception {
        var actual = art(renderer);
        assertEquals(expected.mappingFrames(), actual.mappingFrames(), "mapping source " + when);
        assertEquals(expected.dplcFrames(), actual.dplcFrames(), "DPLC source " + when);
    }
}
