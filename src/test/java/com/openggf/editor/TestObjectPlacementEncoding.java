package com.openggf.editor;

import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.game.common.CommonPlacementParser;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestObjectPlacementEncoding {

    @Test
    void sonic1AndCommonEncodingsBuildTheirNativeRawWords() {
        ObjectSpawn s1 = new Sonic1ObjectPlacementEncoding().create(1, 0x345, 0x7F, 2, 3, true, 8);
        ObjectSpawn common = new CommonObjectPlacementEncoding().create(1, 0x345, 0xFF, 2, 3, true, 9);
        assertEquals(0xC345, s1.rawYWord());
        assertEquals(0xE345, common.rawYWord());
    }

    @Test
    void encodingsRejectValuesThatNativeRecordsCannotRepresent() {
        assertThrows(IllegalArgumentException.class,
                () -> new Sonic1ObjectPlacementEncoding().create(1, 0x1000, 1, 0, 0, false, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Sonic1ObjectPlacementEncoding().create(1, 1, 0x80, 0, 0, false, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CommonObjectPlacementEncoding().create(0xFFFF, 1, 1, 0, 0, false, 1));
    }

    @Test
    void commonParserPreservesDescendingFullXOrderInsideOnePlacementColumn() {
        byte[] objects = new byte[14];
        writeObject(objects, 0, 0x01C0, 0x0100, 1);
        writeObject(objects, 6, 0x0180, 0x0100, 2);
        objects[12] = (byte) 0xFF;
        objects[13] = (byte) 0xFF;
        assertEquals(java.util.List.of(0x01C0, 0x0180),
                CommonPlacementParser.parseObjectRecords(new RomByteReader(objects), 0)
                        .stream().map(ObjectSpawn::x).toList());

        byte[] rings = new byte[10];
        writeWord(rings, 0, 0x01C0); writeWord(rings, 2, 0x0100);
        writeWord(rings, 4, 0x0180); writeWord(rings, 6, 0x0100);
        writeWord(rings, 8, 0xFFFF);
        assertEquals(java.util.List.of(0x01C0, 0x0180),
                CommonPlacementParser.parseRingRecords(new RomByteReader(rings), 0)
                        .stream().map(com.openggf.level.rings.RingSpawn::x).toList());
    }

    private static void writeObject(byte[] bytes, int offset, int x, int y, int id) {
        writeWord(bytes, offset, x); writeWord(bytes, offset + 2, y);
        bytes[offset + 4] = (byte) id; bytes[offset + 5] = 0;
    }

    private static void writeWord(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8); bytes[offset + 1] = (byte) value;
    }
}
