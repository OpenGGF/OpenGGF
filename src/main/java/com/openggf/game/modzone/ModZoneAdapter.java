package com.openggf.game.modzone;

import com.openggf.game.ModApi;
import com.openggf.level.Level;

import java.io.IOException;

/** Host capability for validating and constructing additive zones. */
@ModApi
public interface ModZoneAdapter {
    void validate(String ownerModId, ModZoneLevelData level);

    Level load(String ownerModId, ModZoneLevelData level) throws IOException;

    ModZoneRuntimeProfile runtimeProfile(String ownerModId, ModZoneLevelData level);
}
