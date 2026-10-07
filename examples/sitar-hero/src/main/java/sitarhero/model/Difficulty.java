package sitarhero.model;

/** Arrangement difficulty changes density and melodic frets, never the ROM clock or physical drum bindings. */
public enum Difficulty {
    EASY("Easy", 3, 1, 2),
    MEDIUM("Medium", 4, 2, 4),
    HARD("Hard", 5, 3, 6),
    EXPERT("Expert", 5, 4, 8);

    private final String label;
    private final int lanes, melodicDivision, drumDivision;

    Difficulty(String label, int lanes, int melodicDivision, int drumDivision) {
        this.label = label;
        this.lanes = lanes;
        this.melodicDivision = melodicDivision;
        this.drumDivision = drumDivision;
    }

    public String label() { return label; }
    /** Melodic frets; DAC retains four physical pads plus the separate kick. */
    public int lanes() { return lanes; }
    public int densityDivision(boolean drums) { return drums ? drumDivision : melodicDivision; }
}
