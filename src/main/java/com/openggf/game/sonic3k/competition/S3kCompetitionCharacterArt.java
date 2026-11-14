package com.openggf.game.sonic3k.competition;

import com.openggf.game.*;
import com.openggf.game.presentation.StageSpriteCompiler;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.util.DplcStaticFlattener;
import java.io.IOException;
import java.util.*;

/** Optional creator presentation over unchanged native miniature mechanics and selectors. */
final class S3kCompetitionCharacterArt {
    private S3kCompetitionCharacterArt() { }

    static Map<S3kCompetitionPlayer.Character, CharacterDefinition> slots(PlayableCharacterRegistry registry,
            List<String> selected) {
        var result = new EnumMap<S3kCompetitionPlayer.Character, CharacterDefinition>(S3kCompetitionPlayer.Character.class);
        for (String code : selected) {
            var key = CharacterKey.parsePersisted(code);
            if (key.isBuiltin()) continue;
            registry.find(key).ifPresent(definition -> result.putIfAbsent(switch (definition.behavesLike()) {
                case TAILS_ALONE -> S3kCompetitionPlayer.Character.TAILS;
                case KNUCKLES -> S3kCompetitionPlayer.Character.KNUCKLES;
                default -> S3kCompetitionPlayer.Character.SONIC;
            }, definition));
        }
        return Map.copyOf(result);
    }

    static String nativeKey(S3kCompetitionPlayer.Character character) {
        return "competition_" + character.name().toLowerCase(Locale.ROOT);
    }

    static ObjectSpriteSheet compile(StageSpriteSet set) {
        var art = StageSpriteCompiler.compile(set, null);
        return new ObjectSpriteSheet(art.artTiles(),
                DplcStaticFlattener.applyDplcRemap(art.mappingFrames(), art.dplcFrames()), 0, 1);
    }

    record Banks(Map<String,ObjectSpriteSheet> sheets, Map<String,Set<Integer>> ids, Map<String,Palette> palettes) { }
    static Banks load(Map<S3kCompetitionPlayer.Character,CharacterDefinition> slots) throws IOException {
        var sheets = new LinkedHashMap<String,ObjectSpriteSheet>();
        var ids = new LinkedHashMap<String,Set<Integer>>();
        var palettes = new LinkedHashMap<String,Palette>();
        for (var entry : slots.entrySet()) {
            var definition = entry.getValue();
            if (definition.presentationSupplier() == null) continue;
            var presentation = definition.presentationSupplier().load(definition.key().persisted(), GameId.S3K);
            for (var surface : List.of(StageSpriteSet.Surface.S3K_COMPETITION,StageSpriteSet.Surface.S3K_COMPETITION_TAIL)) {
                var set = presentation.stages().get(surface);
                if (set == null) continue;
                String key = nativeKey(entry.getKey()) + (surface == StageSpriteSet.Surface.S3K_COMPETITION_TAIL ? "_tail" : "") + "_creator";
                sheets.put(key,compile(set)); ids.put(key,set.frames().keySet());
                if (definition.paletteSupplier() != null)
                    palettes.put(key,definition.paletteSupplier().load(definition.key().persisted()).deepCopy());
            }
        }
        return new Banks(Map.copyOf(sheets),Map.copyOf(ids),Map.copyOf(palettes));
    }
}
