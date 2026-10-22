package com.openggf.level.objects.authored;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.game.MusicReference;
import java.io.IOException;
import java.util.Set;

/** One strict typed placement shape shared by project and baked level files. */
public final class AuthoredObjectPropertiesJson {
    private AuthoredObjectPropertiesJson() { }

    public static AuthoredObjectProperties parse(JsonNode node) throws IOException {
        if (node == null || node.isNull()) return null;
        try {
            String kind = text(node, "kind");
            return switch (kind) {
                case "camera-zone" -> {
                    only(node, Set.of("kind", "width", "height", "minX", "maxX", "minY", "maxY", "releaseCondition", "releaseAt"));
                    yield new AuthoredObjectProperties.CameraZone(integer(node, "width"), integer(node, "height"),
                            integer(node, "minX"), integer(node, "maxX"), integer(node, "minY"), integer(node, "maxY"),
                            AuthoredObjectProperties.ReleaseCondition.valueOf(text(node, "releaseCondition")), integer(node, "releaseAt"));
                }
                case "music-zone" -> {
                    only(node, Set.of("kind", "width", "height", "music", "once"));
                    yield new AuthoredObjectProperties.MusicZone(integer(node, "width"), integer(node, "height"),
                            music(node.get("music")), bool(node, "once"));
                }
                case "warp" -> {
                    only(node, Set.of("kind", "width", "height", "link", "twoWay"));
                    yield new AuthoredObjectProperties.Warp(integer(node, "width"), integer(node, "height"),
                            text(node, "link"), bool(node, "twoWay"));
                }
                case "stageplay-trigger" -> {
                    only(node, Set.of("kind", "width", "height", "resource", "trigger", "blocking", "once", "consume"));
                    yield new AuthoredObjectProperties.StageplayTrigger(integer(node, "width"), integer(node, "height"),
                            text(node, "resource"), AuthoredObjectProperties.Trigger.valueOf(text(node, "trigger")),
                            bool(node, "blocking"), bool(node, "once"), bool(node, "consume"));
                }
                default -> throw new IOException("Unknown authored object property kind: " + kind);
            };
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IOException("Invalid authored object properties: " + error.getMessage(), error);
        }
    }

    public static ObjectNode toJson(AuthoredObjectProperties properties) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("kind", properties.objectKey().substring("openggf:".length()));
        node.put("width", properties.width()).put("height", properties.height());
        switch (properties) {
            case AuthoredObjectProperties.CameraZone p -> node.put("minX", p.minX()).put("maxX", p.maxX())
                    .put("minY", p.minY()).put("maxY", p.maxY()).put("releaseCondition", p.releaseCondition().name())
                    .put("releaseAt", p.releaseAt());
            case AuthoredObjectProperties.MusicZone p -> {
                ObjectNode music = node.putObject("music");
                switch (p.music()) {
                    case MusicReference.Stock value -> music.put("stockId", value.musicId());
                    case MusicReference.Namespaced value -> music.put("trackKey", value.owner() + ":" + value.localName());
                }
                node.put("once", p.once());
            }
            case AuthoredObjectProperties.Warp p -> node.put("link", p.link()).put("twoWay", p.twoWay());
            case AuthoredObjectProperties.StageplayTrigger p -> node.put("resource", p.resource())
                    .put("trigger", p.trigger().name()).put("blocking", p.blocking()).put("once", p.once()).put("consume", p.consume());
        }
        return node;
    }

    private static MusicReference music(JsonNode node) throws IOException {
        if (node != null && node.has("stockId")) {
            only(node, Set.of("stockId"));
            return MusicReference.stock(integer(node, "stockId"));
        }
        only(node, Set.of("trackKey"));
        String key = text(node, "trackKey");
        int separator = key.indexOf(':');
        if (separator < 1) throw new IOException("Authored music trackKey must be owner:localName");
        return MusicReference.namespaced(key.substring(0, separator), key.substring(separator + 1));
    }

    private static void only(JsonNode node, Set<String> fields) throws IOException {
        if (node == null || !node.isObject()) throw new IOException("Authored properties must be an object");
        var names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!fields.contains(name)) throw new IOException("Unknown authored property field: " + name);
        }
        for (String field : fields) if (!node.hasNonNull(field)) throw new IOException("Missing authored property: " + field);
    }

    private static int integer(JsonNode node, String field) throws IOException {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt())
            throw new IOException("Authored property " + field + " must be an exact integer");
        return value.intValue();
    }

    private static boolean bool(JsonNode node, String field) throws IOException {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isBoolean()) throw new IOException("Authored property " + field + " must be a boolean");
        return value.booleanValue();
    }

    private static String text(JsonNode node, String field) throws IOException {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.textValue().length() > 512)
            throw new IOException("Authored property " + field + " must be a string of at most 512 characters");
        return value.textValue();
    }
}
