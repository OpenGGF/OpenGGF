package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCdR11aLoadPlan {
    @Test
    void tagsSelectPlcsWithoutBecomingAddressBitsAndTrailing0081StaysUninterpreted() {
        var index = CdR11aLoadPlan.decodeLevelIndex(index());
        assertEquals(3, index.levelArt().plcId());
        assertEquals(2, index.blocks().plcId());
        assertEquals(0x23C4EE, index.levelArt().nativeAddress());
        assertEquals(0x23B0A8, index.blocks().nativeAddress());
        assertEquals(0x0323C4EEL, index.levelArt().encoded());
        assertEquals(0x210000, index.chunksNativeAddress());
        assertEquals(0x0081, index.uninterpretedTrailing().unsignedWord(0));
        assertEquals(0x0505, index.paletteIds().unsignedWord(0));
        byte[] unknown = index();
        unknown[13] = (byte) 0x82;
        assertThrows(UnsupportedOperationException.class, () -> CdR11aLoadPlan.decodeLevelIndex(unknown));
        for (int offset : new int[]{0, 1, 3, 4, 7, 8, 14, 15}) {
            byte[] bad = index();
            bad[offset] ^= 1;
            assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodeLevelIndex(bad));
        }
        assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodeLevelIndex(new byte[15]));
    }

    @Test
    void plcBodiesUseCountMinusOneAndRejectUnprovenArtPointersOrTags() {
        var profile = profile(CdReleaseVariant.USA);
        byte[] body = ByteBuffer.allocate(8).putShort((short) 0).putInt(0x23C4EE).putShort((short) 0).array();
        var entries = CdR11aLoadPlan.decodePlcBody(profile, body);
        assertEquals(1, entries.size());
        assertEquals(ART_LEVEL_TILES, entries.getFirst().source());
        assertEquals(0x3C4EE, entries.getFirst().sourceRange().fileOffset());
        assertEquals(0, entries.getFirst().vramAddress());
        assertThrows(UnsupportedOperationException.class, () -> entries.clear());
        body[2] = 3; // A tagged LevelDataIndex pointer is not a PLC art address.
        assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodePlcBody(profile, body));
        ByteBuffer.wrap(body).putInt(2, profile.range(ART_BUTTON).nativeAddress());
        assertThrows(UnsupportedOperationException.class, () -> CdR11aLoadPlan.decodePlcBody(profile, body));
        ByteBuffer.wrap(body).putInt(2, profile.range(ART_GAME_OVER).nativeAddress());
        assertThrows(UnsupportedOperationException.class, () -> CdR11aLoadPlan.decodePlcBody(profile, body));
        body[1] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodePlcBody(profile, body));
        assertThrows(IllegalArgumentException.class,
                () -> CdR11aLoadPlan.decodePlcBody(profile, new byte[]{(byte) 0xFF, (byte) 0xFF}));
    }

    @Test
    void initialPositionUsesCentreWordsAndBoundsStayTypedData() {
        var centre = CdR11aLoadPlan.decodeInitialCentre(new byte[]{0, 64, 7, 0x6B});
        assertEquals(64, centre.centreX());
        assertEquals(0x76B, centre.centreY());
        var bounds = CdR11aLoadPlan.decodeBounds(new byte[]{0, 4, 0, 0, 0x28, (byte) 0x97, 0, 0, 7, 0x10, 0, 0x60});
        assertEquals(4, bounds.unusedWord());
        assertEquals(0, bounds.left());
        assertEquals(0x2897, bounds.right());
        assertEquals(0x710, bounds.bottom());
        assertEquals(0x60, bounds.cameraCentreY());
        assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodeInitialCentre(new byte[3]));
        assertThrows(IllegalArgumentException.class, () -> CdR11aLoadPlan.decodeBounds(new byte[11]));
    }

    @Test
    void unsupportedStagesWrongFilesAndDomainsFailBeforeAnyAssetDecode() throws IOException {
        assertThrows(UnsupportedOperationException.class,
                () -> CdR11aAssetLoader.load(null, CdReleaseVariant.USA, CdStageKey.parse("R11B")));
        assertThrows(UnsupportedOperationException.class,
                () -> CdR11aAssetLoader.load(null, CdReleaseVariant.USA, CdStageKey.parse("R82A")));
        var profile = profile(CdReleaseVariant.USA);
        assertThrows(IllegalArgumentException.class,
                () -> profile.verifyProgram(CdNativeFile.R12A, profile.domain(), null));
        assertThrows(IllegalArgumentException.class,
                () -> profile.verifyProgram(CdNativeFile.R11A, CdMmdImage.Domain.RELOCATED_COPY, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CdR11aProfile.FileRange(CdNativeFile.R11A, profile.domain(), 0x3FFFF, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new CdR11aProfile.FileRange(CdNativeFile.R11A, profile.domain(), Integer.MAX_VALUE, 1));
        // Matching headers alone never certify the synthetic bytes as the known MMD.
        byte[] fake = new byte[0x40000];
        ByteBuffer.wrap(fake).putInt(8, 0x200100).putInt(12, 0x20010C).putInt(16, 0x200112);
        assertThrows(IOException.class, () -> profile.verifyProgram(CdNativeFile.R11A, profile.domain(), CdMmdImage.parse(fake)));
    }

    @Test
    void regionalOffsetsAndUnknownEncodingsAreExplicitMetadata() {
        var usa = profile(CdReleaseVariant.USA);
        var jp = profile(CdReleaseVariant.JAPAN);
        var eu = profile(CdReleaseVariant.EUROPE);
        assertNotEquals(usa.expectedMmdSha256(), jp.expectedMmdSha256());
        assertEquals(jp.expectedMmdSha256(), eu.expectedMmdSha256());
        assertEquals(0x7906, usa.range(OBJECT_PLACEMENT_RECORDS).fileOffset());
        assertEquals(0x7914, jp.range(OBJECT_PLACEMENT_RECORDS).fileOffset());
        assertEquals(jp.range(OBJECT_PLACEMENT_RECORDS), eu.range(OBJECT_PLACEMENT_RECORDS));
        assertEquals(0x29EE, usa.range(LEVEL_START_LOC).fileOffset());
        assertEquals(0x29EA, eu.range(LEVEL_START_LOC).fileOffset());
        assertEquals(CdR11aAsset.Encoding.UNKNOWN, ART_BUTTON.encodingForReviewedConsumer());
        assertEquals(CdR11aAsset.Encoding.RAW_COPY, ART_TIME_OVER.encodingForReviewedConsumer());
        assertEquals(CdR11aAsset.Encoding.NEMESIS, ART_LEVEL_TILES.encodingForReviewedConsumer());
    }

    private static byte[] index() {
        return ByteBuffer.allocate(16).putInt(0x0323C4EE).putInt(0x0223B0A8).putInt(0x210000)
                .putShort((short) 0x0081).putShort((short) 0x0505).array();
    }
    private static CdR11aProfile profile(CdReleaseVariant variant) {
        return CdR11aProfile.forStage(variant, CdStageKey.parse("R11A"));
    }
}
