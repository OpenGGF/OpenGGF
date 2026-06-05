package com.openggf.level.objects;

import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;

/**
 * Recreate-on-restore codec for dynamic object instances captured by
 * {@link ObjectManager}'s rewind adapter.
 */
public interface DynamicObjectRewindCodec {
    boolean supports(ObjectInstance instance);

    String className();

    ObjectInstance recreate(
            DynamicObjectRecreateContext context,
            ObjectManagerSnapshot.DynamicObjectEntry entry);
}
