package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Kemusi, object $22 ({@code KemusiObject}, objects/r5/kemusi.asm:3-28): a four-segment caterpillar. The
 * layout object (secondary subtype 0) is the head; its routine 0 makes three more segments with
 * {@code SpawnObjectAfter} 12, 24 and 36 pixels to its right (secondary subtype {@code $FF}, :51-118) and
 * links the four so that each knows the other three ({@code oVar3A}-{@code oVar3E}). Each segment checks
 * every other is still object $22 and deletes itself if not (:8-18), so killing or despawning one removes
 * the whole caterpillar. The head carries collision {@code $33} (enemy); segments carry {@code $B4} (hurt)
 * for subtype 0, or {@code $33} for subtype 1.
 *
 * <p>Routines (off_20D62A, :37-43): 2 drop one pixel a frame to the floor, counting arrivals in the head's
 * {@code oVar36}; 4 the head waits for all four, then starts every segment; 6 start a crawl cycle
 * (phase {@code oVar2A ^= 3}, the head toggles its mapping frame); 8 crawl: each frame {@code oVar32} drops by
 * the phase and a pixel step is taken when it reaches zero ({@code oVar30}: 6 for subtype 0, 12 for subtype
 * 1), turning at {@code $50} pixels from the head's start or at an edge ({@code d1 + 7 < $E} fails); after
 * {@code $24} frames it returns to 6. Only the head runs {@code DestroyInGoodFuture} and CheckObjectDespawn2
 * (at its start X). Shipped {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjKemusi extends QqzBadnik {
    private int phase;              // oVar2A
    private int cycleLength;        // oVar2C
    private int stepLength;         // oVar30
    private int cycleTimer;         // oVar2E
    private int stepTimer;          // oVar32
    private int originX;            // oVar34
    private int landed;             // oVar36 (head only)
    /** oVar3A, oVar3C, oVar3E: the other three segments (a child's first is the head). */
    private final ObjKemusi[] others = new ObjKemusi[3];
    /** Their slots, the words {@code movem.w obj.var_3a(a0),a1-a3} loads (:11). */
    private final int[] otherSlots = {-1, -1, -1};
    private final boolean segment;

    public ObjKemusi(ObjectSpawn spawn) {
        super(spawn, "Kemusi");
        this.segment = false;
    }

    private ObjKemusi(ObjectSpawn spawn, boolean segment) {
        super(spawn, "Kemusi segment", 0xFF);
        this.segment = segment;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return !segment; }

    @Override protected int despawnX() { return routine == 0 ? x : originX; }

    @Override protected String sheetKind() { return QqzObjectArt.KEMUSI; }

    @Override
    protected void run(PlayableEntity player) {
        if (standDownInBossAct()) return;
        if (!segment && destroyInGoodFuture()) return;       // tst.b obj.subtype_2(a0); bne.s; DestroyInGoodFuture
        if (routine != 0) {
            for (int otherSlot : otherSlots) {               // movem.w obj.var_3a(a0),a1-a3; cmpi.b #$22,obj.id (:11-17)
                if (!slotHoldsId(otherSlot, 0x22)) {
                    deleteObject();                          // loc_20D634
                    drawThisFrame = false;
                    return;
                }
            }
        }
        switch (routine) {
            case 0 -> init();
            case 2 -> land();
            case 4 -> waitForAll();
            case 6 -> startCycle();
            case 8 -> crawl();
            default -> { }
        }
    }

    /** sub_20D710 (objects/r5/kemusi.asm:122-131): what the head and each segment share. */
    private void commonInit(int headX) {
        routine = 2;
        priority = 3;
        xRadius = 8;
        width = 8;
        tileWord = QqzObjectArt.KEMUSI_TILE;
        originX = headX;                                     // move.w obj.x(a0),obj.var_34(a1)
        initialised = true;
    }

    /** KemusiObject_0_Routine0 (objects/r5/kemusi.asm:51-118). */
    private void init() {
        phase = 3;
        mapFrame = 1;
        yRadius = 0xC;
        colType = 0x33;
        commonInit(x);
        boolean first = spawn.subtype() == 0;
        cycleLength = 0x24;
        stepLength = first ? 6 : 0xC;
        int frame = first ? 2 : 3;
        int radius = first ? 0xB : 7;
        int hitbox = first ? 0xB4 : 0x33;
        var objects = services().objectManager();
        int segmentX = x;
        for (int i = 0; i < 3; i++) {
            segmentX += 0xC;
            ObjKemusi child = new ObjKemusi(new ObjectSpawn(segmentX, y, 0x22, spawn.subtype(), 0, false, 0), true);
            if (objects != null) objects.addDynamicObjectAfterCurrent(child);
            if (objects == null || child.isDestroyed()) {
                deleteObject();                              // bne.s loc_20D634
                drawThisFrame = false;
                return;
            }
            others[i] = child;
            child.phase = 2 - i;                             // move.w d6,obj.var_2a(a1)
            child.mapFrame = frame;
            child.yRadius = radius;
            child.colType = hitbox;
            child.cycleLength = cycleLength;
            child.stepLength = stepLength;
            child.commonInit(x);
        }
        // movem.w ... link: each segment knows the other three.
        ObjKemusi a = others[0], b = others[1], c = others[2];
        a.others[0] = this; a.others[1] = b; a.others[2] = c;
        b.others[0] = this; b.others[1] = a; b.others[2] = c;
        c.others[0] = this; c.others[1] = a; c.others[2] = b;
        for (ObjKemusi part : new ObjKemusi[] {this, a, b, c}) {
            for (int i = 0; i < 3; i++) part.otherSlots[i] = part.others[i].getSlotIndex();
        }
    }

    /** KemusiObject_0_Routine2 (objects/r5/kemusi.asm:135-151): one pixel down until the floor is reached. */
    private void land() {
        y = (y + 1) & 0xFFFF;
        int d1 = floorDistance();
        if (d1 >= 0) return;
        y = (y + d1) & 0xFFFF;
        routine += 2;
        ObjKemusi head = segment ? others[0] : this;         // tst.b obj.subtype_2(a0); beq.s; movea.w obj.var_3a
        head.landed++;                                       // addq.w #1,obj.var_36(a1)
    }

    /** KemusiObject_0_Routine4 (objects/r5/kemusi.asm:155-170): the head starts everyone once all four have landed. */
    private void waitForAll() {
        if (segment || landed != 4) return;
        routine += 2;
        for (ObjKemusi other : others) other.routine += 2;
        startCycle();
    }

    /** KemusiObject_0_Routine6 (objects/r5/kemusi.asm:174-181). */
    private void startCycle() {
        routine += 2;
        phase ^= 3;
        cycleTimer = cycleLength;
        stepTimer = stepLength;
        if (!segment) mapFrame ^= 1;                         // eori.b #1,obj.sprite_frame(a0)
        crawl();
    }

    /** KemusiObject_0_Routine8 (objects/r5/kemusi.asm:183-210). */
    private void crawl() {
        while (true) {
            stepTimer = (stepTimer - phase) & 0xFFFF;        // sub.w d0,obj.var_32(a0)
            if (stepTimer != 0) break;                       // bne.s loc_20D7EE
            stepTimer = stepLength;
            step();
            int offset = Math.abs((short) (originX - x));
            if (offset >= 0x50) {                            // cmpi.w #$50,d0; bge.s loc_20D7FA
                turn();
                continue;
            }
            int d1 = floorDistance();
            if (((d1 + 7) & 0xFFFF) >= 0xE) {                // addq.w #7; subi.w #$E; bcc.s loc_20D7FA
                turn();
                continue;
            }
            y = (y + d1) & 0xFFFF;
            break;
        }
        cycleTimer = (cycleTimer - 1) & 0xFFFF;              // loc_20D7EE: subq.w #1,obj.var_2e(a0)
        if (cycleTimer == 0) routine -= 2;
    }

    /** loc_20D7FA (objects/r5/kemusi.asm:214-219): turn round, then take a step the new way. */
    private void turn() {
        flipHorizontally();
        step();
    }

    /** sub_20D80A (objects/r5/kemusi.asm:222-234): one pixel along {@code oSprFlags} bit 0. */
    private void step() {
        x = (x + ((spriteFlip & 1) != 0 ? 1 : -1)) & 0xFFFF;
    }
}
