package com.openggf.game.sonic2;

import com.openggf.level.Level;
import com.openggf.mods.code.ModLevelDefinition;
import com.openggf.mods.code.ModRegistrationException;
import com.openggf.mods.code.ModZoneAdapter;
import com.openggf.mods.code.ModZoneLoader;
import com.openggf.mods.code.ModZoneRuntimeProfile;

import java.io.IOException;
import java.util.Objects;

/** Sonic 2 host capability for additive mod-zone construction. */
public final class Sonic2ModZoneAdapter implements ModZoneAdapter {
    private final Sonic2GameModule gameModule;

    public Sonic2ModZoneAdapter(Sonic2GameModule gameModule) {
        this.gameModule = Objects.requireNonNull(gameModule, "gameModule");
    }

    @Override
    public void validate(String ownerModId, ModLevelDefinition level) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(level, "level");
        if (level.formatVersion() != 1) {
            throw new ModRegistrationException(ownerModId,
                    "Sonic 2 additive zones require formatVersion 1");
        }
        if (level.blockGridSide() != 8) {
            throw new ModRegistrationException(ownerModId,
                    "Sonic 2 runtime requires blockGridSide 8");
        }
    }

    @Override
    public Level load(String ownerModId, ModLevelDefinition level) throws IOException {
        validate(ownerModId, level);
        return ModZoneLoader.load(level, gameModule.getAdditiveLevelRingSpriteSheet());
    }

    @Override
    public ModZoneRuntimeProfile runtimeProfile(String ownerModId, ModLevelDefinition level) {
        validate(ownerModId, level);
        return ModZoneRuntimeProfile.flatEmpty();
    }
}
