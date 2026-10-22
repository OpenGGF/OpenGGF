package com.openggf.mods.code;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.GameId;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.ModKeySyntax;
import com.openggf.io.ModAssetRoot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict data-only campaign declaration, translated to the existing ordered-act runtime. */
public record ModCampaignDefinition(String name, GameId game, String localKey, List<Level> levels) {
    public static final int MAX_LEVELS = 256;

    public ModCampaignDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(game, "game");
        if (name.isBlank() || name.length() > 256 || game == GameId.STANDALONE)
            throw new IllegalArgumentException("Campaign needs a bounded name and a stock host");
        localKey = ModKeySyntax.requireLocalName(localKey);
        levels = List.copyOf(levels);
        if (levels.isEmpty() || levels.size() > MAX_LEVELS)
            throw new IllegalArgumentException("Campaign needs 1..256 ordered levels");
        Set<String> ids = new HashSet<>();
        Set<BakedLevelRef> paths = new HashSet<>();
        for (int index = 0; index < levels.size(); index++) {
            Level level = levels.get(index);
            if (!ids.add(level.id()) || !paths.add(level.level()))
                throw new IllegalArgumentException("Campaign level ids and paths must be unique");
            String expected = index + 1 < levels.size() ? levels.get(index + 1).id() : null;
            if (!Objects.equals(expected, level.next()))
                throw new IllegalArgumentException("Campaign next links must follow its ordered sequence and end with null");
        }
    }

    public record Level(String id, BakedLevelRef level, String next) {
        public Level {
            id = ModKeySyntax.requireLocalName(id);
            Objects.requireNonNull(level, "level");
            if (next != null) next = ModKeySyntax.requireLocalName(next);
        }
    }

    public static ModCampaignDefinition read(ModAssetRoot assets, String entry, String expectedHost) throws IOException {
        var limits = assets.limits();
        byte[] bytes = assets.readBounded(ModAssetRoot.requireNormalizedEntry(entry), limits.maxMetadataBytes());
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
        var constraints = StreamReadConstraints.builder().maxNestingDepth(limits.maxYamlDepth())
                .maxDocumentLength(limits.maxDocumentCodePoints()).maxTokenCount(8192)
                .maxStringLength(limits.maxStringChars()).maxNameLength(64).maxNumberLength(10).build();
        var mapper = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(constraints).build());
        try (JsonParser parser = mapper.createParser(bytes)) {
            JsonNode root = mapper.readTree(parser);
            if (parser.nextToken() != null) throw new IOException("Trailing campaign JSON token");
            fields(root, Set.of("name", "game", "localKey", "levels"));
            GameId game = GameId.fromCode(text(root, "game"));
            if (!game.code().equals(expectedHost)) throw new IOException("Campaign game must match its manifest baseGame");
            JsonNode array = root.get("levels");
            if (!array.isArray() || array.isEmpty() || array.size() > MAX_LEVELS)
                throw new IOException("Campaign needs 1..256 ordered levels");
            var levels = new java.util.ArrayList<Level>();
            for (JsonNode node : array) {
                fields(node, Set.of("id", "level", "next"));
                JsonNode next = node.get("next");
                if (!next.isNull() && !next.isTextual()) throw new IOException("Campaign next must be a level id or null");
                levels.add(new Level(text(node, "id"), new BakedLevelRef(text(node, "level")),
                        next.isNull() ? null : next.textValue()));
            }
            return new ModCampaignDefinition(text(root, "name"), game, text(root, "localKey"), levels);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Invalid campaign: " + invalid.getMessage(), invalid);
        }
    }

    /** Validates actual baked payloads against the declared host before any transaction publishes. */
    public List<ModLevelDefinition> validateLevels(ModAssetRoot assets) throws IOException {
        var host = GameModuleRegistry.createStockModule(game);
        var definitions = new java.util.ArrayList<ModLevelDefinition>();
        com.openggf.game.GameplayLaunchTeam requiredTeam = null;
        boolean first = true;
        for (Level level : levels) {
            var definition = ModLevelDefinitionParser.read(assets, level.level());
            if (!first && !Objects.equals(requiredTeam, definition.settings().team()))
                throw new IOException("Campaign acts must have the same required team (or all be unrestricted)");
            requiredTeam = definition.settings().team();
            first = false;
            try {
                var data = ModZoneLoader.prepareHostData(definition);
                host.getModZoneAdapter().validate("campaign", data);
                com.openggf.game.modzone.ModLevelSettingsValidation.validate(host, data.settings());
            } catch (IllegalArgumentException | com.openggf.game.modzone.ModZoneRegistrationException invalid) {
                throw new IOException("Campaign level " + level.id() + " is incompatible with " + game.code()
                        + ": " + invalid.getMessage(), invalid);
            }
            definitions.add(definition);
        }
        return List.copyOf(definitions);
    }

    public ModZoneContribution contribution(String insertAfter) {
        if (insertAfter != null) throw new IllegalArgumentException("A data-only campaign cannot inherit a stock insertion anchor");
        return ModZoneContribution.multiAct(localKey, levels.stream().map(Level::level).toList(), null, null, true)
                .asTerminalCampaign();
    }

    private static void fields(JsonNode node, Set<String> required) throws IOException {
        if (node == null || !node.isObject()) throw new IOException("Campaign entries must be objects");
        var actual = new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(required)) throw new IOException("Campaign fields must be exactly " + required);
    }

    private static String text(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
            throw new IOException("Campaign " + field + " must be nonblank text");
        return value.textValue();
    }
}
