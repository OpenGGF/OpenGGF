package com.openggf.game.soniccd.media;

import com.openggf.game.soniccd.CdReleaseVariant;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Observed release-header agreement, never pressing, revision, firmware or environment certification. */
public record CdDiscHeader(String discType, String volumeId, String buildDate, String productId, String market) {
    public enum Match { MATCH, MISMATCH, UNKNOWN }

    public static CdDiscHeader parse(byte[] bytes) throws IOException {
        if (bytes.length < 0x200) {
            throw new IOException("Truncated Sega CD disc header");
        }
        return new CdDiscHeader(text(bytes, 0, 16), text(bytes, 16, 11),
                text(bytes, 0x50, 8), text(bytes, 0x180, 16), text(bytes, 0x1f0, 16));
    }

    private static String text(byte[] bytes, int offset, int length) {
        return new String(Arrays.copyOfRange(bytes, offset, offset + length), StandardCharsets.US_ASCII)
                .replace("\0", " ").stripTrailing();
    }

    public Optional<CdReleaseVariant> observedVariant() {
        if (!discType.equals("SEGADISCSYSTEM")) {
            return Optional.empty();
        }
        return Arrays.stream(CdReleaseVariant.values()).filter(variant ->
                volumeId.equals(variant.discHeaderVolumeId()) && buildDate.equals(variant.sourceBuildDate())
                        && productId.equals(variant.productId()) && market.equals(variant.headerMarket())).findFirst();
    }

    public Match match(CdReleaseVariant requested) {
        return observedVariant().map(observed -> observed == requested ? Match.MATCH : Match.MISMATCH)
                .orElse(Match.UNKNOWN);
    }
}
