package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.S3kEmeraldProgression;
import com.openggf.game.sonic3k.S3kSanctuaryRuntimeState;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;

import java.io.IOException;
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
    private record PaletteScript(
            int[] colorIndices, int[] durations, byte[][] colors, byte[] glowFrames) {
    }

    private int scriptByteOffset;
    private int rotationTimer;
    private int currentColorIndex;
    private PaletteScript paletteScript;
    private S3kEmeraldProgression progression;
    private S3kSanctuaryRuntimeState runtime;
    private HPZSSEntryControlObjectInstance parentRef;
    private boolean completionInitialized;
    private boolean completedAtSpawn;
    private boolean glowSpawned;
    private Boolean onScreenOverrideForTest;

    private record RewindExtra(int scriptByteOffset, int rotationTimer,
                               int currentColorIndex,
                               boolean completionInitialized, boolean completedAtSpawn,
                               boolean glowSpawned, ObjectRefId parentId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public HPZMasterEmeraldObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HPZMasterEmerald");
    }

    public HPZMasterEmeraldObjectInstance(
            ObjectSpawn spawn, HPZSSEntryControlObjectInstance controller) {
        this(spawn);
        progression = controller.progressionForChild();
        runtime = controller.runtimeForChild();
        parentRef = controller;
        completedAtSpawn = progression.states().stream().allMatch(state -> state == 3);
        completionInitialized = true;
    }

    private void latchCompletion() {
        if (completionInitialized) {
            return;
        }
        if (progression == null && tryServices() != null) {
            completedAtSpawn = services().gameState() != null
                    && services().gameState().hasAllSuperEmeralds();
            completionInitialized = true;
            return;
        }
        if (progression != null) {
            completedAtSpawn = progression.states().stream().allMatch(state -> state == 3);
            completionInitialized = true;
        }
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
        latchCompletion();
        if (parentRef != null) {
            progression = parentRef.progressionForChild();
            runtime = parentRef.runtimeForChild();
        }
        if (completedAtSpawn && !glowSpawned) {
            glowSpawned = true;
            spawnChild(() -> new HPZMasterEmeraldGlowObjectInstance(this));
        }
        if (completedAtSpawn && !ensurePaletteScript()) {
            return;
        }
        if (!paletteWriteVisible()) {
            return;
        }
        if (completedAtSpawn && runtime != null && runtime.transformationActive()) {
            return;
        }
        byte[] colors;
        if (!completedAtSpawn) {
            colors = INCOMPLETE_COLORS;
        } else {
            if (--rotationTimer < 0) {
                advancePaletteScript();
            }
            colors = paletteScript.colors()[currentColorIndex];
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

    private boolean paletteWriteVisible() {
        return onScreenOverrideForTest != null ? onScreenOverrideForTest : isOnScreen();
    }

    private boolean ensurePaletteScript() {
        if (paletteScript != null) {
            return true;
        }
        try {
            var reader = services().romReader();
            int base = Sonic3kConstants.HPZ_MASTER_EMERALD_PALETTE_SCRIPT_ADDR;
            int colorsBase = reader.readU32BE(base);
            int colorCount = reader.readU16BE(base + 4) + 1;
            int[] indices = new int[12];
            int[] durations = new int[12];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = reader.readU8(base + 6 + i * 2);
                durations[i] = reader.readU8(base + 7 + i * 2);
            }
            byte[][] colors = new byte[7][];
            for (int i = 0; i < colors.length; i++) {
                int colorAddress = colorsBase + reader.readU16BE(colorsBase + i * 2);
                colors[i] = reader.slice(colorAddress, colorCount * 2);
            }
            byte[] glowFrames = reader.slice(
                    Sonic3kConstants.HPZ_MASTER_EMERALD_GLOW_ANIMATION_ADDR, 12);
            paletteScript = new PaletteScript(indices, durations, colors, glowFrames);
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private void advancePaletteScript() {
        int nextOffset = scriptByteOffset + 2;
        int nextEntry = nextOffset / 2;
        if (nextEntry >= paletteScript.colorIndices().length) {
            nextOffset = 0;
            nextEntry = 0;
        }
        scriptByteOffset = nextOffset;
        currentColorIndex = paletteScript.colorIndices()[nextEntry];
        rotationTimer = paletteScript.durations()[nextEntry];
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parentRef)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(scriptByteOffset, rotationTimer, currentColorIndex,
                        completionInitialized, completedAtSpawn, glowSpawned, parentId));
    }

    @Override
    public void restoreRewindState(
            PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            scriptByteOffset = extra.scriptByteOffset();
            rotationTimer = extra.rotationTimer();
            currentColorIndex = extra.currentColorIndex();
            completionInitialized = extra.completionInitialized();
            completedAtSpawn = extra.completedAtSpawn();
            glowSpawned = extra.glowSpawned();
            if (extra.parentId() != null) {
                parentRef = (HPZSSEntryControlObjectInstance) context.requireIdentityTable()
                        .resolveObject(extra.parentId(), true);
                progression = parentRef.progressionForChild();
                runtime = parentRef.runtimeForChild();
            }
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

    int glowFrameForTest() {
        return paletteScript == null ? 0
                : Byte.toUnsignedInt(paletteScript.glowFrames()[scriptByteOffset / 2]);
    }

    void setOnScreenForTest(boolean onScreen) {
        onScreenOverrideForTest = onScreen;
    }
}
