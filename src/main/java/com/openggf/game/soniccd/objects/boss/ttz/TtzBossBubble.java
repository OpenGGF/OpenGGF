package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Boss bubble, object $4F ({@code BossBubbleObject}, objects/r4/boss.asm:598-1079): the shield Eggman builds before
 * the fight. Eggman allocates sixteen at his X and Y $5B8 (and one decorative bubble first); each rises $1.8 a frame
 * swaying on its own random sine, joins Eggman when it reaches his height and circles him, and when he starts to
 * fight (routine A) the ring spreads evenly ({@code var_2d} * $1000) out to radius $30, where the bubbles become
 * {@code $FF} touches. The ring turns with Eggman ({@code var_2c} bit 3) and, each time he reverses (bit 4), shrinks
 * through his centre to the other side; after each of his dashes (bit 5) it shrinks to radius $10 and spreads again
 * over the bubbles left (word_20CDBE). A popped bubble animates and is deleted; when Eggman flees (routine $14) every
 * bubble pops.
 *
 * <p>Touch: $FF is a {@code $C0} type of size $3F. While {@code boss_started} is 4 a rolling player pops it
 * ({@link TtzBossTouch#popBubble}, which clears the type); the bubble sees the cleared type on its next frame. Only
 * one bubble pops per frame: Eggman's {@code var_2c} bit 6, cleared at the start of his frame, records the first;
 * a later one that frame takes its type back ({@code FixBugs = 0}).
 */
public final class TtzBossBubble extends TtzBossPart implements TouchResponseListener {
    /** Object $4F: BossBubbleObject is index entry $4E (r4/object_index_3.asm:3-80). */
    public static final int ID = 0x4F;
    /** loc_20CBCA: move.b #$FF,obj.collide_type; move.b #2,obj.collide_status (objects/r4/boss.asm:807-808). */
    static final int COLLISION = 0xFF;
    /** var_2c bit 1: the bubble has been made touchable once. */
    private static final int TOUCHABLE = 0x02;

    /** var_2e: Eggman. */
    TtzBossEggman eggman;
    /** var_2d: the bubble's place in the ring. */
    int index;
    /** routine_2: non-zero for the decorative bubble; in routine A the shrink's half. */
    int routine2;
    private int bits;
    /** var_34: the ring angle (the high byte is SineCosine's); var_36: the sway step. */
    private int angle;
    private int sway;
    /** var_38 / var_3a: the last offset from the centre. */
    private int offsetX;
    private int offsetY;
    /** var_3c (with var_3e) and x_speed (with y_speed): the X and Y radii, 16.16. */
    private long radiusX;
    private long radiusY;

    TtzBossBubble(int x, int y, TtzBossEggman eggman) {
        super(ID, x, y, "TtzBossBubble");
        this.eggman = eggman;
    }

    private TtzBossBubble(ObjectSpawn spawn) {
        super(spawn, "TtzBossBubble");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        hidden = false;
        switch (routine) {
            case 0 -> { init(); rise(); }                               // Routine0 falls into Routine2
            case 2 -> rise();
            case 4 -> follow();
            case 6 -> spread();
            case 8 -> ring();
            case 0xA -> turn();
            case 0xC -> animateObject("bubbleAnimations");              // BossBubbleObject_0_RoutineC (:1048-1050)
            case 0xE -> deleteSelf();
            case 0x10 -> regroup();
            default -> { }
        }
    }

    /** BossBubbleObject_0_Routine0 (objects/r4/boss.asm:620-650). */
    private void init() {
        routine = 2;
        priority = 3;
        width = 0x10;
        yRadius = 0x10;
        tileWord = TtzBossArt.BOSS_TILE;
        var rng = services().rng();
        int swayRemainder = (short) rng.nextWord() % 0x500;             // andi.l #$FFFF; ext.l; divs.w #$500; swap
        sway = (swayRemainder < 0 ? swayRemainder - 0x80 : swayRemainder + 0x80) & 0xFFFF;
        int radius = (rng.nextWord() % 0x10) + 0xA;                     // divu.w #$10; swap; addi.w #$A
        radiusX = withWord(radiusX, radius);
    }

    /** BossBubbleObject_0_Routine2 (objects/r4/boss.asm:652-713). */
    private void rise() {
        addYLong(-0x18000);
        if (routine2 != 0) {
            if (yWord() < 0x470) {                                      // cmpi.w #$470,obj.y; bge.s loc_20CA68
                deleteSelf();
                return;
            }
        } else if (eggman != null && eggman.yWord() >= yWord()) {     // cmp.w obj.y(a0),d0; bge.w loc_20CA72
            join();
            return;
        }
        angle = (angle + sway) & 0xFFFF;                                // loc_20CA68
        addX(-offsetX);                                                 // loc_20CAB6: sub.w var_38,obj.x
        store(orbit(angle, word(radiusX), word(radiusY)));
        if (animFrame == 4) {                                           // cmpi.b #4,obj.anim_index
            anim = 1;
            animFrame = 0;
        }
        animateObject("bubbleAnimations");
    }

    /** loc_20CA72 (objects/r4/boss.asm:675-697): the bubble reaches Eggman's height. */
    private void join() {
        int d = (short) (x - eggman.getX());
        angle = d < 0 ? 0 : 0x8000;
        radiusX = withWord(radiusX, d);
        radiusY = withWord(radiusY, d);
        offsetX = -d & 0xFFFF;
        offsetY = 0;
        setXWord(eggman.getX());
        setYWord(eggman.getY());
        routine = 4;
        anim = 2;
    }

    /** BossBubbleObject_0_Routine4 (objects/r4/boss.asm:717-744). */
    private void follow() {
        if (eggman == null) return;
        setXWord(eggman.getX());
        setYWord(eggman.getY());
        store(orbit(angle, word(radiusX), word(radiusY)));
        int step = (short) sway;
        if (step >= 0) step = -step;                                    // bmi.s; neg.w d0
        angle = (angle + step) & 0xFFFF;
        if (eggman.routine() != 0xA) return;
        offsetX = 0;
        offsetY = 0;
        routine = 6;
        setXLong(eggman.xLong());
        setYLong(eggman.yLong());
        angle = (index * 0x1000) & 0xFFFF;                              // mulu.w #$1000,d0; move.w d0,obj.var_34
        radiusX = withWord(radiusX, 0x10);
        radiusY = withWord(radiusY, 0x10);
    }

    /** BossBubbleObject_0_Routine6 (objects/r4/boss.asm:748-813): out to radius $30, then touchable. */
    private void spread() {
        if ((bits & TOUCHABLE) != 0) popCheck();
        int reached = 0;
        centre();
        if (word(radiusX) >= 0x30) { radiusX = withWord(radiusX, 0x30); reached++; } else radiusX += 0x4000;
        if (word(radiusY) >= 0x30) { radiusY = withWord(radiusY, 0x30); reached++; } else radiusY += 0x4000;
        angle = (angle + (eggman != null && eggman.reversed() ? 0x480 : -0x480)) & 0xFFFF;
        store(orbit(angle, word(radiusX), word(radiusY)));
        if (animFrame == 2) {                                           // cmpi.b #2,obj.anim_index
            anim = 3;
            animFrame = 0;
        }
        if (reached == 2) {
            routine = 8;
            boolean was = (bits & TOUCHABLE) != 0;                      // bset #1,obj.var_2c; bne.s
            bits |= TOUCHABLE;
            if (!was) setCollision(COLLISION, 2);
        }
        animateObject("bubbleAnimations");
    }

    /** BossBubbleObject_0_Routine8 (objects/r4/boss.asm:817-850): the ring, $180 a frame. */
    private void ring() {
        centre();
        if (eggman != null && eggman.turning()) {
            routine = 0xA;
            turn();
            return;
        }
        if (eggman != null && eggman.regrouping()) {
            routine = 0x10;
            priority = 3;
            regroup();
            return;
        }
        popCheck();
        angle = (angle + (eggman != null && eggman.reversed() ? 0x180 : -0x180)) & 0xFFFF;
        store(orbit(angle, word(radiusX), word(radiusY)));
    }

    /** BossBubbleObject_0_RoutineA (objects/r4/boss.asm:854-908): the X radius shrinks through 0 to -$30. */
    private void turn() {
        popCheck();
        centre();
        radiusX -= 0x10000;
        if (routine2 == 0) {
            if (radiusX < 0) routine2 = (routine2 + 1) & 0xFF;          // subi.l #$10000; bge.w loc_20CCAA
            turnStep();
            return;
        }
        if (radiusX > -0x300000) {                                      // cmpi.l #-$300000; bgt.s loc_20CCAA
            turnStep();
            return;
        }
        radiusX = 0x300000;
        routine = 8;
        angle = (0x8000 - angle) & 0xFFFF;                              // loc_20CCEC: every quadrant writes $8000 - d0
        turnStep();
        routine2 = 0;
    }

    /** loc_20CCAA (objects/r4/boss.asm:881-908): $480 a frame; the near half draws in front (priority 3), the far behind (5). */
    private void turnStep() {
        boolean reversed = eggman != null && eggman.reversed();
        angle = (angle + (reversed ? 0x480 : -0x480)) & 0xFFFF;
        int side = ((angle >> 8) - 0x40) & 0xFF;                        // move.b obj.var_34,d0; subi.b #$40,d0
        if (reversed) side ^= 0x80;
        priority = (side & 0x80) == 0 ? 3 : 5;
        store(orbit(angle, word(radiusX), word(radiusY)));
    }

    /** BossBubbleObject_0_Routine10 (objects/r4/boss.asm:957-992): shrink to radius $10, then spread over the bubbles left. */
    private void regroup() {
        popCheck();
        centre();
        radiusX -= 0x10000;
        radiusY -= 0x10000;
        if (radiusX <= 0x100000 && eggman != null) {                    // cmpi.l #$100000,obj.var_3c; bgt.s loc_20CDA0
            CdStageImage image = stageImage();
            int step = image == null ? 0 : TtzBossCatalogue.ringSpacing(image, payload(), eggman.bubbles);
            angle = (eggman.regroupCount * step) & 0xFFFF;              // mulu.w d0,d1; move.w d1,obj.var_34
            eggman.regroupCount = (eggman.regroupCount + 1) & 0xFF;
            radiusX = 0x100000;
            radiusY = 0x100000;
            routine = 6;
        }
        popCheck();                                                     // loc_20CDA0: bsr.w loc_20CDE0
        angle = (angle + (eggman != null && eggman.reversed() ? 0x480 : -0x480)) & 0xFFFF;
        store(orbit(angle, word(radiusX), word(radiusY)));
    }

    /**
     * loc_20CDE0 and loc_20CE0C (objects/r4/boss.asm:1017-1044): when Eggman flees, or the player cleared the type
     * and no other bubble popped this frame, the bubble pops (routine C, pose 4) and Eggman counts one bubble
     * fewer; a second popped bubble the same frame takes its type back.
     */
    private void popCheck() {
        if (eggman == null) return;
        if (eggman.routine() != 0x14) {
            if (colType != 0) return;
            if (eggman.markPopped()) {                                  // bset #6,obj.var_2c(a1); beq.s loc_20CE0C
                setCollision(COLLISION, 2);
                return;
            }
        }
        routine = 0xC;
        restartAnimation(4);
        eggman.bubbles = (eggman.bubbles - 1) & 0xFF;                   // subq.b #1,obj.var_2d(a1)
        // bra.w *+4 (objects/r4/boss.asm:1044) lands on BossBubbleObject_0_RoutineC (:1048-1050): the pop's first
        // AnimateObject runs on the pop frame, then the calling routine goes on.
        animateObject("bubbleAnimations");
    }

    /** {@code move.l obj.x(a1),obj.x(a0); move.l obj.y(a1),obj.y(a0)}: centre on Eggman. */
    private void centre() {
        if (eggman == null) return;
        setXLong(eggman.xLong());
        setYLong(eggman.yLong());
    }

    private void store(int[] offset) {
        offsetX = offset[0];
        offsetY = offset[1];
    }

    /** BossBubbleObject_0_RoutineE (objects/r4/boss.asm:1054-1056): addq.l #4,sp; jmp DeleteObject. */
    private void deleteSelf() {
        hidden = true;
        deleteObject();
    }

    /** {@code move.w obj.var_3c(a0),d3}: the pixel word of a 16.16 radius. */
    static int word(long fixed) { return (short) (fixed >> 16); }

    /** {@code move.w #n,obj.var_3c(a0)}: the pixel word, keeping the fraction. */
    static long withWord(long fixed, int value) {
        return ((long) (short) value << 16) | (fixed & 0xFFFF);
    }

    /** loc_2074B6 (r4/player_object_collide.asm:362-382): a rolling player pops it; others pass through. */
    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (player instanceof AbstractPlayableSprite sprite && TtzBossTouch.popBubble(sprite, this)) colType = 0;
    }

    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return true; }

    /** Routines 4, 8, A and $10 skip AnimateObject; every routine but E draws. */
    @Override protected boolean drawn() { return !hidden && routine != 0; }

    @Override String sheet() { return TtzBossArt.BUBBLE; }

    @Override
    protected TtzBossBubble shell(ObjectSpawn spawn) {
        return new TtzBossBubble(spawn);
    }
}
