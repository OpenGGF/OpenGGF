package sitarhero.model;

/** Arcade song metadata; chart/audio clocks are derived from runtime ROM note events. */
public record SongSpec(String id, String label, String game, int musicId, int durationFrames, int zone, int act) {
    public SongSpec {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(label, "label");
        if (!java.util.List.of("s1", "s2", "s3k").contains(game) || durationFrames < 1 || durationFrames > 36_000)
            throw new IllegalArgumentException("Invalid song source/duration");
    }

    /** Shipped MGZ1 and S&K FBZ1 stop every PSG stream: there is no synth part to invent. */
    public java.util.List<Role> availableRoles() {
        return switch (id) {
            case "marble-garden-1", "flying-battery-1" -> java.util.List.of(Role.SITAR, Role.BONGOS, Role.HARP);
            default -> java.util.List.of(Role.values());
        };
    }

    /** Presentation can distinguish the nine excerpts from the original complete cycles. */
    public boolean excerpt() {
        return !java.util.List.of("green-hill", "chemical-plant", "angel-island-1").contains(id);
    }
}
