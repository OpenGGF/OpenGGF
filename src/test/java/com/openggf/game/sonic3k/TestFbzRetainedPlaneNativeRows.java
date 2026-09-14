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

    @Test void horizontalColumnsUseZeroOutdoorAndBlockAlignedIndoorSourcesAndRewind() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(4,0).build();
        var levels=GameServices.level();
        var events=((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).getFbzEvents();
        GameServices.camera().setY((short)159); // indoor background copy is77, not block aligned.
        events.setForegroundLayoutRegion(16);
        events.setOutdoorBobOffset(0); // Outdoor bob must not become the column source Y.
        var runtime=(FbzZoneRuntimeState)GameServices.zoneRuntimeRegistry().current();
        for (boolean outdoor : new boolean[]{true,false}) {
            events.setBackgroundRedraw(0, com.openggf.game.sonic3k.events.Sonic3kFBZEvents.RedrawDirection.NONE);
            events.setBackgroundOutdoor(!outdoor);
            // Establish the normal scrolling row owner before isolating a
            // fresh column pass; a mode switch also emits ordinary row strips.
            events.updateAct1BackgroundEvent(outdoor?0x1B01:0x1AFF,0xFF,false);
            events.setBackgroundRedraw(0, com.openggf.game.sonic3k.events.Sonic3kFBZEvents.RedrawDirection.NONE);
            events.setBackgroundOutdoor(!outdoor);
            byte[] before=levels.captureBackgroundVdpPlane();
            byte[] checkpoint=runtime.captureBytes();
            byte[] expected=before.clone();
            int sourceY=outdoor?0:64;
            int firstX=outdoor?0x3E0:0;
            for(int y=0;y<32;y++) for(int x=0;x<4;x++) {
                int worldX=firstX+x*8, worldY=sourceY+y*8;
                int descriptor=levels.getBackgroundTileDescriptorAtWorld(worldX,worldY);
                int address=(((worldY/8)&31)*64+((worldX/8)&63))*4;
                expected[address]=(byte)descriptor;
                expected[address+1]=(byte)(((descriptor>>8)&7)|((descriptor>>10)&24)
                        |((descriptor>>6)&96)|((descriptor>>8)&128));
                expected[address+2]=0;expected[address+3]=(byte)255;
            }
            for(int cycle=0;cycle<2;cycle++) {
                events.updateAct1BackgroundEvent(outdoor?0x1B01:0x1AFF,0xFF,false);
                assertArrayEquals(expected,levels.captureBackgroundVdpPlane(),
                        "native horizontal redraw supplies zero outdoors and16px aligned indoor block rows");
                runtime.restoreBytes(checkpoint);events.reconcileAct1State();
                assertArrayEquals(before,levels.captureBackgroundVdpPlane());
            }
        }
    }

}
