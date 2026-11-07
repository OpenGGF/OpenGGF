package com.openggf.game.soniccd;

import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdR3PcmSource;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.openggf.game.soniccd.SonicCdProgramLoadServices.Boundary;
import com.openggf.game.soniccd.SonicCdProgramLoadServices.LoadPrefix;
import com.openggf.game.soniccd.SonicCdProgramLoadServices.PcmKnowledge;
import com.openggf.game.soniccd.SonicCdProgramLoadServices.SelectorKnowledge;
import com.openggf.game.soniccd.SonicCdProgramLoadServices.Unsupported;

import static com.openggf.game.soniccd.SonicCdProgramLoadServices.developmentKnownAbsent;
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.loadLevelR1Pcm;
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.loadLevelR3Pcm;
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.requestPastMusic;
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.unknown;
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.validateForCapture;
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.validateWithR3ForCapture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests FIRST, author Java UNRUN. File/initial-variable values, never driver execution or World admission. */
class TestSonicCdR3ProgramLoadServices {
    @Test void oldOrdinalsAndInitialValuesRemainExact() {
        assertEquals(List.of("UNKNOWN", "KNOWN_ABSENT", "LOADED"),
                Arrays.stream(PcmKnowledge.values()).map(Enum::name).toList());
        assertEquals(List.of("UNKNOWN", "CLEARED", "SELECTED"),
                Arrays.stream(SelectorKnowledge.values()).map(Enum::name).toList());
        assertEquals(List.of("NONE", "CDDA_MUSIC_WRITTEN", "CDDA_MASTER_WRITTEN", "MATCH_REUSED",
                        "SELECTOR_WRITTEN", "SOURCE_INSTALLED"),
                Arrays.stream(LoadPrefix.values()).map(Enum::name).toList());
        assertEquals(List.of("PCM_KNOWLEDGE_UNKNOWN", "PCM_INSTALLATION_UNSUPPORTED",
                        "PCM_NOT_INSTALLED", "PCM_REQUEST_INCOMPLETE"),
                Arrays.stream(Boundary.values()).map(Enum::name).toList());
        assertEquals(unknown(), unknown());
        assertEquals(developmentKnownAbsent(), developmentKnownAbsent());
        assertThrows(NullPointerException.class, () -> loadLevelR3Pcm(developmentKnownAbsent(), null));
        assertTrue(unknown().lastLoad().requiredR3Source().isEmpty());
    }

    @Nested class ActualUsa {
        static CdTimeWarpPrograms r1;
        static CdR3PcmSource first, second;

        @BeforeAll static void actualSource() throws Exception {
            var root = System.getProperty("soniccd.media.root");
            Assumptions.assumeTrue(root != null && !root.isBlank(), "Explicit original-USA source opt-in");
            Path cue;
            try (var paths = Files.list(Path.of(root).resolve("USA"))) {
                var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
                assertEquals(1, cues.size());
                cue = cues.getFirst();
            }
            try (var p = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
                r1 = CdTimeWarpPrograms.fromPrograms(p);
                first = CdR3PcmSource.fromPrograms(p);
                second = CdR3PcmSource.fromPrograms(p);
            }
            assertNotSame(first, second);
            assertNotSame(first.bytes(), second.bytes());
            assertEquals(first.source(), second.source());
        }

        @Test void unknownStopsAfterCddaBeforeSelectorAndCannotRetry() {
            var before = unknown();
            var error = assertThrows(Unsupported.class, () -> loadLevelR3Pcm(before, first));
            var state = error.completedPrefix();
            assertEquals(Boundary.PCM_KNOWLEDGE_UNKNOWN, error.boundary());
            assertEquals(LoadPrefix.CDDA_MASTER_WRITTEN, state.lastLoad().prefix());
            assertEquals(first.source(), state.lastLoad().requiredR3Source().orElseThrow());
            assertTrue(state.lastLoad().requiredSource().isEmpty());
            assertEquals(0x0380, state.musicSetting().orElseThrow());
            assertEquals(0x8380, state.masterSetting().orElseThrow());
            assertTrue(state.installed().isEmpty());
            assertTrue(state.requestedSelector().isEmpty());
            assertTrue(before.musicSetting().isEmpty());
            validateWithR3ForCapture(state, r1, first);
            assertThrows(IllegalArgumentException.class, () -> validateForCapture(state, r1));
            var retry = assertThrows(Unsupported.class, () -> loadLevelR3Pcm(state, first));
            assertEquals(Boundary.PCM_REQUEST_INCOMPLETE, retry.boundary());
            assertSame(state, retry.completedPrefix());
        }

        @Test void originalR3InstallationReadsTypedInitialVariablesAndNeverBorrowsR1() {
            var state = loadLevelR3Pcm(developmentKnownAbsent(), first);
            var installed = state.installed().orElseThrow();
            var variables = installed.variables();
            assertEquals(CdNativeFile.R3PCM, installed.driver().file());
            assertEquals(first.source(), installed.driver().r3Source().orElseThrow());
            assertThrows(IllegalStateException.class, () -> installed.driver().source());
            assertSame(first.bytes(), installed.driver().originalSource());
            assertEquals(242597, installed.driver().originalSource().length());
            assertEquals(CdNativeFile.R3PCM, state.requestedSelector().orElseThrow().file());
            assertEquals(first.source(), state.requestedSelector().orElseThrow().r3Source().orElseThrow());
            assertThrows(IllegalStateException.class, () -> state.requestedSelector().orElseThrow().source());
            assertEquals(LoadPrefix.SOURCE_INSTALLED, state.lastLoad().prefix());
            assertEquals(0, variables.on());
            assertEquals(0, variables.soundPlay());
            assertEquals(0, variables.pointerOffset());
            assertEquals(0, variables.queue().slot0());
            assertEquals(0, variables.queue().slot1());
            assertEquals(0, variables.queue().slot2());
            assertEquals(0, variables.queue().slot3());
            assertEquals(0x0380, state.musicSetting().orElseThrow());
            assertEquals(0x8380, state.masterSetting().orElseThrow());
            validateWithR3ForCapture(state, r1, first);
            assertThrows(IllegalArgumentException.class, () -> validateForCapture(state, r1));
        }

        @Test void realBankMismatchReplacesSourceAndVariablesInBothDirections() {
            var old = requestPastMusic(loadLevelR1Pcm(developmentKnownAbsent(), r1));
            var oldDriver = old.installed().orElseThrow().driver();
            var state = loadLevelR3Pcm(old, first);
            assertNotSame(oldDriver, state.installed().orElseThrow().driver());
            assertSame(first.bytes(), state.installed().orElseThrow().driver().originalSource());
            assertTrue(state.lastPastWrite().isEmpty());
            assertEquals(0, state.installed().orElseThrow().variables().queue().slot0());
            assertEquals(0x81, old.installed().orElseThrow().variables().queue().slot0());
            assertEquals(CdNativeFile.R1PCM, oldDriver.file());
            assertSame(r1.r1Pcm(), oldDriver.originalSource());
            validateWithR3ForCapture(old, r1, first);
            var back = loadLevelR1Pcm(state, r1);
            assertEquals(CdNativeFile.R1PCM, back.installed().orElseThrow().driver().file());
            assertTrue(back.installed().orElseThrow().driver().r3Source().isEmpty());
            assertTrue(back.lastLoad().requiredR3Source().isEmpty());
            validateForCapture(back, r1);
        }

        @Test void matchingR3ReusesExactInstalledReferencesAfterEquivalentSourceRead() {
            var old = requestPastMusic(loadLevelR3Pcm(developmentKnownAbsent(), first));
            var state = loadLevelR3Pcm(old, second);
            assertEquals(LoadPrefix.MATCH_REUSED, state.lastLoad().prefix());
            assertSame(old.installed().orElseThrow(), state.installed().orElseThrow());
            assertSame(old.installed().orElseThrow().variables(), state.installed().orElseThrow().variables());
            assertSame(old.requestedSelector().orElseThrow(), state.requestedSelector().orElseThrow());
            assertSame(old.lastPastWrite().orElseThrow(), state.lastPastWrite().orElseThrow());
            assertSame(first.bytes(), state.installed().orElseThrow().driver().originalSource());
            assertEquals(0x81, state.installed().orElseThrow().variables().queue().slot0());
            validateWithR3ForCapture(state, r1, second);
        }

        @Test void sourceDrivenValueReplayEqualsWithoutAliasingDriverInstances() {
            var state = loadLevelR3Pcm(developmentKnownAbsent(), first);
            var other = loadLevelR3Pcm(developmentKnownAbsent(), second);
            assertNotSame(state.installed().orElseThrow().driver(), other.installed().orElseThrow().driver());
            assertEquals(state, other);
            assertEquals(state.hashCode(), other.hashCode());
            var firstStop = assertThrows(Unsupported.class, () -> loadLevelR3Pcm(unknown(), first)).completedPrefix();
            var secondStop = assertThrows(Unsupported.class, () -> loadLevelR3Pcm(unknown(), second)).completedPrefix();
            assertEquals(firstStop, secondStop);
            assertEquals(firstStop.hashCode(), secondStop.hashCode());
            assertNotEquals(state, loadLevelR1Pcm(developmentKnownAbsent(), r1));
        }

        @Test void legacyR1HashFormulasAndCaptureRemainUnchanged() {
            var state = loadLevelR1Pcm(developmentKnownAbsent(), r1);
            assertEquals(Objects.hash(r1.source()), state.installed().orElseThrow().driver().hashCode());
            assertEquals(Objects.hash(r1.source()), state.requestedSelector().orElseThrow().hashCode());
            assertEquals(Objects.hash(r1.source(), LoadPrefix.SOURCE_INSTALLED, null), state.lastLoad().hashCode());
            assertEquals(r1.source(), state.installed().orElseThrow().driver().source());
            assertEquals(r1.source(), state.requestedSelector().orElseThrow().source());
            validateForCapture(state, r1);
            validateWithR3ForCapture(state, r1, first);
        }
    }
}
