package com.openggf.game.soniccd;

/** Payload suffixes in FID_R11A through FID_R83D (System.inc:12-99). */
public enum CdPeriod {
    PRESENT('A'), PAST('B'), GOOD_FUTURE('C'), BAD_FUTURE('D');

    private final char payloadSuffix;

    CdPeriod(char payloadSuffix) {
        this.payloadSuffix = payloadSuffix;
    }

    public char payloadSuffix() {
        return payloadSuffix;
    }

    public static CdPeriod fromPayloadSuffix(char suffix) {
        for (CdPeriod period : values()) {
            if (period.payloadSuffix == suffix) {
                return period;
            }
        }
        throw new IllegalArgumentException("Unknown CD period suffix: " + suffix);
    }
}
