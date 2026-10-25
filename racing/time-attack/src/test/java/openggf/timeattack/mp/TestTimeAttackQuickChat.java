package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneKeys;
import com.openggf.control.PhysicalGamepad;
import com.openggf.control.PhysicalInput;
import com.openggf.net.chat.QuickChatContent;
import com.openggf.net.chat.EnglishQuickChat;
import com.openggf.net.protocol.QuickChatPhrase;
import openggf.timeattack.ui.FakeViewInput;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTimeAttackQuickChat {
    @Test
    void formatterUsesTheMessagesOwnGameAndZoneWithoutDependingOnTheCurrentRound() {
        assertEquals("This is Chemical Plant!", TimeAttackQuickChat.format(
                new QuickChatContent(QuickChatPhrase.THIS_IS_ZONE, "s2", 1, 0)));
        assertEquals("This is Lava Reef!", TimeAttackQuickChat.format(
                new QuickChatContent(QuickChatPhrase.THIS_IS_ZONE, "s3k", 22, 0)));
        assertEquals("This is the zone!", TimeAttackQuickChat.format(
                new QuickChatContent(QuickChatPhrase.THIS_IS_ZONE, "unknown", 0, 0)));
    }

    @Test
    void chooserAndReceivedHistoryShareTheEnglishCatalogue() {
        QuickChatChooser chooser = new QuickChatChooser();
        var rows = chooser.layout(400, 224, "Chemical Plant", "Q").lines();
        QuickChatPhrase[] phrases = QuickChatPhrase.values();
        for (int i = 0; i < phrases.length; i++) {
            String wording = EnglishQuickChat.text(phrases[i], "Chemical Plant");
            assertTrue(rows.get(i + 2).plain().contains(wording));
            assertEquals(wording, TimeAttackQuickChat.format(
                    new QuickChatContent(phrases[i], "s2", 1, 0)));
        }
    }

    @Test
    void chooserSupportsControllerNavigationNumbersAndCancelWithoutArbitraryText() {
        QuickChatChooser chooser = new QuickChatChooser();
        FakeViewInput input = new FakeViewInput();
        List<QuickChatPhrase> sent = new ArrayList<>();
        chooser.open();
        input.tapDown(value -> chooser.update(value, sent::add));
        input.tapEnter(value -> chooser.update(value, sent::add));
        assertEquals(List.of(QuickChatPhrase.TOO_SLOW), sent);
        assertFalse(chooser.isOpen());
        chooser.open();
        input.commit(value -> chooser.update(value, sent::add), "arbitrary text");
        assertEquals(1, sent.size());
        input.tapEscape(value -> chooser.update(value, sent::add));
        assertFalse(chooser.isOpen());
        chooser.open();
        input.tapKey(value -> chooser.update(value, sent::add), SceneKeys.DIGIT_8);
        assertEquals(List.of(QuickChatPhrase.TOO_SLOW, QuickChatPhrase.WELL_PLAYED), sent);
    }

    @Test
    void raceControllerUsesOnePadAndHysteresisAndExplainsItsControlsAtEveryWidth() {
        QuickChatChooser chooser = new QuickChatChooser();
        List<QuickChatPhrase> sent = new ArrayList<>();
        chooser.updateController(pads(pad(2, false, 0, -1)), true, sent::add);
        chooser.updateController(pads(pad(2, false, 0, -1), pad(7, true, 1, 1)), true, sent::add);
        assertFalse(chooser.isOpen(), "the second pad cannot operate the first pad's chooser");
        chooser.updateController(pads(pad(2, true, 0, -1)), true, sent::add);
        assertTrue(chooser.isOpen());
        assertTrue(chooser.raceHint("Q").contains("RS CLICK"));
        for (int width : new int[] {256, 320, 400, 640}) {
            var layout = chooser.raceLayout(width, 224, "Mushroom Hill", "Q");
            assertEquals("QUICK CHAT  RS CLICK CLOSE", layout.lines().getFirst().plain());
            assertEquals("RS UP/DN  RIGHT TRIGGER SEND", layout.lines().get(1).plain());
            assertTrue(layout.y() >= 0 && layout.y() + layout.height() <= 224);
            RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
            chooser.drawRace(canvas, "Mushroom Hill", "Q");
            canvas.assertFillsOnScreen();
        }
        chooser.updateController(pads(pad(2, false, 0.56f, -1)), true, sent::add);
        chooser.updateController(pads(pad(2, false, 0.54f, -1)), true, sent::add);
        chooser.updateController(pads(pad(2, false, 0.31f, -1)), true, sent::add);
        assertEquals(HudTextLayout.GOLD, chooser.raceLayout(320, 224, "Zone", "Q").lines().get(3).color());
        chooser.updateController(pads(pad(2, false, 0.29f, -1)), true, sent::add);
        chooser.updateController(pads(pad(2, false, 0.56f, 0.50f)), true, sent::add);
        assertTrue(sent.isEmpty());
        chooser.updateController(pads(pad(2, false, 0, 0.56f)), true, sent::add);
        assertEquals(List.of(QuickChatPhrase.KNOW_THE_WAY), sent);
        assertFalse(chooser.isOpen());
    }

    @Test
    void raceStickWrapsAndRepeatsAfterADelayAndClickCancelsWithoutSending() {
        QuickChatChooser chooser = new QuickChatChooser();
        List<QuickChatPhrase> sent = new ArrayList<>();
        chooser.updateController(pads(pad(2, false, 0, -1)), true, sent::add);
        chooser.updateController(pads(pad(2, true, 0, -1)), true, sent::add);
        var up = pads(pad(2, false, -1, -1));
        chooser.updateController(up, true, sent::add);
        for (int i = 0; i < 23; i++) chooser.updateController(up, true, sent::add);
        assertEquals(HudTextLayout.GOLD, chooser.raceLayout(320, 224, "Zone", "Q").lines().get(9).color());
        chooser.updateController(up, true, sent::add);
        assertEquals(HudTextLayout.GOLD, chooser.raceLayout(320, 224, "Zone", "Q").lines().get(8).color());
        chooser.updateController(pads(pad(2, true, 0, -1)), true, sent::add);
        assertFalse(chooser.isOpen());
        assertTrue(sent.isEmpty());
    }

    private static PhysicalGamepad pad(int id, boolean click, float stickY, float trigger) {
        boolean[] buttons = new boolean[15];
        buttons[PhysicalGamepad.BUTTON_RIGHT_THUMB] = click;
        return new PhysicalGamepad(id, "Xbox", buttons, new float[] {0, 0, 0, stickY, -1, trigger});
    }

    private static PhysicalInput pads(PhysicalGamepad... gamepads) {
        return new PhysicalInput(0, List.of(), List.of(gamepads), List.of(), 0);
    }

    @Test
    void catalogueFitsSupportedViewportWidthsAndKeepsShortcutLabelsVisible() {
        QuickChatChooser chooser = new QuickChatChooser();
        chooser.open();
        for (int width : new int[] {256, 320, 400, 640}) {
            HudTextLayout.Panel panel = chooser.layout(width, 224, "Mushroom Hill", "Q");
            assertEquals(10, panel.lines().size());
            assertEquals("QUICK CHAT", panel.lines().getFirst().plain());
            assertTrue(panel.lines().get(1).plain().contains("1-8 SEND  Q CLOSE"));
            assertEquals(HudTextLayout.GOLD, panel.lines().get(2).color());
            assertEquals(HudTextLayout.ACCENT, panel.lines().get(3).color());
            assertTrue(panel.x() >= 0 && panel.x() + panel.width() <= width);
            assertTrue(panel.y() >= 0 && panel.y() + panel.height() <= 224);
            RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
            chooser.draw(canvas, "Mushroom Hill", "Q");
            canvas.assertFillsOnScreen();
        }
    }
}
