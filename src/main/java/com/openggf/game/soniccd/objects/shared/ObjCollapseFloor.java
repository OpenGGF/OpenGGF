package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Quartz Quadrant's collapse floor ({@code CollapseFloorObject}, objects/r5/collapse_floor.asm:3-177):
 * a top-solid strip {@code subtype} 8-pixel columns wide. Once stood on it waits 8 frames, then
 * every 8 frames (7 after the first) drops its trailing column as two falling pieces and moves its
 * centre by 8 towards the player's direction, until no column is left. The pieces are the same ID
 * with {@code oSubtype2} $FF ({@link Piece}). Tidal Tempest's and Metallic Madness's collapse floors
 * differ in instructions (objects/r4/collapse_floor.asm, objects/r8/collapse_floor.asm) and are
 * not this class.
 *
 * <p>When the collapse starts the routine clears its saved-flags ID ({@code clr.b obj.state_id},
 * :47), so its layout entry stays loaded after it deletes itself and the floor does not respawn.
 */
public final class ObjCollapseFloor extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** move.w #8,obj.var_2a(a0) (objects/r5/collapse_floor.asm:48); move.w #7 after each column (:119). */
    private static final int FIRST_DELAY = 8, NEXT_DELAY = 7;
    /** moveq #8,d0: the centre moves 8 per column, negated when the player moves left (:49-55). */
    private static final int COLUMN = 8;
    /** move.w #$A3,d0; jsr PlayFmSound (:56-57). */
    private static final int SFX_COLLAPSE = 0xA3;

    private int routine;
    private int columns;
    private int mapFrame;
    private int timer;
    private int shift;
    private boolean stateCleared;

    public ObjCollapseFloor(ObjectSpawn spawn) {
        super(spawn, "Collapse floor");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> { if (init()) wait(player); }
            case 2 -> wait(player);
            case 4 -> shake();
            case 6 -> dropColumn(player);
            default -> { }
        }
    }

    /** CollapseFloorObject_1_Routine0 (objects/r5/collapse_floor.asm:21-40). */
    private boolean init() {
        var level = cdLevel();
        if (level == null) return false;
        routine = 2;
        priority = 3;
        yRadius = 0x11;
        // move.w #$4350,d0; cmpi.b #2,act; bne.s; move.w #$43A0,d0 (:27-33).
        tileWord = SharedObjectCatalogue.offset(level.payload(), act() == 2 ? "collapseFloorTileAct3" : "collapseFloorTile");
        columns = getSpawn().subtype() & 0xFF;
        mapFrame = (columns - 1) & 0xFF;                   // subq.b #1,d0; move.b d0,obj.sprite_frame
        width = (columns << 3) & 0xFF;                     // lsl.b #3,d1 into oXRadius and oWidth
        return true;
    }

    /** CollapseFloorObject_1_Routine2 (objects/r5/collapse_floor.asm:42-61). */
    private void wait(PlayableEntity player) {
        if (!solidObject(ReevaluatingSolidObject.Entry.TOP)) return;
        routine = 4;
        clearStateId();                                    // clr.b obj.state_id(a0)
        timer = FIRST_DELAY;
        int d0 = COLUMN;
        if (player != null && player.getXSpeed() < 0) d0 = -d0; // tst.w player_object+obj.x_speed
        shift = d0;
        services().playSfx(SFX_COLLAPSE);
    }

    /** CollapseFloorObject_1_Routine4 (objects/r5/collapse_floor.asm:65-73). */
    private void shake() {
        solidObject(ReevaluatingSolidObject.Entry.TOP);
        if (--timer < 0) routine = 6;                      // subq.w #1,obj.var_2a; bpl.s
    }

    /** CollapseFloorObject_1_Routine6 (objects/r5/collapse_floor.asm:77-124). */
    private void dropColumn(PlayableEntity player) {
        // d3 = (subtype - 1) * 8, negated unless the floor shifts left (:79-87).
        int d3 = ((columns - 1) << 3);
        if (shift >= 0) d3 = -d3;
        var objects = services().objectManager();
        if (objects != null) {
            // Two SpawnObject pieces copying ID, flags, tile, mappings and position (:90-104).
            objects.addDynamicObject(new Piece(x + d3, y - 8, 4, 8, tileWord, getSpawn()));
            objects.addDynamicObject(new Piece(x + d3, y + 8, 0, 9, tileWord, getSpawn()));
        }
        // loc_20E3EE: TopSolidObject; beq; jsr GetOffObject (:106-110).
        if (solidObject(ReevaluatingSolidObject.Entry.TOP)) {
            if (player != null) ObjectManagerInternalAccess.releaseFromReevaluatingSolid(objects, player, this);
        }
        columns = (columns - 1) & 0xFF;
        if ((byte) columns <= 0) {                         // subq.b #1,obj.subtype(a0); ble.w DeleteObject
            deleteLoaded();
            return;
        }
        routine = 4;
        mapFrame = (mapFrame - 1) & 0xFF;
        width = (width - 8) & 0xFF;
        timer = NEXT_DELAY;
        x += shift;
        solidObject(ReevaluatingSolidObject.Entry.TOP);
    }

    private void clearStateId() {
        stateCleared = true;
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

    /** Only routine 2 ends in CheckObjectDespawn (objects/r5/collapse_floor.asm:59-61); 4 and 6 only draw. */
    @Override protected boolean despawnsByCheckObjDespawn() { return routine <= 2; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 0) drawFrame(SharedObjectArt.COLLAPSE_FLOOR, mapFrame, hFlip(), vFlip());
    }

    public int routine() { return routine; }
    public int columns() { return columns; }

    /** A falling piece: {@code oSubtype2} non-zero (objects/r5/collapse_floor.asm:128-177). */
    static final class Piece extends CdObject implements ObjectVisualFacing.NonDirectional {
        /** addi.l #$4000; cmpi.l #$160000; ble.s (objects/r5/collapse_floor.asm:163-167). */
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
                    // move.l #$08080308,obj.height(a0): oYRadius 8, oXRadius 8, priority 3, oWidth 8 (:143-146).
                    routine = 2;
                    yRadius = 8;
                    width = 8;
                    priority = 3;
                }
                case 2 -> { if (--timer < 0) routine = 4; }   // :150-156
                default -> {
                    // tst.b obj.sprite_flags(a0); bpl.s DeleteObject (:160-162).
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
            drawFrame(SharedObjectArt.COLLAPSE_FLOOR, mapFrame, hFlip(), vFlip());
        }
    }
}
