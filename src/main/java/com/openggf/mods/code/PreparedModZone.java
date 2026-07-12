package com.openggf.mods.code;

import com.openggf.game.ModKeySyntax;
import com.openggf.game.MusicReference;
import com.openggf.level.LevelDescriptor;

import java.util.Objects;
import java.util.Optional;

/** Engine-owned immutable zone payload, fully read before its creator asset view closes. */
public record PreparedModZone(String ownerModId, String localKey, String insertAfter,
                              ModLevelDefinition definition, ZoneEventFactory eventFactory,
                              String zoneName, int levelIndex, int authoredZoneIndex,
                              int startX, int startY) {
    public PreparedModZone {
        ownerModId = ModKeySyntax.requireManifestId(ownerModId);
        localKey = ModKeySyntax.requireLocalName(localKey);
        Objects.requireNonNull(insertAfter, "insertAfter");
        Objects.requireNonNull(zoneName, "zoneName");
    }

    static PreparedModZone prepared(String owner, ModZoneContribution contribution,
                                    ModLevelDefinition definition) {
        return new PreparedModZone(owner, contribution.localKey(), contribution.insertAfter(), definition,
                contribution.eventFactory(), definition.zoneName(), definition.levelIndex(),
                definition.zoneIndex(), definition.start().x(), definition.start().y());
    }

    static PreparedModZone metadata(String owner, String local, String anchor, String name,
                                    int levelIndex, int authoredZone, int startX, int startY) {
        return new PreparedModZone(owner, local, anchor, null, null, name, levelIndex,
                authoredZone, startX, startY);
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
