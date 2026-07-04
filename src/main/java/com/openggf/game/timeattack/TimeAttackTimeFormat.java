package com.openggf.game.timeattack;

/** 60fps frame counts to display strings. */
public final class TimeAttackTimeFormat {
    private TimeAttackTimeFormat() {
    }

    public static String frames(int frames) {
        int totalSeconds = frames / 60;
        int centis = (frames % 60) * 100 / 60;
        return "%d:%02d.%02d".formatted(totalSeconds / 60, totalSeconds % 60, centis);
    }

    public static String delta(int deltaFrames) {
        if (deltaFrames == Integer.MIN_VALUE) {
            return "";
        }
        int abs = Math.abs(deltaFrames);
        int centis = (abs % 60) * 100 / 60;
        return "%s%d.%02d".formatted(deltaFrames < 0 ? "-" : "+", abs / 60, centis);
    }
}
