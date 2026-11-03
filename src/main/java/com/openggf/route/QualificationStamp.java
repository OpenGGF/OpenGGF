package com.openggf.route;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * A recorded verdict for one route content in one environment. Stamps are not part of the
 * route's content identity; a stamp for other content is visibly stale.
 *
 * @param divergedRow the zero-based row whose post-row observation first failed, when not passed
 * @param reason      why it failed; empty when passed
 * @param environment engine or harness identity that produced the verdict
 * @param rows        rows the replay played
 */
public record QualificationStamp(ReplayScope scope, boolean passed, OptionalInt divergedRow, String reason,
                                 ContentHash content, String environment, int rows) {
    public QualificationStamp {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(divergedRow, "divergedRow");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(environment, "environment");
        reason = reason == null ? "" : reason;
        if (passed && divergedRow.isPresent()) throw new IllegalArgumentException("a passed stamp has no divergence");
        if (!passed && divergedRow.isEmpty()) throw new IllegalArgumentException("a failed stamp names its first diverging row");
    }

    public static QualificationStamp passed(ReplayScope scope, ContentHash content, String environment, int rows) {
        return new QualificationStamp(scope, true, OptionalInt.empty(), "", content, environment, rows);
    }

    public static QualificationStamp diverged(ReplayScope scope, int row, String reason, ContentHash content,
                                              String environment, int rows) {
        return new QualificationStamp(scope, false, OptionalInt.of(row), reason, content, environment, rows);
    }
}
