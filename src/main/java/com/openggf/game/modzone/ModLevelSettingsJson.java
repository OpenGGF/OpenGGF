package com.openggf.game.modzone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

/** One strict settings shape shared by project files and baked level metadata. */
public final class ModLevelSettingsJson {
    private ModLevelSettingsJson() { }

    public static ModLevelSettings parse(JsonNode node) throws IOException {
        if (node == null || node.isNull()) return ModLevelSettings.DEFAULT;
        only(node, Set.of("background", "waterHeight", "team"), "settings");
        try {
            ModLevelSettings.StockScrollSource source = null;
            JsonNode bg = node.get("background");
            if (bg != null && !bg.isNull()) {
                only(bg, Set.of("zone", "act"), "settings.background");
                source = new ModLevelSettings.StockScrollSource(integer(bg.get("zone")), integer(bg.get("act")));
            }
            Integer height = node.hasNonNull("waterHeight") ? integer(node.get("waterHeight")) : null;
            GameplayLaunchTeam team = null;
            JsonNode value = node.get("team");
            if (value != null && !value.isNull()) {
                only(value, Set.of("main", "sidekicks"), "settings.team");
                JsonNode members = value.get("sidekicks");
                if (members == null || !members.isArray() || members.size() > 16)
                    throw new IOException("settings.team.sidekicks must be an array of at most 16 characters");
                var sidekicks = new ArrayList<CharacterKey>();
                for (JsonNode member : members) sidekicks.add(character(member));
                team = new GameplayLaunchTeam(character(value.get("main")), sidekicks);
            }
            return new ModLevelSettings(source, height, team);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IOException("Invalid level settings: " + error.getMessage(), error);
        }
    }

    public static ObjectNode toJson(ModLevelSettings settings) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        if (settings.background() != null) {
            node.putObject("background").put("zone", settings.background().zone()).put("act", settings.background().act());
        } else node.putNull("background");
        if (settings.waterHeight() == null) node.putNull("waterHeight");
        else node.put("waterHeight", settings.waterHeight());
        if (settings.team() != null) {
            ObjectNode team = node.putObject("team");
            team.put("main", settings.team().main().persisted());
            var sidekicks = team.putArray("sidekicks");
            settings.team().sidekicks().forEach(key -> sidekicks.add(key.persisted()));
        } else node.putNull("team");
        return node;
    }

    private static void only(JsonNode node, Set<String> fields, String label) throws IOException {
        if (!node.isObject()) throw new IOException(label + " must be an object");
        var names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!fields.contains(name)) throw new IOException("Unknown " + label + " field: " + name);
        }
    }

    private static int integer(JsonNode node) throws IOException {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt())
            throw new IOException("Level setting must be an exact integer");
        return node.intValue();
    }

    private static CharacterKey character(JsonNode node) throws IOException {
        if (node == null || !node.isTextual() || node.textValue().length() > 256)
            throw new IOException("Level setting character must be a bounded character key");
        return CharacterKey.parsePersisted(node.textValue());
    }
}
