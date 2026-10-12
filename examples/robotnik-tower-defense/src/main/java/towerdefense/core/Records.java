package towerdefense.core;

/** Small legacy CSV records file. Scenes preserve rejected input rather than publishing defaults. */
public record Records(int bestScore, int bestWave, int wins) {
    public static Records parse(String text) { return decode(text).orElse(new Records(0, 0, 0)); }

    public static java.util.Optional<Records> decode(String text) {
        if (text == null) return java.util.Optional.empty();
        try {
            String[] parts = text.strip().split(",", -1);
            if (parts.length != 3) return java.util.Optional.empty();
            int score = Integer.parseInt(parts[0]);
            int wave = Integer.parseInt(parts[1]);
            int wins = Integer.parseInt(parts[2]);
            if (score < 0 || score > 1000000 || wave < 0 || wave > Catalog.WAVES || wins < 0 || wins > 1000000) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new Records(score, wave, wins));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    public String encode() { return bestScore + "," + bestWave + "," + wins; }
    public Records completed(int score, int wave, boolean won) {
        return new Records(Math.max(bestScore, score), Math.max(bestWave, wave), Math.min(1000000, wins + (won ? 1 : 0)));
    }
}
