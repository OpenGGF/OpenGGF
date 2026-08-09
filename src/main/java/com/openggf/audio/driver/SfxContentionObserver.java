package com.openggf.audio.driver;

import com.openggf.audio.rewind.SmpsSourceDescriptor;
import java.util.Objects;

/** Opt-in, void-only diagnostic view of SFX admission and channel arbitration. */
public interface SfxContentionObserver {
    SfxContentionObserver NONE = new SfxContentionObserver() { };

    default void onSfxAdmitted(Admission admission) { }

    default void onRoleArbitrated(Arbitration arbitration) { }

    enum Bus { FM, PSG }

    record Source(SmpsSourceDescriptor descriptor, long admissionOrdinal,
                  boolean sfx, boolean specialSfx) {
        public Source {
            Objects.requireNonNull(descriptor, "descriptor");
        }
    }

    record Admission(Source source) {
        public Admission {
            Objects.requireNonNull(source, "source");
            if (!source.sfx() || source.admissionOrdinal() < 0) {
                throw new IllegalArgumentException("admission must identify an SFX request");
            }
        }
    }

    record Arbitration(Bus bus, int channel, Source challenger,
                       Source previousOwner, boolean acquired) {
        public Arbitration {
            Objects.requireNonNull(bus, "bus");
            Objects.requireNonNull(challenger, "challenger");
            if (channel < 0) {
                throw new IllegalArgumentException("channel must be non-negative");
            }
        }
    }
}
