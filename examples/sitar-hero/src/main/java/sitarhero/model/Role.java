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

    /**
     * Union of a role's selected FM channels for metadata/legacy callers. Actual playback
     * uses ChartCurator.audioParts: GHZ changes lead/rhythm ownership at authored section
     * boundaries. CPZ lead is FM1; AIZ FM1 is bass and its doubled melody is FM2+FM3.
     */
    public int fmMask(SongSpec song) {
        return switch (this) {
            case SITAR -> "green-hill".equals(song.id()) ? 0b11101 : "angel-island-1".equals(song.id()) ? 0b00110 : 0b00001;
            case HARP -> "green-hill".equals(song.id()) ? 0b11111 : 0b11000;
            default -> 0;
        };
    }

    /** CPZ stops PSG1/2: its PSG part is the authentic PSG3 noise hi-hat. */
    public int psgMask(SongSpec song) {
        return this == SYNTH ? ("chemical-plant".equals(song.id()) ? 0b100 : 0b011) : 0;
    }

    /** True when subtraction of DAC forms the performer's part. */
    public boolean dacMuted(SongSpec song) { return this == BONGOS; }
}
