package com.openggf.game.sonic2;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.objects.SuperSonicStarsObjectInstance;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.SuperState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Ordinary S2 controller RAM on an S3K host, independent of native ending rebinding. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic2DonorSuperStateRewind {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    private record Run(HeadlessTestFixture fixture, Sonic2SuperStateController controller,
                       SpriteAnimationSet normalAnimations) { }

    private Run boot() throws Exception {
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS,true);
        config.setSessionOverride(SonicConfiguration.SONIC_2_ROM,
                java.util.Objects.requireNonNull(System.getProperty("sonic2.rom.path")));
        var fixture=HeadlessTestFixture.builder().withCrossGameDonation("s2")
                .withZoneAndAct(2,0).withFreshLevelStartLifecycle().build();
        var player=fixture.sprite();
        var controller=assertInstanceOf(Sonic2SuperStateController.class,player.getSuperStateController());
        var normal=player.getAnimationSet();assertNotNull(normal);
        GameServices.gameState().configureSpecialStageProgress(7,7);
        for(int i=0;i<7;i++)GameServices.gameState().markEmeraldCollected(i);
        player.setRingCount(100);player.setAir(true);player.setJumping(true);player.setYSpeed((short)0x28);
        controller.checkTransformationBeforeMove();
        assertEquals(SuperState.TRANSFORMING,controller.getState(),"ordinary pre-move jump gate");
        return new Run(fixture,controller,normal);
    }

    private static void step(Run run,int count) {
        for(int i=0;i<count;i++)run.fixture().stepFrame(false,false,false,false,false);
    }

    private static void same(CompositeSnapshot expected,CompositeSnapshot actual,String label) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet(),label);
        for(String key:expected.entries().keySet()) {
            var diff=RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(diff.isEmpty(),()->label+" "+key+": "+diff);
        }
    }

    private static void scripts(Run run) {
        if(run.controller().getState()==SuperState.SUPER)
            assertNotSame(run.normalAnimations(),run.fixture().sprite().getAnimationSet(),"active scripts");
        else assertSame(run.normalAnimations(),run.fixture().sprite().getAnimationSet(),"ordinary scripts");
    }

    private static void replayTwice(Run run,CompositeSnapshot before,int count) {
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        step(run,count);var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);scripts(run);same(before,registry.capture(),"before cycle"+cycle);
            step(run,count);same(after,registry.capture(),"forward cycle"+cycle);
            registry.restore(before);scripts(run);same(before,registry.capture(),"reverse cycle"+cycle);
            registry.restore(after);scripts(run);same(after,registry.capture(),"after cycle"+cycle);
        }
    }

    @Test void normalTailsRestoreKeepsTheCharacterPhysicsProfile() throws Exception {
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"tails");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.SONIC_2_ROM,
                java.util.Objects.requireNonNull(System.getProperty("sonic2.rom.path")));
        var fixture=HeadlessTestFixture.builder().withCrossGameDonation("s2")
                .withZoneAndAct(2,0).withFreshLevelStartLifecycle().build();
        var player=fixture.sprite();
        var owner=assertInstanceOf(Sonic2SuperStateController.class,player.getSuperStateController());
        assertEquals(SuperState.NORMAL,owner.getState());
        var normalProfile=player.getPhysicsProfile();
        assertEquals(15,normalProfile.standYRadius(),"literal normal Tails body radius");
        var before=owner.captureRewindState();
        owner.restoreRewindState(before);
        assertEquals(normalProfile,player.getPhysicsProfile(),"ordinary restore retains the matching character profile");
    }

    @Test void midTransformationRestoresPaletteTimerDrainAndOrdinaryMapping() throws Exception {
        var run=boot();step(run,5);
        var state=run.controller().captureRewindState();assertNotNull(state,"S2 live controller RAM must be captured");
        assertEquals(SuperState.TRANSFORMING,state.state());assertEquals(1,state.paletteState());
        assertEquals(10,state.paletteTimer());
        assertSame(run.normalAnimations(),run.fixture().sprite().getAnimationSet());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        replayTwice(run,registry.capture(),45);
        assertEquals(SuperState.SUPER,run.controller().getState());
        assertEquals(PhysicsProfile.SONIC_2_SUPER_SONIC,run.fixture().sprite().getPhysicsProfile());
    }

    @Test void activeToRevertedWorldRecreationDeletesTheCurrentStarsOwnerAndRestoresMappings() throws Exception {
        var run=boot();step(run,40);
        assertEquals(SuperState.SUPER,run.controller().getState());
        assertNotNull(run.controller().captureRewindState());
        assertNotSame(run.normalAnimations(),run.fixture().sprite().getAnimationSet());
        run.fixture().sprite().setRingCount(1);
        var manager=GameServices.level().getObjectManager();
        assertEquals(1,manager.activeObjectsOfType(SuperSonicStarsObjectInstance.class).size());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        replayTwice(run,registry.capture(),90);
        assertEquals(SuperState.NORMAL,run.controller().getState());
        assertEquals(0,manager.activeObjectsOfType(SuperSonicStarsObjectInstance.class).size());
        assertSame(run.normalAnimations(),run.fixture().sprite().getAnimationSet());
        assertEquals(PhysicsProfile.SONIC_2_SONIC,run.fixture().sprite().getPhysicsProfile());
    }

    @Test void normalStateStillCapturesTheInFlightRevertPaletteAndShippedResidualByte() throws Exception {
        var run=boot();step(run,40);run.controller().debugDeactivate();step(run,3);
        var state=run.controller().captureRewindState();assertNotNull(state);
        assertEquals(SuperState.NORMAL,state.state());assertEquals(2,state.paletteState());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        replayTwice(run,registry.capture(),32);
        assertEquals(0,run.controller().getPaletteState());
        assertEquals(0xF8,run.controller().getPaletteFrame(),"fixBugs=0 clears only the high byte after underflow");
        assertSame(run.normalAnimations(),run.fixture().sprite().getAnimationSet());
    }
}
