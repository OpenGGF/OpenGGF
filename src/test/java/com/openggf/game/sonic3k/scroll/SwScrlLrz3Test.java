package com.openggf.game.sonic3k.scroll;

import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class SwScrlLrz3Test {
    @Test void distantScrollAndHeatReadIndependentRomPhasesAndEventGates() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(22,0).build();
        var rom=GameServices.rom().getRom();var scroll=new SwScrlLrz3(rom);
        var state=new LrzZoneRuntimeState(22,0,PlayerCharacter.SONIC_AND_TAILS);
        int[] buffer=new int[224]; byte[] wave=rom.readBytes(0x5077E,64);
        for(int cameraY:new int[]{-17,0,0x2F0,0x430}) for(int frame:new int[]{0,1,15,31,0x4000,0xFFFF}) {
            for(int stage:new int[]{4,8,12}) {
                state.bossAct().setForegroundRoutine(stage);
                scroll.render(buffer,0x921,cameraY,frame,state,320,false);
                int bgX=0x921>>4,bgY=(cameraY>>4)+16;
                assertEquals(bgY,scroll.getVscrollFactorBG()); assertEquals(bgX>>1,state.animationPhaseX1());
                for(int y=0;y<224;y++) {
                    int fi=(((cameraY+2*frame)&62)/2+y)&31,bi=(((bgY+frame)&62)/2+y)&31;
                    int fw=stage>=8?word(wave,fi):0,bw=stage>=8?word(wave,bi):0;
                    assertEquals((short)(-0x921+fw),(short)(buffer[y]>>16));
                    assertEquals((short)(-bgX+bw),(short)buffer[y]);
                }
            }
        }
        state.bossAct().setCapsuleOpened(true);scroll.render(buffer,0x921,0x430,0,state,320,false);
        assertEquals(1,java.util.Arrays.stream(buffer).distinct().count());
        state.bossAct().setCapsuleOpened(false);scroll.render(buffer,0x921,0x430,0,state,320,true);
        assertEquals(1,java.util.Arrays.stream(buffer).distinct().count());
    }

    @Test void lavaCarryArithmeticAndColumnSamplesRoundTripAtEveryAmplitude() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(22,0).build();
        var scroll=new SwScrlLrz3(GameServices.rom().getRom());
        var state=new LrzZoneRuntimeState(22,0,PlayerCharacter.SONIC_AND_TAILS);
        state.setBackgroundRoutine(12);state.bossAct().setForegroundRoutine(12);
        int[] buffer=new int[224];
        for(int direction:new int[]{1,-1}) for(int amplitude=0;amplitude<=128;amplitude++) {
            state.bossAct().rebuildLavaHeights(amplitude,direction);
            byte[] before=state.captureBytes();state.bossAct().rebuildLavaHeights(0,1);state.restoreBytes(before);
            // Independent native ADD/ADDX repeated fractional accumulator, followed by
            // SUB/SUBX into the opposite sixteen-byte extension.
            int[] expected=new int[192];int accumulator=0x300000;
            for(int i=0;i<176;i++) {
                int index=direction<0?175-i:16+i;expected[index]=(accumulator>>16)&255;
                accumulator+=amplitude<<8;
            }
            accumulator=0x300000;
            for(int i=0;i<16;i++) {
                accumulator-=amplitude<<8;expected[direction<0?176+i:15-i]=(accumulator>>16)&255;
            }
            for(int i=0;i<192;i++) assertEquals(expected[i],state.bossAct().lavaHeight(i));
            scroll.render(buffer,0xA00,0x560,12,state,800,false);
            assertEquals(0x60,scroll.getVscrollFactorBG());assertEquals(50,scroll.getPerColumnVScrollBG().length);
            for(int i=0;i<50;i++) assertEquals(expected[Math.min(191,19+8*i)]-48,scroll.getPerColumnVScrollBG()[i]);
        }
        state.setBackgroundRoutine(16);scroll.render(buffer,0xA00,0x560,12,state,320,false);
        assertNull(scroll.getPerColumnVScrollBG());
    }
    @Test void bossBackgroundWindowReadsThePoolBeyondTheDistantStrip() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(22,0).build();
        var level = GameServices.level();
        var provider = level.getZoneFeatureProvider();
        assertTrue(provider.bgWrapsHorizontally(), "DrawBGAsYouMove must move the Plane B window");
        assertTrue(provider.useLinearBackgroundLayoutOverflow(22),
                "sub_59DA2 selects source $300, beyond the distant strip's $200 extent");
        assertEquals(512, com.openggf.level.LevelGeometry.forLevel(level.getCurrentLevel())
                .bgContiguousWidthPx());
        assertNotEquals(0, level.getBackgroundTileDescriptorAtWorld(0x300, 0x120) & 0x8000,
                "ROM pool tiles cover the low-priority foreground lava wall");
        var effects = new com.openggf.game.render.SpecialRenderEffectRegistry();
        provider.registerSpecialRenderEffects(effects, 22, 0);
        assertEquals(1, effects.size(com.openggf.game.render.SpecialRenderEffectStage.AFTER_FOREGROUND));
        assertEquals(1, effects.size(com.openggf.game.render.SpecialRenderEffectStage.SPRITE_PRIORITY_MASK));
        effects.clear();
        provider.registerSpecialRenderEffects(effects, 22, 1);
        assertTrue(effects.isEmpty(), "Hidden Palace does not inherit the boss pool replay");
        assertNotEquals(level.getBackgroundTileDescriptorAtWorld(0x100, 0x100),
                level.getBackgroundTileDescriptorAtWorld(0x300, 0x100),
                "Wrapping the pool source at $200 reads different ROM art");
    }

    private static short word(byte[] data,int index) {return (short)((data[2*index]&255)<<8|data[2*index+1]&255);}
}
