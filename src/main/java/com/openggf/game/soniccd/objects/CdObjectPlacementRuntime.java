package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * Bounded native placement prerequisite, NOT an object/body/player admission.
 * Source: Level/Object Spawner.asm at 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Uses common-list order, native word/carry/signed branches and the actual 2+$2FC saved bank.
 * No frame/input/service clock, object dispatch, ring expansion, touch, art or drawing is owned here.
 */
public final class CdObjectPlacementRuntime {
    public static final int SAVED_BODY_BYTES = 0x2FC;
    public static final int SAVED_BANK_BYTES = 2 + SAVED_BODY_BYTES;

    /** Source BSET/BCLR operands. Collection semantics/warp selection remain with their body owner. */
    public enum SavedBit {
        MEMBER_1(0), MEMBER_2(1), MEMBER_3(2), MEMBER_4(3), MEMBER_5(4), MEMBER_6(5), MEMBER_7(6), LOADED(7);
        private final int bit;
        SavedBit(int bit) { this.bit = bit; }
    }

    /** Record cursors index the bound COMMON list; size() denotes its forward sentinel.
     * Null offsets are within the distinct six-byte Null list: -1 unbound, 0 bound at Init.
     * The two saved IDs are projections of bank bytes 0/1, never independently stored shadows.
     */
    public record Status(int routine, int previousChunkWord, int rightRecordIndex, int leftRecordIndex,
                         int rightSavedId, int leftSavedId, int nullRightByteOffset, int nullLeftByteOffset) { }

    public static final class Snapshot {
        private final Object owner;
        private final CdObjectPlacements placements;
        private final Optional<CdR11aAssets.Identity> identity;
        private final Optional<CdR11bAssets.Identity> pastIdentity;
        private final Status status;
        private final CdObjectPool.Snapshot pool;
        private final CdAssetBytes savedFlags;
        private Snapshot(Object owner, CdObjectPlacements placements, Optional<CdR11aAssets.Identity> identity,
                         Optional<CdR11bAssets.Identity> pastIdentity,
                         Status status, CdObjectPool.Snapshot pool, CdAssetBytes savedFlags) {
            this.owner = owner;
            this.placements = placements;
            this.identity = identity;
            this.pastIdentity = pastIdentity;
            this.status = status;
            this.pool = pool;
            this.savedFlags = savedFlags;
        }
        public Status status() { return status; }
        public CdObjectPool.Snapshot pool() { return pool; }
        public CdAssetBytes savedFlags() { return savedFlags; }
    }

    private final Object owner = new Object();
    private final CdObjectPlacements placements;
    private final Optional<CdR11aAssets.Identity> identity;
    private final Optional<CdR11bAssets.Identity> pastIdentity;
    private final CdObjectPool pool = new CdObjectPool();
    private final byte[] savedFlags = new byte[SAVED_BANK_BYTES];
    private int routine;
    private int previousChunk;
    private int right;
    private int left;
    private int nullRight = -1;
    private int nullLeft = -1;

    /**
     * Pure decoder consumer for constructed or independently parsed data, with no production authority.
     * Requires the full ObjectLayouts decode: records-only input cannot invent the other sentinels.
     * Production callers use fromAssets; this constructor never creates an authenticated CdR11aAssets.
     */
    public CdObjectPlacementRuntime(CdObjectPlacements placements) { this(placements, Optional.empty()); }

    private CdObjectPlacementRuntime(CdObjectPlacements placements, Optional<CdR11aAssets.Identity> identity) {
        this(placements, identity, Optional.empty());
    }

    private CdObjectPlacementRuntime(CdObjectPlacements placements, Optional<CdR11aAssets.Identity> identity,
                                     Optional<CdR11bAssets.Identity> pastIdentity) {
        this.placements = Objects.requireNonNull(placements);
        this.identity = identity;
        this.pastIdentity = pastIdentity;
        if (placements.backwardSentinel().isEmpty() || placements.nullList().isEmpty()) {
            throw new IllegalArgumentException("CD spawner requires distinct backward/forward/Null layout views");
        }
        // Reject constructed saved IDs which would address beyond the real bank before a pass can mutate it.
        // Do not round the 764-byte body up to an invented 256*3 table.
        int savedId = 1;
        for (var row : placements.records()) {
            if (row.hasSavedFlag()) {
                for (int period = 0; period < 3; period++) {
                    if ((row.periodFlags() & (0x20 << period)) != 0) { bankIndex(savedId, period); }
                }
                savedId++;
            }
        }
    }

    /** Authenticated production ingress; no asserted digest/raw-file/disassembly/test certificate seam. */
    public static CdObjectPlacementRuntime fromAssets(CdR11aAssets assets) {
        Objects.requireNonNull(assets);
        var identity = assets.identity();
        if (identity.variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("New CD placement prerequisite is audited for USA R11A only");
        }
        if (!identity.stage().equals(CdStageKey.parse("R11A")) || identity.file() != CdNativeFile.R11A
                || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM) {
            throw new IllegalArgumentException("CD placement source must be authenticated original R11A Word RAM");
        }
        return new CdObjectPlacementRuntime(assets.placements(), Optional.of(identity));
    }

    /**
     * Restart ingress for the in-module LevelStart path, used only by the runtime's restart factory: the exact
     * carried savedObjFlags bank (2+OBJFLAGSCNT bytes, Main CPU Variables.inc:47-48), every byte including both
     * header IDs. Routine 8's ResetSavedObjFlags already ran (Sonic/Main.asm:2465) and .NormalLoad has no writer;
     * the next SpawnObjects_Init rewrites only the header (Object Spawner.asm:54-55,74,92). The bank is checked
     * before the asset gate; everything else is the cold factory above. Never a restore of another owner's capture.
     */
    static CdObjectPlacementRuntime fromAssets(CdR11aAssets assets, CdAssetBytes exactSavedBank) {
        Objects.requireNonNull(exactSavedBank, "exact carried saved-object bank");
        if (exactSavedBank.length() != SAVED_BANK_BYTES) {
            throw new IllegalArgumentException("Carried saved-object bank must be 2+$2FC bytes, was " + exactSavedBank.length());
        }
        var runtime = fromAssets(assets);
        exactSavedBank.buffer().get(runtime.savedFlags);
        return runtime;
    }

    /**
     * Authenticated original USA R11B placement source with the exact surviving MAINVARS bank.
     * Native savedObjFlags at FF1200..FF14FD survives RunMMD/.ClearFlowers/.ClearObjects;
     * Main:37-55 selects ResetSavedObjFlags separately, and SpawnObjects_Init:54-55 writes
     * only header0101 before its native scans. The caller owns the actual carry/clear/Main402
     * cut; this factory executes none of them and never restores another owner's capture.
     * Bank width is checked before the source gate, matching the retained A carried ingress.
     * B provenance stays private; sourceIdentity() retains its existing A-only return type.
     */
    public static CdObjectPlacementRuntime fromAssets(CdR11bAssets assets, CdAssetBytes exactSavedBank) {
        Objects.requireNonNull(exactSavedBank, "exact carried saved-object bank");
        if (exactSavedBank.length() != SAVED_BANK_BYTES) {
            throw new IllegalArgumentException("Carried saved-object bank must be 2+$2FC bytes, was " + exactSavedBank.length());
        }
        Objects.requireNonNull(assets, "authenticated original R11B assets");
        var identity = assets.identity();
        if (identity.variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("Past CD placement prerequisite is audited for USA R11B only");
        }
        if (!identity.stage().equals(CdStageKey.parse("R11B")) || identity.file() != CdNativeFile.R11B
                || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM) {
            throw new IllegalArgumentException("CD placement source must be authenticated original R11B Word RAM");
        }
        var runtime = new CdObjectPlacementRuntime(assets.placements(), Optional.empty(), Optional.of(identity));
        exactSavedBank.buffer().get(runtime.savedFlags);
        return runtime;
    }

    public CdObjectPlacements placements() { return placements; }
    public Optional<CdR11aAssets.Identity> sourceIdentity() { return identity; }

    /** Package-local admission guards; the public identity accessor retains its A-only contract. */
    boolean hasAuthenticatedSource() { return identity.isPresent() || pastIdentity.isPresent(); }
    boolean sameBSource(CdR11bAssets assets) {
        return pastIdentity.equals(Optional.of(Objects.requireNonNull(assets, "assets").identity()));
    }

    public CdObjectPool pool() { return pool; }
    public Status status() {
        return new Status(routine, previousChunk, right, left, header(0), header(1), nullRight, nullLeft);
    }

    /** ResetSavedObjFlags (11-20), a separate source lifetime from Init, pool clear and rewind. */
    public void resetSavedObjectFlags() {
        Arrays.fill(savedFlags, (byte) 0);
        savedFlags[0] = savedFlags[1] = 1;
    }

    public int savedFlag(int savedId, int nativePeriod) { return Byte.toUnsignedInt(savedFlags[bankIndex(savedId, nativePeriod)]); }
    public void bsetSavedFlag(int savedId, int nativePeriod, SavedBit bit) {
        int at = bankIndex(savedId, nativePeriod);
        savedFlags[at] = (byte) (savedFlags[at] | (1 << Objects.requireNonNull(bit).bit));
    }
    public void bclrSavedFlag(int savedId, int nativePeriod, SavedBit bit) {
        int at = bankIndex(savedId, nativePeriod);
        savedFlags[at] = (byte) (savedFlags[at] & ~(1 << Objects.requireNonNull(bit).bit));
    }

    /**
     * SpawnObjects_Init then Main, once at the parent's pre-scroll source call cut.
     * Inputs are native unsigned camera word and timeZone byte (bit 7 masked by CheckObjTimeZone).
     * The caller owns unpaused-loop entitlement. Equal chunk returns even after allocation failure.
     */
    public void spawnObjects(int cameraXWord, int timeZoneByte) {
        if ((cameraXWord & ~0xFFFF) != 0 || (timeZoneByte & ~0xFF) != 0) {
            throw new IllegalArgumentException("CD placement inputs require native word/byte widths");
        }
        int period = timeZoneByte & 0x7F;
        requirePeriod(period);
        if (routine == 0) { initialize(cameraXWord); }
        int chunk = cameraXWord & 0xFF80;
        if (chunk == previousChunk) { return; }
        boolean forward = (short) chunk >= (short) previousChunk; // CMP.W/BGE, including overflow.
        previousChunk = chunk; // Before allocation: an unchanged chunk MUST NOT retry a failure.
        if (forward) { forward(chunk, period); }
        else { backward(chunk, period); }
    }

    /** SpawnObjects_Init:42-101; only header bytes reset, not saved collection/loaded state. */
    private void initialize(int camera) {
        routine = 2;
        right = left = 0;
        nullRight = nullLeft = 0;
        savedFlags[0] = savedFlags[1] = 1;
        int bound = camera < 0x80 ? 0 : word(camera - 0x80); // SUBI.W/BCC, not signed max().
        bound &= 0xFF80;
        while (bound > x(right)) { // CMP.W/BLS: unsigned, strict.
            if (entry(right).hasSavedFlag()) { addHeader(0, 1); }
            right++;
        }
        boolean borrow = bound < 0x80;
        bound = word(bound - 0x80);
        if (!borrow) {
            while (bound > x(left)) {
                if (entry(left).hasSavedFlag()) { addHeader(1, 1); }
                left++;
            }
        }
        previousChunk = 0xFFFF;
    }

    /** SpawnObjects_Forward:176-227. */
    private void forward(int chunk, int period) {
        int bound = word(chunk + 0x280);
        int savedId = 0; // MOVEQ at Main; unflagged records do not replace it.
        while (bound > x(right)) { // BLS stops at equality, unsigned after word wrap.
            var row = entry(right);
            if (row.hasSavedFlag()) {
                savedId = header(0);
                addHeader(0, 1);
            }
            if (spawn(row, savedId, period)) { right++; }
            else {
                if (row.hasSavedFlag()) {
                    addHeader(0, -1);
                    bclrSavedFlag(savedId, period, SavedBit.LOADED);
                }
                break;
            }
        }
        boolean borrow = bound < 0x300;
        bound = word(bound - 0x300); // Use wrapped ADDI result; cannot simplify to chunk-80 at overflow.
        if (!borrow) {
            while (bound > x(left)) {
                if (entry(left).hasSavedFlag()) { addHeader(1, 1); }
                left++;
            }
        }
    }

    /** SpawnObjects_Backward:124-172; SpawnObject's successful +8 cancels caller's -8. */
    private void backward(int chunk, int period) {
        int bound = word(chunk - 0x80);
        int savedId = 0;
        if (chunk >= 0x80) { // SUBI.W/BCS.
            while ((short) bound < (short) x(left - 1)) { // CMP.W/BGE stops, SIGNED.
                var row = entry(left - 1);
                left--;
                if (row.hasSavedFlag()) {
                    addHeader(1, -1);
                    savedId = header(1);
                }
                if (!spawn(row, savedId, period)) {
                    if (row.hasSavedFlag()) {
                        addHeader(1, 1);
                        bclrSavedFlag(savedId, period, SavedBit.LOADED);
                    }
                    left++; // Allocation failure: restore the cursor, unlike a successful skip/spawn.
                    break;
                }
            }
        }
        bound = word(bound + 0x300); // Executed even when the left subtraction borrowed.
        while ((short) bound <= (short) x(right - 1)) { // CMP.W/BGT stops, SIGNED and strict.
            if (entry(right - 1).hasSavedFlag()) { addHeader(0, -1); }
            right--;
        }
    }

    /** CheckObjTimeZone/SpawnObject:240-319. True includes wrong-period/already-loaded successful skips. */
    private boolean spawn(CdObjectPlacements.Placement row, int savedId, int period) {
        // ROL.B #3 / ANDI.B #7 / BTST period is equivalent for the decoded E0-only period field.
        if ((row.periodFlags() & (0x20 << period)) == 0) { return true; }
        if (row.hasSavedFlag()) {
            boolean alreadyLoaded = (savedFlag(savedId, period) & 0x80) != 0;
            bsetSavedFlag(savedId, period, SavedBit.LOADED); // BSET before FindObjSlot, even on failure.
            if (alreadyLoaded) { return true; }
        }
        var found = pool.findObjSlot();
        if (found.isEmpty()) { return false; }
        var state = pool.state(found.getAsInt());
        state = state.moveWord(X, row.centreX()).moveWord(Y, row.packedY() & 0xFFF);
        int rotated = word((row.packedY() << 2) | (row.packedY() >>> 14));
        int flips = rotated & 3;
        state = state.moveByte(SPRITE_FLAGS, flips).moveByte(FLAGS, flips);
        if (row.hasSavedFlag()) { state = state.moveByte(SAVED_FLAGS_ID, savedId); }
        state = state.moveByte(ID, row.objectId()).moveByte(SUBTYPE, row.subtype()).moveByte(SUBTYPE2, row.subtype2());
        pool.write(state); // No implicit clear, no routine/fraction/collision/mapping writes.
        return true;
    }

    public Snapshot capture() {
        return new Snapshot(owner, placements, identity, pastIdentity, status(), pool.capture(), CdAssetBytes.copyOf(savedFlags));
    }

    public void validateRestore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (snapshot.owner != owner || snapshot.placements != placements || !snapshot.identity.equals(identity)
                || !snapshot.pastIdentity.equals(pastIdentity)) {
            throw new IllegalArgumentException("Foreign CD placement source/runtime capture");
        }
        pool.validateRestore(snapshot.pool);
    }

    /** No init, allocation, body execution or other side effects are replayed by restore. */
    public void restore(Snapshot snapshot) {
        validateRestore(snapshot);
        byte[] restored = snapshot.savedFlags.copy();
        pool.restore(snapshot.pool);
        System.arraycopy(restored, 0, savedFlags, 0, SAVED_BANK_BYTES);
        var state = snapshot.status;
        routine = state.routine();
        previousChunk = state.previousChunkWord();
        right = state.rightRecordIndex();
        left = state.leftRecordIndex();
        nullRight = state.nullRightByteOffset();
        nullLeft = state.nullLeftByteOffset();
    }

    private int header(int offset) { return Byte.toUnsignedInt(savedFlags[offset]); }
    private void addHeader(int offset, int delta) { savedFlags[offset] = (byte) (savedFlags[offset] + delta); }
    private static int word(int value) { return value & 0xFFFF; }
    private static void requirePeriod(int period) {
        if (period < 0 || period > 2) { throw new IllegalArgumentException("Native CD period must be 0, 1 or 2"); }
    }
    private static int bankIndex(int savedId, int period) {
        if ((savedId & ~0xFF) != 0) { throw new IllegalArgumentException("CD saved ID requires a native byte"); }
        requirePeriod(period);
        return Objects.checkIndex(2 + 3 * savedId + period, SAVED_BANK_BYTES);
    }

    private int x(int row) {
        if (row == -1) { return placements.backwardSentinel().orElseThrow().unsignedWord(0); }
        if (row == placements.records().size()) { return placements.forwardSentinel().unsignedWord(0); }
        return entry(row).centreX();
    }
    private CdObjectPlacements.Placement entry(int row) {
        if (row < 0 || row >= placements.records().size()) {
            throw new UnsupportedOperationException("Native CD spawner selected outside its bounded records: " + row);
        }
        return placements.records().get(row);
    }
}
