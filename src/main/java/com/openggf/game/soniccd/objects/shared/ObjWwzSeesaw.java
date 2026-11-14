package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/**
 * Wacky Workbench's seesaw, object $2C ({@code SeesawObject}, objects/r6/seesaw.asm:3-216; Metallic Madness's
 * {@code SeesawObject} is another routine, {@code objects/r8/seesaw.asm}). The placed object is the body; routine 0
 * (:23-48) spawns two pads ({@link Pad}, subtype $FF, 16 wide, 8 high, frame 9), the first $28 left and $18 above it, the
 * second $28 right and $18 below it. Routine 2 (:70-97) counts $78 frames ({@code SeesawAnimateTilt}, :107-124): at 60 it
 * steps the animation, at 0 it starts the body sliding down a pixel a frame ({@code SeesawApplyTiltMotion}, :129-153,
 * until the second pad's outer corner meets the floor, :156-165). Each frame the body calls {@code TopSolidObject}
 * for the second pad, then for the first; a rider on the first starts routine 4 (:99-104, :167-185), which lifts the second pad $18 and the body
 * $C a frame for four frames and then swaps the pads' roles ({@code SeesawReset}, :187-203).
 *
 * <p>The pads' solid calls run in the body's turn, in the ROM with the pad as the object; here the body is the solid object
 * and borrows the pad's position, width and radius for each call. The engine keeps one standing latch per object, the ROM one
 * per pad (bit 3 of {@code oFlags}), so a call for the pad the player does not stand on would release them: the pad the
 * player stood on at the previous call goes first and, if it still holds them, the other pad is not asked (the pads are
 * disjoint in X: $28 apart, 16 wide, so the other pad's call would be a release of a latch it never held).
 */
public final class ObjWwzSeesaw extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private static final int ROUTINE_MAIN = 2;
    private static final int ROUTINE_PUSH_UP = 4;

    private int routine;
    /** {@code obj.var_2e}, a signed word. */
    private int time;
    /** {@code obj.y_speed}; the routines use its high byte as pixels a frame. */
    private int ySpeed;
    /** {@code obj.var_2a} and {@code obj.var_2c}. */
    private Pad padA;
    private Pad padB;
    /** The pad the player stood on at the last call that held them, or null. */
    private Pad standingPad;
    private Pad activePad;
    private int spriteFlip;
    private int mapFrame;
    private int animId;
    private CdAnimator animator;
    private boolean initialised;

    public ObjWwzSeesaw(ObjectSpawn spawn) {
        super(spawn, "Seesaw");
        spriteFlip = spawn.renderFlags() & 3;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case ROUTINE_MAIN -> main();
            default -> pushUp();
        }
    }

    /** SeesawObject_0_Routine0 (objects/r6/seesaw.asm:23-48). */
    private void init() {
        var level = cdLevel();
        var objects = services().objectManager();
        if (level == null || objects == null) return;
        routine = ROUTINE_MAIN;
        priority = 3;
        width = 0x18;
        yRadius = 0x18;
        tileWord = SharedObjectCatalogue.offset(level.payload(), "wwzSeesawTile");
        padA = new Pad(this, x - 0x28, y - 0x18, spriteFlip);          // the child initialiser, then subi $28 / $18 (SeesawObject_0_Routine0, :34-39)
        objects.addDynamicObject(padA);                                 // jsr SpawnObject
        padB = new Pad(this, x + 0x28, y + 0x18, spriteFlip | 1);      // addi $28 / $18, bset #0 sprite and status flags (:41-47)
        objects.addDynamicObject(padB);
        time = 0x78;                                                    // move.w #$78,obj.var_2e(a0) (:65)
        initialised = true;
    }

    /** SeesawObject_0_Routine2 (:70-97). */
    private void main() {
        if (ySpeed == 0) {
            animateTilt();
        } else {
            applyTiltMotion();
        }
        boolean stoodOnA = false;
        Pad first = standingPad;
        if (first == padA) {
            stoodOnA = topSolid(padA);
            if (!stoodOnA) topSolid(padB);
        } else if (first == padB) {
            if (!topSolid(padB)) stoodOnA = topSolid(padA);
        } else {
            if (!topSolid(padB)) {                                      // TopSolidObject for var_2c, then var_2a (:73-90)
                stoodOnA = topSolid(padA);                              // sne obj.var_3f(a0)
            }
        }
        if (stoodOnA) {                                                 // SeesawStartReset (:99-104)
            routine = ROUTINE_PUSH_UP;
            time = 3;
            mapFrame = 8;
            return;
        }
        animate();                                                      // lea of the animation table; jmp AnimateObject
    }

    /** SeesawAnimateTilt (:107-124). */
    private void animateTilt() {
        if (time < 0) return;                                           // tst.w obj.var_2e(a0); bmi.s
        time--;
        if (time < 0) {
            ySpeed = 0x100;                                             // SeesawSetTiltVelocity
            animId = (animId + 1) & 0xFF;                               // SeesawAdvanceAnimation
        } else if (time == 0x3C) {
            animId = (animId + 1) & 0xFF;
        }
    }

    /** SeesawApplyTiltMotion (:129-153) and SeesawCorrectGroundPenetration (:156-165). */
    private void applyTiltMotion() {
        int step = (ySpeed >> 8) & 0xFF;                                // move.b obj.y_speed(a0),d0 reads the word's high byte
        y = (y + step) & 0xFFFF;
        padA.addY(step);
        padB.addY(step);
        int d3 = (short) padB.posX();
        d3 += (d3 >= (short) x) ? padB.halfWidth() : -padB.halfWidth();   // move.b obj.width_2(a2),d0; cmp.w obj.x(a0),d3; blt.s; neg.w
        int d1 = ObjectTerrainUtils.checkFloorDist(d3, padB.posY(), padB.radiusY()).distance();   // CheckBlockDown2 with a0 = the pad
        if (d1 < 0) {
            y = (y + d1) & 0xFFFF;
            padA.addY(d1);
            padB.addY(d1);
            ySpeed = 0;
        }
    }

    /** SeesawObject_0_Routine4 (:167-185). */
    private void pushUp() {
        padB.addY(-0x18);
        y = (y - 0xC) & 0xFFFF;
        time--;
        if (time < 0) reset();                                          // subq.w #1,obj.var_2e(a0); bpl.s; bsr.s SeesawReset
    }

    /** SeesawReset (:187-203). */
    private void reset() {
        Pad oldB = padB;
        routine = ROUTINE_MAIN;
        ySpeed = 0;
        time = 0x78;
        padB = padA;
        padA = oldB;
        animId = animId > 2 ? 0 : 3;                                    // cmpi.b #2,obj.anim_id(a0); bgt.s
        restartAnimation = true;                                        // move.b #$FF,obj.prev_anim_id(a0)
    }

    private boolean restartAnimation;

    /** AnimateObject over the stage's animation table (SeesawObject_0_Routine2, :96-97). */
    private void animate() {
        if (animator == null) {
            animator = animator(e -> SharedObjectCatalogue.offset(artProvider().payload(), "wwzSeesawAnims"), animId);
            if (animator == null) return;
        }
        if (restartAnimation) {
            animator.restart();
            restartAnimation = false;
        }
        animator.anim = animId;
        animator.step();
        animId = animator.anim;
        mapFrame = animator.mapFrame();
        // AnimateObject: sprite_flags = (sprite_flags & ~3) | (status & 3) ^ the script's flip bits.
        scriptFlip = animator.flipBits();
    }

    private int scriptFlip;

    /** {@code TopSolidObject} with the pad as the object; the result is d0 (a top contact). */
    private boolean topSolid(Pad pad) {
        int savedX = x, savedY = y, savedWidth = width, savedRadius = yRadius;
        x = pad.posX();
        y = pad.posY();
        width = pad.halfWidth();
        yRadius = pad.radiusY();
        activePad = pad;
        try {
            boolean contact = solidObject(ReevaluatingSolidObject.Entry.TOP);
            if (contact) standingPad = pad;
            else if (standingPad == pad) standingPad = null;
            return contact;
        } finally {
            x = savedX;
            y = savedY;
            width = savedWidth;
            yRadius = savedRadius;
            activePad = null;
        }
    }

    /** {@code tst.b obj.sprite_flags(a0)}: the pad's, while a pad is the object of the call. */
    @Override
    public boolean solidRenderedOnScreen() {
        return activePad != null ? activePad.onScreen() : super.solidRenderedOnScreen();
    }

    /** SeesawObject's CheckObjectDespawn (:10-11). */
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        int flip = (spriteFlip ^ scriptFlip) & 3;
        drawFrame(SharedObjectArt.WWZ_SEESAW, mapFrame, (flip & 1) != 0, (flip & 2) != 0);
    }

    private int priorityOf() { return priority; }
    private int tileWordOf() { return tileWord; }

    /** The pads (SeesawInitializeChild, :51-66): idle, they follow the body's writes and go when it does (:205-213). */
    public static final class Pad extends CdObject implements ObjectVisualFacing.NonDirectional {
        private final ObjWwzSeesaw body;
    /** The slot of {@code body} the native child stores and tests each turn ({@code cmpi.b #$2C,obj.id(a1)}). */
    private int bodySlot = -1;
        private final int flip;

        Pad(ObjWwzSeesaw body, int x, int y, int flip) {
            super(new ObjectSpawn(x, y, 0x2C, 0xFF, flip, false, 0), "Seesaw pad");
            this.body = body;
        this.bodySlot = body == null ? -1 : body.getSlotIndex();
            this.flip = flip;
            this.x = x;
            this.y = y;
            this.priority = body.priorityOf();
            this.tileWord = body.tileWordOf();
            this.width = 0x10;
            this.yRadius = 8;
        }

        boolean onScreen() { return renderFlagOnScreen(); }
        int posX() { return x; }
        int posY() { return y; }
        int halfWidth() { return width; }
        int radiusY() { return yRadius; }
        /** {@code add.w d0,obj.y(a1)}. */
        void addY(int delta) { y = (y + delta) & 0xFFFF; }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (!slotHoldsId(bodySlot, 0x2C)) setDestroyed(true);                // cmpi.b #$2C,obj.id(a1); bne.s SeesawDelete
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawFrame(SharedObjectArt.WWZ_SEESAW, 9, (flip & 1) != 0, (flip & 2) != 0);
        }
    }
}
