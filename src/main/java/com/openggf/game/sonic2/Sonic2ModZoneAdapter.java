package com.openggf.game.sonic2;

import com.openggf.level.Level;
import com.openggf.game.modzone.ModZoneAdapter;
import com.openggf.game.modzone.ModZoneDataSelectDecorator;
import com.openggf.game.modzone.ModZoneLevelData;
import com.openggf.game.modzone.ModZoneRegistrationException;
import com.openggf.game.modzone.ModZoneRuntimeProfile;

import java.io.IOException;
import java.util.Objects;

/** Sonic 2 host capability for additive mod-zone construction. */
public final class Sonic2ModZoneAdapter implements ModZoneAdapter, ModZoneDataSelectDecorator {
    private final Sonic2GameModule gameModule;

    public Sonic2ModZoneAdapter(Sonic2GameModule gameModule) {
        this.gameModule = Objects.requireNonNull(gameModule, "gameModule");
    }

    @Override
    public void validate(String ownerModId, ModZoneLevelData level) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(level, "level");
        if (level.formatVersion() != 1) {
            throw new ModZoneRegistrationException(ownerModId,
                    "Sonic 2 additive zones require formatVersion 1");
        }
        if (level.blockGridSide() != 8) {
            throw new ModZoneRegistrationException(ownerModId,
                    "Sonic 2 runtime requires blockGridSide 8");
        }
    }

    @Override
    public Level load(String ownerModId, ModZoneLevelData level) throws IOException {
        validate(ownerModId, level);
        Sonic2Level loaded = Sonic2Level.inMemoryBuilder(level.zoneIndex(), level.patternBytes(),
                        level.chunkBytes(), level.blockBytes())
                .layout(level.width(), level.height(), level.foregroundMap(),
                        level.backgroundMap().orElse(null))
                .paletteLines(level.paletteLines())
                .solidProfiles(level.solidHeights(), level.solidWidths(), level.solidAngles())
                .collisionIndices(level.primaryCollisionIndices(), level.secondaryCollisionIndices())
                .boundaries(level.minX(), level.maxX(), level.minY(), level.maxY())
                .spawns(level.objects(), level.rings(), gameModule.getAdditiveLevelRingSpriteSheet())
                .build();
        loaded.setPalette(0, gameModule.getAdditiveLevelCharacterPalette());
        return loaded;
    }

    @Override
    public ModZoneRuntimeProfile runtimeProfile(String ownerModId, ModZoneLevelData level) {
        validate(ownerModId, level);
        return ModZoneRuntimeProfile.flatEmpty();
    }

    @Override
    public com.openggf.game.dataselect.DataSelectHostProfile decorateHostProfile(
            com.openggf.game.dataselect.DataSelectHostProfile inherited,
            java.util.function.Supplier<com.openggf.game.ZoneRegistry> effectiveZones,
            java.util.function.BiConsumer<String,
                    com.openggf.game.dataselect.ModZoneSaveFinding> saveFindingSink) {
        return new com.openggf.game.sonic2.dataselect.S2DataSelectProfile(
                effectiveZones, finding -> saveFindingSink.accept(
                        finding.ownerModId() == null ? "s2-save" : finding.ownerModId(),
                        new com.openggf.game.dataselect.ModZoneSaveFinding(
                                finding.ownerModId(), finding.code(), finding.detail())));
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
