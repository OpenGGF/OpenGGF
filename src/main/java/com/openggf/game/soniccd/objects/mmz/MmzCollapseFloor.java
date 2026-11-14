package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Metallic Madness's collapse floor, object $25 in every index ({@code CollapseFloorObject}, objects/r8/collapse_floor.asm:3-207):
 * a strip {@code |subtype|} 8-pixel columns wide. A positive subtype makes it top-solid and collapsing: once stood on it
 * waits 8 frames, then every 8 frames (7 after the first) drops its trailing column as two falling pieces and moves its
 * centre by 8 towards the player's direction, until no column is left. A negative subtype is a plain solid strip that
 * never collapses (Routine8, :144-148). The pieces are the same ID with {@code oSubtype2} $FF ({@link Piece}).
 *
 * <p>Differs from Quartz Quadrant's copy (objects/r5/collapse_floor.asm, {@code objects/shared/ObjCollapseFloor}) in
 * the tile word, the negative-subtype branch and in ending its init routine with {@code rts} instead of running into
 * Routine2.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzCollapseFloor extends MmzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** move.w #8,obj.var_2a(a0) (objects/r8/collapse_floor.asm:64); move.w #7 after each column (:135). */
    private static final int FIRST_DELAY = 8, NEXT_DELAY = 7;
    /** moveq #8,d0: the centre moves 8 per column, negated when the player moves left (:65-71). */
    private static final int COLUMN = 8;
    /** move.w #$A3,d0; jsr PlayFmSound (:72-73). */
    private static final int SFX_COLLAPSE = 0xA3;

    private int routine;
    private int columns;
    private int mapFrame;
    private int timer;
    private int shift;
    private boolean stateCleared;

    public MmzCollapseFloor(ObjectSpawn spawn) {
        super(spawn, "Collapse floor");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> wait(player);
            case 4 -> shake();
            case 6 -> dropColumn(player);
            case 8 -> solidObject(Entry.SOLID);      // CollapseFloorObject_1_Routine8 (:144-148)
            default -> { }
        }
    }

    /** CollapseFloorObject_1_Routine0 (objects/r8/collapse_floor.asm:22-54): ends in rts, so nothing else runs this frame. */
    private void init() {
        yRadius = 0x11;                                    // move.b #$11,obj.height(a0)
        // move.w #$44D8,d0; cmpi.b #2,act; bne.s; move.w #$437A,d0 (:26-32).
        tileWord = act() == 2 ? MmzObjectArt.COLLAPSE_FLOOR_TILE_ACT3 : MmzObjectArt.COLLAPSE_FLOOR_TILE;
        int subtype = getSpawn().subtype() & 0xFF;
        int d0 = (byte) subtype < 0 ? (-subtype) & 0xFF : subtype;   // move.b obj.subtype,d0; bpl.s; neg.b d0 (:33-37)
        columns = subtype;
        mapFrame = (d0 - 1) & 0xFF;                        // subq.b #1,d0; move.b d0,obj.sprite_frame
        width = (d0 << 3) & 0xFF;                          // lsl.b #3,d1 into obj.width and obj.width_2
        if ((byte) subtype >= 0) {                         // tst.b obj.subtype(a0); bpl.s (:46-53)
            priority = 3;
            routine = 2;
        } else {
            priority = 1;
            routine = 8;
        }
    }

    /** CollapseFloorObject_1_Routine2 (objects/r8/collapse_floor.asm:58-77). */
    private void wait(PlayableEntity player) {
        if (!solidObject(Entry.TOP)) return;
        routine = 4;
        stateCleared = true;                               // clr.b obj.state_id(a0)
        timer = FIRST_DELAY;
        int d0 = COLUMN;
        if (player != null && player.getXSpeed() < 0) d0 = -d0; // tst.w player_object+obj.x_speed
        shift = d0;
        services().playSfx(SFX_COLLAPSE);
    }

    /** CollapseFloorObject_1_Routine4 (objects/r8/collapse_floor.asm:81-89). */
    private void shake() {
        solidObject(Entry.TOP);
        if (--timer < 0) routine = 6;                      // subq.w #1,obj.var_2a; bpl.s
    }

    /** CollapseFloorObject_1_Routine6 (objects/r8/collapse_floor.asm:93-140). */
    private void dropColumn(PlayableEntity player) {
        // d3 = (subtype - 1) * 8, negated unless the floor shifts left (:97-103).
        int d3 = (columns - 1) << 3;
        if (shift >= 0) d3 = -d3;
        var objects = services().objectManager();
        if (objects != null) {
            // Two SpawnObject pieces copying ID, flags, tile and mappings, then the position (:106-120).
            objects.addDynamicObject(new Piece(x + d3, y - 8, 4, 8, tileWord, getSpawn()));
            objects.addDynamicObject(new Piece(x + d3, y + 8, 0, 9, tileWord, getSpawn()));
        }
        // loc_20C788: TopSolidObject; beq; jsr GetOffObject (:122-126).
        if (solidObject(Entry.TOP) && player != null) {
            ObjectManagerInternalAccess.releaseFromReevaluatingSolid(objects, player, this);
        }
        columns = (columns - 1) & 0xFF;
        if ((byte) columns <= 0) {                         // subq.b #1,obj.subtype(a0); ble.w DeleteObject (:129-130, loc_20C848 :201)
            deleteLoaded();
            return;
        }
        routine = 4;                                       // subq.b #2,obj.routine(a0): 6 -> 4
        mapFrame = (mapFrame - 1) & 0xFF;
        width = (width - 8) & 0xFF;
        timer = NEXT_DELAY;
        x += shift;
        solidObject(Entry.TOP);
    }

    /** DeleteObject with the saved-flags ID cleared: the spawner keeps the entry loaded. */
    private void deleteLoaded() {
        var objects = services().objectManager();
        if (objects != null && stateCleared) {
            if (spawn.respawnTracked()) ObjectLifetimeOps.markSpawnRemembered(objects, spawn);
            else ObjectLifetimeOps.removeSpawnFromActive(objects, spawn);
        }
        setDestroyed(true);
    }

    /** Routines 2 and 8 end in CheckObjectDespawn (:75-77, :147-148); 4 and 6 only draw. */
    @Override protected boolean despawnsByCheckObjDespawn() { return routine == 2 || routine == 8; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 0) drawSheet(MmzObjectArt.COLLAPSE_FLOOR, mapFrame, hFlip(), vFlip());
    }

    public int routine() { return routine; }
    public int columns() { return columns; }

    /** A falling piece: {@code oSubtype2} non-zero (objects/r8/collapse_floor.asm:152-201). */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    static final class Piece extends MmzObject implements ObjectVisualFacing.NonDirectional {
        /** addi.l #$4000; cmpi.l #$160000; ble.s (objects/r8/collapse_floor.asm:187-191). */
        private static final int GRAVITY = 0x4000, MAX_SPEED = 0x160000;
        private final int mapFrame;
        private int routine;
        private int timer;
        private int speed;
        private long yFixed;

        Piece(int x, int y, int timer, int mapFrame, int tileWord, ObjectSpawn parent) {
            super(new ObjectSpawn(x, y, parent.objectId(), 0, parent.renderFlags(), false, 0),
                    "Collapse floor piece");
            this.timer = timer;                            // move.w #4,obj.var_2a(a1) for the upper piece only
            this.mapFrame = mapFrame;
            this.tileWord = tileWord;
            this.yFixed = (long) y << 16;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            switch (routine) {
                case 0 -> {
                    // move.l #$08080308,obj.height(a0): oYRadius 8, oXRadius 8, priority 3, oWidth 8 (:168-170).
                    routine = 2;
                    yRadius = 8;
                    width = 8;
                    priority = 3;
                }
                case 2 -> { if (--timer < 0) routine = 4; }   // :175-180
                default -> {
                    // tst.b obj.sprite_flags(a0); bpl.s DeleteObject (:185-186, :201).
                    if (!renderFlagOnScreen()) {
                        setDestroyed(true);
                        return;
                    }
                    speed = Math.min(speed + GRAVITY, MAX_SPEED);
                    yFixed += speed;
                    y = (int) (yFixed >> 16);
                }
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawSheet(MmzObjectArt.COLLAPSE_FLOOR, mapFrame, hFlip(), vFlip());
        }
    }
}
