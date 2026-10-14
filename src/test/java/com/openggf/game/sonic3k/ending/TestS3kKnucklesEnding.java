package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.openggf.camera.NativeViewportFraming;
import static org.junit.jupiter.api.Assertions.*;

/** Short loc_5E6C0 edges remain independent of the 8700-frame final-fight route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kKnucklesEnding {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private HeadlessTestFixture boot(){
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"knuckles");
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        return HeadlessTestFixture.builder().withZoneAndAct(10,1).withFreshLevelStartLifecycle().build();
    }
    private EndingKnucklesFlightObjectInstance start(HeadlessTestFixture fixture){
        var owner=GameServices.level().getObjectManager().createDynamicObject(EndingKnucklesFlightObjectInstance::new);
        owner.update(0,fixture.sprite());return owner;
    }
    @Test void nativeJumpWaitsThreeHundredPassesAndUsesLightGravityThenThreeSecondHover(){
        var fixture=boot();var owner=start(fixture);
        assertEquals(2,owner.routine());assertTrue(fixture.sprite().isObjectControlled());
        int initialY=owner.getY();
        for(int i=0;i<299;i++)owner.update(i,fixture.sprite());
        assertEquals(2,owner.routine());assertEquals(initialY,owner.getY());
        owner.update(299,fixture.sprite());assertEquals(4,owner.routine());
        assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(EndingCloudObjectInstance.class).isEmpty(),
                "loc_5E80A does not allocate the later loc_5EA1A cloud emitter");
        for(int i=0;i<53;i++)owner.update(i,fixture.sprite());
        assertEquals(4,owner.routine());
        owner.update(53,fixture.sprite());assertEquals(6,owner.routine());
        assertEquals(initialY-186,owner.getY(),"54 native $20-gravity steps at initial -$6C0");
        for(int i=0;i<179;i++)owner.update(i,fixture.sprite());assertEquals(6,owner.routine());
        owner.update(179,fixture.sprite());assertEquals(8,owner.routine());
    }
    @Test void jumpAndHoverRestoreReplayPositionAnimationArtHandlesAndEndingRam(){
        var fixture=boot();var owner=start(fixture);
        for(int i=0;i<310;i++)owner.update(i,fixture.sprite());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        for(int i=0;i<180;i++)owner.update(i,fixture.sprite());
        int[] expected={owner.getX(),owner.getY(),owner.routine(),fixture.sprite().getCentreX(),fixture.sprite().getCentreY()};
        byte[] ram=((SszZoneRuntimeState)GameServices.zoneRuntimeState()).captureBytes();
        registry.restore(saved);
        owner=GameServices.level().getObjectManager().activeObjectsOfType(EndingKnucklesFlightObjectInstance.class).getFirst();
        for(int i=0;i<180;i++)owner.update(i,fixture.sprite());
        assertArrayEquals(expected,new int[]{owner.getX(),owner.getY(),owner.routine(),fixture.sprite().getCentreX(),fixture.sprite().getCentreY()});
        assertArrayEquals(ram,((SszZoneRuntimeState)GameServices.zoneRuntimeState()).captureBytes());
    }
    @ParameterizedTest @ValueSource(ints={320,352,400,528,800})
    void finalFlightReadsThePriorNativeRenderFlagAndReplaysItsOffscreenEdge(int width){
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        GameServices.camera().refreshViewportDimensions(config);
        assertEquals(width,GameServices.camera().getWidth());
        var fixture=boot();var state=(SszZoneRuntimeState)GameServices.zoneRuntimeState();
        state.setCenterNativeArenaCamera(true);
        GameServices.camera().setX((short)NativeViewportFraming.visibleLeft(0,width));
        GameServices.camera().setXCopy(GameServices.camera().getX());
        var owner=start(fixture);state.setEndingCloudAligned(true);
        for(int i=0;i<1000&&owner.routine()!=16;i++)owner.update(i,fixture.sprite());
        assertEquals(16,owner.routine());
        var camera=GameServices.camera();camera.setX((short)NativeViewportFraming.visibleLeft(owner.getX()-351,width));
        camera.setXCopy(camera.getX());camera.setYCopy((short)owner.getY());
        owner.refreshPostCameraRenderState();
        owner.update(0,fixture.sprite());
        assertEquals(16,owner.routine(),"moving across the right edge does not replace the prior visible flag");
        owner.refreshPostCameraRenderState();
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        owner.update(1,fixture.sprite());assertEquals(18,owner.routine());
        int expectedX=owner.getX();
        registry.restore(saved);
        owner=GameServices.level().getObjectManager().activeObjectsOfType(EndingKnucklesFlightObjectInstance.class).getFirst();
        owner.update(1,fixture.sprite());assertEquals(18,owner.routine());assertEquals(expectedX,owner.getX());
    }
    @ParameterizedTest @ValueSource(ints={320,352,400,528,800})
    void centeredViewsPreserveNativeInitAndTurnaroundWordsWithRestore(int width){
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        GameServices.camera().refreshViewportDimensions(config);
        assertEquals(width,GameServices.camera().getWidth());
        var fixture=boot();var camera=GameServices.camera();assertEquals(width,camera.getWidth());
        ((SszZoneRuntimeState)GameServices.zoneRuntimeState()).setCenterNativeArenaCamera(true);
        camera.setX((short)NativeViewportFraming.visibleLeft(0,width));camera.setXCopy(camera.getX());
        var owner=start(fixture);assertEquals(0xC0,owner.getX());
        for(int i=0;i<534;i++)owner.update(i,fixture.sprite());assertEquals(8,owner.routine());
        for(int i=0;i<78;i++)owner.update(i,fixture.sprite());assertEquals(0x1F8,owner.getX());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        owner.update(0,fixture.sprite());assertEquals(8,owner.routine());assertEquals(0x1FC,owner.getX());
        owner.update(1,fixture.sprite());assertEquals(10,owner.routine());assertEquals(0x200,owner.getX());
        registry.restore(saved);
        owner=GameServices.level().getObjectManager().activeObjectsOfType(EndingKnucklesFlightObjectInstance.class).getFirst();
        owner.update(0,fixture.sprite());assertEquals(8,owner.routine());assertEquals(0x1FC,owner.getX());
        owner.update(1,fixture.sprite());assertEquals(10,owner.routine());assertEquals(0x200,owner.getX());
    }
    @Test void completeSuperProgressRotatesNativeMasterPairOnInitAndReplaysTheByteTimer(){
        var fixture=boot();var game=GameServices.gameState();game.configureSpecialStageProgress(7,7);
        for(int i=0;i<7;i++){game.markEmeraldCollected(i);game.markSuperEmeraldCollected(i);}
        var services=TestEnvironment.objectServices();services.paletteOwnershipRegistryOrNull().beginFrame();
        var owner=start(fixture);resolvePalette();
        assertEquals(0x680,paletteWord(14));assertEquals(0x8A0,paletteWord(15));
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        for(int i=0;i<9;i++)updatePalette(owner,i,fixture);
        assertEquals(0x8A0,paletteWord(15),"row1's delay byte9 survives nine decrements");
        updatePalette(owner,9,fixture);assertEquals(0x8C0,paletteWord(15));
        registry.restore(saved);
        owner=GameServices.level().getObjectManager().activeObjectsOfType(EndingKnucklesFlightObjectInstance.class).getFirst();
        for(int i=0;i<10;i++)updatePalette(owner,i,fixture);
        assertEquals(0x8C0,paletteWord(15));
    }
    private void updatePalette(EndingKnucklesFlightObjectInstance owner,int clock,HeadlessTestFixture fixture){
        TestEnvironment.objectServices().paletteOwnershipRegistryOrNull().beginFrame();
        owner.update(clock,fixture.sprite());resolvePalette();
    }
    private void resolvePalette(){
        var services=TestEnvironment.objectServices();var level=services.currentLevel();
        var palettes=new com.openggf.level.Palette[]{level.getPalette(0),level.getPalette(1),level.getPalette(2),level.getPalette(3)};
        services.paletteOwnershipRegistryOrNull().resolveInto(palettes,null,null,palettes[0]);
    }
    private int paletteWord(int color){
        return com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                TestEnvironment.objectServices().currentLevel().getPalette(2).getColor(color));
    }
}
