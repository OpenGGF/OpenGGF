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
    void rightStickSelectsAllEightPhrasesClockwiseFromNorthWithoutScrollingOrRepeating() {
        QuickChatPhrase[] clockwise = {
                QuickChatPhrase.BRUTAL, QuickChatPhrase.TOO_SLOW, QuickChatPhrase.KNOW_THE_WAY,
                QuickChatPhrase.THIS_IS_THE_WAY, QuickChatPhrase.THATS_NO_GOOD,
                QuickChatPhrase.THIS_IS_ZONE, QuickChatPhrase.GOOD_LUCK, QuickChatPhrase.WELL_PLAYED
        };
        List<QuickChatPhrase> sent = new ArrayList<>();
        for (int sector = 0; sector < clockwise.length; sector++) {
            QuickChatChooser chooser = new QuickChatChooser();
            chooser.updateController(pads(pad(2, false, 0, 0, -1)), true, sent::add);
            chooser.updateController(pads(pad(2, true, 0, 0, -1)), true, sent::add);
            assertTrue(chooser.isRadial());
            var aimed = pads(aim(2, false, sector * 45, 1, -1));
            for (int tick = 0; tick < 40; tick++) chooser.updateController(aimed, true, sent::add);
            chooser.updateController(pads(aim(2, false, sector * 45, 1, 1)), true, sent::add);
            assertEquals(List.of(clockwise[sector]), sent.subList(sector, sent.size()));
            assertFalse(chooser.isOpen());
            assertFalse(chooser.isRadial());
        }
        assertEquals(List.of(clockwise), sent);
    }

    @Test
    void radialCenterAndDeadZoneNeverSendAPhrase() {
        for (float[] stick : new float[][] {{0, 0}, {0.54f, 0}, {0, -0.55f}, {0.38f, 0.38f}}) {
            QuickChatChooser chooser = new QuickChatChooser();
            List<QuickChatPhrase> sent = new ArrayList<>();
            chooser.openController(pads(pad(2, false, 0, 0, -1)));
            chooser.updateController(pads(pad(2, false, stick[0], stick[1], 1)), true, sent::add);
            assertTrue(sent.isEmpty());
            assertTrue(chooser.isRadial());
        }
    }

    @Test
    void radialActivationUsesMagnitudeAndRetainsAimUntilTheReleaseThreshold() {
        QuickChatChooser chooser = new QuickChatChooser();
        List<QuickChatPhrase> sent = new ArrayList<>();
        chooser.openController(pads(pad(2, false, 0, 0, -1)));
        chooser.updateController(pads(pad(2, false, 0.4f, -0.4f, -1)), true, sent::add);
        chooser.updateController(pads(aim(2, false, 45, 0.31, 1)), true, sent::add);
        assertEquals(List.of(QuickChatPhrase.TOO_SLOW), sent);

        chooser.openController(pads(aim(2, false, 90, 1, -1)));
        chooser.updateController(pads(aim(2, false, 90, 0.29, -1)), true, sent::add);
        chooser.updateController(pads(aim(2, false, 90, 0.54, 1)), true, sent::add);
        assertEquals(1, sent.size(), "after centering, aim must cross the activation threshold again");
        chooser.updateController(pads(aim(2, false, 90, 0.56, 1)), true, sent::add);
        assertEquals(1, sent.size(), "a held trigger cannot send when aim becomes active");
        chooser.updateController(pads(aim(2, false, 90, 0.56, -1)), true, sent::add);
        chooser.updateController(pads(aim(2, false, 90, 0.56, 1)), true, sent::add);
        assertEquals(List.of(QuickChatPhrase.TOO_SLOW, QuickChatPhrase.KNOW_THE_WAY), sent);
    }

    @Test
    void angularHysteresisRejectsJitterOnBothSidesOfEveryBoundaryIncludingNorthWrap() {
        for (int sector = 0; sector < 8; sector++) {
            for (int direction : new int[] {-1, 1}) {
                QuickChatChooser chooser = new QuickChatChooser();
                List<QuickChatPhrase> sent = new ArrayList<>();
                chooser.openController(pads(aim(2, false, sector * 45, 1, -1)));
                for (double offset : new double[] {21, 24, 22, 27, 23}) {
                    chooser.updateController(pads(aim(2, false, sector * 45 + direction * offset, 1, -1)),
                            true, sent::add);
                }
                chooser.updateController(pads(aim(2, false, sector * 45 + direction * 27, 1, 1)),
                        true, sent::add);
                assertEquals(List.of(QuickChatPhrase.values()[sector]), sent);

                chooser.openController(pads(aim(2, false, sector * 45, 1, -1)));
                chooser.updateController(pads(aim(2, false, sector * 45 + direction * 30, 1, 1)),
                        true, sent::add);
                assertEquals(QuickChatPhrase.values()[Math.floorMod(sector + direction, 8)], sent.getLast());
            }
        }
    }

    @Test
    void triggerRequiresAFreshEdgeAfterOpeningAndAfterACenteredPress() {
        QuickChatChooser chooser = new QuickChatChooser();
        List<QuickChatPhrase> sent = new ArrayList<>();
        chooser.updateController(pads(pad(2, false, 0, 0, -1)), true, sent::add);
        chooser.updateController(pads(pad(2, true, 0, -1, 0.56f)), true, sent::add);
        assertTrue(chooser.isRadial());
        assertTrue(sent.isEmpty(), "opening R3 and RT together cannot send");
        for (float trigger : new float[] {0.56f, 0.54f, 0.31f, 0.54f}) {
            chooser.updateController(pads(pad(2, false, 0, -1, trigger)), true, sent::add);
            assertTrue(sent.isEmpty());
        }
        chooser.updateController(pads(pad(2, false, 0, -1, 0.30f)), true, sent::add);
        chooser.updateController(pads(pad(2, false, 0, -1, 0.55f)), true, sent::add);
        assertTrue(sent.isEmpty());
        chooser.updateController(pads(pad(2, false, 0, -1, 0.56f)), true, sent::add);
        assertEquals(List.of(QuickChatPhrase.BRUTAL), sent);

        chooser.openController(pads(pad(2, false, 0, 0, -1)));
        chooser.updateController(pads(pad(2, false, 0, 0, 1)), true, sent::add);
        chooser.updateController(pads(pad(2, false, 1, 0, 1)), true, sent::add);
        assertEquals(1, sent.size());
        chooser.updateController(pads(pad(2, false, 1, 0, -1)), true, sent::add);
        chooser.updateController(pads(pad(2, false, 1, 0, 1)), true, sent::add);
        assertEquals(List.of(QuickChatPhrase.BRUTAL, QuickChatPhrase.KNOW_THE_WAY), sent);
    }

    @Test
    void explicitControllerOpeningBaselinesHeldControlsAndPreservesTriggerHysteresis() {
        QuickChatChooser chooser = new QuickChatChooser();
        List<QuickChatPhrase> sent = new ArrayList<>();
        chooser.openController(pads(pad(2, true, -1, 0, 1)));
        chooser.updateController(pads(pad(2, true, -1, 0, 1)), true, sent::add);
        assertTrue(chooser.isRadial());
        assertTrue(sent.isEmpty());
        chooser.close();
        chooser.openController(pads(pad(2, true, -1, 0, 0.4f)));
        chooser.updateController(pads(pad(2, true, -1, 0, 0.56f)), true, sent::add);
        assertTrue(sent.isEmpty(), "reopening cannot reset an already-held trigger inside its release band");
        chooser.updateController(pads(pad(2, false, -1, 0, 0.29f)), true, sent::add);
        chooser.updateController(pads(pad(2, false, -1, 0, 0.56f)), true, sent::add);
        assertEquals(List.of(QuickChatPhrase.GOOD_LUCK), sent);
    }

    @Test
    void keyboardActivationStaysAListAndIgnoresTheStickAndTrigger() {
        QuickChatChooser chooser = new QuickChatChooser();
        List<QuickChatPhrase> sent = new ArrayList<>();
        chooser.updateController(pads(pad(2, false, 0, 0, -1)), true, sent::add);
        chooser.open();
        for (int tick = 0; tick < 40; tick++) {
            chooser.updateController(pads(pad(2, false, 0, 1, 1)), true, sent::add);
        }
        assertTrue(chooser.isOpen());
        assertFalse(chooser.isRadial());
        assertTrue(sent.isEmpty());
        assertEquals("QUICK CHAT", chooser.raceLayout(320, 224, "Zone", "Q").lines().getFirst().plain());
        FakeViewInput input = new FakeViewInput();
        input.tapEnter(value -> chooser.update(value, sent::add));
        assertEquals(List.of(QuickChatPhrase.BRUTAL), sent);
        chooser.open();
        chooser.updateController(pads(pad(2, false, 0, 0, -1)), true, sent::add);
        chooser.updateController(pads(pad(2, true, 0, 0, -1)), true, sent::add);
        assertFalse(chooser.isOpen(), "R3 may close the keyboard list");
    }

    @Test
    void firstPadOwnsAimTriggerAndClickUntilItDisconnects() {
        QuickChatChooser chooser = new QuickChatChooser();
        List<QuickChatPhrase> sent = new ArrayList<>();
        chooser.updateController(pads(pad(2, false, 0, 0, -1)), true, sent::add);
        chooser.updateController(pads(pad(2, false, 0, 0, -1), pad(7, true, 1, 0, 1)), true, sent::add);
        assertFalse(chooser.isOpen(), "the second pad cannot operate the first pad's chooser");
        chooser.updateController(pads(pad(7, true, 1, 0, 1), pad(2, true, 0, 0, -1)), true, sent::add);
        assertTrue(chooser.isRadial(), "reordering connected devices does not change the owner");
        assertTrue(chooser.raceHint("Q").contains("RS CLICK"));
        chooser.updateController(pads(pad(2, false, 0, -1, -1), pad(7, false, 1, 0, -1)), true, sent::add);
        chooser.updateController(pads(pad(2, false, 0, -1, -1), pad(7, true, 1, 0, 1)), true, sent::add);
        assertTrue(sent.isEmpty());
        assertTrue(chooser.isRadial());
        chooser.updateController(pads(pad(2, false, 0, -1, 1), pad(7, true, 1, 0, 1)), true, sent::add);
        assertEquals(List.of(QuickChatPhrase.BRUTAL), sent);
    }

    @Test
    void connectionReplacementAndResetCannotTurnHeldControlsIntoNewPresses() {
        for (boolean reset : new boolean[] {false, true}) {
            QuickChatChooser chooser = new QuickChatChooser();
            List<QuickChatPhrase> sent = new ArrayList<>();
            chooser.updateController(pads(pad(2, true, 0, -1, 1)), true, sent::add);
            assertFalse(chooser.isOpen());
            chooser.updateController(pads(pad(2, false, 0, -1, -1)), true, sent::add);
            chooser.updateController(pads(pad(2, true, 0, -1, -1)), true, sent::add);
            assertTrue(chooser.isRadial());
            if (reset) chooser.resetRaceInput();
            chooser.updateController(pads(pad(7, true, 1, 0, 1)), true, sent::add);
            assertFalse(chooser.isOpen());
            chooser.updateController(pads(pad(7, true, 1, 0, 1)), true, sent::add);
            assertFalse(chooser.isOpen());
            assertTrue(sent.isEmpty());
            chooser.updateController(pads(pad(7, false, 1, 0, -1)), true, sent::add);
            chooser.updateController(pads(pad(7, true, 1, 0, 1)), true, sent::add);
            assertTrue(chooser.isRadial());
            assertTrue(sent.isEmpty());
            chooser.updateController(pads(), true, sent::add);
            assertFalse(chooser.isOpen());
            chooser.updateController(pads(pad(7, true, 1, 0, 1)), true, sent::add);
            assertFalse(chooser.isOpen());
            assertTrue(sent.isEmpty());
        }
    }

    @Test
    void unavailableChatAndRetryOrPauseResetRequireAReleasedClickBeforeReopening() {
        QuickChatChooser chooser = new QuickChatChooser();
        List<QuickChatPhrase> sent = new ArrayList<>();
        chooser.updateController(pads(pad(2, false, 0, 0, -1)), true, sent::add);
        for (boolean reset : new boolean[] {false, true}) {
            chooser.updateController(pads(pad(2, true, 0, -1, -1)), true, sent::add);
            assertTrue(chooser.isRadial());
            if (reset) chooser.resetRaceInput();
            else chooser.updateController(pads(pad(2, true, 0, -1, 1)), false, sent::add);
            chooser.updateController(pads(pad(2, true, 0, -1, 1)), true, sent::add);
            chooser.updateController(pads(pad(2, true, 0, -1, 1)), true, sent::add);
            assertFalse(chooser.isOpen());
            assertTrue(sent.isEmpty());
            chooser.updateController(pads(pad(2, false, 0, 0, -1)), true, sent::add);
        }
    }

    @Test
    void clickCancelsEvenWhenTheTriggerIsPressedOnTheSameTick() {
        QuickChatChooser chooser = new QuickChatChooser();
        List<QuickChatPhrase> sent = new ArrayList<>();
        chooser.openController(pads(pad(2, false, 0, -1, -1)));
        chooser.updateController(pads(pad(2, true, 0, -1, 1)), true, sent::add);
        assertFalse(chooser.isOpen());
        assertFalse(chooser.isRadial());
        assertTrue(sent.isEmpty());
    }

    @Test
    void lobbyRadialUsesPhysicalAimAndTriggerButIgnoresOrdinaryDirectionsAndConfirm() {
        QuickChatChooser chooser = new QuickChatChooser();
        List<QuickChatPhrase> sent = new ArrayList<>();
        FakeViewInput logical = new FakeViewInput();
        chooser.openController(pads(pad(2, false, 0, 0, -1)));
        logical.up = logical.down = logical.accept = true;
        logical.physical = pads(pad(2, false, -1, 0, -1));
        chooser.update(logical, sent::add);
        assertTrue(chooser.isRadial());
        assertTrue(sent.isEmpty());
        logical.physical = pads(pad(2, false, -1, 0, 1));
        chooser.update(logical, sent::add);
        assertEquals(List.of(QuickChatPhrase.GOOD_LUCK), sent);
    }

    @Test
    void lobbyBackCancelsBeforeATriggerEdgeAndDigitsCanExplicitlySendInRadialMode() {
        QuickChatChooser chooser = new QuickChatChooser();
        List<QuickChatPhrase> sent = new ArrayList<>();
        FakeViewInput logical = new FakeViewInput();
        chooser.openController(pads(pad(2, false, 0, -1, -1)));
        logical.back = true;
        logical.physical = pads(pad(2, false, 0, -1, 1));
        chooser.update(logical, sent::add);
        assertFalse(chooser.isOpen());
        assertTrue(sent.isEmpty());
        chooser.openController(pads(pad(2, false, 0, 0, -1)));
        logical.clear();
        logical.pressed.add(SceneKeys.DIGIT_8);
        chooser.update(logical, sent::add);
        assertEquals(List.of(QuickChatPhrase.WELL_PLAYED), sent);
        assertFalse(chooser.isOpen());
    }

    @Test
    void radialRendererKeepsEverySelectionAndLongZoneOnScreenAtSupportedWidths() {
        QuickChatChooser chooser = new QuickChatChooser();
        for (int width : new int[] {256, 320, 400, 640}) {
            for (int sector = -1; sector < 8; sector++) {
                chooser.openController(pads(aim(2, false, sector * 45, sector < 0 ? 0 : 1, -1)));
                RecordingSceneCanvas race = new RecordingSceneCanvas(width);
                RecordingSceneCanvas lobby = new RecordingSceneCanvas(width);
                String zone = "A very long zone name with punctuation, apostrophes and descenders!";
                chooser.drawRace(race, zone, "Q");
                chooser.draw(lobby, zone, "B/ESC");
                QuickChatPhrase selected = sector < 0 ? null : QuickChatPhrase.values()[sector];
                RecordingSceneCanvas expectedRace = new RecordingSceneCanvas(width);
                RecordingSceneCanvas expectedLobby = new RecordingSceneCanvas(width);
                new QuickChatRadialMenu().draw(expectedRace, selected, zone, false, "");
                new QuickChatRadialMenu().draw(expectedLobby, selected, zone, true, "B/ESC");
                assertDrawingEquals(expectedRace, race);
                assertDrawingEquals(expectedLobby, lobby);
                assertFalse(race.fills.isEmpty());
                assertFalse(lobby.fills.isEmpty());
                race.assertFillsOnScreen();
                lobby.assertFillsOnScreen();
            }
        }
    }

    private static PhysicalGamepad pad(int id, boolean click, float stickX, float stickY, float trigger) {
        boolean[] buttons = new boolean[15];
        buttons[PhysicalGamepad.BUTTON_RIGHT_THUMB] = click;
        return new PhysicalGamepad(id, "Xbox", buttons, new float[] {0, 0, stickX, stickY, -1, trigger});
    }

    private static PhysicalGamepad aim(int id, boolean click, double degrees, double magnitude, float trigger) {
        double radians = Math.toRadians(degrees);
        return pad(id, click, (float) (Math.sin(radians) * magnitude),
                (float) (-Math.cos(radians) * magnitude), trigger);
    }

    private static PhysicalInput pads(PhysicalGamepad... gamepads) {
        return new PhysicalInput(0, List.of(), List.of(gamepads), List.of(), 0);
    }

    private static void assertDrawingEquals(RecordingSceneCanvas expected, RecordingSceneCanvas actual) {
        assertEquals(expected.lines, actual.lines);
        assertEquals(expected.fills.size(), actual.fills.size());
        for (int i = 0; i < expected.fills.size(); i++) {
            assertArrayEquals(expected.fills.get(i), actual.fills.get(i), "draw operation " + i);
        }
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
