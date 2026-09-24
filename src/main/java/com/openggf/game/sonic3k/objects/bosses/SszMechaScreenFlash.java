package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.util.List;

/**
 * loc_7D09C: three brighten steps, thirty-frame hold, three restore steps and exact target copy.
 * The final-defeat variant runs loc_85E64 with eight whitening steps and $3A=7 instead.
 */
public final class SszMechaScreenFlash extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private final boolean holdWhite;
    private boolean started, nativeStatus7;
    private int timer;
    private int steps = 2;
    private boolean restoring;
    private boolean pendingDelete;
    private static final String OWNER = "s3k.ssz.mechaFlash";

    public SszMechaScreenFlash(ObjectSpawn spawn) {
        super(spawn, "SSZMechaScreenFlash");
        holdWhite = spawn.subtype() == 1;
        if (holdWhite) steps = 7;
    }

    /** loc_85E64 with subtype0 and $3A=7, selected by loc_7BC70. */
    static SszMechaScreenFlash finalWhiteFade() {
        return new SszMechaScreenFlash(new ObjectSpawn(0, 0, 0, 1, 0, false, 0));
    }
    boolean nativeFadeCompleted() { return nativeStatus7; }

    /** loc_7BE7E runs before AllocateObject, including when allocation subsequently fails. */
    static void saveTarget(ObjectServices services) {
        var registry = services.paletteOwnershipRegistryOrNull();
        if (registry == null) return;
        for (int line = 0; line < 4; line++) registry.applyTargetPatch(OWNER, line, 0, normalLine(services, line));
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (!started) {
            started = true;
            if (holdWhite && services().paletteOwnershipRegistryOrNull() != null)
                services().paletteOwnershipRegistryOrNull().setPaletteRotationDisabled(true);
        }
        timer = (short) (timer - 1);
        if (timer >= 0) return;
        timer = holdWhite ? 7 : restoring ? 3 : 5;
        var registry = services().paletteOwnershipRegistryOrNull();
        for (int line = 0; line < 4; line++) {
            byte[] data = normalLine(services(), line);
            byte[] target = restoring && registry != null ? registry.targetSegaData(line, 0, 16) : new byte[32];
            for (int color = 0; color < 16; color++) {
                int offset = color * 2;
                int word = ((data[offset] & 255) << 8) | (data[offset + 1] & 255);
                int end = ((target[offset] & 255) << 8) | (target[offset + 1] & 255);
                // sub_85EB4 / sub_85F2A change all three channels independently.
                for (int shift = 0; shift <= 8; shift += 4) {
                    int component = (word >>> shift) & 14;
                    if (!restoring && component < 14) word += 2 << shift;
                    else if (restoring && component > ((end >>> shift) & 14)) word -= 2 << shift;
                }
                data[offset] = (byte) (word >>> 8); data[offset + 1] = (byte) word;
            }
            writeLine(line, data);
        }
        if (--steps >= 0) return;
        if (holdWhite) {
            if (registry != null) registry.setPaletteRotationDisabled(false);
            nativeStatus7 = true; pendingDelete = true;
        } else if (!restoring) {
            restoring = true; steps = 2; timer = 29;
        } else {
            if (registry != null) for (int line = 0; line < 4; line++)
                writeLine(line, registry.targetSegaData(line, 0, 16));
            pendingDelete = true;
        }
        // The short loc_7D09C flash leaves palette rotation enabled; loc_85E64 owns the gate.
    }

    private void writeLine(int line, byte[] bytes) {
        S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(), services().currentLevel(),
                services().graphicsManager(), OWNER, S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE, line, bytes, true);
    }

    private static byte[] normalLine(ObjectServices services, int line) {
        byte[] bytes = new byte[32];
        for (int i = 0; i < 16; i++) {
            int word = PaletteWriteSupport.segaWordFromColor(services.currentLevel().getPalette(line).getColor(i));
            bytes[i * 2] = (byte) (word >>> 8); bytes[i * 2 + 1] = (byte) word;
        }
        return bytes;
    }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
