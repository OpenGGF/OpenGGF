package com.openggf.game.soniccd.objects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * CD-private CPU DrawObject/DrawObjects owner; no object dispatch, renderer or V-int latch.
 * Source: Level/Object Functions.asm:137-196,243-365,382-567 at
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff (shipped behavior).
 * Bound ports and the caller own source/world authentication and native loop entitlement.
 */
public final class CdObjectDrawQueue implements AutoCloseable {
    public static final int BUCKET_COUNT = 8;
    public static final int POINTERS_PER_BUCKET = 63;
    public static final int SAT_PIECE_CAPACITY = 80;
    public static final int SAT_BYTE_SIZE = 640;

    /** Physical pointer identity only; never an allocation generation or a dynamic-pool identity. */
    public record CdSlotRef(int wholeIndex) {
        public CdSlotRef { range(wholeIndex, 0, 127, "physical slot index"); }
        public int nativePointerWord() { return 0xD000 + wholeIndex * 0x40; }
        public int nativeAddress24() { return 0xFF0000 | nativePointerWord(); }
    }

    public record CameraWords(int xWord, int yWord) {
        public CameraWords {
            word(xWord, "camera X");
            word(yWord, "camera Y");
        }
    }

    public record DrawCameras(CameraWords foreground, CameraWords background, CameraWords background3) {
        public DrawCameras {
            Objects.requireNonNull(foreground, "foreground camera");
            Objects.requireNonNull(background, "background camera");
            Objects.requireNonNull(background3, "background3 camera");
        }
    }

    /** Exact DrawObject call cut. Only ref is retained after this synchronous call. */
    public record DrawCallCut(CdSlotRef ref, int spriteFlagsByte, int xWord, int yWord,
                              int yRadiusByte, int widthByte, int priorityByte) {
        public DrawCallCut {
            Objects.requireNonNull(ref, "slot reference");
            octet(spriteFlagsByte, "sprite flags");
            word(xWord, "call X");
            word(yWord, "call Y");
            octet(yRadiusByte, "Y radius");
            octet(widthByte, "width");
            octet(priorityByte, "priority");
        }
    }

    /**
     * A single live dereference, including the WORD oYScr alias at $0A, not world Y at $0C.
     * mappingAddressBits is an unmodified native long's bits; this port does not narrow it to 24 bits.
     */
    public record LiveDrawSlot(int idByte, int spriteFlagsByte, int xWord, int yWord,
                               int screenYWord, int mappingAddressBits, int mapFrameByte,
                               int baseTileWord) {
        public LiveDrawSlot {
            octet(idByte, "object ID");
            octet(spriteFlagsByte, "sprite flags");
            word(xWord, "live X");
            word(yWord, "live Y");
            word(screenYWord, "screen Y word at $0A");
            octet(mapFrameByte, "mapping frame");
            word(baseTileWord, "base tile");
        }
    }

    /**
     * Bound authoritative player/pool adapter. Both methods must reject unowned refs (1..31 here).
     * read must return the CURRENT occupant, including ID zero; null is a failed ownership lookup.
     * writeOnScreenBit may change ONLY bit 7 of the currently addressed slot's sprite flags.
     * These synchronous ports must not reenter this queue or run other object phases.
     */
    public interface CdDrawSlots {
        LiveDrawSlot read(CdSlotRef ref);
        void writeOnScreenBit(CdSlotRef ref, boolean value);
    }

    /**
     * Resolve an authenticated, bounded semantic view by pointer/mode/full unsigned frame, never ID.
     * Counted mode selects a signed relative WORD at root + 2*mapFrameByte; static mode bypasses
     * that table and its count byte. The production resolver owns those source bindings/bounds.
     * This pure input-port interface itself is NOT evidence of authenticated media or residency.
     */
    @FunctionalInterface
    public interface CdDrawMappings {
        MappingBody resolve(int addressBits, boolean staticPiece, int mapFrameByte);
    }

    /** Bounded semantic five-byte piece; size high bits are outside this finite admission. */
    public record RawPiece(int y, int sizeByte, int rawTileWord, int x) {
        public RawPiece {
            range(y, -128, 127, "signed piece Y byte");
            range(sizeByte, 0, 15, "admitted piece size nibble");
            word(rawTileWord, "piece tile word");
            range(x, -128, 127, "signed piece X byte");
        }
    }

    public sealed interface MappingBody permits FrameMapping, StaticMapping {
        List<RawPiece> pieces();
    }

    /**
     * Counted frame. Native SUBQ.B/BMI returns empty for count 0 AND 129..255
     * (Object Functions.asm:.DrawSprite,333-340). This finite component deliberately REJECTS
     * 129..255 as unsupported asset data, matching the existing bounded visual decoder policy.
     * That rejection is not a claim that the native branch draws or faults on those bytes.
     */
    public record FrameMapping(int countByte, List<RawPiece> pieces) implements MappingBody {
        public FrameMapping {
            octet(countByte, "mapping count");
            if (countByte > 128) {
                throw new UnsupportedOperationException("Mapping count " + countByte
                        + " takes the native empty branch; unsupported by bounded mapping policy");
            }
            Objects.requireNonNull(pieces, "mapping pieces");
            if (pieces.size() != countByte) {
                throw new IllegalArgumentException("Mapping count and bounded piece view disagree");
            }
            pieces = List.copyOf(pieces);
        }
    }

    /** One raw piece at oMap, with NO table/count-byte header (flag bit 5). */
    public record StaticMapping(RawPiece piece) implements MappingBody {
        public StaticMapping { Objects.requireNonNull(piece, "static mapping piece"); }
        @Override public List<RawPiece> pieces() { return List.of(piece); }
    }

    public enum EnqueueResult { QUEUED, CULLED, BUCKET_FULL }
    public enum Phase { CLOSED_CUT, COLLECTING, DRAINING, FAILED, CLOSED }

    /** Diagnostic origin of an emitted piece; never an input to gameplay or SAT arithmetic. */
    public record Provenance(int queueBucket, int queueOrdinal, CdSlotRef slotRef, int mappingPieceOrdinal) {
        public Provenance {
            range(queueBucket, 0, BUCKET_COUNT - 1, "queue bucket");
            range(queueOrdinal, 0, POINTERS_PER_BUCKET - 1, "queue ordinal");
            Objects.requireNonNull(slotRef, "provenance slot");
            range(mappingPieceOrdinal, 0, 127, "mapping piece ordinal");
        }
    }

    /** Exact immutable $280-byte CPU image, including the retained unused raw tail. */
    public static final class CpuSat {
        private final byte[] bytes;
        private final int spriteCount;
        private final List<Provenance> provenance;

        private CpuSat(byte[] bytes, int spriteCount, List<Provenance> provenance) {
            if (bytes.length != SAT_BYTE_SIZE) { throw new IllegalArgumentException("SAT must be 640 bytes"); }
            range(spriteCount, 0, SAT_PIECE_CAPACITY, "SAT piece count");
            this.bytes = bytes.clone();
            this.spriteCount = spriteCount;
            this.provenance = List.copyOf(provenance);
            if (this.provenance.size() != spriteCount) {
                throw new IllegalArgumentException("SAT count and provenance disagree");
            }
        }

        public byte[] bytes() { return bytes.clone(); }
        public int spriteCount() { return spriteCount; }
        public List<Provenance> provenance() { return provenance; }
    }

    /** Closed CPU partition only. Slot bits, cameras and displayed-next-VInt picture have other owners. */
    public static final class Snapshot {
        private final Object owner;
        private final CpuSat cpuSat;
        private Snapshot(Object owner, CpuSat cpuSat) {
            this.owner = owner;
            this.cpuSat = cpuSat;
        }
    }

    private final Object owner = new Object();
    private final CdDrawSlots slots;
    private final CdDrawMappings mappings;
    // Pointer lists contain ONLY physical refs. Counts retain the source's POINTER-BYTE units.
    private final CdSlotRef[][] pointers = new CdSlotRef[BUCKET_COUNT][POINTERS_PER_BUCKET];
    private final int[] pointerByteCounts = new int[BUCKET_COUNT];
    private CpuSat cpuSat;
    private Phase phase = Phase.CLOSED_CUT;
    private boolean operating;

    /**
     * Caller explicitly supplies the initial raw buffer. No native ingress/clear is inferred.
     * Its initial logical count/provenance are declared empty; bytes are preserved exactly until build.
     * The root's composite owner must separately declare the initial displayed picture and source ingress.
     */
    public CdObjectDrawQueue(CdDrawSlots slots, CdDrawMappings mappings, byte[] initialSatBytes) {
        this.slots = Objects.requireNonNull(slots, "bound live slots");
        this.mappings = Objects.requireNonNull(mappings, "bound mappings");
        cpuSat = new CpuSat(Objects.requireNonNull(initialSatBytes, "explicit initial SAT"), 0, List.of());
    }

    public Phase phase() { return phase; }

    /** Caller owns ordinary-loop, spawn/player/dynamic ordering and any pause/lag entitlement. */
    public void beginPass() {
        requirePhase(Phase.CLOSED_CUT);
        for (int count : pointerByteCounts) {
            if (count != 0) {
                phase = Phase.FAILED;
                throw new IllegalStateException("Undrained CD draw queue cannot begin another pass");
            }
        }
        phase = Phase.COLLECTING;
    }

    /** DrawOtherObject is deliberately not exposed: it has different clear/cull semantics. */
    public EnqueueResult drawObject(DrawCallCut cut, CameraWords preScrollForeground) {
        requirePhase(Phase.COLLECTING);
        Objects.requireNonNull(cut, "DrawObject call cut");
        Objects.requireNonNull(preScrollForeground, "pre-scroll foreground");
        requireOwned(cut.ref()); // Finite admission, not a restriction of the native pointer format.
        operating = true;
        try {
            // DrawObject:138: entry clear precedes culling/capacity, with NO ID test.
            slots.writeOnScreenBit(cut.ref(), false);
            requireOperatingPhase(Phase.COLLECTING);
            if ((cut.spriteFlagsByte() & 12) != 0 && culled(cut, preScrollForeground)) {
                return EnqueueResult.CULLED;
            }
            // DrawObject:184-187 reads priority+width as one word; only priority & 7 survives.
            int bucket = cut.priorityByte() & 7;
            int byteCount = pointerByteCounts[bucket];
            if (byteCount >= 0x7E) { return EnqueueResult.BUCKET_FULL; }
            pointers[bucket][byteCount >>> 1] = cut.ref();
            pointerByteCounts[bucket] = byteCount + 2;
            return EnqueueResult.QUEUED;
        } catch (RuntimeException | Error failure) {
            phase = Phase.FAILED;
            throw failure;
        } finally {
            operating = false;
        }
    }

    /**
     * One native DrawObjects after camera. Root must require completed dynamic dispatch before calling.
     * Every occurrence rereads live state, and every loaded occurrence resolves mappings even if full.
     * A callback failure permanently closes admission; already committed slot bits are not rolled back.
     */
    public CpuSat finishAfterCamera(DrawCameras postScrollCameras) {
        requirePhase(Phase.COLLECTING);
        Objects.requireNonNull(postScrollCameras, "post-scroll cameras");
        phase = Phase.DRAINING;
        operating = true;
        try {
            byte[] nextBytes = cpuSat.bytes();
            var provenance = new ArrayList<Provenance>(SAT_PIECE_CAPACITY);
            int spriteCount = 0;
            for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
                int ordinal = 0;
                while (pointerByteCounts[bucket] != 0) {
                    CdSlotRef ref = pointers[bucket][ordinal];
                    LiveDrawSlot live = Objects.requireNonNull(slots.read(ref), "Missing live CD slot " + ref.wholeIndex());
                    requireOperatingPhase(Phase.DRAINING);
                    if (live.idByte() != 0) {
                        int flags = live.spriteFlagsByte();
                        int originX = live.xWord();
                        int originY = live.screenYWord();
                        CameraWords camera = switch (flags & 12) {
                            case 4 -> postScrollCameras.foreground();
                            case 8 -> postScrollCameras.background();
                            case 12 -> postScrollCameras.background3();
                            default -> null;
                        };
                        if (camera != null) {
                            originX = (live.xWord() - camera.xWord() + 128) & 0xFFFF;
                            originY = (wrappedY(live.yWord(), camera.yWord()) - camera.yWord() + 128) & 0xFFFF;
                        }
                        MappingBody body = resolve(ref, live);
                        List<RawPiece> pieces = body.pieces();
                        for (int pieceOrdinal = 0;
                             pieceOrdinal < pieces.size() && spriteCount < SAT_PIECE_CAPACITY;
                             pieceOrdinal++) {
                            writePiece(nextBytes, spriteCount, pieces.get(pieceOrdinal), live, originX, originY);
                            provenance.add(new Provenance(bucket, ordinal, ref, pieceOrdinal));
                            spriteCount++;
                        }
                        // DrawDone:345-346: includes empty frames and partial/full-SAT nonemission.
                        slots.writeOnScreenBit(ref, true);
                        requireOperatingPhase(Phase.DRAINING);
                    }
                    pointers[bucket][ordinal++] = null;
                    pointerByteCounts[bucket] -= 2;
                }
            }
            if (spriteCount == SAT_PIECE_CAPACITY) {
                nextBytes[(SAT_PIECE_CAPACITY - 1) * 8 + 3] = 0;
            } else {
                // DrawObjects:361 clears exactly the next entry's first longword, not its tile/X.
                Arrays.fill(nextBytes, spriteCount * 8, spriteCount * 8 + 4, (byte) 0);
            }
            var completed = new CpuSat(nextBytes, spriteCount, provenance);
            cpuSat = completed; // Publish only after ALL dereferences, bit writes and termination succeed.
            phase = Phase.CLOSED_CUT;
            return completed;
        } catch (RuntimeException | Error failure) {
            phase = Phase.FAILED;
            throw failure;
        } finally {
            operating = false;
        }
    }

    /** Reject stale publication access during an active, failed or closed admission. */
    public CpuSat cpuSat() {
        requirePhase(Phase.CLOSED_CUT);
        return cpuSat;
    }

    /** Queue cut only; root additionally requires its completed composite tail and displayed partition. */
    public Snapshot captureClosedCut() {
        requirePhase(Phase.CLOSED_CUT);
        return new Snapshot(owner, cpuSat);
    }

    /** Composite owner calls every partition's preflight before mutating ANY partition. */
    public void validateRestore(Snapshot snapshot) {
        requirePhase(Phase.CLOSED_CUT);
        Objects.requireNonNull(snapshot, "queue snapshot");
        if (snapshot.owner != owner) { throw new IllegalArgumentException("Foreign CD draw queue owner capture"); }
    }

    /** No slot writes, camera work, mapping lookups, dispatch or V-int service. Never revives failure/close. */
    public void restore(Snapshot snapshot) {
        validateRestore(snapshot);
        cpuSat = snapshot.cpuSat;
    }

    /** Explicit terminal close; closing from a port is a failed in-flight admission. */
    @Override public void close() {
        if (operating || phase == Phase.FAILED) { phase = Phase.FAILED; }
        else { phase = Phase.CLOSED; }
    }

    private MappingBody resolve(CdSlotRef ref, LiveDrawSlot live) {
        boolean staticPiece = (live.spriteFlagsByte() & 0x20) != 0;
        MappingBody body;
        try {
            body = mappings.resolve(live.mappingAddressBits(), staticPiece, live.mapFrameByte());
        } catch (RuntimeException failure) {
            throw mappingFailure(ref, live, staticPiece, "resolution failed", failure);
        }
        requireOperatingPhase(Phase.DRAINING);
        if (body == null || staticPiece != (body instanceof StaticMapping)) {
            throw mappingFailure(ref, live, staticPiece, "absent or mismatched bounded mapping mode", null);
        }
        return body;
    }

    private static UnsupportedOperationException mappingFailure(CdSlotRef ref, LiveDrawSlot live,
                                                                boolean staticPiece, String reason,
                                                                RuntimeException cause) {
        return new UnsupportedOperationException("CD draw slot " + ref.wholeIndex() + " mapping $"
                + String.format(java.util.Locale.ROOT, "%08X", live.mappingAddressBits())
                + " static=" + staticPiece + " frame=" + live.mapFrameByte() + ": " + reason, cause);
    }

    private static boolean culled(DrawCallCut cut, CameraWords foreground) {
        int dx = (short) (cut.xWord() - foreground.xWord());
        if ((short) (dx + cut.widthByte()) < 0 || (short) (dx - cut.widthByte()) >= 320) { return true; }
        int dy = (short) (wrappedY(cut.yWord(), foreground.yWord()) - foreground.yWord());
        return (short) (dy + cut.yRadiusByte()) < 0 || (short) (dy - cut.yRadiusByte()) >= 224;
    }

    private static int wrappedY(int yWord, int cameraYWord) {
        // DrawObject:158-170 / DrawObjects:285-297 use UNSIGNED thresholds before word subtraction.
        if (cameraYWord < 0x100 && yWord >= 0x800) { return (yWord - 0x800) & 0xFFFF; }
        if (cameraYWord >= 0x700 && yWord < 0x100) { return (yWord + 0x800) & 0xFFFF; }
        return yWord;
    }

    private static void writePiece(byte[] bytes, int index, RawPiece piece, LiveDrawSlot live,
                                   int originX, int originY) {
        int flags = live.spriteFlagsByte();
        int relativeX = piece.x();
        int relativeY = piece.y();
        if ((flags & 1) != 0) { relativeX = -relativeX - 8 * (((piece.sizeByte() >>> 2) & 3) + 1); }
        if ((flags & 2) != 0) { relativeY = -relativeY - 8 * ((piece.sizeByte() & 3) + 1); }
        int x = (originX + relativeX) & 0x1FF;
        if (x == 0) { x = 1; }
        // Native ADD.W across the ENTIRE tile attribute word precedes the object's flip XOR.
        int tileWord = ((piece.rawTileWord() + live.baseTileWord()) & 0xFFFF) ^ ((flags & 3) << 11);
        int at = index * 8;
        putWord(bytes, at, originY + relativeY);
        bytes[at + 2] = (byte) piece.sizeByte();
        bytes[at + 3] = (byte) (index + 1);
        putWord(bytes, at + 4, tileWord);
        putWord(bytes, at + 6, x);
    }

    private void requirePhase(Phase expected) {
        if (operating) {
            phase = Phase.FAILED;
            throw new IllegalStateException("Reentrant CD draw queue operation closes admission");
        }
        if (phase != expected) { throw new IllegalStateException("CD draw queue requires " + expected + ", was " + phase); }
    }

    private void requireOperatingPhase(Phase expected) {
        if (phase != expected) { throw new IllegalStateException("CD draw callback closed its admission"); }
    }

    private static void requireOwned(CdSlotRef ref) {
        if (ref.wholeIndex() > 0 && ref.wholeIndex() < 32) {
            throw new IllegalArgumentException("Unowned reserved CD slot " + ref.wholeIndex()
                    + "; finite admission owns player 0 and dynamic 32..127 only");
        }
    }

    private static void putWord(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static void word(int value, String field) { range(value, 0, 0xFFFF, field); }
    private static void octet(int value, String field) { range(value, 0, 0xFF, field); }
    private static void range(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) { throw new IllegalArgumentException("Invalid native " + field + ": " + value); }
    }
}
