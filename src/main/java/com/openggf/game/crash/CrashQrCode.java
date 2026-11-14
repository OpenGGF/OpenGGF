package com.openggf.game.crash;

import io.nayuki.qrcodegen.QrCode;

/**
 * The crash page's QR code of the issue form, for a player whose browser could not open or who
 * would rather report from another device. It encodes {@link CrashIssueLink#shortAddresses}: the
 * first address whose code still draws each module at least {@link #MIN_SCALE} pixels wide, with
 * a {@link #QUIET} module margin, inside the page's art box.
 */
public final class CrashQrCode {
    /** Smallest module size, in page pixels, a phone camera reliably reads off a screen. */
    static final int MIN_SCALE = 2;
    /** Light margin, in modules, around the code. The standard asks for four; the white tile is two. */
    static final int QUIET = 2;

    private final QrCode code;
    private final String address;

    private CrashQrCode(QrCode code, String address) {
        this.code = code;
        this.address = address;
    }

    /** The fullest address that fits {@code boxSize}; the bare form address always does. */
    static CrashQrCode of(CrashReport report, int boxSize) {
        CrashQrCode fallback = null;
        for (String address : CrashIssueLink.shortAddresses(report)) {
            CrashQrCode candidate = new CrashQrCode(QrCode.encodeText(address, QrCode.Ecc.LOW), address);
            fallback = candidate;
            if (candidate.scale(boxSize) >= MIN_SCALE) return candidate;
        }
        return fallback;
    }

    public String address() { return address; }

    public int size() { return code.size; }

    public boolean dark(int x, int y) { return code.getModule(x, y); }

    /** Whole pixels per module so the code and its margin fit {@code boxSize}. */
    public int scale(int boxSize) { return boxSize / (code.size + 2 * QUIET); }
}
