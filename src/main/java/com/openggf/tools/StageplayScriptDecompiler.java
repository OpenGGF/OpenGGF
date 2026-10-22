package com.openggf.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.stageplay.StageplayDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Reconstructs editable, shot-local screenplay from the complete canonical document. */
public final class StageplayScriptDecompiler {
    private static final ObjectMapper JSON = new ObjectMapper();

    private StageplayScriptDecompiler() {}

    /**
     * Emits deterministic screenplay v6. Identity, list order and supported authored data are
     * preserved; source formatting, comments and the former shot labels are not canonical data.
     */
    public static byte[] decompile(StageplayDocument document) throws IOException {
        Objects.requireNonNull(document, "document");
        byte[] bytes = encode(build(document));
        StageplayDocument compiled = StageplayScriptCompiler.compile(bytes);
        if (!document.equals(compiled)) {
            throw new IOException(
                    "Decompiled screenplay did not preserve the complete Stageplay document");
        }
        // Compare the next serialization directly, without recursively invoking decompile.
        // This also detects unstable synthetic shot names or ID/order reconstruction.
        if (!java.util.Arrays.equals(bytes, encode(build(compiled)))) {
            throw new IOException("Decompiled screenplay is not stable across recompilation");
        }
        return bytes;
    }

    private static ObjectNode build(StageplayDocument document) {
        ObjectNode canonical = JSON.valueToTree(document);
        ObjectNode script = JSON.createObjectNode();
        script.put("version", StageplayScriptCompiler.VERSION);
        script.set("name", canonical.get("name"));
        script.set("width", canonical.get("width"));
        script.set("height", canonical.get("height"));
        Map<String, ObjectNode> puppets = new LinkedHashMap<>();
        for (var puppet : canonical.withArray("puppets")) {
            puppets.put(puppet.path("actorId").textValue(), (ObjectNode) puppet);
        }
        ArrayNode cast = script.putArray("cast");
        for (var actor : canonical.withArray("actors")) {
            ObjectNode role = actor.deepCopy();
            role.remove("keys");
            ObjectNode puppet = puppets.get(role.path("id").textValue());
            role.put("puppet", puppet != null);
            if (puppet != null) {
                role.set("snapToFloor", puppet.get("snapToFloor"));
                role.set("collisionPath", puppet.get("collisionPath"));
            }
            cast.add(role);
        }
        ArrayNode objects = script.putArray("objectCast");
        for (var actor : canonical.withArray("objectActors")) {
            ObjectNode role = actor.deepCopy();
            role.remove(java.util.List.of("keys", "commands"));
            objects.add(role);
        }
        script.set("dialogueTypers", canonical.get("dialogueTypers"));
        script.set("dialogueSkins", canonical.get("dialogueSkins"));
        ObjectNode order = script.putObject("order");
        for (String field :
                java.util.List.of("puppets", "captions", "audio", "cameraEffects", "dialogue")) {
            ArrayNode ids = order.putArray(field);
            for (var entry : canonical.withArray(field)) {
                ids.add(entry.path(field.equals("puppets") ? "actorId" : "id").textValue());
            }
        }
        ArrayNode changes = canonical.withArray("sceneChanges");
        ArrayNode shots = script.putArray("shots");
        int first = 0;
        for (int index = 0; index <= changes.size(); index++) {
            int end =
                    index < changes.size()
                            ? changes.get(index).path("frame").intValue()
                            : document.durationFrames();
            ObjectNode shot = shots.addObject();
            shot.put("id", "shot" + index);
            shot.put("durationFrames", end - first);
            if (index == 0) {
                shot.set("set", canonical.get("set"));
            } else {
                JsonNode change = changes.get(index - 1);
                shot.set("set", change.get("set"));
                ObjectNode transition = shot.putObject("transition");
                transition.set("id", change.get("id"));
                transition.set("kind", change.get("transition"));
                transition.set("durationFrames", change.get("durationFrames"));
            }
            shot.set("camera", localize(canonical.withArray("camera"), first, end, "frame"));
            ObjectNode actorTracks = shot.putObject("actors");
            ObjectNode actions = shot.putObject("actions");
            for (var actor : canonical.withArray("actors")) {
                String id = actor.path("id").textValue();
                actorTracks.set(id, localize((ArrayNode) actor.get("keys"), first, end, "frame"));
                ObjectNode puppet = puppets.get(id);
                if (puppet != null) {
                    actions.set(
                            id, localize((ArrayNode) puppet.get("commands"), first, end, "frame"));
                }
            }
            ObjectNode objectTracks = shot.putObject("objects");
            ObjectNode objectActions = shot.putObject("objectActions");
            for (var actor : canonical.withArray("objectActors")) {
                String id = actor.path("id").textValue();
                objectTracks.set(id, localize((ArrayNode) actor.get("keys"), first, end, "frame"));
                objectActions.set(
                        id, localize((ArrayNode) actor.get("commands"), first, end, "frame"));
            }
            shot.set(
                    "worldCommands",
                    localize(canonical.withArray("worldCommands"), first, end, "frame"));
            shot.set("audio", localize(canonical.withArray("audio"), first, end, "frame"));
            for (String field : java.util.List.of("captions", "cameraEffects", "dialogue")) {
                shot.set(field, localize(canonical.withArray(field), first, end, "startFrame"));
            }
            first = end;
        }
        return script;
    }

    /** A crossing interval remains owned by its start shot; its end is relative to that origin. */
    private static ArrayNode localize(ArrayNode input, int first, int end, String startField) {
        ArrayNode output = JSON.createArrayNode();
        for (JsonNode item : input) {
            int frame = item.path(startField).intValue();
            if (frame < first || frame >= end) continue;
            ObjectNode local = item.deepCopy();
            local.put(startField, frame - first);
            if (startField.equals("startFrame")) {
                local.put("endFrame", item.path("endFrame").intValue() - first);
            }
            output.add(local);
        }
        return output;
    }

    private static byte[] encode(ObjectNode screenplay) throws IOException {
        try {
            return boundedEncode(screenplay, true);
        } catch (IOException tooLargePretty) {
            Throwable cause = tooLargePretty;
            while (cause != null && !(cause instanceof ScreenplaySizeException))
                cause = cause.getCause();
            if (cause == null) throw tooLargePretty;
            // Whitespace can push a valid dense canonical scene over the source cap.
            // Retry compact JSON once, with the same bound and exact authored tree.
            return boundedEncode(screenplay, false);
        }
    }

    private static byte[] boundedEncode(ObjectNode screenplay, boolean pretty) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8192);
        try (OutputStream bounded =
                new OutputStream() {
                    private int count;

                    @Override
                    public void write(int value) throws IOException {
                        reserve(1);
                        bytes.write(value);
                    }

                    @Override
                    public void write(byte[] source, int offset, int length) throws IOException {
                        reserve(length);
                        bytes.write(source, offset, length);
                    }

                    private void reserve(int length) throws IOException {
                        if (length > StageplayScriptCompiler.MAX_BYTES - count) {
                            throw new ScreenplaySizeException();
                        }
                        count += length;
                    }
                }) {
            var writer = pretty ? JSON.writerWithDefaultPrettyPrinter() : JSON.writer();
            writer.writeValue(bounded, screenplay);
        }
        return bytes.toByteArray();
    }

    private static final class ScreenplaySizeException extends IOException {
        private ScreenplaySizeException() {
            super("Decompiled screenplay exceeds 32 MiB");
        }
    }
}
