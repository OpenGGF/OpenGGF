package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Taga-Taga, object $2D ({@code TagaTagaObject}, objects/r4/taga_taga.asm:3-263): a collision-$2D badnik (tile $23D0, priority 3,
 * $14 wide, 8 tall) that paces along X at {@code -$10000} (subtype 0) or {@code -$8000} (subtype 1, smaller sprites,
 * {@code $190} frames a leg instead of {@code $C8}) 16.16 a frame, turning when its timer runs out. A subtype 0 one carries three
 * missiles (the same ID with subtype {@code $FF}, {@link Missile}) that it spawns at its init and moves along with itself; when
 * the player is within $38 above it and $28-$A0 in front, it waits $3C frames (routine 4, sound $B3 on screen), throws the
 * missiles up and apart (routine 4's second half) and after another $3C frames charges away at {@code $40000} (subtype 1:
 * {@code $18000}) in its facing direction until it despawns. Every frame ends in {@code DrawObject} and CheckObjectDespawn on the
 * current X, after {@code DestroyInGoodFuture}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjTagaTaga extends TtzBadnik {
    /** The sound the wind-up plays on screen: move.w #$B3,d0; jsr PlayFmSound (Routine4, :166-168). */
    private static final int SFX_WIND_UP = 0xB3;

    private final boolean small;       // subtype != 0
    private int velocity;              // oVar2C
    private int timer;                 // oVar2A
    private int leg;                   // oVar36
    private Missile[] missiles;        // oVar30, oVar32, oVar34

    public ObjTagaTaga(ObjectSpawn spawn) {
        super(spawn, "TagaTaga");
        this.small = spawn.subtype() != 0;
    }

    @Override protected String sheetKind() { return small ? TtzObjectArt.TAGA2 : TtzObjectArt.TAGA1; }

    @Override
    protected void run(PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> patrol(player);
            case 4 -> windUp();
            case 6 -> pause();
            case 8 -> addToX(velocity);                                        // TagaTagaObject_0_Routine8
            default -> { }
        }
    }

    /** TagaTagaObject_0_Routine0 (objects/r4/taga_taga.asm:36-96). */
    private void init() {
        priority = 3;
        tileWord = TtzObjectArt.TAGA_TILE;
        width = 0x14;
        yRadius = 8;
        colType = 0x2D;
        routine = 2;
        velocity = -0x10000;
        leg = 0xC8;
        initialised = true;
        animator = TtzObjectArt.animator(artProvider(), "tagaAnimations", 0);   // oAnim 0: AnimateObject over TagaTagaAnims
        if (small) {
            velocity = -0x8000;
            leg = 0x190;
            return;
        }
        missiles = new Missile[3];
        for (int i = 0; i < 3; i++) {
            missiles[i] = new Missile(this, x, y);
            if (!spawnChild(missiles[i])) {                                   // bne.w loc_20DE8A: DeleteObject
                setDestroyed(true);
                return;
            }
        }
        // sub_20DBB8 positions each missile on the badnik; then the offsets (:73-90).
        missiles[0].place(x + 2, y - 0xA);
        missiles[1].place(x - 3, y - 0xA);
        missiles[2].place(x - 7, y - 9);
    }

    /** TagaTagaObject_0_Routine2 (objects/r4/taga_taga.asm:112-178). */
    private void patrol(PlayableEntity player) {
        if (!small && playerInFront(player)) {                                // sub_20DCA2; bcs.w loc_20DCD0
            routine = 4;
            timer = 0x3C;
            return;
        }
        addToX(velocity);
        if (missiles != null) for (Missile m : missiles) m.addToX(velocity);
        timer = (timer - 1) & 0xFFFF;                                         // addi.w #-1,obj.var_2a(a0); bpl.w
        if ((short) timer < 0) {
            timer = leg;
            velocity = -velocity;
            flipHorizontally();                                              // bchg #0 on oSprFlags and oFlags
            if (missiles != null) {
                for (Missile m : missiles) m.placeX(x);
                int d1 = -2, d2 = 3, d3 = 7;
                if ((spriteFlip & 1) != 0) {
                    d1 = -d1 - 3;
                    d2 = -d2 - 3;
                    d3 = -d3 - 3;
                }
                missiles[0].placeX(missiles[0].getX() + d1);
                missiles[1].placeX(missiles[1].getX() + d2);
                missiles[2].placeX(missiles[2].getX() + d3);
            }
        }
        animate();                                                            // loc_20DC98
    }

    /**
     * sub_20DCA2 (:180-199), its carry: the player is above the badnik and at most $38 higher
     * ({@code (player.y - y + $38) mod 2^16 < $38}) and {@code $28 <= d < $A0} with {@code d = player.x - x}, negated when the
     * badnik faces left.
     */
    private boolean playerInFront(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return false;
        int dy = ((sprite.getCentreY() - y) + 0x38) & 0xFFFF;                 // subi.w #-$38,d0
        if (dy >= 0x38) return false;                                         // subi.w #$38,d0; bcc.s: no borrow
        int dx = (sprite.getCentreX() - x) & 0xFFFF;
        if ((spriteFlip & 1) == 0) dx = (-dx) & 0xFFFF;                       // btst #0,oSprFlags; bne; neg.w d0
        return ((dx - 0x28) & 0xFFFF) < 0x78;                                 // subi.w #$28,d0; subi.w #$78,d0: carry
    }

    /** TagaTagaObject_0_Routine4 (:201-246). */
    private void windUp() {
        timer = (timer - 1) & 0xFFFF;
        if ((short) timer >= 0) return;                                       // bpl.w locret_20DD80
        if (renderFlagOnScreen()) services().playSfx(SFX_WIND_UP);
        timer = 0x3C;
        routine = 6;
        if (small) return;
        // The missiles go up and apart: x speeds -$20000, +$10000, +$20000, y speed -$30000, gravity $2000.
        int[] sideways = {-0x20000, 0x10000, 0x20000};
        for (int i = 0; i < 3; i++) missiles[i].launch(sideways[i]);
    }

    /** TagaTagaObject_0_Routine6 (:248-262). */
    private void pause() {
        timer = (timer - 1) & 0xFFFF;
        if ((short) timer >= 0) return;
        routine = 8;
        velocity = small ? 0x18000 : 0x40000;
        if ((spriteFlip & 1) == 0) velocity = -velocity;
    }

    /** A missile: the same ID with subtype $FF ({@code TagaMissileObject}, :269-309). */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    public static final class Missile extends TtzObject implements ObjectVisualFacing.NonDirectional {
        private final ObjTagaTaga parent;
        /** The badnik's slot (oVar2E): the missile tests the ID byte there each turn. */
        private final int parentSlot;
        private int routine;
        private int xFixed;
        private int yFixed;
        private int xSpeed;            // oVar2A
        private int ySpeed;            // oVar2E
        /** oVar32 = 0 and oVar36 = $2000 (Routine4, :222-224). */
        private static final int X_ACCEL = 0, Y_ACCEL = 0x2000;

        Missile(ObjTagaTaga parent, int x, int y) {
            super(childSpawn(x, y, 0x2D, 0xFF, 0), "TagaMissile");
            this.parent = parent;
            this.parentSlot = parent.getSlotIndex();
            this.xFixed = x << 16;
            this.yFixed = y << 16;
            this.priority = 3;
            this.width = 2;
            this.yRadius = 2;
            this.tileWord = TtzObjectArt.TAGA_TILE;
        }

        void place(int px, int py) {
            xFixed = (px & 0xFFFF) << 16 | (xFixed & 0xFFFF);
            yFixed = (py & 0xFFFF) << 16 | (yFixed & 0xFFFF);
            x = px & 0xFFFF;
            y = py & 0xFFFF;
        }

        void placeX(int px) {
            xFixed = (px & 0xFFFF) << 16 | (xFixed & 0xFFFF);
            x = px & 0xFFFF;
        }

        void addToX(int velocity) {
            xFixed += velocity;
            x = xFixed >>> 16;
        }

        /** The parent's launch: {@code addq.b #2,obj.routine(a1)} with the speeds written (Routine4, :219-244). */
        void launch(int sideways) {
            xSpeed = sideways;
            ySpeed = -0x30000;
            routine += 2;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (!slotHoldsId(parentSlot, 0x2D)) {                             // cmpi.b #$2D,obj.id(a1); bne.w loc_20DE8A
                setDestroyed(true);
                return;
            }
            switch (routine) {
                case 0 -> routine = 2;                                        // TagaMissileObject_0_Routine0, then DrawObject
                case 2 -> { }
                default -> fly(player);
            }
        }

        /** TagaMissileObject_0_Routine4 (:288-309). */
        private void fly(PlayableEntity player) {
            xFixed += xSpeed;
            yFixed += ySpeed;
            xSpeed += X_ACCEL;
            ySpeed += Y_ACCEL;
            x = xFixed >>> 16;
            y = yFixed >>> 16;
            if (player != null && (short) (y - player.getCentreY()) >= 0xE0) setDestroyed(true);   // cmpi.w #$E0,d0; bge
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (routine == 0) return;
            drawSheet(TtzObjectArt.TAGA_MISSILE, 0, false, false);
        }
    }
}
