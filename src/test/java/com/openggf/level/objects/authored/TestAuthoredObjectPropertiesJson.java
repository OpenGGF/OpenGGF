package com.openggf.level.objects.authored;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.MusicReference;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestAuthoredObjectPropertiesJson {
    private final ObjectMapper json = new ObjectMapper();

    @Test void everyKindRoundTripsExactTypedDataWithoutAFormatVersionBranch() throws Exception {
        for (var properties : values()) {
            var encoded = AuthoredObjectPropertiesJson.toJson(properties);
            assertFalse(encoded.has("formatVersion"));
            var decoded = AuthoredObjectPropertiesJson.parse(json.readTree(json.writeValueAsBytes(encoded)));
            assertEquals(properties, decoded);
            assertEquals(encoded, AuthoredObjectPropertiesJson.toJson(decoded));
        }
        assertNull(AuthoredObjectPropertiesJson.parse(null));
        assertNull(AuthoredObjectPropertiesJson.parse(json.getNodeFactory().nullNode()));
    }

    @Test void rejectsUnknownFieldsUnknownKindsAndAbsentRequiredValues() {
        for (var properties : values()) {
            var unknown = AuthoredObjectPropertiesJson.toJson(properties); unknown.put("surprise", 1);
            assertTrue(assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(unknown)).getMessage().contains("Unknown"));
            var missing = AuthoredObjectPropertiesJson.toJson(properties); missing.remove("height");
            assertTrue(assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(missing)).getMessage().contains("Missing"));
            var nullValue = AuthoredObjectPropertiesJson.toJson(properties); nullValue.putNull("width");
            assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(nullValue));
        }
        var unknownKind = json.createObjectNode().put("kind", "custom");
        assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(unknownKind));
    }

    @Test void rejectsNumericBooleanEnumAndTextCoercionAndOutOfRangeData() {
        var warp = new AuthoredObjectProperties.Warp(32, 64, "door", true);
        for (var invalid : List.of(json.getNodeFactory().textNode("32"), json.getNodeFactory().numberNode(32.0),
                json.getNodeFactory().numberNode(Long.MAX_VALUE), json.getNodeFactory().booleanNode(true))) {
            var value = AuthoredObjectPropertiesJson.toJson(warp); value.set("width", invalid);
            assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(value));
        }
        var bool = AuthoredObjectPropertiesJson.toJson(warp); bool.put("twoWay", "true");
        assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(bool));
        var link = AuthoredObjectPropertiesJson.toJson(warp); link.put("link", "../door");
        assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(link));
        var outside = AuthoredObjectPropertiesJson.toJson(warp); outside.put("height", 4097);
        assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(outside));
        var trigger = AuthoredObjectPropertiesJson.toJson(values().getLast()); trigger.put("trigger", "proximity");
        assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(trigger));
        trigger.put("trigger", "PROXIMITY"); trigger.put("resource", "../../intro.stageplay");
        assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(trigger));
    }

    @Test void musicShapeIsExclusiveAndNamespacedKeysAreValidated() {
        var properties = new AuthoredObjectProperties.MusicZone(32, 64, MusicReference.stock(129), false);
        var both = AuthoredObjectPropertiesJson.toJson(properties); ((com.fasterxml.jackson.databind.node.ObjectNode) both.get("music")).put("trackKey", "example:music");
        assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(both));
        var malformed = AuthoredObjectPropertiesJson.toJson(properties); malformed.putObject("music").put("trackKey", "unowned");
        assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(malformed));
        var missing = AuthoredObjectPropertiesJson.toJson(properties); missing.putObject("music");
        assertThrows(IOException.class, () -> AuthoredObjectPropertiesJson.parse(missing));
    }

    private static List<AuthoredObjectProperties> values() {
        return List.of(new AuthoredObjectProperties.CameraZone(512, 256, 100, 900, 0, 400,
                        AuthoredObjectProperties.ReleaseCondition.CROSS_X, 600),
                new AuthoredObjectProperties.MusicZone(32, 64, MusicReference.stock(129), true),
                new AuthoredObjectProperties.MusicZone(320, 224, MusicReference.namespaced("example", "boss"), false),
                new AuthoredObjectProperties.Warp(32, 64, "door-a", true),
                new AuthoredObjectProperties.StageplayTrigger(64, 64, "scenes/intro.stageplay",
                        AuthoredObjectProperties.Trigger.PROXIMITY, true, true, true));
    }
}
