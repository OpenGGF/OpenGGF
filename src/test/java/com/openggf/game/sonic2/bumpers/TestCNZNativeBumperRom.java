package com.openggf.game.sonic2.bumpers;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class TestCNZNativeBumperRom {
    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void shippedTablesRetainEveryRawAddressAndBothBoundaryForms() throws Exception {
        Rom rom = GameServices.rom().getRom();
        assertEquals("8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9", HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-1").digest(rom.readAllBytes())));
        CNZBumperDataLoader loader = new CNZBumperDataLoader();
        for (int act = 0; act < 2; act++) {
            CNZNativeBumperTable table = loader.loadNativeCompetition(rom, act);
            int base = act == 0 ? 0x1781A : 0x1795E;
            assertEquals(54, table.entryCount());
            for (int i = -1; i < 54; i++) {
                var entry = table.entry(i);
                assertEquals(base + i * 6, entry.address());
                assertEquals(rom.read16BitAddr(entry.address()), entry.id());
                assertEquals(rom.read16BitAddr(entry.address() + 2), entry.x());
                assertEquals(rom.read16BitAddr(entry.address() + 4), entry.y());
            }
            assertEquals(0xFFFF, table.entry(53).x());
        }
        CNZNativeBumperTable first = loader.loadNativeCompetition(rom, 0);
        assertEquals(new CNZNativeBumperTable.Entry(0x17814, 0x4EF9, 0, 0x1370), first.entry(-1));
        CNZNativeBumperTable second = loader.loadNativeCompetition(rom, 1);
        assertEquals(new CNZNativeBumperTable.Entry(0x1795E, 0, 0, 0), second.entry(0));
        assertEquals(0x18C0, second.entry(19).x()); assertEquals(0x1890, second.entry(20).x());
        assertEquals(0x2530, second.entry(46).x()); assertEquals(0x2530, second.entry(50).x());
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void actualCnz2RawInversionMakesNativeCursorHistoryObservable() throws Exception {
        CNZNativeBumperTable table = new CNZBumperDataLoader().loadNativeCompetition(GameServices.rom().getRom(), 1);
        var f = new TestSonic2NativeCNZBumpers.Fixture(table);
        f.move(0x1768, 0x1768);
        assertEquals(0x1795E + 13 * 6, f.bumpers.startAddress(0));
        assertEquals(0x1795E + 18 * 6, f.bumpers.endAddress(0));
        f.move(0x1779, 0x1768);
        assertEquals(0x1795E + 21 * 6, f.bumpers.endAddress(0));
        assertEquals(0x1795E + 18 * 6, f.bumpers.endAddress(1));
        f.move(0x1768, 0x1768);
        assertEquals(0x1795E + 21 * 6, f.bumpers.endAddress(0), "raw previous1890 halts backward walk");
        assertEquals(0x1795E + 18 * 6, f.bumpers.endAddress(1));
        Object captured = f.bumpers.rewindAdapter().capture();
        f.bumpers.swapNativeCompetitionSlots();
        assertEquals(0x1795E + 18 * 6, f.bumpers.endAddress(0));
        f.bumpers.swapNativeCompetitionSlots();
        assertEquals(captured, f.bumpers.rewindAdapter().capture());
        var snapshots = f.snapshots();
        var historical = snapshots.capture();
        f.move(2000, 7000);
        snapshots.restore(historical);
        f.bumpers.updateCursors();
        assertEquals(0x1795E + 21 * 6, f.bumpers.endAddress(0), "restore must not rebuild from camera");
        assertEquals(0x1795E + 18 * 6, f.bumpers.endAddress(1));
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void actualAct1RecordsBounceEachHumanOnlyInsideItsNativeCursorPair() throws Exception {
        var table = new CNZBumperDataLoader().loadNativeCompetition(GameServices.rom().getRom(), 0);
        var f = new TestSonic2NativeCNZBumpers.Fixture(table);
        f.move(0x410, 0x8F0);
        f.playerAt(0, 0x408, 0x540); // first ROM entry: id$A, X$410, Y$540
        f.playerAt(1, 0x8F8, 0x400); // fourth ROM entry: id$8, X$8F0, Y$400
        assertFalse(f.bumpers.checkPlayerCollision(1), "Init still uses P1's pair");
        f.bumpers.updateCursors();
        for (var p : f.players) { p.setXSpeed((short) 0x123); p.setYSpeed((short) 0x234); }
        assertTrue(f.bumpers.checkPlayerCollision(0)); assertTrue(f.bumpers.checkPlayerCollision(1));
        assertEquals(java.util.List.of(0, 1), f.sounds);
        for (var p : f.players) { assertEquals(0xA00, p.getXSpeed()); assertEquals(0x234, p.getYSpeed()); }
    }

    @Test void nativeParserDoesNotClampFilterSortOrLeakInputBytes() throws Exception {
        byte[] bytes = new byte[0x1795E + 324];
        ByteBuffer b = ByteBuffer.wrap(bytes);
        b.position(0x1781A - 6); b.putShort((short) 0x4EF9).putShort((short) 0).putShort((short) 0x1370);
        b.putShort((short) 0xE).putShort((short) 0x9000).putShort((short) 0x9000);
        b.putShort((short) 0).putShort((short) 0).putShort((short) 0);
        b.putShort((short) 4).putShort((short) 100).putShort((short) 256);
        b.putShort((short) 4).putShort((short) 100).putShort((short) 256);
        b.putShort(0x1781A + 53 * 6 + 2, (short) 0xFFFF);
        try (Rom rom = Rom.fromReader(new RomByteReader(bytes), "native bumper parser control")) {
            var loader = new CNZBumperDataLoader();
            var table = loader.loadNativeCompetition(rom, 0);
            assertEquals(0xE, table.entry(0).id()); assertEquals(0x9000, table.entry(0).y());
            assertEquals(0, table.entry(1).x()); assertEquals(100, table.entry(2).x());
            assertEquals(100, table.entry(3).x()); assertEquals(54, table.entryCount());
            bytes[0x1781A + 1] = 0;
            assertEquals(0xE, table.entry(0).id());
            assertThrows(IllegalArgumentException.class, () -> loader.loadNativeCompetition(rom, 2));
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void additiveNativeLoaderLeavesOrdinaryDecodeContractUntouched() throws Exception {
        Rom rom = GameServices.rom().getRom();
        var loader = new CNZBumperDataLoader();
        var before = loader.load(rom, 1);
        loader.loadNativeCompetition(rom, 1);
        assertEquals(before, loader.load(rom, 1));
        assertEquals(52, before.size());
        for (int i = 1; i < before.size(); i++) assertTrue(before.get(i - 1).x() <= before.get(i).x());
    }
}
