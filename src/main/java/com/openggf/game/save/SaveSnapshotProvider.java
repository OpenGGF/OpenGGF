package com.openggf.game.save;

import java.util.Map;

@FunctionalInterface
@com.openggf.game.ModApi
public interface SaveSnapshotProvider {
    Map<String, Object> capture(SaveReason reason, RuntimeSaveContext context);
}
