package towerdefense.core;

/** Small, version-independent records file. Corrupt data resets safely. */
public record Records(int bestScore, int bestWave, int wins) {
    public static Records parse(String text) {
        try {
            String[] parts = text.strip().split(",", -1);
            if (parts.length != 3) return new Records(0, 0, 0);
            int score = Integer.parseInt(parts[0]);
            int wave = Integer.parseInt(parts[1]);
            int wins = Integer.parseInt(parts[2]);
            if (score < 0 || score > 1000000 || wave < 0 || wave > Catalog.WAVES || wins < 0 || wins > 1000000) {
                return new Records(0, 0, 0);
            }
            return new Records(score, wave, wins);
        } catch (NumberFormatException e) {
            return new Records(0, 0, 0);
        }
    }

    public String encode() { return bestScore + "," + bestWave + "," + wins; }
    public Records completed(int score, int wave, boolean won) {
        return new Records(Math.max(bestScore, score), Math.max(bestWave, wave), Math.min(1000000, wins + (won ? 1 : 0)));
    }
}
