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
    /**
     * off_5EFB6 (sonic3k.asm:125511) gives locked-on Knuckles loc_5F0A6 at progress 0 and +2:
     * its loc_5F20A keeps the cleared subtype 0, so sub_5FF1C (sonic3k.asm:126931) reads
     * word_5F260[0] = ($120,$B0). Only progress -2 takes loc_5F0C4 (subtype 8, ($120,$F0)).
     * The native Knuckles complete run (_unkFA88 2) holds the logo SST at ($120,$B0) from
     * input 432728.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"7,7,176","7,0,176","0,0,240"})
    void knucklesBannerUsesTheWord5F260RowOfTheSelectedInitializer(int chaos,int superEmeralds,int y){
        var fixture=boot();var owner=start(fixture,chaos,superEmeralds);
        assertEquals(0x120,owner.getX());assertEquals(y,owner.getY());
    }
    /** Every locked-on off_5EFB6 row: loc_5F05C (Sonic, team, Tails) writes subtype 4. */
    @Test void lockedOnInitializersSelectTheirWord5F260RowAndPose() throws Exception {
        boot();var rom=TestEnvironment.objectServices().romReader();
        for(var character:com.openggf.game.PlayerCharacter.values())for(int progress=-2;progress<=2;progress+=2){
            var dispatch=S3kPostCreditsData.resolve(rom,character,progress);
            boolean knux=character==com.openggf.game.PlayerCharacter.KNUCKLES;
            int[] expected=!knux?new int[]{0xD0,0xB8}:progress<0?new int[]{0x120,0xF0}:new int[]{0x120,0xB0};
            assertArrayEquals(expected,new int[]{S3kPostCreditsData.bannerX(rom,dispatch),S3kPostCreditsData.bannerY(rom,dispatch)},
                    character+"/"+progress);
            assertEquals(knux&&progress>=0,dispatch.knucklesPose(),character+"/"+progress);
        }
    }
    /**
     * loc_5F480 (sonic3k.asm:125947) allocates loc_5FBA0 (sonic3k.asm:126562) only for a
     * positive _unkFA88. Native credits-done RAM (inputs 432728..433088, every 20 inputs)
     * dates Obj_5EF68's init to input 432709 ($2E = $1EC at 432728) and holds the flash's
     * $3A below. Colours 2/3 come from the longword, colour 5 from move.w 4(a1,d0.w),2(a2);
     * colour 4 keeps Pal_KnuxEndPose's $0080 (native CRAM agrees).
     */
    @Test void superKnucklesBannerCyclesPoseColoursLikeTheNativeRunAndReplays() throws Exception {
        var fixture=boot();var owner=start(fixture,7,7); // pass 0 = native input 432709
        var rom=TestEnvironment.objectServices().romReader();
        int[] native3A={0x2A,0,0x24,0,0x24,0,0x1E,0,0x18,0,0x18,0,0x12,0,0x0C,0x36,0x0C,0x30,0x06};
        int pass=0;
        for(int seq=1;seq<=native3A.length;seq++) {
            while(pass<20*seq-1)owner.update(++pass,fixture.sprite());
            int entry=0x5FBE2+native3A[seq-1]; // Pal_KnuxEndPoseSuper
            assertArrayEquals(new int[]{rom.readU16BE(entry),rom.readU16BE(entry+2),0x0080,rom.readU16BE(entry+4)},
                    new int[]{paletteWord(0,2),paletteWord(0,3),paletteWord(0,4),paletteWord(0,5)},"native seq"+seq);
        }
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        int[] expected=new int[60];
        for(int i=0;i<60;i++){owner.update(++pass,fixture.sprite());expected[i]=paletteWord(0,2)<<16|paletteWord(0,5);}
        registry.restore(saved);pass-=60;
        owner=GameServices.level().getObjectManager().activeObjectsOfType(S3kPostCreditsObjectInstance.class).getFirst();
        for(int i=0;i<60;i++){owner.update(++pass,fixture.sprite());
            assertEquals(expected[i],paletteWord(0,2)<<16|paletteWord(0,5),"replay pass "+pass);}
        // loc_5F15A sets Palette_cycle_counters+0 before slot 8 runs: the flash deletes unwritten.
        // Any later flash write would survive the object fade.
        while(owner.phase()==1)owner.update(++pass,fixture.sprite());
        while(owner.phase()==2)owner.update(++pass,fixture.sprite());
        assertArrayEquals(new int[]{0,0,0},new int[]{paletteWord(0,2),paletteWord(0,3),paletteWord(0,5)});
    }
    /**
     * sub_5FF1C (sonic3k.asm:126931) allocates loc_5FF62 after the logo, so its first step
     * falls in the init pass. Every fourth pass sub_85EB4 raises all 16 line-1 colours one
     * level toward $EEE (eight steps); loc_5FF94 then falls into loc_5FFA6's countdown and
     * sub_85F2A lowers them toward Target_palette_line_2 (eight steps). Native RAM: input
     * 432728 (pass 19) holds $0AAA throughout, 432748 (pass 39) the values below, 432768 the target.
     */
    @Test void bannerLogoFadesThroughWhiteLikeTheNativeRun() throws Exception {
        var fixture=boot();var owner=start(fixture,7,7);
        var rom=TestEnvironment.objectServices().romReader();
        int pass=0;
        while(pass<19)owner.update(++pass,fixture.sprite());
        for(int c=0;c<16;c++)assertEquals(0x0AAA,paletteWord(1,c),"pass 19 colour "+c);
        while(pass<39)owner.update(++pass,fixture.sprite());
        int[] native39={0x0888,0x0EEE,0x08EE,0x08CE,0x0888,0x088E,0x0E88,0x0888,
                0x0888,0x0888,0x088E,0x088E,0x088A,0x088C,0x0888,0x0888};
        for(int c=0;c<16;c++)assertEquals(native39[c],paletteWord(1,c),"pass 39 colour "+c);
        while(pass<59)owner.update(++pass,fixture.sprite());
        for(int c=0;c<16;c++)
            assertEquals(rom.readU16BE(S3kEndingResources.PAL_S3K_LOGO+c*2),paletteWord(1,c),"pass 59 colour "+c);
    }
    @Test void chaosKnucklesBannerKeepsThePosePalette(){
        var fixture=boot();var owner=start(fixture,7,0);
        for(int i=1;i<100;i++)owner.update(i,fixture.sprite());
        assertArrayEquals(new int[]{0x0206,0x020C,0x0080,0x064E},
                new int[]{paletteWord(0,2),paletteWord(0,3),paletteWord(0,4),paletteWord(0,5)},"no loc_5FBA0 at _unkFA88 0");
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
