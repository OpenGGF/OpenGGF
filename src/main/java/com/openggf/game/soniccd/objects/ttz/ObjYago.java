package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.ObjectTerrainUtils;

/**
 * Yago, object $2E ({@code YagoObject}, objects/r4/yago.asm:3-472): a collision-$2F badnik (tile $23F0, priority 1,
 * draw half-width $18, floor radius $C) that crawls along the floor, turns at walls and ledges, and leaps at the
 * player when it is in front of it. The subtype picks one of two copies of the routine that differ in a few
 * constants (:3-8): subtype 0 is {@code YagoObject_0} (:32-228), any other {@code YagoObject_1} (:260-464). Each frame
 * ends in the shared tail (loc_20DF12 / loc_20E17C: {@code AnimateObject}, {@code DrawObject},
 * {@code CheckObjectDespawn2} on the first X), and the routine begins with {@code DestroyInGoodFuture}.
 *
 * <p>Routines: 0 init (falls into 2); 2 falls 1 px a frame until the floor is within 5 (subtype 1 also snaps to it),
 * then starts the crawl; 4 crawls for $50 frames ({@code oVar2A} counts {@code $500000} down by $4000) at
 * {@code oVar2E = -$4000} (16.16, turned by the flip bit), snapping to the floor each frame; it stops at a wall
 * (probe distance &lt; 5) or when the player is in front within {@code [$10,$50)} (loc_20DF7C-loc_20DFB2); 6 waits
 * $1F frames and turns round; 8 waits $1F frames and resumes crawling; A waits 7 frames; C sinks by $2000
 * ($2800 for subtype 1) a frame for $1F frames then picks the leap speed ({@code -$48000}, subtype 1
 * {@code -$10000}, negated for a right-facing Yago) and {@code oVar34 = -$18000}; E leaps; 10 falls back to the
 * floor.
 *
 * <p>The wall probe adds {@code obj.width} (never set by this object, so 0) to X for both directions:
 * ObjGetRWallDist (Level/Collision Check.asm:239-243) and ObjGetLWallDist (Level/Collision Check.asm:401-405) both
 * start {@code add.w oX(a0),d3}; the floor probe is {@code (x, y + obj.height)} (ObjGetFloorDist,
 * Level/Collision Check.asm:157-175). Shipped
 * {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjYago extends TtzBadnik {
    private static final int FLOOR_CLEARANCE = 5;
    private static final int CRAWL_TIME = 0x500000;
    private static final int CRAWL_STEP = 0x4000;
    private static final int TURN_TIME = 0x1F;

    private final boolean second;
    private int originX;
    /** oVar2A: the crawl clock; oVar2E: the crawl speed. */
    private int crawlClock;
    private int crawlSpeed;
    /** oVar32: the stage timer. */
    private int timer;
    /** oVar34: the leap's vertical speed; oVar38: its horizontal speed. */
    private int leapVertical;
    private int leapHorizontal;
    /** The routine reached the full CheckObjectDespawn inside routine E (:196). */
    private boolean ownDespawn;

    public ObjYago(ObjectSpawn spawn) {
        super(spawn, "Yago");
        this.second = spawn.subtype() != 0;
    }

    @Override protected String sheetKind() { return second ? TtzObjectArt.YAGO2 : TtzObjectArt.YAGO1; }

    @Override protected int despawnX() { return originX; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return outsideDespawnRange(originX, cameraX, renderFlagOnScreen())
                || (ownDespawn && outsideDespawnRange(x, cameraX, renderFlagOnScreen()));
    }

    @Override
    protected void run(PlayableEntity player) {
        ownDespawn = false;
        switch (routine) {
            case 0 -> { init(); fall(); }                                // Routine0 has no rts: it runs Routine2
            case 2 -> fall();
            case 4 -> crawl(player);
            case 6 -> turnPause();
            case 8 -> resumePause();
            case 0xA -> chargePause();
            case 0xC -> sink();
            case 0xE -> leap();
            case 0x10 -> land();
            default -> { }
        }
        animate();                                                       // loc_20DF12 / loc_20E17C
    }

    /** YagoObject_0_Routine0 and YagoObject_1_Routine0 (:32-42, :260-270). */
    private void init() {
        originX = x;
        routine += 2;
        colType = 0x2F;
        priority = 1;
        width = 0x18;
        yRadius = 0xC;
        tileWord = TtzObjectArt.YAGO_TILE;
        animator = TtzObjectArt.animator(artProvider(), second ? "yagoAnimations2" : "yagoAnimations1", 0);
        initialised = true;
    }

    private int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();       // CheckBlockDown
    }

    /** CheckBlockRight when the flip bit is set, else CheckBlockLeft, with {@code d3 = obj.width = 0}. */
    private int wallDistance() {
        return (spriteFlip & 1) != 0
                ? ObjectTerrainUtils.checkRightWallDist(x, y).distance()
                : ObjectTerrainUtils.checkLeftWallDist(x, y).distance();
    }

    private void addToY(int velocity) {
        int position = ((y & 0xFFFF) << 16 | ySub) + velocity;
        y = position >>> 16;
        ySub = position & 0xFFFF;
    }

    /** {@code add.w d1,obj.y(a0)}: a word add, the sub-pixel is untouched. */
    private void addToYWord(int delta) {
        y = (y + delta) & 0xFFFF;
    }

    /** Routine2 (:43-56, :271-290). */
    private void fall() {
        addToY(0x10000);
        int d1 = floorDistance();
        if (d1 < FLOOR_CLEARANCE) {                                      // cmpi.w #5,d1; bmi.s
            routine += 2;                                                // loc_20DF2E / loc_20E198
            if (second) addToYWord(d1 - FLOOR_CLEARANCE);                // subq.w #5,d1; add.w d1,obj.y(a0)
            crawlClock = CRAWL_TIME;
            crawlSpeed = -CRAWL_STEP;
        }
    }

    /** Routine4 (:66-111, :296-342). */
    private void crawl(PlayableEntity player) {
        crawlClock += -CRAWL_STEP;
        if (crawlClock < 0) {                                            // bmi.s loc_20DFA4
            turnAround();
            return;
        }
        addToX(crawlSpeed);
        addToYWord(floorDistance() - FLOOR_CLEARANCE);
        if (wallDistance() < FLOOR_CLEARANCE) {                          // cmpi.w #5,d1; bmi.s loc_20DFA4
            turnAround();
            return;
        }
        if (!(player instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite)) return;
        int d0 = (x - sprite.getCentreX()) & 0xFFFF;
        if ((spriteFlip & 1) != 0) d0 = -d0 & 0xFFFF;
        if (((d0 - 0x10) & 0xFFFF) < 0x40) {                             // subi.w #$10,d0; subi.w #$40,d0; bcs.s loc_20DFB2
            routine += 6;
            timer = 7;
        }
    }

    /** loc_20DFA4 (:99-104). */
    private void turnAround() {
        routine += 2;
        timer = TURN_TIME;
    }

    /** Routine6 (:113-129). */
    private void turnPause() {
        timer = (timer - 1) & 0xFFFF;
        if (timer != 0) return;
        routine += 2;
        timer = TURN_TIME;
        crawlClock = CRAWL_TIME;
        crawlSpeed = -crawlSpeed;
        flipHorizontally();
    }

    /** Routine8 (:131-142). */
    private void resumePause() {
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) routine -= 4;
    }

    /** RoutineA (:144-156). */
    private void chargePause() {
        timer = (timer - 1) & 0xFFFF;
        if (timer != 0) return;
        routine += 2;
        timer = TURN_TIME;
    }

    /** RoutineC (:158-177, :388-407). */
    private void sink() {
        addToY(second ? 0x2800 : 0x2000);
        timer = (timer - 1) & 0xFFFF;
        if (timer != 0) return;
        routine += 2;
        leapHorizontal = second ? -0x10000 : -0x48000;
        if ((spriteFlip & 1) != 0) leapHorizontal = -leapHorizontal;
        leapVertical = -0x18000;
    }

    /** RoutineE (:179-203, :409-444). */
    private void leap() {
        addToX(leapHorizontal);
        int wall = wallDistance();
        if (!second) {
            if (wall < FLOOR_CLEARANCE) {                                // cmpi.w #5,d1; bmi.s loc_20E014
                routine += 2;
                timer = TURN_TIME;
                return;
            }
            addToY(leapVertical);
            if (leapVertical < 0) leapVertical += 0x2000;                // tst.l oVar34; bpl.s; addi.l #$2000
            ownDespawn = true;                                           // jsr CheckObjectDespawn
        } else {
            if (wall < 0) {                                              // tst.w d1; bmi.s loc_20E316
                routine += 2;
                return;
            }
            addToY(leapVertical);
            leapVertical += 0x1000;
            if (leapVertical < 0) return;                                // bmi.w loc_20E17C
            if (floorDistance() < FLOOR_CLEARANCE) routine = 4;          // loc_20E31E
        }
    }

    /** Routine10 (:210-226, :446-462). */
    private void land() {
        addToY(0x10000);
        int d1 = floorDistance();
        if (d1 >= FLOOR_CLEARANCE) return;
        routine = 6;
        addToYWord(d1 - FLOOR_CLEARANCE);
        timer = TURN_TIME;
    }
}
