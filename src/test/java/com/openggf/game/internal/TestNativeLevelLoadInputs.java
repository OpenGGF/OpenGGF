package com.openggf.game.internal;

import com.openggf.data.RomByteReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestNativeLevelLoadInputs {
    @Test
    void matchesIndependentCanonicalVectorAndRetainsOriginalSelection() {
        byte[] rom = new byte[16];
        for (int index = 0; index < rom.length; index++) rom[index] = (byte) index;
        var selected = new ArrayList<>(List.of(
                new NativeLevelLoadInputs.RegionSelection(2, 3),
                new NativeLevelLoadInputs.RegionSelection(12, 2)));
        var choices = new ArrayList<>(List.of(2, 0));
        var input = NativeLevelLoadInputs.fromRom(new RomByteReader(rom), selected, choices);
        selected.clear();
        choices.set(0, 99);
        rom[2] = 99;
        // Independently calculated with Python hashlib + struct.pack('>i', ...).
        assertEquals("sha256:0af7440ad54b63621f8f2792ec056bc43c6d3dafeee757187a30dbe2bc789b74",
                input.fingerprint("level_data_decode"));
        assertNotEquals(input.fingerprint("level_data_decode"), input.fingerprint("level_setup"));
    }

    @Test
    void selectionOrderAndActualCartridgeIdentityMatter() {
        var rom = new RomByteReader(new byte[16]);
        var a = new NativeLevelLoadInputs.RegionSelection(2, 3);
        var b = new NativeLevelLoadInputs.RegionSelection(12, 2);
        String original = NativeLevelLoadInputs.fromRom(rom, List.of(a, b), List.of(2, 0))
                .fingerprint("level_setup");
        assertNotEquals(original, NativeLevelLoadInputs.fromRom(rom, List.of(b, a), List.of(2, 0))
                .fingerprint("level_setup"));
        assertNotEquals(original, NativeLevelLoadInputs.fromRom(rom, List.of(a, b), List.of(0, 2))
                .fingerprint("level_setup"));
        byte[] other = new byte[16];
        other[15] = 1;
        assertNotEquals(original, NativeLevelLoadInputs.fromRom(new RomByteReader(other),
                List.of(a, b), List.of(2, 0)).fingerprint("level_setup"));
        assertThrows(IndexOutOfBoundsException.class, () -> NativeLevelLoadInputs.fromRom(rom,
                List.of(new NativeLevelLoadInputs.RegionSelection(15, 2)), List.of()));
    }
}
