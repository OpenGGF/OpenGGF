package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Semi, object $32 ({@code ObjSemi}, Level/Wacky Workbench/Objects/Semi/Main.asm:14-187): a flying badnik that
 * waits {@code oSubtype2 + 1} frames, then until the player is within 96 pixels vertically and 120
 * horizontally, swoops at the player for 96 frames (damaged: 42, slower) and then patrols, dropping a bomb
 * every 64 frames while the player is still in range. The bomb (secondary subtype -1,
 * {@code ObjSemiBomb}, :212-293) falls, lands, waits 120 frames, flashes for 120 frames and turns into
 * an explosion in its slot. Act 3's index leaves $32 to {@code NullObject}.
 */
public final class ObjSemi extends WwzBadnik {
    private static final int WAIT = 2, WAIT_PLAYER = 4, START_MOVE = 6, MOVE = 8, START_ATTACK = 0xA, ATTACK = 0xC;

    private final boolean damaged;
    private int xVel;
    private int yVel;
    private int time;
    private int playerX;
    private CdAnimator animator;
    private boolean initialised;

    public ObjSemi(ObjectSpawn spawn) {
        super(spawn, "Semi");
        this.damaged = spawn.subtype() != 0;
    }

    @Override
    protected void run(PlayableEntity player) {
        AbstractPlayableSprite sprite = sprite(player);
        switch (routine) {
            case 0 -> init();
            case WAIT -> {
                time = (time - 1) & 0xFFFF;                      // subq.w #1,oSemiTime / bpl.s .End
                if ((time & 0x8000) != 0) routine += 2;
            }
            case WAIT_PLAYER -> {
                if (sprite != null && checkPlayer(sprite)) routine += 2;
            }
            case START_MOVE -> {
                startMove();
                move();
            }
            case MOVE -> move();
            case START_ATTACK -> {
                startAttack();
                attack(sprite);
            }
            case ATTACK -> attack(sprite);
            default -> { }
        }
    }

    /** ObjSemi_Init (:41-58). */
    private void init() {
        routine = WAIT;
        priority = 1;
        yRadius = 16;
        xRadius = 19;
        width = 19;
        tileWord = WwzObjectArt.SEMI_TILE;
        colType = 0x36;
        time = subtype2() & 0xFF;                                // move.b oSubtype2(a0),oSemiTime+1(a0)
        initialised = true;
    }

    /** ObjSemi_CheckPlayer (:83-96): carry set when the player is within range; {@code oSemiPlayerX} is stored on the way. */
    private boolean checkPlayer(AbstractPlayableSprite player) {
        int dy = (player.getCentreY() - y + 96) & 0xFFFF;         // subi.w #-96,d0
        if (dy >= 192) return false;                              // subi.w #192,d0 / bcc.s .End
        int dx = (player.getCentreX() - x) & 0xFFFF;
        playerX = (short) dx;                                     // move.w d0,oSemiPlayerX(a0)
        return ((dx + 120) & 0xFFFF) < 240;                       // subi.w #-120,d0 / subi.w #240,d0
    }

    /** ObjSemi_StartMove (:101-120). */
    private void startMove() {
        routine = MOVE;
        int dx = damaged ? 0xC000 : 0x10000;
        yVel = damaged ? 0x6000 : -0x8000;
        time = damaged ? 42 : 96;
        xVel = playerX < 0 ? dx : -dx;                            // tst.w oSemiPlayerX / bmi.s / neg.l d0
    }

    /** ObjSemi_Move (:124-136). */
    private void move() {
        time = (time - 1) & 0xFFFF;
        if ((time & 0x8000) == 0) {                               // bpl.s .Move
            // still moving
        } else {
            routine += 2;
        }
        addToX(xVel);
        addToY(yVel);
        animate();
    }

    /** ObjSemi_StartAttack (:140-154). */
    private void startAttack() {
        routine = ATTACK;
        time = 0;
        int dx = damaged ? 0xC000 : 0x10000;
        xVel = playerX < 0 ? dx : -dx;
    }

    /** ObjSemi_Attack (:158-187). */
    private void attack(AbstractPlayableSprite player) {
        if (!damaged) {
            time &= 0x3F;                                         // andi.w #$3F,oSemiTime(a0)
            if (time == 0 && player != null && checkPlayer(player)) {
                var objects = services().objectManager();
                if (objects != null) objects.addDynamicObject(new ObjSemiBomb(this));
            }
            time = (time + 1) & 0xFFFF;
        }
        addToX(xVel);
        animate();
    }

    private void animate() {
        if (animator == null) animator = WwzObjectArt.animator(artProvider(), "semiAnimations", 0);
        if (animator == null) return;
        animator.step();
        mapFrame = animator.mapFrame();
        spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
    }

    @Override
    protected String sheetKey() {
        return initialised ? WwzObjectArt.key(damaged ? WwzObjectArt.SEMI_DAMAGED : WwzObjectArt.SEMI) : null;
    }

    /** The bomb (:212-293): hazard collision $B7, a $6-radius ball. */
    static final class ObjSemiBomb extends WwzObject
            implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
        private static final int FALL = 2, WAIT = 4, DETONATE = 6, EXPLODE = 8;
        private int yVel;
        private int time;
        private int colType;
        private CdAnimator animator;
        private boolean initialised;

        ObjSemiBomb(ObjSemi parent) {
            super(new ObjectSpawn(parent.x, (parent.y + 10) & 0xFFFF, 0x32, 0, 0, false, 0), "SemiBomb");
            // move.l oX(a0),oX(a1) / move.l oY(a0),oY(a1) / addi.w #10,oY(a1) (:171-173): the subpixels come too.
            this.xSub = parent.xSub;
            this.ySub = parent.ySub;
            this.spriteFlip = parent.spriteFlip;
            this.priority = parent.priority + 1;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            switch (routine) {
                case 0 -> init();
                case FALL -> fall();
                case WAIT -> {
                    time = (time - 1) & 0xFFFF;
                    if ((time & 0x8000) != 0) {
                        routine += 2;
                        time = 120;
                    }
                }
                case DETONATE -> {
                    time = (time - 1) & 0xFFFF;
                    if ((time & 0x8000) != 0) routine += 2;
                    animate();
                }
                case EXPLODE -> explode();
                default -> { }
            }
        }

        /** ObjSemiBomb_Init (:232-240). */
        private void init() {
            routine = FALL;
            colType = 0xB7;
            yRadius = 6;
            width = 6;
            tileWord = WwzObjectArt.SEMI_BOMB_TILE;
            yVel = 0x8000;
            initialised = true;
        }

        /** ObjSemiBomb_Fall (:244-257). */
        private void fall() {
            addToY(yVel);
            yVel += 0x4000;
            int floor = ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
            if (floor >= 0) return;
            routine += 2;
            y = (y + floor) & 0xFFFF;
            time = 120;
        }

        private void animate() {
            if (animator == null) animator = WwzObjectArt.animator(artProvider(), "semiBombAnimations", 0);
            if (animator == null) return;
            animator.step();
            mapFrame = animator.mapFrame();
        }

        /**
         * ObjSemiBomb_Explode (:283-293): the slot becomes explosion $18 with {@code oExplodeBadnik} set
         * (no points, no flower); {@code oSubtype} is 0 and {@code oLayer} the $FF this bomb was given.
         */
        private void explode() {
            if (renderFlagOnScreen()) services().playSfx(WwzSounds.EXPLODE);
            becomeExplosion(0, 0xFF, 0, false);
        }

        @Override public int getCollisionFlags() { return initialised ? colType : 0; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
        @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

        @Override protected boolean despawnsByCheckObjDespawn() { return true; }

        @Override
        protected String sheetKey() {
            return initialised ? WwzObjectArt.key(WwzObjectArt.SEMI_BOMB) : null;
        }
    }
}
