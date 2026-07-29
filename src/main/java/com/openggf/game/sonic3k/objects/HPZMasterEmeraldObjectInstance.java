package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** ROM {@code Obj_HPZMasterEmerald} (SKL object $B0). */
public final class HPZMasterEmeraldObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int X = 0x1640;
    private static final int Y = 0x340;
    private static final int MAPPING_FRAME = 0xB;
    private static final int PALETTE_LINE = 3;
    private static final byte[] INCOMPLETE_COLORS = {
            0x06, (byte) 0xA0, 0x06, 0x60
    };
    private static final int[] ROTATION_COLOR_INDEX = {
            0, 1, 2, 3, 4, 5, 6, 5, 4, 3, 2, 1
    };
    private static final int[] ROTATION_DURATION = {
            0xF, 9, 9, 7, 7, 5, 5, 5, 7, 7, 9, 9
    };
    private static final byte[][] ROTATION_COLORS = {
            {0x06, (byte) 0xA0, 0x06, 0x60},
            {0x08, (byte) 0xC0, 0x06, (byte) 0x80},
            {0x0A, (byte) 0xC0, 0x06, (byte) 0x80},
            {0x0C, (byte) 0xE0, 0x08, (byte) 0x80},
            {0x0C, (byte) 0xE6, 0x06, (byte) 0xA2},
            {0x0C, (byte) 0xE8, 0x0A, (byte) 0xC0},
            {0x0E, (byte) 0xEC, 0x0C, (byte) 0xE8}
    };

    private int rotationStep;
    private int rotationTimer = ROTATION_DURATION[0];

    private record RewindExtra(int rotationStep, int rotationTimer)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public HPZMasterEmeraldObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HPZMasterEmerald");
    }

    @Override
    public HPZMasterEmeraldObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HPZMasterEmeraldObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        var gameState = services().gameState();
        byte[] colors = INCOMPLETE_COLORS;
        if (gameState != null && gameState.hasAllSuperEmeralds()) {
            colors = ROTATION_COLORS[ROTATION_COLOR_INDEX[rotationStep]];
            if (rotationTimer-- == 0) {
                rotationStep = (rotationStep + 1) % ROTATION_COLOR_INDEX.length;
                rotationTimer = ROTATION_DURATION[rotationStep];
            }
        }
        // loc_90700 owns Normal_palette_line_4 colors 1-2. Incomplete
        // emeralds use the fixed green pair; completion runs off_914CE.
        S3kPaletteWriteSupport.applyContiguousPatch(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                S3kPaletteOwners.HPZ_MASTER_EMERALD,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                PALETTE_LINE,
                1,
                colors);
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState() {
        return super.captureRewindState().withObjectSubclassExtra(
                new RewindExtra(rotationStep, rotationTimer));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot) {
        super.restoreRewindState(snapshot);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            rotationStep = extra.rotationStep();
            rotationTimer = extra.rotationTimer();
        }
    }

    @Override public int getX() { return X; }
    @Override public int getY() { return Y; }
    @Override public int getOutOfRangeReferenceX() { return X; }
    @Override public int getPriorityBucket() { return 4; }
    int mappingFrameForTest() { return MAPPING_FRAME; }
    int renderPaletteLineForTest() { return PALETTE_LINE; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_MASTER_EMERALD);
        if (renderer != null) {
            renderer.drawFrameIndex(MAPPING_FRAME, X, Y, false, false, PALETTE_LINE);
        }
    }
}
