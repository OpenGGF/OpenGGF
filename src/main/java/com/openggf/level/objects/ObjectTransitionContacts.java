package com.openggf.level.objects;

import java.util.List;

/** Internal transfer of SST-owned contacts across a manager replacement. */
public final class ObjectTransitionContacts {
    private ObjectTransitionContacts() { }

    /** Called after exact surviving SST identities have received their world offsets. */
    public static void inherit(ObjectManager target, ObjectManager previous,
            List<ObjectInstance> carried) {
        if (previous != null) {
            target.inheritRetainedSstContacts(previous, carried);
        }
    }
}
