package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Native Draw_PlaneVertSingleBottomUp clipping/count oracle; no screenshot fixture data. */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzRetainedPlaneNativeRows {
    @Test void outdoorBobSkipsOutOfWindowRowThenWritesExactlyThirtyTwoBlocksAndRewinds() throws Exception {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(4,0).build();
        var levels=GameServices.level();
        var events=((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).getFbzEvents();
        GameServices.camera().setY((short)0x5DF);
        events.setForegroundLayoutRegion(24);
        events.setForegroundOutdoor(false);
        events.setBackgroundOutdoor(false);
        events.setOutdoorBobOffset(0); // Native $16 yields delayed start $100.
        byte[] before=levels.captureBackgroundVdpPlane();
        events.updateAct1BackgroundEvent(0x100,0x641,false);
        assertEquals(0x100,events.getBackgroundRedrawPosition());
        assertArrayEquals(before,levels.captureBackgroundVdpPlane(),
                "native d2=0 clips delayed position$100 outside [0,$F0] but consumes the pass");
        var runtime=(FbzZoneRuntimeState)GameServices.zoneRuntimeRegistry().current();
        byte[] checkpoint=runtime.captureBytes();
        byte[] expected=before.clone();
        for(int row=0;row<2;row++)for(int x=0;x<64;x++) {
            int descriptor=levels.getBackgroundTileDescriptorAtWorld(0x200+x*8,0xF0+row*8);
            int address=((30+row)*64+x)*4;
            expected[address]=(byte)descriptor;
            expected[address+1]=(byte)(((descriptor>>8)&7)|((descriptor>>10)&24)
                    |((descriptor>>6)&96)|((descriptor>>8)&128));
            expected[address+2]=0;expected[address+3]=(byte)255;
        }
        for(int cycle=0;cycle<2;cycle++) {
            events.updateAct1BackgroundEvent(0x100,0x641,false);
            assertArrayEquals(expected,levels.captureBackgroundVdpPlane(),
                    "Setup_TileRowDraw subtracts1 before DBF: 32 blocks, no extra next-row cells");
            runtime.restoreBytes(checkpoint);events.reconcileAct1State();
            assertArrayEquals(before,levels.captureBackgroundVdpPlane());
        }
    }
}
