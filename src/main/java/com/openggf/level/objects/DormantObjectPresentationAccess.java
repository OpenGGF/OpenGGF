package com.openggf.level.objects;

/** Frame-boundary entry for the NATIVE widescreen dormant layer, outside the ObjectManager mod API. */
public final class DormantObjectPresentationAccess {
    private DormantObjectPresentationAccess() { }

    /** Bakes the next slice of dormant poses for the current layout; no-op without a presentation margin. */
    public static void prepare(ObjectManager objects, com.openggf.game.rewind.RewindRegistry registry) {
        objects.prepareDormantPresentation(registry);
    }

    /**
     * A locked title-card iteration's boundary, before its load scan and title work. The physics
     * branch runs {@code LevelFrameStep.execute}, which bakes at its own boundary instead.
     */
    public static void prepareDuringTitleCard(com.openggf.level.LevelManager level,
                                              com.openggf.game.TitleCardProvider titleCard,
                                              com.openggf.game.rewind.RewindRegistry registry) {
        ObjectManager objects = level == null ? null : level.getObjectManager();
        if (objects == null || titleCard == null || titleCard.shouldRunPlayerPhysics()) return;
        objects.prepareDormantPresentation(registry);
    }
}
