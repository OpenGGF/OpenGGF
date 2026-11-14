package com.openggf.game.soniccd.objects;

import com.openggf.level.objects.ObjectFactory;

/**
 * One registration fragment: the common objects, or one zone's objects. Each stage file
 * carries its own object index (e.g. Palmtree Panic/Object Index (Act 1 Present).asm),
 * so an object ID is resolved against the loaded stage's zone fragment first and the
 * common fragment second. A lane adds its objects to its own fragment file only.
 */
public interface CdObjectRegistration {
    void register(Registrar registrar);

    /**
     * Registers this fragment's sprite sheets for the loaded stage (common fragment first, then
     * the stage's zone fragment). Read table offsets from the fragment's own catalogue and build
     * sheets with {@code art.sheet(...)}; keep per-load state in {@code art.zoneArt(...)}.
     */
    default void registerArt(com.openggf.game.soniccd.objects.art.CdObjectArtProvider art) throws java.io.IOException { }

    interface Registrar {
        void object(int objectId, String name, ObjectFactory factory);

        /**
         * A zone routine at {@code objectId} only in the stages {@code indexedIn} accepts, because the stage's
         * ObjectIndex starts it there and another routine at the same ID elsewhere. In the zone's other stages the
         * ID resolves as if this fragment had not registered it: the shared fragment, then the common one.
         */
        default void object(int objectId, String name, java.util.function.Predicate<com.openggf.game.soniccd.CdStagePayload> indexedIn,
                            ObjectFactory factory) {
            throw new UnsupportedOperationException("stage-scoped object");
        }

        void badnik(int objectId, String name, ObjectFactory factory);

        /**
         * A routine whose object ID differs by stage: the common catalogue column {@code idColumn}
         * gives, per stage, the ObjectIndex slot that starts it (-1 where no slot does).
         */
        default void stageObject(String idColumn, String name, ObjectFactory factory) {
            throw new UnsupportedOperationException("stageObject");
        }
    }
}
