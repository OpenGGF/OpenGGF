package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdPoints;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Pocket, object $2F ({@code ObjPocket}, Level/Collision Chaos/Objects/Pocket/Main.asm:7-310, legacy ABI): a prize
 * machine. Subtype bit 7 set is the machine, otherwise its glass-front child ({@link Child}). The machine (tile
 * {@code $3AF}, priority 0) is deleted in a good future. It waits until a falling player, not hurt or dead, is within
 * -$18..-8 above and $18 either side of it, then captures him: rolling ball, speed (0, -$800), moved onto the machine, his
 * control and visibility bits set (the pad is ignored and he is hidden). It runs a fixed sequence of timed mapping
 * frames: spawn the child, open, award 100 points twice (a counter of 7 prizes, one per $1E frames, only while it is
 * above zero), close, release the player (sound {@code $9F}; he keeps the -$800 speed) and rest $78 frames.
 *
 * <p>Player writes: {@code oPlayerCtrl} bit 0 (controlled by an object) is {@code setObjectControlled}; bit 6 (invisible;
 * also the Collision Chaos entry test of {@code PlayerObjectCollide}, r3/player_object_collide.asm:4) is {@code setHidden}.
 * The Collision Chaos player profile's {@code objectCollisionEnabled} must treat a hidden player as bit 6 set (reported).
 * {@code debugMode} (the machine ignores the player in debug mode) is not hosted.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczPocket extends CczObject implements ObjectVisualFacing.NonDirectional {
    /** move.w #$D7 (capture) and #$9F (release) (:119, :219). */
    public static final int CAPTURE_SOUND = 0xD7, RELEASE_SOUND = 0x9F;

    private int timer;               // oVar2A
    private Child child;             // oVar2C
    private int prizes;              // oVar30
    private boolean caught;          // oVar3E

    public CczPocket(ObjectSpawn spawn) {
        super(spawn, "Pocket");
    }

    /** ObjPocket: {@code tst.b oSubtype(a0); bmi.w ObjPocket_Parent} (:15-18). */
    public static boolean isMachine(int subtype) {
        return (byte) subtype < 0;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                init();
                if (!isDestroyed()) waitForPlayer(player);
            }
            case 2 -> waitForPlayer(player);
            case 4 -> spawnEffect(player);
            case 6 -> step(2, 3);
            case 8 -> step(0x1E, 0);
            case 10 -> award(0x1E);
            case 12 -> award(0x1E);
            case 14 -> step(4, 3);
            case 16 -> step(6, 2);
            case 18 -> release(player);
            default -> cooldown();
        }
    }

    /** ObjPocket_Init (:55-69); falls into ObjPocket_WaitForPlayer. */
    private void init() {
        if (timeZone() == 2 && goodFuture()) {                            // cmpi.b #TIME_FUTURE,timeZone; tst.b goodFuture
            setDestroyed(true);
            return;
        }
        routine = 2;
        priority = 0;
        tileWord = CczObjectArt.POCKET_TILE;
        prizes = 7;
    }

    /** ObjPocket_WaitForPlayer and ObjPocket_CheckPlayer (:71-125). */
    private void waitForPlayer(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        if (sprite.getYSpeed() < 0) return;                                // tst.w oYVel(a6); bmi
        if (sprite.getDead() || sprite.isHurt()) return;                   // cmpi.b #4 / #6,oRoutine(a6)
        int dy = (sprite.getCentreY() - y + 0x18) & 0xFFFF;                // subi #-$18; subi #$10; bcc
        if (dy >= 0x10) return;
        int dx = (sprite.getCentreX() - x + 0x18) & 0xFFFF;
        if (dx >= 0x30) return;
        caught = true;
        sprite.setObjectControlled(true);                                  // bset #0,oPlayerCtrl(a6)
        sprite.setObjectControlAllowsCpu(true);                            // bit 0 only: oPlayerCtrl's sign stays clear
        sprite.setRolling(true);                                           // bset #2,oFlags(a6)
        sprite.setAnimationId(2);                                          // move.b #2,oAnim(a6)
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) -0x800);
        NativePositionOps.writeXPosPreserveSubpixel(sprite, x);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, y - 0x10);
        // ObjPocket_CapturePlayer (:127-135)
        routine += 2;
        timer = 8;
        mapFrame = 1;
        services().playSfx(CAPTURE_SOUND);
    }

    /** ObjPocket_SpawnEffect (:137-163). */
    private void spawnEffect(PlayableEntity player) {
        timer = (timer - 1) & 0xFFFF;
        if ((timer & 0x8000) == 0) return;                                 // addi.w #-1; bpl .End
        routine += 2;
        mapFrame = 2;
        timer = 6;
        var objects = services().objectManager();
        if (objects == null) {
            setDestroyed(true);
            return;
        }
        child = new Child(new ObjectSpawn(x, y, 0x2F, 0, 0, false, 0), this);
        objects.addDynamicObject(child);
        if (player instanceof AbstractPlayableSprite sprite) sprite.setHidden(true);   // bset #6,oPlayerCtrl(a6)
    }

    /**
     * The timed frames: {@code addi.w #-1,oPocketTimer; bpl .End}, then the next routine sets {@code nextTimer} and the
     * mapping frame {@code nextFrame} (ObjPocket_Open, _BeginAward, _BeginClosing, _Close, :165-209).
     */
    private void step(int nextTimer, int nextFrame) {
        timer = (timer - 1) & 0xFFFF;
        if ((timer & 0x8000) == 0) return;
        routine += 2;
        timer = nextTimer;
        mapFrame = nextFrame;
    }

    /** ObjPocket_AwardFirstPoints and _AwardSecondPoints (:177-202). */
    private void award(int nextTimer) {
        timer = (timer - 1) & 0xFFFF;
        if ((timer & 0x8000) == 0) return;
        routine += 2;
        timer = nextTimer;
        if (prizes == 0) return;
        prizes--;
        var game = services().gameState();
        if (game != null) game.addScore(100);                              // moveq #10,d0; bsr.w AddPoints
        var objects = services().objectManager();
        if (objects != null) objects.addDynamicObject(new CdPoints(new ObjectSpawn(x, y, 0x1C, 0x80, 0, false, 0)));
    }

    /** ObjPocket_ReleasePlayer (:211-232). */
    private void release(PlayableEntity player) {
        timer = (timer - 1) & 0xFFFF;
        if ((timer & 0x8000) == 0) return;
        services().playSfx(RELEASE_SOUND);
        routine += 2;
        mapFrame = 0;
        timer = 0x78;
        if (child != null) child.finish();                                 // move.b #4,oRoutine(a1)
        if (!caught) return;
        caught = false;
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.setObjectControlled(false);                             // bclr #0,var_2c
            sprite.setHidden(false);                                       // bclr #6,var_2c
        }
    }

    /** Clears the control and invisibility bits this pocket set (ObjPocket_ReleasePlayer's two {@code bclr} writes). */
    @Override
    public void releasePlayersForVisualDirection() {
        if (!caught) return;
        caught = false;
        if (services().camera().getFocusedSprite() instanceof AbstractPlayableSprite sprite) {
            sprite.setObjectControlled(false);
            sprite.setHidden(false);
        }
    }

    /** ObjPocket_Cooldown (:234-244). */
    private void cooldown() {
        timer = (timer - 1) & 0xFFFF;
        if ((timer & 0x8000) == 0) return;
        routine = 2;
        caught = false;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, CczObjectArt.POCKET1, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }

    public int timer() { return timer; }
    public int prizes() { return prizes; }
    public boolean caught() { return caught; }

    /**
     * The machine's glass front ({@code ObjPocket_Child}, :248-278): copies the machine's position, animates
     * {@code Ani_Pocket} (width $18, tile {@code $43AF}, priority 0) while the parent is still object $2F, and is deleted
     * by its routine 4, which the machine writes when it finishes.
     */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    public static final class Child extends CczObject implements ObjectVisualFacing.NonDirectional {
        private final CczPocket parent;

        Child(ObjectSpawn spawn, CczPocket parent) {
            super(spawn, "Pocket front");
            this.parent = parent;
        }

        /**
         * {@code move.b #4,oRoutine(a1)} (ObjPocket_ReleasePlayer, Pocket/Main.asm:497): the child's routine table
         * (:590-593) sends routine 4 to its delete entry, run on its next pass; until then its slot shows routine 4.
         */
        void finish() {
            routine = 4;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (routine == 0) {
                routine = 2;
                priority = 0;
                width = 0x18;
                yRadius = 8;
                tileWord = CczObjectArt.POCKET_CHILD_TILE;
            }
            if (routine == 4 || parent == null || parent.isDestroyed()) {
                setDestroyed(true);
                return;
            }
            if (animator == null) animator = CczObjectArt.animator(provider(), "pocketAnimations", 0);
            animate();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (routine == 0) return;
            CdObjectArtProvider provider = provider();
            if (provider == null) return;
            String key = CczObjectArt.key(provider, CczObjectArt.POCKET2, tileWord, services().camera().getX());
            if (key != null) drawFrame(key, mapFrame, false, false);
        }
    }
}
