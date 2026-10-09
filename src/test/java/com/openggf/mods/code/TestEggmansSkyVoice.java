package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.mods.scene.SceneAudio;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneStorage;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Announcements through the actual packaged mod, without ROM or audio-device prerequisites. */
class TestEggmansSkyVoice {
    @TempDir static Path work;
    private static ExampleModHarness harness;
    private static Class<?> voiceType;
    private static Class<?> lineType;
    private static Class<?> playerType;

    @BeforeAll static void build() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/eggmans-sky"), work);
        voiceType = harness.loader().loadClass("eggsky.core.Voice");
        lineType = harness.loader().loadClass("eggsky.core.VoiceLine");
        playerType = harness.loader().loadClass("eggsky.game.Player");
    }
    @AfterAll static void close() throws Exception { if (harness != null) harness.close(); }

    private static final class Audio implements SceneAudio {
        final List<String> played = new ArrayList<>();
        @Override public boolean playSfx(String id) { played.add(id); return true; }
        @Override public void playMusic(int id) { }
        @Override public void playSfx(int id) { }
        @Override public void fadeOutMusic() { }
        @Override public void stopMusic() { }
    }
    private Object voice() throws Exception { return voiceType.getConstructor().newInstance(); }
    private Object line(String id) throws Exception { return lineType.getField(id).get(null); }
    private void say(Object voice, String id) throws Exception {
        voiceType.getMethod("say", lineType).invoke(voice, line(id));
    }
    private int duration(String id) throws Exception { return lineType.getField("durationTicks").getInt(line(id)); }
    private void update(Object voice, Audio audio, long tick) throws Exception {
        voiceType.getMethod("update", SceneAudio.class, long.class).invoke(voice, audio, tick);
    }

    @Test void emergenciesReplaceLowerVitalWarningsAndNeverOverlapDiscovery() throws Exception {
        Object voice = voice();
        Audio audio = new Audio();
        say(voice, "DISCOVERY");
        say(voice, "LIFE_LOW");
        say(voice, "LIFE_EMPTY");
        say(voice, "LIFE_EMPTY");
        update(voice, audio, 1);
        assertEquals(List.of("voice-life-empty"), audio.played);
        update(voice, audio, duration("LIFE_EMPTY"));
        assertEquals(1, audio.played.size());
        update(voice, audio, duration("LIFE_EMPTY")+13L);
        assertEquals(List.of("voice-life-empty", "voice-discovery"), audio.played);
    }

    @Test void cooldownStartsAtPlaybackAndWantedQueueTracksTheLatestState() throws Exception {
        Object voice = voice();
        Audio audio = new Audio();
        say(voice, "WANTED_THREE");
        say(voice, "WANTED_CLEAR");
        update(voice, audio, 1);
        assertEquals(List.of("voice-wanted-clear"), audio.played);
        update(voice, audio, 1000);
        say(voice, "WANTED_CLEAR");
        update(voice, audio, 1001);
        assertEquals(1, audio.played.size());
        update(voice, audio, 1801);
        say(voice, "WANTED_CLEAR");
        update(voice, audio, 1802);
        assertEquals(2, audio.played.size());
    }

    @Test void recoveredVitalsRemoveQueuedWarningsAndPulseHasHysteresis() throws Exception {
        Object voice = voice();
        Audio audio = new Audio();
        Class<?> catalog = harness.loader().loadClass("eggsky.game.Catalog");
        Object player = playerType.getConstructor(catalog).newInstance(catalog.getConstructor().newInstance());
        var observe = voiceType.getMethod("vitals", playerType, boolean.class, boolean.class, boolean.class);
        say(voice, "LIFE_LOW");
        say(voice, "HAZARD_EMPTY");
        observe.invoke(voice, player, true, false, true);
        update(voice, audio, 1);
        assertTrue(audio.played.isEmpty(), "healthy vitals cancel stale queued warnings");
        playerType.getField("pulse").setFloat(player, 19);
        observe.invoke(voice, player, false, true, true);
        update(voice, audio, 2);
        assertEquals(List.of("voice-pulse-low"), audio.played);
        update(voice, audio, 2000);
        playerType.getField("pulse").setFloat(player, 21);
        observe.invoke(voice, player, false, true, true);
        playerType.getField("pulse").setFloat(player, 19);
        observe.invoke(voice, player, false, true, true);
        update(voice, audio, 2001);
        assertEquals(1, audio.played.size(), "small threshold crossings do not retrigger");
        playerType.getField("pulse").setFloat(player, 31);
        observe.invoke(voice, player, false, true, true);
        playerType.getField("pulse").setFloat(player, 19);
        observe.invoke(voice, player, false, true, true);
        update(voice, audio, 2002);
        assertEquals(2, audio.played.size());
    }

    @Test void muteClearsQueuedSpeechAndNewExpeditionsRespectThePlayingClip() throws Exception {
        Object voice = voice();
        Audio audio = new Audio();
        say(voice, "CRASH_LANDING");
        update(voice, audio, 1);
        say(voice, "DISCOVERY");
        voiceType.getMethod("enabled", boolean.class).invoke(voice, false);
        voiceType.getMethod("reset").invoke(voice);
        say(voice, "SYSTEMS_ONLINE");
        voiceType.getMethod("enabled", boolean.class).invoke(voice, true);
        say(voice, "SYSTEMS_ONLINE");
        update(voice, audio, 2);
        assertEquals(1, audio.played.size());
        update(voice, audio, duration("CRASH_LANDING")+13L);
        assertEquals(List.of("voice-crash-landing", "voice-systems-online"), audio.played);
    }

    @Test void queueIsBoundedAndObsoleteMessagesExpire() throws Exception {
        Object voice = voice();
        Audio audio = new Audio();
        for (String id : List.of("DISCOVERY", "PLANET_NAMED", "SCAN", "SOLD", "BOUGHT", "REFINED",
                "CRAFTED", "DISCARDED", "SALVAGE", "RINGS_RECEIVED", "LIFE_EMPTY")) say(voice, id);
        update(voice, audio, 1);
        assertEquals(List.of("voice-life-empty"), audio.played);
        update(voice, audio, 1000);
        assertEquals(1, audio.played.size(), "queued old confirmations expire instead of narrating the past");
    }

    @Test void partialRechargeDoesNotClaimFullRestoration() throws Exception {
        Object voice = voice();
        Audio audio = new Audio();
        Class<?> catalog = harness.loader().loadClass("eggsky.game.Catalog");
        Object player = playerType.getConstructor(catalog).newInstance(catalog.getConstructor().newInstance());
        playerType.getField("life").setFloat(player, 20);
        var recharge = voiceType.getMethod("recharged", playerType, int.class);
        recharge.invoke(voice, player, 0);
        update(voice, audio, 1);
        assertEquals(List.of("voice-system-recharged"), audio.played);
        playerType.getField("life").setFloat(player, 100);
        recharge.invoke(voice, player, 0);
        update(voice, audio, duration("SYSTEM_RECHARGED")+13L);
        assertEquals(List.of("voice-system-recharged", "voice-life-restored"), audio.played);
    }

    @Test void voiceMutePersistsIndependentlyAndPreservesOtherSettings() throws Exception {
        Class<?> gameType = harness.loader().loadClass("eggsky.Game");
        Object game = gameType.getConstructor().newInstance();
        SceneContext context = mock(SceneContext.class);
        SceneStorage storage = mock(SceneStorage.class);
        when(context.storage()).thenReturn(storage);
        when(storage.read("settings.txt")).thenReturn(Optional.of("other=value\nvoice=true\n"));
        when(storage.write(anyString(), anyString())).thenReturn(true);
        gameType.getField("ctx").set(game, context);
        gameType.getMethod("toggleVoice").invoke(game);
        Object voice = gameType.getField("voice").get(game);
        assertEquals(false, voiceType.getMethod("enabled").invoke(voice));
        verify(storage).write("settings.txt", "other=value\nvoice=false\n");
        verify(context, never()).audio();
        Object sound = gameType.getField("sound").get(game);
        assertTrue(sound.getClass().getField("enabled").getBoolean(sound));
    }

    @Test void aRejectedStorageWriteReportsFailureInsteadOfSaveSuccess() throws Exception {
        Class<?> gameType = harness.loader().loadClass("eggsky.Game");
        Object game = gameType.getConstructor().newInstance();
        SceneContext context = mock(SceneContext.class);
        SceneStorage storage = mock(SceneStorage.class);
        when(context.storage()).thenReturn(storage);
        when(storage.write(anyString(), anyString())).thenReturn(false);
        gameType.getField("ctx").set(game, context);
        Object catalog = gameType.getField("catalog").get(game);
        gameType.getField("player").set(game, playerType.getConstructor(catalog.getClass()).newInstance(catalog));
        Class<?> galaxy = harness.loader().loadClass("eggsky.world.Galaxy");
        gameType.getField("galaxy").set(game, galaxy.getConstructor(long.class, int.class).newInstance(42L, 1));
        assertEquals(false, gameType.getMethod("save").invoke(game));
        Audio audio = new Audio();
        update(gameType.getField("voice").get(game), audio, 1);
        assertEquals(List.of("voice-save-failed"), audio.played);
    }
}
