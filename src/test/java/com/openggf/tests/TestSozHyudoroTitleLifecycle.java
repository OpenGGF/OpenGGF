package com.openggf.tests;
import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.*;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.game.sonic3k.runtime.*;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;
/** Native title-owner retirement obligations, independent of the long SOZ routes. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozHyudoroTitleLifecycle {
    private final EnumMap<SonicConfiguration,Object> saved=new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;
    @BeforeEach void setup() {
        config=SonicConfigurationService.getInstance();
        for(var k:SonicConfiguration.values())if(config.hasSessionOverride(k))saved.put(k,config.getConfigValue(k));
        config.clearSessionOverrides();config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() {
        CrossGameFeatureProvider.getInstance().resetState();config.clearSessionOverrides();saved.forEach(config::setSessionOverride);
        config.resolveDisplayAspect();SessionManager.clear();TestEnvironment.activeGameplayMode();
    }
    @Test void omittedTitleRetirementCreatesExactlyOneColdControllerAndRecreatesState() {
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1).withFreshLevelStartLifecycle().build();
        assertEquals(0,count(),"controller waits for the modeled omitted title to retire");
        assertEquals(0,S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow().lighting().darknessLevel());
        awaitBirth(f,false);
        for(int i=0;i<50;i++)f.stepFrame(false,false,false,false,false);
        assertEquals(1,count());assertEquals(0,controller().ghostCount());
    }
    @Test void visibleTitleRetirementCreatesControllerAfterChildrenRetire() throws Exception {
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,0).withFreshLevelStartLifecycle().build();
        GameServices.level().loadZoneAndActWithTitleCard(8,1);
        var title=(Sonic3kTitleCardManager)GameServices.module().getTitleCardProvider();
        assertEquals(0,count());awaitBirth(f,false);assertTrue(title.isComplete());
        for(int i=0;i<10;i++)title.update();assertEquals(1,count());
    }
    @Test void checkpointDeathReloadRecreatesControllerWithoutSeamlessDarkness() {
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1).withFreshLevelStartLifecycle().build();awaitBirth(f,false);
        var old=controller();((CheckpointState)GameServices.level().getCheckpointState()).saveCheckpoint(1,0x140,0x3AC,false);
        GameServices.level().respawnPlayer();assertEquals(0,count());
        assertEquals(0,S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow().lighting().darknessLevel());
        awaitBirth(f,false);
        assertNotSame(old,controller());assertEquals(1,GameServices.level().getCheckpointState().getLastCheckpointIndex());
    }
    @Test void seamlessReloadWaitsForDelayedInLevelTitleAndRewindsRetirement() throws Exception {
        var f=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,0).withFreshLevelStartLifecycle().build();
        var request=SeamlessLevelTransitionRequest.builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(8,1).runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.TITLE_OWNER)
                .preserveLevelGamestate(true).build();
        GameServices.level().executeActTransition(request);
        assertEquals(0,count());
        // Isolate the delayed title contract; the Act1 screen-event owner seeds
        // this native transition state separately from the synchronous reload.
        S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow().lighting().initializeSeamlessDarkness();
        assertEquals(5,S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow().lighting().darknessLevel());
        for(int i=0;i<20;i++)f.stepFrame(false,false,false,false,false);assertEquals(0,count(),"the reload does not substitute for the delayed title owner");
        GameServices.level().requestInLevelTitleCard(8,1,true);
        awaitBirth(f,false);assertEquals(1,count());
    }
    private static long count(){return GameServices.level().getObjectManager().getActiveObjects().stream().filter(SozHyudoroControllerObjectInstance.class::isInstance).count();}
    private static SozHyudoroControllerObjectInstance controller(){return GameServices.level().getObjectManager().getActiveObjects().stream().filter(SozHyudoroControllerObjectInstance.class::isInstance).map(SozHyudoroControllerObjectInstance.class::cast).findFirst().orElseThrow();}
    private static void awaitBirth(HeadlessTestFixture f,boolean explicitVisibleTitleUpdate){
        var title=(Sonic3kTitleCardManager)GameServices.module().getTitleCardProvider();
        for(int i=0;i<400;i++){
            var registry=f.gameplayMode().getRewindRegistry();var before=registry.capture();
            if(explicitVisibleTitleUpdate)title.update();f.stepFrame(false,false,false,false,false);
            if(count()>0){
                assertEquals(1,count());var after=registry.capture();registry.restore(before);same(before,registry.capture(),"title retirement restore");
                if(explicitVisibleTitleUpdate)title.update();f.stepFrame(false,false,false,false,false);same(after,registry.capture(),"title retirement forward replay");return;
            }
        }
        fail("native title owner did not create Hyudoro; title="+title.getStateName());
    }
    private static void replay(HeadlessTestFixture fixture,CompositeSnapshot before,boolean jump,String label) {
        var registry=fixture.gameplayMode().getRewindRegistry();var after=registry.capture();
        registry.restore(before);same(before,registry.capture(),label+" restore");
        fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,""));
        fixture.stepFrame(false,false,false,false,jump);same(after,registry.capture(),label+" forward replay");
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual,String label) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet(),label);
        for(String key:expected.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),
                ()->label+" "+key+": "+RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)));
    }
}
