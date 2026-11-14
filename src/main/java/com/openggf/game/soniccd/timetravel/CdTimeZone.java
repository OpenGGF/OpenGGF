package com.openggf.game.soniccd.timetravel;

import com.openggf.game.soniccd.CdPeriod;

/**
 * The {@code timeZone} byte's low bits: {@code TIME_PAST}, {@code TIME_PRESENT},
 * {@code TIME_FUTURE} ({@code src/_Include/Main CPU Variables.inc:11-15}). Good and bad
 * futures share {@code FUTURE}; {@code goodFuture} tells them apart.
 */
public enum CdTimeZone {
    PAST, PRESENT, FUTURE;

    /** The {@code timeZone} value 0, 1 or 2. */
    public int nativeValue() {
        return ordinal();
    }

    /** The time zone a stage payload of {@code period} runs in. */
    public static CdTimeZone of(CdPeriod period) {
        return switch (period) {
            case PAST -> PAST;
            case PRESENT -> PRESENT;
            case GOOD_FUTURE, BAD_FUTURE -> FUTURE;
        };
    }

    /**
     * {@code .GetNewTime}: add the direction and keep the result in past..future
     * ({@code bpl}/{@code cmpi.b #3; bcs}, Sonic/Main.asm:625-636). A past post in the past
     * or a future post in the future warps to the same period.
     */
    public CdTimeZone step(int direction) {
        int next = ordinal() + direction;
        if (next < 0) return PAST;
        if (next >= 3) return FUTURE;
        return values()[next];
    }
}
