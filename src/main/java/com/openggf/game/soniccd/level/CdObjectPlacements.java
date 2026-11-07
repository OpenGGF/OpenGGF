package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdPeriod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Static placement ABI, not spawned runtime objects or saved-object state. */
public final class CdObjectPlacements {
    /** Finite value widths; selecting a layout does not authenticate an original source. */
    public enum Layout {
        LEGACY_497(497, 64), R31A_517(517, 60);
        private final int rows, objectIds;
        Layout(int rows, int objectIds) { this.rows = rows; this.objectIds = objectIds; }
    }

    /** All encoded fields are retained, including currently uninterpreted Y flag bits 12/13. */
    public record Placement(int index, int centreX, int packedY, int encodedId, int subtype,
                             int periodFlags, int subtype2, Layout layout) {
        /** Original seven-argument admission remains bounded to indices0..496 and IDs1..64. */
        public Placement(int index, int centreX, int packedY, int encodedId, int subtype,
                         int periodFlags, int subtype2) {
            this(index, centreX, packedY, encodedId, subtype, periodFlags, subtype2, Layout.LEGACY_497);
        }
        public Placement {
            Objects.requireNonNull(layout, "finite placement layout");
            if (index < 0 || index >= layout.rows || centreX < 0 || centreX >= 0xFFFF
                    || (packedY & ~0xFFFF) != 0 || (encodedId & ~0xFF) != 0
                    || (encodedId & 0x7F) < 1 || (encodedId & 0x7F) > layout.objectIds
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
    private final CdR31aAssets r31aSource; // Actual retained owner, never a public value certificate.

    private CdObjectPlacements(List<Placement> records, CdAssetBytes sentinel,
                                Optional<CdAssetBytes> backwardSentinel, Optional<CdAssetBytes> nullList) {
        this(records, sentinel, backwardSentinel, nullList, null);
    }

    private CdObjectPlacements(List<Placement> records, CdAssetBytes sentinel,
                                Optional<CdAssetBytes> backwardSentinel, Optional<CdAssetBytes> nullList,
                                CdR31aAssets r31aSource) {
        this.r31aSource = r31aSource;
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

    /** Finite original USA R13C/D source order, admitted only by its authentic immutable asset owner. */
    static CdObjectPlacements decodeR13NativeOrder(CdR13FutureAssets owner) {
        Objects.requireNonNull(owner, "authenticated R13 asset owner").requireAuthenticatedLayout();
        var bytes = Objects.requireNonNull(owner.nativeObjectLayout(), "authenticated R13 ObjectLayouts");
        if (bytes.length() != 514) {
            throw new IllegalArgumentException("R13 ObjectLayouts extent");
        }
        int nullOffset = bytes.unsignedWord(2);
        if (bytes.unsignedWord(0) != 12 || nullOffset != 508
                || bytes.unsignedWord(4) != 0xFFFF || bytes.unsignedLong(6) != 0 || bytes.unsignedWord(10) != 0
                || bytes.unsignedWord(nullOffset) != 0xFFFF || bytes.unsignedLong(nullOffset + 2) != 0) {
            throw new IllegalArgumentException("R13 ObjectLayouts relative pointers/backwards sentinel/Null list differ");
        }
        List<Placement> records = new ArrayList<>();
        for (int at = 12; at < nullOffset; at += 8) {
            int x = bytes.unsignedWord(at);
            if (x == 0xFFFF) {
                if (at + 8 != nullOffset) { throw new IllegalArgumentException("Data after R13 placement sentinel"); }
                if (records.size() != 61) { throw new IllegalArgumentException("R13 placement record count"); }
                return new CdObjectPlacements(records, bytes.slice(at, 8),
                        Optional.of(bytes.slice(4, 8)), Optional.of(bytes.slice(nullOffset, 6)));
            }
            // N r1/stage_object_maps_3.asm retains row55 C10 before rows56..58 BF0.
            // SpawnObjects_Forward's sequential BLS gate depends on this shipped reversal.
            records.add(new Placement(records.size(), x, bytes.unsignedWord(at + 2), bytes.unsignedByte(at + 4),
                    bytes.unsignedByte(at + 5), bytes.unsignedByte(at + 6), bytes.unsignedByte(at + 7)));
        }
        throw new IllegalArgumentException("Missing R13 forward placement sentinel");
    }

    // BEGIN R31A RETAINED PLACEMENT SOURCE 20261107
    /**
     * Actual R3 Legacy Act1 Shared includes common/object_spawn.asm and r3/stage_object_maps_1.asm.
     * StageObjectMaps/StageObjects/StageObjectsNull (stage_object_maps_1.asm:3-325) retain517 rows.
     * This finite decoder is reachable only through the original authenticated R31A asset owner.
     */
    static CdObjectPlacements decodeR31NativeOrder(CdR31aAssets owner) {
        Objects.requireNonNull(owner, "authenticated R31A asset owner").requireAuthenticated();
        var bytes = Objects.requireNonNull(owner.view(CdR31aAssets.Asset.OBJECT_LAYOUTS), "authenticated R31A ObjectLayouts");
        if (bytes.length() != 4162) { throw new IllegalArgumentException("R31A ObjectLayouts extent"); }
        int nullOffset = bytes.unsignedWord(2);
        if (bytes.unsignedWord(0) != 12 || nullOffset != 4156
                || bytes.unsignedWord(4) != 0xFFFF || bytes.unsignedLong(6) != 0 || bytes.unsignedWord(10) != 0
                || bytes.unsignedWord(nullOffset) != 0xFFFF || bytes.unsignedLong(nullOffset + 2) != 0) {
            throw new IllegalArgumentException("R31A ObjectLayouts relative pointers/backwards sentinel/Null list differ");
        }
        List<Placement> records = new ArrayList<>();
        int previousX = -1;
        for (int at = 12; at < nullOffset; at += 8) {
            int x = bytes.unsignedWord(at);
            if (x == 0xFFFF) {
                if (at + 8 != nullOffset || records.size() != 517 || bytes.unsignedLong(at + 2) != 0
                        || bytes.unsignedWord(at + 6) != 0) {
                    throw new IllegalArgumentException("R31A placement count/forward sentinel differs");
                }
                return new CdObjectPlacements(records, bytes.slice(at, 8),
                        Optional.of(bytes.slice(4, 8)), Optional.of(bytes.slice(nullOffset, 6)), owner);
            }
            if (x < previousX) { throw new IllegalArgumentException("R31A placements are not in native X order"); }
            records.add(new Placement(records.size(), x, bytes.unsignedWord(at + 2), bytes.unsignedByte(at + 4),
                    bytes.unsignedByte(at + 5), bytes.unsignedByte(at + 6), bytes.unsignedByte(at + 7), Layout.R31A_517));
            previousX = x;
        }
        throw new IllegalArgumentException("Missing R31A forward placement sentinel");
    }

    /**
     * Cross-package authenticated source relation; no raw-byte or caller-asserted identity ingress.
     * Public Placement values, even identical ones, cannot manufacture this retained owner/cache relation.
     */
    public static CdObjectPlacements fromR31aAssets(CdR31aAssets owner) {
        Objects.requireNonNull(owner, "authenticated R31A asset owner").requireAuthenticated();
        var decoded = Objects.requireNonNull(owner.placements(), "retained R31A placements");
        if (decoded.r31aSource != owner) { throw new IllegalArgumentException("Foreign R31A placement source/cache"); }
        return decoded;
    }
    // END R31A RETAINED PLACEMENT SOURCE 20261107

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
