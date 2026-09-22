package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;

import java.io.IOException;
import java.util.List;

/** Obj_MechaSonic_Sparks, subtype zero from act-1 loc_7B888. */
public final class SszMechaSonicSparkChild extends AbstractObjectInstance
        implements SpawnCoordinateRewindRecreatable {
    private int parentSlot = -1;
    private boolean alternate;
    private boolean visible;
    private boolean flipped;
    private int mappingFrame = 4;

    public SszMechaSonicSparkChild(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "SSZMechaSonicSparks");
    }

    static void spawnFor(ObjectServices services, int parentSlot, int x, int y) {
        var manager = services.objectManager();
        if (manager == null) return;
        int slot = ObjectLifetimeOps.reserveFindNextFreeChildSlot(manager, parentSlot);
        if (slot < 0) return;
        try {
            var child = ObjectConstructionContext.with(services, slot,
                    () -> new SszMechaSonicSparkChild(x, y));
            child.parentSlot = parentSlot;
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, child, slot);
        } catch (RuntimeException | Error failure) {
            manager.releaseDynamicSlot(slot);
            throw failure;
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        AbstractObjectInstance parent = null;
        var manager = services().objectManager();
        if (manager != null) {
            for (var object : manager.getActiveObjects()) {
                if (object instanceof AbstractObjectInstance candidate
                        && candidate.getSlotIndex() == parentSlot && !candidate.isDestroyed()) {
                    parent = candidate;
                    break;
                }
            }
        }
        if (parent instanceof SszMechaSonicObjectInstance mecha && mecha.stopsSparks()) {
            ObjectLifetimeOps.deleteNoRespawn(this); // loc_7C8F8, not deferred Go_Delete.
            return;
        }
        var level = services().currentLevel();
        if (level == null || PaletteWriteSupport.segaWordFromColor(
                level.getPalette(1).getColor(9)) != 0xE88) {
            return;
        }
        services().playSfx(Sonic3kSfx.MECHA_SPARK.id);
        alternate = !alternate;
        int row = Sonic3kConstants.SSZ_MECHA_SPARK_OFFSETS_ADDR + (alternate ? 4 : 0);
        try {
            var reader = services().romReader();
            int dx = (byte) reader.readU8(row);
            int dy = (byte) reader.readU8(row + 1);
            mappingFrame = reader.readU8(row + 2);
            flipped = parent instanceof SszMechaSonicObjectInstance mecha
                    && mecha.renderFlippedForTest();
            // The ROM has no parent-liveness test. A freed SST supplies zero position;
            // after reuse the new occupant supplies position. Arbitrary replacement
            // $38/render-flags are not exposed by the semantic object API.
            int x = parent == null ? 0 : parent.getX();
            int y = parent == null ? 0 : parent.getY();
            updateDynamicSpawn((x + (flipped ? -dx : dx)) & 0xFFFF, (y + dy) & 0xFFFF);
            visible = true;
        } catch (IOException failure) {
            throw new IllegalStateException("Mecha sparks require the S3K ROM", failure);
        }
    }

    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 4; }
    @Override public int getOnScreenHalfWidth() { return 0x14; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }

    public boolean visibleForTest() { return visible; }
    public int mappingFrameForTest() { return mappingFrame; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_SPARKS);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), flipped, false, 1);
        }
    }
}
