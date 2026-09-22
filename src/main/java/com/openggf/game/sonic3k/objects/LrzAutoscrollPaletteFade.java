package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_85E64 ($3A=3, subtype=0) and loc_85EE6 as allocated by the LRZ3 flash. */
public final class LrzAutoscrollPaletteFade extends AbstractObjectInstance implements RewindRecreatable {
    private boolean restoring, initialized, nativeStatus7;
    private int timer, steps = 7;

    public LrzAutoscrollPaletteFade() {
        this(false);
    }
    LrzAutoscrollPaletteFade(boolean restoring) {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "LRZ3PaletteFade");
        this.restoring = restoring;
    }
    @Override
    public LrzAutoscrollPaletteFade recreateForRewind(RewindRecreateContext context) {
        return new LrzAutoscrollPaletteFade();
    }
    boolean finished() {
        return nativeStatus7;
    }
    @Override
    public void update(int clock, PlayableEntity player) {
        if (nativeStatus7) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        var registry = services().paletteOwnershipRegistryOrNull();
        if (!initialized) {
            initialized = true;
            if (restoring)
                timer = 3;
            else if (registry != null)
                registry.setPaletteRotationDisabled(true);
        }
        timer = (short) (timer - 1);
        if (timer >= 0)
            return;
        timer = 3;
        // sub_85EB4 raises each channel independently; sub_85F2A only lowers toward Target.
        for (int line = 0; line < 4; line++) {
            byte[] data = normalLine(services(), line);
            byte[] target = restoring && registry != null ? registry.targetSegaData(line, 0, 16) : null;
            for (int i = 0; i < 32; i += 2) {
                int word = ((data[i] & 255) << 8) | (data[i + 1] & 255);
                int destination = target == null ? 0xEEE : ((target[i] & 255) << 8) | (target[i + 1] & 255);
                for (int shift = 0; shift <= 8; shift += 4) {
                    int current = (word >>> shift) & 14, end = (destination >>> shift) & 14;
                    if (!restoring && current < 14)
                        word += 2 << shift;
                    else if (restoring && current > end)
                        word -= 2 << shift;
                }
                data[i] = (byte) (word >>> 8);
                data[i + 1] = (byte) word;
            }
            S3kPaletteWriteSupport.applyLine(registry, services().currentLevel(), services().graphicsManager(),
                    "lrz.autoscroll.fade", S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE, line, data, true);
        }
        if (--steps < 0) {
            if (registry != null)
                registry.setPaletteRotationDisabled(false);
            nativeStatus7 = true; // Go_Delete_Sprite is observed before the following deletion dispatch.
        }
    }
    static byte[] normalLine(ObjectServices services, int line) {
        byte[] data = new byte[32];
        var palette = services.currentLevel().getPalette(line);
        for (int i = 0; i < 16; i++) {
            var color = palette.getColor(i);
            int word = ((((color.b & 255) * 7 + 127) / 255) << 9) | ((((color.g & 255) * 7 + 127) / 255) << 5)
                    | ((((color.r & 255) * 7 + 127) / 255) << 1);
            data[i * 2] = (byte) (word >>> 8);
            data[i * 2 + 1] = (byte) word;
        }
        return data;
    }
    @Override
    public boolean isPersistent() {
        return true;
    }
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {}
}
