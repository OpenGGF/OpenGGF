package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.io.IOException;
import java.util.List;

/** ROM-backed {@code Run_PalRotationScript} owner installed from {@code word_7D9EA}. */
public final class SszMechaPaletteRotationChild extends AbstractObjectInstance
        implements RewindRecreatable {
    public static final int SCRIPT_POINTER = 0x7D9EA;
    private static final int NORMAL_PALETTE_RAM = 0xFC00;

    private SszMechaSonicObjectInstance parent;
    private boolean loaded;
    private boolean unavailable;
    private int dataAddress;
    private int cursor;
    private int timer;
    private int paletteLine;
    private int startColor;
    private int colorCount;

    private record RewindExtra(ObjectRefId parentId, boolean loaded, boolean unavailable, int dataAddress, int cursor,
                               int timer, int paletteLine, int startColor, int colorCount)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }

    public SszMechaPaletteRotationChild(ObjectSpawn spawn, SszMechaSonicObjectInstance parent) {
        super(spawn, "SSZ Mecha palette rotation");
        this.parent = parent;
    }

    public SszMechaPaletteRotationChild(ObjectSpawn spawn) { this(spawn, null); }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SszMechaPaletteRotationChild(context.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                parentId, loaded, unavailable, dataAddress, cursor, timer,
                paletteLine, startColor, colorCount));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            Object resolved = extra.parentId() == null ? null
                    : context.requireIdentityTable().resolveObject(extra.parentId(), true);
            parent = resolved instanceof SszMechaSonicObjectInstance boss ? boss : null;
            loaded = extra.loaded();
            unavailable = extra.unavailable();
            dataAddress = extra.dataAddress();
            cursor = extra.cursor();
            timer = extra.timer();
            paletteLine = extra.paletteLine();
            startColor = extra.startColor();
            colorCount = extra.colorCount();
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (parent == null || parent.isDestroyed()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        var registry = services().paletteOwnershipRegistryOrNull();
        if (registry != null && registry.isPaletteRotationDisabled()) return;
        if (!loaded && !load()) return;
        if (--timer >= 0) return;
        advance();
    }

    private boolean load() {
        try {
            int displacement = services().rom().read16BitAddr(SCRIPT_POINTER);
            int header = services().rom().read32BitAddr(SCRIPT_POINTER + 4) & 0x00FF_FFFF;
            int destination = services().rom().read16BitAddr(header);
            colorCount = Byte.toUnsignedInt(services().rom().readByte(header + 2)) + 1;
            paletteLine = ((destination - NORMAL_PALETTE_RAM) >>> 5) & 3;
            startColor = ((destination - NORMAL_PALETTE_RAM) & 0x1F) >>> 1;
            dataAddress = header + displacement;
            cursor = dataAddress;
            loaded = true;
            return true;
        } catch (IOException | RuntimeException failure) {
            unavailable = true;
            return false;
        }
    }

    private void advance() {
        try {
            int command = services().rom().read16BitAddr(cursor);
            if ((command & 0x8000) != 0) {
                if (command != 0xFFFC) throw new IllegalStateException(
                        String.format("Unsupported SSZ palette command $%04X", command));
                cursor = dataAddress;
            }
            int[] colors = new int[colorCount];
            int[] indices = new int[colorCount];
            for (int index = 0; index < colorCount; index++) {
                colors[index] = services().rom().read16BitAddr(cursor);
                indices[index] = startColor + index;
                cursor += 2;
            }
            timer = services().rom().read16BitAddr(cursor) & 0xFF;
            cursor += 2;
            S3kPaletteWriteSupport.applyColors(services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(), services().graphicsManager(),
                    S3kPaletteOwners.SSZ_MECHA_SONIC,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    paletteLine, indices, colors);
        } catch (IOException | RuntimeException failure) {
            unavailable = true;
        }
    }

    public int cursorForTest() { return cursor; }
    public int timerForTest() { return timer; }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
