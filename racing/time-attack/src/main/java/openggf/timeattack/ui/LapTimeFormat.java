package openggf.timeattack.ui;

import com.openggf.mods.run.TrackKind;
import openggf.timeattack.TimeAttackTimeFormat;

import java.util.List;

/**
 * The one formatter for Competition times, so the HUD, the lobby and the results read the same
 * value the same way.
 *
 * <p>Arena times are shown as the ROM's competition clock shows them: each lap's frames become
 * hundredths through {@code sub_B0E4}'s table {@code byte_37E52 (sonic3k.asm:74906-74909)}, whose
 * entries are {@code frame * 100 / 60} truncated, and the clock adds the truncated laps, so a
 * total is the sum of its truncated laps (design D8, &sect;5.3). The clock stops at 9'59"99
 * ({@code S3kCompetitionSession.MAX_TIME}). Acts keep Time Attack's {@code m:ss.cc}.
 */
public final class LapTimeFormat {
    /** 9'59"99 in hundredths: the ROM competition clock's cap. */
    public static final int MAX_CENTISECONDS = 9 * 6000 + 59 * 100 + 99;

    private LapTimeFormat() {
    }

    /** One lap's frames as ROM hundredths: whole seconds, then {@code byte_37E52[frames % 60]}. */
    public static int centiseconds(int frames) {
        long value = Math.max(0, frames);
        return (int) Math.min(MAX_CENTISECONDS, value / 60 * 100 + value % 60 * 100 / 60);
    }

    /** ROM hundredths as the competition clock draws them: {@code 0'21"50}. */
    public static String clock(int centiseconds) {
        int value = Math.clamp(centiseconds, 0, MAX_CENTISECONDS);
        return "%d'%02d\"%02d".formatted(value / 6000, value / 100 % 60, value % 100);
    }

    /** A lap (or a lap in progress) in the ROM format. */
    public static String lap(int frames) {
        return clock(centiseconds(frames));
    }

    /** A race total as the ROM shows it: the sum of the truncated laps, capped at 9'59"99. */
    public static String total(List<Integer> lapFrames) {
        long sum = 0;
        for (int frames : lapFrames) sum += centiseconds(frames);
        return clock((int) Math.min(MAX_CENTISECONDS, sum));
    }

    /** A finish time on its track: arenas sum their truncated laps, acts read as Time Attack's clock. */
    public static String finish(TrackKind kind, int totalFrames, List<Integer> lapFrames) {
        if (kind == TrackKind.ARENA) return lapFrames.isEmpty() ? lap(totalFrames) : total(lapFrames);
        return TimeAttackTimeFormat.frames(totalFrames);
    }

    /** Seconds left on a host countdown, rounded up so the plate reaches 0:00 at the instant itself. */
    public static String countdown(long millis) {
        long seconds = (Math.max(0, millis) + 999) / 1000;
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }
}
