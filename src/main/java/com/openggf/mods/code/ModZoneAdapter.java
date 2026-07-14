package com.openggf.mods.code;

import com.openggf.game.ModApi;
import com.openggf.level.Level;

import java.io.IOException;

/** Host-game capability for validating and constructing additive mod zones. */
@ModApi
public interface ModZoneAdapter {
    void validate(String ownerModId, ModLevelDefinition level);

    Level load(String ownerModId, ModLevelDefinition level) throws IOException;

    ModZoneRuntimeProfile runtimeProfile(String ownerModId, ModLevelDefinition level);

    default boolean isUnsupported() {
        return false;
    }
}
