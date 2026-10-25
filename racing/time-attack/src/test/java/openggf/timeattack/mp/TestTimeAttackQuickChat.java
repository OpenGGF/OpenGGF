package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneKeys;
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
