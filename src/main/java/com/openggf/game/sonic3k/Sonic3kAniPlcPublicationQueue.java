package com.openggf.game.sonic3k;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/** ROM AniPLC submissions drained by VInt's Process_DMA_Queue, never by rendering.
 * Snapshot includes actual presented bytes and pending immutable uploads so rewind
 * does not publish a future animation frame. Origin: FBZ visual completion, 2026-09-14.
 */
final class Sonic3kAniPlcPublicationQueue {
    private final List<Upload> pending = new ArrayList<>();
    private final BitSet destinations = new BitSet();

    void registerDestination(int destination, int tileCount) {
        if (destination < 0 || tileCount <= 0 || destination + tileCount > 2048)
            throw new IllegalArgumentException("Invalid AniPLC registered range");
        destinations.set(destination, destination + tileCount);
    }

    void submit(int destination, byte[] payload) {
        Upload upload = new Upload(destination, payload);
        pending.add(upload);
        registerDestination(destination, payload.length / 32);
    }

    void publish(BiConsumer<Integer, byte[]> publisher) {
        // Consume before dispatch: the same physical VBlank cannot publish twice.
        List<Upload> ready = List.copyOf(pending);
        pending.clear();
        for (Upload upload : ready) publisher.accept(upload.destination(), upload.payload());
    }

    byte[] capture(IntFunction<byte[]> presentedPattern) {
        int bytes = 8 + destinations.cardinality() * 36;
        for (Upload upload : pending) bytes += 8 + upload.bytes.length;
        ByteBuffer out = ByteBuffer.allocate(bytes);
        out.putInt(destinations.cardinality());
        for (int tile = destinations.nextSetBit(0); tile >= 0; tile = destinations.nextSetBit(tile + 1)) {
            byte[] pattern = presentedPattern.apply(tile);
            if (pattern.length != 32) throw new IllegalStateException("Invalid presented pattern");
            out.putInt(tile).put(pattern);
        }
        out.putInt(pending.size());
        for (Upload upload : pending) out.putInt(upload.destination()).putInt(upload.bytes.length).put(upload.bytes);
        return out.array();
    }

    void restore(byte[] snapshot, BiConsumer<Integer, byte[]> publisher) {
        ByteBuffer in = ByteBuffer.wrap(snapshot);
        int presentedCount = boundedCount(in.getInt(), 2048);
        List<Upload> presented = new ArrayList<>();
        for (int i = 0; i < presentedCount; i++) {
            int tile = in.getInt(); byte[] payload = new byte[32]; in.get(payload);
            presented.add(new Upload(tile, payload));
        }
        int pendingCount = boundedCount(in.getInt(), 4096);
        List<Upload> restored = new ArrayList<>();
        for (int i = 0; i < pendingCount; i++) {
            int tile = in.getInt(); int length = boundedCount(in.getInt(), 65536);
            byte[] payload = new byte[length]; in.get(payload); restored.add(new Upload(tile, payload));
        }
        if (in.hasRemaining()) throw new IllegalStateException("Trailing AniPLC publication state");
        pending.clear(); pending.addAll(restored); destinations.clear();
        for (int first = 0; first < presented.size();) {
            int end = first + 1;
            while (end < presented.size() && presented.get(end).destination() == presented.get(end - 1).destination() + 1) end++;
            int destination = presented.get(first).destination();
            byte[] contiguous = new byte[(end - first) * 32];
            for (int i = first; i < end; i++) System.arraycopy(presented.get(i).bytes, 0, contiguous, (i - first) * 32, 32);
            destinations.set(destination, destination + end - first);
            publisher.accept(destination, contiguous);
            first = end;
        }
    }

    void clear() { pending.clear(); }
    private static int boundedCount(int count, int maximum) {
        if (count < 0 || count > maximum) throw new IllegalStateException("Invalid AniPLC publication count");
        return count;
    }
    private record Upload(int destination, byte[] bytes) {
        Upload {
            if (destination < 0 || bytes.length == 0 || bytes.length % 32 != 0
                    || destination + bytes.length / 32 > 2048)
                throw new IllegalArgumentException("Invalid AniPLC publication range");
            bytes = bytes.clone();
        }
        byte[] payload() { return bytes.clone(); }
    }
}
