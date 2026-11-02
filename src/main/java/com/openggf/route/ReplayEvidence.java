package com.openggf.route;

import java.util.List;
import java.util.Objects;

/**
 * Proof that a complete forward replay of one route content passed in one environment. There is
 * no public constructor: only {@link RouteReplay} mints evidence, and {@link RouteFiles#writeNew}
 * requires evidence matching the content it writes.
 *
 * @param coverage the checks this replay evaluated
 */
public final class ReplayEvidence {
    private final ReplayScope scope;
    private final ContentHash content;
    private final String environment;
    private final int rows;
    private final List<String> coverage;

    ReplayEvidence(ReplayScope scope, ContentHash content, String environment, int rows, List<String> coverage) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.content = Objects.requireNonNull(content, "content");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.rows = rows;
        this.coverage = List.copyOf(coverage);
    }

    public ReplayScope scope() {
        return scope;
    }

    public ContentHash content() {
        return content;
    }

    public String environment() {
        return environment;
    }

    public int rows() {
        return rows;
    }

    public List<String> coverage() {
        return coverage;
    }

    public QualificationStamp stamp() {
        return new QualificationStamp(scope, "passed", content, environment, rows);
    }

    @Override
    public String toString() {
        return scope + " passed " + rows + " rows of " + content.shortForm() + " on " + environment + " " + coverage;
    }
}
