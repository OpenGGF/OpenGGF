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

    /**
     * ObjGetCeilDist, Collision Check.asm:318-338 at 1ecc5f3a. All added cases are
     * SOURCE-ONLY / UNRUN. Literal expectations come from the native WORD/BYTE operations
     * and FindLevelFloor[/2], not from another call used as an expected-value oracle.
     */
    @Test
    void ceilingBlankPreservesTheIncomingRawAngleWithoutAPreclear() {
        int[][] vectors = {{0, 0}, {0x66, 0x66}, {0x23, 0x80}, {0xFF, 0x80}};
        for (int[] vector : vectors) {
            var state = object(0x270, 0x735, 0x10, 0);
            byte[] before = state.copyBytes();
            var writes = new java.util.ArrayList<Integer>();
            var result = CdObjectCollisionCheck.objGetCeilDist(new Fixture().probes(), state, vector[0], writes::add);
            // ($735 - $10) EOR $F = $72A; two blank samples: 15 - 10 + 16.
            assertEquals(21, result.distance());
            assertEquals(vector[0], result.primaryAngle());
            assertEquals(vector[1], result.angleResult());
            assertTrue(writes.isEmpty(), "the object ceiling entry has no initial addressed store");
            assertEquals(64, before.length);
            assertArrayEquals(before, state.copyBytes());
        }
    }

    @Test
    void ceilingSubtractsTheSignedRadiusByteInsteadOfPlayerFixedTen() {
        // The first six probes are $787; the positive-radius control probes $767.
        int[][] vectors = {
                {0x798, 0x10, -8}, {0x778, 0xF0, -8}, {0x708, 0x80, -8},
                {0x807, 0x7F, -8}, {0x787, 0xFF, -8}, {0x788, 0, -8},
                {0x778, 0x10, 24}, {0x798, 0x0A, -2}
        };
        var probes = ceilingFixture(2, 0x4001, 0x22, 16).probes();
        for (int[] vector : vectors) {
            var state = object(0x270, vector[0], vector[1], 0);
            var writes = new java.util.ArrayList<Integer>();
            var result = CdObjectCollisionCheck.objGetCeilDist(probes, state, 0x66, writes::add);
            assertEquals(vector[2], result.distance());
            assertEquals(vector[2] == 24 ? 0x66 : 0x22, result.primaryAngle());
            assertEquals(vector[2] == 24 ? java.util.List.of() : java.util.List.of(0x22), writes);
        }
    }

    @Test
    void ceilingSubtractionWrapsTheYWordBeforeTheNibbleXor() {
        int[][] vectors = {{0x0008, 0x10, 24}, {0xFFFF, 0xFF, 16}, {0x0000, 0x01, 31}};
        for (int[] vector : vectors) {
            var writes = new java.util.ArrayList<Integer>();
            var result = CdObjectCollisionCheck.objGetCeilDist(new Fixture().probes(),
                    object(0x270, vector[0], vector[1], 0), 0x66, writes::add);
            // Wrapped/XOR Y values: $FFF7, $000F, $FFF0; all selected layout cells are blank.
            assertEquals(vector[2], result.distance());
            assertEquals(0x66, result.primaryAngle());
            assertTrue(writes.isEmpty());
        }
    }

    @Test
    void ceilingUsesIntegerWordsAndNeverWritesAnyOfThe64SourceBytes() {
        var state = object(0x270, 0x798, 0x10, 0)
                .moveLong(CdObjectState.LongField.X_POSITION, 0x0270BEEF)
                .moveLong(CdObjectState.LongField.Y_POSITION, 0x0798CAFE)
                .moveLong(CdObjectState.LongField.MAPPINGS, 0x89ABCDEF)
                .moveWord(TILE, 0xA55A).moveWord(X_VELOCITY, 0xFEDC).moveWord(Y_VELOCITY, 0x8123)
                .moveByte(X_RADIUS, 0x7F).moveByte(FLAGS, 0xC3).moveByte(ANGLE, 0x91)
                .moveByte(SUBTYPE, 0x70).moveByte(SUBTYPE2, 0xF8)
                .moveByte(RAW_CONTROL_2C, 0xA6).moveByte(STAND_LINK_3D, 0xFF);
        byte[] before = state.copyBytes();
        var writes = new java.util.ArrayList<Integer>();
        var result = CdObjectCollisionCheck.objGetCeilDist(
                ceilingFixture(2, 0x5801, 0x22, 6).probes(), state, 0x66, value -> {
                    assertArrayEquals(before, state.copyBytes(), "source image also stays immutable during each store");
                    writes.add(value);
                });
        assertEquals(2, result.distance());
        assertEquals(java.util.List.of(0x22, 0xDE, 0x1E, 0xE2, 0xA2), writes);
        assertEquals(0xA2, result.primaryAngle());
        assertEquals(0xA2, result.angleResult());
        assertEquals(0xBEEF, state.word(X_FRACTION));
        assertEquals(0xCAFE, state.word(Y_FRACTION));
        assertEquals(64, before.length);
        assertArrayEquals(before, state.copyBytes());
    }

    @Test
    void ceilingTestsBit14WhileTheInheritedFloorTestsBit13() {
        int[][] vectors = {{0x0001, 24, 23}, {0x2001, 24, -9}, {0x4001, -8, 23}, {0x6001, -8, -9}};
        for (int[] vector : vectors) {
            var probes = ceilingFixture(2, vector[0], 0x22, 16).probes();
            var writes = new java.util.ArrayList<Integer>();
            var ceiling = CdObjectCollisionCheck.objGetCeilDist(probes, object(0x270, 0x798, 0x10, 0), 0x66, writes::add);
            assertEquals(vector[1], ceiling.distance());
            assertEquals(vector[1] == -8 ? java.util.List.of(0x22) : java.util.List.of(), writes);
            var floor = CdObjectCollisionCheck.objGetFloorDist(probes, object(0x270, 0x778, 0x10, 0));
            assertEquals(vector[2], floor.distance());
        }
    }

    @Test
    void ceilingUsesTheObjectsOwnSpritePathBitAndNotItsOtherFlags() {
        var f = ceilingFixture(3, 0x4001, 0x22, 16).flaggedCell();
        int[][] vectors = {{0x40, -8}, {0x80, 24}, {0x04, 24}, {0xC0, -8}};
        for (int[] vector : vectors) {
            var state = object(0x270, 0x798, 0x10, vector[0]).moveByte(FLAGS, 0x40);
            byte[] before = state.copyBytes();
            var writes = new java.util.ArrayList<Integer>();
            var result = CdObjectCollisionCheck.objGetCeilDist(f.probes(), state, 0x66, writes::add);
            assertEquals(vector[1], result.distance());
            assertEquals(vector[1] == -8 ? 0x22 : 0x66, result.primaryAngle());
            assertEquals(vector[1] == -8 ? java.util.List.of(0x22) : java.util.List.of(), writes);
            assertArrayEquals(before, state.copyBytes());
        }
    }

    @Test
    void ceilingKeepsTheXWordAndNativeFlippedSampleSelection() {
        int[][] vectors = {{0x270, 0x4001, -6, 0x22}, {0x8270, 0x4001, -6, 0x22},
                {0x280, 0x4001, 26, 0x66}, {0x270, 0x4801, 26, 0xDE}};
        for (int[] vector : vectors) {
            var f = ceilingFixture(2, vector[1], 0x22, 0);
            f.heights[7 * 16] = 6;
            f.heights[7 * 16 + 15] = 3;
            var writes = new java.util.ArrayList<Integer>();
            var result = CdObjectCollisionCheck.objGetCeilDist(f.probes(),
                    object(vector[0], 0x79A, 0x10, 0).moveByte(X_RADIUS, 0x7F), 0x66, writes::add);
            // Query Y $785: metric -6 crosses the lower neighbor; -3 selects the upper neighbor.
            // X $8270 uses the same masked native layout column as $0270, with unchanged sample 0.
            assertEquals(vector[2], result.distance());
            assertEquals(vector[3], result.primaryAngle());
            assertEquals(vector[3], result.angleResult());
            assertEquals(vector[0] == 0x280 ? java.util.List.of()
                    : vector[1] == 0x4801 ? java.util.List.of(0x22, 0xDE) : java.util.List.of(0x22), writes);
        }
    }

    @Test
    void ceilingOddRawAngleChangesOnlyTheReturnedRegisterTo80() {
        var writes = new java.util.ArrayList<Integer>();
        var actual = new java.util.concurrent.atomic.AtomicInteger(0x66);
        var result = CdObjectCollisionCheck.objGetCeilDist(
                ceilingFixture(2, 0x4001, 0x23, 16).probes(), object(0x270, 0x798, 0x10, 0), 0x66,
                value -> { actual.set(value); writes.add(value); });
        assertEquals(-8, result.distance());
        assertEquals(0x23, result.primaryAngle());
        assertEquals(0x80, result.angleResult());
        assertEquals(0x23, actual.get());
        assertEquals(java.util.List.of(0x23), writes, "there is no raw-buffer normalization write");
    }

    @Test
    void ceilingForwardsEveryFlipStoreSynchronouslyWithoutReplayOrForcedZero() {
        int[] words = {0x4001, 0x4801, 0x5001, 0x5801};
        int[] distances = {24, 24, 2, 2};
        int[] angles = {0x22, 0xDE, 0x5E, 0xA2};
        var expected = java.util.List.of(java.util.List.of(0x22), java.util.List.of(0x22, 0xDE),
                java.util.List.of(0x22, 0x62, 0x9E, 0x5E),
                java.util.List.of(0x22, 0xDE, 0x1E, 0xE2, 0xA2));
        for (int i = 0; i < words.length; i++) {
            var sequence = expected.get(i);
            var writes = new java.util.ArrayList<Integer>();
            var actual = new java.util.concurrent.atomic.AtomicInteger(0x66);
            var result = CdObjectCollisionCheck.objGetCeilDist(ceilingFixture(2, words[i], 0x22, 6).probes(),
                    object(0x270, 0x798, 0x10, 0), 0x66, value -> {
                        int index = writes.size();
                        assertTrue(index < sequence.size(), "no extra clear or post-return replay");
                        assertEquals(index == 0 ? 0x66 : sequence.get(index - 1).intValue(), actual.get());
                        assertEquals(sequence.get(index).intValue(), value);
                        actual.set(value);
                        writes.add(value);
                    });
            assertEquals(distances[i], result.distance());
            assertEquals(angles[i], result.primaryAngle());
            assertEquals(angles[i], result.angleResult());
            assertEquals(angles[i], actual.get());
            assertEquals(sequence, writes);
        }
    }

    @Test
    void ceilingCannotTakeThePlus16FloorOnlyForcedZeroBranch() {
        var probes = ceilingFixture(2, 0x7001, 0x22, 6).probes();
        var writes = new java.util.ArrayList<Integer>();
        var ceiling = CdObjectCollisionCheck.objGetCeilDist(probes, object(0x270, 0x798, 0x10, 0), 0x66, writes::add);
        assertEquals(2, ceiling.distance());
        assertEquals(0x5E, ceiling.primaryAngle());
        assertEquals(java.util.List.of(0x22, 0x62, 0x9E, 0x5E), writes);
        writes.clear();
        var floor = CdObjectCollisionCheck.objGetFloorDist(probes, object(0x270, 0x778, 0x10, 0), writes::add);
        assertEquals(-9, floor.distance());
        assertEquals(0, floor.primaryAngle());
        assertEquals(java.util.List.of(0, 0x22, 0x62, 0x9E, 0x5E, 0), writes);
    }

    @Test
    void ceilingStillPublishesASameValueZeroShapeStore() {
        var writes = new java.util.ArrayList<Integer>();
        var result = CdObjectCollisionCheck.objGetCeilDist(
                ceilingFixture(2, 0x4001, 0, 16).probes(), object(0x270, 0x798, 0x10, 0), 0, writes::add);
        assertEquals(-8, result.distance());
        assertEquals(0, result.primaryAngle());
        assertEquals(0, result.angleResult());
        assertEquals(java.util.List.of(0), writes, "this zero belongs to the reached shape, not a preclear");
    }

    @Test
    void ceilingUsesTheLastActualNearbyRawStoreWhenTheFirstHeightIsZero() {
        var f = ceilingFixture(2, 0x4001, 0x22, 0);
        int at = 512 + (7 * 16 + 7) * 2;
        f.chunks[at] = 0x50; f.chunks[at + 1] = 2;
        f.ids[2] = 8; f.angles[8] = 0x33;
        Arrays.fill(f.heights, 8 * 16, 8 * 16 + 16, (byte) 4);
        var writes = new java.util.ArrayList<Integer>();
        var result = CdObjectCollisionCheck.objGetCeilDist(f.probes(), object(0x270, 0x798, 0x10, 0), 0x66, writes::add);
        // First $787 height 0 falls back to $777: height +4, offset7 => 4, then +16 => 20.
        assertEquals(20, result.distance());
        assertEquals(0x4D, result.primaryAngle());
        assertEquals(0x80, result.angleResult());
        assertEquals(java.util.List.of(0x22, 0x33, 0x73, 0x8D, 0x4D), writes);
    }

    @Test
    void ceilingFirstUnavailableWordPrecedesEveryCallbackAndUsesTheExactProbeAddress() {
        var f = new Fixture();
        f.fg[2 + 7 * 42 + 2] = (byte) 0xF9;
        var state = object(0x270, 0x798, 0x10, 0x40);
        byte[] before = state.copyBytes();
        var writes = new java.util.ArrayList<Integer>();
        var actual = new java.util.concurrent.atomic.AtomicInteger(0x66);
        var failure = assertThrows(IllegalStateException.class, () -> CdObjectCollisionCheck.objGetCeilDist(
                f.probes(), state, 0x66, value -> { actual.set(value); writes.add(value); }));
        // Loader admits chunk121; querying this object's upper path selects the absent chunk122.
        // First query $270/$787 => chunk offset $F30E, native address $21F30E.
        assertEquals(IllegalStateException.class, failure.getClass());
        assertEquals("Unavailable native collision word at $21f30e: OUTSIDE_SUPPLIED_CHUNKS", failure.getMessage());
        assertNull(failure.getCause());
        assertTrue(writes.isEmpty(), "a floor-style preclear would incorrectly run before this read");
        assertEquals(0x66, actual.get());
        assertArrayEquals(before, state.copyBytes());
    }

    @Test
    void ceilingRetainsCompletedRawStoresBeforeAnActualLaterUnavailableTerrainRead() {
        var f = ceilingFixture(81, 0x5801, 0x22, 0);
        f.fg[2 + 7 * 42 + 2] = (byte) 0xA8;
        // Declared chunk40 remains valid; the real path bit selects alternate chunk81.
        // Only that alternate's earlier neighbor at $777 has collision index1024.
        int at = 80 * 512 + (7 * 16 + 7) * 2;
        f.chunks[at] = 0x44; f.chunks[at + 1] = 0;
        var probes = f.probes();
        var state = object(0x270, 0x798, 0x10, 0x40);
        byte[] before = state.copyBytes();
        var writes = new java.util.ArrayList<Integer>();
        var actual = new java.util.concurrent.atomic.AtomicInteger(0x66);
        var failure = assertThrows(IllegalStateException.class, () -> CdObjectCollisionCheck.objGetCeilDist(
                probes, state, 0x66, value -> {
                    assertArrayEquals(before, state.copyBytes());
                    actual.set(value); writes.add(value);
                }));
        assertEquals(IllegalStateException.class, failure.getClass(), "legacy floor/7 propagates its raw exception");
        assertEquals("Collision ID requires unavailable resident bytes: 1024", failure.getMessage());
        assertNull(failure.getCause());
        assertEquals(java.util.List.of(0x22, 0xDE, 0x1E, 0xE2, 0xA2), writes);
        assertEquals(0xA2, actual.get(), "the completed callback prefix survives the later read failure");
        assertArrayEquals(before, state.copyBytes());
    }

    @Test
    void ceilingPreservesCallerExceptionIdentityAtEveryReachedFlipStore() {
        var expected = java.util.List.of(0x22, 0x62, 0x9E, 0x5E);
        for (int stop = 1; stop <= expected.size(); stop++) {
            int stopAt = stop;
            var state = object(0x270, 0x798, 0x10, 0);
            byte[] before = state.copyBytes();
            var sentinel = new IllegalStateException("actual caller angle-port failure " + stopAt);
            var writes = new java.util.ArrayList<Integer>();
            var actual = new java.util.concurrent.atomic.AtomicInteger(0x66);
            var failure = assertThrows(IllegalStateException.class, () -> CdObjectCollisionCheck.objGetCeilDist(
                    ceilingFixture(2, 0x5001, 0x22, 6).probes(), state, 0x66, value -> {
                        actual.set(value); writes.add(value);
                        if (writes.size() == stopAt) throw sentinel;
                    }));
            assertSame(sentinel, failure, "no marker reclassification, wrapping, or rollback");
            assertEquals(expected.subList(0, stopAt), writes);
            assertEquals(expected.get(stopAt - 1).intValue(), actual.get());
            assertArrayEquals(before, state.copyBytes());
        }
        var sentinel = new AssertionError("actual caller Error after second raw store");
        var writes = new java.util.ArrayList<Integer>();
        assertSame(sentinel, assertThrows(AssertionError.class, () -> CdObjectCollisionCheck.objGetCeilDist(
                ceilingFixture(2, 0x5001, 0x22, 6).probes(), object(0x270, 0x798, 0x10, 0), 0x66, value -> {
                    writes.add(value);
                    if (writes.size() == 2) throw sentinel;
                })));
        assertEquals(java.util.List.of(0x22, 0x62), writes);
    }

    @Test
    void ceilingPreservesAnOriginalTerrainExceptionCrossingTheActualCallbackPort() {
        var f = new Fixture();
        f.fg[2 + 7 * 42 + 2] = (byte) 0xF9;
        var unavailable = f.probes();
        var original = new IllegalStateException[1];
        var writes = new java.util.ArrayList<Integer>();
        var failure = assertThrows(IllegalStateException.class, () -> CdObjectCollisionCheck.objGetCeilDist(
                ceilingFixture(2, 0x5001, 0x22, 6).probes(), object(0x270, 0x798, 0x10, 0), 0x66, value -> {
                    writes.add(value);
                    if (writes.size() == 2) {
                        try {
                            unavailable.floor(0x270, 0x787, 14, 0x1000, -16, 0x40, ignored -> fail("unreached store"));
                        } catch (IllegalStateException actualTerrainFailure) {
                            original[0] = actualTerrainFailure;
                            throw actualTerrainFailure;
                        }
                        fail("the declared alternate path must require an unavailable native word");
                    }
                }));
        assertNotNull(original[0]);
        assertSame(original[0], failure);
        assertEquals("Unavailable native collision word at $21f30e: OUTSIDE_SUPPLIED_CHUNKS", failure.getMessage());
        assertEquals(java.util.List.of(0x22, 0x62), writes);
    }

    @Test
    void ceilingRejectsNullInputsAndNonNativeWidthsWithoutPublishing() {
        var f = new Fixture();
        f.fg[2 + 7 * 42 + 2] = (byte) 0xF9;
        var probes = f.probes();
        var state = object(0x270, 0x798, 0x10, 0x40);
        var writes = new java.util.ArrayList<Integer>();
        assertThrows(NullPointerException.class, () -> CdObjectCollisionCheck.objGetCeilDist(null, state, 0x66, writes::add));
        assertThrows(NullPointerException.class, () -> CdObjectCollisionCheck.objGetCeilDist(probes, null, 0x66, writes::add));
        assertThrows(NullPointerException.class, () -> CdObjectCollisionCheck.objGetCeilDist(probes, state, 0x66, null));
        for (int angle : new int[]{-1, 0x100, 0x10000}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectCollisionCheck.objGetCeilDist(probes, state, angle, writes::add));
        }
        assertTrue(writes.isEmpty());
        for (int distance : new int[]{-0x8001, 0x8000}) {
            assertThrows(IllegalArgumentException.class, () -> new CdObjectCollisionCheck.CeilingDistance(distance, 0, 0));
        }
        for (int angle : new int[]{-1, 0x100}) {
            assertThrows(IllegalArgumentException.class, () -> new CdObjectCollisionCheck.CeilingDistance(0, angle, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdObjectCollisionCheck.CeilingDistance(0, 0, angle));
        }
        var low = new CdObjectCollisionCheck.CeilingDistance(-0x8000, 0xFF, 0xFE);
        var high = new CdObjectCollisionCheck.CeilingDistance(0x7FFF, 0, 0x80);
        assertEquals(-0x8000, low.distance()); assertEquals(0xFF, low.primaryAngle()); assertEquals(0xFE, low.angleResult());
        assertEquals(0x7FFF, high.distance()); assertEquals(0, high.primaryAngle()); assertEquals(0x80, high.angleResult());
    }

    /** Native-format constructed terrain, using the inherited fixture and decoder unchanged. */
    private static Fixture ceilingFixture(int chunk, int word, int angle, int height) {
        var f = new Fixture().floor(chunk, angle);
        int at = (chunk - 1) * 512 + (8 * 16 + 7) * 2;
        f.chunks[at] = (byte) (word >>> 8); f.chunks[at + 1] = (byte) word;
        Arrays.fill(f.heights, 7 * 16, 7 * 16 + 16, (byte) height);
        return f;
    }
}
