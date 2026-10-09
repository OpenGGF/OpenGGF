package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneKeys;
import openggf.racing.client.DirectJoinAddress;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The keyboard text field behind LAN invites, chat, the display name and the master URL. */
class TestTextEntry {
    @Test
    void acceptsAlnumAndAllowedExtras() {
        TextEntry field = new TextEntry("T", "", 64, ".:-");
        for (char c : "192.168.1.5:27888".toCharArray()) {
            field.feedChar(c);
        }
        assertEquals("192.168.1.5:27888", field.text());
    }

    @Test
    void rejectsDisallowedCharsAndEnforcesMaxLength() {
        TextEntry field = new TextEntry("T", "", 3, "");
        for (char c : "a!bcd".toCharArray()) {
            field.feedChar(c);
        }
        assertEquals("abc", field.text());
    }

    @Test
    void backspaceAndSetTextFilterAndClamp() {
        TextEntry field = new TextEntry("T", "", 10, ".");
        field.setText("10.0.0.1");
        field.backspace();
        assertEquals("10.0.0.", field.text());
        field.setText("this-is-way-too-long!!");
        assertEquals("thisiswayt", field.text());
    }

    @Test
    void typedKeysFollowShiftAndTheUsLayout() {
        TextEntry field = new TextEntry("T", "", 64, " .:/#-_[]!?'+,;");
        FakeViewInput input = new FakeViewInput();
        input.type(field::update, "Ab1 _-.:/?#[]!+',;");
        assertEquals("Ab1 _-.:/?#[]!+',;", field.text());
        input.tapKey(field::update, SceneKeys.BACKSPACE);
        assertEquals("Ab1 _-.:/?#[]!+',", field.text());
    }

    @Test
    void fullPinnedIpv6InviteTypesIntoTheInviteField() {
        String invite = "[2001:db8::1]:27888#" + DirectJoinAddress.shareCode("ab".repeat(32), "cd".repeat(32));
        assertTrue(invite.length() > 64);
        TextEntry field = new TextEntry("LAN INVITE", "", 192, ".:/#-_[]");
        new FakeViewInput().type(field::update, invite);
        assertEquals(invite, field.text());
        DirectJoinAddress parsed = DirectJoinAddress.parse(field.text(), 27888);
        assertEquals("ab".repeat(32), parsed.certificateSha256());
        assertEquals("cd".repeat(32), parsed.hostFingerprint());
    }

    @Test
    void onlyKeysConfirmOrCancelSoMappedButtonsKeepTyping() {
        TextEntry field = new TextEntry("T", "", 64, " ");
        FakeViewInput input = new FakeViewInput();
        List<TextEntry.Result> results = new ArrayList<>();
        // Space is pad A and Backspace is Start by default: the menu reports them as accept,
        // but the field types and deletes with them instead.
        input.clear();
        input.accept = true;
        input.pressed.add(SceneKeys.SPACE);
        results.add(field.update(input));
        assertEquals(" ", field.text());
        input.clear();
        input.back = true;
        results.add(field.update(input));
        assertEquals(List.of(TextEntry.Result.NONE, TextEntry.Result.NONE), results);
        input.clear();
        input.pressed.add(SceneKeys.ENTER);
        assertEquals(TextEntry.Result.ACCEPTED, field.update(input));
        input.clear();
        input.pressed.add(SceneKeys.KP_ENTER);
        assertEquals(TextEntry.Result.ACCEPTED, field.update(input));
        input.clear();
        input.pressed.add(SceneKeys.ESCAPE);
        assertEquals(TextEntry.Result.CANCELLED, field.update(input));
    }

    @Test
    void longValuesWrapSoEveryCharacterIsDrawn() {
        String invite = "HOST_IP:27888#" + "Ab-_".repeat(22);
        TextEntry field = new TextEntry("LAN INVITE", invite, 192, ".:/#-_[]");
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        field.draw(canvas);
        String drawn = String.join("", canvas.lines.subList(1, canvas.lines.size() - 2));
        assertEquals(invite + "_", drawn);
    }
}
