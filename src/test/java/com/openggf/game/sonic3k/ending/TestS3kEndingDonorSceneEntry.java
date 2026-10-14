package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import com.openggf.game.rewind.RewindSnapshotDiff;

/** Declared native reunion entry; no donor-name branch may replace its palette/control owner. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingDonorSceneEntry {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    static Stream<Arguments> supportedDonorEntries() {
        String[][] entries={{"s1","sonic","-2"},{"s2","sonic","-2"},{"s2","tails","-2"},{"s1","sonic","0"},{"s1","sonic","2"},{"s2","sonic","0"},
                {"s2","sonic","2"},{"s2","tails","0"},{"s2","tails","2"}};
        return Stream.of(entries).flatMap(entry -> java.util.Arrays.stream(new int[]{320,352,400,528,800})
                .mapToObj(width -> Arguments.of(entry[0],entry[1],Integer.parseInt(entry[2]),width)));
    }
    @ParameterizedTest @MethodSource("supportedDonorEntries")
    void supportedDonorReunionReleasesTheNativeGateAndAdmitsPostCredits(String donor,String character,int progress,int width) throws Exception {
        var config=SonicConfigurationService.getInstance();
        var aspect=java.util.Arrays.stream(com.openggf.configuration.WidescreenAspect.values())
                .filter(value -> value.pixelWidth()==width).findFirst().orElseThrow();
        com.openggf.tests.SozAcceptanceConfigurations.selectDisplay(aspect);
        // RequiresRom constructs Camera before per-vector session overrides.
        GameServices.camera().refreshViewportDimensions(config);
        assertEquals(width,GameServices.camera().getWidth());
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(donor.equals("s1")?SonicConfiguration.SONIC_1_ROM:SonicConfiguration.SONIC_2_ROM,
                java.util.Objects.requireNonNull(System.getProperty(donor.equals("s1")?"sonic1.rom.path":"sonic2.rom.path")));
        var f=HeadlessTestFixture.builder().withCrossGameDonation(donor).withZoneAndAct(13,1)
                .withFreshLevelStartLifecycle().build();
        assertTrue(CrossGameFeatureProvider.isActive());
        assertEquals(width,GameServices.camera().getWidth(),"native entry uses the declared viewport");
        com.openggf.tests.SozAcceptanceConfigurations.assertDisplay(aspect);
        assertEquals(character,f.sprite().getCode(),"supported donor character must not fall back");
        int nativeBank=character.equals("tails")?0x6A0:0x680;
        var ordinaryController=f.sprite().getSuperStateController();var ordinaryRenderer=f.sprite().getSpriteRenderer();
        var ordinaryTail=f.sprite().getTailsTailsController();
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        var state=(EndingZoneRuntimeState)GameServices.zoneRuntimeState();state.ending().initializeProgress(progress<0?0:7,progress==2?7:0);
        // Declare Obj_Ending's entry after the native clear-RAM terrain handoff.
        var manager=GameServices.level().getObjectManager();
        for(var old:manager.activeObjectsOfType(EndingRescueDirectorObjectInstance.class))ObjectLifetimeOps.deleteNoRespawn(old);
        var owner=manager.createDynamicObject(()->new EndingRescueDirectorObjectInstance(EndingRescueDirectorObjectInstance.REUNION));
        state.ending().credits().initialize(TestEnvironment.objectServices().romReader(),state.playerCharacter(),progress,false);
        for(int i=0;i<60_000&&!state.ending().credits().complete();i++)
            state.ending().credits().step(TestEnvironment.objectServices().romReader());
        assertTrue(state.ending().credits().complete());
        owner.update(0,f.sprite());boolean transformed=false,replayed=false;
        boolean needsTransformation=progress>=0&&(!character.equals("tails")||progress==2);
        com.openggf.game.rewind.CompositeSnapshot activeCheckpoint=null;
        assertInstanceOf(com.openggf.game.sonic3k.Sonic3kSuperStateController.class,f.sprite().getSuperStateController());
        assertEquals(nativeBank,f.sprite().getSpriteRenderer().patternBankBase(),"native S3K P1 presentation bank");
        if(character.equals("tails")){
            assertNotNull(ordinaryTail);assertNotSame(ordinaryTail,f.sprite().getTailsTailsController());
            assertEquals(0x6B0,f.sprite().getTailsTailsController().getRenderer().patternBankBase(),"native S3K appendage bank");
        }
        for(int i=0;i<1000&&!state.ending().postCreditsStarted();i++){
            advancePlayer(f.sprite());
            owner.update(i,f.sprite());transformed|=owner.routine()==10;
            if(owner.routine()==(needsTransformation?10:6)&&!replayed){
                replayed=true;var saved=registry.capture();activeCheckpoint=saved;
                for(int n=0;n<45;n++){advancePlayer(f.sprite());owner.update(n,f.sprite());}
                var expected=registry.capture();registry.restore(saved);
                owner=manager.activeObjectsOfType(EndingRescueDirectorObjectInstance.class).stream()
                        .filter(o->o.sequence()==EndingRescueDirectorObjectInstance.REUNION).findFirst().orElseThrow();
                for(int n=0;n<45;n++){advancePlayer(f.sprite());owner.update(n,f.sprite());}
                var actual=registry.capture();assertEquals(expected.entries().keySet(),actual.entries().keySet());
                for(String key:expected.entries().keySet()){
                    var diff=RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
                    assertTrue(diff.isEmpty(),()->key+": "+diff);
                }
                registry.restore(saved);owner=manager.activeObjectsOfType(EndingRescueDirectorObjectInstance.class).stream()
                        .filter(o->o.sequence()==EndingRescueDirectorObjectInstance.REUNION).findFirst().orElseThrow();
            }
        }
        assertEquals(needsTransformation,transformed,"native Tails Chaos-only reunion omits transformation");
        assertTrue(state.ending().postCreditsStarted(),"donor reunion stalled at native routine"+owner.routine());
        assertTrue(replayed);assertEquals(needsTransformation?50:0,f.sprite().getRingCount(),"native silent reunion ring write");
        registry.restore(before);
        assertSame(ordinaryController,f.sprite().getSuperStateController(),"restore the pre-entry controller binding");
        assertSame(ordinaryRenderer,f.sprite().getSpriteRenderer(),"restore the pre-entry donor artwork");
        assertSame(ordinaryTail,f.sprite().getTailsTailsController(),"restore the pre-entry appendage owner");
        same(before,registry.capture(),"pre-entry whole registry");
        registry.restore(activeCheckpoint);
        assertInstanceOf(com.openggf.game.sonic3k.Sonic3kSuperStateController.class,f.sprite().getSuperStateController());
        assertEquals(nativeBank,f.sprite().getSpriteRenderer().patternBankBase());
        if(character.equals("tails"))assertEquals(0x6B0,f.sprite().getTailsTailsController().getRenderer().patternBankBase());
        same(activeCheckpoint,registry.capture(),"active whole registry after reverse boundary");
        GameServices.level().loadZoneAndAct(0,0);
        var fresh=GameServices.sprites().getMainPlayable();
        assertEquals(width,GameServices.camera().getWidth(),"fresh load retains the declared viewport");
        com.openggf.tests.SozAcceptanceConfigurations.assertDisplay(aspect);
        if(ordinaryController==null)assertNull(fresh.getSuperStateController());
        else assertEquals(ordinaryController.getClass(),fresh.getSuperStateController().getClass(),"fresh load uses ordinary delegation");
        assertEquals(ordinaryRenderer.headProfileId(),fresh.getSpriteRenderer().headProfileId(),"fresh load restores donor artwork");
        assertEquals(ordinaryRenderer.patternBankCapacity(),fresh.getSpriteRenderer().patternBankCapacity());
        if(ordinaryTail!=null){
            assertNotNull(fresh.getTailsTailsController());
            assertEquals(ordinaryTail.getRenderer().headProfileId(),fresh.getTailsTailsController().getRenderer().headProfileId());
            assertEquals(ordinaryTail.getRenderer().patternBankCapacity(),fresh.getTailsTailsController().getRenderer().patternBankCapacity());
        }
        assertEquals(fresh.getCentreX(),fresh.getRenderCentreX());assertEquals(fresh.getCentreY(),fresh.getRenderCentreY());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void ordinaryPoweredDonorRamRestoresAfterItsMatchingControllerIsRebound(boolean alreadyActive) throws Exception {
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.SONIC_2_ROM,
                java.util.Objects.requireNonNull(System.getProperty("sonic2.rom.path")));
        var f=HeadlessTestFixture.builder().withCrossGameDonation("s2").withZoneAndAct(13,1)
                .withFreshLevelStartLifecycle().build();
        var ordinary=assertInstanceOf(com.openggf.game.sonic2.Sonic2SuperStateController.class,
                f.sprite().getSuperStateController());
        // Declared pre-entry controller request, not a cold powered campaign.
        ordinary.debugActivate();
        if(alreadyActive) {
            // Independently declared incoming HUD-timer context. No native scene has dispatched yet.
            GameServices.level().getLevelGamestate().resumeTimer();
            for(int n=0;n<80&&ordinary.getState()!=com.openggf.sprites.playable.SuperState.SUPER;n++)ordinary.update();
            assertEquals(1,GameServices.level().getObjectManager()
                    .activeObjectsOfType(com.openggf.game.sonic2.objects.SuperSonicStarsObjectInstance.class).size());
        }
        var ordinaryRam=ordinary.captureRewindState();assertNotNull(ordinaryRam);
        assertEquals(alreadyActive?com.openggf.sprites.playable.SuperState.SUPER:
                com.openggf.sprites.playable.SuperState.TRANSFORMING,ordinaryRam.state());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        f.stepFrame(false,false,false,false,false);var after=registry.capture();
        assertInstanceOf(com.openggf.game.sonic3k.Sonic3kSuperStateController.class,f.sprite().getSuperStateController());
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);assertSame(ordinary,f.sprite().getSuperStateController());
            assertEquals(ordinaryRam,ordinary.captureRewindState());same(before,registry.capture(),"ordinary owner cycle"+cycle);
            ordinary.update();assertNotEquals(ordinaryRam,ordinary.captureRewindState(),"changed ordinary future RAM");
            registry.restore(after);same(after,registry.capture(),"native owner cycle"+cycle);
            registry.restore(before);assertSame(ordinary,f.sprite().getSuperStateController());
            assertEquals(ordinaryRam,ordinary.captureRewindState());same(before,registry.capture(),"ordinary reverse cycle"+cycle);
            f.stepFrame(false,false,false,false,false);same(after,registry.capture(),"native entry forward cycle"+cycle);
        }
    }

    @org.junit.jupiter.api.Test
    void progressedNativeSceneDoesNotSupplyRamToReplayedFreshEntry() throws Exception {
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.SONIC_2_ROM,
                java.util.Objects.requireNonNull(System.getProperty("sonic2.rom.path")));
        var f=HeadlessTestFixture.builder().withCrossGameDonation("s2").withZoneAndAct(13,1)
                .withFreshLevelStartLifecycle().build();
        for(int n=0;n<7;n++)GameServices.gameState().markEmeraldCollected(n);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        f.stepFrame(false,false,false,false,false);var after=registry.capture();
        var nativeController=assertInstanceOf(com.openggf.game.sonic3k.Sonic3kSuperStateController.class,
                f.sprite().getSuperStateController());var firstRam=nativeController.captureRewindState();
        for(int cycle=0;cycle<2;cycle++) {
            for(int n=0;n<17;n++)f.stepFrame(false,false,false,false,false);
            assertNotEquals(firstRam,nativeController.captureRewindState(),"real scene advanced its own palette RAM");
            registry.restore(before);same(before,registry.capture(),"before progressed scene"+cycle);
            f.stepFrame(false,false,false,false,false);same(after,registry.capture(),"fresh native replay"+cycle);
        }
    }

    private static void advancePlayer(com.openggf.sprites.playable.AbstractPlayableSprite player){
        if(player.getSuperStateController()!=null)player.getSuperStateController().update();
        if(player.getTailsTailsController()!=null)player.getTailsTailsController().update();
    }
    private void same(com.openggf.game.rewind.CompositeSnapshot a,com.openggf.game.rewind.CompositeSnapshot b,String label){
        assertEquals(a.entries().keySet(),b.entries().keySet(),label);
        for(String key:a.entries().keySet()) {
            var diff=RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key));
            assertTrue(diff.isEmpty(),()->label+" "+key+": "+diff);
        }
    }
}
