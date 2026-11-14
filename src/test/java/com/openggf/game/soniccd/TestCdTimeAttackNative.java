package com.openggf.game.soniccd;

import com.openggf.game.soniccd.backupram.CdBackupRam;
import com.openggf.game.soniccd.backupram.CdBackupRamAssets;
import com.openggf.game.soniccd.backupram.CdBackupRamHost;
import com.openggf.game.soniccd.backupram.CdSaveRecord;
import com.openggf.game.soniccd.program.CdProgramData;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.timeattack.CdTimeAttackAssets;
import com.openggf.game.soniccd.timeattack.CdTimeAttackProgram;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native check of the Time Attack menu's state: the program's whole VRAM (tiles, both planes, the scroll
 * table) and CRAM against the VDP memories the USA disc left in BizHawk 2.11
 * ({@code native/rehost-timeattack/ta-records} and {@code record-entry}, recorded by
 * {@code tools/soniccd/backupram_native_probe.cs} from a save with crafted Time Attack records), and the
 * backup RAM after a record and its initials were entered.
 *
 * <p>Alignment: native spends lag frames inside the picture loads (a Nemesis decode and eight upload
 * passes) that the engine does not model, so each native button press (held four frames) is one press
 * here, and each state is compared after it has settled. The blinking items (the selected row's bar, the
 * lit EXIT/NEXT/GO item, the new rank's label) follow the frame counter, so a state matches when the
 * engine reaches it at any frame of a 200-frame window; the window and frame are reported. Not compared:
 * frame timing, the transitions between states. The record-entry run's IPX variables (time 1'32"16 on
 * Collision Chaos act 1, one life) were poked natively before the menu read them; everything after that
 * was driven by input.
 */
class TestCdTimeAttackNative {
    private static final int DOWN = 0x02, RIGHT = 0x08, B = 0x10, A = 0x40, UP = 0x01, START = 0x80;
    private static final int WINDOW = 200;

    private record Result(int bestDifference, int frame) { }

    private static byte[] nativeBytes(String relative) throws Exception {
        return Files.readAllBytes(CdTestMedia.nativeFile("rehost-timeattack/" + relative));
    }

    static int difference(CdProgramScreenRenderer.Frame frame, byte[] vram, byte[] cram) {
        int d = 0;
        for (int i = 0; i < 0x10000; i++) if (frame.tiles()[i] != vram[i]) d++;
        for (int i = 0; i < 64; i++) if (frame.palette()[i] != (((cram[2 * i] & 0xFF) << 8) | (cram[2 * i + 1] & 0xFF))) d++;
        return d;
    }

    /** Press once (or not), then run the window; the smallest VRAM+CRAM difference and where. */
    private static Result settle(CdTimeAttackProgram program, int press, String run, int nativeFrame) throws Exception {
        byte[] vram = nativeBytes(run + String.format("/vram-%05d.bin", nativeFrame));
        byte[] cram = nativeBytes(run + String.format("/cram-%05d.bin", nativeFrame));
        if (press != 0) program.frame(press);
        Result best = new Result(Integer.MAX_VALUE, -1);
        for (int f = 0; f < WINDOW && !program.done(); f++) {
            program.frame(0);
            int d = difference(program.screen(), vram, cram);
            if (d < best.bestDifference()) best = new Result(d, f);
        }
        return best;
    }

    private static CdBackupRam ram(CdDiscDataSource disc, byte[] image) {
        CdBackupRam ram = CdBackupRam.open(CdBackupRamAssets.initialRecord(disc), new CdBackupRamHost() {
            @Override public Optional<Images> load() { return Optional.of(new Images(image.clone(), null)); }
            @Override public void commit(Images images) { }
        }, 0);
        assertEquals(CdBackupRam.Init.READY, ram.initialize());
        return ram;
    }

    private static CdTimeAttackProgram program(CdDiscDataSource disc, CdBackupRam ram, int level, int time, int lives) {
        int[] flags = {0x30};
        return new CdTimeAttackProgram(new CdTimeAttackAssets(new CdProgramData(disc)), new CdTimeAttackProgram.Saves() {
            @Override public CdSaveRecord read() { return ram.readSave(); }
            @Override public void write(CdSaveRecord record) { ram.writeSave(record); }
        }, new CdTimeAttackProgram.Host() {
            @Override public int timeAttackLevel() { return level; }
            @Override public int timeAttackTime() { return time; }
            @Override public int lives() { return lives; }
            @Override public int timeAttackUnlock() { return 21; }
            @Override public int titleFlags() { return flags[0]; }
            @Override public void setTitleFlags(int f) { flags[0] = f; }
        });
    }

    @Test
    void menuStatesEqualNativeVram() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdTimeAttackProgram program = program(disc, ram(disc, nativeBytes("inputs/bram-ta-records.bin")), 0, 0, 0);
            try {
                // The list (Palmtree Panic), Down (Collision Chaos), A (its act panel), Down (act 2).
                int[][] steps = {{0, 2300}, {DOWN, 2500}, {A, 2700}, {DOWN, 3100}};
                List<String> report = new ArrayList<>();
                for (int[] step : steps) {
                    Result r = settle(program, step[0], "ta-records", step[1]);
                    report.add("native " + step[1] + ": " + r);
                    assertEquals(0, r.bestDifference(), "VRAM and CRAM at native frame " + step[1] + ": " + report);
                }
                System.out.println("CD Time Attack menu states: " + report);
                // Control: the settled act-2 panel is not native's act-1 panel.
                byte[] other = nativeBytes("ta-records/vram-02700.bin");
                assertTrue(difference(program.screen(), other, nativeBytes("ta-records/cram-02700.bin")) > 0,
                        "a different native state must not match");
            } finally {
                program.close();
            }
        }
    }

    @Test
    void recordAndInitialsEqualNativeBackupRam() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdBackupRam ram = ram(disc, nativeBytes("inputs/bram-ta-records.bin"));
            CdTimeAttackProgram program = program(disc, ram, 4, 0x00012010, 1);
            try {
                Result inserted = settle(program, 0, "record-entry", 2300);
                assertEquals(0, inserted.bestDifference(), "the record panel with the new second place: " + inserted);
                // B ends the rank blink; Down, A, Up, A, Down edit the initials; Start keeps them.
                for (int press : new int[] {B, DOWN, A, UP, A, DOWN}) settle(program, press, "record-entry", 2650);
                Result done = settle(program, START, "record-entry", 3590);
                assertEquals(0, done.bestDifference(), "the panel after the initials: " + done);
                byte[] engine = ram.internal().image();
                byte[] nativeRam = nativeBytes("record-entry/bram-03590.bin");
                List<String> diff = new ArrayList<>();
                // Block 0's first 16 bytes are firmware-private words the BIOS rewrites (TestCdBackupRamNative).
                for (int i = 0x10; i < engine.length; i++) {
                    if (engine[i] != nativeRam[i]) diff.add(String.format("$%04X %02X/%02X", i, engine[i], nativeRam[i]));
                }
                System.out.println("CD Time Attack record entry: backup RAM " + (8192 - 16 - diff.size()) + " of 8176 bytes equal native "
                        + diff + "; record panel " + inserted + "; final panel " + done);
                assertEquals(List.of(), diff);
                assertEquals(0x00012010, ram.readSave().time(3, 1), "Collision Chaos act 1, second place");
                assertEquals(0x24182000, ram.readSave().initials(3, 1), "Z N V");
            } finally {
                program.close();
            }
        }
    }
}
