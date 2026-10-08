package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_7C818..7C878: the fixed Master Emerald used by the SSZ2 transformation/fight. */
public final class SszMasterEmerald extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private boolean initialized;
    private boolean rotating;
    private boolean visible;
    private int frame;
    private int paletteDelay;
    private int paletteCursor;

    public SszMasterEmerald(ObjectSpawn spawn) { super(spawn, "SSZMasterEmerald"); }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (!initialized) {
            initialized = true;
            var camera = services().camera();
            updateDynamicSpawn((camera.getMaxX() + 0x100) & 0xFFFF,
                    (camera.getY() + 0xA8) & 0xFFFF);
            rotating = services().gameState().hasAllSuperEmeralds();
            return; // Native setup returns before either palette tick or Draw_Sprite.
        }
        if (rotating) rotatePalette();
        var state = (SszZoneRuntimeState) services().zoneRuntimeState();
        frame = state.cutsceneFlag(6) ? 1 : 0;
        if (state.act2EndingActive()) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        visible = true;
    }

    /** Run_PalRotationScript2: object-local $3A/$3B, first tick selects pair1, not pair0. */
    private void rotatePalette() {
        var registry = services().paletteOwnershipRegistryOrNull();
        if (registry != null && registry.isPaletteRotationDisabled()) return;
        paletteDelay = (byte) (paletteDelay - 1);
        if (paletteDelay >= 0) return;
        try {
            var rom = services().rom();
            int table = 0x7DD5A;
            int rows = rom.read32BitAddr(table);
            int count = rom.read16BitAddr(table + 4) + 1;
            paletteCursor = (paletteCursor + 2) & 0xFF;
            int row = rom.readBytes(table + 6 + paletteCursor, 1)[0];
            if (row < 0) {
                paletteCursor = 0;
                row = rom.readBytes(table + 6, 1)[0] & 0xFF;
            }
            paletteDelay = rom.readBytes(table + 7 + paletteCursor, 1)[0] & 0xFF;
            int address = rows + (short) rom.read16BitAddr(rows + row * 2);
            S3kPaletteWriteSupport.applyContiguousPatch(registry, services().currentLevel(),
                    services().graphicsManager(), S3kPaletteOwners.SSZ_MECHA_SONIC,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 2, 14,
                    rom.readBytes(address, count * 2));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 6; }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 0x18; }
    int frameForTest() { return frame; }
    public boolean isDrawing() { return visible && !isDestroyed(); }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_MASTER_EMERALD);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(frame, getX(), getY(), false, false, 0);
    }
}
