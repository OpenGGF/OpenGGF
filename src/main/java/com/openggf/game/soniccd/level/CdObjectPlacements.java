package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdProgramAssets;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Static placement ABI, not spawned runtime objects or saved-object state. */
public final class CdObjectPlacements {
    /** Finite value widths; selecting a layout does not authenticate an original source. */
    public enum Layout {
        LEGACY_497(497, 64), R31A_517(517, 60),
        ORIGINAL_461_60(461,60,true), ORIGINAL_358_60(358,60,true), ORIGINAL_72_64(72,64,true),
        ORIGINAL_640_60(640,60,true), ORIGINAL_822_60(822,60,true), ORIGINAL_79_60(79,60,true),
        ORIGINAL_TT1_360_82(360,82,true), ORIGINAL_TT2_426_82(426,82,true), ORIGINAL_TT3_32_80(32,80,true),
        ORIGINAL_PPZ1_497_64(497,64,true), ORIGINAL_PPZ2_488_64(488,64,true), ORIGINAL_PPZ3_61_64(61,64,true);
        final int rows; private final int objectIds;
        private final boolean rawMaskByte;
        Layout(int rows,int objectIds) { this(rows,objectIds,false); }
        Layout(int rows,int objectIds,boolean rawMaskByte) {
            this.rows=rows;this.objectIds=objectIds;this.rawMaskByte=rawMaskByte;
        }
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
            if (layout.rawMaskByte ? (periodFlags & ~0xFF) != 0 : (periodFlags & ~0xE0) != 0 || periodFlags == 0) {
                throw new UnsupportedOperationException("Unverified placement period encoding");
            }
        }
        public int centreY() { return packedY & 0xFFF; }
        public int nativeYFlags() { return packedY & 0xF000; }
        public boolean flipX() { return (packedY & 0x4000) != 0; }
        public boolean flipY() { return (packedY & 0x8000) != 0; }
        public int objectId() { return encodedId & 0x7F; }
        public boolean hasSavedFlag() { return (encodedId & 0x80) != 0; }
        /** CheckObjectTimeZone (src/common/object_spawn.asm:210-223): ROL.B3/AND7/BTST.
         * Low five raw bits do not select a period; zero selects none. No saved-state query occurs.
         */
        public boolean appliesTo(CdPeriod period) {
            return (periodFlags & periodMask(period)) != 0;
        }
    }

    private final List<Placement> records;
    private final List<CdAssetBytes> rawRows;
    private final CdAssetBytes forwardSentinel;
    private final Optional<CdAssetBytes> layoutHeader,backwardSentinel,nullList;
    private CdProgrammeReader programmeSource;
    private final CdR31aAssets r31aSource;
    private CdObjectPlacements(List<Placement> records,List<CdAssetBytes> rawRows,CdAssetBytes sentinel,
                               Optional<CdAssetBytes> header,Optional<CdAssetBytes> backward,
                               Optional<CdAssetBytes> nullList,CdR31aAssets r31) {
        this.records=List.copyOf(records);this.rawRows=List.copyOf(rawRows);forwardSentinel=sentinel;
        layoutHeader=header;backwardSentinel=backward;this.nullList=nullList;r31aSource=r31;
    }

    /** Legacy constructed records retain their497-row, monotone, nonzero upper-mask contract. */
    public static CdObjectPlacements decodeRecords(byte[] input) {
        var bytes=CdAssetBytes.copyOf(input);
        return parseRecords(bytes,Layout.LEGACY_497,-1,false,false,Optional.empty(),Optional.empty(),Optional.empty(),null);
    }

    /** Legacy ObjectLayouts retains its original strict header and bounded constructed contract. */
    public static CdObjectPlacements decodeLayout(byte[] input) {
        return parseLayout(CdAssetBytes.copyOf(input),Layout.LEGACY_497,-1,false,false,null);
    }

    /**
     * Public immutable DATA ingress through the same complete-disc/MMD authentication as all programme products.
     * No caller layout/identity/record descriptor is accepted. No live spawner or operation is admitted.
     */
    public static CdObjectPlacements fromProgrammeAssets(CdProgramAssets actual,CdStageKey selected)throws IOException {
        return CdProgrammeReader.open(actual,selected).placements();
    }

    static CdObjectPlacements decodeProgramme(CdProgrammeReader owner) {
        Objects.requireNonNull(owner,"actual placement DATA reader").requireRetained();
        var profile=owner.definition().placements().orElseThrow(()->new UnsupportedOperationException("No authenticated original placement profile"));
        var bytes=owner.view(profile.key());
        var result=parseLayout(bytes,profile.layout(),profile.rows(),true,true,null);
        var span=owner.definition().raw().stream().filter(s->s.key()==profile.key()).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Foreign original placement span"));
        if(bytes.length()!=span.length() || !span.sha().equals(CdR11aProfile.sha256(bytes.copy())))
            throw new IllegalArgumentException("Authenticated original placement source differs");
        result.programmeSource=owner;
        return result;
    }

    /** Exact private reader provenance; public decoded rows cannot manufacture it. */
    public boolean isRetainedOriginalProgramme() {
        if(programmeSource==null)return false;programmeSource.requireRetained();return programmeSource.placements()==this;
    }

    /** One header/row/query algorithm. Profiles are finite values, never source identity certificates. */
    private static CdObjectPlacements parseLayout(CdAssetBytes bytes,Layout layout,int exactRows,
                                                  boolean originalOrder,boolean strictForward,CdR31aAssets r31) {
        int maximum=26+layout.rows*8;
        if(bytes.length()<26 || bytes.length()>maximum || (exactRows>=0 && bytes.length()!=26+exactRows*8))
            throw new IllegalArgumentException("ObjectLayouts extent");
        // Native ADDA.W consumes signed root-relative words; all admitted roots fit positive signed words.
        int first=(short)bytes.unsignedWord(0),nullOffset=(short)bytes.unsignedWord(2);
        if(first!=12 || nullOffset!=bytes.length()-6 || bytes.unsignedWord(4)!=0xFFFF
                || bytes.unsignedLong(6)!=0 || bytes.unsignedWord(10)!=0
                || bytes.unsignedWord(nullOffset)!=0xFFFF || bytes.unsignedLong(nullOffset+2)!=0)
            throw new IllegalArgumentException("ObjectLayouts relative pointers/backwards sentinel/Null list differ");
        return parseRecords(bytes.slice(first,nullOffset-first),layout,exactRows,originalOrder,strictForward,
                Optional.of(bytes.slice(0,4)),Optional.of(bytes.slice(4,8)),Optional.of(bytes.slice(nullOffset,6)),r31);
    }

    private static CdObjectPlacements parseRecords(CdAssetBytes bytes,Layout layout,int exactRows,
                                                   boolean originalOrder,boolean strictForward,
                                                   Optional<CdAssetBytes> header,Optional<CdAssetBytes> backward,Optional<CdAssetBytes> nullList,CdR31aAssets r31) {
        if(bytes.length()<8 || bytes.length()>8*(layout.rows+1) || bytes.length()%8!=0)
            throw new IllegalArgumentException("Placement extent must contain complete bounded records and sentinel");
        var records=new ArrayList<Placement>();var rawRows=new ArrayList<CdAssetBytes>();int previousX=-1;
        for(int at=0;at<bytes.length();at+=8) {
            int x=bytes.unsignedWord(at);
            if(x==0xFFFF) {
                if(at+8!=bytes.length())throw new IllegalArgumentException("Data after placement sentinel");
                if(exactRows>=0 && records.size()!=exactRows)throw new IllegalArgumentException("Original placement record count differs");
                if(strictForward && (bytes.unsignedLong(at+2)!=0 || bytes.unsignedWord(at+6)!=0))
                    throw new IllegalArgumentException("Original placement forward sentinel differs");
                return new CdObjectPlacements(records,rawRows,bytes.slice(at,8),header,backward,nullList,r31);
            }
            if(!originalOrder && x<previousX)throw new IllegalArgumentException("Placements are not in native X order");
            records.add(new Placement(records.size(),x,bytes.unsignedWord(at+2),bytes.unsignedByte(at+4),
                    bytes.unsignedByte(at+5),bytes.unsignedByte(at+6),bytes.unsignedByte(at+7),layout));
            rawRows.add(bytes.slice(at,8));previousX=x;
        }
        throw new IllegalArgumentException("Missing forward placement sentinel");
    }

    /** Existing authentic owners now delegate their row/header work to the same parser. */
    static CdObjectPlacements decodeR13NativeOrder(CdR13FutureAssets owner) {
        Objects.requireNonNull(owner,"authenticated R13 asset owner").requireAuthenticatedLayout();
        return parseLayout(Objects.requireNonNull(owner.nativeObjectLayout()),Layout.LEGACY_497,61,true,false,null);
    }

    static CdObjectPlacements decodeR31NativeOrder(CdR31aAssets owner) {
        Objects.requireNonNull(owner,"authenticated R31A asset owner").requireAuthenticated();
        return parseLayout(Objects.requireNonNull(owner.view(CdR31aAssets.Asset.OBJECT_LAYOUTS)),Layout.R31A_517,517,false,true,owner);
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

    public List<Placement> records() { return records; }
    public List<CdAssetBytes> rawRows() { return rawRows; }
    public Optional<CdAssetBytes> layoutHeader() { return layoutHeader; }
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
