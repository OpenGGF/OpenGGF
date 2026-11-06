package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Source-first values only; ALL JAVA UNRUN. Actual clear/publication stays with the native caller. */
class TestCdR12bCameraArtResidency {
    @Test void eightPastRegionsAreTheAuditedPlcPlusDirectWriterUnionRatherThanThePresentProfile() {
        int[][] expected={{0,0x63A0},{0x6600,0x560},{0x6BC0,0x35A0},{0xA400,0x10E0},
                {0xB500,0xA40},{0xD000,0x960},{0xDAE0,0x4E0},{0xF5C0,0x1C0}};
        var regions=CdR12bCameraArtResidency.Region.values();assertEquals(8,regions.length);int tiles=0;
        for(int i=0;i<regions.length;i++){assertEquals(expected[i][0],regions[i].destinationWord());assertEquals(expected[i][1],regions[i].byteLength());tiles+=regions[i].tileCount();}
        assertEquals(1614,tiles);assertNotEquals(1542,tiles);assertNotEquals(1633,tiles);
    }

    @Test void unknownConstructionNeverExposesAnyOwnedByteAsAKnownPixel() {
        var state=CdR12bCameraArtResidency.unknown();
        for(var region:CdR12bCameraArtResidency.Region.values()) {
            for(int at=0;at<region.byteLength();at++)assertTrue(state.region(region).knownByte(at).isEmpty());
            for(int at=0;at<region.byteLength();at+=32) {
                int address=region.destinationWord()+at;
                var failure=assertThrows(CdR12bCameraArtResidency.OwnershipBoundary.class,()->CdR12bCameraArtResidency.readTile(state,address>>>5));
                assertEquals(CdR12bCameraArtResidency.Boundary.UNKNOWN_PATTERN,failure.boundary());
            }
        }
    }

    @Test void playerPlanesGapsAndLegacyContinueTailStayOutsideThisPatternValue() {
        var state=CdR12bCameraArtResidency.unknown();
        for(int address:new int[]{0x63A0,0x6540,0x6B60,0x6BA0,0xA160,0xB4E0,0xBF40,0xC000,0xD960,0xDFC0,0xDFE0,0xE000,0xF000,0xF2E0,0xF300,0xF780,0xF800,0xFC00}) {
            var failure=assertThrows(CdR12bCameraArtResidency.OwnershipBoundary.class,()->CdR12bCameraArtResidency.readTile(state,address>>>5));
            assertEquals(CdR12bCameraArtResidency.Boundary.OUTSIDE_OWNED_WINDOW,failure.boundary());
        }
    }

    @Test void explicitValueClearKnowsOnlyTheFiniteAuditedProfileAndDoesNotMutateUnknownInput() {
        var before=CdR12bCameraArtResidency.unknown();var after=CdR12bCameraArtResidency.clear(before);
        for(var region:CdR12bCameraArtResidency.Region.values())for(int at=0;at<region.byteLength();at++){
            assertTrue(before.region(region).knownByte(at).isEmpty());assertEquals(0,after.region(region).knownByte(at).orElseThrow());
        }
        assertThrows(CdR12bCameraArtResidency.OwnershipBoundary.class,()->CdR12bCameraArtResidency.readTile(after,0xF000>>>5));
    }

    @Test void oneCompleteTileSharesEveryUnchangedRegionAndRetainsUnknownNeighbours() {
        for(var selected:CdR12bCameraArtResidency.Region.values()) {
            var before=CdR12bCameraArtResidency.unknown();byte[] tile=new byte[32];Arrays.fill(tile,(byte)0xA5);
            int dest=selected.destinationWord()+selected.byteLength()-32;
            var after=CdR12bCameraArtResidency.withTile(before,dest,CdAssetBytes.copyOf(tile));Arrays.fill(tile,(byte)0);
            assertArrayEquals(fill(0xA5),CdR12bCameraArtResidency.readTile(after,dest>>>5).copy());
            for(var region:CdR12bCameraArtResidency.Region.values()) {
                if(region!=selected)assertSame(before.region(region),after.region(region));
                for(int at=0;at<region.byteLength();at++) {
                    var value=after.region(region).knownByte(at);boolean known=region==selected && at>=selected.byteLength()-32;
                    if(known)assertEquals(0xA5,value.orElseThrow());else assertTrue(value.isEmpty());
                    assertTrue(before.region(region).knownByte(at).isEmpty());
                }
            }
        }
    }

    @Test void invalidTileSizesAlignmentAddressesAndAttributesCannotCreateKnowledge() {
        var before=CdR12bCameraArtResidency.unknown();
        for(int size:new int[]{0,31,33,64})assertThrows(IllegalArgumentException.class,()->CdR12bCameraArtResidency.withTile(before,0xA880,CdAssetBytes.copyOf(new byte[size])));
        for(int dest:new int[]{-32,1,0x10000})assertThrows(IllegalArgumentException.class,()->CdR12bCameraArtResidency.withTile(before,dest,CdAssetBytes.copyOf(fill(1))));
        assertThrows(IllegalArgumentException.class,()->CdR12bCameraArtResidency.readTile(before,0x800));
        assertThrows(NullPointerException.class,()->CdR12bCameraArtResidency.withTile(before,0xA880,null));
    }
    private static byte[] fill(int value){byte[] bytes=new byte[32];Arrays.fill(bytes,(byte)value);return bytes;}
}
