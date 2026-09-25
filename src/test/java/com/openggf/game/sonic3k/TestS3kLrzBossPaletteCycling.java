package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Palette;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzBossPaletteCycling {
    @Test void bothChannelsUseRomColorsAndIndependentPeriodsAcrossWrap() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(22,0).build();
        var level=GameServices.level().getCurrentLevel(); var rom=GameServices.rom().getRom();
        var state=S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().bossAct();
        state.setPaletteMode(1);
        var cycler=new Sonic3kPaletteCycler(RomByteReader.fromRom(rom),level,22,0);
        int untouched=color(level.getPalette(3),1);
        for(int tick=0;tick<257;tick++) {
            cycler.update();
            assertColors(level.getPalette(2),1,rom.readBytes(0x327E+((tick/16)%16)*8,8));
            assertColors(level.getPalette(3),12,rom.readBytes(0x36EC+((tick/8)%15)*4,4));
            assertEquals(untouched,color(level.getPalette(3),1),"LRZ1/2 crystal channel must not run");
        }
    }

    @Test void modeGatesFreezeBothClocksOrOnlyDisableFireAndStateRewinds() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(22,0).build();
        var level=GameServices.level().getCurrentLevel(); var rom=GameServices.rom().getRom();
        var runtime=S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var state=runtime.bossAct();
        var cycler=new Sonic3kPaletteCycler(RomByteReader.fromRom(rom),level,22,0);
        int fireBefore=color(level.getPalette(3),12); cycler.update();
        assertEquals(fireBefore,color(level.getPalette(3),12));
        byte[] frozen=cycler.captureCyclerState(); state.setPaletteMode(0x80);
        for(int i=0;i<100;i++) cycler.update();
        assertArrayEquals(frozen,cycler.captureCyclerState());
        state.setPaletteMode(1); byte[] before=cycler.captureCyclerState(), world=runtime.captureBytes();
        for(int i=0;i<120;i++) cycler.update();
        byte[] after=cycler.captureCyclerState(); int primary=color(level.getPalette(2),1), fire=color(level.getPalette(3),12);
        state.setPaletteMode(0x80); runtime.restoreBytes(world); cycler.restoreCyclerState(before);
        for(int i=0;i<120;i++) cycler.update();
        assertArrayEquals(after,cycler.captureCyclerState());
        assertEquals(primary,color(level.getPalette(2),1)); assertEquals(fire,color(level.getPalette(3),12));
    }

    private static void assertColors(Palette palette,int first,byte[] data) {
        for(int i=0;i<data.length/2;i++) {
            var expected=new Palette.Color();expected.fromSegaFormat(data,2*i);
            assertEquals(rgb(expected),color(palette,first+i),"color "+(first+i));
        }
    }
    private static int color(Palette p,int index) {return rgb(p.getColor(index));}
    private static int rgb(Palette.Color c) {return (c.r&255)<<16|(c.g&255)<<8|(c.b&255);}
}
