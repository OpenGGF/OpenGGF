package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Searchlight, object $38 in acts 1 and 2 ({@code SearchlightObject}, objects/r7/searchlight.asm:3-214): a
 * beam sprite pinned $70 below the camera's top that sweeps along a pattern chosen by its subtype
 * ({@code off_20F72A}, :72-81) and is drawn only on the level frames that match its secondary subtype
 * (:48-68), which dithers the beam. Subtypes 0, 1, 2, 3 step through a table of (frame, duration, flip,
 * X offset) rows ({@code loc_20F776}, :107-139); 4-6 show frame subtype-4 and 7-8 show frame subtype-6
 * flipped (:192-214).
 *
 * <p>Secondary subtype: bits 0-1 are the frame phase ({@code oVar30}); bit 2 selects a phase out of four
 * level frames instead of two (:50-52); bit 3 selects the front layer, priority 3 and tile $24AF (plus $2000
 * outside the present) instead of priority 1 and $A4AF (:26-37). The despawn check uses the placement X
 * ({@code oVar36}, :8-9, :25). {@code stage_frames} is the level frame counter.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSearchlight extends SszObject implements ObjectVisualFacing.Source {
    /** One row of the sweep tables: mapping frame, duration, flip, signed X offset. */
    private record Step(int frame, int duration, int flip, int xOffset) { }

    /** byte_20F74A (:92-103). */
    private static final Step[] SWEEP_A = {
        new Step(0, 0x0A, 0, 0), new Step(3, 0x14, 0, 0), new Step(2, 0x14, 0, 0x44), new Step(1, 0x14, 0, 0x0C),
        new Step(3, 0x0A, 0, 0), new Step(2, 0x1E, 1, -4), new Step(1, 0x14, 0, 0x0C), new Step(3, 0x14, 0, 0),
        new Step(1, 0x0A, 1, 0x40), new Step(3, 0x14, 0, 0), new Step(2, 0x14, 1, -0x10)};
    /** byte_20F7E4 (:150-158). */
    private static final Step[] SWEEP_B = {
        new Step(0, 0x1E, 0, 0), new Step(1, 0x1E, 0, 0x30), new Step(2, 0x1E, 0, 0x5C), new Step(1, 0x1E, 0, 0x30),
        new Step(0, 0x1E, 0, 0), new Step(1, 0x1E, 1, -0x30), new Step(2, 0x1E, 1, -0x5C), new Step(1, 0x1E, 1, -0x30)};
    /** byte_20F812 (:169-173). */
    private static final Step[] SWEEP_C = {
        new Step(0, 0x1E, 0, 0), new Step(1, 0x1E, 0, 0x30), new Step(2, 0x1E, 0, 0x5C), new Step(1, 0x1E, 0, 0x30)};
    /** byte_20F830 (:184-188). */
    private static final Step[] SWEEP_D = {
        new Step(0, 0x1E, 0, 0), new Step(1, 0x1E, 1, -0x30), new Step(2, 0x1E, 1, -0x5C), new Step(1, 0x1E, 1, -0x30)};

    private int originX;
    /** oVar30: the frame phase (:26-28). */
    private int phase;
    /** oVar3A, oVar3C: the row's remaining frames and the row index. */
    private int timer;
    private int row;
    private boolean drawThisFrame;

    public ObjSearchlight(ObjectSpawn spawn) {
        super(spawn, "Searchlight");
    }

    @Override protected String sheetKind() { return SszObjectArt.SEARCHLIGHT; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override protected int despawnX() { return originX; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
        // SearchlightObject_0_Routine2 (:39-68)
        y = (cameraY() + 0x70) & 0xFFFF;
        switch (spawn.subtype()) {
            case 0 -> sweep(SWEEP_A);
            case 1 -> sweep(SWEEP_B);
            case 2 -> sweep(SWEEP_C);
            case 3 -> sweep(SWEEP_D);
            case 4, 5, 6 -> mapFrame = Math.max(0, spawn.subtype() - 4);           // Routine8 (:192-200)
            case 7, 8 -> {                                                         // RoutineE (:204-214)
                mapFrame = Math.max(0, spawn.subtype() - 6);
                spriteFlip |= 1;
                statusFlip |= 1;
            }
            default -> { }
        }
        int frames = levelFrames();
        int phaseFrame = frames & 3;                                               // andi.b #3,d0
        if ((subtype2() & 4) == 0) phaseFrame &= 1;                                // loc_20F718: andi.b #1,d0
        drawThisFrame = phaseFrame == phase;
    }

    /** SearchlightObject_0_Routine0 (:19-37). */
    private void init() {
        routine += 2;
        width = 0x7F;
        yRadius = 0x7F;
        originX = x;
        phase = subtype2() & 3;
        priority = 1;
        tileWord = SszObjectArt.SEARCHLIGHT_TILES[0];
        if ((subtype2() & 8) != 0) {
            priority = 3;
            tileWord = SszObjectArt.SEARCHLIGHT_TILES[1];
            if (liveTimeZone() != 1) tileWord += 0x2000;
        }
        initialised = true;
    }

    /** loc_20F776 and loc_20F7BC (:107-139) over one table. */
    private void sweep(Step[] table) {
        if (timer == 0) {
            Step step = table[row];
            mapFrame = step.frame();
            timer = step.duration();
            x = (originX + step.xOffset()) & 0xFFFF;
            statusFlip &= ~1;
            spriteFlip &= ~1;
            if (step.flip() != 0) {
                statusFlip |= 1;
                spriteFlip |= 1;
            }
        }
        if (--timer == 0 && ++row >= table.length) row = 0;
    }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    /** DrawObject runs only on the frames of the beam's phase (:48-68). */
    @Override protected boolean calledDrawObjectThisFrame() { return drawThisFrame; }

    /** The routine's DrawObject precedes CheckObjectDespawn2 (:7-9), which then reads the bit cleared. */
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (drawThisFrame) super.appendRenderCommands(commands);
    }
}
