package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.resources.TestCdR13SectionArt.*;
import static org.junit.jupiter.api.Assertions.*;

/** Typed UNKNOWN values over the existing generic storage; no bank or GPU. Java UNRUN. */
class TestCdR13ArtResidency {
    @Test void exactUnionOfEveryActualPlcOutputAndTheRawLifeWriterDefinesBothProfiles()throws Exception{
        for(var a:actual().all()){var s=CdR13ArtResidency.unknown(a);Set<Integer> expected=new TreeSet<>();var sources=CdR13SectionSources.fromAssets(a);for(var list:a.plcs())for(var row:list.entries())for(int at=0;at<sources.source(row.sourceNativeAddress()).outputBytes();at+=32)expected.add(row.vramAddress()+at);for(int at=0;at<128;at+=32)expected.add(0xB420+at);
            Set<Integer> actual=new TreeSet<>();for(var region:CdR13ArtResidency.Region.values()){var snap=s.region(region);for(int at=0;at<snap.byteLength();at+=32)actual.add(region.destinationWord()+at);for(byte b:snap.knownMask().copy())assertEquals(0,b);}assertEquals(expected,actual);assertEquals(11,CdR13ArtResidency.Region.values().length);assertTrue(s.ownsAssets(a));assertEquals(0,knownTiles(s));}
    }
    @Test void oneActualTileUpdatesOnlyItsWindowAndKeepsUnknownGaps()throws Exception{
        var a=actual().good();var before=CdR13ArtResidency.unknown(a);var bytes=CdR13SectionSources.fromAssets(a).decoded(CdR13SectionSources.SourceId.ART_RINGS).bytes().slice(0,32);var after=CdR13ArtResidency.withTile(before,0xF5C0,bytes);assertEquals(0,knownTiles(before));assertEquals(1,knownTiles(after));assertArrayEquals(bytes.copy(),CdR13ArtResidency.readTile(after,0xF5C0>>>5).copy());for(var r:CdR13ArtResidency.Region.values())if(r!=CdR13ArtResidency.Region.RINGS)assertSame(before.region(r),after.region(r));
        assertEquals(CdR13ArtResidency.Boundary.UNKNOWN_PATTERN,assertThrows(CdR13ArtResidency.OwnershipBoundary.class,()->CdR13ArtResidency.readTile(before,0)).boundary());for(int gap:new int[]{0x6600,0xA880,0xB060,0xF000})assertEquals(CdR13ArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,assertThrows(CdR13ArtResidency.OwnershipBoundary.class,()->CdR13ArtResidency.readTile(after,gap>>>5)).boundary());}
    @Test void explicitClearIsPureAndOwnedValuesKeepExactAggregateIdentity()throws Exception{
        var f=actual();var unknown=CdR13ArtResidency.unknown(f.good());var cleared=CdR13ArtResidency.clear(unknown);assertEquals(0,knownTiles(unknown));for(var r:CdR13ArtResidency.Region.values())for(int at=0;at<cleared.region(r).byteLength();at++)assertEquals(0,cleared.region(r).knownByte(at).orElseThrow());assertFalse(cleared.ownsAssets(f.sameGood()));assertFalse(cleared.ownsAssets(f.bad()));assertFalse(cleared.ownsAssets(null));assertNotEquals(unknown,CdR13ArtResidency.unknown(f.sameGood()));assertThrows(IllegalArgumentException.class,()->CdR13ArtResidency.withTile(unknown,1,CdAssetBytes.copyOf(new byte[32])));assertThrows(IllegalArgumentException.class,()->CdR13ArtResidency.readTile(unknown,0x800));assertThrows(NullPointerException.class,()->CdR13ArtResidency.unknown(null));}
}
