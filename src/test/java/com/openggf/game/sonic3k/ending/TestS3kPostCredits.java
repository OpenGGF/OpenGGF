package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameOverExit;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Native terminal entry is tested independently of the incoming final fight. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kPostCredits {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private HeadlessTestFixture boot(){
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"knuckles");
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        return HeadlessTestFixture.builder().withZoneAndAct(10,1).withFreshLevelStartLifecycle().build();
    }
    private S3kPostCreditsObjectInstance start(HeadlessTestFixture fixture,int chaos,int superEmeralds){
        var state=((SszZoneRuntimeState)GameServices.zoneRuntimeState()).ending();
        // Declared Obj_5EF68 entry RAM. The full incoming route owns its earlier collection/fight.
        state.initializeProgress(chaos,superEmeralds);
        S3kEndingCoordinator.beginPostCredits(TestEnvironment.objectServices());
        var owner=GameServices.level().getObjectManager().activeObjectsOfType(S3kPostCreditsObjectInstance.class).getFirst();
        owner.update(0,fixture.sprite());return owner;
    }
    @Test void chaosKnucklesHoldsNativeBannerThenFadesAndRequestsSegaTitle(){
        var fixture=boot();var owner=start(fixture,7,0);
        for(int i=0;i<511;i++)owner.update(i,fixture.sprite());
        assertEquals(1,owner.phase());assertFalse(owner.isDestroyed());
        owner.update(511,fixture.sprite());assertEquals(2,owner.phase());
        for(int i=0;i<42;i++)owner.update(i,fixture.sprite());
        assertEquals(2,owner.phase());
        owner.update(42,fixture.sprite());assertEquals(8,owner.phase());assertTrue(owner.isDestroyed());
        assertEquals(GameOverExit.TITLE_SCREEN,GameServices.level().consumeGameOverExitRequest());
    }
    @Test void bannerAnimationPaletteAndFadeRestoreReplayBeforeTheSuperEyecatch(){
        var fixture=boot();var owner=start(fixture,7,7);
        for(int i=0;i<470;i++)owner.update(i,fixture.sprite());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        for(int i=0;i<100;i++)owner.update(i,fixture.sprite());
        assertEquals(3,owner.phase(),"post-banner pause is the native32-tick loc_5FD5A helper");
        byte[] ram=((SszZoneRuntimeState)GameServices.zoneRuntimeState()).captureBytes();
        registry.restore(saved);
        owner=GameServices.level().getObjectManager().activeObjectsOfType(S3kPostCreditsObjectInstance.class).getFirst();
        for(int i=0;i<100;i++)owner.update(i,fixture.sprite());
        assertEquals(3,owner.phase());assertArrayEquals(ram,((SszZoneRuntimeState)GameServices.zoneRuntimeState()).captureBytes());
        for(int i=0;i<32;i++)owner.update(i,fixture.sprite());assertEquals(6,owner.phase());
        ((SszZoneRuntimeState)GameServices.zoneRuntimeState()).ending().setStartHeld(true);
        owner.update(1,fixture.sprite());assertTrue(owner.isDestroyed());
        assertEquals(GameOverExit.TITLE_SCREEN,GameServices.level().consumeGameOverExitRequest());
    }
    @Test void badKnucklesRunsAllocatedChargeChildThenNativeJumpLandingAndAutomaticTitleHold(){
        var fixture=boot();var owner=start(fixture,0,0);
        for(int i=0;i<555;i++)owner.update(i,fixture.sprite());
        assertEquals(7,owner.phase());
        assertEquals(1,GameServices.level().getObjectManager().activeObjectsOfType(EndingTerminalMasterObjectInstance.class).size());
        for(int i=0;i<240;i++)owner.update(i,fixture.sprite());
        var manager=GameServices.level().getObjectManager();
        assertEquals(1,manager.activeObjectsOfType(EndingMechaChargeObjectInstance.class).size());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        for(int i=0;i<160;i++)tickTerminal(owner,fixture,i);
        int[] expected={owner.getY(),owner.mappingFrame(),owner.phase()};
        registry.restore(saved);owner=manager.activeObjectsOfType(S3kPostCreditsObjectInstance.class).getFirst();
        for(int i=0;i<160;i++)tickTerminal(owner,fixture,i);
        assertArrayEquals(expected,new int[]{owner.getY(),owner.mappingFrame(),owner.phase()});
        for(int i=0;i<2000&&!owner.isDestroyed();i++)tickTerminal(owner,fixture,i);
        assertTrue(owner.isDestroyed());assertEquals(GameOverExit.TITLE_SCREEN,GameServices.level().consumeGameOverExitRequest());
    }
    @Test void badEndingMasterAllocatesTheNativeFadeOwnerAndReplaysItsTimer(){
        var fixture=boot();var services=TestEnvironment.objectServices();
        S3kEndingPalette.blackLine(services,0);
        var manager=GameServices.level().getObjectManager();
        var master=manager.createDynamicObject(()->new EndingTerminalMasterObjectInstance(4));
        master.update(0,fixture.sprite());
        var fade=manager.activeObjectsOfType(EndingPaletteFadeObjectInstance.class).getFirst();
        for(int i=0;i<5;i++)fade.update(i,fixture.sprite());
        assertTrue(services.paletteOwnershipRegistryOrNull().isPaletteRotationDisabled());
        assertEquals(0,paletteWord(0,1),"native wait word5 first increments on its sixth dispatch");
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        fade.update(5,fixture.sprite());assertEquals(0x222,paletteWord(0,1));
        for(int i=0;i<42;i++)fade.update(i,fixture.sprite());
        assertTrue(fade.isDestroyed());assertFalse(services.paletteOwnershipRegistryOrNull().isPaletteRotationDisabled());
        assertEquals(0xEEE,paletteWord(0,1));
        registry.restore(saved);fade=manager.activeObjectsOfType(EndingPaletteFadeObjectInstance.class).getFirst();
        for(int i=0;i<43;i++)fade.update(i,fixture.sprite());
        assertTrue(fade.isDestroyed());assertEquals(0xEEE,paletteWord(0,1));
        assertFalse(services.paletteOwnershipRegistryOrNull().isPaletteRotationDisabled());
    }
    @Test void mechaChargeReleaseRetainsTheNativeParentInitializationDispatchAcrossRestore(){
        var fixture=boot();var owner=start(fixture,0,0);
        for(int i=0;i<795;i++)owner.update(i,fixture.sprite());
        assertEquals(6,owner.mechaStage());
        var manager=GameServices.level().getObjectManager();
        var child=manager.activeObjectsOfType(EndingMechaChargeObjectInstance.class).getFirst();
        for(int i=0;i<160&&owner.mechaStage()==6;i++)child.update(i,fixture.sprite());
        assertEquals(8,owner.mechaStage());assertEquals(0xE,owner.mappingFrame());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        owner.update(0,fixture.sprite());assertEquals(1,owner.mechaStage());assertEquals(0xE,owner.mappingFrame());
        assertEquals(0,owner.mechaAnimationTimer(),"loc_5F802 only initializes the script cursor");
        owner.update(1,fixture.sprite());assertEquals(0xE,owner.mappingFrame());assertEquals(5,owner.mechaAnimationTimer());
        registry.restore(saved);owner=manager.activeObjectsOfType(S3kPostCreditsObjectInstance.class).getFirst();
        owner.update(0,fixture.sprite());assertEquals(0xE,owner.mappingFrame());assertEquals(0,owner.mechaAnimationTimer());
        owner.update(1,fixture.sprite());assertEquals(0xE,owner.mappingFrame());assertEquals(5,owner.mechaAnimationTimer());
    }
    @Test void terminalSubtypeEightySparksUseNativeZeroTableRowAndParentChargeGateAcrossRestore(){
        var fixture=boot();var owner=start(fixture,0,0);
        for(int i=0;i<555;i++)owner.update(i,fixture.sprite());assertEquals(7,owner.phase());
        var manager=GameServices.level().getObjectManager();
        var spark=manager.activeObjectsOfType(com.openggf.game.sonic3k.objects.bosses.SszMechaSonicSparkChild.class).getFirst();
        assertTrue(spark.getSlotIndex()>owner.getSlotIndex(),"CreateChild6_Simple uses after-current allocation");
        var services=TestEnvironment.objectServices();
        com.openggf.game.sonic3k.S3kPaletteWriteSupport.applyContiguousPatch(services.paletteOwnershipRegistryOrNull(),
                services.currentLevel(),services.graphicsManager(),"ending-spark-vector",
                com.openggf.game.sonic3k.S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,1,9,new byte[]{0xE,(byte)0x88});
        com.openggf.game.sonic3k.S3kPaletteWriteSupport.resolvePendingWritesNow(services.paletteOwnershipRegistryOrNull(),
                services.currentLevel(),services.graphicsManager());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        spark.update(0,fixture.sprite());assertTrue(spark.visibleForTest());assertEquals(5,spark.mappingFrameForTest());
        assertEquals(owner.getX(),spark.getX());assertEquals(owner.getY()+8,spark.getY());
        spark.update(1,fixture.sprite());assertEquals(4,spark.mappingFrameForTest());
        assertEquals(owner.getX()-4,spark.getX());assertEquals(owner.getY()+0xC,spark.getY());
        registry.restore(saved);
        spark=manager.activeObjectsOfType(com.openggf.game.sonic3k.objects.bosses.SszMechaSonicSparkChild.class).getFirst();
        owner=manager.activeObjectsOfType(S3kPostCreditsObjectInstance.class).getFirst();
        spark.update(0,fixture.sprite());assertEquals(5,spark.mappingFrameForTest());
        spark.update(1,fixture.sprite());assertEquals(4,spark.mappingFrameForTest());
        for(int i=0;i<240;i++)owner.update(i,fixture.sprite());assertTrue(owner.stopsSparks());
        spark.update(2,fixture.sprite());assertTrue(spark.isDestroyed(),"loc_5F7D8 sets native parent $38 bit6");
    }
    private int paletteWord(int line,int color){
        return com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                TestEnvironment.objectServices().currentLevel().getPalette(line).getColor(color));
    }
    private void tickTerminal(S3kPostCreditsObjectInstance owner,HeadlessTestFixture fixture,int frame){
        owner.update(frame,fixture.sprite());
        for(var child:GameServices.level().getObjectManager().activeObjectsOfType(EndingMechaChargeObjectInstance.class))
            if(!child.isDestroyed())child.update(frame,fixture.sprite());
        for(var child:GameServices.level().getObjectManager().activeObjectsOfType(EndingTerminalMasterObjectInstance.class))
            if(!child.isDestroyed())child.update(frame,fixture.sprite());
        for(var child:GameServices.level().getObjectManager().activeObjectsOfType(EndingPaletteFadeObjectInstance.class))
            if(!child.isDestroyed())child.update(frame,fixture.sprite());
    }
}
