package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Tentou, object $2E ({@code ObjTentou}, Level/Collision Chaos/Objects/Tentou/Main.asm:20-300; legacy ABI). The object splits on
 * {@code subtype - 1}: negative is the badnik, otherwise its bomb ({@link Bomb}). The badnik (collision 6, priority 1, width
 * and Y radius $E, tile {@code $A46A}) settles onto the floor one pixel a frame, bobs by {@code oTentouVerticalVelocity}
 * ($3000 per frame, reversed every 16 phase steps), waits until the player is within $50 pixels each way, then flies
 * away from him at $C000 (16.16) along the floor, turning to rise at a wall (routine 8) or to cross a gap (routine $A,
 * which resumes the floor settle when the floor returns). A subtype-0 Tentou drops a bomb every 128 phase steps.
 *
 * <p>The wall probes ({@code CheckBlockLeft}/{@code CheckBlockRight}) add the byte {@code oXRadius} to X for both directions
 * (Tentou/Main.asm:107-112 loads {@code oXRadius}, which init never sets, so the probe is at X itself).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczTentou extends CczBadnik {
    private int hVel;                // oVar2A (long)
    private int vVel;                // oVar2E (long)
    private int baseY;               // oVar32 (long)
    private int phase;               // oVar36
    private boolean bombDropper;     // oVar3F
    private boolean secondSheet;
    private int xRadius;             // oXRadius, never set by init

    public CczTentou(ObjectSpawn spawn) {
        super(spawn, "Tentou");
    }

    /** ObjTentou: {@code addi.b #-1,d0; bmi.s ObjTentou_Parent} (:57-76). */
    public static boolean isParent(int subtype) {
        return ((subtype - 1) & 0xFF) >= 0x80;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (destroyOnGoodFuture()) return;
        switch (routine) {
            case 0 -> {
                init();
                findFloor();
            }
            case 2 -> findFloor();
            case 4 -> waitForPlayer(player);
            case 6 -> move();
            case 8 -> addToY(-0x8000);                                     // ObjTentou_RiseAfterWall (:234-238)
            default -> crossGap();
        }
        if (animator == null) animator = CczObjectArt.animator(provider(), "tentouAnimations", 0);
        animate();
    }

    /** ObjTentou_Init (:88-105). */
    private void init() {
        routine = 2;
        priority = 1;
        colType = 6;
        width = 0xE;
        yRadius = 0xE;
        tileWord = CczObjectArt.TENTOU_TILE;
        secondSheet = (byte) spawn.subtype() < 0;
        bombDropper = !secondSheet;
    }

    private int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();     // TentouGetFloorDist = CheckBlockDown
    }

    /** ObjTentou_FindFloor (:107-124). */
    private void findFloor() {
        addToY(0x10000);
        int d1 = floorDistance() - 8;
        if (d1 > 0) return;                                                // subq.w #8,d1; bgt.s .End
        y = (y - d1) & 0xFFFF;
        baseY = yLong();
        vVel = 0x3000;
        phase = 8;
        routine += 2;
        if (hVel != 0) routine += 2;                                       // tst.l oTentouHorizontalVelocity
    }

    /** ObjTentou_WaitForPlayer (:126-151). */
    private void waitForPlayer(PlayableEntity player) {
        bob();
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        int dy = (y - sprite.getCentreY() + 0x50) & 0xFFFF;                // subi.w #-$50; subi.w #$A0; bcc
        if (dy >= 0xA0) return;
        int d1 = (short) (x - sprite.getCentreX());
        int dx = (d1 + 0x50) & 0xFFFF;
        if (dx >= 0xA0) return;
        routine += 2;
        hVel = -0xC000;
        if (d1 < 0) return;                                                // tst.w d1; bmi.s .End
        flipHorizontally();                                                // bchg #0,oSprFlags; bchg #0,oFlags
        hVel = -hVel;
    }

    /** ObjTentou_Move (:153-232). */
    private void move() {
        addToX(hVel);
        setY(baseY);
        // Both wall probes use x + oXRadius (never set, so x itself) (:107-112).
        int probeX = x + xRadius;
        int wall = hVel < 0
                ? ObjectTerrainUtils.checkLeftWallDist(probeX, y).distance()
                : ObjectTerrainUtils.checkRightWallDist(probeX, y).distance();
        if (wall < 0) {
            routine += 2;                                                  // .HitWall
            return;
        }
        int d1 = floorDistance();
        if (d1 >= 0x10) {                                                  // .HitFloor
            routine += 4;
            bob();
            baseY = yLong();
            return;
        }
        y = (y + (d1 - 8)) & 0xFFFF;                                       // subq.w #8,d1; add.w d1,oY(a0)
        baseY = yLong();
        bob();
        if (!bombDropper) return;
        phase &= 0x7F;
        if (phase != 0) return;
        var objects = services().objectManager();
        if (objects == null) return;
        Bomb bomb = new Bomb(new ObjectSpawn(x, (y + 0x10) & 0xFFFF, 0x2E, 1, 0, false, 0));
        bomb.xSub = xSub;
        bomb.ySub = ySub;
        objects.addDynamicObject(bomb);
    }

    /** ObjTentou_CrossGap (:240-261). */
    private void crossGap() {
        addToX(hVel);
        int d3 = x - width;
        if (hVel < 0) d3 += 2 * width;                                     // tst.l; bpl; add.w d4 twice
        int d1 = ObjectTerrainUtils.checkFloorDist(d3, y, yRadius).distance();   // TentouGetFloorDist2
        if (d1 < 0x10) return;
        routine -= 8;
    }

    /** ObjTentou_Bob (:263-280). */
    private void bob() {
        phase = (phase + 1) & 0xFFFF;
        addToY(vVel);
        if ((phase & 0xF) == 0) vVel = -vVel;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, secondSheet ? CczObjectArt.TENTOU2 : CczObjectArt.TENTOU1, tileWord,
                services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }

    public int phase() { return phase; }
    public int horizontalVelocity() { return hVel; }

    /**
     * Tentou's bomb ({@code ObjTentou_Bomb}, :249-340): falls one pixel a frame to the floor, waits $78 frames, fuses for $78
     * frames (animating {@code Ani_TentouBomb}), and explodes (the slot becomes explosion $18 with routine 2 = "not a
     * badnik"). A touch by the player ({@code oColStatus}) explodes it at once. Collision {@code $8B}, priority 2, size 6.
     */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    public static final class Bomb extends CczObject implements TouchResponseProvider, TouchResponseListener,
            ObjectVisualFacing.NonDirectional {
        /** FM_EXPLODE (Sound Drivers/FM/_Sound IDs.inc:24). */
        public static final int EXPLODE_SOUND = 0x9E;

        private int timer;           // oVar2A
        private boolean touched;     // oColStatus

        public Bomb(ObjectSpawn spawn) {
            super(spawn, "Tentou bomb");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            switch (routine) {
                case 0 -> {                                                // ObjTentou_Bomb_Init (:352-363), falls into Fall
                    routine = 2;
                    priority = 2;
                    colType = 0x8B;
                    width = 6;
                    yRadius = 6;
                    tileWord = CczObjectArt.TENTOU_BOMB_TILE;
                    fall();
                }
                case 2 -> fall();
                case 4 -> arm();
                case 6 -> fuse();
                default -> explode();
            }
        }

        private void fall() {
            if (touched) { explode(); return; }
            addToY(0x10000);
            int d1 = ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
            if (d1 >= 0) return;                                           // tst.w d1; bpl.s .End
            y = (y + d1) & 0xFFFF;
            timer = 0x78;
            routine += 2;
        }

        private void arm() {
            if (touched) { explode(); return; }
            timer = (timer - 1) & 0xFFFF;
            if (timer != 0) return;
            timer = 0x78;
            routine += 2;
        }

        private void fuse() {
            if (touched) { explode(); return; }
            timer = (timer - 1) & 0xFFFF;
            if (timer == 0) routine += 2;
            if (animator == null) animator = CczObjectArt.animator(provider(), "tentouBombAnimations", 0);
            animate();
        }

        /** ObjTentou_Bomb_Explode (:423-435): the slot becomes explosion $18, routine2 1 (not from a badnik). */
        private void explode() {
            if (renderFlagOnScreen()) services().playSfx(EXPLODE_SOUND);     // tst.b oSprFlags; bpl; FM_EXPLODE
            becomeExplosion(0, 0, 1, false);
        }

        @Override
        public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
            touched = true;
        }

        @Override public int getCollisionFlags() { return colType; }
        @Override public int getCollisionProperty() { return 0; }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (routine == 0) return;
            CdObjectArtProvider provider = provider();
            if (provider == null) return;
            String key = CczObjectArt.key(provider, CczObjectArt.TENTOU_BOMB, tileWord, services().camera().getX());
            if (key != null) drawFrame(key, mapFrame, false, false);
        }

        public int timer() { return timer; }
    }
}
