package openggf.timeattack;

import com.openggf.mods.scene.SceneKeys;
import openggf.timeattack.ui.FakeViewInput;
import openggf.timeattack.ui.FullTextReaderProbe;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestRacingFullTextAccess {
    @Test
    void settingsAndProfileReadEveryAcceptedNameAndAddressWithoutSaving() {
        TimeAttackSettings settings = new TimeAttackSettings();
        settings.setDisplayName("日本語".repeat(8));
        String address = "https://example.test/" + "long-path/".repeat(13) + "ADDRESS_TAIL";
        settings.setMasterSelection(new MasterSelection.Custom(address));
        AtomicInteger changes = new AtomicInteger();
        FakeViewInput input = new FakeViewInput();
        SettingsView view = new SettingsView(settings, changes::incrementAndGet, cue -> { }, "s2", "tails");
        input.tapKey(view::update, SceneKeys.F1);
        String read = FullTextReaderProbe.readAll(input, view::update, view::draw, view::detailsVisibleLines, view::detailsFirstLine);
        assertTrue(read.contains(settings.customMasterText()));
        assertTrue(read.contains("ADDRESS_TAIL"));
        assertTrue(read.contains(settings.displayName()));
        input.tapEnter(view::update);
        assertEquals(0, changes.get());
        assertFalse(view.consumeCloseRequested());

        ProfileView profile = new ProfileView(settings, "s2", "tails", changes::incrementAndGet, cue -> { });
        input.tapKey(profile::update, SceneKeys.F1);
        read = FullTextReaderProbe.readAll(input, profile::update, profile::draw, profile::detailsVisibleLines, profile::detailsFirstLine);
        assertTrue(read.contains(settings.displayName()));
        input.tapEnter(profile::update);
        assertEquals(0, changes.get());
        assertFalse(profile.consumeCloseRequested());
    }

    @Test
    void poolReaderListsAllTrackNamesWithoutChangingThePool() {
        TimeAttackSettings settings = new TimeAttackSettings();
        AtomicInteger changes = new AtomicInteger();
        FakeViewInput input = new FakeViewInput();
        VotePoolEditorView view = new VotePoolEditorView(settings, "s2", changes::incrementAndGet, cue -> { });
        input.tapEnter(view::update); // DEFAULT copies to an unsaved draft.
        input.tapKey(view::update, SceneKeys.F1);
        String read = FullTextReaderProbe.readAll(input, view::update, view::draw, view::detailsVisibleLines, view::detailsFirstLine);
        for (var track : TimeAttackTrackCatalog.eligibleFor("s2")) assertTrue(read.contains(openggf.timeattack.ui.MenuPage.words(track.label())), track.label());
        input.tapEnter(view::update);
        assertEquals(0, changes.get());
        assertTrue(settings.votePools("s2").isEmpty());
        view.draw(new RecordingSceneCanvas(320));
    }
}
