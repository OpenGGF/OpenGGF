package com.openggf.tests;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

/** MGZ2: independent production-preset, supported main/donor and finite roster entry floor. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMgz2EntryMatrix extends CanonicalEntryMatrixSupport {
    @Override TestS3kCanonicalEntryLifecycle.Act binding() {
        return new TestS3kCanonicalEntryLifecycle.Act("MGZ2", 2, 1);
    }
}
