package com.openggf.game.soniccd.backupram;

import com.openggf.game.save.SaveManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The three backup-RAM owners without the disc: the firmware file system ({@link CdBackupRamVolume}), the
 * record codec ({@link CdSaveRecord}) and host persistence ({@link CdBackupRamHost}), wired by
 * {@link CdBackupRam}. Byte agreement with the native USA BIOS is {@code TestCdBackupRamNative}'s.
 */
class TestCdBackupRam {
    /** A stand-in for the disc's InitSaveData bytes: every time 5'00"00, title flags $10. */
    private static byte[] initial() {
        byte[] b = new byte[CdSaveRecord.FILE_BYTES];
        for (int i = 0; i < 84; i++) b[4 * i + 1] = 5;
        b[CdBackupRamAssets.layout("svTitleFlags")] = 0x10;
        return b;
    }

    private static final class RecordingHost implements CdBackupRamHost {
        final List<Images> commits = new ArrayList<>();
        Images stored;
        @Override public Optional<Images> load() { return Optional.ofNullable(stored); }
        @Override public void commit(Images images) { commits.add(images); stored = images; }
    }

    @Test
    void protectedBlocksRoundTripAndCorrectOneSymbol() {
        byte[] payload = new byte[32];
        for (int i = 0; i < 32; i++) payload[i] = (byte) (i * 7 + 3);
        byte[] stored = CdBramEcc.encode(payload);
        assertArrayEquals(payload, CdBramEcc.decode(stored, 0));
        stored[17] ^= 0x40;
        assertArrayEquals(payload, CdBramEcc.decode(stored, 0), "one corrupted byte is corrected");
    }

    @Test
    void formatLeavesTotalMinusThreeBlocks() {
        CdBackupRamVolume v = CdBackupRamVolume.formatted(0x2000);
        assertTrue(v.isFormatted());
        assertEquals(0x2000 / 64 - 3, v.freeBlocks());
        assertEquals(0, v.fileCount());
        assertFalse(CdBackupRamVolume.of(new byte[0x2000]).isFormatted());
    }

    @Test
    void filesPackUpwardAndDeleteClosesTheGap() {
        CdBackupRamVolume v = CdBackupRamVolume.formatted(0x2000);
        byte[] a = CdBackupRamFile.name("AAAAAAAAAAA"), b = CdBackupRamFile.name("BBBBBBBBBBB"), c = CdBackupRamFile.name("CCCCCCCCCCC");
        assertTrue(v.write(a, 0, 2, new byte[] {1}));
        assertTrue(v.write(b, 0xFF, 3, new byte[] {2, 3}));
        assertTrue(v.write(c, 0, 1, new byte[] {4}));
        // 125 free, minus 6 blocks, minus one directory block for the third entry.
        assertEquals(125 - 6 - 1, v.freeBlocks());
        assertEquals(List.of(1, 3, 6), v.directory().stream().map(CdBackupRamFile::startBlock).toList());
        assertArrayEquals(Arrays.copyOf(new byte[] {2, 3}, 96), v.read(b).orElseThrow(), "protected file reads back 32 bytes a block");
        assertTrue(v.delete(a));
        assertEquals(List.of(1, 4), v.directory().stream().map(CdBackupRamFile::startBlock).toList());
        assertEquals(4, v.read(c).orElseThrow()[0], "moved data follows its entry");
        // Two entries fill the first directory block, so the next one stays reserved (buram.c buram_update_counters).
        assertEquals(125 - 4 - 1, v.freeBlocks());
        assertFalse(v.write(CdBackupRamFile.name("DDDDDDDDDDD"), 0, 200, new byte[0]), "a file that does not fit is refused");
    }

    @Test
    void initializeCreatesTheSaveOnceAndCommitsIt() {
        RecordingHost host = new RecordingHost();
        CdBackupRam ram = CdBackupRam.open(initial(), host, 0);
        assertEquals(CdBackupRam.Init.READY, ram.initialize());
        assertEquals(1, host.commits.size());
        CdBackupRamFile file = ram.internal().search(CdSaveRecord.FILE_NAME).orElseThrow();
        assertEquals(new CdBackupRamFile(CdSaveRecord.FILE_NAME, 0, 1, 11), file);
        assertEquals(125 - 11, ram.internal().freeBlocks());
        // A second session finds it and writes nothing.
        CdBackupRam again = CdBackupRam.open(initial(), host, 0);
        assertEquals(CdBackupRam.Init.READY, again.initialize());
        assertEquals(1, host.commits.size());
    }

    @Test
    void readSaveDataReadsSavedLevelAsAWord() {
        CdBackupRam ram = CdBackupRam.open(initial(), CdBackupRamHost.SESSION, 0);
        ram.initialize();
        CdSaveRecord record = ram.readSave();
        record.store(new CdSaveVariables(0x0300, 0, 0x30, 21, 0, 0, 0));
        ram.writeSave(record);
        // move.w svZone,savedLevel: the low byte is svTmAtkUnlock (IPX.asm:1423).
        assertEquals(0x0315, ram.readVariables().savedLevel());
        assertEquals(21, ram.readVariables().timeAttackUnlock());
    }

    @Test
    void writeVariablesKeepsTheTimeAttackTables() {
        CdBackupRam ram = CdBackupRam.open(initial(), CdBackupRamHost.SESSION, 0);
        ram.initialize();
        CdSaveRecord record = ram.readSave();
        record.setTime(4, 1, 0x00012345);
        ram.writeSave(record);
        ram.writeVariables(new CdSaveVariables(0x0100, 1, 0x70, 3, 0, 0, 0x7F));
        assertEquals(0x00012345, ram.readSave().time(4, 1));
        assertEquals(0x7F, ram.readVariables().timeStones());
    }

    @Test
    void aFullDeviceDisablesSavingAndKeepsTheSaveInTheTemporaryBuffer() {
        CdBackupRamVolume full = CdBackupRamVolume.formatted(0x2000);
        assertTrue(full.write(CdBackupRamFile.name("OTHERGAME__"), 0, 120, new byte[0]));
        RecordingHost host = new RecordingHost();
        host.stored = new CdBackupRamHost.Images(full.image(), null);
        CdBackupRam ram = CdBackupRam.open(initial(), host, 0);
        assertEquals(CdBackupRam.Init.FULL, ram.initialize());
        assertTrue(ram.saveDisabled());
        assertTrue(ram.internal().search(CdSaveRecord.FILE_NAME).isEmpty());
        ram.writeVariables(new CdSaveVariables(0x0200, 0, 0x30, 6, 0, 0, 0));
        assertEquals(0x0206, ram.readVariables().savedLevel(), "the session keeps it through SaveDataTemp");
        assertEquals(0, host.commits.size(), "nothing durable changed");
    }

    @Test
    void unformattedDevicesStopBramInit() {
        RecordingHost host = new RecordingHost();
        host.stored = new CdBackupRamHost.Images(new byte[0x2000], null);
        assertEquals(CdBackupRam.Init.INTERNAL_UNFORMATTED, CdBackupRam.open(initial(), host, 0).initialize());
        host.stored = new CdBackupRamHost.Images(CdBackupRamVolume.formatted(0x2000).image(), new byte[0x10000]);
        assertEquals(CdBackupRam.Init.CART_UNFORMATTED, CdBackupRam.open(initial(), host, 0).initialize());
    }

    @Test
    void rewindRestoreNeverCommits() {
        RecordingHost host = new RecordingHost();
        CdBackupRam ram = CdBackupRam.open(initial(), host, 0);
        ram.initialize();
        var adapter = ram.rewindAdapter();
        var before = adapter.capture();
        assertSame(before, adapter.capture(), "an unchanged device reuses its snapshot");
        ram.writeVariables(new CdSaveVariables(0x0400, 0, 0x30, 12, 0, 0, 0));
        assertEquals(2, host.commits.size());
        adapter.restore(before);
        assertEquals(2, host.commits.size(), "restoring writes nothing to the host");
        assertEquals(0, ram.readVariables().timeAttackUnlock());
        ram.writeVariables(new CdSaveVariables(0x0400, 0, 0x30, 12, 0, 0, 0));
        assertEquals(3, host.commits.size(), "a later game write commits again");
    }

    @Test
    void durableHostRoundTripsThroughTheSaveManager(@TempDir Path root) {
        SaveManager saves = new SaveManager(root);
        CdBackupRamHost host = CdBackupRamHost.durable(saves);
        assertTrue(host.load().isEmpty());
        CdBackupRam ram = CdBackupRam.open(initial(), host, 0x8000);
        ram.initialize();
        ram.writeVariables(new CdSaveVariables(0x0500, 3, 0x30, 15, 0, 0, 0));
        saves.flushPendingWrites();
        CdBackupRamHost.Images loaded = CdBackupRamHost.durable(new SaveManager(root)).load().orElseThrow();
        assertArrayEquals(ram.internal().image(), loaded.internal());
        assertArrayEquals(ram.cartridge().image(), loaded.cartridge());
        CdBackupRam next = CdBackupRam.open(initial(), CdBackupRamHost.durable(new SaveManager(root)), 0);
        assertEquals(CdBackupRam.Init.READY, next.initialize());
        assertEquals(0x050F, next.readVariables().savedLevel());
    }

    /**
     * Host policy for a damaged save file (not the firmware's: native BRAMINIT stops on its message, which a host
     * file the engine wrote can never need): the file is kept aside as {@code .corrupt}, the game starts on freshly
     * formatted backup RAM, and the next commit writes a new slot.
     */
    @Test
    void aDamagedSaveFileIsKeptAsideAndTheGameStartsOnFreshBackupRam(@TempDir Path root) throws Exception {
        Path slot = root.resolve("soniccd").resolve("slot0.json");
        SaveManager saves = new SaveManager(root);
        String unformatted = java.util.Base64.getEncoder().encodeToString(new byte[CdBackupRamVolume.INTERNAL_BYTES]);
        for (String internal : List.of("not base64!", unformatted)) {
            saves.writeSlot("soniccd", 0, java.util.Map.of("format", "soniccd-backup-ram-1", "internal", internal));
            CdBackupRamHost host = CdBackupRamHost.durable(root);
            CdBackupRam ram = CdBackupRam.open(initial(), host, 0);
            assertEquals(CdBackupRam.Init.READY, ram.initialize(), "fresh backup RAM, not BRAMINIT's stop");
            saves.flushPendingWrites();
            try (var files = java.nio.file.Files.list(slot.getParent())) {
                assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("slot0.json.corrupt")),
                        "the damaged file is kept aside");
            }
            assertArrayEquals(ram.internal().image(), CdBackupRamHost.durable(root).load().orElseThrow().internal(),
                    "BRAMINIT's new record was committed as the slot");
        }
        // The file the save owner itself cannot read (not JSON) is quarantined by it, with the same outcome.
        java.nio.file.Files.writeString(slot, "{ damaged");
        assertTrue(CdBackupRamHost.durable(root).load().isEmpty());
    }
}
