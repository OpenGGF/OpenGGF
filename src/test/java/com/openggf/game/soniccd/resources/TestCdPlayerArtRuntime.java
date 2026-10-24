package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Constructed format/ingress cases; these do not authenticate media or execute a player. */
class TestCdPlayerArtRuntime {
    @Test
    void ingressRequiresBothCompleteWindowsAndAnExplicitNativeLastFrameByte() {
        byte[] complete = filled(0x300, 0x37);
        byte[] shortCpu = filled(0x2FF, 0x37);
        byte[] longCpu = filled(0x301, 0x37);
        byte[] shortResident = filled(0x2E0, 0x73);
        byte[] longResident = filled(0x301, 0x73);
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerArtRuntime.State(shortCpu, complete, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerArtRuntime.State(longCpu, complete, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerArtRuntime.State(complete, shortResident, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerArtRuntime.State(complete, longResident, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerArtRuntime.State(complete, complete, -1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CdPlayerArtRuntime.State(complete, complete, 256, false));
    }

    @Test
    void frameBoundsFailBeforeChangingOrServicingPendingState() {
        var assets = new Fixture().decode();
        var ingress = state(0x35, 0x79, 255, true);
        var runtime = new CdPlayerArtRuntime(assets, ingress);
        for (int invalid : new int[] {-1, 198, 255, Integer.MIN_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> runtime.serviceVInt(invalid));
            assertState(ingress, runtime.capture());
        }
        // Both admitted endpoints work; lastFrame may hold any native byte, without a forced cue.
        assertTrue(runtime.serviceVInt(197).isPresent());
        assertEquals(197, runtime.capture().lastFrame());
        assertTrue(runtime.serviceVInt(0).isEmpty());
        assertEquals(0, runtime.capture().lastFrame());
    }

    @Test
    void unchangedNonemptyFrameDoesNotForceAnInitialCueAndEmptyFrameStillBecomesLast() {
        var f = new Fixture();
        f.cue(1, 0x0005);
        f.art(5, 1, 0x62);
        var runtime = new CdPlayerArtRuntime(f.decode(), state(0x35, 0x79, 1, false));
        var ingress = runtime.capture();
        assertTrue(runtime.serviceVInt(1).isEmpty());
        assertState(ingress, runtime.capture());
        assertTrue(runtime.serviceVInt(0).isEmpty());
        assertEquals(0, runtime.capture().lastFrame());
        assertFalse(runtime.capture().dirty());
        assertBytes(runtime.capture().cpuBuffer(), 0, 0x300, 0x35);
        assertBytes(runtime.capture().residentBank(), 0, 0x300, 0x79);
        assertTrue(runtime.serviceVInt(0).isEmpty());
    }

    @Test
    void countOneAndZeroTileNibbleCopyExactlyOneTileThenRepeatedFrameDoesNothing() {
        var f = new Fixture();
        f.cue(1, 0x0005);
        f.art(5, 1, 0x62);
        var runtime = new CdPlayerArtRuntime(f.decode(), state(0x35, 0x79, 0, false));
        var dma = runtime.serviceVInt(1).orElseThrow();
        assertEquals(0xF000, dma.vramByteAddress());
        assertEquals(0x2E0, dma.bytes().length());
        assertBytes(dma.bytes(), 0, 32, 0x62);
        assertBytes(dma.bytes(), 32, 0x2E0, 0x35);
        assertBytes(runtime.capture().cpuBuffer(), 32, 0x300, 0x35);
        assertBytes(runtime.capture().residentBank(), 0x2E0, 0x300, 0x79);
        var after = runtime.capture();
        assertEquals(1, after.lastFrame());
        assertFalse(after.dirty());
        assertTrue(runtime.serviceVInt(1).isEmpty());
        assertState(after, runtime.capture());
    }

    @Test
    void piecesUseTwelveBitSourceOffsetsAndConcatenateBeforeFixedDmaPreservingBothTails() {
        var f = new Fixture();
        f.cue(1, 0x1123, 0x0002); // Two tiles at $123, then one at 2; 96 output bytes.
        f.art(0x123, 2, 0x31);
        f.art(2, 1, 0x72);
        var runtime = new CdPlayerArtRuntime(f.decode(), state(0x55, 0x6A, 0, false));
        var dma = runtime.serviceVInt(1).orElseThrow();
        assertEquals(0x2E0, dma.bytes().length());
        assertBytes(dma.bytes(), 0, 64, 0x31);
        assertBytes(dma.bytes(), 64, 96, 0x72);
        assertBytes(dma.bytes(), 96, 0x2E0, 0x55);
        assertBytes(runtime.capture().cpuBuffer(), 96, 0x300, 0x55);
        assertBytes(runtime.capture().residentBank(), 0, 64, 0x31);
        assertBytes(runtime.capture().residentBank(), 64, 96, 0x72);
        assertBytes(runtime.capture().residentBank(), 96, 0x2E0, 0x55);
        assertBytes(runtime.capture().residentBank(), 0x2E0, 0x300, 0x6A);
    }

    @Test
    void lastArtTileUsesTheExistingBoundedReadViewIncludingItsAlignmentByte() {
        var f = new Fixture();
        f.cue(1, 0x087A); // Tile 2170 starts at 69440 and ends at the real read-view bound.
        Arrays.fill(f.art, 69440, 69471, (byte) 0x5B);
        var runtime = new CdPlayerArtRuntime(f.decode(), state(0x35, 0x79, 0, false));
        var dma = runtime.serviceVInt(1).orElseThrow();
        assertBytes(dma.bytes(), 0, 31, 0x5B);
        assertEquals(0, dma.bytes().unsignedByte(31));
        assertBytes(dma.bytes(), 32, 0x2E0, 0x35);
        assertBytes(runtime.capture().cpuBuffer(), 32, 0x300, 0x35);
    }

    @Test
    void callerArraysAreDiscardedAndOverlappingIngressDoesNotAliasCpuWritesIntoResidentTail() {
        var f = new Fixture();
        // Decoder admits the full CPU capacity; this constructed case exceeds retail's $2E0 maximum.
        f.cue(1, 0xF000, 0x7000); // Sixteen + eight tiles, both from source tile zero.
        f.art(0, 16, 0x44);
        var assets = f.decode();
        byte[] shared = filled(0x300, 0x35);
        var ingress = new CdPlayerArtRuntime.State(shared, shared, 0, false);
        var runtime = new CdPlayerArtRuntime(assets, ingress);
        Arrays.fill(shared, (byte) 0xEE);
        Arrays.fill(f.mapping, (byte) 0xEE);
        Arrays.fill(f.dplc, (byte) 0xEE);
        Arrays.fill(f.art, (byte) 0xEE);
        var dma = runtime.serviceVInt(1).orElseThrow();
        assertBytes(dma.bytes(), 0, 0x2E0, 0x44);
        assertBytes(runtime.capture().cpuBuffer(), 0, 0x300, 0x44);
        assertBytes(runtime.capture().residentBank(), 0, 0x2E0, 0x44);
        assertBytes(runtime.capture().residentBank(), 0x2E0, 0x300, 0x35);
        assertBytes(ingress.cpuBuffer(), 0, 0x300, 0x35);
        assertBytes(ingress.residentBank(), 0, 0x300, 0x35);
    }

    @Test
    void unchangedFrameStillServicesPendingDirtyWithoutRecopyingItsCue() {
        var f = new Fixture();
        f.cue(1, 0x0005);
        f.art(5, 1, 0x62);
        var runtime = new CdPlayerArtRuntime(f.decode(), state(0x35, 0x79, 1, true));
        var dma = runtime.serviceVInt(1).orElseThrow();
        assertBytes(dma.bytes(), 0, 0x2E0, 0x35);
        assertBytes(runtime.capture().cpuBuffer(), 0, 0x300, 0x35);
        assertBytes(runtime.capture().residentBank(), 0, 0x2E0, 0x35);
        assertBytes(runtime.capture().residentBank(), 0x2E0, 0x300, 0x79);
        assertFalse(runtime.capture().dirty());
        assertTrue(runtime.serviceVInt(1).isEmpty());
    }

    @Test
    void emptyChangedFrameUpdatesLastBeforeServicingPreviouslyDirtyCpuBytes() {
        var runtime = new CdPlayerArtRuntime(new Fixture().decode(), state(0x35, 0x79, 9, true));
        var dma = runtime.serviceVInt(0).orElseThrow();
        assertEquals(0, runtime.capture().lastFrame());
        assertBytes(dma.bytes(), 0, 0x2E0, 0x35);
        assertBytes(runtime.capture().residentBank(), 0x2E0, 0x300, 0x79);
        assertFalse(runtime.capture().dirty());
        assertTrue(runtime.serviceVInt(0).isEmpty());
    }

    @Test
    void newNonemptyCueOverwritesPendingPrefixBeforeDmaInsteadOfSubmittingOldBufferFirst() {
        var f = new Fixture();
        f.cue(1, 0x0005);
        f.art(5, 1, 0x62);
        var runtime = new CdPlayerArtRuntime(f.decode(), state(0x35, 0x79, 0, true));
        var dma = runtime.serviceVInt(1).orElseThrow();
        assertBytes(dma.bytes(), 0, 32, 0x62);
        assertBytes(dma.bytes(), 32, 0x2E0, 0x35);
        assertFalse(runtime.capture().dirty());
        assertTrue(runtime.serviceVInt(1).isEmpty());
    }

    @Test
    void absentPlayerSkipsFrameReadAndCueButDoesNotSuppressPendingDma() {
        var runtime = new CdPlayerArtRuntime(new Fixture().decode(), state(0x35, 0x79, 9, false));
        var ingress = runtime.capture();
        assertTrue(runtime.serviceVInt(false, 255).isEmpty());
        assertState(ingress, runtime.capture());
        runtime.restore(state(0x35, 0x79, 9, true));
        var dma = runtime.serviceVInt(false, 255).orElseThrow();
        assertEquals(9, runtime.capture().lastFrame());
        assertBytes(dma.bytes(), 0, 0x2E0, 0x35);
        assertFalse(runtime.capture().dirty());
        assertTrue(runtime.serviceVInt(false, -1).isEmpty());
    }

    @Test
    void snapshotsAndRequestsHaveNoMutableViewsAndRestoreReplaysAllOwnedBytes() {
        var f = sequenceFixture();
        var runtime = new CdPlayerArtRuntime(f.decode(), state(0x35, 0x79, 0, false));
        var ingress = runtime.capture();
        var request = runtime.serviceVInt(1).orElseThrow();
        var checkpoint = runtime.capture();
        byte[] cpuCopy = checkpoint.cpuBuffer().copy();
        byte[] residentCopy = checkpoint.residentBank().copy();
        byte[] dmaCopy = request.bytes().copy();
        Arrays.fill(cpuCopy, (byte) 0xEE);
        Arrays.fill(residentCopy, (byte) 0xEE);
        Arrays.fill(dmaCopy, (byte) 0xEE);
        assertBytes(checkpoint.cpuBuffer(), 0, 32, 0x62);
        assertBytes(checkpoint.residentBank(), 0, 32, 0x62);
        assertBytes(request.bytes(), 0, 32, 0x62);
        var cpuView = checkpoint.cpuBuffer().buffer();
        var residentView = checkpoint.residentBank().buffer();
        var dmaView = request.bytes().buffer();
        assertThrows(ReadOnlyBufferException.class, () -> cpuView.put(0, (byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> residentView.put(0, (byte) 0));
        assertThrows(ReadOnlyBufferException.class, () -> dmaView.put(0, (byte) 0));
        cpuView.position(7);
        residentView.position(8);
        dmaView.position(9);
        assertEquals(0, checkpoint.cpuBuffer().buffer().position());
        assertEquals(0, checkpoint.residentBank().buffer().position());
        assertEquals(0, request.bytes().buffer().position());

        var first = service(runtime, 2, 0, 0, 1);
        var firstFinal = runtime.capture();
        assertBytes(checkpoint.cpuBuffer(), 0, 32, 0x62);
        assertBytes(ingress.cpuBuffer(), 0, 0x300, 0x35);
        runtime.restore(checkpoint); // Restoring must not issue a fresh cue or dirty the captured bank.
        assertState(checkpoint, runtime.capture());
        assertRequests(first, service(runtime, 2, 0, 0, 1));
        assertState(firstFinal, runtime.capture());

        var pending = state(0x53, 0x97, 1, true);
        runtime.restore(pending);
        var pendingRequest = runtime.serviceVInt(1).orElseThrow();
        runtime.restore(pending);
        assertState(pending, runtime.capture());
        assertArrayEquals(pendingRequest.bytes().copy(), runtime.serviceVInt(1).orElseThrow().bytes().copy());
    }

    @Test
    void chunkBoundariesAndOwnerRecreationPreserveServiceSequenceAndResidentState() {
        var assets = sequenceFixture().decode();
        var ingress = state(0x35, 0x79, 0, true);
        var whole = new CdPlayerArtRuntime(assets, ingress);
        var expected = service(whole, 0, 1, 1, 0, 2, 2, 0, 197, 1);
        var chunks = new CdPlayerArtRuntime(assets, ingress);
        var observed = new ArrayList<>(service(chunks, 0, 1));
        chunks = new CdPlayerArtRuntime(assets, chunks.capture());
        observed.addAll(service(chunks, 1, 0, 2));
        chunks = new CdPlayerArtRuntime(assets, chunks.capture());
        observed.addAll(service(chunks, 2, 0, 197, 1));
        assertRequests(expected, observed);
        assertState(whole.capture(), chunks.capture());
    }

    private static Fixture sequenceFixture() {
        var f = new Fixture();
        f.cue(1, 0x0005);
        f.cue(2, 0x1008);
        f.art(5, 1, 0x62);
        f.art(8, 2, 0x43);
        return f;
    }

    private static List<Optional<CdPlayerArtRuntime.DmaRequest>> service(CdPlayerArtRuntime runtime, int... frames) {
        var requests = new ArrayList<Optional<CdPlayerArtRuntime.DmaRequest>>();
        for (int frame : frames) { requests.add(runtime.serviceVInt(frame)); }
        return requests;
    }

    private static void assertRequests(List<Optional<CdPlayerArtRuntime.DmaRequest>> expected,
                                       List<Optional<CdPlayerArtRuntime.DmaRequest>> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).isPresent(), actual.get(i).isPresent(), "request " + i);
            if (expected.get(i).isPresent()) {
                assertEquals(expected.get(i).orElseThrow().vramByteAddress(),
                        actual.get(i).orElseThrow().vramByteAddress());
                assertArrayEquals(expected.get(i).orElseThrow().bytes().copy(),
                        actual.get(i).orElseThrow().bytes().copy());
            }
        }
    }

    private static void assertState(CdPlayerArtRuntime.State expected, CdPlayerArtRuntime.State actual) {
        assertEquals(expected.lastFrame(), actual.lastFrame());
        assertEquals(expected.dirty(), actual.dirty());
        assertArrayEquals(expected.cpuBuffer().copy(), actual.cpuBuffer().copy());
        assertArrayEquals(expected.residentBank().copy(), actual.residentBank().copy());
    }

    private static void assertBytes(CdAssetBytes bytes, int from, int to, int value) {
        assertArrayEquals(filled(to - from, value), bytes.slice(from, to - from).copy(),
                "bytes " + from + ".." + to);
    }

    private static CdPlayerArtRuntime.State state(int cpuByte, int residentByte, int lastFrame, boolean dirty) {
        return new CdPlayerArtRuntime.State(filled(0x300, cpuByte), filled(0x300, residentByte), lastFrame, dirty);
    }

    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static final class Fixture {
        final byte[] mapping = new byte[398];
        final byte[] dplc = new byte[1636];
        final byte[] art = filled(69472, 0x6D);
        int nextCue = 398;

        Fixture() {
            for (int frame = 0; frame < 198; frame++) {
                word(mapping, frame * 2, 396);
                word(dplc, frame * 2, 396); // Shared zero-count body.
            }
            art[69471] = 0; // Explicit constructed decoder input, not inferred native RAM.
        }

        void cue(int frame, int... pieces) {
            word(dplc, frame * 2, nextCue);
            word(dplc, nextCue, pieces.length);
            for (int i = 0; i < pieces.length; i++) { word(dplc, nextCue + 2 + i * 2, pieces[i]); }
            nextCue += 2 + pieces.length * 2;
        }

        void art(int tile, int count, int value) { Arrays.fill(art, tile * 32, (tile + count) * 32, (byte) value); }
        CdSonicVisualAssets decode() { return CdSonicVisualAssets.decode(mapping, dplc, art); }

        private static void word(byte[] bytes, int offset, int value) {
            bytes[offset] = (byte) (value >>> 8);
            bytes[offset + 1] = (byte) value;
        }
    }
}
