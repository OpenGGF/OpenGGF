package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the port; all cases UNRUN by the external author. Constructed terrain goes
 * through the production CdR11aTerrain/CdPlayerTerrain owners; no floor value is hard-coded here.
 * Source: Collision Check.asm:157-179 (ObjGetFloorDist) at 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 */
class TestCdObjectCollisionCheck {
    @Test
    void blankTerrainKeepsTheNearbyDefaultDistanceAndWritesNoAngle() {
        var f = new Fixture();
        var result = CdObjectCollisionCheck.objGetFloorDist(f.probes(), object(0x270, 0x735, 0x10, 0));
        // Probe at $745: blank block, blank block below: (15 - 5) + 16.
        assertEquals(26, result.distance());
        assertEquals(0, result.primaryAngle(), "MOVE.B #0,(a4) stands when FindLevelFloor writes nothing");
        assertEquals(0, result.angleResult());
    }

    @Test
    void solidFloorBelowGivesNegativeDistanceAndPublishesAnEvenAngle() {
        var f = new Fixture().floor(2, 0x22);
        var result = CdObjectCollisionCheck.objGetFloorDist(f.probes(), object(0x270, 0x778, 0x10, 0));
        // Probe at $788 is nine pixels below the floor top row $780: distance $77F - $788.
        assertEquals(-9, result.distance());
        assertEquals(0x22, result.primaryAngle());
        assertEquals(0x22, result.angleResult());
    }

    @Test
    void oddAngleStaysInTheGlobalButReturnsZeroInD3() {
        var f = new Fixture().floor(2, 0x23);
        var result = CdObjectCollisionCheck.objGetFloorDist(f.probes(), object(0x270, 0x778, 0x10, 0));
        assertEquals(-9, result.distance());
        assertEquals(0x23, result.primaryAngle());
        assertEquals(0, result.angleResult(), "BTST #0 / MOVE.B #0,d3");
    }

    @Test
    void yRadiusByteIsSignExtendedBeforeTheWordAdd() {
        var f = new Fixture().floor(2, 0x22);
        var probes = f.probes();
        // $F0 is -16: Y $798 probes $788, inside the floor.
        assertEquals(-9, CdObjectCollisionCheck.objGetFloorDist(probes, object(0x270, 0x798, 0xF0, 0)).distance());
        // An unsigned $F0 would probe $888; the positive radius $10 probes $7A8 below the floor instead.
        assertEquals(23, CdObjectCollisionCheck.objGetFloorDist(probes, object(0x270, 0x798, 0x10, 0)).distance());
    }

    @Test
    void probeCoordinatesWrapAsNativeWords() {
        var f = new Fixture().floor(2, 0x22);
        var result = CdObjectCollisionCheck.objGetFloorDist(f.probes(), object(0x270, 0xFFF8, 0x10, 0));
        // $FFF8 + $10 wraps to $0008: layout row 0 is blank, so (15 - 8) + 16.
        assertEquals(23, result.distance());
        assertEquals(0, result.primaryAngle());
    }

    @Test
    void theObjectsOwnPathLayerBitSelectsTheFlaggedChunk() {
        var f = new Fixture().flaggedCell().floor(3, 0x22);
        var probes = f.probes();
        var upper = CdObjectCollisionCheck.objGetFloorDist(probes, object(0x270, 0x778, 0x10, 0x40));
        assertEquals(-9, upper.distance(), "bit 6 swaps the flagged chunk 2 to chunk 3, which has the floor");
        assertEquals(0x22, upper.primaryAngle());
        var lower = CdObjectCollisionCheck.objGetFloorDist(probes, object(0x270, 0x778, 0x10, 0x80));
        assertEquals(23, lower.distance(), "bit 7 is not a path layer; chunk 2 stays blank");
        assertEquals(0, lower.primaryAngle());
    }

    @Test
    void nullInputsAndNonNativeWidthsAreRejected() {
        var f = new Fixture();
        var state = object(0x270, 0x735, 0x10, 0);
        assertThrows(NullPointerException.class, () -> CdObjectCollisionCheck.objGetFloorDist(null, state));
        assertThrows(NullPointerException.class, () -> CdObjectCollisionCheck.objGetFloorDist(f.probes(), null));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectCollisionCheck.FloorDistance(0x8000, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectCollisionCheck.FloorDistance(0, 0x100, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdObjectCollisionCheck.FloorDistance(0, 0, -1));
        var kept = new CdObjectCollisionCheck.FloorDistance(-0x8000, 0xFF, 0xFE);
        assertEquals(-0x8000, kept.distance());
    }

    private static CdObjectState object(int x, int y, int yRadius, int spriteFlags) {
        return new CdObjectPool().state(32).moveWord(X, x).moveWord(Y, y)
                .moveByte(Y_RADIUS, yRadius).moveByte(SPRITE_FLAGS, spriteFlags).moveByte(ID, 0x3D);
    }

    /** World X $200..$2FF, Y $700..$7FF is layout cell (2,7); X $270 is block column 7 of that chunk. */
    @Test void observerPreclearOccursForBlankAndSameValueNativeZero() {
        var blank=new Fixture();var writes=new java.util.ArrayList<Integer>();
        var noFloor=CdObjectCollisionCheck.objGetFloorDist(blank.probes(),object(0x270,0x735,0x10,0),writes::add);
        assertEquals(26,noFloor.distance());assertEquals(0,noFloor.primaryAngle());assertEquals(java.util.List.of(0),writes);
        var f=new Fixture().floor(2,0);writes.clear();
        var solid=CdObjectCollisionCheck.objGetFloorDist(f.probes(),object(0x270,0x778,0x10,0),writes::add);
        assertEquals(-9,solid.distance());assertEquals(java.util.List.of(0,0),writes);
    }

    @Test void observerPreservesOddGlobalWhileOnlyD3ReturnsZero() {
        var f=new Fixture().floor(2,0x23);var writes=new java.util.ArrayList<Integer>();
        var result=CdObjectCollisionCheck.objGetFloorDist(f.probes(),object(0x270,0x778,0x10,0),writes::add);
        assertEquals(-9,result.distance());assertEquals(0x23,result.primaryAngle());assertEquals(0,result.angleResult());
        assertEquals(java.util.List.of(0,0x23),writes);
    }

    @Test void observerPreclearSurvivesFirstUnavailableReadWithoutObjectWrites() {
        var f=new Fixture();f.fg[2+7*42+2]=(byte)0xF9;var probes=f.probes();var state=object(0x270,0x778,0x10,0x40);
        var before=state.copyBytes();var writes=new java.util.ArrayList<Integer>();
        var failure=assertThrows(IllegalStateException.class,()->CdObjectCollisionCheck.objGetFloorDist(probes,state,writes::add));
        assertTrue(failure.getMessage().contains("OUTSIDE_SUPPLIED_CHUNKS"));assertEquals(java.util.List.of(0),writes);assertArrayEquals(before,state.copyBytes());
    }

    @Test void observerPreservesFirstNativeAngleBeforeUnavailableFallback() {
        var f=new Fixture().floor(81,0x22);f.fg[2+7*42+2]=(byte)0xA8;
        // Actor path bit6 selects81, while the declared foreground40 remains loader-valid.
        int at=80*512+(7*16+7)*2;f.chunks[at]=0x24;f.chunks[at+1]=0;
        var probes=f.probes();var state=object(0x270,0x778,0x10,0x40);var before=state.copyBytes();var writes=new java.util.ArrayList<Integer>();
        var failure=assertThrows(IllegalStateException.class,()->CdObjectCollisionCheck.objGetFloorDist(probes,state,writes::add));
        assertTrue(failure.getMessage().contains("unavailable resident bytes: 1024"));assertEquals(java.util.List.of(0,0x22),writes);assertArrayEquals(before,state.copyBytes());
    }

    @Test void observerFailureAtPreclearPreventsAllTerrainReads() {
        var f=new Fixture();f.fg[2+7*42+2]=(byte)0xF9;var probes=f.probes();var state=object(0x270,0x778,0x10,0x40);
        var sentinel=new IllegalArgumentException("actual prezero writer failure");var writes=new java.util.ArrayList<Integer>();
        assertSame(sentinel,assertThrows(IllegalArgumentException.class,()->CdObjectCollisionCheck.objGetFloorDist(probes,state,value->{writes.add(value);throw sentinel;})));
        assertEquals(java.util.List.of(0),writes);
    }

    @Test void observerReceivesEveryFlipStoreIncludingForcedZero() {
        int[] flips={0,0x800,0x1000,0x1800};int[] finalAngles={0x22,0xDE,0,0};
        var expected=java.util.List.of(java.util.List.of(0,0x22),java.util.List.of(0,0x22,0xDE),
                java.util.List.of(0,0x22,0x62,0x9E,0x5E,0),java.util.List.of(0,0x22,0xDE,0x1E,0xE2,0xA2,0));
        for(int i=0;i<4;i++) {
            var f=new Fixture().floor(2,0x22);int at=512+(8*16+7)*2;int word=0x2001|flips[i];f.chunks[at]=(byte)(word>>>8);f.chunks[at+1]=(byte)word;
            var writes=new java.util.ArrayList<Integer>();var result=CdObjectCollisionCheck.objGetFloorDist(f.probes(),object(0x270,0x778,0x10,0),writes::add);
            assertEquals(-9,result.distance());assertEquals(finalAngles[i],result.primaryAngle());assertEquals(finalAngles[i],result.angleResult());assertEquals(expected.get(i),writes);
        }
    }

    @Test void observerSequenceKeepsSuccessfulProbeThenCurrentPrezeroOnFailure() {
        var f=new Fixture().floor(2,0x22);var writes=new java.util.ArrayList<Integer>();var actual=new java.util.concurrent.atomic.AtomicInteger(0x66);
        java.util.function.IntConsumer writer=value->{actual.set(value);writes.add(value);};var state=object(0x270,0x778,0x10,0x40);
        CdObjectCollisionCheck.objGetFloorDist(f.probes(),state,writer);assertEquals(0x22,actual.get());
        f.fg[2+7*42+2]=(byte)0xF9;var unavailable=f.probes();
        assertThrows(IllegalStateException.class,()->CdObjectCollisionCheck.objGetFloorDist(unavailable,state,writer));
        assertEquals(0,actual.get());assertEquals(java.util.List.of(0,0x22,0),writes);
    }

    @Test void observerNullAdmissionDoesNotPublish() {
        var f=new Fixture();var probes=f.probes();var state=object(0x270,0x735,0x10,0);var writes=new java.util.ArrayList<Integer>();
        assertThrows(NullPointerException.class,()->CdObjectCollisionCheck.objGetFloorDist(null,state,writes::add));
        assertThrows(NullPointerException.class,()->CdObjectCollisionCheck.objGetFloorDist(probes,null,writes::add));
        assertThrows(NullPointerException.class,()->CdObjectCollisionCheck.objGetFloorDist(probes,state,null));assertTrue(writes.isEmpty());
    }

    private static final class Fixture {
        final byte[] chunks = new byte[61952];
        final byte[] fg = new byte[338];
        final byte[] bg = new byte[50];
        final byte[] ids = new byte[900];
        final byte[] angles = new byte[256];
        final byte[] heights = new byte[4096];
        final byte[] widths = new byte[4096];

        Fixture() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7;
            fg[2 + 7 * 42 + 2] = 2;
        }
        Fixture flaggedCell() { fg[2 + 7 * 42 + 2] = (byte) 0x82; return this; }
        /** Full-height solid block at column 7, row 8 of the given chunk: its top is world Y $780. */
        Fixture floor(int chunk, int angle) {
            int at = (chunk - 1) * 512 + (8 * 16 + 7) * 2;
            chunks[at] = 0x20; chunks[at + 1] = 0x01;
            ids[1] = 7; angles[7] = (byte) angle;
            Arrays.fill(heights, 7 * 16, 7 * 16 + 16, (byte) 16);
            return this;
        }
        CdPlayerTerrain probes() {
            return new CdPlayerTerrain(CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg,
                    ids, angles, heights, widths));
        }
    }
}
