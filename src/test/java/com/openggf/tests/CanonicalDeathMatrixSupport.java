package com.openggf.tests;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Independent finite actual-restart products, sharing the qualified native death driver. */
abstract class CanonicalDeathMatrixSupport extends CanonicalEntryMatrixSupport {
    @ParameterizedTest(name = "two actual deaths / two incoming world cycles {0}")
    @MethodSource("configurations")
    void actualDeathReloadPreservesTheSupportedProductAndReplaysEachIncomingWorldTwice(Row row) {
        TestS3kCanonicalDeathLifecycle.verifyDeaths(this, row);
    }
}
