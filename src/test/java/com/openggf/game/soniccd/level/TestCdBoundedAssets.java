package com.openggf.game.soniccd.level;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TestCdBoundedAssets {
    @Test
    void bytesOwnTheirStorageAndReturnIndependentReadOnlyViews() {
        byte[] source = {1, 2, (byte) 0xFE, (byte) 0xDC};
        var bytes = CdAssetBytes.copyOf(source);
        source[0] = 99;
        byte[] copy = bytes.copy();
        copy[1] = 99;
        var view = bytes.buffer();
        view.position(3);
        assertEquals(0, bytes.buffer().position());
        assertEquals(1, bytes.unsignedByte(0));
        assertEquals(2, bytes.unsignedByte(1));
        assertEquals(-2, bytes.signedByte(2));
        assertEquals(0xFEDC, bytes.unsignedWord(2));
        assertThrows(ReadOnlyBufferException.class, () -> bytes.buffer().put(0, (byte) 0));
        assertEquals(0xFEDC, bytes.slice(2, 2).unsignedWord(0));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.slice(-1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.slice(1, Integer.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.unsignedWord(3));
    }

    @Test
    void realNemesisReaderDecodesTheRunAndReportsItsActualLookaheadCursor() throws IOException {
        // One tile of A nibbles; repeat eight, one-bit code 0, eight runs.
        byte[] input = {0, 1, (byte) 0x8A, 0x71, 0, (byte) 0xFF, 0, 0, 77};
        var decoded = CdNemesisAsset.decode(input, 32, 32);
        assertEquals(8, decoded.consumedInputBytes());
        byte[] expected = new byte[32];
        Arrays.fill(expected, (byte) 0xAA);
        assertArrayEquals(expected, decoded.bytes().copy());
        input[6] = (byte) 0xFF;
        assertArrayEquals(expected, decoded.bytes().copy());
    }

    @Test
    void xorModeUsesThePreviousFourByteRow() throws IOException {
        byte[] input = {(byte) 0x80, 1, (byte) 0x8A, 0x71, 0, (byte) 0xFF, 0, 0};
        var decoded = CdNemesisAsset.decode(input, 32, 32);
        assertEquals(0xAA, decoded.bytes().unsignedByte(0));
        assertEquals(0, decoded.bytes().unsignedByte(4));
        assertEquals(0xAA, decoded.bytes().unsignedByte(8));
        assertEquals(0, decoded.bytes().unsignedByte(28));
    }

    @Test
    void declarationsCannotAllocateBeyondTheReviewedOutputLimit() {
        byte[] huge = {0x7F, (byte) 0xFF, (byte) 0xFF};
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(huge, 32, 32));
        assertThrows(IllegalArgumentException.class, () -> CdNemesisAsset.decode(huge, 32, 31));
        assertThrows(IllegalArgumentException.class, () -> CdNemesisAsset.decode(huge, 0x10020, 0x10020));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(new byte[]{0}, 32, 32));
    }

    @Test
    void truncationInvalidCodesAndRunsPastTheOutputAreFailures() {
        byte[] noLookahead = {0, 1, (byte) 0x8A, 0x71, 0, (byte) 0xFF, 0};
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(noLookahead, 32, 32));
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(new byte[]{0, 1, (byte) 0xFF, 0}, 32, 32));
        byte[] sevenAtATime = {0, 1, (byte) 0x8A, 0x61, 0, (byte) 0xFF, 0, 0, 0};
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(sevenAtATime, 32, 32));
    }
}
