package com.openggf.tests;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

/** AIZ2: independent production-preset, supported main/donor and finite roster entry and independent actual-restart floor. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAiz2EntryMatrix extends CanonicalDeathMatrixSupport {
    @Override TestS3kCanonicalEntryLifecycle.Act binding() {
        return new TestS3kCanonicalEntryLifecycle.Act("AIZ2", 0, 1);
    }
}
