package sitarhero.model;

import java.util.List;

/** Cosmetic ROM-backed performers. There are deliberately no ability or scoring fields. */
public enum Roster {
    SONIC("sonic", "Sonic"),
    ROBOTNIK("robotnik", "Robotnik"),
    TAILS("tails", "Tails"),
    SILVER_SONIC("silver-sonic", "Silver Sonic (S2)"),
    KNUCKLES("knuckles", "Knuckles"),
    MECHA_SONIC("mecha-sonic", "Mecha Sonic (S3K)"),
    EGG_ROBO("egg-robo", "Egg Robo (SSZ)");

    private final String id;
    private final String label;

    Roster(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }
    public boolean available(List<String> installedGames) { return game(installedGames) != null; }

    /** ROM donor for the artwork, independent of the song's game. Null means unavailable. */
    public String game(List<String> installedGames) {
        if (this == SILVER_SONIC) return installedGames.contains("s2") ? "s2" : null;
        if (this == KNUCKLES || this == MECHA_SONIC || this == EGG_ROBO)
            return installedGames.contains("s3k") ? "s3k" : null;
        if (installedGames.contains("s3k")) return "s3k";
        if (installedGames.contains("s2")) return "s2";
        return this != TAILS && installedGames.contains("s1") ? "s1" : null;
    }

    public static List<Roster> availablePerformers(List<String> installedGames) {
        return java.util.Arrays.stream(values()).filter(performer -> performer.available(installedGames)).toList();
    }
}
