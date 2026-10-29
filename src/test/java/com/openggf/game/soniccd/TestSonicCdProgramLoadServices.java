package com.openggf.game.soniccd;

import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static com.openggf.game.soniccd.SonicCdProgramLoadServices.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure semantic programme-file adoption and request values only. No module admission, native load
 * readiness, driver update, InitDriver, CDDA gain, chip clock or audible output is established here.
 * Installation positives opt in with actual complete USA media through soniccd.media.root.
 */
class TestSonicCdProgramLoadServices {
    @Test void unknownAndExplicitDevelopmentAbsenceDoNotInventAnInstalledProgramme() {
        var unknown = unknown();
        var absent = developmentKnownAbsent();
        assertEquals(PcmKnowledge.UNKNOWN, unknown.pcmKnowledge());
        assertEquals(SelectorKnowledge.UNKNOWN, unknown.selectorKnowledge());
        assertEquals(PcmKnowledge.KNOWN_ABSENT, absent.pcmKnowledge());
        assertEquals(SelectorKnowledge.CLEARED, absent.selectorKnowledge());
        for (var state : new State[]{unknown, absent}) {
            assertTrue(state.installed().isEmpty());
            assertTrue(state.requestedSelector().isEmpty());
            assertTrue(state.musicSetting().isEmpty());
            assertTrue(state.masterSetting().isEmpty());
            assertEquals(LoadPrefix.NONE, state.lastLoad().prefix());
            assertTrue(state.lastLoad().requiredSource().isEmpty());
            assertTrue(state.lastLoad().stoppedAt().isEmpty());
            assertTrue(state.lastPastWrite().isEmpty());
        }
    }

    @Test void pastMusicWithoutAnActualDriverRejectsWithoutChangingTheExactValue() {
        for (var state : new State[]{unknown(), developmentKnownAbsent()}) {
            var failure = assertThrows(Unsupported.class, () -> requestPastMusic(state));
            assertEquals(Boundary.PCM_NOT_INSTALLED, failure.boundary());
            assertSame(state, failure.completedPrefix());
            assertTrue(state.installed().isEmpty());
            assertTrue(state.lastPastWrite().isEmpty());
            assertEquals(LoadPrefix.NONE, state.lastLoad().prefix());
        }
    }

    @Test void equivalentInitialValuesHaveCompleteValueEqualityAndHashCodes() {
        var unknown = unknown(); var absent = developmentKnownAbsent();
        assertEquals(unknown, unknown());
        assertEquals(unknown.hashCode(), unknown().hashCode());
        assertEquals(absent, developmentKnownAbsent());
        assertEquals(absent.hashCode(), developmentKnownAbsent().hashCode());
        assertNotEquals(unknown, absent);
        assertNotEquals(unknown, null); assertNotEquals(unknown, "metadata");
    }

    @Test void missingArgumentsCannotMintSourceOrInstallationAuthority() {
        var state = developmentKnownAbsent();
        assertThrows(NullPointerException.class, () -> loadLevelR1Pcm(null, null));
        assertThrows(NullPointerException.class, () -> loadLevelR1Pcm(state, null));
        assertThrows(NullPointerException.class, () -> requestPastMusic(null));
        assertThrows(NullPointerException.class, () -> validateForCapture(null, null));
        assertThrows(NullPointerException.class, () -> validateForCapture(state, null));
        assertTrue(state.musicSetting().isEmpty());
        assertTrue(state.masterSetting().isEmpty());
        assertTrue(state.installed().isEmpty());
    }

    @Nested class ActualUsaSource {
        private static CdTimeWarpPrograms first;
        private static CdTimeWarpPrograms second;

        @BeforeAll static void prepareOnlyThroughActualCompleteDiscAdmission() throws Exception {
            String root = System.getProperty("soniccd.media.root");
            Assumptions.assumeTrue(root != null && !root.isBlank(),
                    "Set soniccd.media.root for real programme installation positives");
            Path cue;
            try (var files = Files.list(Path.of(root).resolve("USA"))) {
                var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
                assertEquals(1, cues.size());
                cue = cues.getFirst();
            }
            // Both disc owners have closed before a semantic install runs. No metadata or usable owner is mocked.
            first = CdStockLaunch.prepare(cue, CdStockLaunch.Request.r11a()).timeWarpPrograms().orElseThrow();
            second = CdStockLaunch.prepare(cue, CdStockLaunch.Request.r11a()).timeWarpPrograms().orElseThrow();
            assertNotSame(first, second);
            assertNotSame(first.r1Pcm(), second.r1Pcm());
            assertEquals(first.source(), second.source());
        }

        @Test void unknownComparisonStopsAfterBothExactCddaSettingsAndBeforeAnySelectorWrite() {
            var before = unknown();
            var failure = assertThrows(Unsupported.class, () -> loadLevelR1Pcm(before, first));
            assertEquals(Boundary.PCM_KNOWLEDGE_UNKNOWN, failure.boundary());
            var prefix = failure.completedPrefix();
            assertNotSame(before, prefix);
            assertEquals(0x0380, prefix.musicSetting().orElseThrow());
            assertEquals(0x8380, prefix.masterSetting().orElseThrow());
            assertEquals(LoadPrefix.CDDA_MASTER_WRITTEN, prefix.lastLoad().prefix());
            assertEquals(first.source(), prefix.lastLoad().requiredSource().orElseThrow());
            assertEquals(Boundary.PCM_KNOWLEDGE_UNKNOWN, prefix.lastLoad().stoppedAt().orElseThrow());
            assertEquals(PcmKnowledge.UNKNOWN, prefix.pcmKnowledge());
            assertEquals(SelectorKnowledge.UNKNOWN, prefix.selectorKnowledge());
            assertTrue(prefix.requestedSelector().isEmpty());
            assertTrue(prefix.installed().isEmpty());
            assertTrue(prefix.lastPastWrite().isEmpty());
            assertTrue(before.musicSetting().isEmpty());
            assertTrue(before.masterSetting().isEmpty());
            validateForCapture(prefix, first); // Finite/source consistency, not a live-World admission.
        }

        @Test void actualInstallationAdoptsSourceReadTypedZerosWithoutExecutingDriverInitialization() {
            var before = developmentKnownAbsent();
            var loaded = loadLevelR1Pcm(before, first);
            var installed = loaded.installed().orElseThrow();
            var driver = installed.driver();
            var variables = installed.variables();
            var bytes = first.r1Pcm();
            assertEquals(PcmKnowledge.LOADED, loaded.pcmKnowledge());
            assertEquals(SelectorKnowledge.SELECTED, loaded.selectorKnowledge());
            assertEquals(CdNativeFile.R1PCM, loaded.requestedSelector().orElseThrow().file());
            assertEquals(first.source(), loaded.requestedSelector().orElseThrow().source());
            assertEquals(LoadPrefix.SOURCE_INSTALLED, loaded.lastLoad().prefix());
            assertTrue(loaded.lastLoad().stoppedAt().isEmpty());
            assertEquals(first.source(), driver.source());
            assertEquals(CdNativeFile.R1PCM, driver.file());
            assertSame(bytes, driver.originalSource());
            assertEquals(217866, driver.originalSource().length());
            // N1ecc5f3a Variables (_Driver.asm:33-34), _Variables.inc:68-81: read actual file18+offset.
            assertEquals(bytes.unsignedByte(0x18 + 2), variables.on());
            assertEquals(bytes.unsignedByte(0x18 + 9), variables.soundPlay());
            assertEquals(bytes.unsignedByte(0x18 + 0xA), variables.queue().slot0());
            assertEquals(bytes.unsignedByte(0x18 + 0xB), variables.queue().slot1());
            assertEquals(bytes.unsignedByte(0x18 + 0xC), variables.queue().slot2());
            assertEquals(bytes.unsignedByte(0x18 + 0xD), variables.queue().slot3());
            assertEquals(bytes.unsignedLong(0x18 + 0x10), variables.pointerOffset());
            assertEquals(0, variables.on());
            assertEquals(0, variables.soundPlay());
            assertEquals(0, variables.pointerOffset());
            assertEquals(0x0380, loaded.musicSetting().orElseThrow());
            assertEquals(0x8380, loaded.masterSetting().orElseThrow());
            assertTrue(loaded.lastPastWrite().isEmpty());
            assertTrue(before.installed().isEmpty());
            assertTrue(before.musicSetting().isEmpty());
            validateForCapture(loaded, first);
        }

        @Test void realPastRequestWritesOnlyQueueZeroAndRetainsItsActualDriverInstance() {
            var loaded = loadLevelR1Pcm(developmentKnownAbsent(), first);
            var old = loaded.installed().orElseThrow();
            var queued = requestPastMusic(loaded);
            var installed = queued.installed().orElseThrow();
            assertNotSame(old, installed);
            assertSame(old.driver(), installed.driver());
            assertEquals(0x81, installed.variables().queue().slot0());
            assertEquals(old.variables().queue().slot1(), installed.variables().queue().slot1());
            assertEquals(old.variables().queue().slot2(), installed.variables().queue().slot2());
            assertEquals(old.variables().queue().slot3(), installed.variables().queue().slot3());
            assertEquals(old.variables().on(), installed.variables().on());
            assertEquals(old.variables().soundPlay(), installed.variables().soundPlay());
            assertEquals(old.variables().pointerOffset(), installed.variables().pointerOffset());
            assertEquals(0, old.variables().queue().slot0(), "immutable earlier state is not changed");
            var write = queued.lastPastWrite().orElseThrow();
            assertSame(installed.driver(), write.driver());
            assertEquals(0, write.queueSlot());
            assertEquals(0x81, write.value());
            assertTrue(loaded.lastPastWrite().isEmpty());
            assertSame(loaded.lastLoad(), queued.lastLoad());
            assertSame(loaded.requestedSelector().orElseThrow(), queued.requestedSelector().orElseThrow());
            var repeated = requestPastMusic(queued);
            assertSame(installed.driver(), repeated.installed().orElseThrow().driver());
            assertEquals(0x81, repeated.installed().orElseThrow().variables().queue().slot0());
            assertNotSame(write, repeated.lastPastWrite().orElseThrow());
            validateForCapture(queued, first);
        }

        @Test void matchingReusePreservesTheExactProgrammeVariablesAndPastWriteReferences() {
            var queued = requestPastMusic(loadLevelR1Pcm(developmentKnownAbsent(), first));
            var reused = loadLevelR1Pcm(queued, first);
            assertEquals(LoadPrefix.MATCH_REUSED, reused.lastLoad().prefix());
            assertTrue(reused.lastLoad().stoppedAt().isEmpty());
            assertSame(queued.installed().orElseThrow(), reused.installed().orElseThrow());
            assertSame(queued.installed().orElseThrow().driver(), reused.installed().orElseThrow().driver());
            assertSame(queued.installed().orElseThrow().variables(), reused.installed().orElseThrow().variables());
            assertSame(queued.lastPastWrite().orElseThrow(), reused.lastPastWrite().orElseThrow());
            assertSame(queued.requestedSelector().orElseThrow(), reused.requestedSelector().orElseThrow());
            assertEquals(0x81, reused.installed().orElseThrow().variables().queue().slot0());
            assertEquals(0x0380, reused.musicSetting().orElseThrow());
            assertEquals(0x8380, reused.masterSetting().orElseThrow());
            validateForCapture(reused, first);
        }

        @Test void separatelyAuthenticatedSameDiscBundlesCompareAsDataIdentityWithoutWorldAuthority() {
            var loaded = loadLevelR1Pcm(developmentKnownAbsent(), first);
            var reused = loadLevelR1Pcm(loaded, second);
            assertEquals(LoadPrefix.MATCH_REUSED, reused.lastLoad().prefix());
            assertSame(loaded.installed().orElseThrow(), reused.installed().orElseThrow());
            assertSame(first.r1Pcm(), reused.installed().orElseThrow().driver().originalSource());
            assertNotSame(second.r1Pcm(), reused.installed().orElseThrow().driver().originalSource());
            validateForCapture(loaded, second);
            validateForCapture(reused, first);
            // These values have no World/Frame/phase/exact-once authority. Only existing module owners supply it.
        }

        @Test void genuineSourceDrivenForwardReplayHasValueEqualityWithoutAliasingInstallations() {
            var restored = developmentKnownAbsent();
            var initial = requestPastMusic(loadLevelR1Pcm(restored, first));
            var replay = requestPastMusic(loadLevelR1Pcm(restored, second));
            assertNotSame(initial, replay);
            assertNotSame(initial.installed().orElseThrow().driver(), replay.installed().orElseThrow().driver());
            assertEquals(initial, replay);
            assertEquals(initial.hashCode(), replay.hashCode());
            var again = requestPastMusic(initial);
            assertSame(initial.installed().orElseThrow().driver(), again.installed().orElseThrow().driver());
            assertEquals(initial, again); assertEquals(initial.hashCode(), again.hashCode());
            assertNotEquals(initial, loadLevelR1Pcm(initial, first), "last completed load prefix remains part of the value");
            var stopped = assertThrows(Unsupported.class, () -> loadLevelR1Pcm(unknown(), first)).completedPrefix();
            var stoppedReplay = assertThrows(Unsupported.class, () -> loadLevelR1Pcm(unknown(), second)).completedPrefix();
            assertEquals(stopped, stoppedReplay); assertEquals(stopped.hashCode(), stoppedReplay.hashCode());
            assertNotEquals(stopped, unknown());
        }

        @Test void stoppedComparisonCannotBeRetriedOrUsedAsACompletedInstalledRequest() {
            var stopped = assertThrows(Unsupported.class, () -> loadLevelR1Pcm(unknown(), first)).completedPrefix();
            var retry = assertThrows(Unsupported.class, () -> loadLevelR1Pcm(stopped, first));
            assertEquals(Boundary.PCM_REQUEST_INCOMPLETE, retry.boundary());
            assertSame(stopped, retry.completedPrefix());
            var past = assertThrows(Unsupported.class, () -> requestPastMusic(stopped));
            assertEquals(Boundary.PCM_REQUEST_INCOMPLETE, past.boundary());
            assertSame(stopped, past.completedPrefix());
            assertEquals(LoadPrefix.CDDA_MASTER_WRITTEN, stopped.lastLoad().prefix());
            assertTrue(stopped.installed().isEmpty());
            assertTrue(stopped.lastPastWrite().isEmpty());
            validateForCapture(stopped, first); // A diagnostic value is not a healthy captured visit.
        }
    }
}
