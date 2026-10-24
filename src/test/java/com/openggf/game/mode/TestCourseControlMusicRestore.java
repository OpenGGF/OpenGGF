package com.openggf.game.mode;

import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.DelegatingZoneRegistry;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.timing.Sonic2LevelMusicScheduler;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-free course restore through real session, sprite and music-scheduler adapters. */
class TestCourseControlMusicRestore {
    @AfterEach
    void close() {
        try {
            var audio = GameServices.audio();
            audio.resetState();
            audio.setBackend(new NullAudioBackend());
        } finally {
            SessionManager.clear();
            SonicConfigurationService.getInstance().clearSessionOverrides();
        }
    }

    @ParameterizedTest(name = "music={0}, capturedPending={1}, livePending={2}")
    @CsvSource({
            "-1,  false, false, false, false",
            "-2,  false, false, false, false",
            "0,   false, false, true,  false",
            "129, false, false, true,  false",
            "129, true,  true,  false, true",
            "129, true,  false, true,  false",
            "129, false, true,  true,  false"
    })
    void restoreRequestsOnlyDefinedMusicAfterRestoringThePublicationGate(
            int musicId, boolean capturedPending, boolean livePending,
            boolean expectMusic, boolean expectPending) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var base = GameModuleRegistry.getCurrent();
        var scheduler = base.rewindAdapters().stream()
                .filter(Sonic2LevelMusicScheduler.class::isInstance)
                .map(Sonic2LevelMusicScheduler.class::cast)
                .findFirst().orElseThrow();
        var silent = new SonicCdGameModule();
        // Exercise the real CD no-song registry/profile, without admitting CD media or a player.
        ZoneRegistry zones = musicId == -1 ? silent.getZoneRegistry()
                : new DelegatingZoneRegistry(silent.getZoneRegistry()) {
                    @Override public int getMusicId(int zone, int act) { return musicId; }
                };
        TestEnvironment.configureGameModuleFixture(new DelegatingGameModule(base, "test:course-music") {
            @Override public ZoneRegistry getZoneRegistry() { return zones; }
            @Override public com.openggf.audio.GameAudioProfile getAudioProfile() {
                return silent.getAudioProfile();
            }
        });
        var context = TestEnvironment.activeGameplayMode();
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var player = new CheckpointPlayer();
        context.getSpriteManager().addSprite(player);
        context.getCamera().setFocusedSprite(player);

        var audio = GameServices.audio();
        audio.resetState();
        var backend = new AudioTestFixtures.RecordingAudioBackend();
        audio.setBackend(backend);
        audio.setAudioProfile(silent.getAudioProfile());
        var requests = new ArrayList<String>();
        audio.setRequestObserver((kind, id) -> requests.add(kind + ":" + id));
        var beforeAudio = audio.captureLogicalSnapshot();
        assertNull(beforeAudio.presentation().activeMusic());
        assertFalse(beforeAudio.presentation().speedShoesEnabled());

        if (capturedPending) scheduler.arm(0x81, 2);
        var course = new CourseControl(context);
        player.setXSpeed((short) 0x123);
        var checkpoint = course.capture();
        player.setXSpeed((short) 0x456);
        if (livePending) scheduler.arm(0x82, 1);
        else scheduler.cancel();
        backend.clear();

        course.restore(checkpoint);

        assertSame(player, context.getCamera().getFocusedSprite());
        assertEquals(0x123, player.getXSpeed(), "the actual sprite adapter must restore before the audio tail");
        assertEquals(expectPending, base.getLevelInitProfile().isLevelMusicPublicationPending());
        assertEquals(expectPending ? new Sonic2LevelMusicScheduler.Snapshot(0x81, 2)
                : new Sonic2LevelMusicScheduler.Snapshot(-1, 0), scheduler.capture(),
                "restored pending entry work is retained only until its first publication");
        assertEquals(expectMusic ? List.of("MUSIC:" + musicId) : List.of(), requests);
        var commands = audio.commandTimeline().entries().stream().map(entry -> entry.command()).toList();
        assertEquals(expectMusic ? List.of(new AudioCommand.PlayMusic(
                musicId, AudioCommand.MusicRoute.FALLBACK_WAV, false, null)) : List.of(), commands,
                "no-song restore must emit neither music nor speed-shoes commands");
        assertFalse(audio.captureLogicalSnapshot().presentation().speedShoesEnabled());
        if (!expectMusic) assertEquals(beforeAudio, audio.captureLogicalSnapshot(),
                "silent or still-pending restore must preserve all logical audio state");
        // Commands are owned by the presentation producer; this backend log alone cannot catch the bug.
        assertEquals(List.of(), backend.calls);
    }

    private static final class CheckpointPlayer extends AbstractPlayableSprite {
        CheckpointPlayer() { super("sonic", (short) 0, (short) 0); }
        @Override protected void defineSpeeds() { }
        @Override protected void createSensorLines() {
            groundSensors = new Sensor[0]; ceilingSensors = new Sensor[0]; pushSensors = new Sensor[0];
        }
        @Override public void draw() { fail("checkpoint restore must not render"); }
    }
}
