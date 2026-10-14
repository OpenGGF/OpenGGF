package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Literal loc5DD0E/5E984 -> PalLoad_Line1($85940), no movie/reference state. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingSmallPlanePalette {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> cases(){
        var rows=new java.util.ArrayList<org.junit.jupiter.params.provider.Arguments>();
        for(int width:new int[]{320,800})for(String main:new String[]{"sonic","tails","knuckles"})
            for(String emeralds:new String[]{"0000000","1111111","3333333"})
                rows.add(org.junit.jupiter.params.provider.Arguments.of(width,main,emeralds));
        return rows.stream();
    }
    @ParameterizedTest @MethodSource("cases")
    void smallPlaneLoadsNativeLineTwoAndPreservesLineOneAcrossBothWorldDirections(int width,String main,String emeralds)throws Exception{
        var config=SonicConfigurationService.getInstance();config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,main);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);GameServices.camera().refreshViewportDimensions(config);
        boolean knuckles=main.equals("knuckles");
        var f=HeadlessTestFixture.builder().withZoneAndAct(knuckles?10:13,1).withFreshLevelStartLifecycle().build();
        var digits=emeralds.chars().map(c->c-'0').boxed().toList();
        GameServices.gameState().restoreS3kEmeraldProgress(digits,digits.stream().anyMatch(c->c>=2));
        var manager=GameServices.level().getObjectManager();
        com.openggf.level.objects.AbstractObjectInstance owner;
        if(knuckles){
            var flight=manager.createDynamicObject(EndingKnucklesFlightObjectInstance::new);flight.update(0,f.sprite());
            // Independent native loc5E95A component: after movement X must fall
            // below Camera_X+$60-$A0, before PalLoad_Line1. No frame fit/hydration.
            set(flight,"routine",10);set(flight,"xFixed",((GameServices.camera().getX()+0x60-0xA0)&0xFFFF)<<16);
            set(flight,"xVelocity",-0x200);
            com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(f.sprite(),flight.getX());
            com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(f.sprite(),flight.getY());owner=flight;
        }else{
            var rescue=manager.activeObjectsOfType(EndingRescueDirectorObjectInstance.class).getFirst();rescue.update(0,f.sprite());
            // loc5DCA6: MoveSprite2 then X>$220 admits loc5DD0E in this dispatch.
            set(rescue,"routine",24);set(rescue,"xFixed",0x220<<16);set(rescue,"xVelocity",0x100);
            rescue.afterRewindRestoreSettled();owner=rescue;
        }
        resolve();int[] firstLine=words(0);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();int slot=owner.getSlotIndex();
        owner.update(0,f.sprite());resolve();
        assertEquals(knuckles?12:26,knuckles?((EndingKnucklesFlightObjectInstance)owner).routine():((EndingRescueDirectorObjectInstance)owner).routine());
        var reader=TestEnvironment.objectServices().romReader();int[] expected=new int[16];
        for(int color=0;color<16;color++)expected[color]=reader.readU16BE(S3kEndingResources.PAL_SMALL_PLANE+color*2);
        assertArrayEquals(expected,words(1),"PalLoad_Line1 actually writes Normal_palette_line_2, engine line1");
        assertArrayEquals(firstLine,words(0),"the source does not overwrite the player's first palette line");
        var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(before);same(before,registry.capture());assertArrayEquals(firstLine,words(0));
            owner=(com.openggf.level.objects.AbstractObjectInstance)com.openggf.level.objects.NativeSstSlotAccess.currentOwner(manager,slot);
            owner.update(0,f.sprite());resolve();same(after,registry.capture());assertArrayEquals(expected,words(1));
            registry.restore(before);same(before,registry.capture());registry.restore(after);same(after,registry.capture());
            assertArrayEquals(expected,words(1));assertArrayEquals(firstLine,words(0));
        }
    }
    private static void set(Object owner,String name,int value)throws Exception{var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.setInt(owner,value);}
    private static void resolve(){var services=TestEnvironment.objectServices();S3kPaletteWriteSupport.resolvePendingWritesNow(
            services.paletteOwnershipRegistryOrNull(),services.currentLevel(),services.graphicsManager());}
    private static int[] words(int line){int[] words=new int[16];for(int i=0;i<16;i++)words[i]=PaletteWriteSupport.segaWordFromColor(
            GameServices.level().getCurrentLevel().getPalette(line).getColor(i));return words;}
    private static void same(com.openggf.game.rewind.CompositeSnapshot a,com.openggf.game.rewind.CompositeSnapshot b){
        assertEquals(a.entries().keySet(),b.entries().keySet());for(var key:a.entries().keySet()){
            var diff=RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key));assertTrue(diff.isEmpty(),()->key+" "+diff);}}
}
