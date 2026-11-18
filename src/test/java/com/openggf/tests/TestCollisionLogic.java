package com.openggf.tests;

import com.openggf.data.RomChannel;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_2)
public class TestCollisionLogic {

    @Test
    void decodesRetailEhzAndHtzPrimaryCollisionIndices() throws Exception {
        // ColP_EHZHTZ (s2.asm:90287) is the shared EHZ/HTZ primary archive at $44E50
        // in REV01. The digest comes from an independent decode of the archive.
        try (var channel = RomChannel.at(TestEnvironment.currentRom(), 0x44E50)) {
            byte[] indices = KosinskiReader.decompress(channel);
            assertEquals(0x300, indices.length);
            assertEquals("ed8f26a2b336bd2060d3e7fe14693cfd9b7780f49df74ec58bb6734a9b62de6e",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(indices)));
        }
    }
}
