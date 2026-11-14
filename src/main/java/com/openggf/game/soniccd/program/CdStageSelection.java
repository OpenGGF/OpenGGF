package com.openggf.game.soniccd.program;

import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.CdProgramCatalogue;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One {@code StageSels} row (IPX.asm:730-927): the file-load command, {@code zoneAct}, time zone
 * and good-future flag that {@code StageSelect} (IPX.asm:710-728) and {@code TimeAttack}
 * (IPX.asm:1214-1238) copy before running the file. Read from IPX___.MMD on the disc.
 */
public record CdStageSelection(int index, CdCommand command, int zoneAct, int timeZone, boolean goodFuture) {
    /** Rows the stage-select menu can reach: {@code (Text_SelectionsEnd-Text_Selections)/$E} (Stage Select.asm MainLoop). */
    public static final int MENU_ENTRIES = 74;
    /** {@code dc.w command, zoneAct; dc.b timeZone, goodFuture} (IPX.asm:730-927). */
    private static final int ROW_BYTES = 6;
    private static final CdProgramCatalogue CATALOGUE = CdProgramCatalogue.forRelease(CdReleaseVariant.USA);

    public Optional<CdStagePayload> stage() { return CATALOGUE.stageForCommand(command); }

    public Optional<CdNativeProgram> program() { return CATALOGUE.programForCommand(command); }

    /** Every row of the table, including the four unused rows after the special-stage demo. */
    public static List<CdStageSelection> read(CdProgramData data) {
        byte[] table = data.bytes("stageSels");
        List<CdStageSelection> out = new ArrayList<>();
        for (int i = 0; i + ROW_BYTES <= table.length; i += ROW_BYTES) {
            int command = ((table[i] & 0xFF) << 8) | (table[i + 1] & 0xFF);
            int zoneAct = ((table[i + 2] & 0xFF) << 8) | (table[i + 3] & 0xFF);
            out.add(new CdStageSelection(i / ROW_BYTES, CdCommand.fromId(command), zoneAct,
                    table[i + 4] & 0xFF, table[i + 5] != 0));
        }
        return List.copyOf(out);
    }
}
