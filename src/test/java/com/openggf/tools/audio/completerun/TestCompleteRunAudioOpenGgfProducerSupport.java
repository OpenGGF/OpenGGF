package com.openggf.tools.audio.completerun;

import com.openggf.Engine;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.tools.audio.completerun.CompleteRunAudioProducer.Request;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerKind;
import com.openggf.tools.audio.completerun.s1.S1CompleteRunAudioProfile;
import com.openggf.tools.audio.completerun.s2.S2CompleteRunAudioProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestCompleteRunAudioOpenGgfProducerSupport {
    @TempDir Path temporary;

    @Test
    void unavailableFixedBindingsStopBeforeReadingInputsOrOwningRuntime() throws Exception {
        for (String id : new String[] {S1CompleteRunAudioProfile.ID, S2CompleteRunAudioProfile.ID}) {
            Path output = Files.writeString(temporary.resolve(id), "preserve");
            var request = request(ProducerKind.OPENGGF, id, output);
            var previous = Engine.currentGameLoop();
            Exception failure = assertThrows(Exception.class,
                    () -> CompleteRunAudioOpenGgfProducerSupport.capture(request, id));
            assertFalse(failure instanceof java.nio.file.NoSuchFileException,
                    "the gate must precede poisoned input paths");
            assertEquals("preserve", Files.readString(output));
            assertSame(previous, Engine.currentGameLoop());
        }
    }

    @Test
    void typedProducerAndProfileCannotBeSubstituted() {
        assertThrows(IllegalArgumentException.class, () -> CompleteRunAudioOpenGgfProducerSupport.capture(
                request(ProducerKind.OPENGGF, S1CompleteRunAudioProfile.ID, temporary.resolve("out")),
                S2CompleteRunAudioProfile.ID));
    }

    @Test
    void fixedStartupUsesPrivateDefaultsAndTheDeclaredRomAndTeam() throws Exception {
        for (String id : new String[] {S1CompleteRunAudioProfile.ID, S2CompleteRunAudioProfile.ID}) {
            Path preferences = Files.createDirectory(temporary.resolve(id));
            var startup = CompleteRunAudioProducerRegistry.startup(id);
            var request = request(ProducerKind.OPENGGF, id, temporary.resolve("out"));
            var config = CompleteRunAudioOpenGgfProducerSupport.configuration(preferences, request, startup);
            assertEquals(startup.gameId(), config.getString(SonicConfiguration.DEFAULT_ROM));
            assertEquals(request.rom().toString(), config.getString(startup.romKey()));
            assertEquals(startup.sidekick(), config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
            assertTrue(config.getBoolean(SonicConfiguration.TITLE_SCREEN_ON_STARTUP));
            assertTrue(config.getBoolean(SonicConfiguration.AUDIO_ENABLED));
            assertFalse(config.getBoolean(SonicConfiguration.LEVEL_SELECT_ON_STARTUP));
            assertFalse(config.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
            assertFalse(config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED));
            ProductionBk2AudioRunner.requireAuthenticatedConfiguration(config);
        }
    }

    @Test
    void metadataCannotTurnAnUnavailableDeclarationIntoEvidence() {
        for (var profile : new CompleteRunAudioProfile[] {
                S1CompleteRunAudioProfile.profile(), S2CompleteRunAudioProfile.profile()}) {
            for (var kind : ProducerKind.values()) {
                assertThrows(IllegalStateException.class,
                        () -> CompleteRunAudioProducerMetadata.pinned(profile, kind));
            }
        }
    }

    private Request request(ProducerKind kind, String id, Path output) {
        Path poison = temporary.resolve("absent");
        return new Request(kind, id, poison.resolve("rom.gen"), poison.resolve("movie.bk2"),
                poison.resolve("manifest.json"), null, output);
    }
}
