package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.Level;
import com.openggf.mods.code.ModLevelDefinition;
import com.openggf.mods.code.ModPaletteUsageValidator;
import com.openggf.mods.code.ModRegistrationException;
import com.openggf.mods.code.ModZoneAdapter;
import com.openggf.mods.code.ModZoneLoader;
import com.openggf.mods.code.ModZoneRuntimeProfile;

import java.io.IOException;
import java.util.Objects;

/** Sonic 3&amp;K host capability for additive format-v2 zone construction. */
public final class Sonic3kModZoneAdapter implements ModZoneAdapter {
    private final Sonic3kGameModule gameModule;

    public Sonic3kModZoneAdapter(Sonic3kGameModule gameModule) {
        this.gameModule = Objects.requireNonNull(gameModule, "gameModule");
    }

    @Override
    public void validate(String ownerModId, ModLevelDefinition level) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(level, "level");
        if (level.formatVersion() != 2) {
            throw new ModRegistrationException(ownerModId,
                    "Sonic 3&K additive zones require formatVersion 2");
        }
        if (level.blockGridSide() != 8) {
            throw new ModRegistrationException(ownerModId,
                    "Sonic 3&K runtime requires blockGridSide 8");
        }
        boolean hasStockObjects = level.objects().stream()
                .anyMatch(ModLevelDefinition.StockObjectSpawn.class::isInstance);
        if (hasStockObjects && level.s3kMetadata().isEmpty()) {
            throw new ModRegistrationException(ownerModId,
                    "S3K stock objects require an explicit objectZoneSet");
        }
        S3kZoneSet zoneSet = level.s3kMetadata()
                .map(metadata -> S3kZoneSet.valueOf(metadata.objectZoneSet().name()))
                .orElse(S3kZoneSet.S3KL);
        Sonic3kObjectRegistry registry = (Sonic3kObjectRegistry) gameModule.createObjectRegistry();
        for (ModLevelDefinition.ObjectEntry object : level.objects()) {
            if (object instanceof ModLevelDefinition.StockObjectSpawn stock
                    && !registry.canCreateInCustomZone(zoneSet, stock.stockObjectId())) {
                throw new ModRegistrationException(ownerModId,
                        "MOD_S3K_STOCK_OBJECT_INCOMPATIBLE",
                        "Stock S3K object 0x%02X requires a real ROM zone identity"
                                .formatted(stock.stockObjectId() & 0xFF),
                        null, null);
            }
        }
        ModPaletteUsageValidator.validate(ownerModId, level);
    }

    @Override
    public Level load(String ownerModId, ModLevelDefinition level) throws IOException {
        validate(ownerModId, level);
        S3kZoneSet zoneSet = level.s3kMetadata()
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
                .boundaries(level.bounds().minX(), level.bounds().maxX(),
                        level.bounds().minY(), level.bounds().maxY())
                .objectZoneSet(zoneSet)
                .spawns(ModZoneLoader.decodeObjects(level, true), ModZoneLoader.decodeRings(level),
                        gameModule.getAdditiveLevelRingSpriteSheet())
                .build();
    }

    @Override
    public ModZoneRuntimeProfile runtimeProfile(String ownerModId, ModLevelDefinition level) {
        validate(ownerModId, level);
        return ModZoneRuntimeProfile.flatEmpty();
    }
}
