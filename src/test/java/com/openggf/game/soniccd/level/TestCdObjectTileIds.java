package com.openggf.game.soniccd.level;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** Constructed format vectors only: no source identity, media authentication or execution authority. */
class TestCdObjectTileIds {
    private static final String BAD_TARGET = "Object tile signed relative word points outside declared data bodies";
    // Literal N Present ObjectTileIDs:7-45: 18 entries, 19 body words. Not a padded Past source.
    private static final byte[] PRESENT = HexFormat.of().parseHex(
            "002400260028002c002e0030003a0034003800360032003c003e00400042004400460048"
                    + "23a023b024092370200024284334032000004000400004090374832800000000049003e40000");

    @Test void allEighteenEntriesUseClosestGreaterSignedTargetNotDirectoryOrder() {
        int[] targets = {76,36,64,40,38,44,46,48,50,52,54,56,58,60,62,66,68,70};
        int[] ends = {80,38,66,44,40,46,48,50,52,54,56,58,60,62,64,68,70,76};
        byte[] declared = directory(80, targets);
        var bytes = CdAssetBytes.copyOf(declared);
        for (int selector = 0; selector < 18; selector++) {
            assertEquals(ends[selector], CdObjectTileIds.endOfBody(bytes, selector), "selector " + selector);
        }
        assertArrayEquals(declared, bytes.copy());
    }

    @Test void aliasesShareARealBodyAndTheMinimumFiniteIncludeHasOneWord() {
        int[] aliases = new int[18]; Arrays.fill(aliases, 36);
        var bytes = CdAssetBytes.copyOf(directory(38, aliases));
        for (int selector = 0; selector < 18; selector++) {
            assertEquals(38, CdObjectTileIds.endOfBody(bytes, selector));
        }
    }

    @Test void signedPositiveLastWordAndDeclaredEndAreDistinct() {
        int[] targets = new int[18]; Arrays.fill(targets, 36); targets[17] = 0x7FFE;
        var bytes = CdAssetBytes.copyOf(directory(0x8000, targets));
        assertEquals(0x7FFE, CdObjectTileIds.endOfBody(bytes, 0));
        assertEquals(0x8000, CdObjectTileIds.endOfBody(bytes, 17));
    }

    @Test void nullShortOddAndSelectorOutsideNativeDirectoryAreRejected() {
        assertThrows(NullPointerException.class, () -> CdObjectTileIds.endOfBody(null, 0));
        for (int extent : new int[]{0,1,2,34,35,36,37,39,73,75}) {
            assertThrows(IllegalArgumentException.class,
                    () -> CdObjectTileIds.endOfBody(CdAssetBytes.copyOf(new byte[extent]), 0), "extent " + extent);
        }
        int[] targets = new int[18]; Arrays.fill(targets, 36);
        var bytes = CdAssetBytes.copyOf(directory(38, targets));
        for (int selector : new int[]{Integer.MIN_VALUE,-1,18,19,0x7FFF,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CdObjectTileIds.endOfBody(bytes, selector));
        }
    }

    @Test void everyDirectoryEntryIsValidatedEvenWhenItIsNotSelected() {
        int[] valid = new int[18]; Arrays.fill(valid, 36);
        for (int index = 0; index < 18; index++) {
            for (int bad : new int[]{0,34,35,37,79,80,0x7FFE,0x8000,0xFFFF}) {
                byte[] malformed = directory(80, valid); word(malformed, index * 2, bad);
                var error = assertThrows(IllegalArgumentException.class,
                        () -> CdObjectTileIds.endOfBody(CdAssetBytes.copyOf(malformed), 0));
                assertEquals(BAD_TARGET, error.getMessage(), "directory " + index + " word " + bad);
            }
        }
    }

    @Test void presentDecoderRetainsExactExtentWhitelistAndTwoWordAntonBody() {
        int[] selectors = {0,1,2,4,5,6,10,11,12,15};
        int[] values = {0x23A0,0x23B0,0x2409,0x2428,0x4334,0x0409,0x0320,0x0374,0x8328,0x0490};
        assertEquals(74, PRESENT.length);
        for (int i = 0; i < selectors.length; i++) {
            assertEquals(values[i], CdR11aObjectAssets.decodeTileWord(PRESENT, selectors[i], 0));
        }
        assertEquals(0x2370, CdR11aObjectAssets.decodeTileWord(PRESENT, 2, 1));
        for (int selector : new int[]{3,7,8,9,13,14,16,17,18,0x7FFF}) {
            assertThrows(UnsupportedOperationException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(PRESENT, selector, 0));
        }
        for (int subtype : new int[]{1,2,255}) {
            assertThrows(UnsupportedOperationException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(PRESENT, 15, subtype));
        }
        byte[] shortExtent = Arrays.copyOf(PRESENT, 72);
        assertEquals("ObjectTileIDs extent", assertThrows(IllegalArgumentException.class,
                () -> CdR11aObjectAssets.decodeTileWord(shortExtent, 15, 0)).getMessage());
        assertEquals("ObjectTileIDs extent", assertThrows(IllegalArgumentException.class,
                () -> CdR11aObjectAssets.decodeTileWord(shortExtent, 3, 0)).getMessage());
        for (int[] widths : new int[][]{{-1,0},{0x8000,0},{0,-1},{0,256}}) {
            assertEquals("Object tile selector/subtype width", assertThrows(IllegalArgumentException.class,
                    () -> CdR11aObjectAssets.decodeTileWord(shortExtent, widths[0], widths[1])).getMessage());
        }
    }

    @Test void presentDecoderRetainsWholeDirectoryValidationAndSelectedBodyErrorLabel() {
        byte[] malformed = PRESENT.clone(); word(malformed, 34, 0xFFFF);
        assertEquals(BAD_TARGET, assertThrows(IllegalArgumentException.class,
                () -> CdR11aObjectAssets.decodeTileWord(malformed, 15, 0)).getMessage());
        byte[] cutAnton = PRESENT.clone(); word(cutAnton, 6, 42);
        assertEquals("Anton tile word crosses its selected body", assertThrows(IllegalArgumentException.class,
                () -> CdR11aObjectAssets.decodeTileWord(cutAnton, 2, 1)).getMessage());
    }

    private static byte[] directory(int extent, int[] targets) {
        assertEquals(18, targets.length);
        byte[] bytes = new byte[extent];
        for (int i = 0; i < targets.length; i++) word(bytes, 2 * i, targets[i]);
        return bytes;
    }
    private static void word(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8); bytes[offset + 1] = (byte) value;
    }
}
