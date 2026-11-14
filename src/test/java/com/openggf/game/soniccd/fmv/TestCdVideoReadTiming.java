package com.openggf.game.soniccd.fmv;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdLoadTiming;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.ending.CdEndings;
import com.openggf.game.soniccd.program.CdProgramData;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.LoadTimeSimulationMode;
import com.openggf.game.timing.RomWorkBudgetScheduler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The three video reads in normal play ({@code CD_READ_READY}, {@link CdFmvStreamer#seekFor}). Native
 * ({@code rehost-programs}, CD PRG RAM peeks): the file engine's read request is the frame {@code feSectorsRead}
 * resets with the stream's sector count set ({@code ReadFMVSectors}, CD System Program/SP.asm:720-722; the count from
 * {@code .GetSectors}, :691-699), and the first bank is the frame the main program starts showing bank 0
 * ({@code CdFmvNative.bankStarts}). With {@code FAST} the engine's first bank, counted from the program's start
 * (where the engine issues the read), equals native's counted from the request: 75 for all three. With {@code NONE}
 * the documented fallback seek, {@link CdFmvStreamer#SEEK_VINTS}, gives 80 as before.
 */
class TestCdVideoReadTiming {
    @ParameterizedTest(name = "{0} {2}")
    @CsvSource({"opening-attract, OPENSTM, FAST", "vm-goodend, GOODENDSTM, FAST", "vm-badend, BADENDSTM, FAST",
            "opening-attract, OPENSTM, NONE", "vm-goodend, GOODENDSTM, NONE"})
    void firstBankFollowsTheRequestAsNative(String run, CdNativeFile file, LoadTimeSimulationMode mode) throws Exception {
        Map<Integer, Map<String, String>> frames = CdFmvNative.frames(run);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdFmvNative.usaCue())) {
            int sectors = CdLoadTiming.sectorCount(disc.assets().entry(file).size());
            int request = request(frames, sectors);
            int nativeBank = CdFmvNative.bankStarts(frames).getFirst() - request;
            HardwareTimingService timing = new HardwareTimingService(
                    RomWorkBudgetScheduler.oneWorkUnitAt(HardwareServiceBoundary.POST_OBJECTS),
                    new SonicCdGameModule().createLoadTimeProfile(mode, warning -> { }));
            CdFmvAssets assets = new CdFmvAssets(new CdProgramData(disc));
            var pcm = CdSubCpuAudio.ProgramPcm.detached();
            int engineBank = switch (file) {
                case OPENSTM -> {
                    CdOpeningProgram program = CdFmvScreen.opening(assets, disc.assets(), pcm, timing);
                    yield firstBank(program::frame, () -> program.streamer().banksTaken());
                }
                default -> {
                    var program = CdEndings.program(assets, disc.assets(), file == CdNativeFile.GOODENDSTM, pcm, timing);
                    yield firstBank(program::frame, () -> program.streamer().banksTaken());
                }
            };
            System.out.println("[scd-video-read] " + run + " " + mode + ": native request " + request + ", first bank +"
                    + nativeBank + "; engine first bank at program frame " + engineBank);
            if (mode == LoadTimeSimulationMode.FAST) assertEquals(nativeBank, engineBank, "first bank after the read request");
            else assertEquals(CdFmvStreamer.SEEK_VINTS + 60, engineBank, "NONE keeps the documented seek");
        }
    }

    /** The frame the read of {@code sectors} starts: sectors-read reset to 0 with that sector count set. */
    private static int request(Map<Integer, Map<String, String>> frames, int sectors) {
        for (int f : frames.keySet().stream().sorted().toList()) {
            Map<String, String> row = frames.get(f), before = frames.get(f - 1);
            if (before == null) continue;
            if (Integer.parseInt(row.get("seccnt"), 16) == sectors && Integer.parseInt(row.get("secread"), 16) == 0
                    && Integer.parseInt(before.get("secread"), 16) != 0) {
                return f;
            }
        }
        throw new IllegalStateException("no read of " + sectors + " sectors");
    }

    private static int firstBank(java.util.function.IntConsumer frame, java.util.function.IntSupplier banks) {
        for (int f = 0; f < 400; f++) {
            frame.accept(0);
            if (banks.getAsInt() > 0) return f;
        }
        throw new IllegalStateException("no bank within 400 frames");
    }
}
