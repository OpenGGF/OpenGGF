package com.openggf.game.sonic2;

import com.openggf.level.Level;
import com.openggf.mods.code.ModLevelDefinition;
import com.openggf.mods.code.ModRegistrationException;
import com.openggf.mods.code.ModZoneAdapter;
import com.openggf.mods.code.ModZoneDataSelectDecorator;
import com.openggf.mods.code.ModZoneLoader;
import com.openggf.mods.code.ModZoneRuntimeProfile;

import java.io.IOException;
import java.util.Objects;

/** Sonic 2 host capability for additive mod-zone construction. */
public final class Sonic2ModZoneAdapter implements ModZoneAdapter, ModZoneDataSelectDecorator {
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

    @Override
    public com.openggf.game.dataselect.DataSelectHostProfile decorateHostProfile(
            com.openggf.game.dataselect.DataSelectHostProfile inherited,
            java.util.function.Supplier<com.openggf.game.ZoneRegistry> effectiveZones,
            java.util.function.BiConsumer<String,
                    com.openggf.game.sonic2.dataselect.S2SaveFinding> saveFindingSink) {
        return new com.openggf.game.sonic2.dataselect.S2DataSelectProfile(
                effectiveZones, finding -> saveFindingSink.accept(
                        finding.ownerModId() == null ? "s2-save" : finding.ownerModId(), finding));
    }

    @Override
    public com.openggf.game.dataselect.DataSelectPresentationProvider decoratePresentationProvider(
            com.openggf.game.dataselect.DataSelectPresentationProvider inherited,
            com.openggf.game.dataselect.DataSelectHostProfile effectiveHostProfile) {
        return com.openggf.game.dataselect.CrossGameDataSelectPresentations.donated(
                com.openggf.game.dataselect.CrossGameDataSelectPresentations.DONOR_S3K,
                effectiveHostProfile);
    }
}
