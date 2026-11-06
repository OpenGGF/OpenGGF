package openggf.timeattack;

import java.util.Locale;
import java.util.Objects;

/**
 * Which master server Time Attack uses for browsing and listing rooms. {@link BuildDefault} follows
 * whichever build is running (its packaged {@code network-defaults.properties}) and is never stored
 * as a host name, so moving a profile between builds never carries the wrong master. {@link Custom}
 * holds what the player typed: a bare DNS name (port from SRV) or a {@code ws(s)://} URL. {@link Off}
 * disables browsing and listing; LAN play still works.
 */
public sealed interface MasterSelection {
    record BuildDefault() implements MasterSelection { }

    /** @param address non-blank, single-line, at most {@link TimeAttackSettings#MAX_TEXT_LENGTH} characters */
    record Custom(String address) implements MasterSelection {
        public Custom {
            Objects.requireNonNull(address, "address");
            if (address.isBlank() || address.length() > TimeAttackSettings.MAX_TEXT_LENGTH
                    || address.indexOf('\n') >= 0 || address.indexOf('\r') >= 0 || !address.equals(address.strip())) {
                throw new IllegalArgumentException("custom master address must be one trimmed line");
            }
        }
    }

    record Off() implements MasterSelection { }

    /** Typed text as a selection: blank text means the build default. */
    static MasterSelection typed(String text) {
        String value = Objects.requireNonNullElse(text, "").replace('\r', ' ').replace('\n', ' ').strip();
        if (value.length() > TimeAttackSettings.MAX_TEXT_LENGTH) value = value.substring(0, TimeAttackSettings.MAX_TEXT_LENGTH).strip();
        return value.isEmpty() ? new BuildDefault() : new Custom(value);
    }

    /** The stored mode word: {@code default}, {@code custom} or {@code off}. */
    default String modeName() {
        return switch (this) {
            case BuildDefault ignored -> "default";
            case Custom ignored -> "custom";
            case Off ignored -> "off";
        };
    }

    static String normalizedMode(String stored) {
        return Objects.requireNonNullElse(stored, "").strip().toLowerCase(Locale.ROOT);
    }
}
