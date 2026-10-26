package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdPeriod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Static placement ABI, not spawned runtime objects or saved-object state. */
public final class CdObjectPlacements {
    /** All encoded fields are retained, including currently uninterpreted Y flag bits 12/13. */
    public record Placement(int index, int centreX, int packedY, int encodedId, int subtype,
                             int periodFlags, int subtype2) {
        public Placement {
            if (index < 0 || index >= 497 || centreX < 0 || centreX >= 0xFFFF
                    || (packedY & ~0xFFFF) != 0 || (encodedId & ~0xFF) != 0
                    || (encodedId & 0x7F) < 1 || (encodedId & 0x7F) > 64
                    || (subtype & ~0xFF) != 0 || (subtype2 & ~0xFF) != 0) {
                throw new IllegalArgumentException("Invalid native placement fields");
            }
            if ((periodFlags & ~0xE0) != 0 || periodFlags == 0) {
                throw new UnsupportedOperationException("Unverified placement period encoding");
            }
        }
        public int centreY() { return packedY & 0xFFF; }
        public int nativeYFlags() { return packedY & 0xF000; }
        public boolean flipX() { return (packedY & 0x4000) != 0; }
        public boolean flipY() { return (packedY & 0x8000) != 0; }
        public int objectId() { return encodedId & 0x7F; }
        public boolean hasSavedFlag() { return (encodedId & 0x80) != 0; }
        public boolean appliesTo(CdPeriod period) {
            return (periodFlags & periodMask(period)) != 0;
        }
    }

    private final List<Placement> records;
    private final CdAssetBytes forwardSentinel;
    private final Optional<CdAssetBytes> backwardSentinel;
    private final Optional<CdAssetBytes> nullList;

    private CdObjectPlacements(List<Placement> records, CdAssetBytes sentinel,
                                Optional<CdAssetBytes> backwardSentinel, Optional<CdAssetBytes> nullList) {
        this.records = List.copyOf(records);
        this.forwardSentinel = sentinel;
        this.backwardSentinel = backwardSentinel;
        this.nullList = nullList;
    }

    /**
     * R11 common list: X.w, Y/flags.w, ID/saved.b, subtype.b, periods.b, subtype2.b.
     * _Definitions.inc:oeX..oeSize; Object Spawner.asm:SpawnObject consumes period
     * bits 5/6/7 (Past/Present/Future), with a separate backwards/Null sentinel.
     */
    public static CdObjectPlacements decodeRecords(byte[] input) {
        CdAssetBytes data = CdAssetBytes.copyOf(input);
        if (data.length() < 8 || data.length() > 3984 || data.length() % 8 != 0) {
            throw new IllegalArgumentException("Placement extent must contain complete bounded records and sentinel");
        }
        List<Placement> records = new ArrayList<>();
        int previousX = -1;
        for (int at = 0; at < data.length(); at += 8) {
            int x = data.unsignedWord(at);
            if (x == 0xFFFF) {
                if (at + 8 != data.length()) { throw new IllegalArgumentException("Data after placement sentinel"); }
                return new CdObjectPlacements(records, data.slice(at, 8), Optional.empty(), Optional.empty());
            }
            if (x < previousX) { throw new IllegalArgumentException("Placements are not in native X order"); }
            records.add(new Placement(records.size(), x, data.unsignedWord(at + 2), data.unsignedByte(at + 4),
                    data.unsignedByte(at + 5), data.unsignedByte(at + 6), data.unsignedByte(at + 7)));
            previousX = x;
        }
        throw new IllegalArgumentException("Missing forward placement sentinel");
    }

    /** Decode ObjectLayouts through the separate six-byte Null list, preserving the common record order. */
    public static CdObjectPlacements decodeLayout(byte[] input) {
        var bytes = CdAssetBytes.copyOf(input);
        if (bytes.length() < 26 || bytes.length() > 4002) {
            throw new IllegalArgumentException("ObjectLayouts extent");
        }
        int nullOffset = bytes.unsignedWord(2);
        if (bytes.unsignedWord(0) != 12 || nullOffset != bytes.length() - 6
                || bytes.unsignedWord(4) != 0xFFFF || bytes.unsignedLong(6) != 0 || bytes.unsignedWord(10) != 0
                || bytes.unsignedWord(nullOffset) != 0xFFFF || bytes.unsignedLong(nullOffset + 2) != 0) {
            throw new IllegalArgumentException("ObjectLayouts relative pointers/backwards sentinel/Null list differ");
        }
        var decoded = decodeRecords(bytes.slice(12, nullOffset - 12).copy());
        return new CdObjectPlacements(decoded.records, decoded.forwardSentinel,
                Optional.of(bytes.slice(4, 8)), Optional.of(bytes.slice(nullOffset, 6)));
    }

    public List<Placement> records() { return records; }
    public CdAssetBytes forwardSentinel() { return forwardSentinel; }
    public Optional<CdAssetBytes> backwardSentinel() { return backwardSentinel; }
    public Optional<CdAssetBytes> nullList() { return nullList; }
    public List<Placement> forPeriod(CdPeriod period) {
        Objects.requireNonNull(period);
        return records.stream().filter(row -> row.appliesTo(period)).toList();
    }

    private static int periodMask(CdPeriod period) {
        return switch (Objects.requireNonNull(period)) {
            case PAST -> 0x20;
            case PRESENT -> 0x40;
            case GOOD_FUTURE, BAD_FUTURE -> 0x80;
        };
    }
}
