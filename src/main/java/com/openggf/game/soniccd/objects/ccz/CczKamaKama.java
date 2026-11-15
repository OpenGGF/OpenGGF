package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * KamaKama, object $24 ({@code ObjKamaKama}, Level/Collision Chaos/Objects/KamaKama/Main.asm:34-244; legacy ABI), a
 * mantis badnik (collision 6, tile {@code $23F2}, priority 3, width $10, Y radius $14). It falls ($100 per frame in
 * {@code oYVel}, which is never cleared: the walk keeps adding it and the floor probe corrects the Y each frame) onto the
 * floor, watches while on screen and, when the player is within $20 vertically and $60 horizontally, throws two
 * sickles ({@link Sickle}; a subtype with bit 7 set, the second sheet, throws none), waits $78 + $3C frames, then
 * walks ($100, or $80 for the second sheet) until a wall or a ledge/step of 7 or more pixels, stands $48 frames and turns.
 * Deleted by DestroyOnGoodFuture and CheckObjectDespawn.
 *
 * <p>The wall probes are {@code PlayerCheckBlockLeft}/{@code PlayerCheckBlockRight}: the object's X $A pixels to the side
 * (common/object_block_collide.asm:366-380).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczKamaKama extends CczBadnik {
    private int timer;               // oVar2C
    private int xVel;
    private int yVel;
    private boolean secondSheet;

    public CczKamaKama(ObjectSpawn spawn) {
        super(spawn, "KamaKama");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (destroyOnGoodFuture()) return;
        switch (routine) {
            case 0 -> {
                init();
                fallToFloor();
            }
            case 2 -> fallToFloor();
            case 4 -> watchPlayer(player);
            case 6 -> wait(false);
            case 8 -> wait(true);
            case 10 -> walk();
            default -> turnWait();
        }
    }

    /** ObjKamaKama_Init (:44-62). */
    private void init() {
        secondSheet = (byte) spawn.subtype() < 0;
        mapFrame = secondSheet ? 2 : 1;
        spriteFlip = 0;                                                    // move.b #4,oSprFlags(a0)
        priority = 3;
        width = 0x10;
        yRadius = 0x14;
        tileWord = CczObjectArt.KAMA_TILE;
        yVel = 0x100;
        colType = 6;
        routine = 2;
    }

    private void ensureAnimator() {
        if (animator == null) animator = CczObjectArt.animator(provider(), "kamaKamaAnimations", 0);
    }

    /** ObjKamaKama_AnimateLegacy (:72-75). */
    private void animateLegacy() {
        ensureAnimator();
        animate();
    }

    private void setAnim(int anim) {
        ensureAnimator();
        if (animator != null) animator.anim = anim;
    }

    /** ObjKamaKama_Move (:229-243). */
    private void move() {
        moveX(xVel);
        moveY(yVel);
    }

    private int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();     // KamaKamaGetFloorDist = CheckBlockDown
    }

    /** ObjKamaKama_FallToFloor (:64-71). */
    private void fallToFloor() {
        move();
        if (floorDistance() < 0) routine += 2;
        animateLegacy();
    }

    /** ObjKamaKama_WatchPlayer (:77-117). */
    private void watchPlayer(PlayableEntity player) {
        if (renderFlagOnScreen() && player instanceof AbstractPlayableSprite sprite) {
            setFacingRight(false);                                         // bclr #0,oSprFlags / oFlags
            int d0 = (short) (x - sprite.getCentreX());
            if (d0 < 0) {                                                  // sub.w oX(a1),d0; bcc .CheckVertical
                d0 = -d0;
                setFacingRight(true);
            }
            int d1 = Math.abs((short) (y - sprite.getCentreY()));
            if (d1 < 0x20 && (d0 & 0xFFFF) < 0x60) {
                setAnim(2);
                spawnSickles();
                timer = 0x78;
                routine += 2;
                animateLegacy();
                return;
            }
        }
        setAnim(1);                                                        // .NoTargetLegacy
        animateLegacy();
    }

    /** ObjKamaKama_AttackWait and _RecoveryWait (:119-140). */
    private void wait(boolean recovery) {
        timer = (timer - 1) & 0xFFFF;
        if (timer != 0) {
            animateLegacy();
            return;
        }
        if (!recovery) {
            setAnim(3);
            timer = 0x3C;
            routine += 2;
            animateLegacy();
        } else {
            routine += 2;
            walk();                                                        // falls into ObjKamaKama_Walk
        }
    }

    /** ObjKamaKama_Walk (:142-177). */
    private void walk() {
        int speed = secondSheet ? 0x80 : 0x100;
        setAnim(0);
        xVel = (statusFlip & 1) != 0 ? speed : -speed;
        move();
        // Player_GetLWallDist flips the probe X itself (subi.w #$A; eori.w #$F, Level/Collision Check.asm:388-390);
        // Sonic CD's object left-wall probe passes the X it is given.
        if (ObjectTerrainUtils.checkLeftWallDist((x - 0xA) ^ 0xF, y).distance() < 0
                || ObjectTerrainUtils.checkRightWallDist(x + 0xA, y).distance() < 0) {
            hitWall();
            return;
        }
        int d1 = floorDistance();
        if (d1 != 0) {
            if (d1 >= 7 || d1 <= -7) {                                     // cmpi.w #7,d1; bpl; cmpi.w #-7,d1; bmi
                hitWall();
                return;
            }
            y = (y + d1) & 0xFFFF;
        }
        animateLegacy();
    }

    /** .HitWall (:171-177): {@code move.w #3,oAnim(a0)} writes oAnim 0 and oPrevAnim 3. */
    private void hitWall() {
        timer = 0x48;
        setAnim(0);
        animator.restart();
        routine += 2;
        animateLegacy();
    }

    /** ObjKamaKama_TurnWait (:179-188). */
    private void turnWait() {
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            flipHorizontally();
            routine -= 2;
        }
        animateLegacy();
    }

    /** ObjKamaKama_SpawnSickles (:190-228): two sickles from a positive subtype. */
    private void spawnSickles() {
        if ((byte) spawn.subtype() < 0) return;
        var objects = services().objectManager();
        if (objects == null) return;
        boolean right = (spriteFlip & 1) != 0;
        objects.addDynamicObject(new Sickle(new ObjectSpawn(x + (right ? 0x11 : -0x11), y - 4, 0x25,
                spawn.subtype(), 0, false, 0), this, 0x40, spriteFlip));
        objects.addDynamicObject(new Sickle(new ObjectSpawn(x + (right ? 9 : -9), y - 6, 0x25,
                spawn.subtype(), 0, false, 0), this, 0x14, spriteFlip));
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, secondSheet ? CczObjectArt.KAMA_KAMA2 : CczObjectArt.KAMA_KAMA1,
                tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }

    public int timer() { return timer; }
    public int xVelocity() { return xVel; }
    public int yVelocity() { return yVel; }

    /**
     * KamaKama's sickle, object $25 ({@code ObjKamaSickle}, :246-342). It waits {@code launchDelay} frames beside the
     * mantis (deleted if the parent's slot stops being $24), then flies at $300 in the parent's facing direction with
     * hurt collision {@code $87} for $3C frames. While it flies, a rolling (or invincible) player inside a $1C box deflects it:
     * collision off, bounce at -$800 with gravity $40, and the player's X speed added.
     */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    public static final class Sickle extends CczObject implements TouchResponseProvider,
            ObjectVisualFacing.NonDirectional {
        /** move.w #$B9,d0; jsr PlayFMSound (KamaKama/Main.asm:300-302). */
        public static final int SWING_SOUND = 0xB9;

        private final CczKamaKama parent;
    /** The slot of {@code parent} the native child stores and tests each turn ({@code cmpi.b #$24,obj.id(a1)}). */
    private int parentSlot = -1;
        private final int parentFlip;
        private int launchDelay;     // oVar2C
        private int baseX;           // oVar32
        private int xVel;
        private int yVel;
        private int gravity;         // oVar2A
        private int damageTimer;     // oVar34

        Sickle(ObjectSpawn spawn, CczKamaKama parent, int launchDelay, int parentSpriteFlip) {
            super(spawn, "Kama sickle");
            this.parent = parent;
        this.parentSlot = parent == null ? -1 : parent.getSlotIndex();
            this.launchDelay = launchDelay;
            this.parentFlip = parentSpriteFlip;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            switch (routine) {
                case 0 -> init();
                case 2 -> waitForLaunch();
                default -> fly(player);
            }
        }

        /** ObjKamaSickle_Init (:477-497): ends with an rts. */
        private void init() {
            spriteFlip = 0;
            priority = 1;
            tileWord = CczObjectArt.KAMA_TILE;
            baseX = x;
            int d1 = 0x300;
            if ((parentFlip & 1) == 0) {
                spriteFlip |= 1;
                statusFlip |= 1;
                d1 = -d1;
            }
            xVel = d1;
            routine += 2;
        }

        /** ObjKamaSickle_WaitForLaunch (:499-525). */
        private void waitForLaunch() {
            launchDelay = (launchDelay - 1) & 0xFFFF;
            if (launchDelay == 0) {
                routine += 2;
                colType = 0x87;
                damageTimer = 0x3C;
            }
            if (!slotHoldsId(parentSlot, 0x24)) {                  // cmpi.b #$24,0(a1); beq .Animate
                setDestroyed(true);
                return;
            }
            sickleAnimate();
        }

        /** ObjKamaSickle_Fly (:527-562). */
        private void fly(PlayableEntity player) {
            moveX(xVel);
            moveY(yVel);
            yVel = (short) (yVel + gravity);
            if (mapFrame == 0 && renderFlagOnScreen()) services().playSfx(SWING_SOUND);   // cmpi.b #0,oMapFrame; btst #7,oSprFlags; #$B9
            if (player instanceof AbstractPlayableSprite sprite && checkPlayer(sprite)) {
                colType = 0;                                               // ObjKamaSickle_HurtPlayer (:596-608)
                setAnim(1);
                xVel = (short) (xVel + sprite.getXSpeed());
                yVel = (short) 0xF800;
                gravity = 0x40;
            }
            damageTimer = (damageTimer - 1) & 0xFF;
            if (damageTimer == 0) {
                damageTimer = 1;
                colType = 0;
            }
            sickleAnimate();
        }

        /** ObjKamaSickle_CheckPlayer (:564-594). */
        private boolean checkPlayer(AbstractPlayableSprite sprite) {
            if (sprite.getInvincibleFrames() <= 0 && !sprite.getRolling()) return false;
            int d0 = (short) (sprite.getCentreX() - x + 0x1C);
            if (d0 < 0 || d0 >= 0x38) return false;
            d0 = (short) (sprite.getCentreY() - y + 0x1C);
            return d0 >= 0 && d0 < 0x38;
        }

        private void setAnim(int anim) {
            if (animator == null) animator = CczObjectArt.animator(provider(), "kamaSickleAnimations", 0);
            if (animator != null) animator.anim = anim;
        }

        private void sickleAnimate() {
            if (animator == null) animator = CczObjectArt.animator(provider(), "kamaSickleAnimations", 0);
            animate();
        }

        @Override protected boolean despawnsByCheckObjDespawn() { return true; }
        @Override protected int despawnX() { return baseX; }
        @Override public int getCollisionFlags() { return colType; }
        @Override public int getCollisionProperty() { return 0; }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (routine == 0) return;
            CdObjectArtProvider provider = provider();
            if (provider == null) return;
            String key = CczObjectArt.key(provider, CczObjectArt.KAMA_SICKLE, tileWord, services().camera().getX());
            if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
        }
    }
}
