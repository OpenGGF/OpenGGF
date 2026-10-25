package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneKeys;
import com.openggf.net.client.DirectJoinAddress;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The text field behind LAN invites, chat, the display name and the master URL. */
class TestTextEntry {
    @Test
    void passwordInputPreservesExactUnicodeAndMasksItsValueAndLength() {
        TextEntry field = TextEntry.password("ROOM PASSWORD");
        String password = "  e\u0301\u3000Private\uD83D\uDE80  ";
        new FakeViewInput().commit(field::update, password);
        assertEquals(password, field.text());
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        field.draw(canvas);
        assertFalse(canvas.joined().contains("Private"));
        assertFalse(canvas.joined().contains(field.length() + "/"));
        assertTrue(canvas.joined().contains("********"));
        field.clear();
        assertEquals("", field.text());
        assertEquals(0, field.length());
    }

    @Test
    void maskedInviteInputNeverDrawsTheAddressOrPin() {
        TextEntry field = new TextEntry("LAN INVITE", "192.0.2.4:27888#secret-pin", 192, ".:/#-_").masked();
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        field.draw(canvas);
        assertFalse(canvas.joined().contains("192.0.2.4"));
        assertFalse(canvas.joined().contains("secret-pin"));
        assertTrue(canvas.joined().contains("Hidden"));
        canvas.assertFillsOnScreen();
    }

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
    void typedTextFillsTheFieldAndKeyCodesAloneTypeNothing() {
        TextEntry field = new TextEntry("T", "", 64, " .:/#-_[]!?'+,;");
        FakeViewInput input = new FakeViewInput();
        input.type(field::update, "Ab1 _-.:/?#[]!+',;");
        assertEquals("Ab1 _-.:/?#[]!+',;", field.text());
        input.tapKey(field::update, SceneKeys.BACKSPACE);
        assertEquals("Ab1 _-.:/?#[]!+',", field.text());
        // The layout decides what a key types: a key press with no character event adds nothing,
        // as while an input method is still composing.
        input.tapKey(field::update, SceneKeys.A);
        assertEquals("Ab1 _-.:/?#[]!+',", field.text());
    }

    @Test
    void asciiFieldsKeepTheirCharacterRules() {
        TextEntry port = new TextEntry("PORT", "", 5, "");
        new FakeViewInput().type(port::update, "２7é8:8 8");
        assertEquals("7888", port.text(), "addresses and ports stay ASCII");
    }

    @Test
    void nameFieldsTakeAnyScriptCountCodePointsAndDeleteWholeGraphemes() {
        TextEntry field = TextEntry.name("NAME", "");
        FakeViewInput input = new FakeViewInput();
        input.commit(field::update, "花子");          // an IME commits a word in one tick
        input.type(field::update, " Ле");
        input.type(field::update, "\uD83D\uDE80");   // a rocket: one code point, two UTF-16 units
        input.type(field::update, "e\u0301");        // e + combining acute: one grapheme
        assertEquals("花子 Ле\uD83D\uDE80e\u0301", field.text());
        assertEquals(8, field.length());
        input.tapKey(field::update, SceneKeys.BACKSPACE);
        assertEquals("花子 Ле\uD83D\uDE80", field.text(), "the accent goes with its letter");
        input.tapKey(field::update, SceneKeys.BACKSPACE);
        assertEquals("花子 Ле", field.text(), "a surrogate pair is never split");
        assertEquals(5, field.length());
        input.type(field::update, "\u202E\u200D\u0007\uE000");  // bidi override, ZWJ, bell, private use
        assertEquals("花子 Ле", field.text(), "invisible and disguising characters are refused");
        input.tapKey(field::update, SceneKeys.ENTER);
        assertEquals("花子 Ле", field.acceptedName());
    }

    @Test
    void nameFieldsStopAtTheDisplayNameLimitsAndRefuseBlankNames() {
        TextEntry field = TextEntry.name("NAME", "");
        FakeViewInput input = new FakeViewInput();
        input.commit(field::update, "x".repeat(30));
        assertEquals(com.openggf.net.identity.DisplayNames.MAX_CODE_POINTS, field.length());
        TextEntry wide = TextEntry.name("NAME", "");
        input.commit(wide::update, "\uD83D\uDE80".repeat(20)); // 4 UTF-8 bytes each
        assertEquals(com.openggf.net.identity.DisplayNames.MAX_UTF8_BYTES / 4, wide.length());

        TextEntry blank = TextEntry.name("NAME", "");
        input.type(blank::update, "  ");
        input.clear();
        input.pressed.add(SceneKeys.ENTER);
        assertEquals(TextEntry.Result.NONE, blank.update(input), "a blank name is not accepted");
        assertFalse(blank.error().isEmpty());
        input.type(blank::update, "　Ａｂ　 c");     // ideographic spaces from a Japanese IME
        input.clear();
        input.pressed.add(SceneKeys.ENTER);
        assertEquals(TextEntry.Result.ACCEPTED, blank.update(input));
        assertEquals(com.openggf.net.identity.DisplayNames.sanitize(blank.text()), blank.acceptedName());
        assertTrue(com.openggf.net.identity.DisplayNames.isCanonical(blank.acceptedName()));
    }

    @Test
    void chatFieldsStayWithinTheHostsUtf16Cut() {
        TextEntry chat = TextEntry.text("CHAT", "", 10);
        new FakeViewInput().commit(chat::update, "\uD83D\uDE80".repeat(8));
        assertEquals(5, chat.length(), "ten UTF-16 units hold five rockets");
        assertEquals(10, chat.text().length());
    }

    @Test
    void unicodeFieldsDrawWithTheNameFontInsideTheScreen() {
        TextEntry field = TextEntry.name("CHOOSE YOUR NAME", "Nguyễn 花子").withPrompt("Any script");
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        field.draw(canvas);
        canvas.assertFillsOnScreen();
        assertFalse(canvas.joined().contains("Nguy"), "names are not drawn with the ASCII scene font");
        assertTrue(canvas.fillsIn(0xFFFFFFFF).size() > 20, "the name is drawn as name-font pixel runs");
        TextEntry chat = TextEntry.text("ROOM CHAT", "Привет всем, ".repeat(10), 200);
        RecordingSceneCanvas wrapped = new RecordingSceneCanvas(320);
        chat.draw(wrapped);
        wrapped.assertFillsOnScreen();
        for (String line : TextEntry.wrapPixels(chat.text(), 296)) {
            assertTrue(com.openggf.mods.ui.NameFont.width(line) <= 296, line);
        }
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
        input.typed = " ";
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
