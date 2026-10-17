package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * D01 Sonic/+2 post-credits terminal: off_5EFB6 gives loc_5F05C/loc_5F116, so the banner
 * ends in loc_5F15A and the S3 eyecatch (loc_5F26C subtype 4). The native PAL capture of this
 * product (2026-10-16, poll 13311) holds Normal_palette line 0 at Pal_SonicTails and line 3 black.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingS3Eyecatch {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private HeadlessTestFixture boot(){
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        return HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
    }
    private EndingZoneRuntimeState zone(){return (EndingZoneRuntimeState)GameServices.zoneRuntimeState();}
    private S3kPostCreditsObjectInstance owner(){
        return GameServices.level().getObjectManager().activeObjectsOfType(S3kPostCreditsObjectInstance.class).getFirst();
    }
    private S3kPostCreditsObjectInstance start(HeadlessTestFixture fixture){
        // Declared Obj_5EF68 entry RAM for progress +2; the incoming scene owns its earlier frames.
        zone().ending().initializeProgress(7,7);
        S3kEndingCoordinator.beginPostCredits(TestEnvironment.objectServices());
        var owner=owner();owner.update(0,fixture.sprite());return owner;
    }
    /**
     * One native frame of this terminal: the object pass, then Ending_BackgroundEvent routine 4's
     * sub_5928C (044fa46 sonic3k.asm:118597), which native calls every frame until the exit.
     */
    private void frame(S3kPostCreditsObjectInstance owner,HeadlessTestFixture fixture,int pass){
        owner.update(pass,fixture.sprite());
        zone().screen().aizPalette.tickEndingWater(TestEnvironment.objectServices());
    }

    /**
     * loc_5F15A (044fa46 sonic3k.asm:125704-125706) sets Palette_cycle_counters+0 and nothing in the
     * terminal clears it. sub_5928C tests that byte first, so after the object fade to black the
     * water colours 13-15 of line 3 stay black (native poll 13311: all of line 3 is $000).
     */
    @Test void loc5F15AStopsTheEndingWaterCycleForTheRestOfTheTerminal(){
        var fixture=boot();var owner=start(fixture);
        int pass=0;
        while(owner.phase()<2)frame(owner,fixture,++pass);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        int savedPass=pass;
        int[] expected=new int[200];
        for(int i=0;i<200;i++){frame(owner,fixture,++pass);expected[i]=line3Water();}
        assertTrue(owner.phase()>=6,"the run reaches the S3 eyecatch");
        for(int i=48;i<200;i++)assertEquals(0,expected[i],"line 3 colours 13-15 stay black after the fade, pass "+(savedPass+1+i));
        registry.restore(saved);owner=owner();pass=savedPass;
        for(int i=0;i<200;i++){frame(owner,fixture,++pass);assertEquals(expected[i],line3Water(),"replay pass "+pass);}
    }

    /**
     * loc_5F26C subtype 4's sub_5FF1C (line 1, 16 colours), loc_5F31C (Normal_palette+2,
     * $3A=$E) and loc_5F410 (Normal_palette_line_3+2, $3A=$E) each allocate loc_5FF62
     * (044fa46 sonic3k.asm:126999). The allocations land after their allocators in the same pass
     * (native slots 4 -> 5, 8 -> 11, 10 -> 13), so each takes its first step on the eyecatch
     * initialization pass. sub_85EB4 raises every fourth pass, eight times; loc_5FF94 then
     * waits and sub_85F2A lowers toward the live Target_palette every fourth pass, eight times.
     */
    @Test void s3EyecatchFadesItsThreeLinesThroughWhiteToTheirTargets() throws Exception {
        var fixture=boot();var owner=start(fixture);
        int pass=0;
        while(owner.phase()!=6)frame(owner,fixture,++pass);
        int init=pass;
        var rom=TestEnvironment.objectServices().romReader();
        int[][] target={read(rom,Sonic3kConstants.SONIC_PALETTE_ADDR),read(rom,S3kEndingResources.PAL_S3K_LOGO),
                read(rom,S3kEndingResources.PAL_EYECATCH_KNUCKLES)};
        int[][] model=new int[3][16];
        // The object fade left every line black; sub_5FF1C blacks line 1 again before its first step.
        for(int line=0;line<3;line++)for(int c=0;c<16;c++)model[line][c]=0;
        int[] first={1,0,1};
        int wait=0,steps=15;
        for(int k=0;k<70;k++){
            if(k>0)frame(owner,fixture,++pass);
            // loc_5FF6E / loc_5FFA6 countdown shared by the three same-pass allocations.
            if(steps>=0&&--wait<0){
                wait=3;
                for(int line=0;line<3;line++)for(int c=first[line];c<16;c++)
                    model[line][c]=steps>=8?raise(model[line][c]):lower(model[line][c],target[line][c]);
                if(--steps==7)wait=2;
            }
            for(int line=0;line<3;line++)for(int c=first[line];c<16;c++)
                assertEquals(model[line][c],word(line,c),"pass init+"+(pass-init)+" line "+line+" colour "+c);
        }
        for(int line=0;line<3;line++)for(int c=first[line];c<16;c++)
            assertEquals(target[line][c],word(line,c),"settled line "+line+" colour "+c);
    }

    /** The fades and the stopped water cycle are rewind state: restore mid-fade and replay. */
    @Test void s3EyecatchFadeRestoresAndReplaysMidFade(){
        var fixture=boot();var owner=start(fixture);
        int pass=0;
        while(owner.phase()!=6)frame(owner,fixture,++pass);
        for(int i=0;i<13;i++)frame(owner,fixture,++pass);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        int savedPass=pass;int[][] expected=new int[60][];
        for(int i=0;i<60;i++){frame(owner,fixture,++pass);expected[i]=lines();}
        registry.restore(saved);owner=owner();pass=savedPass;
        for(int i=0;i<60;i++){frame(owner,fixture,++pass);assertArrayEquals(expected[i],lines(),"replay pass "+pass);}
    }

    /**
     * Obj_5EF68 (044fa46 sonic3k.asm:125517-125520) clears Super_palette_status on its first pass, so
     * SuperHyper_PalCycle stops writing line 0 while Super_Sonic_Knux_flag keeps its value.
     * Native PAL Sonic/+2 RAM: Super_palette_status 0 and Super_Sonic_Knux_flag $FF at polls 12287
     * and 13311, with line 0 static between the post-credits start and the object fade.
     */
    @Test void obj5EF68StopsTheHyperPaletteCycleWithoutRevertingTheForm(){
        var fixture=boot();
        var player=(com.openggf.sprites.playable.AbstractPlayableSprite)GameServices.sprites().getMainPlayable();
        var powered=(com.openggf.game.sonic3k.Sonic3kSuperStateController)player.getSuperStateController();
        powered.initializeEndingHyperSonic(); // rescue sub_5FCCE, as the scene leaves it
        for(int i=0;i<16;i++)powered.advanceEndingPaletteAtAnPal();
        var owner=start(fixture);
        int[] settled=line(0);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        for(int pass=1;pass<=120;pass++){
            owner.update(pass,fixture.sprite());powered.advanceEndingPaletteAtAnPal(); // AnPal_Load clock
            assertArrayEquals(settled,line(0),"line 0 after Obj_5EF68, pass "+pass);
        }
        assertTrue(powered.isHyperFormActive(),"Obj_5EF68 leaves the Hyper form flag set");
        registry.restore(saved);owner=owner();
        for(int pass=1;pass<=120;pass++){owner.update(pass,fixture.sprite());powered.advanceEndingPaletteAtAnPal();}
        assertArrayEquals(settled,line(0),"replayed line 0");
    }

    /**
     * loc_5F15A allocates Obj_FadeSelectedToBlack (044fa46 sonic3k.asm:179589) after its own slot.
     * Its first pass falls into loc_8555E with $2E=0, so DecColor_Obj runs on the allocation pass,
     * then every sixth pass, eight times. loc_5F18A sees the deleted fade's status bit 7 on the
     * following pass. Native Tails/+2 NTSC: Palette_cycle_counters+0 and the first decrement both
     * land on poll 14256; loc_5FD5A runs at 14299.
     */
    @Test void objectFadeStepsOnItsAllocationPassAndHandsOverAfterTheEighthStep(){
        var fixture=boot();var owner=start(fixture);
        int pass=0;int[] previous=line(1);
        while(owner.phase()<2){previous=line(1);frame(owner,fixture,++pass);}
        int allocation=pass;
        for(int k=0;k<=43;k++){
            if(k>0)frame(owner,fixture,++pass);
            int[] now=line(1);
            boolean changed=!java.util.Arrays.equals(previous,now),step=k%6==0&&k<=42;
            if(step&&!java.util.Arrays.equals(previous,new int[16]))
                assertTrue(changed,"DecColor_Obj runs on pass allocation+"+k);
            if(!step)assertFalse(changed,"no DecColor_Obj on pass allocation+"+k);
            if(k<43)assertEquals(2,owner.phase(),"the fade owns pass allocation+"+k);
            previous=now;
        }
        assertArrayEquals(new int[16],line(1),"eight steps leave line 1 black");
        assertEquals(3,owner.phase(),"loc_5F18A observes the deleted fade one pass after its eighth step");
        assertEquals(allocation+43,pass);
    }

    private int[] line(int line){int[] w=new int[16];for(int c=0;c<16;c++)w[c]=word(line,c);return w;}
    private static int raise(int w){ // sub_85EB4
        int b=(w>>>8)&0xE,g=w&0xE0,r=w&0xE;
        if(b<0xE)b+=2;if(g<0xE0)g+=0x20;if(r<0xE)r+=2;return b<<8|g|r;
    }
    private static int lower(int w,int t){ // sub_85F2A
        int b=(w>>>8)&0xE,g=w&0xE0,r=w&0xE;
        if(b>((t>>>8)&0xE))b-=2;if(g>(t&0xE0))g-=0x20;if(r>(t&0xE))r-=2;return b<<8|g|r;
    }
    private static int[] read(com.openggf.data.RomByteReader rom,int address){
        int[] words=new int[16];for(int c=0;c<16;c++)words[c]=rom.readU16BE(address+c*2);return words;
    }
    private int line3Water(){return word(3,13)<<24|word(3,14)<<12|word(3,15);}
    private int[] lines(){int[] all=new int[64];for(int i=0;i<64;i++)all[i]=word(i/16,i%16);return all;}
    private int word(int line,int color){
        return com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                TestEnvironment.objectServices().currentLevel().getPalette(line).getColor(color));
    }
}
