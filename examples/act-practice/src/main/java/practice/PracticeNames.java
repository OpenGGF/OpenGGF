package practice;

import java.util.Locale;

final class PracticeNames {
    private PracticeNames() { }
    static String gameName(String gameId) {
        return switch (gameId) {
            case "s1" -> "SONIC 1";
            case "s2" -> "SONIC 2";
            case "s3k" -> "SONIC 3 & KNUCKLES";
            default -> gameId.toUpperCase(Locale.ROOT);
        };
    }
}
