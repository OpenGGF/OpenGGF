package com.openggf.game.sonic2.competition.interlace;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceVramService.CpuWordWrite;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic2InterlaceCpuWordWrites {
    @Test void oddAddressSwapsAnAlignedWordInsteadOfStraddlingTheVramBoundary() {
        var service = service(); var retained = service.current();
        var image = service.publishCpuWordWrites(List.of(new CpuWordWrite(0xFFFF, 2, List.of(0x1234, 0x5678))));
        assertEquals(0x3412, image.readWord(0xFFFE));
        assertEquals(0x7856, image.readWord(0));
        assertArrayEquals(new byte[65536], retained.bytes());
        assertSame(image, service.current());
    }

    @Test void everyUnsignedIncrementPreservesAddressWrappingAndAlternatingByteLanes() {
        for (int increment = 0; increment < 256; increment++) {
            var service = service();
            var values = List.of(0x0123, 0x4567, 0x89AB, 0xCDEF);
            service.publishCpuWordWrites(List.of(new CpuWordWrite(0xFFFD, increment, values)));
            byte[] expected = new byte[65536];
            for (int i = 0; i < values.size(); i++) {
                int address = (0xFFFD + increment * i) % 65536;
                int aligned = address / 2 * 2, value = values.get(i);
                expected[aligned] = (byte) ((address % 2 == 0) ? value / 256 : value % 256);
                expected[aligned + 1] = (byte) ((address % 2 == 0) ? value % 256 : value / 256);
            }
            assertArrayEquals(expected, service.current().bytes(), "VDP register15=" + increment);
        }
    }

    @Test void payloadIsFrozenAndOverlappingCommandsCommitInInputOrderWithoutQueueService() {
        var service = service(); var words = new ArrayList<>(List.of(0x1234, 0xABCD));
        var first = new CpuWordWrite(0xF800, 2, words); words.set(0, 0);
        service.queueDmaTransfer(0, 0xF800, 1);
        var before = service.capture();
        service.publishCpuWordWrites(List.of(first, new CpuWordWrite(0xF802, 0, List.of(0x5678, 0xFEDC))));
        assertEquals(0x1234, service.current().readWord(0xF800));
        assertEquals(0xFEDC, service.current().readWord(0xF802));
        assertEquals(before.pending(), service.capture().pending());
        assertEquals(before.plc(), service.capture().plc());
        assertEquals(before.sonicLast(), service.capture().sonicLast());
        assertThrows(UnsupportedOperationException.class, () -> first.words().set(0, 7));
    }

    @Test void completeTransactionAndPayloadValidationPrecedePublication() {
        var service = service(); var before = service.capture();
        var good = new CpuWordWrite(0, 2, List.of(0x1234));
        assertThrows(NullPointerException.class, () -> service.publishCpuWordWrites(Arrays.asList(good, null)));
        assertEquals(before, service.capture());
        for (int bad : new int[]{-1, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> new CpuWordWrite(bad, 2, List.of(0)));
            assertThrows(IllegalArgumentException.class, () -> new CpuWordWrite(0, 2, List.of(bad)));
        }
        for (int bad : new int[]{-1, 256})
            assertThrows(IllegalArgumentException.class, () -> new CpuWordWrite(0, bad, List.of(0)));
        assertEquals(before, service.capture());
    }

    @Test void destructiveRestoreAndForwardReplayRetainTheSamePhysicalImage() {
        var service = service();
        var writes = List.of(new CpuWordWrite(0xFFF0, 2, List.of(1, 2, 3, 4, 5, 6, 7, 8, 9)));
        service.publishCpuWordWrites(writes); var saved = service.capture();
        var retained = service.current();
        service.publishCpuWordWrites(List.of(new CpuWordWrite(0, 0, List.of(0xCAFE))));
        var forward = service.capture();
        service.resetForNativeEntry(); service.restore(saved);
        assertSame(retained, service.current());
        service.publishCpuWordWrites(List.of(new CpuWordWrite(0, 0, List.of(0xCAFE))));
        assertEquals(forward, service.capture());
    }

    private static Sonic2InterlaceVramService service() {
        // A closed ROM proves CPU publication never reads ROM or drains the DMA FIFO.
        var rom = Rom.fromReader(RomByteReader.fromBytes(new byte[32]), "CPU-only publication test");
        var service = new Sonic2InterlaceVramService(rom, new Sonic2PlcService(rom));
        rom.close(); return service;
    }
}
