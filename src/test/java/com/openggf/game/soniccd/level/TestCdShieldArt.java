package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCdShieldArt {
    @Test
    void rawCopiesIncludeProvenAdjacentBlobContinuationsButNeverTheResidentTail() throws IOException {
        var profile = profile();
        byte[] file = input(profile);
        file[profile.range(ART_TIME_OVER).endExclusive()] = 77;
        file[profile.range(ART_TIME_STARS).endExclusive()] = 66;
        file[profile.range(ART_SHIELD).fileOffset() + 1024] = 55;
        var art = CdShieldArt.decode(profile, CdMmdImage.parse(file));
        assertEquals(5, art.copies().size());
        assertEquals(1024, art.copy(4).bytes().length());
        assertEquals(77, art.copy(4).bytes().unsignedByte(960));
        assertEquals(66, art.copy(2).bytes().unsignedByte(992));
        assertEquals(List.of(ART_TIME_OVER, ART_GAME_OVER), art.copy(4).segments().stream().map(CdShieldArt.SourceSegment::source).toList());
        assertEquals(List.of(ART_TIME_STARS, ART_DIAGONAL_SPRING), art.copy(2).segments().stream().map(CdShieldArt.SourceSegment::source).toList());
        assertEquals(960, art.copy(4).segments().getFirst().length());
        assertEquals(64, art.copy(4).segments().getLast().length());
        assertEquals(32, art.copy(2).segments().getLast().length());
        assertEquals(1152, art.uploadBytes());
        assertEquals(0xA880, art.vramDestination());
        assertEquals(0xFF1980, art.copyRamAddress());
        assertEquals(0xFF1D80, art.residentTail().nativeAddress());
        assertEquals(128, art.residentTail().length());
        assertEquals(CdShieldArt.ContentStatus.UNKNOWN, art.residentTail().status());
        assertThrows(UnsupportedOperationException.class, art::completeUploadBytes);
        assertThrows(UnsupportedOperationException.class, () -> art.copies().clear());
        assertThrows(UnsupportedOperationException.class, () -> art.copy(4).segments().clear());
        Arrays.fill(file, (byte) 0);
        assertEquals(77, art.copy(4).bytes().unsignedByte(960));
        assertThrows(IndexOutOfBoundsException.class, () -> art.copy(0).bytes().unsignedByte(1024));
    }

    @Test
    void pointerTagsWrongSlotsAndTruncatedFileBackedCopiesAreRejected() throws IOException {
        var profile = profile();
        byte[] tagged = input(profile);
        int table = profile.range(SHIELD_ART_INDEX).fileOffset();
        tagged[table] = 3;
        assertThrows(IllegalArgumentException.class, () -> CdShieldArt.decode(profile, CdMmdImage.parse(tagged)));
        byte[] file = input(profile);
        byte[] wrong = file.clone();
        wrong[table + 3]++;
        assertThrows(IllegalArgumentException.class, () -> CdShieldArt.decode(profile, CdMmdImage.parse(wrong)));
        byte[] shortFile = Arrays.copyOf(file, 0x34000);
        assertThrows(IllegalArgumentException.class, () -> CdShieldArt.decode(profile, CdMmdImage.parse(shortFile)));
    }

    private static CdR11aProfile profile() {
        return CdR11aProfile.forStage(CdReleaseVariant.USA, CdStageKey.parse("R11A"));
    }
    private static byte[] input(CdR11aProfile profile) {
        // Component-only input, deliberately not a forged known-media identity.
        byte[] file = new byte[0x40000];
        var data = ByteBuffer.wrap(file);
        int index = profile.range(SHIELD_ART_INDEX).fileOffset();
        CdR11aAsset[] ordered = {ART_SHIELD, ART_INV_STARS, ART_TIME_STARS, ART_GAME_OVER, ART_TIME_OVER};
        for (int slot = 0; slot < ordered.length; slot++) {
            data.putInt(index + slot * 4, profile.range(ordered[slot]).nativeAddress());
        }
        return file;
    }
}
