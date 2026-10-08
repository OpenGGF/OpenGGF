package sitarhero.model;

/** Arcade song metadata; chart/audio clocks are derived from runtime ROM note events. */
public record SongSpec(String id, String label, String game, int musicId, int durationFrames, int zone, int act) {
    public SongSpec {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(label, "label");
        if (!java.util.List.of("s1", "s2", "s3k").contains(game) || durationFrames < 1 || durationFrames > 36_000)
            throw new IllegalArgumentException("Invalid song source/duration");
    }

    /** Only parts with actual native voices are selectable. */
    public java.util.List<Role> availableRoles() {
        var form = SongCatalog.arrangement(id).orElseThrow();
        return java.util.Arrays.stream(Role.values()).filter(form::supports).toList();
    }

    public boolean excerpt() { return SongCatalog.arrangement(id).isEmpty(); }
}
