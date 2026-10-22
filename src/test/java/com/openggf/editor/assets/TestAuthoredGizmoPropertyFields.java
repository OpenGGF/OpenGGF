package com.openggf.editor.assets;

import com.openggf.editor.properties.AuthoredGizmoPropertyFields;
import com.openggf.game.MusicReference;
import com.openggf.level.objects.authored.AuthoredObjectProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestAuthoredGizmoPropertyFields {
    @Test void everyVisibleFieldReturnsTypedDataAndRetainsExactPropertiesWhenUnchanged() {
        for (var properties : values()) for (var field : AuthoredGizmoPropertyFields.fields(properties)) {
            Object value = field.value(properties);
            switch (field.kind()) {
                case BOOL -> assertInstanceOf(Boolean.class, value);
                case INT -> assertInstanceOf(Integer.class, value);
                case ENUM, TEXT -> assertInstanceOf(String.class, value);
            }
            assertEquals(properties, field.withValue(properties, value), field.key());
        }
    }

    @Test void fieldEditsReturnValidatedImmutablePropertiesAndKeepOtherFields() {
        var source = new AuthoredObjectProperties.Warp(32, 64, "door-a", true);
        var edited = AuthoredGizmoPropertyFields.withValue(source, "link", "door-b");
        assertEquals(new AuthoredObjectProperties.Warp(32, 64, "door-b", true), edited);
        assertEquals("door-a", source.link());
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmoPropertyFields.withValue(source, "width", 0));
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmoPropertyFields.withValue(source, "twoWay", "false"));
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmoPropertyFields.withValue(source, "link", "../door"));
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmoPropertyFields.withValue(source, "unknown", 0));
    }

    @Test void switchingMusicSourceChangesVisibleWidgetsAndEditsExactOwnedTrackKey() {
        var source = new AuthoredObjectProperties.MusicZone(32, 64, MusicReference.stock(7), false);
        var track = AuthoredGizmoPropertyFields.withValue(source, "musicSource", "TRACK");
        assertTrue(AuthoredGizmoPropertyFields.fields(track).stream().anyMatch(field -> field.key().equals("trackKey")));
        assertFalse(AuthoredGizmoPropertyFields.fields(track).stream().anyMatch(field -> field.key().equals("stockId")));
        track = AuthoredGizmoPropertyFields.withValue(track, "trackKey", "example:intro");
        assertEquals(MusicReference.namespaced("example", "intro"), ((AuthoredObjectProperties.MusicZone) track).music());
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmoPropertyFields.withValue(source, "musicSource", "CUSTOM"));
    }

    private static List<AuthoredObjectProperties> values() {
        return List.of(new AuthoredObjectProperties.CameraZone(32, 64, 0, 900, 0, 300,
                        AuthoredObjectProperties.ReleaseCondition.CROSS_Y, 200),
                new AuthoredObjectProperties.MusicZone(32, 64, MusicReference.stock(7), false),
                new AuthoredObjectProperties.MusicZone(32, 64, MusicReference.namespaced("example", "intro"), true),
                new AuthoredObjectProperties.Warp(32, 64, "door-a", true),
                new AuthoredObjectProperties.StageplayTrigger(32, 64, "intro.stageplay",
                        AuthoredObjectProperties.Trigger.BUTTON, true, false, true));
    }
}
