package com.openggf.tools;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Compiles shot-local directing recipes into the portable Stageplay contract. No ROM or UI needed. */
public final class StageplayScriptCompiler {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private StageplayScriptCompiler() { }

    public static StageplayDocument compile(byte[] source) throws IOException {
        if (source.length == 0 || source.length > StageplayStore.MAX_BYTES)
            throw new IOException("Screenplay must contain 1 byte to 8 MiB");
        try {
            var parser = JSON.createParser(source);
            JsonNode parsed;
            try (parser) {
                parsed = JSON.readTree(parser);
                if (parser.nextToken() != null) throw new IOException("Trailing screenplay content");
            }
            ObjectNode script = object(parsed, "screenplay", "version", "name", "width", "height", "cast", "shots");
            if (integer(script, "version", -1) != 1) throw new IOException("Unsupported screenplay version");
            ArrayNode cast = array(script, "cast"), shots = array(script, "shots");
            if (cast.size() > 32 || shots.isEmpty() || shots.size() > 129)
                throw new IOException("Screenplay needs 1..129 shots and at most 32 cast members");
            ObjectNode document = JSON.createObjectNode();
            document.put("version", StageplayDocument.VERSION);
            document.set("name", required(script, "name"));
            document.put("width", integer(script, "width", 400));
            document.put("height", integer(script, "height", 224));
            ArrayNode actors = document.putArray("actors"), camera = document.putArray("camera"),
                    captions = document.putArray("captions"), audio = document.putArray("audio"),
                    effects = document.putArray("cameraEffects"), changes = document.putArray("sceneChanges");
            Map<String, ObjectNode> roles = new LinkedHashMap<>();
            Map<String, ObjectNode> lastPoses = new LinkedHashMap<>();
            for (JsonNode item : cast) {
                ObjectNode role = object(item, "cast member", "id", "name", "gameId", "character").deepCopy();
                String id = text(role, "id");
                if (roles.put(id, role) != null) throw new IOException("Duplicate cast id: " + id);
                role.putArray("keys"); actors.add(role);
            }
            Set<String> shotIds = new HashSet<>();
            int offset = 0;
            for (JsonNode item : shots) {
                ObjectNode shot = object(item, "shot", "id", "durationFrames", "set", "transition", "camera",
                        "actors", "captions", "audio", "cameraEffects");
                String id = text(shot, "id");
                if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}") || !shotIds.add(id))
                    throw new IOException("Invalid or duplicate shot id: " + id);
                int duration = integer(shot, "durationFrames", -1);
                if (duration < 1 || duration > StageplayDocument.MAX_DURATION_FRAMES - offset)
                    throw new IOException("Invalid duration for shot " + id);
                ObjectNode set = object(required(shot, "set"), "set", "gameId", "zone", "act", "romSha1", "levelProject").deepCopy();
                defaults(set, "romSha1", ""); defaults(set, "levelProject", "");
                if (offset == 0) {
                    document.set("set", set);
                    if (shot.has("transition")) throw new IOException("The first shot cannot have an incoming transition");
                } else {
                    ObjectNode transition = shot.has("transition")
                            ? object(shot.get("transition"), "transition", "kind", "durationFrames") : JSON.createObjectNode();
                    var change = changes.addObject();
                    change.put("id", id + ".change").put("frame", offset).set("set", set);
                    change.put("transition", transition.has("kind") ? text(transition, "kind") : "CUT");
                    change.put("durationFrames", integer(transition, "durationFrames", 0));
                }
                ArrayNode localCamera = array(shot, "camera");
                if (localCamera.isEmpty() || integer(object(localCamera.get(0), "camera key"), "frame", -1) != 0)
                    throw new IOException("Every shot needs a camera key at local frame zero: " + id);
                appendKeys(camera, localCamera, offset, duration, false);
                ObjectNode tracks = shot.has("actors") ? object(shot.get("actors"), "actor tracks") : JSON.createObjectNode();
                var fields = tracks.fieldNames();
                while (fields.hasNext()) {
                    String role = fields.next();
                    if (!roles.containsKey(role)) throw new IOException("Unknown cast id: " + role);
                }
                for (var entry : roles.entrySet()) {
                    String roleId = entry.getKey();
                    ArrayNode keys = (ArrayNode) entry.getValue().get("keys");
                    if (tracks.has(roleId)) {
                        ArrayNode track = array(tracks, roleId);
                        if (track.isEmpty() || integer(object(track.get(0), "actor key"), "frame", -1) != 0)
                            throw new IOException("Actor track must begin at local frame zero: " + id + "/" + roleId);
                        appendKeys(keys, track, offset, duration, true);
                        lastPoses.put(roleId, ((ObjectNode) keys.get(keys.size() - 1)).deepCopy());
                    } else {
                        ObjectNode hidden = lastPoses.containsKey(roleId) ? lastPoses.get(roleId).deepCopy() : JSON.createObjectNode();
                        hidden.put("frame", offset).put("visible", false);
                        actorDefaults(hidden); keys.add(hidden);
                    }
                }
                appendEvents(captions, optionalArray(shot, "captions"), id, "caption", offset, duration, text(set, "gameId"));
                appendEvents(audio, optionalArray(shot, "audio"), id, "audio", offset, duration, text(set, "gameId"));
                appendEvents(effects, optionalArray(shot, "cameraEffects"), id, "effect", offset, duration, text(set, "gameId"));
                offset += duration;
            }
            document.put("durationFrames", offset);
            return StageplayStore.decode(JSON.writeValueAsBytes(document));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid screenplay: " + failure.getMessage(), failure);
        }
    }

    private static void appendKeys(ArrayNode out, ArrayNode input, int offset, int duration, boolean actor) throws IOException {
        int previous = -1;
        for (JsonNode item : input) {
            ObjectNode key = object(item, actor ? "actor key" : "camera key", actor
                    ? new String[]{"frame", "x", "y", "animation", "pose", "flipX", "visible", "scale", "interpolation"}
                    : new String[]{"frame", "x", "y", "zoom", "interpolation"}).deepCopy();
            int frame = integer(key, "frame", -1);
            if (frame <= previous || frame >= duration) throw new IOException("Keys must ascend within the shot");
            previous = frame;
            key.put("frame", offset + frame);
            if (actor) actorDefaults(key);
            else { defaults(key, "zoom", 1); defaults(key, "interpolation", "LINEAR"); }
            out.add(key);
        }
    }

    private static void actorDefaults(ObjectNode key) {
        defaults(key, "x", 0); defaults(key, "y", 0); defaults(key, "animation", 0); defaults(key, "pose", -1);
        defaults(key, "flipX", false); defaults(key, "visible", true); defaults(key, "scale", 1);
        defaults(key, "interpolation", "LINEAR");
    }

    private static void appendEvents(ArrayNode out, ArrayNode input, String shot, String kind, int offset, int duration, String gameId) throws IOException {
        int index = 0;
        for (JsonNode item : input) {
            ObjectNode event = object(item, kind, switch (kind) {
                case "caption" -> new String[]{"id", "startFrame", "endFrame", "text", "x", "y", "argb"};
                case "audio" -> new String[]{"id", "frame", "kind", "gameId", "soundId", "asset", "gain"};
                default -> new String[]{"id", "startFrame", "endFrame", "amplitudeX", "amplitudeY", "frequency", "seed"};
            }).deepCopy();
            event.put("id", shot + "." + (event.has("id") ? text(event, "id") : kind + index++));
            if (kind.equals("audio")) {
                int frame = integer(event, "frame", -1);
                if (frame < 0 || frame >= duration) throw new IOException("Audio cue lies outside its shot");
                event.put("frame", offset + frame);
                defaults(event, "gameId", gameId); defaults(event, "soundId", 0);
                defaults(event, "asset", ""); defaults(event, "gain", 1);
            } else {
                int start = integer(event, "startFrame", -1), end = integer(event, "endFrame", -1);
                if (start < 0 || end <= start || end > duration) throw new IOException(kind + " lies outside its shot");
                event.put("startFrame", offset + start).put("endFrame", offset + end);
                if (kind.equals("caption")) { defaults(event, "x", 12); defaults(event, "y", 12); defaults(event, "argb", -1); }
                else defaults(event, "seed", 0);
            }
            out.add(event);
        }
    }

    private static JsonNode required(ObjectNode node, String name) throws IOException {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) throw new IOException("Missing " + name);
        return value;
    }
    private static ObjectNode object(JsonNode value, String label, String... allowed) throws IOException {
        if (!(value instanceof ObjectNode node)) throw new IOException(label + " must be an object");
        if (allowed.length != 0) {
            Set<String> names = Set.of(allowed);
            var fields = node.fieldNames();
            while (fields.hasNext()) { String name = fields.next(); if (!names.contains(name)) throw new IOException("Unknown " + label + " field: " + name); }
        }
        return node;
    }
    private static ArrayNode array(ObjectNode node, String field) throws IOException {
        if (!(required(node, field) instanceof ArrayNode array)) throw new IOException(field + " must be an array");
        return array;
    }
    private static ArrayNode optionalArray(ObjectNode node, String field) throws IOException {
        return node.has(field) ? array(node, field) : JSON.createArrayNode();
    }
    private static String text(ObjectNode node, String field) throws IOException {
        JsonNode value = required(node, field);
        if (!value.isTextual()) throw new IOException(field + " must be a string");
        return value.textValue();
    }
    private static int integer(ObjectNode node, String field, int fallback) throws IOException {
        if (!node.has(field)) return fallback;
        JsonNode value = node.get(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw new IOException(field + " must be an integer");
        return value.intValue();
    }
    private static void defaults(ObjectNode node, String name, Object value) {
        if (!node.has(name)) node.set(name, JSON.valueToTree(value));
    }
}
