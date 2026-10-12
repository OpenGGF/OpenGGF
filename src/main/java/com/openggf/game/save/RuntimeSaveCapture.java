package com.openggf.game.save;

import com.openggf.game.session.GameplayModeContext;

/** Engine-internal bridge; creator providers receive only RuntimeSaveContext. */
public final class RuntimeSaveCapture {
    private RuntimeSaveCapture() { }
    public static RuntimeSaveContext capture(GameplayModeContext mode, SaveSessionContext save) {
        return RuntimeSaveContext.forGameplayMode(mode,save);
    }
    /** Called within an engine-owned provider callback boundary before publishing its returned data. */
    public static java.util.Map<String,Object> freezeFields(java.util.Map<String,Object> fields) {
        return RuntimeSaveFields.freeze(fields);
    }
}
