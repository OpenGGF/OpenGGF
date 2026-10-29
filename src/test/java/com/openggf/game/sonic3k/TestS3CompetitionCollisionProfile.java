package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.openggf.game.sonic3k.Sonic3kLevel.CollisionIndexEncoding.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit original-S3 decoder contract; ordinary locked-on byte reads remain distinct. */
class TestS3CompetitionCollisionProfile {
    @Test void threeArgumentProfileRetainsLockedOnCountAndEncoding() {
        var profile = new Sonic3kLevel.CollisionProfile(0x96100, 0x97100, 0x96000);
        assertEquals(256, profile.solidProfileCount());
        assertEquals(BYTE_AT_STRIDE_2, profile.indexEncoding());
        byte[] records = {1, (byte) 255, 0, (byte) 254};
        assertEquals(1, Sonic3kLevel.readCollisionIndex(records, 0));
        assertEquals(0, Sonic3kLevel.readCollisionIndex(records, 1, profile.indexEncoding()));
        assertEquals(511, Sonic3kLevel.readCollisionIndex(records, 0, UNSIGNED_WORD));
        assertEquals(254, Sonic3kLevel.readCollisionIndex(records, 1, UNSIGNED_WORD));
    }

    @Test void unsignedWordsRetainHighByteAfterEntry255AndRejectPartialRecords() throws Exception {
        byte[] records = new byte[0x600];
        records[600] = 1; records[601] = (byte) 255;
        assertEquals(511, Sonic3kLevel.readCollisionIndex(records, 300, UNSIGNED_WORD));
        assertEquals(1, Sonic3kLevel.readCollisionIndex(records, 300, BYTE_AT_STRIDE_2));
        Sonic3kLevel.validateWordCollisionBank(records, 768, 512);
        assertThrows(IOException.class, () -> Sonic3kLevel.validateWordCollisionBank(records, 769, 512));
        assertThrows(IOException.class, () -> Sonic3kLevel.validateWordCollisionBank(records, 1, 511));
        assertThrows(IOException.class, () -> Sonic3kLevel.validateWordCollisionBank(new byte[3], 1, 512));
        assertThrows(IllegalArgumentException.class, () -> Sonic3kLevel.readCollisionIndex(records, 768, UNSIGNED_WORD));
        assertThrows(IllegalArgumentException.class, () -> Sonic3kLevel.readCollisionIndex(records, -1, UNSIGNED_WORD));
    }

    @Test void explicitProfileChecksCompleteHeightsWidthsAnglesAndCount() throws Exception {
        try (var rom = Rom.fromReader(RomByteReader.fromBytes(new byte[0x4200]), "bounded solid profiles")) {
            new Sonic3kLevel.CollisionProfile(0x200, 0x2200, 0, 512, UNSIGNED_WORD).validate(rom);
            assertThrows(IOException.class, () -> new Sonic3kLevel.CollisionProfile(0x201, 0x2201, 0, 512, UNSIGNED_WORD).validate(rom));
            assertThrows(IOException.class, () -> new Sonic3kLevel.CollisionProfile(0x200, 0x2200, 0x4001, 512, UNSIGNED_WORD).validate(rom));
            assertThrows(IllegalArgumentException.class, () -> new Sonic3kLevel.CollisionProfile(0, 0, 0, 0, UNSIGNED_WORD));
            assertThrows(IllegalArgumentException.class, () -> new Sonic3kLevel.CollisionProfile(0, 0, 0, 65537, UNSIGNED_WORD));
        }
    }
}
