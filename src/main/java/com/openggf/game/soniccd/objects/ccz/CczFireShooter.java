package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Fire shooter, object $27 ({@code ObjFireShooter}, Level/Collision Chaos/Objects/Fire Shooter/Main.asm:35-352, legacy ABI),
 * three objects on one ID by subtype: 0 the emitter (tile {@code $379}, priority 2, width $10; it spawns its flame at
 * Y+8 and then idles), 1 the flame ({@link Flame}) and 2 a fireball ({@link Fireball}). The emitter and flame despawn through the
 * object's own chunk test on the base X and {@code DespawnObjectR3} (:39-49, 112-122); the fireball through CheckObjectDespawn.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczFireShooter extends CczObject implements ObjectVisualFacing.NonDirectional {
    private int baseX;               // oVar36

    public CczFireShooter(ObjectSpawn spawn) {
        super(spawn, "Fire shooter");
    }

    /** The registrar's dispatch: {@code cmpi.b #1,d0 / #2,d0} on the subtype byte (:35-41). */
    public static CczObject create(ObjectSpawn spawn) {
        return switch (spawn.subtype() & 0xFF) {
            case 1 -> new Flame(spawn);
            case 2 -> new Fireball(spawn);
            default -> new CczFireShooter(spawn);
        };
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
    }

    /** ObjFireShooter_Emitter_Init (:96-136): without a free slot it returns and retries next frame. */
    private void init() {
        tileWord = CczObjectArt.FIRE_SHOOT_TILE;
        priority = 2;
        baseX = x;
        width = 0x10;
        var objects = services().objectManager();
        if (objects == null) return;
        objects.addDynamicObject(new Flame(new ObjectSpawn(x, y + 8, 0x27, 1, 0, false, 0)));
        routine += 2;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return baseX; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return outsideChunkRange(routine == 0 ? x : baseX, cameraX);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(CczObjectArt.FIRE_SHOOT, mapFrame);
    }

    /**
     * The flame (subtype 1, {@code ObjFireShooter_Flame}, :125-300): solid ($10 wide, $14 high), starting at frame 1. When the
     * player is within $78 pixels it counts $3C frames (animating in the present and in a bad future), then rises 8 pixels a
     * frame to $20 above its base, throws fireballs (not in the past or a good future) and launches a player standing in
     * it upward at -$700, holds 8 frames, drops 4 pixels a frame, rests $3C frames and starts again.
     */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    public static final class Flame extends CczObject implements ReevaluatingSolidObject,
            ObjectVisualFacing.NonDirectional {
        private int baseX;           // oVar36
        private int baseY;           // oVar32
        private int timer;           // oVar3A (byte)
        private int height;          // oVar3B (byte)

        public Flame(ObjectSpawn spawn) {
            super(spawn, "Fire flame");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            switch (routine) {
                case 0 -> init();
                case 2 -> detectPlayer(player);
                case 4 -> raise(player);
                case 6 -> lower();
                default -> waitAtBase();
            }
        }

        /** ObjFireShooter_Flame_Init (:206-223): ends with rts. */
        private void init() {
            tileWord = CczObjectArt.FIRE_SHOOT_TILE;
            priority = 3;
            width = 0x10;
            yRadius = 0x14;
            baseX = x;
            baseY = y;
            mapFrame = 1;
            routine += 2;
        }

        private void solid() {
            solidObject(Entry.SOLID);
        }

        /** ObjFireShooter_Flame_DetectPlayer (:225-281). */
        private void detectPlayer(PlayableEntity player) {
            if (timer == 0) {
                int dx = player instanceof AbstractPlayableSprite sprite
                        ? Math.abs((short) (x - sprite.getCentreX())) : 0x7FFF;
                if (dx >= 0x78) {                                          // cmpi.w #$78,d0; bcs .SetTimer
                    solid();
                    return;
                }
                timer = 0x3C;
            }
            timer = (timer - 1) & 0xFF;
            if (timer == 0) {                                              // .Raise
                timer = 0;
                height = 0;                                                // move.w #0,oFireFlameTimer
                mapFrame = 1;
                routine += 2;
                return;
            }
            int zone = timeZone();
            if (zone != 0 && (zone == 1 || !goodFuture())) {               // present, or a future that is not good
                if (animator == null) animator = CczObjectArt.animator(provider(), "fireShootAnimations", 0);
                animate();
            }
            solid();
        }

        /** ObjFireShooter_Flame_Raise (:283-337). */
        private void raise(PlayableEntity player) {
            height = (height + 8) & 0xFF;
            if (height >= 0x20) height = 0x20;
            y = (baseY - (byte) height) & 0xFFFF;
            if (height != 0x20) {
                solid();
                return;
            }
            spawnFireballs();
            boolean hit = solidObject(Entry.SOLID);                        // CheckSolidAtCurrentPosition
            if (!hit) {
                timer = 8;
                routine += 2;
                return;
            }
            if (player instanceof AbstractPlayableSprite sprite) {         // .HitPlayer (:243-249)
                sprite.setYSpeed((short) -0x700);
                sprite.setAir(true);
                sprite.setOnObject(false);
                sprite.setAnimationId(0x10);
                clearStoodOn();
            }
        }

        /** ObjFireShooter_Flame_Lower (:348-394). */
        private void lower() {
            if (timer != 0) {
                timer = (timer - 1) & 0xFF;
                solid();
                return;
            }
            height -= 4;
            if (height < 0) height = 0;                                    // subq.b #4; bcc; move.b #0
            y = (baseY - (byte) height) & 0xFFFF;
            if (height == 0) {
                timer = 0x3C;
                routine += 2;
            }
            solid();
        }

        /** ObjFireShooter_Flame_Wait (:396-406). */
        private void waitAtBase() {
            if (timer != 0) {
                timer = (timer - 1) & 0xFF;
                solid();
                return;
            }
            routine = 2;
        }

        /** ObjFireShooter_Flame_SpawnProjectiles (:408-452): two fireballs at X +-$18, Y low byte + 8. */
        private void spawnFireballs() {
            int zone = timeZone();
            if (zone == 0 || (zone != 1 && goodFuture())) return;
            var objects = services().objectManager();
            if (objects == null) return;
            int fireY = (y & 0xFF00) | ((y + 8) & 0xFF);                   // addq.b #8,d0 on the low byte
            objects.addDynamicObject(new Fireball(new ObjectSpawn(x - 0x18, fireY, 0x27, 2, 0, false, 0), 0));
            objects.addDynamicObject(new Fireball(new ObjectSpawn(x + 0x18, fireY, 0x27, 2, 0, false, 0), 1));
        }

        @Override public int solidWidth() { return width; }
        @Override public int solidYRadius() { return yRadius; }

        @Override protected boolean despawnsByCheckObjDespawn() { return true; }
        @Override protected int despawnX() { return baseX; }

        @Override
        public boolean isCustomOutOfRange(int cameraX) {
            return outsideChunkRange(routine == 0 ? x : baseX, cameraX);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawSheet(CczObjectArt.FIRE_SHOOT, mapFrame);
        }

        public int height() { return height; }
        public int timer() { return timer; }
    }

    /**
     * The fireball (subtype 2, {@code ObjFireShooter_Projectile}, :454-463): hurt collision {@code $98}, priority 4. Starts at
     * X speed +-$100 accelerating by +-$10 (animation 1 left, 2 right), and after $40 pixels from its base X loses its
     * collision, plays the burn-out animation for $1E frames and deletes itself. CheckObjectDespawn applies.
     */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    public static final class Fireball extends CczObject implements TouchResponseProvider,
            ObjectVisualFacing.NonDirectional {
        private final int direction;     // oVar3A (the word: the right fireball's byte is the high byte)
        private int baseX;               // oVar36
        private int xVel;
        private int acceleration;        // oVar3E
        private int burnout;             // oVar3B

        public Fireball(ObjectSpawn spawn) {
            this(spawn, 0);
        }

        Fireball(ObjectSpawn spawn, int direction) {
            super(spawn, "Fire ball");
            this.direction = direction;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (routine == 0) {
                init();
                moveAndCheck();
            } else {
                moveAndCheck();
            }
        }

        /** ObjFireShooter_Projectile_Init (:471-495): the animation runs once before the move. */
        private void init() {
            tileWord = CczObjectArt.FIRE_SHOOT_TILE;
            priority = 4;
            colType = 0x98;
            baseX = x;
            int d0 = 0x100;
            int d1 = 0x10;
            int anim = 2;
            if (direction == 0) {
                d0 = -d0;
                d1 = -d1;
                anim = 1;
            }
            xVel = d0;
            acceleration = d1;
            routine += 2;
            animator = CczObjectArt.animator(provider(), "fireShootAnimations", anim);
            animate();
        }

        /** ObjFireShooter_Projectile_Move (:497-551). */
        private void moveAndCheck() {
            xVel = (short) (xVel + acceleration);
            moveX(xVel);
            animate();
            int d0 = Math.abs((short) (x - baseX));
            if (d0 < 0x40) return;
            colType = 0;                                                   // .ReachedEnd
            if (burnout == 0) {
                if (animator != null) animator.anim += 2;                  // addq.b #2,oAnim(a0)
                burnout = 0x1E;
                return;
            }
            burnout = (burnout - 1) & 0xFF;
            if (burnout == 0) setDestroyed(true);
        }

        @Override public int getCollisionFlags() { return colType; }
        @Override public int getCollisionProperty() { return 0; }
        @Override protected boolean despawnsByCheckObjDespawn() { return true; }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawSheet(CczObjectArt.FIRE_SHOOT, mapFrame);
        }

        public int xVelocity() { return xVel; }
        public int burnout() { return burnout; }
    }
}
