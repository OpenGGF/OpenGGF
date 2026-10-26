package com.openggf.game.soniccd.service.graphics;

import com.openggf.game.soniccd.device.CdStampRenderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.openggf.game.soniccd.device.CdStampRenderRequest.MapSize.PIXELS_256;
import static com.openggf.game.soniccd.device.CdStampRenderRequest.Priority.NORMAL;
import static com.openggf.game.soniccd.device.CdStampRenderRequest.StampSize.PIXELS_16;
import static org.junit.jupiter.api.Assertions.*;

class TestCdStampOperationRequest {
    @ParameterizedTest
    @CsvSource(textBlock = """
            0,0,48
            0,1,60
            0,3,60
            1,0,84
            2,0,108
            2,3,120
            3,0,132
            3,2,144
            4,0,156
            4,1,168
            5,3,192
            7,2,252
            8,0,264
            8,3,276
            256,0,6960
            256,1,6972
            304,0,8256
            511,0,13848
            511,3,13860
            """)
    void countsSourceAccessesIncludingMisalignedFourPixelGroups(int width, int offset, int expectedCost) {
        var request = new CdStampOperationRequest(geometry(width), 0, 0x20000L + offset, 1);
        assertEquals(expectedCost, request.cyclesPerLine());
    }

    @Test
    void decodesAllUnsignedWordsAtTheirNativeWidthsWithoutGrantingOwnership() {
        var request = CdStampOperationRequest.fromRegisterWords(0xffff, 0xfffb,
                0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff);

        assertEquals(CdStampRenderRequest.StampSize.PIXELS_32, request.renderRequest().stampSize());
        assertEquals(CdStampRenderRequest.MapSize.PIXELS_4096, request.renderRequest().mapSize());
        assertEquals(CdStampRenderRequest.Priority.PRESERVE, request.renderRequest().priority());
        assertTrue(request.renderRequest().repeat());
        assertEquals(0x38000, request.renderRequest().mapByteAddress());
        assertEquals(32, request.renderRequest().imageColumnStrideCells());
        assertEquals(511, request.renderRequest().widthPixels());
        assertEquals(0x3fff8, request.traceByteAddress());
        assertEquals(0x7ffffL, request.imageNibbleAddress());
        assertEquals(255, request.heightLines());
        assertEquals(13860, request.cyclesPerLine());
        assertEquals(0, request.initialRenderState().completedLines());
    }

    @Test
    void rejectsPartialOrOutOfRangeSubmissionDescriptions() {
        var geometry = geometry(1);
        assertThrows(IllegalArgumentException.class, () -> new CdStampOperationRequest(geometry, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampOperationRequest(geometry, -8, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampOperationRequest(geometry, 0x40000, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampOperationRequest(geometry, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampOperationRequest(geometry, 0, 0x80000, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampOperationRequest(geometry, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampOperationRequest(geometry, 0, 0, 256));
        assertThrows(NullPointerException.class, () -> new CdStampOperationRequest(null, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> geometry(-1));
        assertThrows(IllegalArgumentException.class, () -> geometry(512));
        // No API accepts completed-prefix comparison rows as a new operation.
        assertEquals(0, new CdStampOperationRequest(geometry, 0, 0, 0).initialRenderState().completedLines());
    }

    @Test
    void rejectsOneMAndMalformedRegisterWordsRatherThanFollowingNativePermissiveInequality() {
        assertThrows(IllegalArgumentException.class, () -> CdStampOperationRequest.fromRegisterWords(
                0, 4, 0, 0, 1, 0, 0, 0, 1));
        int[] words = {0, 0, 0, 0, 1, 0, 0, 0, 1};
        for (int index = 0; index < words.length; index++) {
            int[] invalid = words.clone();
            invalid[index] = -1;
            assertThrows(IllegalArgumentException.class, () -> decode(invalid), "negative word " + index);
            invalid[index] = 0x10000;
            assertThrows(IllegalArgumentException.class, () -> decode(invalid), "oversized word " + index);
        }
    }

    @Test
    void validatesUnsignedResidualsAndExplicitAdjacentEpochs() {
        assertEquals(0xffffffffL, new CdStampClock(2, 0xffffffffL).cycles());
        assertThrows(IllegalArgumentException.class, () -> new CdStampClock(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdStampClock(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdStampClock(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampClock(0, 0x100000000L));
        assertThrows(IllegalArgumentException.class, () -> new CdStampClock.Rebase(0, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampClock.Rebase(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampClock.Rebase(-1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampClock.Rebase(-1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampClock.Rebase(Long.MAX_VALUE, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampClock.Rebase(0, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new CdStampClock.Rebase(0, 1, 0x100000000L));
    }

    private static CdStampOperationRequest decode(int[] words) {
        return CdStampOperationRequest.fromRegisterWords(words[0], words[1], words[2], words[3],
                words[4], words[5], words[6], words[7], words[8]);
    }

    private static CdStampRenderRequest geometry(int width) {
        return new CdStampRenderRequest(PIXELS_16, PIXELS_256, false, NORMAL, 0x20000, 1, width);
    }
}
