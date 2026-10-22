package openggf.timeattack;

import openggf.timeattack.ui.FakeViewInput;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mod-owned Time Attack settings and their settings page. */
class TestTimeAttackSettings {
    @Test
    void defaultsMatchTheFormerEngineSettings() {
        TimeAttackSettings settings = TimeAttackSettings.load(new MemoryModStorage());
        assertEquals(27888, settings.hostPort());
        assertEquals("", settings.lastJoinAddress());
        assertEquals("", settings.displayName());
        assertEquals("", settings.masterUrl());
        assertFalse(settings.masterTrustInsecure());
        assertTrue(settings.minimap());
        assertEquals(27888, TimeAttackSettings.load(null).hostPort());
    }

    @Test
    void savedSettingsRoundTripThroughModStorageAsUtf8Text() {
        MemoryModStorage storage = new MemoryModStorage();
        TimeAttackSettings settings = new TimeAttackSettings();
        settings.setRetryKey(com.openggf.mods.scene.SceneKeys.F6);
        settings.setHostPort(30000);
        settings.setLastJoinAddress("[2001:db8::1]:27888#code");
        settings.setDisplayName("Knuckles é");
        settings.setMasterUrl("wss://master.example:8443/master");
        settings.setMasterTrustInsecure(true);
        settings.setMinimap(false);
        assertTrue(settings.save(storage));
        assertTrue(storage.read(TimeAttackSettings.FILE_NAME).orElseThrow().contains("hostPort=30000"));

        TimeAttackSettings loaded = TimeAttackSettings.load(storage);
        assertEquals(com.openggf.mods.scene.SceneKeys.F6, loaded.retryKey());
        assertEquals(30000, loaded.hostPort());
        assertEquals("[2001:db8::1]:27888#code", loaded.lastJoinAddress());
        assertEquals("Knuckles é", loaded.displayName());
        assertEquals("wss://master.example:8443/master", loaded.masterUrl());
        assertTrue(loaded.masterTrustInsecure());
        assertFalse(loaded.minimap());
    }

    @Test
    void malformedLinesAndValuesKeepTheirDefaults() {
        TimeAttackSettings settings = TimeAttackSettings.parse("""
                garbage
                hostPort=99999
                =x
                unknown=1
                masterTrustInsecure=yes
                minimap=maybe
                """);
        assertEquals(27888, settings.hostPort());
        assertFalse(settings.masterTrustInsecure());
        assertTrue(settings.minimap());
        assertThrows(IllegalArgumentException.class, () -> settings.setHostPort(0));
    }

    @Test
    void valuesStayOnOneLineAndWithinTheirLimits() {
        TimeAttackSettings settings = new TimeAttackSettings();
        settings.setDisplayName("evil\nhostPort=1");
        settings.setMasterUrl("x".repeat(500));
        TimeAttackSettings reloaded = TimeAttackSettings.parse(settings.encode());
        assertEquals(27888, reloaded.hostPort());
        assertEquals("evil hostPort=1", reloaded.displayName());
        assertEquals(TimeAttackSettings.MAX_TEXT_LENGTH, reloaded.masterUrl().length());
    }

    @Test
    void settingsPageEditsSaveAtOnceAndRejectABadPort() {
        TimeAttackSettings settings = new TimeAttackSettings();
        AtomicInteger saves = new AtomicInteger();
        List<MenuCue> cues = new ArrayList<>();
        SettingsView view = new SettingsView(settings, saves::incrementAndGet, cues::add);
        FakeViewInput input = new FakeViewInput();

        input.tapEnter(view::update); // display name editor
        input.type(view::update, "Tails_2");
        input.tapKey(view::update, com.openggf.mods.scene.SceneKeys.ENTER);
        assertEquals("Tails_2", settings.displayName());
        assertEquals(1, saves.get());

        input.tapDown(view::update); // look
        input.tapDown(view::update); // host port
        input.tapEnter(view::update);
        for (int i = 0; i < 5; i++) input.tapKey(view::update, com.openggf.mods.scene.SceneKeys.BACKSPACE);
        input.type(view::update, "70000");
        input.tapKey(view::update, com.openggf.mods.scene.SceneKeys.ENTER);
        assertEquals(27888, settings.hostPort());
        assertEquals(MenuCue.ERROR, cues.getLast());
        assertEquals(1, saves.get());

        input.tapEnter(view::update);
        for (int i = 0; i < 5; i++) input.tapKey(view::update, com.openggf.mods.scene.SceneKeys.BACKSPACE);
        input.type(view::update, "30001");
        input.tapKey(view::update, com.openggf.mods.scene.SceneKeys.ENTER);
        assertEquals(30001, settings.hostPort());

        input.tapDown(view::update); // master URL
        input.tapEnter(view::update);
        input.type(view::update, "ws://127.0.0.1:9000/master");
        input.tapKey(view::update, com.openggf.mods.scene.SceneKeys.ENTER);
        assertEquals("ws://127.0.0.1:9000/master", settings.masterUrl());

        input.tapDown(view::update); // master TLS
        input.tapRight(view::update);
        assertTrue(settings.masterTrustInsecure());
        input.tapDown(view::update); // minimap
        input.tapEnter(view::update);
        assertFalse(settings.minimap());
        assertEquals(5, saves.get());

        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        view.draw(canvas);
        assertTrue(canvas.joined().contains("TRUST ALL"), canvas.joined());

        assertFalse(view.consumeCloseRequested());
        input.tapEscape(view::update);
        assertTrue(view.consumeCloseRequested());
    }
    @Test
    void failedSettingsPageEditShowsPendingUntilConfirmedRetry() {
        MemoryModStorage storage = new MemoryModStorage();
        TimeAttackSettings settings = new TimeAttackSettings();
        assertTrue(settings.save(storage));
        SettingsView view = new SettingsView(settings, () -> settings.save(storage), cue -> {});
        FakeViewInput input = new FakeViewInput();
        for (int i = 0; i < 5; i++) input.tapDown(view::update); // minimap
        storage.failures = 1;
        input.tapEnter(view::update);
        assertFalse(settings.minimap());
        RecordingSceneCanvas pending = new RecordingSceneCanvas(320); view.draw(pending);
        assertTrue(pending.joined().contains("save pending"));
        assertFalse(pending.joined().contains("Saved"));
        for (int i = 0; i < 60; i++) settings.retryPending(storage);
        RecordingSceneCanvas saved = new RecordingSceneCanvas(320); view.draw(saved);
        assertTrue(saved.joined().contains("Saved"));
        assertFalse(TimeAttackSettings.load(storage).minimap());
    }

    @Test
    void refusedFirstDefaultPublicationHasNoInventedConfirmedSnapshot() {
        MemoryModStorage storage = new MemoryModStorage();
        TimeAttackSettings settings = TimeAttackSettings.load(storage);
        storage.failures = 1;
        assertFalse(settings.save(storage));
        assertTrue(settings.pendingPublication());
        assertTrue(settings.flushPending(storage));
        assertTrue(storage.read(TimeAttackSettings.FILE_NAME).isPresent());
        assertEquals(2, storage.writes);
    }

    @Test
    void revertingToConfirmedSettingsCancelsObsoletePublicationAndRepeatedFailuresStayBounded() {
        MemoryModStorage storage = new MemoryModStorage();
        storage.texts.put(TimeAttackSettings.FILE_NAME, "displayName=Original\n");
        TimeAttackSettings settings = TimeAttackSettings.load(storage);
        settings.setDisplayName("Pending"); storage.failures = 100;
        assertFalse(settings.save(storage));
        assertTrue(settings.pendingPublication());
        for (int i = 0; i < 120; i++) settings.retryPending(storage);
        assertEquals(3, storage.writes);
        settings.setDisplayName("Original");
        assertFalse(settings.pendingPublication());
        assertEquals("", settings.publicationNotice());
        assertTrue(settings.flushPending(storage));
        assertEquals(3, storage.writes, "exit cannot publish an obsolete failed snapshot after a revert");
    }

    @Test
    void retryKeyCaptureSavesAndEscapeCancelsWithoutLosingTheCurrentBinding() {
        TimeAttackSettings settings = new TimeAttackSettings();
        AtomicInteger saves = new AtomicInteger();
        SettingsView view = new SettingsView(settings, saves::incrementAndGet, cue -> { });
        FakeViewInput input = new FakeViewInput();
        for (int row = 0; row < 6; row++) input.tapDown(view::update); // retry key
        input.tapEnter(view::update);
        assertTrue(view.isEditingText());
        input.tapKey(view::update, com.openggf.mods.scene.SceneKeys.F6);
        assertEquals(com.openggf.mods.scene.SceneKeys.F6, settings.retryKey());
        assertEquals(1, saves.get());
        assertFalse(view.isEditingText());
        input.tapEnter(view::update);
        input.tapEscape(view::update);
        assertEquals(com.openggf.mods.scene.SceneKeys.F6, settings.retryKey());
        assertEquals(1, saves.get());
        assertFalse(view.consumeCloseRequested());
    }

    @Test
    void displayNamesAreStoredCanonicalSoHostsAcceptThem() {
        TimeAttackSettings settings = new TimeAttackSettings();
        settings.setDisplayName("  Ле\u202E  Ra\u0000y  ");
        assertEquals("Ле Ray", settings.displayName());
        settings.setDisplayName("x".repeat(40));
        assertEquals(com.openggf.net.identity.DisplayNames.MAX_CODE_POINTS, settings.displayName().length());
        assertTrue(com.openggf.net.identity.DisplayNames.isCanonical(settings.displayName()));
    }

    @Test
    void looksRoundTripAndBuildTheCanonicalAppearanceToSend() {
        TimeAttackSettings settings = new TimeAttackSettings();
        assertEquals(com.openggf.net.identity.PlayerAppearance.STOCK, settings.appearanceFor("s3k", "sonic"),
                "no base colour is the stock look");
        settings.setLookBase(0x0E62);
        assertEquals(com.openggf.mods.run.CharacterLooks.withBaseColour("s3k", "sonic", 0x0E62),
                settings.appearanceFor("s3k", "sonic"), "without a custom palette the body ramp follows the base");
        var custom = List.of(new com.openggf.net.identity.PlayerAppearance.Entry(2, 0x0EEE),
                new com.openggf.net.identity.PlayerAppearance.Entry(9, 0x000E));
        settings.setCustomPalette("s3k", "tails", custom);
        MemoryModStorage storage = new MemoryModStorage();
        assertTrue(settings.save(storage));
        String text = storage.read(TimeAttackSettings.FILE_NAME).orElseThrow();
        assertTrue(text.contains("lookBase=E62\n"), text);
        assertTrue(text.contains("lookPalette.s3k:tails=2EEE.900E\n"), text);

        TimeAttackSettings loaded = TimeAttackSettings.load(storage);
        assertEquals(0x0E62, loaded.lookBase());
        assertEquals(custom, loaded.customPalette("s3k", "tails"));
        assertEquals(new com.openggf.net.identity.PlayerAppearance(0x0E62, custom), loaded.appearanceFor("s3k", "TAILS"));
        assertEquals("E62.2EEE.900E", loaded.appearanceFor("s3k", "tails").encode(), "the wire form sent on join");
        assertEquals("E62", loaded.appearanceFor(null, null).encode(), "no character: the name colour only");
        loaded.setCustomPalette("s3k", "tails", null);
        assertFalse(loaded.appearanceFor("s3k", "sonic").palette().isEmpty());
        assertEquals(com.openggf.mods.run.CharacterLooks.withBaseColour("s3k", "tails", 0x0E62),
                loaded.appearanceFor("s3k", "tails"));
    }

    @Test
    void unreadableLooksFallBackWithoutLosingTheRest() {
        TimeAttackSettings settings = TimeAttackSettings.parse("""
                lookBase=e62
                lookPalette.s3k:sonic=2EEF
                lookPalette.../x=2EEE
                lookPalette.s2:sonic=3EEE.2EEE
                displayName=Ray
                """);
        assertEquals(com.openggf.net.identity.PlayerAppearance.NO_BASE_COLOUR, settings.lookBase(), "lower case is not canonical");
        assertEquals(null, settings.customPalette("s3k", "sonic"));
        assertEquals(null, settings.customPalette("s2", "sonic"), "entries must ascend");
        assertEquals("Ray", settings.displayName());
        assertThrows(IllegalArgumentException.class, () -> settings.setLookBase(0x0E63));
    }

    @Test
    void profilePagePicksPresetsStepsColoursAndEditsSingleEntries() {
        TimeAttackSettings settings = new TimeAttackSettings();
        settings.setDisplayName("Ray");
        AtomicInteger saves = new AtomicInteger();
        List<MenuCue> cues = new ArrayList<>();
        ProfileView view = new ProfileView(settings, "s3k", "sonic", saves::incrementAndGet, cues::add);
        FakeViewInput input = new FakeViewInput();
        assertEquals(ProfileView.Row.PRESET, view.focusedRow());
        input.tapRight(view::update); // Classic -> Crimson
        assertEquals(0x022E, view.appearance().baseColour());
        assertEquals(com.openggf.mods.run.CharacterLooks.withBaseColour("s3k", "sonic", 0x022E), view.appearance());
        input.tapDown(view::update); // edit target
        input.tapDown(view::update); // red
        input.tapLeft(view::update);
        assertEquals(0x022C, view.appearance().baseColour(), "red steps one Mega Drive level");
        assertEquals("Custom colour", label(view));
        input.tapUp(view::update); // edit target
        input.tapRight(view::update); // entry 1
        input.tapRight(view::update); // entry 2 (body ramp)
        input.tapDown(view::update); // red
        input.tapDown(view::update); // green
        input.tapRight(view::update);
        int edited = view.appearance().colourAt(2, -1);
        assertEquals(((com.openggf.mods.run.CharacterLooks.withBaseColour("s3k", "sonic", 0x022C)
                .colourAt(2, -1) >> 5) & 7) + 1, (edited >> 5) & 7, "green of entry 2 moved");
        assertEquals("Custom palette", label(view));
        assertEquals(com.openggf.mods.run.CharacterLooks.withBaseColour("s3k", "sonic", 0x022C).colourAt(4, -1),
                view.appearance().colourAt(4, -1), "the rest of the ramp is kept when one entry changes");
        input.tapUp(view::update);
        input.tapUp(view::update); // edit target
        input.tapRight(view::update); // entry 3
        input.tapEnter(view::update); // back to the ROM colour
        assertEquals(-1, view.appearance().colourAt(3, -1));
        assertEquals(0, saves.get(), "nothing is written before SAVE");
        for (int i = 0; i < 4; i++) input.tapDown(view::update); // save
        input.tapEnter(view::update);
        assertTrue(view.consumeCloseRequested());
        assertTrue(view.wasSaved());
        assertEquals(1, saves.get());
        assertEquals(0x022C, settings.lookBase());
        assertEquals(view.appearance(), settings.appearanceFor("s3k", "sonic"));
        assertEquals(edited, settings.appearanceFor("s3k", "sonic").colourAt(2, -1));
        assertEquals(-1, settings.appearanceFor("s3k", "sonic").colourAt(3, -1));
        assertEquals("Custom palette", LookPresets.label(settings, "s3k", "sonic"));
        assertEquals("Custom colour", LookPresets.label(settings, "s3k", "tails"), "palettes are per character");
    }

    @Test
    void profilePageRenamesInAnyScriptAndCancelKeepsTheSettings() {
        TimeAttackSettings settings = new TimeAttackSettings();
        settings.setDisplayName("Ray");
        AtomicInteger saves = new AtomicInteger();
        ProfileView view = new ProfileView(settings, "s2", "tails", saves::incrementAndGet, cue -> { });
        FakeViewInput input = new FakeViewInput();
        input.tapUp(view::update); // name
        input.tapEnter(view::update);
        assertTrue(view.isEditingText());
        for (int i = 0; i < 3; i++) input.tapKey(view::update, com.openggf.mods.scene.SceneKeys.BACKSPACE);
        input.commit(view::update, "Λευκός");
        input.tapKey(view::update, com.openggf.mods.scene.SceneKeys.ENTER);
        assertEquals("Λευκός", view.name());
        input.tapEscape(view::update);
        assertTrue(view.consumeCloseRequested());
        assertFalse(view.wasSaved());
        assertEquals("Ray", settings.displayName(), "back discards the page's copy");
        assertEquals(0, saves.get());
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        new ProfileView(settings, "s2", "tails", () -> { }, cue -> { }).draw(canvas);
        canvas.assertFillsOnScreen();
        assertTrue(canvas.drewName("Ray", 16, settings.appearanceFor("s2", "tails").nameArgb()));
    }

    private static String label(ProfileView view) {
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        view.draw(canvas);
        for (String name : List.of("Custom palette", "Custom colour", "Classic", "Crimson")) {
            if (canvas.joined().contains("< " + name + " >")) return name;
        }
        return canvas.joined();
    }
}
