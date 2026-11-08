package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdR31BumperCall;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR13FutureAssets;
import com.openggf.game.soniccd.level.CdR31aAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
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
        private final Optional<CdR12aTerrainAssets.Identity> act2Identity;
        private final Optional<CdR12bTerrainAssets.Identity> act2PastIdentity;
        private final Status status;
        private final CdObjectPool.Snapshot pool;
        private final CdAssetBytes savedFlags;
        private final CdR31aAssets r31aSource;
        private Snapshot(Object owner, CdObjectPlacements placements, Optional<CdR11aAssets.Identity> identity,
                         Optional<CdR11bAssets.Identity> pastIdentity,
                         Optional<CdR12aTerrainAssets.Identity> act2Identity,
                         Optional<CdR12bTerrainAssets.Identity> act2PastIdentity,
                         Status status, CdObjectPool.Snapshot pool, CdAssetBytes savedFlags, CdR31aAssets r31aSource) {
            this.owner = owner;
            this.placements = placements;
            this.identity = identity;
            this.pastIdentity = pastIdentity;
            this.act2Identity = act2Identity;
            this.act2PastIdentity = act2PastIdentity;
            this.status = status;
            this.pool = pool;
            this.savedFlags = savedFlags;
            this.r31aSource = r31aSource;
        }
        public Status status() { return status; }
        public CdObjectPool.Snapshot pool() { return pool; }
        public CdAssetBytes savedFlags() { return savedFlags; }
    }

    private final Object owner = new Object();
    private final CdObjectPlacements placements;
    private final Optional<CdR11aAssets.Identity> identity;
    private final Optional<CdR11bAssets.Identity> pastIdentity;
    private final Optional<CdR12aTerrainAssets.Identity> act2Identity;
    private final Optional<CdR12bTerrainAssets.Identity> act2PastIdentity;
    private final CdR13FutureAssets act3Future; // Exact retained aggregate, never a public identity certificate.
    private final CdR31aAssets r31aSource; // Exact original R31A owner, independent of legacy identity projections.
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
        this(placements, identity, pastIdentity, Optional.empty());
    }

    private CdObjectPlacementRuntime(CdObjectPlacements placements, Optional<CdR11aAssets.Identity> identity,
                                     Optional<CdR11bAssets.Identity> pastIdentity,
                                     Optional<CdR12aTerrainAssets.Identity> act2Identity) {
        this(placements, identity, pastIdentity, act2Identity, Optional.empty());
    }

    private CdObjectPlacementRuntime(CdObjectPlacements placements, Optional<CdR11aAssets.Identity> identity,
                                     Optional<CdR11bAssets.Identity> pastIdentity,
                                     Optional<CdR12aTerrainAssets.Identity> act2Identity,
                                     Optional<CdR12bTerrainAssets.Identity> act2PastIdentity) {
        this(placements, identity, pastIdentity, act2Identity, act2PastIdentity, null);
    }

    private CdObjectPlacementRuntime(CdObjectPlacements placements, Optional<CdR11aAssets.Identity> identity,
                                     Optional<CdR11bAssets.Identity> pastIdentity,
                                     Optional<CdR12aTerrainAssets.Identity> act2Identity,
                                     Optional<CdR12bTerrainAssets.Identity> act2PastIdentity, CdR13FutureAssets act3Future) {
        this(placements, identity, pastIdentity, act2Identity, act2PastIdentity, act3Future, null);
    }

    private CdObjectPlacementRuntime(CdObjectPlacements placements, Optional<CdR11aAssets.Identity> identity,
                                     Optional<CdR11bAssets.Identity> pastIdentity,
                                     Optional<CdR12aTerrainAssets.Identity> act2Identity,
                                     Optional<CdR12bTerrainAssets.Identity> act2PastIdentity, CdR13FutureAssets act3Future,
                                     CdR31aAssets r31aSource) {
        this.r31aSource = r31aSource;
        this.act3Future = act3Future;
        this.placements = Objects.requireNonNull(placements);
        this.identity = identity;
        this.pastIdentity = pastIdentity;
        this.act2Identity = act2Identity;
        this.act2PastIdentity = act2PastIdentity;
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
     * Actual original-USA R12A relation over its authenticated terrain owner's retained MMD/layout.
     * The caller supplies every surviving saved-bank byte and owns any reached ResetSavedObjFlags;
     * this factory performs no native clear, Init, spawn, body or programme/World admission.
     * Object Spawner.asm:42-101,240-319 is the same common-list algorithm for PPZ2.
     */
    public static CdObjectPlacementRuntime fromAssets(CdR12aTerrainAssets assets, CdAssetBytes exactSavedBank) {
        Objects.requireNonNull(exactSavedBank, "exact carried saved-object bank");
        if (exactSavedBank.length() != SAVED_BANK_BYTES) {
            throw new IllegalArgumentException("Carried saved-object bank must be 2+$2FC bytes, was " + exactSavedBank.length());
        }
        Objects.requireNonNull(assets, "authenticated original R12A terrain assets");
        var identity = assets.identity();
        if (identity.variant() != CdReleaseVariant.USA || !identity.stage().equals(CdStageKey.parse("R12A"))
                || identity.file() != CdNativeFile.R12A || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM) {
            throw new IllegalArgumentException("CD placement source must be authenticated original USA R12A Word RAM");
        }
        var runtime = new CdObjectPlacementRuntime(assets.objects().placements(), Optional.empty(), Optional.empty(), Optional.of(identity));
        exactSavedBank.buffer().get(runtime.savedFlags);
        return runtime;
    }

    /**
     * Actual original-USA R12B relation over its authenticated retained image and common layout.
     * The caller supplies every surviving savedObjFlags byte and owns any actual clear/Init cut.
     * Object Spawner.asm:42-319 at N1ecc5f3a is the existing algorithm; no native operation,
     * object body, period/World/Frame admission, current art or source relabeling executes here.
     */
    public static CdObjectPlacementRuntime fromAssets(CdR12bTerrainAssets assets, CdAssetBytes exactSavedBank) {
        Objects.requireNonNull(exactSavedBank, "exact carried saved-object bank");
        if (exactSavedBank.length() != SAVED_BANK_BYTES) {
            throw new IllegalArgumentException("Carried saved-object bank must be 2+$2FC bytes, was " + exactSavedBank.length());
        }
        Objects.requireNonNull(assets, "authenticated original R12B terrain assets");
        var identity = assets.identity();
        if (identity.variant() != CdReleaseVariant.USA || !identity.stage().equals(CdStageKey.parse("R12B"))
                || identity.file() != CdNativeFile.R12B || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM) {
            throw new IllegalArgumentException("CD placement source must be authenticated original USA R12B Word RAM");
        }
        var runtime = new CdObjectPlacementRuntime(assets.placements(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(identity));
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

    // BEGIN R13 RETAINED PLACEMENT ADAPTER 20261106
    /** Every bank byte is supplied by the caller's real native clear/carry owner. No clear/spawn/World entry. */
    public static CdObjectPlacementRuntime fromAct3FutureAssets(CdR13FutureAssets assets, CdAssetBytes exactSavedBank) {
        Objects.requireNonNull(exactSavedBank, "exact carried saved-object bank");
        if (exactSavedBank.length() != SAVED_BANK_BYTES)
            throw new IllegalArgumentException("Carried saved-object bank must be 2+$2FC bytes, was " + exactSavedBank.length());
        CdStagePlayerSource.fromAct3Future(Objects.requireNonNull(assets, "actual original R13 future aggregate"));
        var runtime = new CdObjectPlacementRuntime(assets.placements(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), assets);
        exactSavedBank.buffer().get(runtime.savedFlags);
        return runtime;
    }
    /** Actual retained source identity. Equal public records/layout values do not create this relation. */
    public boolean ownsAct3FutureAssets(CdR13FutureAssets assets) { return assets != null && act3Future == assets && placements == assets.placements(); }
    // END R13 RETAINED PLACEMENT ADAPTER 20261106

    // BEGIN R31A RETAINED PLACEMENT ADAPTER 20261107
    /**
     * Original USA R31A source and exact surviving2+$2FC bank; no native reset, Init or World admission.
     * R3 Legacy Act1 Shared includes common/object_spawn.asm:31-312. Its saved ordinal is a BYTE;
     * source row indices0..516 remain full list cursors, not saved IDs. The actual source has224 saved rows.
     */
    public static CdObjectPlacementRuntime fromR31aAssets(CdR31aAssets assets, CdAssetBytes exactSavedBank) {
        Objects.requireNonNull(exactSavedBank, "exact carried saved-object bank");
        if (exactSavedBank.length() != SAVED_BANK_BYTES) {
            throw new IllegalArgumentException("Carried saved-object bank must be 2+$2FC bytes, was " + exactSavedBank.length());
        }
        var layout = CdObjectPlacements.fromR31aAssets(assets);
        var runtime = new CdObjectPlacementRuntime(layout, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), null, assets);
        exactSavedBank.buffer().get(runtime.savedFlags);
        return runtime;
    }
    /** Actual retained owner and its cached native layout; equal identities or rows confer no relation. */
    public boolean ownsR31aAssets(CdR31aAssets assets) {
        return assets != null && r31aSource == assets && placements == assets.placements();
    }
    // END R31A RETAINED PLACEMENT ADAPTER 20261107

    public CdObjectPlacements placements() { return placements; }
    public Optional<CdR11aAssets.Identity> sourceIdentity() { return identity; }

    /** Package-local admission guards; the public identity accessor retains its A-only contract. */
    boolean hasAuthenticatedSource() { return identity.isPresent() || pastIdentity.isPresent() || act2Identity.isPresent() || act2PastIdentity.isPresent() || act3Future != null || r31aSource != null; }
    boolean sameBSource(CdR11bAssets assets) {
        return pastIdentity.equals(Optional.of(Objects.requireNonNull(assets, "assets").identity()));
    }
    boolean sameAct2Source(CdR12aObjectAssets assets) {
        Objects.requireNonNull(assets, "assets");
        return act2Identity.equals(Optional.of(assets.identity())) && placements == assets.placements();
    }

    /** Exact retained R12B layout relation; an equal public Identity cannot lend another source view. */
    boolean sameAct2PastSource(CdR12bTerrainAssets assets) {
        Objects.requireNonNull(assets, "assets");
        return act2PastIdentity.equals(Optional.of(assets.identity())) && placements == assets.placements();
    }

    public CdObjectPool pool() { return pool; }
    public Status status() {
        return new Status(routine, previousChunk, right, left, header(0), header(1), nullRight, nullLeft);
    }

    /** Main.ClearCamera [FFF700,FFF800), Definitions291-298 at N1ecc5f3a:
     * only spawner routine/previous WORD/four layout pointers. Zero pointers are represented
     * as unbound cursors until native Init binds the actual source lists. Saved2+764 and full96
     * are nonwrites. Caller owns genuine native action; this finite writer grants no authority. */
    public void clearLevelStartCameraValues(){routine=0;previousChunk=0;right=0;left=0;nullRight=-1;nullLeft=-1;}

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
        return new Snapshot(owner, placements, identity, pastIdentity, act2Identity, act2PastIdentity, status(), pool.capture(), CdAssetBytes.copyOf(savedFlags), r31aSource);
    }

    public void validateRestore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (snapshot.owner != owner || snapshot.placements != placements || !snapshot.identity.equals(identity)
                || !snapshot.pastIdentity.equals(pastIdentity) || !snapshot.act2Identity.equals(act2Identity)
                || !snapshot.act2PastIdentity.equals(act2PastIdentity) || snapshot.r31aSource != r31aSource) {
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
    /** Original Bumper byte store only: linear counter and period-loaded clear remain distinct. */
    public static final class BumperSavedPublication {
        private final CdObjectPlacementRuntime owner;private final CdR31BumperCall original;
        private final Snapshot before;private final int index,value;private boolean used;
        private BumperSavedPublication(CdObjectPlacementRuntime owner,CdR31BumperCall original,int index,int value){
            this.owner=owner;this.original=original;before=owner.capture();this.index=index;this.value=value;
        }
    }
    private boolean bumperSavedApplying;
    public BumperSavedPublication prepareBumperSavedStore(CdR31BumperCall original){
        if(bumperSavedApplying)throw new IllegalStateException("Bumper saved preparation reentry");
        Objects.requireNonNull(original).requireSavedPreparation(this);var op=original.savedStore();int at;
        if(op.kind()==CdPinballContext.SavedKind.BUMPER_LINEAR_COUNTER){
            if(op.savedId()==0||op.period()!=-1)throw new IllegalArgumentException("Not original linear counter");
            at=Objects.checkIndex(2+op.savedId(),SAVED_BANK_BYTES);
            if(op.expectedByte()>=0x8a||op.outputByte()!=op.expectedByte()+1)throw new IllegalArgumentException("Counter cap/output differs");
        }else{at=bankIndex(op.savedId(),op.period());if(op.outputByte()!=(op.expectedByte()&127))throw new IllegalArgumentException("Loaded clear differs");}
        if(Byte.toUnsignedInt(savedFlags[at])!=op.expectedByte())throw new IllegalArgumentException("Stale selected Bumper bank byte");
        return new BumperSavedPublication(this,original,at,op.outputByte());
    }
    private void bumperSavedCut(BumperSavedPublication p){
        if(p==null||p.owner!=this||p.used)throw new IllegalArgumentException("Foreign/used Bumper saved store");
        validateRestore(p.before);var n=capture();
        if(!n.status().equals(p.before.status())||!n.pool().slots().equals(p.before.pool().slots())
                ||!Arrays.equals(n.savedFlags().copy(),p.before.savedFlags().copy()))throw new IllegalArgumentException("Changed complete placement cut");
    }
    public void validateBumperSavedStore(BumperSavedPublication p){
        if(bumperSavedApplying)throw new IllegalStateException("Bumper saved validation reentry");
        bumperSavedCut(p);p.original.requireSavedPreparation(this);bumperSavedCut(p);
    }
    public void applyBumperSavedStore(BumperSavedPublication p){
        if(bumperSavedApplying)throw new IllegalStateException("Bumper saved application reentry");bumperSavedApplying=true;
        try{bumperSavedCut(p);p.original.requireSavedApplication(this);bumperSavedCut(p);
            // No allocation/callback after the last preflight and before the one native byte write.
            p.used=true;savedFlags[p.index]=(byte)p.value;
        }finally{bumperSavedApplying=false;}
    }

}
