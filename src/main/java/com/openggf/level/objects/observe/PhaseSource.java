package com.openggf.level.objects.observe;

import java.util.List;

/**
 * An object's own named phase, for read-only route observation. The owner reports what its
 * routine already holds; it never predicts, mutates, allocates or plays audio. A phase is one of
 * {@link #phaseNames()}; the clock names what the owner's gate actually reads.
 */
public interface PhaseSource {
    List<String> phaseNames();

    String phase();

    PhaseClock phaseClock();
}
