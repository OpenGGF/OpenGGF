package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.Level;
import com.openggf.game.modzone.ModPaletteUsageValidator;
import com.openggf.game.modzone.ModZoneAdapter;
import com.openggf.game.modzone.ModZoneDataSelectDecorator;
import com.openggf.game.modzone.ModZoneLevelData;
import com.openggf.game.modzone.ModZoneRegistrationException;
import com.openggf.game.modzone.ModZoneRuntimeProfile;

import java.io.IOException;
import java.util.Objects;

/** Sonic 3&amp;K host capability for additive format-v2 zone construction. */
public final class Sonic3kModZoneAdapter implements ModZoneAdapter, ModZoneDataSelectDecorator {
    private final Sonic3kGameModule gameModule;

    public Sonic3kModZoneAdapter(Sonic3kGameModule gameModule) {
        this.gameModule = Objects.requireNonNull(gameModule, "gameModule");
    }

    @Override
    public void validate(String ownerModId, ModZoneLevelData level) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(level, "level");
        if (level.formatVersion() != 2) {
            throw new ModZoneRegistrationException(ownerModId,
                    "Sonic 3&K additive zones require formatVersion 2");
        }
        if (level.blockGridSide() != 8) {
            throw new ModZoneRegistrationException(ownerModId,
                    "Sonic 3&K runtime requires blockGridSide 8");
        }
        boolean hasStockObjects = level.objects().stream().anyMatch(spawn -> spawn.ownerModId() == null);
        if (hasStockObjects && level.hostMetadata().isEmpty()) {
            throw new ModZoneRegistrationException(ownerModId,
                    "S3K stock objects require an explicit objectZoneSet");
        }
        S3kZoneSet zoneSet = level.hostMetadata()
                .map(metadata -> S3kZoneSet.valueOf(metadata.objectZoneSet().name()))
                .orElse(S3kZoneSet.S3KL);
        Sonic3kObjectRegistry registry = (Sonic3kObjectRegistry) gameModule.createObjectRegistry();
        for (com.openggf.level.objects.ObjectSpawn object : level.objects()) {
            if (object.ownerModId() == null
                    && !registry.canCreateInCustomZone(zoneSet, object.objectId())) {
                throw new ModZoneRegistrationException(ownerModId,
                        "MOD_S3K_STOCK_OBJECT_INCOMPATIBLE",
                        "Stock S3K object 0x%02X requires a real ROM zone identity"
                                .formatted(object.objectId() & 0xFF),
                        null, null);
            }
        }
        ModPaletteUsageValidator.validate(ownerModId, level);
        S3kCustomZonePaletteBridge.validateCreatorClaims(ownerModId, level.paletteClaims());
    }

    @Override
    public Level load(String ownerModId, ModZoneLevelData level) throws IOException {
        validate(ownerModId, level);
        S3kZoneSet zoneSet = level.hostMetadata()
                .map(metadata -> S3kZoneSet.valueOf(metadata.objectZoneSet().name()))
                .orElse(S3kZoneSet.S3KL);
        return Sonic3kLevel.inMemoryBuilder(level.zoneIndex(), level.patternBytes(),
                        level.chunkBytes(), level.blockBytes())
                .layout(level.width(), level.height(), level.foregroundMap(),
                        level.backgroundMap().orElse(null))
                .characterPalette(gameModule.getAdditiveLevelCharacterPalette())
                .paletteClaims(level.paletteClaims())
                .solidProfiles(level.solidHeights(), level.solidWidths(), level.solidAngles())
                .collisionIndices(level.primaryCollisionIndices(), level.secondaryCollisionIndices())
                .boundaries(level.minX(), level.maxX(), level.minY(), level.maxY())
                .objectZoneSet(zoneSet)
                .spawns(level.objects(), level.rings(),
                        gameModule.getAdditiveLevelRingSpriteSheet())
                .build();
    }

    @Override
    public ModZoneRuntimeProfile runtimeProfile(String ownerModId, ModZoneLevelData level) {
        validate(ownerModId, level);
        return Sonic3kModZoneRuntimeProfile.flatEmpty();
    }

    @Override
    public com.openggf.game.dataselect.DataSelectHostProfile decorateHostProfile(
            com.openggf.game.dataselect.DataSelectHostProfile inherited,
            java.util.function.Supplier<com.openggf.game.ZoneRegistry> effectiveZones,
            java.util.function.BiConsumer<String,
                    com.openggf.game.dataselect.ModZoneSaveFinding> saveFindingSink) {
        return new com.openggf.game.sonic3k.dataselect.S3kDataSelectProfile(effectiveZones);
    }

    @Override
    public com.openggf.game.dataselect.DataSelectPresentationProvider decoratePresentationProvider(
            com.openggf.game.dataselect.DataSelectPresentationProvider inherited,
            com.openggf.game.dataselect.DataSelectHostProfile effectiveHostProfile) {
        return new com.openggf.game.dataselect.DataSelectPresentationProvider(
                com.openggf.game.sonic3k.dataselect.S3kDataSelectManager::new,
                new com.openggf.game.dataselect.DataSelectSessionController(effectiveHostProfile));
    }
}
