package com.openggf.mods.scene.host;

import com.openggf.audio.AudioManager;
import com.openggf.audio.StreamedMusicPort;
import com.openggf.game.ActExit;
import com.openggf.game.ZoneKey;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.scene.ActResult;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TestSceneVisitEffectAdmission {
    @TempDir Path temp;
    private final ModSceneHost host = new ModSceneHost();

    @AfterEach
    void cleanup() {
        host.close();
    }

    @Test
    void currentVisitReachesEverySceneAudioEffect() {
        AudioManager audio = mock(AudioManager.class);
        SceneRomLibrary library = mock(SceneRomLibrary.class);
        var local = new StreamedMusicPort.SfxRef("effects", "local");
        when(audio.playNamespacedSfx(local)).thenReturn(true);
        var scene = new CapturingScene();
        host.open(owned(scene), services(audio, library, () -> { }, () -> { }), 320, 224);

        var result = useEveryEffect(scene.context);

        assertFalse(result.donorMusic());
        assertTrue(result.localSfx());
        verify(library).sourceRom("s1");
        verify(audio).playMusic(11);
        verify(audio).setMusicTempoPercent(75);
        verify(audio).playSfx(12);
        verify(audio).playNamespacedSfx(local);
        verify(audio).fadeOutMusic();
        verify(audio).stopMusic();
    }

    @Test
    void retiredVisitCannotAffectAudioOrTitlesAfterReplacement() {
        AudioManager retiredAudio = mock(AudioManager.class);
        SceneRomLibrary retiredLibrary = mock(SceneRomLibrary.class);
        AtomicInteger retiredGameTitle = new AtomicInteger();
        AtomicInteger retiredMasterTitle = new AtomicInteger();
        var retired = new CapturingScene();
        host.open(owned(retired), services(retiredAudio, retiredLibrary,
                retiredGameTitle::incrementAndGet, retiredMasterTitle::incrementAndGet), 320, 224);
        host.close();

        AudioManager currentAudio = mock(AudioManager.class);
        var current = new CapturingScene();
        host.open(owned(current), services(currentAudio, null, () -> { }, () -> { }), 320, 224);
        clearInvocations(retiredAudio, retiredLibrary);

        var result = useEveryEffect(retired.context);
        retired.context.exitToGameTitle();
        retired.context.exitToMasterTitle();

        assertFalse(result.donorMusic());
        assertFalse(result.localSfx());
        assertEquals(0, retiredGameTitle.get());
        assertEquals(0, retiredMasterTitle.get());
        verifyNoInteractions(retiredAudio, retiredLibrary);
        current.context.audio().playSfx(99);
        verify(currentAudio).playSfx(99);
        host.update(null);
        assertEquals(1, current.updates);
        assertThrows(IllegalArgumentException.class,
                () -> retired.context.audio().playMusic("unknown", 1));
        assertThrows(IllegalArgumentException.class,
                () -> retired.context.audio().setMusicTempoPercent(101));
        assertThrows(IllegalArgumentException.class,
                () -> retired.context.audio().playSfx("../foreign"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exitRequestRetiresEffectsAndAdmitsOnlyTheRequestedTitle(boolean master) {
        AudioManager audio = mock(AudioManager.class);
        AtomicInteger gameTitle = new AtomicInteger();
        AtomicInteger masterTitle = new AtomicInteger();
        var scene = new CapturingScene();
        host.open(owned(scene), services(audio, null,
                gameTitle::incrementAndGet, masterTitle::incrementAndGet), 320, 224);

        if (master) scene.context.exitToMasterTitle();
        else scene.context.exitToGameTitle();
        useEveryEffect(scene.context);
        scene.context.exitToGameTitle();
        scene.context.exitToMasterTitle();

        assertEquals(master ? 0 : 1, gameTitle.get());
        assertEquals(master ? 1 : 0, masterTitle.get());
        verifyNoInteractions(audio);
        host.update(null);
        assertEquals(0, scene.updates);
    }

    @Test
    void retainedVisitKeepsNativeAudioAndRegainsTitleExitOnlyAfterResume() {
        AudioManager audio = mock(AudioManager.class);
        AtomicInteger gameTitle = new AtomicInteger();
        AtomicInteger masterTitle = new AtomicInteger();
        var scene = new CapturingScene();
        host.open(owned(scene), services(audio, null,
                gameTitle::incrementAndGet, masterTitle::incrementAndGet), 320, 224);
        host.suspend();

        var suspended = useEveryEffect(scene.context);
        scene.context.exitToGameTitle();
        scene.context.exitToMasterTitle();

        assertFalse(suspended.donorMusic());
        assertFalse(suspended.localSfx());
        assertEquals(0, gameTitle.get());
        assertEquals(0, masterTitle.get());
        verify(audio).playMusic(11);
        verify(audio).setMusicTempoPercent(75);
        verify(audio).playSfx(12);
        verify(audio).playNamespacedSfx(new StreamedMusicPort.SfxRef("effects", "local"));
        verify(audio).fadeOutMusic();
        verify(audio).stopMusic();

        host.resume(new ActResult(new ZoneKey.Mod("effects", "return"),
                ActExit.LEFT, 0, 0, Map.of()));
        scene.context.audio().playSfx(21);
        scene.context.exitToMasterTitle();
        verify(audio).playSfx(21);
        assertEquals(0, gameTitle.get());
        assertEquals(1, masterTitle.get());
    }

    @Test
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void finiteMusicCannotRestartDuringSuspensionAndStartsAgainAfterResume() throws Exception {
        AudioManager audio = mock(AudioManager.class);
        when(audio.outputSampleRate()).thenReturn(8_000);
        SceneRomLibrary library = mock(SceneRomLibrary.class);
        when(library.sourceRom("s1")).thenReturn(com.openggf.game.GameServices.rom().getRom());
        var scene = new CapturingScene();
        host.open(owned(scene), services(audio, library, () -> { }, () -> { }), 320, 224);
        var music = scene.context.music();
        var song = music.prepare("s1", 0x81, 1);
        var active = music.start(song, 0, 0, false, 0);
        verify(audio).setScenePcmSource((com.openggf.audio.presentation.ScenePcmSource) active);
        host.suspend();
        clearInvocations(audio, library);

        assertThrows(IllegalStateException.class, () -> music.start(song, 0, 0, false, 0));
        assertThrows(IllegalStateException.class, () -> music.prepareAsync("s1", 0x81, 1));
        verifyNoInteractions(audio, library);

        host.resume(new ActResult(new ZoneKey.Mod("effects", "return"), ActExit.LEFT, 0, 0, Map.of()));
        var resumed = music.start(song, 0, 0, false, 0);
        verify(audio).setScenePcmSource((com.openggf.audio.presentation.ScenePcmSource) resumed);
    }

    @Test
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void finiteMusicCannotRestartOrControlTheAudioOwnerAfterExitIsRequested() throws Exception {
        AudioManager audio = mock(AudioManager.class);
        when(audio.outputSampleRate()).thenReturn(8_000);
        SceneRomLibrary library = mock(SceneRomLibrary.class);
        when(library.sourceRom("s1")).thenReturn(com.openggf.game.GameServices.rom().getRom());
        var scene = new CapturingScene();
        host.open(owned(scene), services(audio, library, () -> { }, () -> { }), 320, 224);
        var music = scene.context.music();
        var song = music.prepare("s1", 0x81, 1);
        var player = music.start(song, 0, 0, false, 0);
        scene.context.exitToMasterTitle();
        clearInvocations(audio, library);

        assertThrows(IllegalStateException.class, () -> music.start(song, 0, 0, false, 0));
        player.pause();
        player.resume();
        player.stop();
        assertFalse(player.cuePart(0, 1, 1, 1, .5, 0));
        verifyNoInteractions(audio, library);

        host.close();
        verify(audio).setScenePcmSource(null);
    }

    private EffectsResult useEveryEffect(SceneContext context) {
        context.audio().playMusic(11);
        boolean donorMusic = context.audio().playMusic("s1", 2);
        context.audio().setMusicTempoPercent(75);
        context.audio().playSfx(12);
        boolean localSfx = context.audio().playSfx("local");
        context.audio().fadeOutMusic();
        context.audio().stopMusic();
        return new EffectsResult(donorMusic, localSfx);
    }

    private SceneServices services(AudioManager audio, SceneRomLibrary library,
                                   Runnable gameTitle, Runnable masterTitle) {
        return new SceneServices(audio, null, temp, null, gameTitle, masterTitle, library);
    }

    private static com.openggf.mods.code.OwnedSceneFactory owned(CapturingScene scene) {
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        return ModContextTestAccess.ownedScene("effects", () -> scene, boundary);
    }

    private record EffectsResult(boolean donorMusic, boolean localSfx) { }

    private static final class CapturingScene implements ModScene {
        private SceneContext context;
        private int updates;
        @Override public void enter(SceneContext context) { this.context = context; }
        @Override public void update(SceneContext context) { updates++; }
        @Override public void draw(SceneContext context, SceneCanvas canvas) { }
    }
}
