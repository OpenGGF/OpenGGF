package com.openggf.editor.properties;

import com.openggf.i18n.UiMessages;
import java.util.Map;

import com.openggf.game.MusicReference;
import com.openggf.level.objects.authored.AuthoredObjectProperties;
import java.util.ArrayList;
import java.util.List;

/** Typed field descriptions shared by desktop widgets and controller/text-entry HUD controls. */
public final class AuthoredGizmoPropertyFields {
    private AuthoredGizmoPropertyFields() { }
    public enum Kind { BOOL, INT, ENUM, TEXT }
    public record Field(String key, String label, Kind kind, int min, int max, List<String> choices) {
        public Field { choices = List.copyOf(choices); }
        public Object value(AuthoredObjectProperties properties) { return AuthoredGizmoPropertyFields.value(properties, key); }
        public AuthoredObjectProperties withValue(AuthoredObjectProperties properties, Object value) {
            if (kind == Kind.INT && (!(value instanceof Integer number) || number < min || number > max))
                throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.must_be_an_integer_in_1tjlang", Map.of("value0", String.valueOf(label), "value1", String.valueOf(min), "value2", String.valueOf(max))));
            if (kind == Kind.BOOL && !(value instanceof Boolean)) throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.must_be_boolean_1hdjznp", Map.of("value0", String.valueOf(label))));
            if ((kind == Kind.ENUM || kind == Kind.TEXT) && !(value instanceof String))
                throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.must_be_text_1a1ojlc", Map.of("value0", String.valueOf(label))));
            if (kind == Kind.ENUM && !choices.contains(value)) throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.unknown_q5f3wh", Map.of("value0", String.valueOf(label), "value1", String.valueOf(value))));
            if (kind == Kind.TEXT && ((String) value).length() > max) throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.is_too_long_12308x", Map.of("value0", String.valueOf(label))));
            return AuthoredGizmoPropertyFields.withValue(properties, key, value);
        }
    }

    public static List<Field> fields(AuthoredObjectProperties properties) {
        var fields = new ArrayList<Field>();
        fields.add(integer("width", UiMessages.text("editor.authored_gizmo_property_fields.region_width_mno3ay"), 1, 65535)); fields.add(integer("height", UiMessages.text("editor.authored_gizmo_property_fields.region_height_1jxxrqr"), 1, 4096));
        switch (properties) {
            case AuthoredObjectProperties.CameraZone p -> {
                fields.add(integer("minX", UiMessages.text("editor.authored_gizmo_property_fields.camera_minimum_x_1tjagmj"), 0, 65535)); fields.add(integer("maxX", UiMessages.text("editor.authored_gizmo_property_fields.camera_maximum_x_1grdkcd"), 0, 65535));
                fields.add(integer("minY", UiMessages.text("editor.authored_gizmo_property_fields.camera_minimum_y_1tjagmk"), 0, 4095)); fields.add(integer("maxY", UiMessages.text("editor.authored_gizmo_property_fields.camera_maximum_y_1grdkce"), 0, 4095));
                fields.add(enumeration("releaseCondition", UiMessages.text("editor.authored_gizmo_property_fields.release_condition_1hz9oo2"), "NONE", "CROSS_X", "CROSS_Y"));
                fields.add(integer("releaseAt", UiMessages.text("editor.authored_gizmo_property_fields.release_threshold_16nxnci"), 0, p.releaseCondition() == AuthoredObjectProperties.ReleaseCondition.CROSS_Y ? 4095 : 65535));
            }
            case AuthoredObjectProperties.MusicZone p -> {
                fields.add(enumeration("musicSource", UiMessages.text("editor.authored_gizmo_property_fields.music_source_ysyhsm"), "STOCK", "TRACK"));
                if (p.music() instanceof MusicReference.Stock) fields.add(integer("stockId", UiMessages.text("editor.authored_gizmo_property_fields.stock_music_id_3yrlcw"), 0, 65535));
                else fields.add(text("trackKey", UiMessages.text("editor.authored_gizmo_property_fields.mod_track_owner_name_1eavhwl"), 256));
                fields.add(bool("once", UiMessages.text("editor.authored_gizmo_property_fields.play_once_14k631")));
            }
            case AuthoredObjectProperties.Warp ignored -> {
                fields.add(text("link", UiMessages.text("editor.authored_gizmo_property_fields.endpoint_link_2hns79"), 64)); fields.add(bool("twoWay", UiMessages.text("editor.authored_gizmo_property_fields.two_way_transport_1u0iy53")));
            }
            case AuthoredObjectProperties.StageplayTrigger ignored -> {
                fields.add(text("resource", UiMessages.text("editor.authored_gizmo_property_fields.stageplay_resource_1v73dcs"), 512));
                fields.add(enumeration("trigger", UiMessages.text("editor.authored_gizmo_property_fields.activation_1uq58om"), "PROXIMITY", "BUTTON"));
                fields.add(bool("blocking", UiMessages.text("editor.authored_gizmo_property_fields.pause_gameplay_1p1m240"))); fields.add(bool("once", UiMessages.text("editor.authored_gizmo_property_fields.trigger_once_7yuww9"))); fields.add(bool("consume", UiMessages.text("editor.authored_gizmo_property_fields.consume_trigger_1cykrck")));
            }
        }
        return List.copyOf(fields);
    }

    public static Object value(AuthoredObjectProperties properties, String key) {
        if (key.equals("width")) return properties.width();
        if (key.equals("height")) return properties.height();
        return switch (properties) {
            case AuthoredObjectProperties.CameraZone p -> switch (key) {
                case "minX" -> p.minX(); case "maxX" -> p.maxX(); case "minY" -> p.minY(); case "maxY" -> p.maxY();
                case "releaseCondition" -> p.releaseCondition().name(); case "releaseAt" -> p.releaseAt(); default -> unknown(key);
            };
            case AuthoredObjectProperties.MusicZone p -> switch (key) {
                case "musicSource" -> (p.music() instanceof MusicReference.Stock ? "STOCK" : "TRACK");
                case "stockId" -> ((MusicReference.Stock) p.music()).musicId();
                case "trackKey" -> { var track = (MusicReference.Namespaced) p.music(); yield track.owner() + ":" + track.localName(); }
                case "once" -> p.once(); default -> unknown(key);
            };
            case AuthoredObjectProperties.Warp p -> switch (key) { case "link" -> p.link(); case "twoWay" -> p.twoWay(); default -> unknown(key); };
            case AuthoredObjectProperties.StageplayTrigger p -> switch (key) {
                case "resource" -> p.resource(); case "trigger" -> p.trigger().name(); case "blocking" -> p.blocking();
                case "once" -> p.once(); case "consume" -> p.consume(); default -> unknown(key);
            };
        };
    }

    /** Applies exactly one described field, returning a new validated property value. */
    public static AuthoredObjectProperties withValue(AuthoredObjectProperties properties, String key, Object value) {
        // Validate through the currently visible descriptor even for programmatic callers.
        Field field = fields(properties).stream().filter(candidate -> candidate.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.unknown_authored_field_b3ohqb", Map.of("value0", String.valueOf(key)))));
        if (field.kind() == Kind.INT && (!(value instanceof Integer n) || n < field.min() || n > field.max()))
            throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.is_outside_its_integer_range_sfvyxb", Map.of("value0", String.valueOf(field.label()))));
        if (field.kind() == Kind.BOOL && !(value instanceof Boolean)) throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.must_be_boolean_1hdjznp", Map.of("value0", String.valueOf(field.label()))));
        if ((field.kind() == Kind.TEXT || field.kind() == Kind.ENUM) && !(value instanceof String))
            throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.must_be_text_1a1ojlc", Map.of("value0", String.valueOf(field.label()))));
        if (field.kind() == Kind.ENUM && !field.choices().contains(value)) throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.unknown_enum_value_b8fci9", Map.of("value0", String.valueOf(value))));
        if (field.kind() == Kind.TEXT && ((String) value).length() > field.max()) throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.is_too_long_12308x", Map.of("value0", String.valueOf(field.label()))));
        int width = key.equals("width") ? (Integer) value : properties.width();
        int height = key.equals("height") ? (Integer) value : properties.height();
        return switch (properties) {
            case AuthoredObjectProperties.CameraZone p -> new AuthoredObjectProperties.CameraZone(width, height,
                    key.equals("minX") ? (Integer) value : p.minX(), key.equals("maxX") ? (Integer) value : p.maxX(),
                    key.equals("minY") ? (Integer) value : p.minY(), key.equals("maxY") ? (Integer) value : p.maxY(),
                    key.equals("releaseCondition") ? AuthoredObjectProperties.ReleaseCondition.valueOf((String) value) : p.releaseCondition(),
                    key.equals("releaseAt") ? (Integer) value : p.releaseAt());
            case AuthoredObjectProperties.MusicZone p -> new AuthoredObjectProperties.MusicZone(width, height,
                    editedMusic(p.music(), key, value), key.equals("once") ? (Boolean) value : p.once());
            case AuthoredObjectProperties.Warp p -> new AuthoredObjectProperties.Warp(width, height,
                    key.equals("link") ? (String) value : p.link(), key.equals("twoWay") ? (Boolean) value : p.twoWay());
            case AuthoredObjectProperties.StageplayTrigger p -> new AuthoredObjectProperties.StageplayTrigger(width, height,
                    key.equals("resource") ? (String) value : p.resource(),
                    key.equals("trigger") ? AuthoredObjectProperties.Trigger.valueOf((String) value) : p.trigger(),
                    key.equals("blocking") ? (Boolean) value : p.blocking(), key.equals("once") ? (Boolean) value : p.once(),
                    key.equals("consume") ? (Boolean) value : p.consume());
        };
    }

    private static MusicReference editedMusic(MusicReference music, String key, Object value) {
        if (key.equals("musicSource")) {
            boolean stock = music instanceof MusicReference.Stock;
            if (value.equals(stock ? "STOCK" : "TRACK")) return music;
            return value.equals("STOCK") ? MusicReference.stock(0x81) : MusicReference.namespaced("project", "track");
        }
        if (key.equals("stockId")) return MusicReference.stock((Integer) value);
        if (key.equals("trackKey")) {
            String name = (String) value; int separator = name.indexOf(':');
            if (separator < 1) throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.track_key_must_be_owner_localname_1bjl5gz"));
            return MusicReference.namespaced(name.substring(0, separator), name.substring(separator + 1));
        }
        return music;
    }
    private static Object unknown(String key) { throw new IllegalArgumentException(UiMessages.text("editor.authored_gizmo_property_fields.unknown_authored_field_b3ohqb", Map.of("value0", String.valueOf(key)))); }
    private static Field integer(String key, String label, int min, int max) { return new Field(key, label, Kind.INT, min, max, List.of()); }
    private static Field bool(String key, String label) { return new Field(key, label, Kind.BOOL, 0, 1, List.of()); }
    private static Field text(String key, String label, int max) { return new Field(key, label, Kind.TEXT, 0, max, List.of()); }
    private static Field enumeration(String key, String label, String... choices) { return new Field(key, label, Kind.ENUM, 0, choices.length - 1, List.of(choices)); }
}
