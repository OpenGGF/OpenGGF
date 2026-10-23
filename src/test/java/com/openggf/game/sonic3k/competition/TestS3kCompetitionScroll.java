package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import com.openggf.game.sonic3k.competition.S3kCompetitionScroll.CameraPair;
import com.openggf.game.sonic3k.competition.S3kCompetitionScroll.CameraPosition;
import com.openggf.game.sonic3k.competition.S3kCompetitionScroll.Tracking;
import com.openggf.game.sonic3k.competition.S3kCompetitionScroll.TrackingOptions;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Independent native word/scanline cases; not an assertion of rendered arena-route parity. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionScroll {
    private Rom rom;
    @BeforeEach void rom() throws Exception {rom=RomManager.getInstance().getRom();}
    private S3kCompetitionScroll scroll(Arena arena) throws Exception {return new S3kCompetitionScroll(rom,arena,5);}
    private static CameraPair cameras(int x1,int y1,int x2,int y2) {
        return new CameraPair(new CameraPosition(x1,y1),new CameraPosition(x2,y2));
    }
    private static S3kCompetitionPlayer player(int slot,int x,int y) {
        return new S3kCompetitionPlayer(slot,slot==0?Character.SONIC:Character.TAILS,x,y);
    }
    private static int background(int packed) {return (short)packed;}
    private static int foreground(int packed) {return (short)(packed>>>16);}

    @Test void lockedOnEventPointersSelectActualS3HalfRoutinesAndTheirData() throws Exception {
        var bytes=RomByteReader.fromRom(rom);
        int[] init={0x23A6CC,0x23A6D2,0x23A6D8,0x23A6DE,0x23A716};
        int[] event={0x23A73E,0x23A748,0x23A796,0x23A752,0x23A75C};
        for(int i=0;i<5;i++) {
            int table=0x4E618+i*32;
            assertEquals(0x23A63C,bytes.readU32BE(table));assertEquals(init[i],bytes.readU32BE(table+4));
            assertEquals(0x23A63C,bytes.readU32BE(table+8));assertEquals(init[i],bytes.readU32BE(table+12));
            assertEquals(i==3?0x23A6AC:0x23A68A,bytes.readU32BE(table+16));
            assertEquals(event[i],bytes.readU32BE(table+20));assertEquals(event[i],bytes.readU32BE(table+28));
        }
        // ALZ_Deformation's LEA owns the S3-half noisy-delta copy; a matching
        // S&K-half table at $5097E is not evidence that ALZ points there.
        assertEquals(0x49FA,bytes.readU16BE(0x23A7F0));
        assertEquals(S3kCompetitionScroll.ALZ_DELTA_ADDRESS,0x23A7F2+(short)bytes.readU16BE(0x23A7F2));
        assertEquals(0xFFFE,bytes.readU16BE(S3kCompetitionScroll.ALZ_DELTA_ADDRESS));
        assertEquals(1,bytes.readU16BE(S3kCompetitionScroll.ALZ_DELTA_ADDRESS+2));
    }
    @Test void allArenaMasksAndPlaneProfilesComeFromNativeRomWords() throws Exception {
        int[][] expected={
                {0x3FF,0x1FF,0x1F0,0xC,0x100,0x100,0xF,0x40,0x100,0x190},
                {0x1FF,0x3FF,0x3F0,0x1C,0x80,0x200,0x1F,0x20,0x200,0x390},
                {0x3FF,0x1FF,0x1F0,0xC,0x100,0x100,0xF,0x40,0x100,0x190},
                {0x3FF,0xFF,0xF0,4,0x100,0x100,0xF,0x40,-0x100,0x1000},
                {0x3FF,0x1FF,0x1F0,0xC,0x100,0x100,0xF,0x40,0x100,0x190}};
        for(Arena arena:Arena.values()) {
            var p=scroll(arena).profile();int[] e=expected[arena.ordinal()];
            assertArrayEquals(e,new int[]{p.xWrapMask(),p.yWrapMask(),p.cameraYMask(),p.layoutRowIndexMask(),
                    p.vramDuplicateStride(),p.foregroundRefreshY(),p.refreshRowsMinusOne(),p.refreshColumns(),
                    p.bounds().minimumY(),p.bounds().maximumY()});
        }
        var alternate=new S3kCompetitionScroll(rom,Arena.CHROME_GADGET,1,5);
        assertEquals(0,alternate.profile().bounds().minimumY());assertEquals(0x90,alternate.profile().bounds().maximumY());
    }
    @Test void allFiveArenasFillExactlyTheNative108And116RasterRegions() throws Exception {
        for(Arena arena:Arena.values()) {
            var s=scroll(arena);s.initialize(cameras(0x500,0x148,0x640,0x168));
            assertEquals(108,s.view(0).horizontalScroll().size());assertEquals(116,s.view(1).horizontalScroll().size());
            assertEquals(0,s.view(0).nativeRasterStart());assertEquals(108,s.view(1).nativeRasterStart());
            int[] packed=s.horizontalScrollBuffer();assertEquals(224,packed.length);
            for(int y=0;y<224;y++) assertEquals(y<108?-0x500:-0x640,foreground(packed[y]),arena+" row "+y);
            assertEquals(0x148,s.view(0).foregroundVScroll());assertEquals(0xF8,s.view(1).foregroundVScroll());
            int original=packed[0];packed[0]=0;assertEquals(original,s.horizontalScrollBuffer()[0]);
        }
    }
    @Test void azureLakeScatterFixedPointAndPerRasterWaterNoiseMatchNativeBands() throws Exception {
        var s=scroll(Arena.AZURE_LAKE);s.initialize(cameras(0x400,0x148,0x400,0x148));
        var p=s.view(0);assertEquals(0x48,p.backgroundY());
        assertEquals(List.of(80,16,96,48,64,32,0x40,0x60,0x80),p.intermediate().subList(0,9));
        assertEquals(0x200,p.intermediate().get(72));assertEquals(0,p.intermediate().get(73));
        assertEquals(0x3E,p.intermediate().get(9));assertEquals(0x41,p.intermediate().get(10));
        var rows=p.horizontalScroll();
        assertEquals(-0x40,background(rows.get(0)));assertEquals(-0x40,background(rows.get(37)));
        assertEquals(-0x60,background(rows.get(38)));assertEquals(-0x80,background(rows.get(44)));
        assertEquals(-0x3E,background(rows.get(57)));assertEquals(-0x41,background(rows.get(58)));
        var second=s.view(1).horizontalScroll();
        assertEquals(-0x40,background(second.get(41)));assertEquals(-0x60,background(second.get(42)));
        assertEquals(-0x3E,background(second.get(61)),"P2 starts deformation from BG Y minus four");
        assertEquals(3,s.capture().waveCounter());assertEquals(0x1000,s.capture().cloudDrift());
        s.advance(cameras(0x400,0x148,0x400,0x148));
        assertEquals(6,s.capture().waveCounter());assertEquals(0x2000,s.capture().cloudDrift(),"shared accumulators advance once for both views");
    }
    @Test void otherArenaIntermediateRatiosAndDesertSecondViewVerticalBiasAreNative() throws Exception {
        var bpz=scroll(Arena.BALLOON_PARK);bpz.initialize(cameras(0x400,0x2C8,0x400,0x2C8));
        assertEquals(List.of(0x80,0xC0,0x100,0x140,0x180,0x1C0,0x200),bpz.view(0).intermediate().subList(0,7));
        assertEquals(0x90,bpz.view(0).backgroundY());
        assertEquals(-0xC0,background(bpz.view(0).horizontalScroll().get(13)));
        assertEquals(-0x100,background(bpz.view(0).horizontalScroll().get(14)));
        var cgz=scroll(Arena.CHROME_GADGET);cgz.initialize(cameras(0x400,0,0x400,0));
        assertEquals(List.of(0x120,0x80,0x100,0x180,0x200),cgz.view(0).intermediate().subList(0,5));
        var emz=scroll(Arena.ENDLESS_MINE);emz.initialize(cameras(0x400,0x148,0x400,0x148));
        assertEquals(List.of(0x200,0x280,0x200,0x180,0x200,0x180,0x100,0x80,0x100,0x180),emz.view(0).intermediate().subList(0,10));
        var dpz=scroll(Arena.DESERT_PALACE);dpz.initialize(cameras(0x400,0x148,0x400,0x168));
        assertEquals(0x200,dpz.view(0).backgroundX());assertEquals(8,dpz.view(0).backgroundY());
        assertEquals(0x8A,dpz.view(1).backgroundY());assertEquals(0x1A,dpz.view(1).backgroundVScroll());
        for(int row:dpz.horizontalScrollBuffer()) assertEquals(-0x200,background(row));
    }
    @Test void eachViewUnwrapsHorizontalLoopsIndependentlyIncludingTheHalfSpanEqualityEdge() throws Exception {
        var s=scroll(Arena.AZURE_LAKE);s.initialize(cameras(0x7F0,0x148,0x410,0x148));
        s.advance(cameras(0x410,0x148,0x7F0,0x148));
        assertEquals(0x810,s.view(0).unwrappedX());assertEquals(0x3F0,s.view(1).unwrappedX());
        assertEquals(0x408,s.view(0).intermediate().get(72));assertEquals(0x1F8,s.view(1).intermediate().get(72));
        s.initialize(cameras(0x400,0x148,0x400,0x148));s.advance(cameras(0x600,0x148,0x600,0x148));
        assertEquals(0x200,s.view(0).unwrappedX(),"native unsigned >=half-span selects the wrapped negative displacement");
        var bpz=scroll(Arena.BALLOON_PARK);bpz.initialize(cameras(0x5F0,0x2C8,0x410,0x2C8));
        bpz.advance(cameras(0x410,0x2C8,0x5F0,0x2C8));
        assertEquals(0x610,bpz.view(0).unwrappedX());assertEquals(0x3F0,bpz.view(1).unwrappedX());
    }
    @Test void chromeGadgetUnwrappedVerticalRouteDrivesRatioAndClampsNegativeBackground() throws Exception {
        var s=scroll(Arena.CHROME_GADGET);s.initialize(cameras(0x400,0xF0,0x500,0x10));
        assertEquals(0x900000/0x590,s.cgzVerticalRatio());
        s.advance(cameras(0x400,0x10,0x500,0xF0));
        assertEquals(0x110,s.view(0).unwrappedY());assertEquals(-0x10,s.view(1).unwrappedY());
        assertEquals((0x110*(0x900000/0x590))>>>16,s.view(0).backgroundY());
        assertEquals(0,s.view(1).backgroundY());
    }
    @Test void nativeHorizontalTrackingKeepsDeadbandCapsAndPerSlotLocks() throws Exception {
        var s=scroll(Arena.AZURE_LAKE);s.initialize(cameras(0x500,0x150,0x500,0x150));
        var p1=player(0,0x590,0x180);var p2=player(1,0x59F,0x180);
        s.trackPlayers(p1,p2);assertEquals(0x500,s.view(0).camera().x());assertEquals(0x500,s.view(1).camera().x());
        p1.setCentre(0x580,0x180);p2.setCentre(0x5B0,0x180);s.trackPlayers(p1,p2);
        assertEquals(0x4F0,s.view(0).camera().x());assertEquals(-0x1000,s.view(0).horizontalDelta());
        assertEquals(0x510,s.view(1).camera().x());assertEquals(0x1000,s.view(1).horizontalDelta());
        var locked=s.view(0).camera();p1.setCentre(0x700,0x300);
        s.trackPlayers(new Tracking(p1,0x60,true),new Tracking(p2),TrackingOptions.NORMAL);
        assertEquals(locked,s.view(0).camera());assertEquals(0,s.view(0).horizontalDelta());
    }
    @Test void nativeVerticalTrackingDistinguishesGroundAirFastAndLookDistanceBranches() throws Exception {
        var s=scroll(Arena.AZURE_LAKE);s.initialize(cameras(0x500,0x150,0x500,0x150));
        var p1=player(0,0x590,0x1A0);var p2=player(1,0x590,0x150);
        s.trackPlayers(p1,p2);assertEquals(0x156,s.view(0).camera().y());assertEquals(0x14A,s.view(1).camera().y());
        assertEquals(0x600,s.view(0).verticalDelta());assertEquals(-0x600,s.view(1).verticalDelta());
        s.initialize(cameras(0x500,0x150,0x500,0x150));p1.setAirborne(true);p2.setGroundVelocity(0x800);
        p1.setCentre(0x590,0x180);p2.setCentre(0x590,0x1C0);s.trackPlayers(p1,p2);
        assertEquals(0x150,s.view(0).camera().y(),"airborne +-32px deadband");assertEquals(0x168,s.view(1).camera().y());
        p1.setCentre(0x590,0x1D0);s.trackPlayers(new Tracking(p1),new Tracking(p2,0x20,false),TrackingOptions.NORMAL);
        assertEquals(0x168,s.view(0).camera().y());assertEquals(0x16A,s.view(1).camera().y());
        s.initialize(cameras(0x500,0x150,0x500,0x150));p1.setAirborne(false);p1.setRolling(true);p1.setCentre(0x590,0x185);
        s.trackPlayers(new Tracking(p1),null,TrackingOptions.NORMAL);assertEquals(0x150,s.view(0).camera().y(),"rolling subtracts five before competition target");
    }
    @Test void chromeVerticalTrackingWrapsBothDirectionsAndRetainsNativeDelta() throws Exception {
        var s=scroll(Arena.CHROME_GADGET);s.initialize(cameras(0x500,0xFA,0x500,2));
        var p1=player(0,0x590,0x50);var p2=player(1,0x590,0x20);
        s.trackPlayers(p1,p2);
        assertEquals(0,s.view(0).camera().y());assertEquals(0x600,s.view(0).verticalDelta());
        assertEquals(0x100,s.view(0).unwrappedY());
        assertEquals(0xFC,s.view(1).camera().y());assertEquals(-0x600,s.view(1).verticalDelta());
        assertEquals(-4,s.view(1).unwrappedY());assertEquals(0,s.view(1).backgroundY());
    }
    @Test void positionHistoryLagIsNativePerSlotAndTeleportDoesNotConsumeItsTimer() throws Exception {
        var s=scroll(Arena.AZURE_LAKE);s.initialize(cameras(0x500,0x150,0x500,0x150));
        var p1=player(0,0x650,0x180);var p2=player(1,0x650,0x180);
        s.resetPositionHistory(0,0x550);s.requestSpinDashLag(0,0x800);
        s.trackPlayers(p1,p2);
        assertEquals(0x4F0,s.view(0).camera().x());assertEquals(0x1F00,s.view(0).cameraLagWord());
        assertEquals(0x510,s.view(1).camera().x());assertEquals(0,s.view(1).cameraLagWord());
        s.trackPlayers(new Tracking(p1),new Tracking(p2),new TrackingOptions(true,false,false));
        assertEquals(0x1F00,s.view(0).cameraLagWord());
        s.requestSpinDashLag(0,0xC00);assertEquals(0x1800,s.view(0).cameraLagWord());
        s.requestSpinDashLag(1,-0xA00);assertEquals(0x1C00,s.view(1).cameraLagWord());
        for(int i=0;i<64;i++) {p1.setCentre(0x600+i,0x180);s.recordPositions(p1,p2);}
        assertEquals(0,s.capture().views().get(0).historyIndex());
        assertEquals(0x63F,s.capture().views().get(0).historyX().get(63));
    }
    @Test void cameraInitializationUsesNativePlayerOffsetsWithoutCampaignClamping() throws Exception {
        var s=scroll(Arena.AZURE_LAKE);var p1=player(0,0x700,0x1D0);var p2=player(1,0x780,0x1E0);
        s.initializeFromPlayers(p1,p2);
        assertEquals(new CameraPosition(0x660,0x170),s.view(0).camera());
        assertEquals(new CameraPosition(0x660,0x170),s.view(1).camera(),"native start initializes both cameras from P1");
        assertEquals(0x700,s.capture().views().get(0).historyX().get(0));
        assertEquals(0x780,s.capture().views().get(1).historyX().get(63));
        assertThrows(IllegalArgumentException.class,()->s.trackPlayers(p2,p1));
    }
    @Test void allArenaSnapshotsRestoreCapturedRasterAndBothViewsThenReplayForwardExactly() throws Exception {
        for(Arena arena:Arena.values()) {
            var s=scroll(arena);s.initialize(cameras(0x500,0x148,0x680,0x168));
            var p1=player(0,0x5A0,0x180);var p2=player(1,0x720,0x1A0);
            s.requestSpinDashLag(0,0xA00);
            for(int f=0;f<6;f++) {s.trackPlayers(p1,p2);s.recordPositions(p1,p2);}
            var before=s.capture();int[] beforeRaster=s.horizontalScrollBuffer();
            for(int f=6;f<30;f++) s.advance(cameras(0x400+(f*17&0x1FF),0x100+(f*11&0xFF),0x400+(f*23&0x1FF),0x100+(f*7&0xFF)));
            var expected=s.capture();int[] expectedRaster=s.horizontalScrollBuffer();
            var restored=scroll(arena);restored.restore(before);
            assertEquals(before,restored.capture());assertArrayEquals(beforeRaster,restored.horizontalScrollBuffer());
            for(int f=6;f<30;f++) restored.advance(cameras(0x400+(f*17&0x1FF),0x100+(f*11&0xFF),0x400+(f*23&0x1FF),0x100+(f*7&0xFF)));
            assertEquals(expected,restored.capture());assertArrayEquals(expectedRaster,restored.horizontalScrollBuffer());
            assertThrows(UnsupportedOperationException.class,()->before.views().get(0).table().set(0,123));
        }
    }
}
