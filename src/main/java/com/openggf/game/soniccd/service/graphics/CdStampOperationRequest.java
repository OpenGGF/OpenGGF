package com.openggf.game.soniccd.service.graphics;

import com.openggf.game.soniccd.device.CdStampRenderRequest;
import com.openggf.game.soniccd.device.CdStampRenderState;

import java.util.Objects;

/**
 * One complete, stable 2M stamp submission. The caller supplies live Word RAM separately at each
 * render; this request contains no prepared pixels, comparison rows, or recorder identity.
 *
 * Geometry, width, priority and horizontal offset are fixed until completion or reset. Native
 * gfx_render/gfx_update can reread registers mid-operation; this finite service does not implement
 * those MMIO writes. A changed descriptor requires a separate submission after completion/reset.
 * Captured partial progress is not a complete submission and must not be restarted through here.
 */
public record CdStampOperationRequest(CdStampRenderRequest renderRequest, int traceByteAddress,
                                      long imageNibbleAddress, int heightLines) {
    public CdStampOperationRequest {
        Objects.requireNonNull(renderRequest, "renderRequest");
        // gfx_start (GPGX 051d430d, core/cd_hw/gfx.c:609,649-653) starts in 19-bit nibble space.
        if (imageNibbleAddress < 0 || imageNibbleAddress > 0x7ffffL) {
            throw new IllegalArgumentException("Submission image cursor must fit 19 nibble-address bits");
        }
        CdStampRenderState.start(renderRequest, traceByteAddress, imageNibbleAddress, heightLines);
    }

    public CdStampRenderState initialRenderState() {
        return CdStampRenderState.start(renderRequest, traceByteAddress, imageNibbleAddress, heightLines);
    }

    /**
     * Source-model approximation in CD master clocks, not certified physical hardware timing.
     * gfx_start (GPGX 051d430d, core/cd_hw/gfx.c:658-668) counts four trace reads, two reads per
     * pixel, and one read/modify/write per four pixels; each access costs 3 sub-CPU cycles x 4.
     * The source explicitly does NOT emulate the additional 1..3 CPU-cycle refresh/contention
     * delays. Its TODO leaves the first misaligned access cost unresolved. Preserve that limit;
     * this formula does not close CD-05 or establish hardware parity.
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
