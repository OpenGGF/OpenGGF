package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.Palette;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * ROM {@code Obj_HPZPaletteControl} (sonic3k.asm:133503-133528), allocated once by
 * {@code HPZ_ScreenEvent} after the entry fade. Each frame it selects
 * {@code Pal_HPZIntro} left of camera X {@code $460} and {@code Pal_HPZ} from there
 * on, and copies {@code $60} bytes into {@code Normal_palette_line_2} only when the
 * selection changes. The stored selection starts at 0, so no write happens until
 * the camera first crosses {@code $460}.
 */
public final class HPZPaletteControlObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int SWITCH_CAMERA_X = 0x460;
    private static final int FIRST_LINE = 1;
    private static final int LINE_COUNT = 3;

    /** {@code $3A(a0)}: 0 selects {@code Pal_HPZIntro}, 4 selects {@code Pal_HPZ}. */
    private int selection;

    public HPZPaletteControlObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HPZPaletteControl");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        int previous = selection;
        selection = (services().camera().getX() & 0xFFFF) >= SWITCH_CAMERA_X ? 4 : 0;
        if (selection == previous) {
            return;
        }
        int source = selection == 0
                ? Sonic3kConstants.HPZ_INTRO_PALETTE_ADDR
                : Sonic3kConstants.HPZ_MAIN_PALETTE_ADDR;
        byte[] lines;
        try {
            lines = services().romReader().slice(source, LINE_COUNT * Palette.PALETTE_SIZE_IN_ROM);
        } catch (IOException ex) {
            throw new IllegalStateException("HPZ palette control ROM palette", ex);
        }
        for (int line = 0; line < LINE_COUNT; line++) {
            S3kPaletteWriteSupport.applyContiguousPatch(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    S3kPaletteOwners.HPZ_PALETTE_CONTROL,
                    S3kPaletteOwners.PRIORITY_ZONE_EVENT,
                    FIRST_LINE + line,
                    0,
                    Arrays.copyOfRange(lines, line * Palette.PALETTE_SIZE_IN_ROM,
                            (line + 1) * Palette.PALETTE_SIZE_IN_ROM));
        }
    }

    public int selectionForTestPublic() {
        return selection;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}
