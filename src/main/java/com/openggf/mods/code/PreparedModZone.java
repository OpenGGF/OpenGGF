package com.openggf.mods.code;

import com.openggf.game.ModKeySyntax;
import com.openggf.game.MusicReference;
import com.openggf.game.modzone.ModZoneLevelData;
import com.openggf.game.modzone.ModZoneRuntimeContribution;
import com.openggf.game.modzone.ModZoneRuntimeProfile;
import com.openggf.level.LevelDescriptor;

import java.util.Objects;
import java.util.Optional;

/** Engine-owned immutable zone payload, fully read before its creator asset view closes. */
public record PreparedModZone(String ownerModId, String localKey, String insertAfter,
                              ModLevelDefinition definition, ZoneEventFactory eventFactory,
                              String zoneName, int levelIndex, int authoredZoneIndex,
                              int startX, int startY, ModZoneRuntimeProfile runtimeProfile,
                              ModZoneLevelData hostData, boolean gameStart) {
    public PreparedModZone {
        ownerModId = ModKeySyntax.requireManifestId(ownerModId);
        localKey = ModKeySyntax.requireLocalName(localKey);
        Objects.requireNonNull(zoneName, "zoneName");
    }

    public PreparedModZone(String ownerModId, String localKey, String insertAfter,
                           ModLevelDefinition definition, ZoneEventFactory eventFactory,
                           String zoneName, int levelIndex, int authoredZoneIndex,
                           int startX, int startY, ModZoneRuntimeProfile runtimeProfile) {
        this(ownerModId, localKey, insertAfter, definition, eventFactory, zoneName,
                levelIndex, authoredZoneIndex, startX, startY, runtimeProfile, null, false);
    }

    /** Compatibility constructor for the pre-game-start canonical shape. */
    public PreparedModZone(String ownerModId, String localKey, String insertAfter,
                           ModLevelDefinition definition, ZoneEventFactory eventFactory,
                           String zoneName, int levelIndex, int authoredZoneIndex,
                           int startX, int startY, ModZoneRuntimeProfile runtimeProfile,
                           ModZoneLevelData hostData) {
        this(ownerModId, localKey, insertAfter, definition, eventFactory, zoneName,
                levelIndex, authoredZoneIndex, startX, startY, runtimeProfile, hostData, false);
    }

    /** Compatibility constructor for payloads that have not yet resolved a host profile. */
    public PreparedModZone(String ownerModId, String localKey, String insertAfter,
                           ModLevelDefinition definition, ZoneEventFactory eventFactory,
                           String zoneName, int levelIndex, int authoredZoneIndex,
                           int startX, int startY) {
        this(ownerModId, localKey, insertAfter, definition, eventFactory, zoneName,
                levelIndex, authoredZoneIndex, startX, startY, null, null, false);
    }

    static PreparedModZone prepared(String owner, ModZoneContribution contribution,
                                    ModLevelDefinition definition) {
        return new PreparedModZone(owner, contribution.localKey(), contribution.insertAfter(), definition,
                contribution.eventFactory(), definition.zoneName(), definition.levelIndex(),
                definition.zoneIndex(), definition.start().x(), definition.start().y(), null, null,
                contribution.gameStart());
    }

    static PreparedModZone metadata(String owner, String local, String anchor, String name,
                                    int levelIndex, int authoredZone, int startX, int startY) {
        return new PreparedModZone(owner, local, anchor, null, null, name, levelIndex,
                authoredZone, startX, startY, null, null, false);
    }

    /** Returns the same engine-owned payload with its resolved host runtime profile. */
    PreparedModZone withRuntimeProfile(ModZoneRuntimeProfile profile, ModZoneLevelData hostData) {
        return new PreparedModZone(ownerModId, localKey, insertAfter, definition, eventFactory,
                zoneName, levelIndex, authoredZoneIndex, startX, startY,
                Objects.requireNonNull(profile, "profile"),
                Objects.requireNonNull(hostData, "hostData"), gameStart);
    }

    public ModZoneRuntimeContribution runtimeContribution() {
        return runtimeProfile == null || hostData == null ? null
                : new ModZoneRuntimeContribution(ownerModId, localKey, hostData, runtimeProfile);
    }

    public LevelDescriptor descriptor() {
        return new LevelDescriptor() {
            @Override public int levelIndex() { return levelIndex; }
            @Override public int startX() { return startX; }
            @Override public int startY() { return startY; }
        };
    }

    public MusicReference musicReference() {
        if (definition == null) return MusicReference.stock(0);
        if (definition.music() instanceof ModLevelDefinition.StockMusic stock) {
            return MusicReference.stock(stock.stockId());
        }
        var track = ((ModLevelDefinition.TrackMusic) definition.music()).trackKey();
        return MusicReference.namespaced(track.modId(), track.localName());
    }

    public Optional<ZoneEventFactory> optionalEventFactory() { return Optional.ofNullable(eventFactory); }
}
