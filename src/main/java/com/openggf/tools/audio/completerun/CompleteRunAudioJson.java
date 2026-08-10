package com.openggf.tools.audio.completerun;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Baseline;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ChipEvent;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Decision;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.DriverService;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Frame;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NormalizedState;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Record;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Request;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.RoleState;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.StateField;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Terminal;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.Lifecycle;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.PsgWrite;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.YmWrite;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Canonical, strict JSONL codec used only by the complete-run capture store. */
final class CompleteRunAudioJson {
    private static final JsonFactory FACTORY = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final ObjectMapper MAPPER = new ObjectMapper(FACTORY);

    private CompleteRunAudioJson() { }

    static String writeRecord(Record record) throws IOException {
        StringWriter out = new StringWriter();
        try (JsonGenerator json = FACTORY.createGenerator(out)) {
            json.writeStartObject();
            String type;
            if (record instanceof Baseline) type = "baseline";
            else if (record instanceof Frame) type = "frame";
            else if (record instanceof Lifecycle) type = "lifecycle";
            else if (record instanceof Terminal) type = "terminal";
            else {
                throw new IllegalArgumentException("unsupported complete-run record type: " + record.getClass().getName());
            }
            json.writeStringField("type", type);
            json.writeFieldName("value");
            MAPPER.writeValue(json, record);
            json.writeEndObject();
        }
        return out.toString();
    }

    static Record readRecord(String line) {
        try (JsonParser parser = FACTORY.createParser(line)) {
            JsonNode root = MAPPER.readTree(parser);
            if (parser.nextToken() != null) throw invalid("trailing JSON after record");
            object(root, "record");
            String type = text(root, "type", "record");
            exact(root, Set.of("type", "value"), "record");
            return switch (type) {
                case "baseline" -> MAPPER.treeToValue(root.required("value"), Baseline.class);
                case "frame" -> frame(root.required("value"));
                case "lifecycle" -> MAPPER.treeToValue(root.required("value"), Lifecycle.class);
                case "terminal" -> MAPPER.treeToValue(root.required("value"), Terminal.class);
                default -> throw invalid("unknown record type: " + type);
            };
        } catch (IOException failure) { throw invalid("invalid complete-run record JSON", failure); }
    }

    private static Frame frame(JsonNode value) throws IOException {
        object(value, "frame value"); exact(value, Set.of("absoluteFrame", "segment", "lag", "requests", "services"), "frame value");
        List<Request> requests = new ArrayList<>();
        for (JsonNode request : array(value, "requests", "frame")) requests.add(MAPPER.treeToValue(request, Request.class));
        List<DriverService> services = new ArrayList<>();
        for (JsonNode service : array(value, "services", "frame")) services.add(service(service));
        JsonNode segment = value.get("segment");
        if (segment == null || !(segment.isNull() || segment.isTextual())) throw invalid("frame segment must be string or null");
        if (!value.path("lag").isBoolean()) throw invalid("frame lag must be boolean");
        return new Frame(integer(value, "absoluteFrame", "frame"), segment.isNull() ? null : segment.textValue(),
                value.path("lag").booleanValue(), requests, services);
    }

    private static DriverService service(JsonNode value) throws IOException {
        object(value, "service value"); exact(value, Set.of("ordinal", "kind", "decisions", "state", "chipEvents"), "service value");
        List<Decision> decisions = new ArrayList<>();
        for (JsonNode decision : array(value, "decisions", "service")) decisions.add(MAPPER.treeToValue(decision, Decision.class));
        List<ChipEvent> chips = new ArrayList<>();
        for (JsonNode chip : array(value, "chipEvents", "service")) chips.add(chip.has("port") ? MAPPER.treeToValue(chip, YmWrite.class) : MAPPER.treeToValue(chip, PsgWrite.class));
        return new DriverService(number(value, "ordinal", "service"), text(value, "kind", "service"), decisions,
                MAPPER.treeToValue(value.required("state"), NormalizedState.class), chips);
    }

    private static void writeState(JsonGenerator json, NormalizedState state) throws IOException {
        json.writeObjectFieldStart("state");
        json.writeArrayFieldStart("fields");
        for (StateField field : state.fields()) { json.writeStartObject(); json.writeStringField("name", field.name()); json.writeObjectField("value", field.value()); json.writeEndObject(); }
        json.writeEndArray();
        json.writeArrayFieldStart("roles");
        for (RoleState role : state.roles()) { json.writeStartObject(); json.writeStringField("role", role.role().name()); json.writeBooleanField("active", role.active()); json.writeArrayFieldStart("fields"); for (StateField field : role.fields()) { json.writeStartObject(); json.writeStringField("name", field.name()); json.writeObjectField("value", field.value()); json.writeEndObject(); } json.writeEndArray(); json.writeEndObject(); }
        json.writeEndArray(); json.writeEndObject();
    }

    private static NormalizedState state(JsonNode node) {
        object(node, "state"); exact(node, Set.of("fields", "roles"), "state");
        return new NormalizedState(fields(node.required("fields"), "state fields"), roles(node.required("roles")));
    }
    private static List<RoleState> roles(JsonNode node) {
        if (!node.isArray()) throw invalid("state roles must be array"); List<RoleState> result = new ArrayList<>();
        for (JsonNode role : node) { object(role, "role state"); exact(role, Set.of("role", "active", "fields"), "role state"); if (!role.required("active").isBoolean()) throw invalid("role active must be boolean"); try { result.add(new RoleState(HardwareRole.valueOf(text(role, "role", "role state")), role.required("active").booleanValue(), fields(role.required("fields"), "role fields"))); } catch (IllegalArgumentException bad) { throw invalid("unknown hardware role", bad); } }
        return result;
    }
    private static List<StateField> fields(JsonNode node, String label) {
        if (!node.isArray()) throw invalid(label + " must be array"); List<StateField> result = new ArrayList<>();
        for (JsonNode field : node) { object(field, label); exact(field, Set.of("name", "value"), label); result.add(new StateField(text(field, "name", label), MAPPER.convertValue(field.required("value"), Object.class))); }
        return result;
    }
    private static Iterable<JsonNode> array(JsonNode node, String name, String label) { JsonNode value=node.get(name); if(value==null||!value.isArray()) throw invalid(label+" "+name+" must be array"); return value; }
    private static void exact(JsonNode node, Set<String> expected, String label) { Set<String> actual = new LinkedHashSet<>(); node.fieldNames().forEachRemaining(actual::add); if (!actual.equals(expected)) throw invalid(label + " fields must be exactly " + expected); }
    private static void object(JsonNode node, String label) { if (node == null || !node.isObject()) throw invalid(label + " must be object"); }
    private static String text(JsonNode node, String field, String label) { JsonNode value = node.get(field); if (value == null || !value.isTextual()) throw invalid(label + " " + field + " must be string"); return value.textValue(); }
    private static int integer(JsonNode node, String field, String label) { JsonNode value = node.get(field); if (value == null || !value.canConvertToInt() || !value.isIntegralNumber()) throw invalid(label + " " + field + " must be integer"); return value.intValue(); }
    private static long number(JsonNode node, String field, String label) { JsonNode value = node.get(field); if (value == null || !value.isIntegralNumber()) throw invalid(label + " " + field + " must be integer"); return value.longValue(); }
    static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    static IllegalArgumentException invalid(String message, Throwable cause) { return new IllegalArgumentException(message, cause); }
}
