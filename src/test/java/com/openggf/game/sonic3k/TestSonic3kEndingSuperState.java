package com.openggf.game.sonic3k;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioAdmissionObserver;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.driver.SfxContentionObserver;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.LevelRingDisplay;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PowerUpSpawner;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.SuperState;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ROM-ending entry vectors: sub_5FC8E/sub_5FCB4/sub_5FCCE and Ending_Give_SuperSonic. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestSonic3kEndingSuperState {
    @BeforeEach void setUp() {
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
        GameServices.level().resetLevelGamestate(GameModuleRegistry.getCurrent().createLevelState());
    }

    @Test void rescueSuperWritesItsPaletteAndMappingsWithoutPhysicsRingsOrControlRelease() throws Exception {
        var player = new Sonic("sonic", (short) 0, (short) 0);
        var controller = bind(player);
        PhysicsProfile original = player.getPhysicsProfile();
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setAnimationId(15);
        controller.initializeEndingSuperSonic();
        assertEquals(SuperState.SUPER, controller.getState());
        assertEquals(S3kFormTier.SUPER, controller.getActiveFormTier());
        assertEquals(-1, controller.captureRewindState().paletteState());
        assertEquals(0x24, controller.captureRewindState().paletteFrame());
        assertEquals(0, controller.captureRewindState().paletteTimer());
        assertEquals(0, player.getAnimationId());
        for (int tick = 0; tick < 120; tick++) controller.update();
        assertEquals(7, player.getRingCount(), "sub_5FC8E never awards or drains rings");
        assertSame(original, player.getPhysicsProfile(), "the rescue routine never writes Max_speed");
        assertTrue(player.isObjectControlled());
    }

    @Test void rescueHyperAndTailsDoNotCreateReunionStarsOrBirds() throws Exception {
        var sonic = new Sonic("sonic", (short) 0, (short) 0);
        var hyper = bind(sonic);
        PowerUpSpawner sonicSpawns = mock(PowerUpSpawner.class);
        sonic.setPowerUpSpawner(sonicSpawns);
        // Sonic binds his persistent Insta-Shield during spawner setup. The
        // ending routine's observation window starts after that ordinary owner.
        clearInvocations(sonicSpawns);
        hyper.initializeEndingHyperSonic();
        assertTrue(hyper.isHyperFormActive());
        assertEquals(0, hyper.captureRewindState().paletteFrame());
        assertEquals(0, hyper.captureRewindState().paletteTimer());
        assertNull(hyper.currentTrailSample(0), "sub_5FCCE installs no after-image owner");
        verifyNoInteractions(sonicSpawns);

        var tails = new Tails("tails", (short) 0, (short) 0);
        var superTails = bind(tails);
        PowerUpSpawner tailsSpawns = mock(PowerUpSpawner.class);
        tails.setPowerUpSpawner(tailsSpawns);
        clearInvocations(tailsSpawns);
        superTails.initializeEndingSuperTails();
        superTails.update();
        assertTrue(superTails.isSuperTailsFormActive());
        assertEquals(7, tails.getRingCount());
        verifyNoInteractions(tailsSpawns);
    }

    @Test void rescueRevertRetainsNativeControlPhysicsAndMapsUntilFollowingDispatch() throws Exception {
        var player = new Sonic("sonic", (short) 0, (short) 0);
        var controller = bind(player);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        PhysicsProfile original = player.getPhysicsProfile();
        PlayerSpriteRenderer normal = mock(PlayerSpriteRenderer.class);
        PlayerSpriteRenderer powered = mock(PlayerSpriteRenderer.class);
        player.setSpriteRenderer(normal);
        var poweredRendererField = Sonic3kSuperStateController.class.getDeclaredField("superRenderer");
        poweredRendererField.setAccessible(true);
        poweredRendererField.set(controller, powered);
        controller.initializeEndingSuperSonic();
        assertSame(powered, player.getSpriteRenderer());
        controller.beginEndingRescueRevert();
        assertFalse(player.isSuperSonic());
        assertEquals(SuperState.NORMAL, controller.getState());
        assertEquals(15, player.getAnimationId());
        assertEquals(2, controller.captureRewindState().paletteState());
        assertEquals(0x1E, controller.captureRewindState().paletteFrame());
        assertTrue(player.isObjectControlled());
        assertSame(original, player.getPhysicsProfile());
        assertEquals(0, player.getInvincibleFrames(), "ending revert does not schedule music-restoring grace");
        assertSame(powered, player.getSpriteRenderer(), "loc_5FDE0 leaves Map_SuperSonic for one dispatch");
        var snapshot = player.captureRewindState();
        controller.completeEndingRescueRevertMappings();
        assertSame(normal, player.getSpriteRenderer());
        player.restoreRewindState(snapshot);
        assertSame(powered, player.getSpriteRenderer(), "rewind retains the pending mappings edge");
        controller.completeEndingRescueRevertMappings();
        assertSame(normal, player.getSpriteRenderer());
        assertTrue(player.isObjectControlled());
    }

    @Test void knucklesPoweredFlightWritesOnlyNativePaletteFlagAndHeldRingCounter() {
        var player = new Knuckles("knuckles", (short) 0, (short) 0);
        var controller = bind(player);
        PhysicsProfile original = player.getPhysicsProfile();
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setAnimationId(0x15);
        int frame = controller.captureRewindState().paletteFrame();
        controller.initializeEndingKnucklesFlight();
        assertEquals(S3kFormTier.SUPER, controller.getActiveFormTier());
        assertEquals(-1, controller.captureRewindState().paletteState());
        assertEquals(0xF, controller.captureRewindState().paletteTimer());
        assertEquals(frame, controller.captureRewindState().paletteFrame());
        assertEquals(0x7FFF, controller.captureRewindState().ringDrainCounter());
        assertNull(controller.currentTrailSample(0));
        for (int tick = 0; tick < 120; tick++) controller.update();
        assertEquals(7, player.getRingCount());
        assertEquals(0x15, player.getAnimationId());
        assertSame(original, player.getPhysicsProfile());
        assertTrue(player.isObjectControlled());
        assertEquals(0x7FFF, controller.captureRewindState().ringDrainCounter());
    }

    @Test void knucklesFlightRevertPreservesActorAnimationMappingControlAndPhysics() {
        var player = new Knuckles("knuckles", (short) 0, (short) 0);
        var controller = bind(player);
        PhysicsProfile original = player.getPhysicsProfile();
        PlayerSpriteRenderer renderer = mock(PlayerSpriteRenderer.class);
        player.setSpriteRenderer(renderer);
        player.setAnimationId(0x15);
        player.setForcedAnimationId(0x15);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        controller.initializeEndingKnucklesFlight();
        for (int tick = 0; tick < 5; tick++) controller.update();
        int timer = controller.captureRewindState().paletteTimer();
        controller.beginEndingKnucklesFlightRevert();
        assertEquals(SuperState.NORMAL, controller.getState());
        assertFalse(player.isSuperSonic());
        assertEquals(2, controller.captureRewindState().paletteState());
        assertEquals(0x1E, controller.captureRewindState().paletteFrame());
        assertEquals(timer, controller.captureRewindState().paletteTimer());
        assertEquals(0x15, player.getAnimationId());
        assertEquals(0x15, player.getForcedAnimationId());
        assertSame(renderer, player.getSpriteRenderer());
        assertSame(original, player.getPhysicsProfile());
        assertTrue(player.isObjectControlled());
        var snapshot = player.captureRewindState();
        controller.update();
        player.restoreRewindState(snapshot);
        assertSame(renderer, player.getSpriteRenderer());
        assertEquals(0x15, player.getAnimationId());
        assertTrue(player.isObjectControlled());
    }

    @Test void reunionSonicForcesHyperWithoutEmeraldsOrTrailAndReleasesOnActualFade() throws Exception {
        var player = new Sonic("sonic", (short) 0, (short) 0);
        var controller = bind(player);
        player.setRingCount(80);
        player.setObjectMappingFrameControl(true);
        LevelRingDisplay.publish(player.currentLevelState());
        int lives = player.currentGameState().getLives();
        controller.startEndingReunionTransformation();
        assertEquals(130, player.getRingCount(), "Ending_Give_SuperSonic silently adds50");
        assertEquals(80, LevelRingDisplay.value(player.currentLevelState()), "no HUD-ring refresh write");
        assertEquals(lives, player.currentGameState().getLives(), "no ring-mutator life award at100");
        assertFalse(player.isObjectMappingFrameControl(), "object_control=$81 clears the former $83 pose gate");
        assertEquals(S3kFormTier.HYPER, controller.getActiveFormTier(), "FixBugs0 forcesHyper regardless of emeralds");
        assertNull(controller.currentTrailSample(0), "FixBugs0 omits Sonic's after-images");
        assertEquals(60, controller.captureRewindState().ringDrainCounter());
        assertEquals(PhysicsProfile.SONIC_3K_SUPER_SONIC, player.getPhysicsProfile());
        for (int tick = 0; tick < 25; tick++) controller.update();
        assertEquals(SuperState.TRANSFORMING, controller.getState());
        assertTrue(player.isObjectControlled());
        player.setObjectMappingFrameControl(true);
        controller.update();
        assertEquals(SuperState.SUPER, controller.getState());
        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectMappingFrameControl(), "palette release writes the entire control byte to zero");
        assertEquals(1, controller.captureRewindState().paletteTimer(), "native fade leaves its reload1");
        for (int tick = 0; tick < 100; tick++) controller.update();
        assertEquals(130, player.getRingCount(), "the ending owner does not execute ordinary drain");
    }

    @Test void endingRevertPaletteKeepsShippedSonicUnderflowAndNonSonicPop() throws Exception {
        var sonic = new Sonic("sonic", (short) 0, (short) 0);
        var controller = bind(sonic);
        controller.initializeEndingSuperSonic();
        controller.beginEndingRescueRevert();
        for (int tick = 0; tick < 25; tick++) controller.update();
        assertEquals(0, controller.captureRewindState().paletteState());
        assertEquals(0xFA, controller.captureRewindState().paletteFrame(),
                "FixBugs=0 clears the high byte after $FFFA underflow");

        var tails = new Tails("tails", (short) 0, (short) 0);
        var superTails = bind(tails);
        superTails.initializeEndingSuperTails();
        for (int tick = 0; tick < 5; tick++) superTails.update();
        int timer = superTails.captureRewindState().paletteTimer();
        superTails.beginEndingRescueRevert();
        superTails.update();
        assertEquals(0, superTails.captureRewindState().paletteState());
        assertEquals(0, superTails.captureRewindState().paletteFrame());
        assertEquals(timer, superTails.captureRewindState().paletteTimer(),
                "SuperHyper_PalCycle_RevertNotSonic does not write its timers");
    }

    @Test void reunionKnucklesKeepsSuperFlagButGetsNativeHyperTrailAndSixteenTickRelease() throws Exception {
        var player = new Knuckles("knuckles", (short) 0, (short) 0);
        var controller = bind(player);
        controller.startEndingReunionTransformation();
        assertEquals(S3kFormTier.SUPER, controller.getActiveFormTier(), "FixBugs0 omits the Hyper flag write");
        assertFalse(controller.isHyperFormActive());
        assertNotNull(controller.currentTrailSample(0), "the native trail pointer is still installed");
        for (int tick = 0; tick < 15; tick++) controller.update();
        assertTrue(player.isObjectControlled());
        controller.update();
        assertFalse(player.isObjectControlled());
        assertEquals(PhysicsProfile.SONIC_3K_SUPER_KNUCKLES, player.getPhysicsProfile());
    }

    @Test void reunionTailsRetainsItsNativePaletteTimerAndCreatesBirdsOnlyAtReunion() throws Exception {
        var player = new Tails("tails", (short) 0, (short) 0);
        var controller = bind(player);
        PowerUpSpawner spawns = mock(PowerUpSpawner.class);
        player.setPowerUpSpawner(spawns);
        controller.initializeEndingSuperTails();
        for (int tick = 0; tick < 5; tick++) controller.update();
        var previous = controller.captureRewindState();
        verifyNoInteractions(spawns);
        controller.startEndingReunionTransformation();
        assertEquals(previous.paletteTimer(), controller.captureRewindState().paletteTimer(),
                "Ending_Give_SuperSonic writes shared Palette_timer, not Palette_timer_Tails");
        assertEquals(previous.paletteFrame(), controller.captureRewindState().paletteFrame());
        verify(spawns).registerObject(any(com.openggf.game.sonic3k.objects.SuperTailsFlickyFlockObjectInstance.class));
        for (int tick = 0; tick < 15; tick++) controller.update();
        assertTrue(player.isObjectControlled());
        controller.update();
        assertFalse(player.isObjectControlled());
        assertEquals(0, controller.captureRewindState().paletteFrame());
        assertEquals(previous.paletteTimer(), controller.captureRewindState().paletteTimer());
        assertEquals(57, player.getRingCount());
    }

    @Test void endingModeAndFadeStateRoundTripWithoutReplayingRingAward() throws Exception {
        var player = new Sonic("sonic", (short) 0, (short) 0);
        var controller = bind(player);
        controller.startEndingReunionTransformation();
        for (int tick = 0; tick < 12; tick++) controller.update();
        PerObjectRewindSnapshot captured = player.captureRewindState();
        for (int tick = 0; tick < 20; tick++) controller.update();
        var expected = controller.captureRewindState();
        player.restoreRewindState(captured);
        for (int tick = 0; tick < 20; tick++) controller.update();
        assertEquals(expected, controller.captureRewindState());
        assertEquals(57, player.getRingCount());
        assertFalse(player.isObjectControlled());
    }

    @Test void rescueAndReunionNeverIssueWhistleTransformOrSuperMusicRequests() throws Exception {
        var player = new Sonic("sonic", (short) 0, (short) 0);
        var controller = bind(player);
        var requests = new ArrayList<String>();
        var observers = new AudioManager.DiagnosticObserverSet(
                (kind, id) -> requests.add(kind + ":" + id), AudioAdmissionObserver.NONE,
                SmpsDriverServiceObserver.NONE, ChipWriteObserver.NONE, SfxContentionObserver.NONE);
        try (var lease = GameServices.audio().acquireDiagnosticObservers(observers)) {
            controller.initializeEndingHyperSonic();
            for (int tick = 0; tick < 30; tick++) controller.update();
            controller.startEndingReunionTransformation();
            for (int tick = 0; tick < 30; tick++) controller.update();
            controller.beginEndingRescueRevert();
            controller.update();
            assertTrue(requests.isEmpty(), "ending helpers have no audio call: " + requests);
        }
    }

    private Sonic3kSuperStateController bind(AbstractPlayableSprite player) {
        GameServices.sprites().clearAllSprites();
        GameServices.sprites().addSprite(player, player.getCode());
        player.setRingCount(7);
        var controller = new Sonic3kSuperStateController(player);
        player.setSuperStateController(controller);
        return controller;
    }

}
