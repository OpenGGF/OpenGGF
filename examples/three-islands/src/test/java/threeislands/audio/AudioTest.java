package threeislands.audio;

import static org.junit.jupiter.api.Assertions.*;
import com.openggf.mods.scene.SceneAudio;
import com.openggf.mods.scene.SceneContext;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Portable recording driver; no Mockito agent or checkout-only test dependency. */
class AudioTest {
    private final List<String> calls = new ArrayList<>();
    private boolean unsupported;
    private boolean throwing;
    private Audio audio;

    @BeforeEach void setup() {
        SceneAudio driver = (SceneAudio) Proxy.newProxyInstance(SceneAudio.class.getClassLoader(),
                new Class<?>[] {SceneAudio.class}, (proxy, method, args) -> {
                    String call = method.getName();
                    if (args != null) for (Object arg : args) call += ":" + arg;
                    calls.add(call);
                    if (call.equals("playMusic:s1:132")) {
                        if (throwing) throw new IllegalArgumentException("unavailable");
                        return !unsupported;
                    }
                    return method.getReturnType() == boolean.class ? true : null;
                });
        SceneContext ctx = (SceneContext) Proxy.newProxyInstance(SceneContext.class.getClassLoader(),
                new Class<?>[] {SceneContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("audio")) return driver;
                    throw new AssertionError("Unexpected context call: " + method.getName());
                });
        audio = new Audio(ctx);
    }
    @Test void repeatedAreaUpdatesNeverRestartTheNativeLoop() {
        for (int i = 0; i < 10000; i++) audio.music("s1", 0x84, 0x20);
        assertEquals(List.of("playMusic:s1:132"), calls);
        assertTrue(audio.playerActive());
    }
    @Test void dungeonBattleAndReturnHaveOnlyTheirRequestedCues() {
        audio.music("s1", 0x81, 0x20);
        audio.music("s1", 0x83, 0x20);
        audio.music("s3k", Audio.MUS_BOSS);
        audio.jingle(Audio.MUS_ACT_CLEAR);
        audio.music("s1", 0x83, 0x20);
        assertEquals(List.of("playMusic:s1:129", "playMusic:s1:131", "playMusic:" + Audio.MUS_BOSS,
                "playMusic:" + Audio.MUS_ACT_CLEAR, "playMusic:s1:131"), calls);
    }
    @Test void unsupportedHostGetsAStableFallbackWithoutRepeatedRequests() {
        unsupported = true;
        for (int i = 0; i < 120; i++) audio.music("s1", 0x84, 0x20);
        assertEquals(List.of("playMusic:s1:132", "playMusic:32"), calls);
        assertTrue(audio.failed());
    }
    @Test void failedTrackCanBeReplacedAndSceneCanClose() {
        throwing = true;
        assertDoesNotThrow(() -> audio.music("s1", 0x84, 0x20));
        assertTrue(audio.failed());
        audio.music("s2", 0x81, 0x21);
        assertFalse(audio.failed());
        audio.close();
        assertEquals("stopMusic", calls.getLast());
        assertFalse(audio.playerActive());
    }
    @Test void fadeAllowsTheSameTrackToBeRequestedAgain() {
        audio.music("s1", 0x84);
        audio.fadeOut();
        audio.music("s1", 0x84);
        assertEquals(List.of("playMusic:s1:132", "fadeOutMusic", "playMusic:s1:132"), calls);
    }
}
