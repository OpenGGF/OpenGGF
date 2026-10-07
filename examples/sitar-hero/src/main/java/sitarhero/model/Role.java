package sitarhero.model;

/** Musical roles retain the ROM's voices; instrument names describe the performer props. */
public enum Role {
    SITAR("Sitar / Lead", false),
    BONGOS("Bongos / DAC", true),
    SYNTH("Synth / PSG", false),
    HARP("Harp / Rhythm", false);

    private final String label;
    private final boolean drums;

    Role(String label, boolean drums) {
        this.label = label;
        this.drums = drums;
    }

    public String label() { return label; }
    public boolean drums() { return drums; }

    /** Union of authored section ownership. Playback uses ChartCurator.audioParts. */
    public int fmMask(SongSpec song) {
        if (this == SITAR) return switch (song.id()) {
            case "green-hill" -> 0b11101;
            case "marble", "hydrocity-1" -> 0b00101;
            case "spring-yard", "labyrinth", "chemical-plant" -> 0b00001;
            case "emerald-hill", "aquatic-ruin", "angel-island-1" -> 0b00110;
            case "casino-night" -> 0b10010;
            case "marble-garden-1" -> 0b11110;
            case "flying-battery-1" -> 0b10001;
            default -> throw new IllegalArgumentException("No authored musical part for " + song.id());
        };
        if (this == HARP) return switch (song.id()) {
            case "green-hill" -> 0b11111;
            case "labyrinth" -> 0b10000;
            case "casino-night", "marble-garden-1", "flying-battery-1" -> 0b01100;
            case "marble", "spring-yard", "emerald-hill", "chemical-plant", "aquatic-ruin",
                    "angel-island-1", "hydrocity-1" -> 0b11000;
            default -> throw new IllegalArgumentException("No authored musical part for " + song.id());
        };
        return 0;
    }

    /** CPZ stops PSG1/2: its PSG part is the authentic PSG3 noise hi-hat. */
    public int psgMask(SongSpec song) {
        if (this != SYNTH) return 0;
        return switch (song.id()) {
            case "chemical-plant" -> 0b100;
            case "spring-yard" -> 0b001;
            case "marble-garden-1", "flying-battery-1" -> 0;
            case "green-hill", "marble", "labyrinth", "emerald-hill", "aquatic-ruin",
                    "casino-night", "angel-island-1", "hydrocity-1" -> 0b011;
            default -> throw new IllegalArgumentException("No authored musical part for " + song.id());
        };
    }

    /** True when subtraction of DAC forms the performer's part. */
    public boolean dacMuted(SongSpec song) { return this == BONGOS; }
}
