package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Tidal Tempest's collapse floor, object $39 ({@code CollapseFloorObject}, objects/r4/collapse_floor.asm:3-176): a
 * top-solid strip {@code subtype} 8-pixel columns wide (tile {@code $4364}, priority 3, Y radius $11). Once stood on it
 * waits 8 frames, then every 8 frames (7 after the first) drops its trailing column as two falling pieces and moves its
 * centre by 8 towards the player's direction, until no column is left. The pieces are the same ID with
 * {@code oSubtype2} $FF ({@link Piece}). It is Quartz Quadrant's routine (objects/r5/collapse_floor.asm,
 * {@code shared.ObjCollapseFloor}) with the tile as an immediate and both pieces drawn with frame 8: the r5 parent
 * writes frames 8 and 9 into its pieces (:97, :104), the r4 piece's own init writes 8 (:137).
 *
 * <p>When the collapse starts the routine clears its saved-flags ID ({@code clr.b obj.state_id}, :47), so its layout
 * entry stays loaded after it deletes itself and the floor does not respawn.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class TtzCollapseFloor extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** move.w #8,obj.var_2a(a0) (:47); move.w #7 after each column (:121). */
    private static final int FIRST_DELAY = 8, NEXT_DELAY = 7;
    /** moveq #8,d0: the centre moves 8 per column, negated when the player moves left (:48-54). */
    private static final int COLUMN = 8;
    /** move.w #$A3,d0; jsr PlayFmSound (:55-56). */
    private static final int SFX_COLLAPSE = 0xA3;

    private int routine;
    private int columns;
    private int mapFrame;
    private int timer;
    private int shift;
    private boolean stateCleared;

    public TtzCollapseFloor(ObjectSpawn spawn) {
        super(spawn, "Collapse floor");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> { init(); wait(player); }                  // Routine0 falls into Routine2
            case 2 -> wait(player);
            case 4 -> shake();
            case 6 -> dropColumn(player);
            default -> { }
        }
    }

    /** CollapseFloorObject_1_Routine0 (objects/r4/collapse_floor.asm:20-38). */
    private void init() {
        routine = 2;
        priority = 3;
        yRadius = 0x11;
        tileWord = TtzObjectArt.COLLAPSE_FLOOR_TILE;
        columns = getSpawn().subtype() & 0xFF;
        mapFrame = (columns - 1) & 0xFF;                          // subq.b #1,d0; move.b d0,obj.sprite_frame
        width = (columns << 3) & 0xFF;                            // lsl.b #3,d1 into oXRadius and oWidth
    }

    /** CollapseFloorObject_1_Routine2 (objects/r4/collapse_floor.asm:40-59). */
    private void wait(PlayableEntity player) {
        if (solidObject(Entry.TOP)) {
            routine = 4;
            stateCleared = true;                                  // clr.b obj.state_id(a0)
            timer = FIRST_DELAY;
            int d0 = COLUMN;
            if (player != null && player.getXSpeed() < 0) d0 = -d0;   // tst.w player_object+obj.x_speed
            shift = d0;
            services().playSfx(SFX_COLLAPSE);
        }
    }

    /** CollapseFloorObject_1_Routine4 (objects/r4/collapse_floor.asm:63-71). */
    private void shake() {
        solidObject(Entry.TOP);
        if (--timer < 0) routine = 6;                             // subq.w #1,obj.var_2a; bpl.s
    }

    /** CollapseFloorObject_1_Routine6 (objects/r4/collapse_floor.asm:75-126). */
    private void dropColumn(PlayableEntity player) {
        // d3 = (subtype - 1) * 8, negated unless the floor shifts left (:77-85).
        int d3 = (columns - 1) << 3;
        if (shift >= 0) d3 = -d3;
        // Two SpawnObject pieces; a failed first allocation skips the second (:86-102, bne.w loc_20F342).
        if (spawnChild(new Piece(x + d3, y - 8, 4, tileWord, getSpawn()))) {
            spawnChild(new Piece(x + d3, y + 8, 0, tileWord, getSpawn()));
        }
        // loc_20F342: TopSolidObject; beq; jsr GetOffObject (:104-108).
        if (solidObject(Entry.TOP) && player != null) {
            ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
        }
        columns = (columns - 1) & 0xFF;
        if ((byte) columns <= 0) {                                // subq.b #1,obj.subtype(a0); ble.w DeleteObject
            deleteLoaded();
            return;
        }
        routine = 4;
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

    /** Only routine 2 ends in CheckObjectDespawn (:57-58); 4 and 6 only draw. */
    @Override protected boolean despawnsByCheckObjDespawn() { return routine <= 2; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 0) drawSheet(TtzObjectArt.COLLAPSE_FLOOR, mapFrame, false, false);
    }

    public int routine() { return routine; }
    public int columns() { return columns; }

    /** A falling piece: {@code oSubtype2} non-zero (objects/r4/collapse_floor.asm:128-176). */
    @com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
    static final class Piece extends TtzObject implements ObjectVisualFacing.NonDirectional {
        /** addi.l #$4000; cmpi.l #$160000; ble.s (:163-168). */
        private static final int GRAVITY = 0x4000, MAX_SPEED = 0x160000;
        private int routine;
        private int timer;
        private int speed;
        private long yFixed;

        Piece(int x, int y, int timer, int tileWord, ObjectSpawn parent) {
            super(new ObjectSpawn(x, y, parent.objectId(), 0, parent.renderFlags(), false, 0), "Collapse floor piece");
            this.timer = timer;                                   // move.w #4,obj.var_2a(a1) for the upper piece only
            this.tileWord = tileWord;
            this.yFixed = (long) y << 16;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            switch (routine) {
                case 0 -> {
                    // move.b #8,obj.sprite_frame; move.l #$08080308,obj.height(a0): oYRadius 8, oXRadius 8, priority 3,
                    // oWidth 8 (:137-140).
                    routine = 2;
                    yRadius = 8;
                    width = 8;
                    priority = 3;
                }
                case 2 -> { if (--timer < 0) routine = 4; }       // :144-150
                default -> {
                    // tst.b obj.sprite_flags(a0); bpl.s DeleteObject (:154-156).
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
            drawSheet(TtzObjectArt.COLLAPSE_FLOOR, 8, false, false);
        }
    }
}
