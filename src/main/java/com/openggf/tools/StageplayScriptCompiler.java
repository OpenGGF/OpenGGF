package com.openggf.tools;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
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

/**
 * Compiles shot-local directing recipes into the portable Stageplay contract. No ROM or UI needed.
 */
public final class StageplayScriptCompiler {
    public static final int VERSION = 6;
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    private static final ObjectMapper JSON =
            new ObjectMapper(
                    JsonFactory.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .streamReadConstraints(
                                    StreamReadConstraints.builder()
                                            .maxNestingDepth(16)
                                            .maxStringLength(4096)
                                            .maxNameLength(64)
                                            .maxNumberLength(32)
                                            .maxDocumentLength(MAX_BYTES)
                                            .maxTokenCount(12_000_000)
                                            .build())
                            .build());

    private StageplayScriptCompiler() {}

    public static StageplayDocument compile(byte[] source) throws IOException {
        if (source.length == 0 || source.length > MAX_BYTES)
            throw new IOException("Screenplay must contain 1 byte to 32 MiB");
        try {
            var parser = JSON.createParser(source);
            JsonNode parsed;
            try (parser) {
                parsed = JSON.readTree(parser);
                if (parser.nextToken() != null)
                    throw new IOException("Trailing screenplay content");
            }
            ObjectNode script =
                    object(
                            parsed,
                            "screenplay",
                            "version",
                            "name",
                            "width",
                            "height",
                            "cast",
                            "shots",
                            "dialogueTypers",
                            "dialogueSkins",
                            "objectCast",
                            "order");
            int version = integer(script, "version", -1);
            if (version != VERSION)
                throw new IOException("Unsupported screenplay version: " + version);
            ArrayNode cast = array(script, "cast"), shots = array(script, "shots");
            if (cast.size() > 32 || shots.isEmpty() || shots.size() > 129)
                throw new IOException("Screenplay needs 1..129 shots and at most 32 cast members");
            int totalDuration = 0;
            for (var item : shots) {
                int duration = integer(object(item, "shot"), "durationFrames", -1);
                if (duration < 1
                        || duration > StageplayDocument.MAX_DURATION_FRAMES - totalDuration)
                    throw new IOException("Invalid screenplay shot duration");
                totalDuration += duration;
            }
            ObjectNode document = JSON.createObjectNode();
            document.put("version", StageplayDocument.VERSION);
            document.set("name", required(script, "name"));
            document.put("width", integer(script, "width", 400));
            document.put("height", integer(script, "height", 224));
            ArrayNode actors = document.putArray("actors"),
                    camera = document.putArray("camera"),
                    captions = document.putArray("captions"),
                    audio = document.putArray("audio"),
                    effects = document.putArray("cameraEffects"),
                    changes = document.putArray("sceneChanges"),
                    dialogue = document.putArray("dialogue"),
                    puppets = document.putArray("puppets");
            appendDialogueProfiles(script, document);
            Map<String, ObjectNode> roles = new LinkedHashMap<>();
            Map<String, ObjectNode> lastPoses = new LinkedHashMap<>();
            Map<String, ArrayNode> commands = new LinkedHashMap<>();
            for (JsonNode item : cast) {
                ObjectNode role =
                        object(
                                        item,
                                        "cast member",
                                        new String[] {
                                                    "id",
                                                    "name",
                                                    "gameId",
                                                    "character",
                                                    "puppet",
                                                    "snapToFloor",
                                                    "collisionPath"
                                                })
                                .deepCopy();
                String id = text(role, "id");
                if (roles.put(id, role) != null) throw new IOException("Duplicate cast id: " + id);
                if (bool(role, "puppet", true)) {
                    var track =
                            puppets.addObject()
                                    .put("actorId", id)
                                    .put("snapToFloor", bool(role, "snapToFloor", true))
                                    .put("collisionPath", integer(role, "collisionPath", 0));
                    commands.put(id, track.putArray("commands"));
                } else if (role.has("snapToFloor") || role.has("collisionPath")) {
                    throw new IOException(
                            "Floor snapping and collision paths require a puppet: " + id);
                }
                role.remove(java.util.List.of("puppet", "snapToFloor", "collisionPath"));
                role.putArray("keys");
                actors.add(role);
            }
            Map<String, ObjectNode> objectRoles = new LinkedHashMap<>();
            Map<String, ObjectNode> lastObjectPoses = new LinkedHashMap<>();
            Map<String, ArrayNode> objectCommands = new LinkedHashMap<>();
            ArrayNode worldCommands = JSON.createArrayNode();
            {
                ArrayNode objects = document.putArray("objectActors");
                worldCommands = document.putArray("worldCommands");
                ArrayNode objectCast = optionalArray(script, "objectCast");
                if (objectCast.size() > 1024)
                    throw new IOException("Screenplay has more than 1024 object cast members");
                for (JsonNode item : objectCast) {
                    ObjectNode role =
                            object(
                                            item,
                                            "object cast member",
                                            "id",
                                            "name",
                                            "gameId",
                                            "zone",
                                            "act",
                                            "objectId",
                                            "subtype",
                                            "layoutIndex",
                                            "sourceKind")
                                    .deepCopy();
                    String id = text(role, "id");
                    if (roles.containsKey(id) || objectRoles.put(id, role) != null)
                        throw new IOException("Duplicate cast id: " + id);
                    required(role, "objectId");
                    defaults(role, "zone", 0);
                    defaults(role, "act", 0);
                    defaults(role, "subtype", 0);
                    defaults(role, "layoutIndex", -1);
                    defaults(role, "sourceKind", "LAYOUT_OBJECT");
                    role.putArray("keys");
                    objectCommands.put(id, role.putArray("commands"));
                    objects.add(role);
                }
            }
            Set<String> shotIds = new HashSet<>();
            int offset = 0;
            for (JsonNode item : shots) {
                ObjectNode shot =
                        object(
                                item,
                                "shot",
                                "id",
                                "durationFrames",
                                "set",
                                "transition",
                                "camera",
                                "actors",
                                "captions",
                                "audio",
                                "cameraEffects",
                                "dialogue",
                                "actions",
                                "objects",
                                "objectActions",
                                "worldCommands");
                String id = text(shot, "id");
                if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}") || !shotIds.add(id))
                    throw new IOException("Invalid or duplicate shot id: " + id);
                int duration = integer(shot, "durationFrames", -1);
                if (duration < 1 || duration > StageplayDocument.MAX_DURATION_FRAMES - offset)
                    throw new IOException("Invalid duration for shot " + id);
                ObjectNode set =
                        object(
                                        required(shot, "set"),
                                        "set",
                                        "gameId",
                                        "zone",
                                        "act",
                                        "romSha1",
                                        "levelProject",
                                        "worldPolicy")
                                .deepCopy();
                defaults(set, "romSha1", "");
                defaults(set, "levelProject", "");
                defaults(set, "worldPolicy", "LIVE");
                if (offset == 0) {
                    document.set("set", set);
                    if (shot.has("transition"))
                        throw new IOException("The first shot cannot have an incoming transition");
                } else {
                    ObjectNode transition =
                            shot.has("transition")
                                    ? object(
                                            shot.get("transition"),
                                            "transition",
                                            "kind",
                                            "durationFrames",
                                            "id")
                                    : JSON.createObjectNode();
                    var change = changes.addObject();
                    change.put(
                                    "id",
                                    transition.has("id")
                                            ? text(transition, "id")
                                            : id + ".change")
                            .put("frame", offset)
                            .set("set", set);
                    change.put(
                            "transition",
                            transition.has("kind") ? text(transition, "kind") : "CUT");
                    change.put("durationFrames", integer(transition, "durationFrames", 0));
                }
                ArrayNode localCamera = array(shot, "camera");
                if (localCamera.isEmpty()
                        || integer(object(localCamera.get(0), "camera key"), "frame", -1) != 0)
                    throw new IOException(
                            "Every shot needs a camera key at local frame zero: " + id);
                appendKeys(camera, localCamera, offset, duration, false, false);
                ObjectNode tracks =
                        shot.has("actors")
                                ? object(shot.get("actors"), "actor tracks")
                                : JSON.createObjectNode();
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
                        if (track.isEmpty()
                                || integer(object(track.get(0), "actor key"), "frame", -1) != 0)
                            throw new IOException(
                                    "Actor track must begin at local frame zero: "
                                            + id
                                            + "/"
                                            + roleId);
                        appendKeys(
                                keys, track, offset, duration, true, commands.containsKey(roleId));
                        lastPoses.put(roleId, ((ObjectNode) keys.get(keys.size() - 1)).deepCopy());
                    } else {
                        ObjectNode hidden =
                                lastPoses.containsKey(roleId)
                                        ? lastPoses.get(roleId).deepCopy()
                                        : JSON.createObjectNode();
                        hidden.put("frame", offset).put("visible", false);
                        actorDefaults(hidden, commands.containsKey(roleId));
                        keys.add(hidden);
                    }
                }
                appendActions(shot, commands, id, offset, duration);
                {
                    appendObjectTracks(shot, objectRoles, lastObjectPoses, id, offset, duration);
                    appendObjectActions(shot, objectCommands, id, offset, duration);
                    appendWorldCommands(
                            worldCommands,
                            optionalArray(shot, "worldCommands"),
                            id,
                            offset,
                            duration);
                }
                appendEvents(
                        captions,
                        optionalArray(shot, "captions"),
                        id,
                        "caption",
                        offset,
                        duration,
                        text(set, "gameId"),
                        totalDuration);
                appendEvents(
                        audio,
                        optionalArray(shot, "audio"),
                        id,
                        "audio",
                        offset,
                        duration,
                        text(set, "gameId"),
                        totalDuration);
                appendEvents(
                        effects,
                        optionalArray(shot, "cameraEffects"),
                        id,
                        "effect",
                        offset,
                        duration,
                        text(set, "gameId"),
                        totalDuration);
                appendDialogue(
                        dialogue,
                        optionalArray(shot, "dialogue"),
                        id,
                        offset,
                        duration,
                        document.path("width").intValue(),
                        document.path("height").intValue(),
                        totalDuration);
                offset += duration;
            }
            document.put("durationFrames", offset);
            if (script.has("order"))
                applyOrder(
                        document,
                        object(
                                script.get("order"),
                                "order",
                                "puppets",
                                "captions",
                                "audio",
                                "cameraEffects",
                                "dialogue"));
            return StageplayStore.decode(JSON.writeValueAsBytes(document));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid screenplay: " + failure.getMessage(), failure);
        }
    }

    private static void appendObjectTracks(
            ObjectNode shot,
            Map<String, ObjectNode> roles,
            Map<String, ObjectNode> lastPoses,
            String shotId,
            int offset,
            int duration)
            throws IOException {
        ObjectNode tracks =
                shot.has("objects")
                        ? object(shot.get("objects"), "object tracks")
                        : JSON.createObjectNode();
        var fields = tracks.fieldNames();
        while (fields.hasNext()) {
            String id = fields.next();
            if (!roles.containsKey(id)) throw new IOException("Unknown object cast id: " + id);
        }
        for (var entry : roles.entrySet()) {
            String id = entry.getKey();
            ArrayNode keys = (ArrayNode) entry.getValue().get("keys");
            if (tracks.has(id)) {
                ArrayNode input = array(tracks, id);
                if (input.isEmpty()
                        || integer(object(input.get(0), "object key"), "frame", -1) != 0)
                    throw new IOException(
                            "Object track must begin at local frame zero: " + shotId + "/" + id);
                int previous = -1;
                for (var item : input) {
                    var key =
                            object(
                                            item,
                                            "object key",
                                            "frame",
                                            "x",
                                            "y",
                                            "animation",
                                            "pose",
                                            "flipX",
                                            "visible",
                                            "interpolation",
                                            "mode",
                                            "parts")
                                    .deepCopy();
                    int frame = integer(key, "frame", -1);
                    if (frame <= previous || frame < 0 || frame >= duration)
                        throw new IOException("Object keys must ascend within their shot");
                    previous = frame;
                    objectDefaults(key);
                    key.put("frame", offset + frame);
                    keys.add(key);
                }
                lastPoses.put(id, ((ObjectNode) keys.get(keys.size() - 1)).deepCopy());
            } else {
                ObjectNode hidden =
                        lastPoses.containsKey(id)
                                ? lastPoses.get(id).deepCopy()
                                : JSON.createObjectNode();
                hidden.put("frame", offset).put("visible", false);
                objectDefaults(hidden);
                keys.add(hidden);
            }
        }
    }

    private static void objectDefaults(ObjectNode key) throws IOException {
        defaults(key, "x", 0);
        defaults(key, "y", 0);
        defaults(key, "animation", 0);
        defaults(key, "pose", -1);
        defaults(key, "flipX", false);
        defaults(key, "visible", true);
        defaults(key, "mode", "FREE");
        defaults(key, "interpolation", text(key, "mode").equals("FREE") ? "HOLD" : "LINEAR");
        defaults(key, "parts", java.util.List.of());
        for (var item : array(key, "parts")) {
            var part = object(item, "object part pose", "partId", "animation", "pose");
            defaults(part, "animation", 0);
            defaults(part, "pose", -1);
        }
    }

    private static void appendObjectActions(
            ObjectNode shot,
            Map<String, ArrayNode> tracks,
            String shotId,
            int offset,
            int duration)
            throws IOException {
        if (!shot.has("objectActions")) return;
        var actions = object(shot.get("objectActions"), "object actions");
        var fields = actions.fieldNames();
        while (fields.hasNext()) {
            String actor = fields.next();
            ArrayNode out = tracks.get(actor);
            if (out == null)
                throw new IOException("Object actions require an object cast member: " + actor);
            int index = 0, previous = -1;
            for (var item : array(actions, actor)) {
                var command =
                        object(item, "object action", "id", "frame", "kind", "x", "y", "speed")
                                .deepCopy();
                int frame = integer(command, "frame", -1);
                if (frame <= previous || frame < 0 || frame >= duration)
                    throw new IOException("Object actions must strictly ascend within their shot");
                previous = frame;
                if (text(command, "kind").equals("MOVE_TO")) {
                    required(command, "x");
                    required(command, "y");
                    defaults(command, "speed", 1);
                } else {
                    defaults(command, "x", 0);
                    defaults(command, "y", 0);
                    defaults(command, "speed", 0);
                }
                command.put(
                        "id",
                        command.has("id")
                                ? text(command, "id")
                                : shotId
                                        + "."
                                        + actor
                                        + "."
                                        + "objectAction" + index);
                index++;
                command.put("frame", offset + frame);
                out.add(command);
            }
        }
    }

    private static void appendWorldCommands(
            ArrayNode out, ArrayNode input, String shotId, int offset, int duration)
            throws IOException {
        int index = 0, previous = -1;
        for (var item : input) {
            var command =
                    object(item, "world command", "id", "frame", "kind", "actorId", "target")
                            .deepCopy();
            int frame = integer(command, "frame", -1);
            if (frame < previous || frame < 0 || frame >= duration)
                throw new IOException("World commands must ascend within their shot");
            previous = frame;
            defaults(command, "actorId", "");
            defaults(command, "target", StageplayDocument.ObjectTarget.none());
            if (command.has("target")) {
                var target =
                        object(
                                command.get("target"),
                                "world command target",
                                "layoutIndex",
                                "objectId",
                                "subtype",
                                "offsetX",
                                "offsetY");
                defaults(target, "offsetX", 0);
                defaults(target, "offsetY", 0);
            }
            command.put(
                    "id",
                    command.has("id")
                            ? text(command, "id")
                            : shotId
                                    + "."
                                    + "worldCommand" + index);
            index++;
            command.put("frame", offset + frame);
            out.add(command);
        }
    }

    private static void appendActions(
            ObjectNode shot,
            Map<String, ArrayNode> tracks,
            String shotId,
            int offset,
            int duration)
            throws IOException {
        if (!shot.has("actions")) return;
        ObjectNode actions = object(shot.get("actions"), "puppet actions");
        var fields = actions.fieldNames();
        while (fields.hasNext()) {
            String actor = fields.next();
            ArrayNode out = tracks.get(actor);
            if (out == null)
                throw new IOException("Actions require a physical cast member: " + actor);
            int index = 0, previous = -1;
            for (JsonNode item : array(actions, actor)) {
                var command =
                        object(
                                        item,
                                        "puppet action",
                                        "id",
                                        "frame",
                                        "kind",
                                        "x",
                                        "y",
                                        "urgency",
                                        "holdFrames",
                                        "target",
                                        "condition",
                                        "timeoutFrames")
                                .deepCopy();
                int frame = integer(command, "frame", -1);
                if (frame < previous || frame < 0 || frame >= duration)
                    throw new IOException("Puppet actions must ascend within their shot");
                previous = frame;
                String kind = text(command, "kind");
                if (kind.equals("MOVE_TO") || kind.equals("FLY_TO") || kind.equals("GLIDE_TO")) {
                    required(command, "x");
                    required(command, "y");
                    defaults(command, "urgency", 0.5);
                    defaults(command, "holdFrames", 0);
                } else {
                    defaults(command, "x", 0);
                    defaults(command, "y", 0);
                    defaults(command, "urgency", 0);
                    defaults(command, "holdFrames", kind.equals("JUMP") ? 12 : 0);
                }
                defaults(command, "target", StageplayDocument.ObjectTarget.none());
                defaults(command, "condition", "NONE");
                defaults(command, "timeoutFrames", 0);
                if (kind.equals("JUMP") && integer(command, "holdFrames", -1) > duration - frame)
                    throw new IOException("Jump hold crosses a shot boundary");
                command.put(
                        "id",
                        command.has("id")
                                ? text(command, "id")
                                : shotId
                                        + "."
                                        + actor
                                        + "."
                                        + "action" + index);
                index++;
                command.put("frame", offset + frame);
                out.add(command);
            }
        }
    }

    private static void appendDialogueProfiles(ObjectNode script, ObjectNode document)
            throws IOException {
        ArrayNode typers = document.putArray("dialogueTypers"),
                skins = document.putArray("dialogueSkins");
        for (var item : optionalArray(script, "dialogueTypers")) {
            var typer =
                    object(
                                    item,
                                    "dialogue typer",
                                    "id",
                                    "framesPerGrapheme",
                                    "punctuationPauseFrames",
                                    "spacePauseFrames")
                            .deepCopy();
            defaults(typer, "framesPerGrapheme", 2);
            defaults(typer, "punctuationPauseFrames", 6);
            defaults(typer, "spacePauseFrames", 0);
            typers.add(typer);
        }
        for (var item : optionalArray(script, "dialogueSkins")) {
            var skin =
                    object(
                                    item,
                                    "dialogue skin",
                                    "id",
                                    "textArgb",
                                    "nameArgb",
                                    "panelArgb",
                                    "borderArgb",
                                    "padding",
                                    "decorationAsset",
                                    "decorationBorder",
                                    "pointer")
                            .deepCopy();
            defaults(skin, "textArgb", 0xFFF6F0DC);
            defaults(skin, "nameArgb", 0xFFFFD45C);
            defaults(skin, "panelArgb", 0xFF101827);
            defaults(skin, "borderArgb", 0xFF8895A7);
            defaults(skin, "padding", 6);
            defaults(skin, "decorationAsset", "");
            defaults(skin, "decorationBorder", 0);
            defaults(skin, "pointer", true);
            skins.add(skin);
        }
    }

    private static void appendDialogue(
            ArrayNode out,
            ArrayNode input,
            String shot,
            int offset,
            int duration,
            int width,
            int height,
            int totalDuration)
            throws IOException {
        int index = 0;
        for (var item : input) {
            var cue =
                    object(
                                    item,
                                    "dialogue",
                                    "id",
                                    "startFrame",
                                    "endFrame",
                                    "speakerActor",
                                    "speakerName",
                                    "text",
                                    "skinId",
                                    "typerId",
                                    "x",
                                    "y",
                                    "width",
                                    "height",
                                    "portraitAsset",
                                    "revealFrames")
                            .deepCopy();
            int start = integer(cue, "startFrame", -1), end = integer(cue, "endFrame", -1);
            if (start < 0
                    || start >= duration
                    || end <= start
                    || end > (totalDuration - offset))
                throw new IOException("Dialogue lies outside its shot start or scene end");
            cue.put(
                    "id",
                    cue.has("id")
                            ? text(cue, "id")
                            : shot
                                    + "."
                                    + "dialogue" + index++);
            cue.put("startFrame", offset + start).put("endFrame", offset + end);
            defaults(cue, "speakerActor", "");
            defaults(cue, "speakerName", "");
            defaults(cue, "skinId", "");
            defaults(cue, "typerId", "");
            defaults(cue, "x", 12);
            defaults(cue, "y", 12);
            defaults(cue, "width", width - 24);
            defaults(cue, "height", Math.min(70, height - 24));
            defaults(cue, "portraitAsset", "");
            defaults(cue, "revealFrames", java.util.List.of());
            out.add(cue);
        }
    }

    private static void appendKeys(
            ArrayNode out, ArrayNode input, int offset, int duration, boolean actor, boolean puppet)
            throws IOException {
        int previous = -1;
        for (JsonNode item : input) {
            ObjectNode key =
                    object(
                                    item,
                                    actor ? "actor key" : "camera key",
                                    actor
                                            ? new String[] {
                                                "frame",
                                                "x",
                                                "y",
                                                "animation",
                                                "pose",
                                                "flipX",
                                                "visible",
                                                "scale",
                                                "interpolation"
                                            }
                                            : new String[] {
                                                "frame", "x", "y", "zoom", "interpolation", "follow"
                                            })
                            .deepCopy();
            int frame = integer(key, "frame", -1);
            if (frame <= previous || frame >= duration)
                throw new IOException("Keys must ascend within the shot");
            previous = frame;
            key.put("frame", offset + frame);
            if (actor) actorDefaults(key, puppet);
            else {
                defaults(key, "zoom", 1);
                defaults(key, "follow", StageplayDocument.CameraFollow.none());
                defaults(key, "interpolation", "LINEAR");
            }
            out.add(key);
        }
    }

    private static void actorDefaults(ObjectNode key, boolean puppet) {
        defaults(key, "x", 0);
        defaults(key, "y", 0);
        defaults(key, "animation", 0);
        defaults(key, "pose", -1);
        defaults(key, "flipX", false);
        defaults(key, "visible", true);
        defaults(key, "scale", 1);
        defaults(key, "interpolation", puppet ? "HOLD" : "LINEAR");
    }

    private static void appendEvents(
            ArrayNode out,
            ArrayNode input,
            String shot,
            String kind,
            int offset,
            int duration,
            String gameId,
            int totalDuration)
            throws IOException {
        int index = 0;
        for (JsonNode item : input) {
            ObjectNode event =
                    object(
                                    item,
                                    kind,
                                    switch (kind) {
                                        case "caption" ->
                                                new String[] {
                                                    "id",
                                                    "startFrame",
                                                    "endFrame",
                                                    "text",
                                                    "x",
                                                    "y",
                                                    "argb"
                                                };
                                        case "audio" ->
                                                new String[] {
                                                    "id", "frame", "kind", "gameId", "soundId",
                                                    "asset", "gain"
                                                };
                                        default ->
                                                new String[] {
                                                    "id",
                                                    "startFrame",
                                                    "endFrame",
                                                    "amplitudeX",
                                                    "amplitudeY",
                                                    "frequency",
                                                    "seed"
                                                };
                                    })
                            .deepCopy();
            event.put(
                    "id",
                    event.has("id")
                            ? text(event, "id")
                            : shot + "." + kind + index++);
            if (kind.equals("audio")) {
                int frame = integer(event, "frame", -1);
                if (frame < 0 || frame >= duration)
                    throw new IOException("Audio cue lies outside its shot");
                event.put("frame", offset + frame);
                defaults(event, "gameId", gameId);
                defaults(event, "soundId", 0);
                defaults(event, "asset", "");
                defaults(event, "gain", 1);
            } else {
                int start = integer(event, "startFrame", -1), end = integer(event, "endFrame", -1);
                if (start < 0
                        || start >= duration
                        || end <= start
                        || end > (totalDuration - offset))
                    throw new IOException(kind + " lies outside its shot start or scene end");
                event.put("startFrame", offset + start).put("endFrame", offset + end);
                if (kind.equals("caption")) {
                    defaults(event, "x", 12);
                    defaults(event, "y", 12);
                    defaults(event, "argb", -1);
                } else defaults(event, "seed", 0);
            }
            out.add(event);
        }
    }

    /** Restore list order independently of chronological shot ownership. */
    private static void applyOrder(ObjectNode document, ObjectNode order) throws IOException {
        var fields = order.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            ArrayNode requested = array(order, field), existing = array(document, field);
            if (requested.size() != existing.size())
                throw new IOException("Order must name every " + field + " identity exactly once");
            Map<String, JsonNode> byId = new LinkedHashMap<>();
            String identity = field.equals("puppets") ? "actorId" : "id";
            for (var entry : existing) {
                String id = text(object(entry, field + " entry"), identity);
                if (byId.put(id, entry) != null)
                    throw new IOException("Duplicate " + field + " identity: " + id);
            }
            ArrayNode sorted = JSON.createArrayNode();
            for (var item : requested) {
                if (!item.isTextual()) throw new IOException("Order identities must be strings");
                JsonNode entry = byId.remove(item.textValue());
                if (entry == null)
                    throw new IOException(
                            "Unknown or duplicate "
                                    + field
                                    + " order identity: "
                                    + item.textValue());
                sorted.add(entry);
            }
            document.set(field, sorted);
        }
    }

    private static JsonNode required(ObjectNode node, String name) throws IOException {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) throw new IOException("Missing " + name);
        return value;
    }

    private static ObjectNode object(JsonNode value, String label, String... allowed)
            throws IOException {
        if (!(value instanceof ObjectNode node))
            throw new IOException(label + " must be an object");
        if (allowed.length != 0) {
            Set<String> names = Set.of(allowed);
            var fields = node.fieldNames();
            while (fields.hasNext()) {
                String name = fields.next();
                if (!names.contains(name))
                    throw new IOException("Unknown " + label + " field: " + name);
            }
        }
        return node;
    }

    private static ArrayNode array(ObjectNode node, String field) throws IOException {
        if (!(required(node, field) instanceof ArrayNode array))
            throw new IOException(field + " must be an array");
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

    private static boolean bool(ObjectNode node, String field, boolean fallback)
            throws IOException {
        if (!node.has(field)) return fallback;
        JsonNode value = node.get(field);
        if (!value.isBoolean()) throw new IOException(field + " must be a boolean");
        return value.booleanValue();
    }

    private static int integer(ObjectNode node, String field, int fallback) throws IOException {
        if (!node.has(field)) return fallback;
        JsonNode value = node.get(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt())
            throw new IOException(field + " must be an integer");
        return value.intValue();
    }

    private static void defaults(ObjectNode node, String name, Object value) {
        if (!node.has(name)) node.set(name, JSON.valueToTree(value));
    }
}
