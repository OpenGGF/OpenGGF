package com.openggf.route;

import java.util.Objects;

/**
 * A recorded verdict for one route content in one environment. Stamps are not part of the
 * route's content identity; a stamp for other content is visibly stale.
 *
 * @param verdict     {@code passed}, or {@code diverged at row N: reason}
 * @param environment engine/harness identity that produced the verdict
 */
public record QualificationStamp(ReplayScope scope, String verdict, ContentHash content, String environment,
                                 int rows) {
    public QualificationStamp {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(environment, "environment");
    }

    public boolean passed() {
        return verdict.equals("passed");
    }
}
