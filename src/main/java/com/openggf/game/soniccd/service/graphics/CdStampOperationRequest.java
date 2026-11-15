package com.openggf.game.soniccd.service.graphics;

import com.openggf.game.soniccd.device.CdStampRenderRequest;
import com.openggf.game.soniccd.device.CdStampRenderState;

import java.util.Objects;

/**
 * One complete, stable 2M stamp submission. The caller supplies live Word RAM separately at each
 * render; this request contains no prepared pixels, comparison rows, or recorder identity.
 *
 * Geometry, width, priority and horizontal offset are fixed until completion or reset. Register
 * writes during an operation are not implemented by this finite service. A changed descriptor requires a separate submission after completion/reset.
 * Captured partial progress is not a complete submission and must not be restarted through here.
 */
public record CdStampOperationRequest(CdStampRenderRequest renderRequest, int traceByteAddress,
                                      long imageNibbleAddress, int heightLines) {
    public CdStampOperationRequest {
        Objects.requireNonNull(renderRequest, "renderRequest");
        // The image start register addresses 2M Word RAM: 19 bits of nibble address.
        if (imageNibbleAddress < 0 || imageNibbleAddress > 0x7ffffL) {
            throw new IllegalArgumentException("Submission image cursor must fit 19 nibble-address bits");
        }
        CdStampRenderState.start(renderRequest, traceByteAddress, imageNibbleAddress, heightLines);
    }

    public CdStampRenderState initialRenderState() {
        return CdStampRenderState.start(renderRequest, traceByteAddress, imageNibbleAddress, heightLines);
    }

    /**
     * Per-line cost in CD master clocks: {@code 12 * (4 + 2 * width + ceil((width + phase) / 4))},
     * with {@code phase} the image cursor's position within its 16-bit word. This is the reference
     * emulator's cost, not certified physical hardware timing: it equals the cost recorded for all
     * 1,052 operations in the owner ledger measured under BizHawk 2.11 ({@code tools/soniccd/native_owner_run.py}, fixtures {@code ~/Downloads/Sonic-CD/native/owner-ledger}; compared by {@code TestCdStampOwnerLedgerSourceModel}), which cover
     * widths 256 and 304 only. Other widths are unmeasured and no document
     * gives the formula; this does not close CD-05 or establish hardware parity.
     */
    public int cyclesPerLine() {
        int width = renderRequest.widthPixels();
        int offset = (int) (imageNibbleAddress & 3);
        return 12 * (4 + 2 * width + ((width + offset + 3) >>> 2));
    }

    /** Decode unsigned native words at submission; this grants no Word RAM ownership. */
    public static CdStampOperationRequest fromRegisterWords(int stampModeWord, int wordRamModeWord,
            int mapBaseWord, int verticalCellsWord, int horizontalDotsWord, int traceBaseWord,
            int imageBaseWord, int imageOffsetWord, int verticalDotsWord) {
        var request = CdStampRenderRequest.fromRegisterWords(stampModeWord, wordRamModeWord,
                mapBaseWord, verticalCellsWord, horizontalDotsWord);
        if ((wordRamModeWord & 4) != 0) {
            throw new IllegalArgumentException("1M Word RAM stamp service is not implemented");
        }
        var start = CdStampRenderState.fromStartRegisterWords(request, traceBaseWord,
                imageBaseWord, imageOffsetWord, verticalDotsWord);
        return new CdStampOperationRequest(request, start.traceByteCursor(), start.imageNibbleCursor(),
                start.remainingLines());
    }
}
