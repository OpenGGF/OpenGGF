package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * The collapsing platform, object $20 ({@code ObjCollapsePlatform}, Palmtree Panic/Objects/Collapsing
 * Platform/Main.asm:7-310). A layout entry is a top-solid platform (tile $44BE, priority 3); the frame is
 * {@code subtype & $F} and subtype bit 7 picks the single-row tables ({@code Sizes2}, {@code
 * MapSpr_CollapsePlatform2}) over the multi-row ones, subtype bit 4 flips it. Standing on it plays
 * {@code FM_A3}, releases the player and replaces the entry with pieces in free object slots (the same
 * object ID, routine 4), then deletes it. Each piece counts its delay down ({@code oVar2A}), is solid on top
 * while it runs (only the top row of a multi-row platform, every piece of a single row), releases its rider
 * when the delay ends and then falls with $20000 + $4000 per frame (16.16) until it is more than $200
 * pixels below the player.
 *
 * <p>The sizes and mappings tables are the stage's own (catalogue columns {@code collapse*}); every stage
 * links its own copy of this routine, so the sizes tables differ per period and act
 * (Collapsing Platform/Data (Present).asm:7-65 and siblings).
 *
 * <p>{@code FindObjSlot} failing (Main.asm:175-176, 276-277) takes the {@code .Solid} exit: one
 * {@code TopSolidObject} for the player, a {@code GetOffObject} if it stands, and the entry deleted.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjCollapsePlatform extends CdObject implements ReevaluatingSolidObject,
        ObjectVisualFacing.NonDirectional {
    /** {@code ObjCollapsePlatform_Index} (scddisasm/src/Level/Palmtree Panic/Objects/Collapsing Platform/Main.asm:24-28). */
    private static final int INIT = 0, MAIN = 2, DELAY = 4, FALL = 6;
    /** Obj tile word for platform and pieces (ObjCollapsePlatform_Init, Main.asm:35; the piece loops 182 and 279). */
    static final int TILE = 0x44BE;
    /** {@code move.l #$20000,oVar2C(a1)} (ObjCollapsePlatform_BreakUp_MultiRow, Main.asm:184; SingleRow 282). */
    private static final int PIECE_START_SPEED = 0x20000;
    /** {@code addi.l #$4000,oVar2C(a0)} (ObjCollapsePlatform_Fall, Main.asm:117). */
    private static final int FALL_ACCELERATION = 0x4000;
    /** {@code cmpi.w #$200,d0; bgt.w .Delete} (ObjCollapsePlatform_Fall, Main.asm:121-122). */
    private static final int DELETE_BELOW_PLAYER = 0x200;

    private int routine = INIT;
    private final boolean piece;
    private int subtype;
    private boolean flipX;
    private boolean singleRowTables;
    /** oVar3E: this piece carries the solid (ObjCollapsePlatform_Delay, Main.asm:99-100; BreakUp 194-195, 286). */
    private boolean solidPiece;
    /** oVar2A: the delay in frames (ObjCollapsePlatform_Delay, Main.asm:94; BreakUp 203, 294). */
    private int delay;
    /** oVar2C: the fall speed, 16.16 (ObjCollapsePlatform_Fall, Main.asm:115-117). */
    private int fallSpeed;
    private int ySub;
    private int mapFrame;
    private boolean singleRowPiece;

    public ObjCollapsePlatform(ObjectSpawn spawn) {
        super(spawn, "Collapsing platform");
        this.piece = false;
        this.subtype = spawn.subtype();
    }

    private ObjCollapsePlatform(int x, int y, int mapFrame, int delay, boolean hFlip, boolean solidPiece,
                                int xRadius, int yRadius, boolean singleRow) {
        super(new ObjectSpawn(x, y, 0x20, 0, 0, false, 0), "Collapsing platform piece");
        this.piece = true;
        this.routine = DELAY;                                   // move.b oRoutine(a0),oRoutine(a1)
        this.priority = 3;
        this.tileWord = TILE;
        this.fallSpeed = PIECE_START_SPEED;
        this.mapFrame = mapFrame;
        this.delay = delay;
        this.flipX = hFlip;
        this.solidPiece = solidPiece;
        this.width = xRadius;
        this.yRadius = yRadius;
        this.singleRowPiece = singleRow;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return !piece && routine < DELAY; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return !piece && routine < DELAY && super.isCustomOutOfRange(cameraX);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case INIT -> {
                if (PpzStage.standDown(services(), this)) return;
                init();
                main();                                         // Init has no rts: it falls into Main
            }
            case MAIN -> main();
            case DELAY -> runDelay();
            case FALL -> fall();
            default -> { }
        }
    }

    /** ObjCollapsePlatform_Init (scddisasm/src/Level/Palmtree Panic/Objects/Collapsing Platform/Main.asm:31-68). */
    private void init() {
        routine = MAIN;
        priority = 3;
        tileWord = TILE;
        singleRowTables = (subtype & 0x80) != 0;
        flipX = hFlip();
        if ((subtype & 0x10) != 0) flipX = true;                // bset #0,oSprFlags(a0)
        mapFrame = subtype & 0xF;
        CdStageImage image = image();
        int sizes = sizesTable();
        if (image == null || sizes < 0) return;
        int entry = sizes + (short) image.u16(sizes + mapFrame * 2);
        int d1 = ((image.u8(entry) + 1) << 3) & 0xFF;           // addq.b #1,d1; asl.b #3,d1
        width = d1;
        int rows = (byte) image.u8(entry + 1);                  // move.b 1(a2,d0.w),d1; bpl.s .AbsDY; neg.b d1
        if (rows < 0) rows = -rows & 0xFF;
        yRadius = (((rows + 1) << 3) + 2) & 0xFF;               // addq.b #1,d1; asl.b #3,d1; addq.b #2,d1
    }

    /** ObjCollapsePlatform_Main (scddisasm/src/Level/Palmtree Panic/Objects/Collapsing Platform/Main.asm:73-88). */
    private void main() {
        if (!solidObject(Entry.TOP)) return;
        release();
        services().playSfx(PpzSounds.FM_A3);                    // move.w #FM_A3,d0; jsr PlayFMSound
        routine = DELAY;                                        // addq.b #2,oRoutine(a0)
        if ((subtype & 0x80) == 0) breakUpMultiRow();
        else breakUpSingleRow();
    }

    /** ObjCollapsePlatform_Delay (scddisasm/src/Level/Palmtree Panic/Objects/Collapsing Platform/Main.asm:93-106). */
    private void runDelay() {
        delay = (delay - 1) & 0xFFFF;                           // addi.w #-1,oVar2A(a0)
        if (delay == 0) routine = FALL;                         // bne.s .KeepOn; addq.b #2,oRoutine(a0)
        if (!solidPiece) return;                                // move.b oVar3E(a0),d0; beq.s .End
        if (solidObject(Entry.TOP) && delay == 0) release();    // beq.s .End; tst.w oVar2A; bne.s .End; GetOffObject
    }

    /** ObjCollapsePlatform_Fall (scddisasm/src/Level/Palmtree Panic/Objects/Collapsing Platform/Main.asm:114-128): the position is a 16.16 long, so this adds the speed to Y. */
    private void fall() {
        int position = ((y & 0xFFFF) << 16 | ySub & 0xFFFF) + fallSpeed;
        y = position >>> 16;
        ySub = position & 0xFFFF;
        fallSpeed += FALL_ACCELERATION;
        var rider = services().camera().getFocusedSprite();
        int d0 = (short) (y - (rider == null ? 0 : rider.getCentreY()));
        if (d0 > DELETE_BELOW_PLAYER) setDestroyed(true);
    }

    /** {@code jsr GetOffObject} for the player on the platform. */
    private void release() {
        var player = services().camera().getFocusedSprite();
        if (player != null) {
            ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
        }
    }

    /**
     * ObjCollapsePlatform_BreakUp_MultiRow (scddisasm/src/Level/Palmtree Panic/Objects/Collapsing Platform/Main.asm:134-223): pieces in rows from the bottom up, the
     * columns left to right ({@code hFlip}: right to left); frame bytes of {@code $80} or more leave a gap.
     */
    private void breakUpMultiRow() {
        CdStageImage image = image();
        var entry = PpzObjectCatalogue.entry(artProvider().payload());
        boolean flipped = (subtype & 0x10) != 0;                // suba.l a4,a4; btst #4,d0; lea ...,a4
        int sizes = entry.collapseSizes1();
        int a6 = sizes + (short) image.u16(sizes + (subtype & 0xF) * 2);
        int columns = image.u8(a6++);                           // movea.w d0,a5 (columns - 1)
        int d0 = columns << 3;
        int step = -0x10;                                       // move.w #$FFF0,d1
        if (!flipped) {
            d0 = -d0;
            step = -step;
        }
        int startX = (x + d0) & 0xFFFF;
        int rows = image.u8(a6++);                              // moveq #0,d6; move.b (a6)+,d6
        int d4 = ((rows << 3) + y) & 0xFFFF;
        int d2 = 9;
        int d6 = rows;
        var objects = services().objectManager();
        while (true) {
            int d3 = startX;
            int d1 = d2;
            for (int column = columns; column >= 0; column--) {
                int frame = image.u8(a6++);
                if ((frame & 0x80) == 0) {                      // bmi.w .Endxt
                    boolean top = d6 == 0;                      // tst.w d6; bne.s .NotLast
                    ObjCollapsePlatform piece = new ObjCollapsePlatform(d3, d4, frame, d1, flipped, top,
                            top ? 8 : 0, top ? 9 : 0, false);
                    objects.addDynamicObject(piece);
                    if (piece.getSlotIndex() < 0) {             // jsr FindObjSlot; bne.w .Solid
                        solidFailure();
                        return;
                    }
                }
                d3 = (d3 + step) & 0xFFFF;
                d1 = (d1 + 0xC) & 0xFFFF;
            }
            d4 = (d4 - 0x10) & 0xFFFF;
            d2 += 5;
            if (--d6 < 0) break;                                // dbf d6,.Loop
        }
        setDestroyed(true);                                     // .Delete: jmp DeleteObject
    }

    /**
     * ObjCollapsePlatform_BreakUp_SingleRow (scddisasm/src/Level/Palmtree Panic/Objects/Collapsing Platform/Main.asm:227-308): one row of pieces whose order follows the
     * subtype (bit 6 clear: bit 5 picks the direction; bit 6 set: the player's X speed, negated by bit 5).
     */
    private void breakUpSingleRow() {
        CdStageImage image = image();
        var entry = PpzObjectCatalogue.entry(artProvider().payload());
        int sizes = entry.collapseSizes2();
        int a6 = sizes + (short) image.u16(sizes + (subtype & 0x1F) * 2);
        int d5 = image.u8(a6++);                                // move.b (a6)+,d5 (pieces - 1)
        int d1 = image.u8(a6++);
        d1 = ((((d1 + 1) << 3) & 0xFF) + 2) & 0xFF;             // addq.b #1,d1; asl.b #3,d1; addq.b #2,d1
        int d4 = -(d5 << 3);
        int d3 = 0x10;
        int d6 = 1;
        boolean reverse;
        if ((subtype & 0x40) != 0) {                            // btst #6,d2
            var player = services().camera().getFocusedSprite();
            int speed = player == null ? 0 : player.getXSpeed();
            if ((subtype & 0x20) != 0) speed = (short) -speed;  // btst #5,d2; beq.s .GotSpeed; neg.w d0
            reverse = speed < 0;                                // tst.w d0
        } else {
            reverse = (subtype & 0x20) != 0;                    // lsl.b #2,d2 moves bit 5 into the sign
        }
        if (reverse) {                                          // bpl.s .InitX
            a6 += d5;
            d4 = -d4;
            d3 = -d3;
            d6 = -d6;
        }
        d4 = (d4 + x) & 0xFFFF;
        int d2 = 9;
        var objects = services().objectManager();
        for (int piece = d5; piece >= 0; piece--) {
            int frame = image.u8(a6);
            a6 += d6;
            ObjCollapsePlatform p = new ObjCollapsePlatform(d4, y, frame, d2, false, true, 8, d1, true);
            objects.addDynamicObject(p);
            if (p.getSlotIndex() < 0) {                         // jsr FindObjSlot; bne.w .Solid2
                solidFailure();
                return;
            }
            d4 = (d4 + d3) & 0xFFFF;
            d2 = (d2 + 0xC) & 0xFFFF;
        }
        setDestroyed(true);
    }

    /** .Solid and .Solid2: one TopSolidObject, GetOffObject if it stands, then DeleteObject. */
    private void solidFailure() {
        if (solidObject(Entry.TOP)) release();
        setDestroyed(true);
    }

    private CdStageImage image() {
        CdObjectArtProvider provider = artProvider();
        return provider == null ? null : provider.image();
    }

    private int sizesTable() {
        var provider = artProvider();
        if (provider == null) return -1;
        var entry = PpzObjectCatalogue.entry(provider.payload());
        return singleRowTables ? entry.collapseSizes2() : entry.collapseSizes1();
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!piece && routine == INIT) return;
        var provider = artProvider();
        if (provider == null) return;
        String kind = piece ? (singleRowPiece ? PpzObjectArt.PPZ_COLLAPSE4 : PpzObjectArt.PPZ_COLLAPSE3)
                : (singleRowTables ? PpzObjectArt.PPZ_COLLAPSE2 : PpzObjectArt.PPZ_COLLAPSE1);
        String key = PpzObjectArt.key(provider, kind, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, flipX, piece ? false : vFlip());
    }

    public int mapFrame() { return mapFrame; }
    public int delayFrames() { return delay; }
    public boolean isPiece() { return piece; }
}
