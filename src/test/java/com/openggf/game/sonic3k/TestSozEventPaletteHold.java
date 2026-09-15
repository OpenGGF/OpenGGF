package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozEventPaletteHold {
    @Test void coldAct2TicksButSeamlessFadeHoldsClocksAndColors() throws Exception {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(8,1).withFreshLevelStartLifecycle().build();
        var state=S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var level=GameServices.level().getCurrentLevel();var registry=GameServices.paletteOwnershipRegistryOrNull();
        var cycle=new Sonic3kPaletteCycler(RomByteReader.fromRom(GameServices.rom().getRom()),level,8,1,registry,null);
        cycle.update();assertEquals(899,state.lighting().masterTimer());assertEquals(1,state.lighting().darknessLevel());
        state.lighting().initializeSeamlessDarkness();state.events().seamlessEntry(true);
        for(int line:new int[]{2,3})S3kPaletteWriteSupport.applyLine(registry,level,GameServices.graphics(),"test",999,line,new byte[32],true);
        registry.beginFrame();
        byte[] before=state.captureBytes();for(int tick=0;tick<40;tick++)cycle.update();
        assertArrayEquals(before,state.captureBytes());assertEquals(1799,state.lighting().masterTimer());
        for(int line:new int[]{2,3})for(int color=0;color<16;color++)assertEquals(0,
                com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(level.getPalette(line).getColor(color)));
        state.events().seamlessEntry(false);cycle.update();assertEquals(1798,state.lighting().masterTimer());
    }
}
