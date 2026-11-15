package com.openggf.level.objects;

/** Frame-boundary entry for the NATIVE widescreen dormant layer, outside the ObjectManager mod API. */
public final class DormantObjectPresentationAccess {
    private DormantObjectPresentationAccess() { }

    /** Bakes dormant poses once per layout; no-op without a presentation margin. */
    public static void prepare(ObjectManager objects, com.openggf.game.rewind.RewindRegistry registry) {
        objects.prepareDormantPresentation(registry);
    }
}
