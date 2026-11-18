package com.openggf.editor.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sparse edits to an editor state. Unchanged values are read from the native stage. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record EditorProjectEdits(JsonNode value, Map<String, EditorProjectEdits> changes, Integer length) {
    private static final EditorProjectEdits REMOVE = new EditorProjectEdits(null, null, null);

    static EditorProjectEdits between(JsonNode source, JsonNode edited) throws IOException {
        if (source.equals(edited)) return null;
        if (source instanceof BinaryNode before && edited instanceof BinaryNode after) {
            byte[] original = before.binaryValue();
            byte[] replacement = after.binaryValue();
            var changes = new LinkedHashMap<String, EditorProjectEdits>();
            for (int i = 0; i < replacement.length; i++) {
                if (i >= original.length || original[i] != replacement[i])
                    changes.put(Integer.toString(i), replace(IntNode.valueOf(Byte.toUnsignedInt(replacement[i]))));
            }
            return new EditorProjectEdits(null, changes, replacement.length == original.length ? null : replacement.length);
        }
        if (source instanceof ArrayNode before && edited instanceof ArrayNode after) {
            var changes = new LinkedHashMap<String, EditorProjectEdits>();
            for (int i = 0; i < after.size(); i++)
                add(changes, Integer.toString(i), i < before.size() ? between(before.get(i), after.get(i)) : replace(after.get(i)));
            return new EditorProjectEdits(null, changes, before.size() == after.size() ? null : after.size());
        }
        if (source instanceof ObjectNode before && edited instanceof ObjectNode after) {
            var changes = new LinkedHashMap<String, EditorProjectEdits>();
            for (var field : before.properties())
                add(changes, field.getKey(), after.has(field.getKey())
                        ? between(field.getValue(), after.get(field.getKey())) : REMOVE);
            for (var field : after.properties())
                if (!before.has(field.getKey())) changes.put(field.getKey(), replace(field.getValue()));
            return new EditorProjectEdits(null, changes, null);
        }
        return replace(edited);
    }

    private static EditorProjectEdits replace(JsonNode value) {
        // Null replacements are tagged as a value so field deletion has a distinct encoding.
        return new EditorProjectEdits(value, null, null);
    }

    private static void add(Map<String, EditorProjectEdits> changes, String key, EditorProjectEdits change) {
        if (change != null) changes.put(key, change);
    }

    JsonNode apply(JsonNode source) throws IOException {
        if (value != null) {
            if (changes != null || length != null) throw invalid();
            return value;
        }
        if (changes == null) throw invalid();
        if (source instanceof BinaryNode before) {
            byte[] original = before.binaryValue();
            byte[] result = Arrays.copyOf(original, editedLength(original.length));
            for (var entry : changes.entrySet()) {
                int position = position(entry.getKey(), result.length);
                var replacement = edit(entry).apply(NullNode.getInstance());
                if (!replacement.isIntegralNumber() || !replacement.canConvertToInt()
                        || replacement.intValue() < 0 || replacement.intValue() > 255) throw invalid();
                result[position] = (byte) replacement.intValue();
            }
            return BinaryNode.valueOf(result);
        }
        if (source instanceof ArrayNode before) {
            int size = editedLength(before.size());
            ArrayNode result = before.arrayNode();
            for (int i = 0; i < size; i++) result.add(i < before.size() ? before.get(i) : NullNode.getInstance());
            for (var entry : changes.entrySet()) {
                int position = position(entry.getKey(), size);
                result.set(position, edit(entry).apply(result.get(position)));
            }
            return result;
        }
        if (source instanceof ObjectNode before && length == null) {
            ObjectNode result = before.objectNode();
            result.setAll(before);
            for (var entry : changes.entrySet()) {
                var change = edit(entry);
                var key = entry.getKey();
                if (change.value() == null && change.changes() == null && change.length() == null) {
                    if (result.remove(key) == null) throw invalid();
                } else {
                    result.set(key, change.apply(result.path(key)));
                }
            }
            return result;
        }
        throw invalid();
    }

    private int editedLength(int original) throws IOException {
        if (length == null) return original;
        if (length < 0 || length > (long) original + changes.size()) throw invalid();
        return length;
    }

    private static EditorProjectEdits edit(Map.Entry<String, EditorProjectEdits> entry) throws IOException {
        if (entry.getValue() == null) throw invalid();
        return entry.getValue();
    }

    private static int position(String text, int length) throws IOException {
        final int index;
        try { index = Integer.parseInt(text); }
        catch (NumberFormatException invalidIndex) { throw invalid(); }
        if (index < 0 || index >= length || !Integer.toString(index).equals(text)) throw invalid();
        return index;
    }

    /** Stable placement IDs let a deletion or reorder retain the other source records by reference. */
    static void indexPlacements(ObjectNode level, String field, String idField) {
        ArrayNode placements = (ArrayNode) level.get(field);
        var indexed = level.objectNode();
        var order = indexed.putArray("order");
        var byId = indexed.putObject("byId");
        for (JsonNode placement : placements) {
            int id = placement.get(idField).intValue();
            order.add(id);
            byId.set(Integer.toString(id), placement);
        }
        level.set(field, indexed);
    }

    static void restorePlacements(ObjectNode level, String field, String idField) throws IOException {
        JsonNode indexed = level.get(field);
        if (indexed == null || !indexed.isObject() || indexed.size() != 2) throw invalid();
        JsonNode order = indexed.path("order");
        JsonNode byId = indexed.path("byId");
        if (!order.isArray() || !byId.isObject() || order.size() != byId.size()
                || order.size() > EditorProjectState.MAX_SPAWNS) throw invalid();
        var seen = new HashSet<Integer>();
        var placements = level.arrayNode();
        for (var id : order) {
            if (!id.isIntegralNumber() || !id.canConvertToInt() || !seen.add(id.intValue())) throw invalid();
            JsonNode placement = byId.path(id.asText());
            if (!placement.isObject() || !id.equals(placement.get(idField))) throw invalid();
            placements.add(placement);
        }
        level.set(field, placements);
    }

    private static IOException invalid() {
        return new IOException("Invalid native project edits");
    }
}
