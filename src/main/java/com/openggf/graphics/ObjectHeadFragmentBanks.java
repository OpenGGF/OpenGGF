package com.openggf.graphics;

/** Draw-occurrence banks, retained until queued commands finish. Shared enemy art never aliases
 * another enemy's cutout, and retired objects do not accumulate lifetime allocations. */
final class ObjectHeadFragmentBanks {
    private int next;
    int allocate() {
        int base=PatternAtlasRange.OBJECT_HEAD_PRESENTATION.base()+next*256;
        if(base+256>PatternAtlasRange.OBJECT_HEAD_PRESENTATION.endExclusive())
            throw new IllegalStateException("Object head draw budget exceeded");
        next++; return base;
    }
    void reset() { next=0; }
}
