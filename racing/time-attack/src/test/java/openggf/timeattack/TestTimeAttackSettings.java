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
        settings.setHostPort(30000);
        settings.setLastJoinAddress("[2001:db8::1]:27888#code");
        settings.setDisplayName("Knuckles é");
        settings.setMasterUrl("wss://master.example:8443/master");
        settings.setMasterTrustInsecure(true);
        settings.setMinimap(false);
        assertTrue(settings.save(storage));
        assertTrue(storage.read(TimeAttackSettings.FILE_NAME).orElseThrow().contains("hostPort=30000"));

        TimeAttackSettings loaded = TimeAttackSettings.load(storage);
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
}
