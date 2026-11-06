package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.level.TestCdR13PlaneSources.*;
import static org.junit.jupiter.api.Assertions.*;

/** New explicit native-HUD profile; legacy PLC/life profile and11 Region values remain exact. Java UNRUN. */
class TestCdR13NativeHudProfile {
    @Test void oldProfileRetainsEveryOriginalUnknownWindowAndRejectsHudGaps()throws Exception{
        for(var a:actual()){var old=CdR13ArtResidency.unknown(a);assertFalse(old.hasNativeHudProfile());assertEquals(11,CdR13ArtResidency.Region.values().length);for(var r:CdR13ArtResidency.HudRegion.values())assertTrue(old.nativeHud(r).isEmpty());for(int dest:new int[]{0xB060,0xB400,0xB4A0,0xB4C0})assertEquals(CdR13ArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,assertThrows(CdR13ArtResidency.OwnershipBoundary.class,()->CdR13ArtResidency.readTile(old,dest>>>5)).boundary());}
    }
    @Test void nativeProfileAddsOnlyTwoAuthenticWriterSpansWithNoInitialKnowledge()throws Exception{
        for(var a:actual()){var state=CdR13ArtResidency.unknownForNativeHud(a);assertTrue(state.hasNativeHudProfile());assertTrue(state.ownsAssets(a));assertEquals(2,CdR13ArtResidency.HudRegion.values().length);assertEquals(0xB060,CdR13ArtResidency.HudRegion.NUMBERS.destinationWord());assertEquals(0x3C0,state.nativeHud(CdR13ArtResidency.HudRegion.NUMBERS).orElseThrow().byteLength());assertEquals(0xB4A0,CdR13ArtResidency.HudRegion.LIVES.destinationWord());assertEquals(64,state.nativeHud(CdR13ArtResidency.HudRegion.LIVES).orElseThrow().byteLength());for(var r:CdR13ArtResidency.HudRegion.values())for(byte b:state.nativeHud(r).orElseThrow().knownMask().copy())assertEquals(0,b);assertEquals(CdR13ArtResidency.Boundary.UNKNOWN_PATTERN,assertThrows(CdR13ArtResidency.OwnershipBoundary.class,()->CdR13ArtResidency.readTile(state,0xB060>>>5)).boundary());for(int gap:new int[]{0x6600,0xA880,0xB4E0,0xF300})assertEquals(CdR13ArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,assertThrows(CdR13ArtResidency.OwnershipBoundary.class,()->CdR13ArtResidency.readTile(state,gap>>>5)).boundary());}
    }
    @Test void originalRawTileWriteUpdatesOneNativeHudTileAndPreservesEntireArtTuple()throws Exception{
        for(var a:actual()){var art=CdR13SectionArt.fromAssets(a);var current=CdR13ArtResidency.unknownForNativeHud(a);var s=art.fromValues(fill(512,0x5A),current,0xA55A,CdR11aPlcBuffer.cleared(),Optional.empty(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.XOR,1,2,3,4,5),0,6,7,8);var raw=CdR13ObjectAssets.fromAssets(a).hudAssets().numbers().slice(64,32);var out=art.writeCurrentTile(s,0xB4A0,raw);assertSame(s.queue(),out.queue());assertSame(s.codeTable(),out.codeTable());assertSame(s.savedDecoder(),out.savedDecoder());assertEquals(s.sectionWord(),out.sectionWord());assertEquals(s.processBudgetWord(),out.processBudgetWord());assertEquals(s.hintWord(),out.hintWord());assertEquals(s.padWord(),out.padWord());assertArrayEquals(raw.copy(),CdR13ArtResidency.readTile(out.residency(),0xB4A0>>>5).copy());assertTrue(current.nativeHud(CdR13ArtResidency.HudRegion.LIVES).orElseThrow().knownByte(0).isEmpty());assertTrue(out.residency().nativeHud(CdR13ArtResidency.HudRegion.LIVES).orElseThrow().knownByte(32).isEmpty());for(var r:CdR13ArtResidency.Region.values())assertSame(current.region(r),out.residency().region(r));}
    }
    @Test void explicitClearMakesOnlyOwnedNativeHudBytesKnownAndLeavesLegacyValueUnknown()throws Exception{
        for(var a:actual()){var before=CdR13ArtResidency.unknownForNativeHud(a);var after=CdR13ArtResidency.clear(before);for(var r:CdR13ArtResidency.HudRegion.values())for(int at=0;at<after.nativeHud(r).orElseThrow().byteLength();at++){assertTrue(before.nativeHud(r).orElseThrow().knownByte(at).isEmpty());assertEquals(0,after.nativeHud(r).orElseThrow().knownByte(at).orElseThrow());}assertThrows(NullPointerException.class,()->CdR13ArtResidency.unknownForNativeHud(null));}
    }
}
