package com.openggf.game.soniccd;

import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdR4PcmSource;
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
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.loadLevelR4Pcm;
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.requestPastMusic;
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.unknown;
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.validateForCapture;
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.validateWithR4ForCapture;
import static com.openggf.game.soniccd.SonicCdProgramLoadServices.validateWithR3ForCapture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests FIRST, author Java UNRUN. File/initial-variable values, never driver execution or World admission. */
class TestSonicCdR4ProgramLoadServices {
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
        assertThrows(NullPointerException.class, () -> loadLevelR4Pcm(developmentKnownAbsent(), null));
        assertTrue(unknown().lastLoad().requiredR4Source().isEmpty());
    }

    @Test void originalR41AndR42SelectR4ButR43SelectsDistinctUnsupportedBoss() {
        for(var file:List.of(CdNativeFile.R41A,CdNativeFile.R41B,CdNativeFile.R41C,CdNativeFile.R41D,
                CdNativeFile.R42A,CdNativeFile.R42B,CdNativeFile.R42C,CdNativeFile.R42D))
            assertEquals(CdNativeFile.R4PCM,SonicCdProgramLoadServices.r4PcmFile(file));
        assertEquals(CdNativeFile.BOSSPCM,SonicCdProgramLoadServices.r4PcmFile(CdNativeFile.R43C));
        assertEquals(CdNativeFile.BOSSPCM,SonicCdProgramLoadServices.r4PcmFile(CdNativeFile.R43D));
        assertThrows(IllegalArgumentException.class,()->SonicCdProgramLoadServices.r4PcmFile(CdNativeFile.R31A));
        assertEquals(SonicCdProgramLoadServices.TimerInterrupt.UNKNOWN,unknown().timerInterrupt());
    }

    @Nested class ActualUsa {
        static CdTimeWarpPrograms r1;
        static com.openggf.game.soniccd.media.CdR3PcmSource r3;
        static CdR4PcmSource first, second;

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
                r3 = com.openggf.game.soniccd.media.CdR3PcmSource.fromPrograms(p);
                first = CdR4PcmSource.fromPrograms(p);
                second = CdR4PcmSource.fromPrograms(p);
            }
            assertNotSame(first, second);
            assertNotSame(first.bytes(), second.bytes());
            assertEquals(first.source(), second.source());
        }

        @Test void unknownStopsAfterCddaBeforeSelectorAndCannotRetry() {
            var before = unknown();
            var error = assertThrows(Unsupported.class, () -> loadLevelR4Pcm(before, first));
            var state = error.completedPrefix();
            assertEquals(Boundary.PCM_KNOWLEDGE_UNKNOWN, error.boundary());
            assertEquals(SonicCdProgramLoadServices.TimerInterrupt.DISABLED,state.timerInterrupt());
            assertEquals(LoadPrefix.CDDA_MASTER_WRITTEN, state.lastLoad().prefix());
            assertEquals(first.source(), state.lastLoad().requiredR4Source().orElseThrow());
            assertTrue(state.lastLoad().requiredSource().isEmpty());
            assertEquals(0x0380, state.musicSetting().orElseThrow());
            assertEquals(0x8380, state.masterSetting().orElseThrow());
            assertTrue(state.installed().isEmpty());
            assertTrue(state.requestedSelector().isEmpty());
            assertTrue(before.musicSetting().isEmpty());
            validateWithR4ForCapture(state, r1, r3, first);
            assertThrows(IllegalArgumentException.class, () -> validateForCapture(state, r1));
            var retry = assertThrows(Unsupported.class, () -> loadLevelR4Pcm(state, first));
            assertEquals(Boundary.PCM_REQUEST_INCOMPLETE, retry.boundary());
            assertSame(state, retry.completedPrefix());
        }

        @Test void originalR4InstallationReadsTypedInitialVariablesAndNeverBorrowsR1() {
            var state = loadLevelR4Pcm(developmentKnownAbsent(), first);
            assertEquals(SonicCdProgramLoadServices.TimerInterrupt.ENABLED,state.timerInterrupt());
            var installed = state.installed().orElseThrow();
            var variables = installed.variables();
            assertEquals(CdNativeFile.R4PCM, installed.driver().file());
            assertEquals(first.source(), installed.driver().r4Source().orElseThrow());
            assertThrows(IllegalStateException.class, () -> installed.driver().source());
            assertSame(first.bytes(), installed.driver().originalSource());
            assertEquals(201562, installed.driver().originalSource().length());
            assertEquals(CdNativeFile.R4PCM, state.requestedSelector().orElseThrow().file());
            assertEquals(first.source(), state.requestedSelector().orElseThrow().r4Source().orElseThrow());
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
            validateWithR4ForCapture(state, r1, r3, first);
            assertThrows(IllegalArgumentException.class, () -> validateForCapture(state, r1));
        }

        @Test void realBankMismatchReplacesSourceAndVariablesInBothDirections() {
            var old = requestPastMusic(loadLevelR1Pcm(developmentKnownAbsent(), r1));
            var oldDriver = old.installed().orElseThrow().driver();
            var state = loadLevelR4Pcm(old, first);
            assertNotSame(oldDriver, state.installed().orElseThrow().driver());
            assertSame(first.bytes(), state.installed().orElseThrow().driver().originalSource());
            assertTrue(state.lastPastWrite().isEmpty());
            assertEquals(0, state.installed().orElseThrow().variables().queue().slot0());
            assertEquals(0x81, old.installed().orElseThrow().variables().queue().slot0());
            assertEquals(CdNativeFile.R1PCM, oldDriver.file());
            assertSame(r1.r1Pcm(), oldDriver.originalSource());
            validateWithR4ForCapture(old, r1, r3, first);
            var back = loadLevelR1Pcm(state, r1);
            assertEquals(CdNativeFile.R1PCM, back.installed().orElseThrow().driver().file());
            assertTrue(back.installed().orElseThrow().driver().r4Source().isEmpty());
            assertTrue(back.lastLoad().requiredR4Source().isEmpty());
            validateForCapture(back, r1);
        }

        @Test void matchingR4ReusesExactInstalledReferencesAfterEquivalentSourceRead() {
            var old = requestPastMusic(loadLevelR4Pcm(developmentKnownAbsent(), first));
            var state = loadLevelR4Pcm(old, second);
            assertEquals(LoadPrefix.MATCH_REUSED, state.lastLoad().prefix());
            assertEquals(SonicCdProgramLoadServices.TimerInterrupt.ENABLED,state.timerInterrupt());
            assertSame(old.installed().orElseThrow(), state.installed().orElseThrow());
            assertSame(old.installed().orElseThrow().variables(), state.installed().orElseThrow().variables());
            assertSame(old.requestedSelector().orElseThrow(), state.requestedSelector().orElseThrow());
            assertSame(old.lastPastWrite().orElseThrow(), state.lastPastWrite().orElseThrow());
            assertSame(first.bytes(), state.installed().orElseThrow().driver().originalSource());
            assertEquals(0x81, state.installed().orElseThrow().variables().queue().slot0());
            validateWithR4ForCapture(state, r1, r3, second);
        }

        @Test void sourceDrivenValueReplayEqualsWithoutAliasingDriverInstances() {
            var state = loadLevelR4Pcm(developmentKnownAbsent(), first);
            var other = loadLevelR4Pcm(developmentKnownAbsent(), second);
            assertNotSame(state.installed().orElseThrow().driver(), other.installed().orElseThrow().driver());
            assertEquals(state, other);
            assertEquals(state.hashCode(), other.hashCode());
            var firstStop = assertThrows(Unsupported.class, () -> loadLevelR4Pcm(unknown(), first)).completedPrefix();
            var secondStop = assertThrows(Unsupported.class, () -> loadLevelR4Pcm(unknown(), second)).completedPrefix();
            assertEquals(firstStop, secondStop);
            assertEquals(firstStop.hashCode(), secondStop.hashCode());
            assertNotEquals(state, loadLevelR1Pcm(developmentKnownAbsent(), r1));
        }

        @Test void r3AndR4RemainDistinctEvenWhenVariableTemplatesAndDiscAreEqual() {
            var old=SonicCdProgramLoadServices.loadLevelR3Pcm(developmentKnownAbsent(),r3);
            var next=loadLevelR4Pcm(old,first);
            assertNotSame(old.installed().orElseThrow().driver(),next.installed().orElseThrow().driver());
            assertEquals(CdNativeFile.R4PCM,next.requestedSelector().orElseThrow().file());
            assertThrows(IllegalArgumentException.class,()->validateWithR3ForCapture(next,r1,r3));
            var back=SonicCdProgramLoadServices.loadLevelR3Pcm(next,r3);
            assertEquals(old,back);
            validateWithR4ForCapture(back,r1,r3,first);
        }

        @Test void legacyR1HashFormulasAndCaptureRemainUnchanged() {
            var state = loadLevelR1Pcm(developmentKnownAbsent(), r1);
            assertEquals(Objects.hash(r1.source()), state.installed().orElseThrow().driver().hashCode());
            assertEquals(Objects.hash(r1.source()), state.requestedSelector().orElseThrow().hashCode());
            assertEquals(Objects.hash(r1.source(), LoadPrefix.SOURCE_INSTALLED, null), state.lastLoad().hashCode());
            assertEquals(r1.source(), state.installed().orElseThrow().driver().source());
            assertEquals(r1.source(), state.requestedSelector().orElseThrow().source());
            validateForCapture(state, r1);
            validateWithR4ForCapture(state, r1, r3, first);
        }
    }
}
