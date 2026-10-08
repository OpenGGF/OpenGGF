package sitarhero.model;

/** Musical roles retain the ROM's voices; instrument names describe the performer props. */
public enum Role {
    SITAR("Sitar", "Lead", false),
    BONGOS("Bongos", "DAC", true),
    SYNTH("Synth", "PSG", false),
    HARP("Harp", "Rhythm", false);

    private final String instrument;
    private final String part;
    private final boolean drums;

    Role(String instrument, String part, boolean drums) {
        this.instrument = instrument;
        this.part = part;
        this.drums = drums;
    }

    /** Menu label: the prop the performer holds, then the ROM voices it plays. */
    public String label() { return instrument + " / " + part; }
    /** The prop alone, as HUDs and story cards name it. */
    public String instrument() { return instrument; }
    public boolean drums() { return drums; }

    /** Union of authored section ownership. Playback uses ChartCurator.audioParts. */
    public int fmMask(SongSpec song) {
        return mask(SongCatalog.arrangement(song.id()).orElseThrow(), "FM");
    }

    public int psgMask(SongSpec song) {
        return mask(SongCatalog.arrangement(song.id()).orElseThrow(), "PSG");
    }

    private int mask(SongArrangement form, String kind) {
        int mask = 0;
        for (SongArrangement.Section section : form.sections(this)) {
            if (!section.kind().equals(kind)) continue;
            mask |= 1 << section.channel();
            if (section.harmony() >= 0) mask |= 1 << section.harmony();
        }
        return mask;
    }

    /** True when subtraction of DAC forms the performer's part. */
    public boolean dacMuted(SongSpec song) { return this == BONGOS; }
}
