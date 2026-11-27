package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** The $23 layouts use subtypes 1, 3, 4 and $A (VMoveBlockObject, objects/r4/v_move_block.asm:3-24). */
@CameraRelativeDraw(CameraRelativeDraw.Use.ART_SELECTION)
public final class ObjVerticalMoveBlock extends TtzObject
        implements ReevaluatingSolidObject, SpawnRewindRecreatable, ObjectVisualFacing.NonDirectional {
    static final int ID = 0x23;
    static final int TILE = 0x446A;

    enum Shape {
        // VMoveBlockObject_1_Routine0 (objects/r4/v_move_block.asm:59-74).
        WAVE(0x10, 0x40, 0x40, 65, 0xC0, "vmove1", "vMoveMappings1"),
        // VMoveBlockObject_3_Routine0 (objects/r4/v_move_block.asm:172-189).
        TALL(0x20, 0x60, -0x50, 80, 0x40, "vmove2", "vMoveMappings2"),
        // VMoveBlockObject_4_Routine0 (objects/r4/v_move_block.asm:228-253).
        SPIKED(0x12, 0x30, 0x30, 48, 0xC0, "vmove3", "vMoveMappings3"),
        // VMoveBlockObject_9_Routine0 (objects/r4/v_move_block.asm:738-751).
        SHORT_WAVE(0x10, 0x10, 0, 48, 0, "vmove7", "vMoveMappings7");

        final int halfWidth;
        final int halfHeight;
        final int centreOffset;
        final int amplitude;
        final int startPhase;
        final String sheet;
        final String mappingColumn;

        Shape(int halfWidth, int halfHeight, int centreOffset, int amplitude, int startPhase,
              String sheet, String mappingColumn) {
            this.halfWidth = halfWidth;
            this.halfHeight = halfHeight;
            this.centreOffset = centreOffset;
            this.amplitude = amplitude;
            this.startPhase = startPhase;
            this.sheet = sheet;
            this.mappingColumn = mappingColumn;
        }

        static Shape forSubtype(int subtype) {
            return switch (subtype & 0x7F) {
                case 0, 1 -> WAVE;
                case 3 -> TALL;
                case 4 -> SPIKED;
                case 10 -> SHORT_WAVE;
                default -> null;
            };
        }
    }

    private final Shape shape;
    private boolean initialized;
    private boolean companion;
    private int originX;
    private int waveXOffset;
    private int homeY;
    private int phase;
    private int speedY;

    public ObjVerticalMoveBlock(ObjectSpawn spawn) {
        super(spawn, "Vertical move block");
        shape = Shape.forSubtype(spawn.subtype());
        originX = x;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (shape == null) return;
        if (!initialized) initialize();

        // loc_20C398 (objects/r4/v_move_block.asm:118-142) checks the old position
        // while rising and the new position while descending, including both on a turn.
        // The spiked form always checks after moving (VMoveBlockObject_4_Routine2, objects/r4/v_move_block.asm:271-293).
        if (shape != Shape.SPIKED && speedY < 0) solidObject(Entry.SOLID);
        move((TrigLookupTable.sinHex(phase) * shape.amplitude) >> 8);
        if (shape == Shape.SPIKED || speedY >= 0) solidObject(Entry.SOLID);
    }

    private void initialize() {
        initialized = true;
        priority = 3;
        tileWord = TILE;
        width = shape.halfWidth;
        yRadius = shape.halfHeight;
        homeY = (y + shape.centreOffset) & 0xFFFF;
        x = (x + waveXOffset) & 0xFFFF;
        if (!companion) phase = shape.startPhase;
        if (shape == Shape.SPIKED) {
            // loc_20C532 (objects/r4/v_move_block.asm:244-269).
            findObjSlot(new ObjSpikes(childSpawn(x, y, 0x22, 1, 0), getSlotIndex(), originX));
            if (!companion) {
                spawnCompanion(-0x50, phase);
                spawnCompanion(0x50, phase);
            }
        } else if (!companion && (shape == Shape.WAVE || shape == Shape.SHORT_WAVE)) {
            // byte_20C386 (objects/r4/v_move_block.asm:103-110) and
            // byte_20CB42 (objects/r4/v_move_block.asm:779-786).
            for (int side : new int[]{-1, 1}) {
                for (int step = 1; step <= 3; step++) {
                    int angle = shape == Shape.WAVE ? phase + side * step * 0x18 : -step * 0x20;
                    spawnCompanion(side * step * 0x20, angle);
                }
            }
        }
    }

    private void spawnCompanion(int offsetX, int angle) {
        // loc_20C33C and loc_20C36C (objects/r4/v_move_block.asm:76-99):
        // the long wave's companions offset X on their first turn, which can be next frame.
        boolean longWave = shape == Shape.WAVE;
        int subtype = longWave ? 1 : spawn.subtype();
        int childX = longWave ? x : (x + offsetX) & 0xFFFF;
        var child = new ObjVerticalMoveBlock(childSpawn(childX, y, ID, subtype, 0));
        child.companion = true;
        child.waveXOffset = longWave ? offsetX : 0;
        child.originX = originX;
        child.phase = angle & 0xFF;
        findObjSlot(child);
    }

    private void move(int offset) {
        int nextY = (homeY + offset) & 0xFFFF;
        // loc_20C3A2 (objects/r4/v_move_block.asm:123-139) stores an 8.8 speed word.
        speedY = (short) ((nextY - y) << 8);
        y = nextY;
        phase = (phase + 1) & 0xFF;
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }
    @Override public int solidYVelocity() { return speedY; }
    @Override protected boolean calledDrawObjectThisFrame() { return initialized; }
    @Override protected boolean despawnsByCheckObjDespawn() { return initialized; }
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }
    @Override protected int despawnX() { return originX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialized) drawSheet(shape.sheet, 0, false, false);
    }
}
