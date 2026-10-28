package openggf.timeattack;

import com.openggf.net.protocol.ChatMode;
import openggf.timeattack.ui.FakeViewInput;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestTimeAttackCommunicationSettings {
    @Test
    void hostedModeDefaultsAndEveryChoiceRoundTripsWithoutStoringEnginePreferences() {
        assertEquals(ChatMode.FULL_CHAT, new TimeAttackSettings().chatMode());
        assertEquals(ChatMode.FULL_CHAT, TimeAttackSettings.parse("chatMode=unknown\n").chatMode());
        for (ChatMode mode : ChatMode.values()) {
            TimeAttackSettings settings = new TimeAttackSettings();
            settings.setChatMode(mode);
            assertEquals(mode, TimeAttackSettings.parse(settings.encode()).chatMode());
            assertFalse(settings.encode().contains("censor"));
            assertFalse(settings.encode().contains("ChatDisabled"));
        }
        assertThrows(NullPointerException.class, () -> new TimeAttackSettings().setChatMode(null));
    }

    @Test
    void roomModeIsAnImmediateSavedSettingAndGlobalFiltersHaveEngineGuidance() {
        TimeAttackSettings settings = new TimeAttackSettings();
        AtomicInteger changes = new AtomicInteger();
        SettingsView view = new SettingsView(settings, changes::incrementAndGet, cue -> { });
        FakeViewInput input = new FakeViewInput();
        while (view.focusedRow() != SettingsView.Row.CHAT_MODE) input.tapDown(view::update);
        input.tapRight(view::update);
        assertEquals(ChatMode.QUICK_CHAT, settings.chatMode());
        input.tapEnter(view::update);
        assertEquals(ChatMode.OFF, settings.chatMode());
        input.tapLeft(view::update);
        assertEquals(ChatMode.QUICK_CHAT, settings.chatMode());
        assertEquals(3, changes.get());
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        // A fresh view has no temporary save notice obscuring the engine guidance.
        new SettingsView(settings, () -> { }, cue -> { }).draw(canvas);
        assertTrue(canvas.shows("Engine Settings"), canvas.joined());
        // The value reads in sentence case, as the lobby stepper and room browser write it.
        assertTrue(canvas.shows("Quick chat"), canvas.joined());
        canvas.assertFillsOnScreen();
    }
}
