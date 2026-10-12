package sitarhero.model;

import java.util.List;
import java.util.Optional;

/** Arcade song metadata with immutable role preparation; chart/audio clocks still come from ROM notes. */
public record SongSpec(String id, String label, String game, int musicId, int durationFrames, int zone, int act,
                       List<Role> preparedRoles, boolean preparedExcerpt) {
    /** Compatibility construction resolves authored metadata once rather than on every menu lookup. */
    public SongSpec(String id, String label, String game, int musicId, int durationFrames, int zone, int act) {
        this(id, label, game, musicId, durationFrames, zone, act, SongCatalog.arrangement(id));
    }
    private SongSpec(String id, String label, String game, int musicId, int durationFrames, int zone, int act,
                     Optional<SongArrangement> form) {
        this(id, label, game, musicId, durationFrames, zone, act,
                form.map(value -> java.util.Arrays.stream(Role.values()).filter(value::supports).toList()).orElse(List.of()), form.isEmpty());
    }
    public SongSpec {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(label, "label");
        if (!List.of("s1", "s2", "s3k").contains(game) || durationFrames < 1 || durationFrames > 36_000)
            throw new IllegalArgumentException("Invalid song source/duration");
        preparedRoles = List.copyOf(preparedRoles);
    }
    /** Only parts with actual native voices are selectable. */
    public List<Role> availableRoles() {
        if (preparedExcerpt) throw new java.util.NoSuchElementException("No authored song arrangement");
        return preparedRoles;
    }
    public boolean excerpt() { return preparedExcerpt; }
}
