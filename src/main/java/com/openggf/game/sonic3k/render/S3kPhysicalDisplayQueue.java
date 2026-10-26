package com.openggf.game.sonic3k.render;

import java.util.Arrays;
import java.util.Objects;

/**
 * CPU input-queue and normal-path publication DATA core.
 *
 * <p>Native044 Draw_Sprite (sonic3k.asm:36171..36192) stores physical SST words,
 * including duplicates, in eight128-byte regions. Render_Sprites
 * (:36376..36430) resolves each word's CURRENT contents and publishes bit7
 * before mapping/piece/SAT work. This is not a GPU display list or a producer,
 * owner-residue store, whole-world capture adapter, or accepted-parent witness.
 *
 * <p>The candidate admits only legal physical SST words/aligned priority words
 * and the normal, flag6-clear consumer. Unsupported/unresolved inputs fail
 * the caller's qualification before mutation; that failure is NOT a native
 * skip/delete branch. The caller must stop, not fall back to an old Java owner.
 * The world adapter now captures/restores this DATA and the actual fresh-Level
 * boundary clears its input. Ordinary Draw_Sprite producers and normal render
 * publication remain unintegrated; no current-RAM or native-pixel claim follows.
 */
final class S3kPhysicalDisplayQueue {
    private static final int REGIONS = 8;
    private static final int REGION_BYTES = 0x80;
    private static final int LAST_COUNT = 0x7E;
    private static final int SST_BASE = 0xB000;
    private static final int SST_BYTES = 0x4A;
    private static final int SST_COUNT = 110;

    private final byte[] input = new byte[0x400];
    private int region;
    private int entryOffset = 2;
    private boolean walking;
    private boolean visitOpen;

    enum Consumer { NORMAL, COMPETITION }

    /** Actual byte/word contents, including a known cleared or code-zero header. */
    record Header(int renderFlags, int xWord, int yWord, int widthByte, int heightByte) {
        Header {
            renderFlags &= 0xFF;
            xWord &= 0xFFFF;
            yWord &= 0xFFFF;
            widthByte &= 0xFF;
            heightByte &= 0xFF;
        }
    }

    /** Already-published camera WORDS and Screen_Y_wrap_value; never fresh/live lookup. */
    record CameraWords(int xWord, int yWord, int yWrapWord) {
        CameraWords {
            xWord &= 0xFFFF;
            yWord &= 0xFFFF;
            yWrapWord &= 0xFFFF;
        }
    }

    /**
     * Caller-owned current physical contents. Address arguments are unsigned
     * stored WORDS (MOVEA.W corresponds to 0xFFFF0000 | addressWord on the CPU).
     * Null means UNRESOLVED, never clear/empty/dead. Writes must target that
     * same physical address, including an explicitly cleared free slot.
     * No callback here may replace contents or submit/reenter queue operations.
     */
    interface Projection {
        Header read(int addressWord);
        void writeRenderFlags(int addressWord, int renderFlags);
    }

    record Submission(int region, int entryOffset) { }

    /** Publication stage only; no assertion about emitted pieces, SAT capacity or GPU readiness. */
    record Visit(int region, int entryOffset, int addressWord,
                 int flagsBefore, int flagsAfter, boolean published) { }

    /**
     * An actual producer call, not registration or a visibility query.
     * Null is native full-through-region7 rejection. There is no implicit reset.
     * This bounded core admits the producer-then-consumer phase, not submissions
     * during a consumer walk; future reentrant adapters need their own proof.
     */
    Submission submit(int addressWord, int priorityWord) {
        requirePhysicalWord(addressWord);
        if (priorityWord < 0 || priorityWord > 0x380 || (priorityWord & 0x7F) != 0) {
            throw new IllegalArgumentException("Priority must be an aligned native word in 0000..0380");
        }
        if (walking) {
            throw new IllegalStateException("Submission during an active consumer pass is unqualified");
        }
        for (int r = priorityWord / REGION_BYTES; r < REGIONS; r++) {
            int base = r * REGION_BYTES;
            int count = word(base);
            if (count < LAST_COUNT) {
                int next = count + 2;
                writeWord(base, next);
                writeWord(base + next, addressWord);
                return new Submission(r, next);
            }
        }
        return null;
    }

    /** Selects an actual normal consumer pass; only cursor state is initialized. */
    void beginPass(Consumer consumer) {
        if (Objects.requireNonNull(consumer) != Consumer.NORMAL) {
            throw new UnsupportedOperationException("Competition publication/count aliases require another adapter");
        }
        if (walking) {
            throw new IllegalStateException("Previous consumer pass has not completed");
        }
        region = 0;
        entryOffset = 2;
        walking = true;
    }

    /**
     * Resolves and publishes the next current physical header. Returns null at
     * end of this pass. The count is deliberately unchanged until finishVisit:
     * mapping/piece/SAT work belongs after publication and before NextObj.
     */
    Visit publishNext(Projection projection, CameraWords camera) {
        if (!walking || visitOpen) {
            throw new IllegalStateException("Begin a pass and finish its previous visit first");
        }
        Objects.requireNonNull(projection);
        Objects.requireNonNull(camera);
        int nextRegion = region;
        int nextOffset = entryOffset;
        while (nextRegion < REGIONS && word(nextRegion * REGION_BYTES) == 0) {
            nextRegion++;
            nextOffset = 2;
        }
        if (nextRegion == REGIONS) {
            region = REGIONS;
            entryOffset = 2;
            walking = false;
            return null;
        }

        int address = word(nextRegion * REGION_BYTES + nextOffset);
        Header header = projection.read(address);
        if (header == null) {
            throw new IllegalStateException("Unresolved current physical SST word: " + Integer.toHexString(address));
        }
        if ((header.renderFlags() & 0x40) != 0) {
            throw new UnsupportedOperationException("Multi-draw publication requires another adapter");
        }

        int cleared = header.renderFlags() & 0x7F;
        projection.writeRenderFlags(address, cleared); // loc_1AD54: visit clears before geometry
        boolean published = insideNormalBounds(header, camera);
        int after = published ? cleared | 0x80 : cleared;
        if (published) {
            projection.writeRenderFlags(address, after); // loc_1ADB2: before SAT/piece tests
        }
        region = nextRegion;
        entryOffset = nextOffset;
        visitOpen = true;
        return new Visit(region, entryOffset, address, header.renderFlags(), after, published);
    }

    /** NextObj, only after the caller's separate piece/SAT stage (or its skip). */
    void finishVisit() {
        if (!walking || !visitOpen) {
            throw new IllegalStateException("No published visit is awaiting NextObj");
        }
        int base = region * REGION_BYTES;
        writeWord(base, word(base) - 2);
        entryOffset += 2;
        visitOpen = false;
    }

    /**
     * Exact core-owned image/cursor only. Header residue, flags, camera and
     * consumer side effects belong to the caller and are NOT captured here.
     */
    Snapshot capture() {
        return new Snapshot(input, region, entryOffset, walking, visitOpen);
    }

    void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        System.arraycopy(snapshot.input, 0, input, 0, input.length);
        region = snapshot.region;
        entryOffset = snapshot.entryOffset;
        walking = snapshot.walking;
        visitOpen = snapshot.visitOpen;
    }

    /** Internal immutable value, not a registered/public rewind snapshot shape. */
    static final class Snapshot {
        private final byte[] input;
        private final int region;
        private final int entryOffset;
        private final boolean walking;
        private final boolean visitOpen;

        private Snapshot(byte[] input, int region, int entryOffset, boolean walking, boolean visitOpen) {
            this.input = input.clone();
            this.region = region;
            this.entryOffset = entryOffset;
            this.walking = walking;
            this.visitOpen = visitOpen;
        }

        byte[] image() {
            return input.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            return other instanceof Snapshot snapshot
                    && region == snapshot.region
                    && entryOffset == snapshot.entryOffset
                    && walking == snapshot.walking
                    && visitOpen == snapshot.visitOpen
                    && Arrays.equals(input, snapshot.input);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(input);
            result = 31 * result + region;
            result = 31 * result + entryOffset;
            result = 31 * result + Boolean.hashCode(walking);
            return 31 * result + Boolean.hashCode(visitOpen);
        }
    }

    private static boolean insideNormalBounds(Header header, CameraWords camera) {
        if ((header.renderFlags() & 4) == 0) {
            return true; // normal flag6-clear screen-coordinate path has no world bounds test
        }
        int relativeX = (short) (header.xWord() - camera.xWord());
        if ((short) (relativeX + header.widthByte()) < 0
                || (short) (relativeX - header.widthByte()) >= 320) {
            return false;
        }
        int wrappedY = ((header.yWord() - camera.yWord() + header.heightByte()) & 0xFFFF)
                & camera.yWrapWord();
        return wrappedY < 224 + 2 * header.heightByte();
    }

    private static void requirePhysicalWord(int addressWord) {
        if (addressWord < SST_BASE || addressWord >= SST_BASE + SST_COUNT * SST_BYTES
                || (addressWord - SST_BASE) % SST_BYTES != 0) {
            throw new IllegalArgumentException("Expected an unsigned native110-slot SST address word");
        }
    }

    private int word(int offset) {
        return ((input[offset] & 0xFF) << 8) | (input[offset + 1] & 0xFF);
    }

    private void writeWord(int offset, int value) {
        input[offset] = (byte) (value >>> 8);
        input[offset + 1] = (byte) value;
    }
}
