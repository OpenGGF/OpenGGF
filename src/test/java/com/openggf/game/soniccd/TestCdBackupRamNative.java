package com.openggf.game.soniccd;

import com.openggf.game.soniccd.backupram.CdBackupRam;
import com.openggf.game.soniccd.backupram.CdBackupRamAssets;
import com.openggf.game.soniccd.backupram.CdBackupRamFile;
import com.openggf.game.soniccd.backupram.CdBackupRamHost;
import com.openggf.game.soniccd.backupram.CdBramEcc;
import com.openggf.game.soniccd.backupram.CdSaveRecord;
import com.openggf.game.soniccd.backupram.CdSaveVariables;
import com.openggf.game.soniccd.program.CdProgramData;
import com.openggf.game.soniccd.program.CdProgramFlow;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native check of the backup-RAM owners: the engine's device and record codec against the internal backup
 * RAM the USA BIOS left in BizHawk 2.11 (GPGX "CD BRAM", exported in device byte order by
 * {@code tools/soniccd/backupram_native_probe.cs} into {@code native/rehost-timeattack/}).
 *
 * <p>Compared byte for byte, all 8,192 bytes: the power-on device (GPGX formats an absent save like the
 * firmware's format), the device after {@code BRAMINIT} created {@code SONICCD____} on a fresh boot, and the
 * device after NEW GAME's {@code WriteSaveData} from a save with progress. Masked: bytes $00-$0F of block 0
 * in the NEW GAME dump, firmware-private words the BIOS rewrites during saves and disc loads
 * ({@code 0000 0002} then {@code FFFE 0002}, four copies); no file or game variable lies there and the engine
 * does not model them. Also compared: the save variables IPX holds in main-CPU RAM after {@code ReadSaveData}
 * of that save ({@code savedLevel} $FF0F02, {@code timeAttackUnlock} $FF0F18, {@code titleFlags} $FF0F1D,
 * {@code goodFutures} $FF0F1A, {@code timeStones} $FF0F20, {@code curSpecStage} $FF0F21).
 */
class TestCdBackupRamNative {
    private static final int BLOCK_ZERO_PRIVATE = 0x10;

    private static byte[] dump(String relative) throws Exception {
        return Files.readAllBytes(CdTestMedia.nativeFile("rehost-timeattack/" + relative));
    }

    private static List<String> differences(byte[] engine, byte[] nativeBytes, int maskedPrefix) {
        assertEquals(nativeBytes.length, engine.length, "device size");
        List<String> out = new ArrayList<>();
        for (int i = maskedPrefix; i < engine.length; i++) {
            if (engine[i] != nativeBytes[i]) out.add(String.format("$%04X engine %02X native %02X", i, engine[i] & 0xFF, nativeBytes[i] & 0xFF));
        }
        return out;
    }

    private static CdBackupRamHost stored(byte[] image) {
        return new CdBackupRamHost() {
            @Override public Optional<Images> load() { return Optional.of(new Images(image.clone(), null)); }
            @Override public void commit(Images images) { }
        };
    }

    @Test
    void freshBootDeviceEqualsTheNativeBackupRam() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdBackupRam ram = CdBackupRam.open(CdBackupRamAssets.initialRecord(disc), CdBackupRamHost.SESSION, 0);
            assertEquals(List.of(), differences(ram.internal().image(), dump("fresh-boot/bram-power-on.bin"), 0), "power-on");
            assertEquals(CdBackupRam.Init.READY, ram.initialize());
            byte[] engine = ram.internal().image();
            byte[] nativeBytes = dump("fresh-boot/bram-01600.bin");
            List<String> diff = differences(engine, nativeBytes, 0);
            System.out.println("CD backup RAM fresh boot: " + (8192 - diff.size()) + " of 8192 bytes equal native " + diff);
            assertEquals(List.of(), diff, "after BRAMINIT");
            // The directory block the BIOS wrote decodes to the game's entry.
            byte[] entry = CdBramEcc.decode(nativeBytes, 0x1F80);
            assertNotNull(entry, "native directory block corrects and passes its CRC");
            assertEquals("SONICCD____", new String(entry, 16, 11, java.nio.charset.StandardCharsets.US_ASCII));
            // Control: one changed byte must be seen.
            engine[0x2E8] ^= 1;
            assertEquals(1, differences(engine, nativeBytes, 0).size(), "the comparison sees a single changed byte");
        }
    }

    /**
     * The firmware's delete, driven natively from the RAM DATA screen (CD panel, the file, DELETE, Yes) on a
     * device holding SONICCD____ plus two files the engine's device wrote (TESTSAVE___ unprotected, 5 blocks;
     * PROTECTED__ protected, 2 blocks): native read both entries back, and deleting each must leave the same
     * device bytes, the stale directory entry and the free-block count included. The middle file's delete
     * moves the last file's data and entry down.
     */
    @Test
    void deletesLeaveTheSameDeviceBytesAsNative() throws Exception {
        byte[] three = dump("inputs/bram-three-files.bin");
        for (String[] c : new String[][] {{"PROTECTED__", "ramdata-cd/bram-04390.bin"}, {"TESTSAVE___", "ramdata-delete/bram-04190.bin"}}) {
            com.openggf.game.soniccd.backupram.CdBackupRamVolume device = com.openggf.game.soniccd.backupram.CdBackupRamVolume.of(three);
            assertEquals(3, device.fileCount());
            assertTrue(device.delete(CdBackupRamFile.name(c[0])));
            List<String> diff = differences(device.image(), dump(c[1]), BLOCK_ZERO_PRIVATE);
            System.out.println("CD backup RAM delete " + c[0] + ": " + (8192 - BLOCK_ZERO_PRIVATE - diff.size()) + " of "
                    + (8192 - BLOCK_ZERO_PRIVATE) + " unmasked bytes equal native " + diff);
            assertEquals(List.of(), diff, c[0]);
        }
    }

    /** BuRAMManager (IPX.asm:101-105) reads the save again, dropping GameOver's unwritten savedLevel (IPX.asm:332-338). */
    @Test
    void backupRamManagerReturnReadsTheStoredSave() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdProgramFlow flow = CdProgramFlow.read(new CdProgramData(disc));
            flow.attachBackupRam(CdBackupRam.open(CdBackupRamAssets.initialRecord(disc), stored(dump("inputs/bram-progress.bin")), 0));
            flow.enterTitle();
            flow.titleExited(CdProgramFlow.EXIT_LOAD_GAME);             // zone 2 from the save, written at RunR4
            flow.stageEnded(com.openggf.game.soniccd.program.CdStageResult.gameOver(1, false, 0));
            assertEquals(0x0200, flow.savedLevel(), "GameOver keeps zone 2, act 0 in memory only");
            flow.backupRamManagerReturned();
            assertEquals(0x0215, flow.savedLevel(), "ReadSaveData reads svZone and svTmAtkUnlock back as a word");
            flow.titleExited(CdProgramFlow.EXIT_BURAM_MANAGER);
            assertEquals(0x0215, flow.savedLevel());
        }
    }

    @Test
    void newGameWritesTheSameDeviceBytesAsNative() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            byte[] before = dump("inputs/bram-progress.bin");
            assertEquals(List.of(), differences(before, dump("new-game/bram-01600.bin"), 0), "native boot left the save as stored");
            CdProgramFlow flow = CdProgramFlow.read(new CdProgramData(disc));
            assertEquals(CdBackupRam.Init.READY, flow.attachBackupRam(
                    CdBackupRam.open(CdBackupRamAssets.initialRecord(disc), stored(before), 0)));

            // ReadSaveData at IPX Start, against main-CPU RAM after native's.
            byte[] ram = dump("new-game/ram68k-01600.bin");
            assertEquals(((ram[0xF02] & 0xFF) << 8) | (ram[0xF03] & 0xFF), flow.savedLevel(), "savedLevel (the word quirk)");
            assertEquals(ram[0xF18] & 0xFF, flow.timeAttackUnlock(), "timeAttackUnlock");
            assertEquals(ram[0xF1D] & 0xFF, flow.titleFlags(), "titleFlags");
            assertEquals(ram[0xF1A] & 0xFF, flow.goodFutures(), "goodFutures");
            assertEquals(ram[0xF21] & 0xFF, flow.currentSpecialStage(), "curSpecStage");

            flow.enterTitle();
            flow.titleExited(CdProgramFlow.EXIT_NEW_GAME);   // NewGame: clear progress, WriteSaveData
            List<String> diff = differences(flow.backupRam().internal().image(), dump("new-game/bram-02300.bin"), BLOCK_ZERO_PRIVATE);
            System.out.println("CD backup RAM after NEW GAME: " + (8192 - BLOCK_ZERO_PRIVATE - diff.size()) + " of "
                    + (8192 - BLOCK_ZERO_PRIVATE) + " unmasked bytes equal native " + diff);
            assertEquals(List.of(), diff);
            CdSaveVariables v = CdSaveRecord.decode(flow.backupRam().internal().read(CdSaveRecord.FILE_NAME).orElseThrow()).variables();
            assertEquals(new CdSaveVariables(0x0015, 0, 0x30, 21, 0x5A, 0, 0), v, "decoded record after the write");
            assertEquals(new CdBackupRamFile(CdSaveRecord.FILE_NAME, 0, 1, 11), flow.backupRam().internal().directory().get(0));
        }
    }
}
