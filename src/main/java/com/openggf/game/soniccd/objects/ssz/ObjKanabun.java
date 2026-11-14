package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.TrigLookupTable;

/**
 * Kanabun, object $21 in acts 1 and 2 ({@code KanabunObject}, objects/r7/kanabun.asm:3-73): a beetle that
 * flies back and forth $40 pixels either side of its placement while bobbing on a sine ({@code oVar32} steps
 * by {@code oVar34}). Subtype 0 uses the first sprite and script tables with a bob speed of {@code -$200} per
 * frame, any other subtype the second with {@code -$100}; horizontal speed is {@code -$4000} (16.16) either way
 * (:17-32). At each end the horizontal speed reverses, the flip bits toggle, the bob phase and the script are
 * restarted ({@code move.w #$FF,obj.anim_id}, :46-51). The collision byte follows the animation frame
 * (:66-70, from the four-byte table at :93-97: $2F, $30, $30, 0 for frames 0-7 in pairs). It begins with
 * {@code DestroyInGoodFuture} (:4) and ends in {@code CheckObjectDespawn2} against the placement X (:72-73).
 *
 * <p>The bob height is {@code asr.l #2} of {@code sine << 16} (the low word of the long is the previous
 * register contents, :59-63) added to a Y origin whose fraction is $8000; those low bits cannot change the
 * integer Y (the sum of the fractions stays inside one carry bucket), so they are modelled as zero.
 * Shipped {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjKanabun extends SszBadnik {
    /** byte_20D41C (:93-97). */
    private static final int[] COLLISION_BY_FRAME_PAIR = {0x2F, 0x30, 0x30, 0};
    private static final int REVERSE_DISTANCE = 0x40;

    private int originX;
    /** oVar2E:oVar30 as a 16.16 long: the Y origin (:15-16). */
    private int originY;
    /** oVar3C: horizontal speed, 16.16 (:30). */
    private int xSpeed;
    /** oVar32 and oVar34: the bob phase word and its step. */
    private int phase;
    private int phaseStep;
    private boolean second;
    private SszAnimator animator;

    public ObjKanabun(ObjectSpawn spawn) {
        super(spawn, "Kanabun");
    }

    @Override protected String sheetKind() { return second ? SszObjectArt.KANABUN2 : SszObjectArt.KANABUN1; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override protected int despawnX() { return originX; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (destroyInGoodFuture()) return;
        if (routine == 0) {
            if (artProvider() == null) return;
            init();
        }
        move();
    }

    /** KanabunObject (:5-32). */
    private void init() {
        routine += 2;
        priority = 3;
        tileWord = SszObjectArt.KANABUN_TILE;
        width = 0x10;
        yRadius = 0x10;
        originX = x;
        originY = (y << 16) | 0x8000;
        second = spawn.subtype() != 0;
        phaseStep = second ? -0x100 : -0x200;
        xSpeed = -0x4000;
        animator = SszObjectArt.animator(artProvider(),
                second ? SszObjectCatalogue.Entry::kanabunAnimations2 : SszObjectCatalogue.Entry::kanabunAnimations1, 0);
        initialised = true;
    }

    /** loc_20D12E (:35-73). */
    private void move() {
        int position = ((x & 0xFFFF) << 16 | xSub) + xSpeed;
        int distance = Math.abs((short) ((position >>> 16) - originX));
        if (distance > REVERSE_DISTANCE) {
            xSpeed = -xSpeed;                                 // neg.l obj.var_3c(a0); add.l obj.var_3c(a0),d6
            position += xSpeed;
            flipHorizontally();
            phase = 0;
            animator.restartFromZero();
        }
        x = position >>> 16;
        xSub = position & 0xFFFF;
        phase = (phase + phaseStep) & 0xFFFF;                 // move.w obj.var_32(a0),d0; add.w obj.var_34(a0),d0
        int sine = TrigLookupTable.sinHex((phase >> 8) & 0xFF);
        int yLong = originY + ((sine << 16) >> 2);            // swap d0; asr.l #2,d0; add.l obj.var_2e(a0),d0
        y = (yLong >>> 16) & 0xFFFF;
        ySub = yLong & 0xFFFF;
        animator.step();                                      // jsr AnimateObject
        mapFrame = animator.mapFrame();
        if (animator.showedFrame()) spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
        colType = COLLISION_BY_FRAME_PAIR[(mapFrame >> 1) & 3];
    }
}
