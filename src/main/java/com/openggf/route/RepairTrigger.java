package com.openggf.route;

import java.util.Set;

/** What opens an editable window in a repair. */
public sealed interface RepairTrigger {
    /** The first source row whose post-row position leaves the tracking tolerance. */
    record TrackingDrift() implements RepairTrigger { }

    /**
     * A window that must be re-authored even though engine tracking holds (for example after a
     * native mismatch), starting at source row {@code sourceFrom}. Solutions whose tape hash is in
     * {@code rejected} do not count, so a repair cannot return a tape already known to fail.
     */
    record RequiredEdit(int sourceFrom, Set<ContentHash> rejected) implements RepairTrigger {
        public RequiredEdit {
            rejected = Set.copyOf(rejected);
        }
    }
}
