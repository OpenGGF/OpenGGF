package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.identity.ObjectRefId;
import java.util.List;

/**
 * Transaction spanning every missing FBZ cloud and its ObjectManager side effects.
 * {@link #recreateAll()} stages objects without publishing them. {@link #commit()}
 * atomically publishes the staged graph. {@link #rollback()} is mandatory and
 * must restore the pre-batch graph both before and after commit.
 */
public interface FbzCloudRecreationBatch {
    List<ObjectRefId> recreateAll();
    void commit();
    void rollback();
}
