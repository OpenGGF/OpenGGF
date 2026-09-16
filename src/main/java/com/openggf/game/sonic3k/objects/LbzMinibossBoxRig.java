package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindStateful;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.render.PatternSpriteRenderer;

/**
 * Shared state machine for the ten yellow miniboss-box pieces.
 *
 * <p>ROM: {@code ChildObjDat_8D25C} children running {@code loc_8CE34} with the
 * per-subtype setup table {@code loc_8D12C} ({@code byte_8D1D4} frames/offsets,
 * {@code word_8D170} release delays, {@code off_8D184} raw-animation scripts).
 * The same children are spawned by {@code Obj_LBZ1Robotnik}'s carried box child
 * ({@code loc_8CD5C}), the standalone {@code Obj_LBZMinibossBox}, and the
 * Knuckles {@code loc_8D046} boxes, so the engine hosts them in this reusable
 * rig that the owning object ticks and renders.
 */
public final class LbzMinibossBoxRig implements RewindStateful<LbzMinibossBoxRig.Snapshot> {
    /** ROM byte_8D1D4: mapping frame, render-flag OR bits, child_dx, child_dy. */
    private static final int[][] PIECE_PARTS = {
            {6, 0, -0x10, 0},
            {6, 2, 0x10, 0},
            {9, 0, 0, 0x14},
            {9, 1, 0, 0x0C},
            {9, 0, 0, -0x0C},
            {9, 1, 0, -0x14},
            {0, 0, -0x0C, -0x10},
            {0, 3, 0x0C, 0x10},
            {3, 0, 0x14, -0x0C},
            {3, 3, -0x14, 0x0C}
    };
    /** ROM word_8D170: frames each piece waits after the parent sets $38 bit 3. */
    private static final int[] RELEASE_DELAYS = {0, 0, 0x10, 0x10, 0x10, 0x10, 0x40, 0x50, 0x70, 0x60};
    /** ROM off_8D184: byte_8D280 {0;6,7,8}, byte_8D285 {0;9,$A,$B}, byte_8D294 {0;0,0}, byte_8D298 {0;3,3}. */
    private static final int[][] PRIMARY_ANIM_FRAMES = {
            {7, 8}, {7, 8},
            {0x0A, 0x0B}, {0x0A, 0x0B}, {0x0A, 0x0B}, {0x0A, 0x0B},
            {0}, {0},
            {3}, {3}
    };
    /** ROM word_8CEEC: y velocities for the four drifting pieces. */
    private static final int[] DRIFT_Y_VELS = {-0x40, 0x40, -0x40, 0x40};
    /** ROM off_8CEDC: byte_8D28A {5;0,1,2}, byte_8D28F {5;3,4,5} — first advance shows index 1. */
    private static final int[][] LATE_ANIM_FRAMES = {{1, 2}, {1, 2}, {4, 5}, {4, 5}};
    private static final int LATE_ANIM_DELAY = 5;
    /** ROM loc_8CEAE: move.w #$5F,$2E(a0) drift duration before the late animation. */
    private static final int DRIFT_FRAMES = 0x5F;
    /** ROM loc_8CF1E: pieces persist until $280 past the coarse camera-back position. */
    private static final int OFFSCREEN_COARSE_RANGE = 0x280;
    private static final int DRIFTING_PIECE_BASE = 6;
    /**
     * ROM loc_8CE64: every piece takes ObjDat3_8D23C priority $100 through SetUp_ObjAttributes
     * (sonic3k.asm:192427-192429, 192786-192789). The art word make_art_tile(ArtTile_LBZMinibossBox,2,0)
     * leaves bit 15 clear (sonic3k.asm:192788), so pieces never sit above high-priority tiles.
     */
    public static final int PIECE_PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x100);
    /**
     * ROM loc_8CF10: when a drifting piece's late Animate_Raw script ends ($F4 -> $34 callback),
     * the callback installs loc_8CF1E and writes priority $380 (sonic3k.asm:192502-192505). The
     * six burst pieces Go_Delete_Sprite instead (off_8D1AC, sonic3k.asm:192733-192739).
     */
    public static final int LINGER_PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x380);
    private static final int[] NO_EXTRA_BUCKETS = new int[0];

    private final Piece[] pieces = new Piece[PIECE_PARTS.length];
    private boolean released;

    public LbzMinibossBoxRig(int anchorX, int anchorY) {
        for (int i = 0; i < PIECE_PARTS.length; i++) {
            pieces[i] = new Piece(i, anchorX, anchorY);
        }
    }

    /** ROM Refresh_ChildPosition: while closed, the pieces track the box anchor. */
    public void follow(int anchorX, int anchorY) {
        if (released) {
            return;
        }
        for (Piece piece : pieces) {
            piece.x = (anchorX + PIECE_PARTS[piece.index][2]) & 0xFFFF;
            piece.y = (anchorY + PIECE_PARTS[piece.index][3]) & 0xFFFF;
        }
    }

    /**
     * ROM loc_8CE72/loc_8CE84: the pieces see the parent's $38 bit 3 on the
     * same frame it is set (children run after their parent), entering the
     * per-piece release-delay routine immediately.
     */
    public void release() {
        released = true;
        for (Piece piece : pieces) {
            if (piece.phase == Piece.Phase.FOLLOW) {
                piece.phase = Piece.Phase.WAIT_RELEASE;
                piece.timer = RELEASE_DELAYS[piece.index];
            }
        }
    }

    public boolean isReleased() {
        return released;
    }

    /** Camera-less tick for contexts without an active camera (headless tests). */
    public static final int NO_CAMERA = Integer.MIN_VALUE;

    /**
     * Ticks every piece.
     *
     * @param cameraX current camera X for the ROM loc_8CF1E off-screen removal,
     *                or {@link #NO_CAMERA} to skip the off-screen cull
     */
    public void update(int cameraX) {
        for (Piece piece : pieces) {
            piece.update(cameraX);
        }
    }

    public boolean hasVisiblePieces() {
        for (Piece piece : pieces) {
            if (!piece.deleted) {
                return true;
            }
        }
        return false;
    }

    /** Draws every live piece regardless of bucket (headless callers and tests). */
    public void draw(PatternSpriteRenderer renderer, int paletteLine) {
        draw(renderer, paletteLine, -1);
    }

    /**
     * Draws the live pieces whose ROM priority word currently maps to {@code bucket};
     * {@code -1} draws every live piece.
     */
    public void draw(PatternSpriteRenderer renderer, int paletteLine, int bucket) {
        for (Piece piece : pieces) {
            if (piece.deleted || (bucket >= 0 && piece.priorityBucket != bucket)) {
                continue;
            }
            int flags = PIECE_PARTS[piece.index][1];
            renderer.drawFrameIndex(piece.frame, piece.x, piece.y,
                    (flags & 0x01) != 0, (flags & 0x02) != 0, paletteLine);
        }
    }

    /** Bit mask (bit = bucket index) of the buckets the live pieces currently occupy. */
    public int liveBucketMask() {
        int mask = 0;
        for (Piece piece : pieces) {
            if (!piece.deleted) {
                mask |= 1 << piece.priorityBucket;
            }
        }
        return mask;
    }

    /** Current bucket of piece {@code index} (test/diagnostic access). */
    public int pieceBucketForTest(int index) {
        return pieces[index].priorityBucket;
    }

    /**
     * Buckets other than {@code ownerBucket} that any live piece of {@code rigs} occupies, for
     * {@link com.openggf.level.objects.MultiBucketRenderable#extraRenderBuckets()}.
     */
    public static int[] extraRenderBuckets(int ownerBucket, LbzMinibossBoxRig... rigs) {
        int mask = 0;
        for (LbzMinibossBoxRig rig : rigs) {
            if (rig != null) {
                mask |= rig.liveBucketMask();
            }
        }
        mask &= ~(1 << ownerBucket);
        if (mask == 0) {
            return NO_EXTRA_BUCKETS;
        }
        int[] buckets = new int[Integer.bitCount(mask)];
        int n = 0;
        for (int bucket = RenderPriority.MIN; bucket <= RenderPriority.MAX; bucket++) {
            if ((mask & (1 << bucket)) != 0) {
                buckets[n++] = bucket;
            }
        }
        return buckets;
    }

    @Override
    public Snapshot captureRewindStateValue() {
        PieceSnapshot[] pieceStates = new PieceSnapshot[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            Piece piece = pieces[i];
            pieceStates[i] = new PieceSnapshot(
                    piece.phase.ordinal(), piece.x, piece.y, piece.ySub, piece.yVel,
                    piece.frame, piece.timer, piece.animIndex, piece.deleted);
        }
        return new Snapshot(released, pieceStates);
    }

    @Override
    public void restoreRewindStateValue(Snapshot state) {
        released = state.released();
        PieceSnapshot[] snapshots = state.pieces();
        for (int i = 0; i < pieces.length && i < snapshots.length; i++) {
            PieceSnapshot snapshot = snapshots[i];
            Piece piece = pieces[i];
            piece.phase = Piece.Phase.values()[snapshot.phase()];
            piece.x = snapshot.x();
            piece.y = snapshot.y();
            piece.ySub = snapshot.ySub();
            piece.yVel = snapshot.yVel();
            piece.frame = snapshot.frame();
            piece.timer = snapshot.timer();
            piece.animIndex = snapshot.animIndex();
            piece.deleted = snapshot.deleted();
            // The $380 write is the loc_8CF10 callback that installs loc_8CF1E, so the
            // bucket is a function of the restored phase and needs no snapshot field.
            piece.priorityBucket = piece.phase == Piece.Phase.LINGER
                    ? LINGER_PRIORITY_BUCKET : PIECE_PRIORITY_BUCKET;
        }
    }

    public record Snapshot(boolean released, PieceSnapshot[] pieces) {
        public Snapshot {
            pieces = pieces.clone();
        }

        @Override
        public PieceSnapshot[] pieces() {
            return pieces.clone();
        }
    }

    public record PieceSnapshot(int phase, int x, int y, int ySub, int yVel,
                                int frame, int timer, int animIndex, boolean deleted) {
    }

    private final class Piece {
        private final int index;
        private int x;
        private int y;
        private int ySub;
        private int yVel;
        private int frame;
        private int timer;
        private int animIndex;
        private Phase phase = Phase.FOLLOW;
        private boolean deleted;
        /** Live ROM priority word as a bucket; $100 from loc_8CE64, $380 from loc_8CF10. */
        private int priorityBucket = PIECE_PRIORITY_BUCKET;

        private enum Phase {
            FOLLOW,
            WAIT_RELEASE,
            PRIMARY_ANIM,
            DRIFT,
            LATE_ANIM,
            LINGER
        }

        private Piece(int index, int anchorX, int anchorY) {
            this.index = index;
            this.frame = PIECE_PARTS[index][0];
            this.x = (anchorX + PIECE_PARTS[index][2]) & 0xFFFF;
            this.y = (anchorY + PIECE_PARTS[index][3]) & 0xFFFF;
        }

        private void update(int cameraX) {
            if (deleted) {
                return;
            }
            switch (phase) {
                case FOLLOW -> {
                    if (released) {
                        phase = Phase.WAIT_RELEASE;
                        timer = RELEASE_DELAYS[index];
                    }
                }
                case WAIT_RELEASE -> {
                    timer--;
                    if (timer < 0) {
                        phase = Phase.PRIMARY_ANIM;
                        animIndex = 0;
                    }
                }
                case PRIMARY_ANIM -> {
                    // ROM Animate_Raw with delay 0: one script frame per update.
                    int[] frames = PRIMARY_ANIM_FRAMES[index];
                    if (animIndex < frames.length) {
                        frame = frames[animIndex++];
                        return;
                    }
                    if (index < DRIFTING_PIECE_BASE) {
                        // ROM off_8D1AC: the six burst panels Go_Delete_Sprite.
                        deleted = true;
                        return;
                    }
                    // ROM loc_8CEAE: the four large panels start drifting.
                    yVel = DRIFT_Y_VELS[index - DRIFTING_PIECE_BASE];
                    timer = DRIFT_FRAMES;
                    phase = Phase.DRIFT;
                }
                case DRIFT -> {
                    moveY();
                    timer--;
                    if (timer < 0) {
                        phase = Phase.LATE_ANIM;
                        animIndex = 0;
                        timer = LATE_ANIM_DELAY;
                    }
                }
                case LATE_ANIM -> {
                    timer--;
                    if (timer >= 0) {
                        return;
                    }
                    int[] frames = LATE_ANIM_FRAMES[index - DRIFTING_PIECE_BASE];
                    if (animIndex < frames.length) {
                        frame = frames[animIndex++];
                        timer = LATE_ANIM_DELAY;
                        return;
                    }
                    // ROM loc_8CF10 -> loc_8CF1E: stay drawn until off-screen, now
                    // behind everything else (move.w #$380,priority(a0), sonic3k.asm:192504).
                    phase = Phase.LINGER;
                    priorityBucket = LINGER_PRIORITY_BUCKET;
                }
                case LINGER -> {
                    if (cameraX == NO_CAMERA) {
                        return;
                    }
                    int coarseDistance = ((x & 0xFF80) - ((cameraX - 0x80) & 0xFF80)) & 0xFFFF;
                    if (coarseDistance > OFFSCREEN_COARSE_RANGE) {
                        deleted = true;
                    }
                }
            }
        }

        private void moveY() {
            int total = ((short) y << 8) + ySub + yVel;
            y = (total >> 8) & 0xFFFF;
            ySub = total & 0xFF;
        }
    }
}
