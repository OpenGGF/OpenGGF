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
            ObjectNode script = object(parsed, "screenplay", "version", "name", "width", "height", "cast", "shots", "dialogueTypers", "dialogueSkins");
            int version = integer(script, "version", -1);
            if (version < 1 || version > 4) throw new IOException("Unsupported screenplay version");
            ArrayNode cast = array(script, "cast"), shots = array(script, "shots");
            if (cast.size() > 32 || shots.isEmpty() || shots.size() > 129)
                throw new IOException("Screenplay needs 1..129 shots and at most 32 cast members");
            ObjectNode document = JSON.createObjectNode();
            // Older recipes keep their original world/actor semantics through the same
            // strict migration as saved canonical documents.
            document.put("version", version <= 3 ? 5 : StageplayDocument.VERSION);
            document.set("name", required(script, "name"));
            document.put("width", integer(script, "width", 400));
            document.put("height", integer(script, "height", 224));
            ArrayNode actors = document.putArray("actors"), camera = document.putArray("camera"),
                    captions = document.putArray("captions"), audio = document.putArray("audio"),
                    effects = document.putArray("cameraEffects"), changes = document.putArray("sceneChanges"),
                    dialogue = document.putArray("dialogue"), puppets = document.putArray("puppets");
            appendDialogueProfiles(script, document);
            Map<String, ObjectNode> roles = new LinkedHashMap<>();
            Map<String, ObjectNode> lastPoses = new LinkedHashMap<>();
            Map<String, ArrayNode> commands = new LinkedHashMap<>();
            for (JsonNode item : cast) {
                ObjectNode role = object(item, "cast member", version == 1
                        ? new String[]{"id", "name", "gameId", "character"}
                        : new String[]{"id", "name", "gameId", "character", "puppet", "snapToFloor", "collisionPath"}).deepCopy();
                String id = text(role, "id");
                if (roles.put(id, role) != null) throw new IOException("Duplicate cast id: " + id);
                if (version >= 2 && bool(role, "puppet", true)) {
                    var track = puppets.addObject().put("actorId", id)
                            .put("snapToFloor", bool(role, "snapToFloor", true))
                            .put("collisionPath", integer(role, "collisionPath", 0));
                    commands.put(id, track.putArray("commands"));
                } else if (role.has("snapToFloor") || role.has("collisionPath")) {
                    throw new IOException("Floor snapping and collision paths require a puppet: " + id);
                }
                role.remove(java.util.List.of("puppet", "snapToFloor", "collisionPath"));
                role.putArray("keys"); actors.add(role);
            }
            Set<String> shotIds = new HashSet<>();
            int offset = 0;
            for (JsonNode item : shots) {
                ObjectNode shot = object(item, "shot", "id", "durationFrames", "set", "transition", "camera",
                        "actors", "captions", "audio", "cameraEffects", "dialogue", "actions");
                if (version == 1 && shot.has("actions")) throw new IOException("Puppet actions require screenplay version 2");
                if (version < 3) {
                    for (var cameraKey : optionalArray(shot,"camera")) if (cameraKey.has("follow"))
                        throw new IOException("Camera following requires screenplay version 3");
                    if (shot.has("actions")) {
                        var actionTracks = object(shot.get("actions"),"actions");
                        for (var track : actionTracks) for (var command : track) {
                            String kind = command.path("kind").asText();
                            if (command.has("target") || command.has("condition") || command.has("timeoutFrames")
                                    || !Set.of("MOVE_TO","STOP","JUMP").contains(kind))
                                throw new IOException("Live directing fields require screenplay version 3");
                        }
                    }
                }
                String id = text(shot, "id");
                if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}") || !shotIds.add(id))
                    throw new IOException("Invalid or duplicate shot id: " + id);
                int duration = integer(shot, "durationFrames", -1);
                if (duration < 1 || duration > StageplayDocument.MAX_DURATION_FRAMES - offset)
                    throw new IOException("Invalid duration for shot " + id);
                ObjectNode set = object(required(shot, "set"), "set", "gameId", "zone", "act", "romSha1", "levelProject", "worldPolicy").deepCopy();
                defaults(set, "romSha1", ""); defaults(set, "levelProject", "");
                if (version < 3 && set.has("worldPolicy")) throw new IOException("Live worlds require screenplay version 3");
                defaults(set, "worldPolicy", version <= 3 ? "TERRAIN_ONLY" : "LIVE");
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
                appendKeys(camera, localCamera, offset, duration, false, false);
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
                        appendKeys(keys, track, offset, duration, true, commands.containsKey(roleId));
                        lastPoses.put(roleId, ((ObjectNode) keys.get(keys.size() - 1)).deepCopy());
                    } else {
                        ObjectNode hidden = lastPoses.containsKey(roleId) ? lastPoses.get(roleId).deepCopy() : JSON.createObjectNode();
                        hidden.put("frame", offset).put("visible", false);
                        actorDefaults(hidden, commands.containsKey(roleId)); keys.add(hidden);
                    }
                }
                appendActions(shot, commands, id, offset, duration);
                appendEvents(captions, optionalArray(shot, "captions"), id, "caption", offset, duration, text(set, "gameId"));
                appendEvents(audio, optionalArray(shot, "audio"), id, "audio", offset, duration, text(set, "gameId"));
                appendEvents(effects, optionalArray(shot, "cameraEffects"), id, "effect", offset, duration, text(set, "gameId"));
                appendDialogue(dialogue, optionalArray(shot, "dialogue"), id, offset, duration,
                        document.path("width").intValue(), document.path("height").intValue());
                offset += duration;
            }
            document.put("durationFrames", offset);
            return StageplayStore.decode(JSON.writeValueAsBytes(document));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid screenplay: " + failure.getMessage(), failure);
        }
    }

    private static void appendActions(ObjectNode shot, Map<String, ArrayNode> tracks, String shotId,
                                      int offset, int duration) throws IOException {
        if (!shot.has("actions")) return;
        ObjectNode actions = object(shot.get("actions"), "puppet actions");
        var fields = actions.fieldNames();
        while (fields.hasNext()) {
            String actor = fields.next();
            ArrayNode out = tracks.get(actor);
            if (out == null) throw new IOException("Actions require a physical cast member: " + actor);
            int index = 0, previous = -1;
            for (JsonNode item : array(actions, actor)) {
                var command = object(item, "puppet action", "id", "frame", "kind", "x", "y", "urgency", "holdFrames", "target", "condition", "timeoutFrames").deepCopy();
                int frame = integer(command, "frame", -1);
                if (frame < previous || frame < 0 || frame >= duration)
                    throw new IOException("Puppet actions must ascend within their shot");
                previous = frame;
                String kind = text(command, "kind");
                if (kind.equals("MOVE_TO") || kind.equals("FLY_TO") || kind.equals("GLIDE_TO")) {
                    required(command, "x"); required(command, "y"); defaults(command, "urgency", 0.5);
                    defaults(command, "holdFrames", 0);
                } else {
                    defaults(command, "x", 0); defaults(command, "y", 0); defaults(command, "urgency", 0);
                    defaults(command, "holdFrames", kind.equals("JUMP") ? 12 : 0);
                }
                defaults(command, "target", StageplayDocument.ObjectTarget.none());
                defaults(command, "condition", "NONE"); defaults(command, "timeoutFrames", 0);
                if (kind.equals("JUMP") && integer(command, "holdFrames", -1) > duration - frame)
                    throw new IOException("Jump hold crosses a shot boundary");
                command.put("id", shotId + "." + actor + "."
                        + (command.has("id") ? text(command, "id") : "action" + index));
                index++;
                command.put("frame", offset + frame);
                out.add(command);
            }
        }
    }

    private static void appendDialogueProfiles(ObjectNode script, ObjectNode document) throws IOException {
        ArrayNode typers = document.putArray("dialogueTypers"), skins = document.putArray("dialogueSkins");
        for (var item : optionalArray(script, "dialogueTypers")) {
            var typer = object(item, "dialogue typer", "id", "framesPerGrapheme", "punctuationPauseFrames", "spacePauseFrames").deepCopy();
            defaults(typer, "framesPerGrapheme", 2); defaults(typer, "punctuationPauseFrames", 6);
            defaults(typer, "spacePauseFrames", 0); typers.add(typer);
        }
        for (var item : optionalArray(script, "dialogueSkins")) {
            var skin = object(item, "dialogue skin", "id", "textArgb", "nameArgb", "panelArgb", "borderArgb",
                    "padding", "decorationAsset", "decorationBorder", "pointer").deepCopy();
            defaults(skin, "textArgb", 0xFFF6F0DC); defaults(skin, "nameArgb", 0xFFFFD45C);
            defaults(skin, "panelArgb", 0xFF101827); defaults(skin, "borderArgb", 0xFF8895A7);
            defaults(skin, "padding", 6); defaults(skin, "decorationAsset", "");
            defaults(skin, "decorationBorder", 0); defaults(skin, "pointer", true); skins.add(skin);
        }
    }

    private static void appendDialogue(ArrayNode out, ArrayNode input, String shot, int offset,
                                       int duration, int width, int height) throws IOException {
        int index = 0;
        for (var item : input) {
            var cue = object(item, "dialogue", "id", "startFrame", "endFrame", "speakerActor", "speakerName",
                    "text", "skinId", "typerId", "x", "y", "width", "height", "portraitAsset", "revealFrames").deepCopy();
            int start = integer(cue, "startFrame", -1), end = integer(cue, "endFrame", -1);
            if (start < 0 || end <= start || end > duration) throw new IOException("Dialogue lies outside its shot");
            cue.put("id", shot + "." + (cue.has("id") ? text(cue, "id") : "dialogue" + index++));
            cue.put("startFrame", offset + start).put("endFrame", offset + end);
            defaults(cue, "speakerActor", ""); defaults(cue, "speakerName", "");
            defaults(cue, "skinId", ""); defaults(cue, "typerId", "");
            defaults(cue, "x", 12); defaults(cue, "y", 12);
            defaults(cue, "width", width - 24); defaults(cue, "height", Math.min(70, height - 24));
            defaults(cue, "portraitAsset", ""); defaults(cue, "revealFrames", java.util.List.of());
            out.add(cue);
        }
    }

    private static void appendKeys(ArrayNode out, ArrayNode input, int offset, int duration, boolean actor, boolean puppet) throws IOException {
        int previous = -1;
        for (JsonNode item : input) {
            ObjectNode key = object(item, actor ? "actor key" : "camera key", actor
                    ? new String[]{"frame", "x", "y", "animation", "pose", "flipX", "visible", "scale", "interpolation"}
                    : new String[]{"frame", "x", "y", "zoom", "interpolation", "follow"}).deepCopy();
            int frame = integer(key, "frame", -1);
            if (frame <= previous || frame >= duration) throw new IOException("Keys must ascend within the shot");
            previous = frame;
            key.put("frame", offset + frame);
            if (actor) actorDefaults(key, puppet);
            else { defaults(key, "zoom", 1); defaults(key, "follow", StageplayDocument.CameraFollow.none()); defaults(key, "interpolation", "LINEAR"); }
            out.add(key);
        }
    }

    private static void actorDefaults(ObjectNode key, boolean puppet) {
        defaults(key, "x", 0); defaults(key, "y", 0); defaults(key, "animation", 0); defaults(key, "pose", -1);
        defaults(key, "flipX", false); defaults(key, "visible", true); defaults(key, "scale", 1);
        defaults(key, "interpolation", puppet ? "HOLD" : "LINEAR");
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
    private static boolean bool(ObjectNode node, String field, boolean fallback) throws IOException {
        if (!node.has(field)) return fallback;
        JsonNode value = node.get(field);
        if (!value.isBoolean()) throw new IOException(field + " must be a boolean");
        return value.booleanValue();
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
