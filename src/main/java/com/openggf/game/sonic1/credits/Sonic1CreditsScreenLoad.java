package com.openggf.game.sonic1.credits;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.internal.NativeLevelLoadInputs;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission.Phase;
import com.openggf.game.internal.NativeLevelLoadPlan;
import com.openggf.level.NativeLevelLoadAccess;
import java.util.List;
import static com.openggf.game.sonic1.Sonic1RomProfile.resolve;

/** Real GM_Credits screen/font initialization, after its outgoing palette routine. */
final class Sonic1CreditsScreenLoad {
    static final int MAP_CREDITS = 0x17A8E;
    private Sonic1CreditsScreenLoad() { }

    static NativeLevelLoadInputs inputs(RomByteReader rom, int page) {
        if (page < 0 || page > 8) throw new IllegalArgumentException("invalid native credits page");
        int maps = resolve(rom, MAP_CREDITS);
        int row = maps + page * 2;
        int frame = maps + rom.readU16BE(row);
        int demo = resolve(rom, 0x5906) + page * 2;
        int zone = rom.readU16BE(demo) >>> 8;
        int font = resolve(rom, 0x6203A);
        return NativeLevelLoadInputs.fromRom(rom, List.of(
                new NativeLevelLoadInputs.RegionSelection(font, 2),
                new NativeLevelLoadInputs.RegionSelection(row, 2),
                new NativeLevelLoadInputs.RegionSelection(frame, 1 + rom.readU8(frame) * 5),
                new NativeLevelLoadInputs.RegionSelection(resolve(rom, 0x2178), 8),
                new NativeLevelLoadInputs.RegionSelection(demo, 2),
                new NativeLevelLoadInputs.RegionSelection(resolve(rom, 0x1DD16) + zone * 16, 16)),
                List.of(0xB400, maps, rom.readU16BE(font) & 0x7fff));
    }

    static void begin(int page, Runnable prepareFont, Runnable ready) {
        try {
            var inputs = inputs(RomByteReader.fromRom(GameServices.rom().getRom()), page);
            var level = GameServices.level();
            var prepare = List.of(new InitStep("ClearCreditsScreen", "GM_Credits (sonic.asm:3829-3843)",
                    () -> NativeLevelLoadAccess.clearScreenObjects(level)),
                    new InitStep("LoadCreditsFontAndLayout", "Nem_CreditText and Obj_CreditsText (sonic.asm:3845-3861)", prepareFont));
            var plan = new NativeLevelLoadPlan(List.of(new NativeLevelLoadPlan.Section(
                    Phase.LEVEL_SETUP, () -> inputs, () -> true, prepare, List.of())));
            NativeLevelLoadAccess.beginScreenInitialization(level, plan,
                    () -> NativeLevelLoadAccess.serviceZeroRoutineInitializationVInt(level), ready, () -> { });
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
