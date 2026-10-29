package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Inactive pure semantic programme-file adoption and request values, not a module admission port.
 *
 * <p>Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * SPCmd_LoadLevel (CD System Program/SPX.asm:745-773), ResetCDDAVol (:1697-1709),
 * SPCmd_PlayPastMus (:1612-1614), Variables (Sound Drivers/PCM/_Driver.asm:33-34) and
 * pdrvOn/pdrvSndPlay/pdrvQueue/pdrvPtrOffset (_Variables.inc:68-81).
 * These ordinary finite values execute no CPU, BIOS, interrupt, bus or video protocol.
 *
 * <p>Installation consumes authenticated original file bytes and adopts their typed initial
 * variables. It does not execute UpdateDriver, InitDriver, queue arbitration or synthesis, mutate
 * the PCM chip, establish CDDA gain/output or certify native programme-load readiness. Current
 * module/World/Frame owners retain all invocation, phase, lifetime and exact-once authority.
 */
final class SonicCdProgramLoadServices {
    private static final int PCM_FILE_BYTES = 217866;
    private static final int VARIABLE_BASE = 0x18;
    private static final int VARIABLE_BYTES = 0x900;
    private static final int MUSIC_SETTING = 0x0380;
    private static final int MASTER_SETTING = 0x8380;
    private static final int PAST_MUSIC = 0x81;

    private SonicCdProgramLoadServices() { }

    enum PcmKnowledge { UNKNOWN, KNOWN_ABSENT, LOADED }
    enum SelectorKnowledge { UNKNOWN, CLEARED, SELECTED }
    enum LoadPrefix {
        NONE, CDDA_MUSIC_WRITTEN, CDDA_MASTER_WRITTEN, MATCH_REUSED,
        SELECTOR_WRITTEN, SOURCE_INSTALLED
    }
    enum Boundary {
        PCM_KNOWLEDGE_UNKNOWN, PCM_INSTALLATION_UNSUPPORTED,
        PCM_NOT_INSTALLED, PCM_REQUEST_INCOMPLETE
    }

    static State unknown() {
        return initial(PcmKnowledge.UNKNOWN, SelectorKnowledge.UNKNOWN);
    }

    /** Explicit earlier DEV initial condition only; this does not execute native SPX startup. */
    static State developmentKnownAbsent() {
        return initial(PcmKnowledge.KNOWN_ABSENT, SelectorKnowledge.CLEARED);
    }

    private static State initial(PcmKnowledge pcm, SelectorKnowledge selector) {
        return new State(pcm, null, selector, null, OptionalInt.empty(), OptionalInt.empty(),
                new LoadAttempt(null, LoadPrefix.NONE, null), null);
    }

    /**
     * The audio-value suffix following an actual level-file load, when its private owner invokes it.
     * Both semantic CDDA setting writes precede the actual installed-programme comparison.
     */
    static State loadLevelR1Pcm(State before, CdTimeWarpPrograms required) {
        Objects.requireNonNull(before, "programme-load value");
        Objects.requireNonNull(required, "actual authenticated programme bundle");
        requireNoIncompleteRequest(before);

        var music = new State(before.pcmKnowledge, before.installed, before.selectorKnowledge,
                before.requestedSelector, OptionalInt.of(MUSIC_SETTING), before.masterSetting,
                new LoadAttempt(required.source(), LoadPrefix.CDDA_MUSIC_WRITTEN, null), before.lastPastWrite);
        var reset = new State(music.pcmKnowledge, music.installed, music.selectorKnowledge,
                music.requestedSelector, music.musicSetting, OptionalInt.of(MASTER_SETTING),
                new LoadAttempt(required.source(), LoadPrefix.CDDA_MASTER_WRITTEN, null), music.lastPastWrite);

        // SPX:762-764 reads the actual current driver only after ResetCDDAVol returns.
        if (reset.pcmKnowledge == PcmKnowledge.UNKNOWN) {
            throw stopped(Boundary.PCM_KNOWLEDGE_UNKNOWN, reset);
        }
        if (reset.installed != null && sameSource(reset.installed.driver.source, required.source())) {
            return new State(reset.pcmKnowledge, reset.installed, reset.selectorKnowledge,
                    reset.requestedSelector, reset.musicSetting, reset.masterSetting,
                    new LoadAttempt(required.source(), LoadPrefix.MATCH_REUSED, null), reset.lastPastWrite);
        }

        // SPX:767 writes the selector before LoadFile:769. This prefix is not installed readiness.
        var selected = new State(reset.pcmKnowledge, reset.installed, SelectorKnowledge.SELECTED,
                new RequestedPcm(required.source()), reset.musicSetting, reset.masterSetting,
                new LoadAttempt(required.source(), LoadPrefix.SELECTOR_WRITTEN, null), reset.lastPastWrite);
        CdAssetBytes bytes = required.r1Pcm();
        if (bytes.length() != PCM_FILE_BYTES) {
            throw stopped(Boundary.PCM_INSTALLATION_UNSUPPORTED, selected);
        }
        // Variables (_Driver.asm:33-34) is original source data, never a mutable generic RAM bank.
        for (int offset = 0; offset < VARIABLE_BYTES; offset++) {
            if (bytes.unsignedByte(VARIABLE_BASE + offset) != 0) {
                throw stopped(Boundary.PCM_INSTALLATION_UNSUPPORTED, selected);
            }
        }
        var queue = new PcmQueue(bytes.unsignedByte(VARIABLE_BASE + 0xA),
                bytes.unsignedByte(VARIABLE_BASE + 0xB), bytes.unsignedByte(VARIABLE_BASE + 0xC),
                bytes.unsignedByte(VARIABLE_BASE + 0xD));
        var variables = new DriverVariables(bytes.unsignedByte(VARIABLE_BASE + 2),
                bytes.unsignedByte(VARIABLE_BASE + 9), queue, bytes.unsignedLong(VARIABLE_BASE + 0x10));
        var installed = new InstalledPcm(new DriverInstance(required.source(), bytes), variables);
        return new State(PcmKnowledge.LOADED, installed, selected.selectorKnowledge,
                selected.requestedSelector, selected.musicSetting, selected.masterSetting,
                new LoadAttempt(required.source(), LoadPrefix.SOURCE_INSTALLED, null), null);
    }

    /** SPCmd_PlayPastMus writes queue slot0; it does not process that queue or start a track. */
    static State requestPastMusic(State before) {
        Objects.requireNonNull(before, "programme-load value");
        requireNoIncompleteRequest(before);
        if (before.pcmKnowledge != PcmKnowledge.LOADED) {
            throw new Unsupported(Boundary.PCM_NOT_INSTALLED, before);
        }
        var old = before.installed;
        var variables = old.variables;
        var queue = variables.queue;
        var next = new DriverVariables(variables.on, variables.soundPlay,
                new PcmQueue(PAST_MUSIC, queue.slot1, queue.slot2, queue.slot3), variables.pointerOffset);
        return new State(before.pcmKnowledge, new InstalledPcm(old.driver, next),
                before.selectorKnowledge, before.requestedSelector, before.musicSetting, before.masterSetting,
                before.lastLoad, new PastMusicWrite(old.driver));
    }

    /**
     * Pure finite/source consistency only. Equal genuine bundles are the same data identity.
     * This cannot authenticate a World, phase, capture owner, private invocation or exact-once use.
     */
    static void validateForCapture(State candidate, CdTimeWarpPrograms sessionPrograms) {
        Objects.requireNonNull(candidate, "programme-load value");
        Objects.requireNonNull(sessionPrograms, "actual authenticated session programme data");
        candidate.validateShape();
        var source = sessionPrograms.source();
        if ((candidate.installed != null && !sameSource(candidate.installed.driver.source, source))
                || (candidate.requestedSelector != null && !sameSource(candidate.requestedSelector.source, source))
                || (candidate.lastLoad.requiredSource != null && !sameSource(candidate.lastLoad.requiredSource, source))) {
            throw new IllegalArgumentException("Programme-load value differs from the supplied source data");
        }
    }

    private static boolean sameSource(CdTimeWarpPrograms.Source first, CdTimeWarpPrograms.Source second) {
        // Only genuine CdTimeWarpPrograms can mint DriverInstance. Source records alone cannot.
        return first.equals(second);
    }

    private static void requireNoIncompleteRequest(State state) {
        if (state.lastLoad.stoppedAt != null || switch (state.lastLoad.prefix) {
            case NONE, MATCH_REUSED, SOURCE_INSTALLED -> false;
            default -> true;
        }) {
            throw new Unsupported(Boundary.PCM_REQUEST_INCOMPLETE, state);
        }
    }

    private static Unsupported stopped(Boundary boundary, State prefix) {
        var stopped = new State(prefix.pcmKnowledge, prefix.installed, prefix.selectorKnowledge,
                prefix.requestedSelector, prefix.musicSetting, prefix.masterSetting,
                new LoadAttempt(prefix.lastLoad.requiredSource, prefix.lastLoad.prefix, boundary), prefix.lastPastWrite);
        return new Unsupported(boundary, stopped);
    }

    static final class State {
        private final PcmKnowledge pcmKnowledge;
        private final InstalledPcm installed;
        private final SelectorKnowledge selectorKnowledge;
        private final RequestedPcm requestedSelector;
        private final OptionalInt musicSetting;
        private final OptionalInt masterSetting;
        private final LoadAttempt lastLoad;
        private final PastMusicWrite lastPastWrite;

        private State(PcmKnowledge pcmKnowledge, InstalledPcm installed, SelectorKnowledge selectorKnowledge,
                      RequestedPcm requestedSelector, OptionalInt musicSetting, OptionalInt masterSetting,
                      LoadAttempt lastLoad, PastMusicWrite lastPastWrite) {
            this.pcmKnowledge = Objects.requireNonNull(pcmKnowledge);
            this.installed = installed;
            this.selectorKnowledge = Objects.requireNonNull(selectorKnowledge);
            this.requestedSelector = requestedSelector;
            this.musicSetting = Objects.requireNonNull(musicSetting);
            this.masterSetting = Objects.requireNonNull(masterSetting);
            this.lastLoad = Objects.requireNonNull(lastLoad);
            this.lastPastWrite = lastPastWrite;
            validateShape();
        }

        PcmKnowledge pcmKnowledge() { return pcmKnowledge; }
        Optional<InstalledPcm> installed() { return Optional.ofNullable(installed); }
        SelectorKnowledge selectorKnowledge() { return selectorKnowledge; }
        Optional<RequestedPcm> requestedSelector() { return Optional.ofNullable(requestedSelector); }
        OptionalInt musicSetting() { return musicSetting; }
        OptionalInt masterSetting() { return masterSetting; }
        LoadAttempt lastLoad() { return lastLoad; }
        Optional<PastMusicWrite> lastPastWrite() { return Optional.ofNullable(lastPastWrite); }

        private void validateShape() {
            if ((pcmKnowledge == PcmKnowledge.LOADED) != (installed != null)
                    || (selectorKnowledge == SelectorKnowledge.SELECTED) != (requestedSelector != null)) {
                throw new IllegalArgumentException("Inconsistent programme installation or selector knowledge");
            }
            if (musicSetting.isPresent()) requireUnsigned(musicSetting.getAsInt(), 0xFFFF, "CDDA music setting");
            if (masterSetting.isPresent()) requireUnsigned(masterSetting.getAsInt(), 0xFFFF, "CDDA master setting");
            if (lastPastWrite != null && (installed == null || lastPastWrite.driver != installed.driver
                    || installed.variables.queue.slot0 != PAST_MUSIC)) {
                throw new IllegalArgumentException("Past request has no matching actual driver queue");
            }
            if (lastLoad.prefix == LoadPrefix.NONE) {
                if (lastLoad.requiredSource != null || lastLoad.stoppedAt != null || installed != null
                        || requestedSelector != null || lastPastWrite != null
                        || musicSetting.isPresent() || masterSetting.isPresent()
                        || (pcmKnowledge == PcmKnowledge.UNKNOWN) != (selectorKnowledge == SelectorKnowledge.UNKNOWN)) {
                    throw new IllegalArgumentException("Initial programme value contains invented completed work");
                }
                return;
            }
            if (lastLoad.requiredSource == null || musicSetting.orElse(-1) != MUSIC_SETTING
                    || (lastLoad.prefix != LoadPrefix.CDDA_MUSIC_WRITTEN
                        && masterSetting.orElse(-1) != MASTER_SETTING)) {
                throw new IllegalArgumentException("Programme request omits its source or ordered CDDA prefix");
            }
            if (lastLoad.prefix == LoadPrefix.MATCH_REUSED || lastLoad.prefix == LoadPrefix.SOURCE_INSTALLED) {
                if (lastLoad.stoppedAt != null || installed == null || requestedSelector == null
                        || !sameSource(installed.driver.source, lastLoad.requiredSource)
                        || !sameSource(requestedSelector.source, lastLoad.requiredSource)) {
                    throw new IllegalArgumentException("Completed programme request has no matching installation");
                }
            }
            if (lastLoad.prefix == LoadPrefix.SELECTOR_WRITTEN
                    && (requestedSelector == null || !sameSource(requestedSelector.source, lastLoad.requiredSource))) {
                throw new IllegalArgumentException("Pending programme selector is missing or inconsistent");
            }
            if (lastLoad.stoppedAt != null
                    && !((lastLoad.stoppedAt == Boundary.PCM_KNOWLEDGE_UNKNOWN
                            && lastLoad.prefix == LoadPrefix.CDDA_MASTER_WRITTEN
                            && pcmKnowledge == PcmKnowledge.UNKNOWN)
                        || (lastLoad.stoppedAt == Boundary.PCM_INSTALLATION_UNSUPPORTED
                            && lastLoad.prefix == LoadPrefix.SELECTOR_WRITTEN))) {
                throw new IllegalArgumentException("Unsupported boundary disagrees with its completed prefix");
            }
        }
        // Capture comparison is by represented value; existing module/Frame owners authenticate
        // lifetime and invocation. Equality never replays a source operation or grants authority.

        @Override public boolean equals(Object other) {
            return this == other || other instanceof State value
                    && pcmKnowledge == value.pcmKnowledge
                    && Objects.equals(installed, value.installed)
                    && selectorKnowledge == value.selectorKnowledge
                    && Objects.equals(requestedSelector, value.requestedSelector)
                    && Objects.equals(musicSetting, value.musicSetting)
                    && Objects.equals(masterSetting, value.masterSetting)
                    && Objects.equals(lastLoad, value.lastLoad)
                    && Objects.equals(lastPastWrite, value.lastPastWrite);
        }
        @Override public int hashCode() {
            return Objects.hash(pcmKnowledge, installed, selectorKnowledge, requestedSelector, musicSetting, masterSetting, lastLoad, lastPastWrite);
        }

    }

    static final class InstalledPcm {
        private final DriverInstance driver;
        private final DriverVariables variables;
        private InstalledPcm(DriverInstance driver, DriverVariables variables) {
            this.driver = Objects.requireNonNull(driver);
            this.variables = Objects.requireNonNull(variables);
        }
        DriverInstance driver() { return driver; }
        DriverVariables variables() { return variables; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof InstalledPcm value
                    && Objects.equals(driver, value.driver)
                    && Objects.equals(variables, value.variables);
        }
        @Override public int hashCode() {
            return Objects.hash(driver, variables);
        }

    }

    static final class DriverInstance {
        private final CdTimeWarpPrograms.Source source;
        private final CdAssetBytes originalSource;
        private DriverInstance(CdTimeWarpPrograms.Source source, CdAssetBytes originalSource) {
            this.source = Objects.requireNonNull(source);
            this.originalSource = Objects.requireNonNull(originalSource);
        }
        CdTimeWarpPrograms.Source source() { return source; }
        CdNativeFile file() { return CdNativeFile.R1PCM; }
        CdAssetBytes originalSource() { return originalSource; }
        // Private construction consumes a genuine authenticated bundle. Its complete-disc source
        // identity fixes R1PCM bytes; separate immutable views of that same data compare equal.
        // Native reuse still preserves this exact instance, checked with identity where required.

        @Override public boolean equals(Object other) {
            return this == other || other instanceof DriverInstance value
                    && Objects.equals(source, value.source);
        }
        @Override public int hashCode() {
            return Objects.hash(source);
        }

    }

    static final class DriverVariables {
        private final int on;
        private final int soundPlay;
        private final PcmQueue queue;
        private final long pointerOffset;
        private DriverVariables(int on, int soundPlay, PcmQueue queue, long pointerOffset) {
            requireUnsigned(on, 0xFF, "Driver on byte");
            requireUnsigned(soundPlay, 0xFF, "Driver sound-play byte");
            if (pointerOffset < 0 || pointerOffset > 0xFFFFFFFFL) {
                throw new IllegalArgumentException("Driver pointer offset is outside its unsigned long");
            }
            this.on = on;
            this.soundPlay = soundPlay;
            this.queue = Objects.requireNonNull(queue);
            this.pointerOffset = pointerOffset;
        }
        int on() { return on; }
        int soundPlay() { return soundPlay; }
        PcmQueue queue() { return queue; }
        long pointerOffset() { return pointerOffset; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof DriverVariables value
                    && on == value.on
                    && soundPlay == value.soundPlay
                    && Objects.equals(queue, value.queue)
                    && pointerOffset == value.pointerOffset;
        }
        @Override public int hashCode() {
            return Objects.hash(on, soundPlay, queue, pointerOffset);
        }

    }

    static final class PcmQueue {
        private final int slot0;
        private final int slot1;
        private final int slot2;
        private final int slot3;
        private PcmQueue(int slot0, int slot1, int slot2, int slot3) {
            requireUnsigned(slot0, 0xFF, "Driver queue0");
            requireUnsigned(slot1, 0xFF, "Driver queue1");
            requireUnsigned(slot2, 0xFF, "Driver queue2");
            requireUnsigned(slot3, 0xFF, "Driver queue3");
            this.slot0 = slot0;
            this.slot1 = slot1;
            this.slot2 = slot2;
            this.slot3 = slot3;
        }
        int slot0() { return slot0; }
        int slot1() { return slot1; }
        int slot2() { return slot2; }
        int slot3() { return slot3; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof PcmQueue value
                    && slot0 == value.slot0
                    && slot1 == value.slot1
                    && slot2 == value.slot2
                    && slot3 == value.slot3;
        }
        @Override public int hashCode() {
            return Objects.hash(slot0, slot1, slot2, slot3);
        }

    }

    static final class RequestedPcm {
        private final CdTimeWarpPrograms.Source source;
        private RequestedPcm(CdTimeWarpPrograms.Source source) { this.source = Objects.requireNonNull(source); }
        CdTimeWarpPrograms.Source source() { return source; }
        CdNativeFile file() { return CdNativeFile.R1PCM; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof RequestedPcm value
                    && Objects.equals(source, value.source);
        }
        @Override public int hashCode() {
            return Objects.hash(source);
        }

    }

    static final class LoadAttempt {
        private final CdTimeWarpPrograms.Source requiredSource;
        private final LoadPrefix prefix;
        private final Boundary stoppedAt;
        private LoadAttempt(CdTimeWarpPrograms.Source requiredSource, LoadPrefix prefix, Boundary stoppedAt) {
            this.requiredSource = requiredSource;
            this.prefix = Objects.requireNonNull(prefix);
            this.stoppedAt = stoppedAt;
        }
        Optional<CdTimeWarpPrograms.Source> requiredSource() { return Optional.ofNullable(requiredSource); }
        LoadPrefix prefix() { return prefix; }
        Optional<Boundary> stoppedAt() { return Optional.ofNullable(stoppedAt); }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof LoadAttempt value
                    && Objects.equals(requiredSource, value.requiredSource)
                    && prefix == value.prefix
                    && stoppedAt == value.stoppedAt;
        }
        @Override public int hashCode() {
            return Objects.hash(requiredSource, prefix, stoppedAt);
        }

    }

    static final class PastMusicWrite {
        private final DriverInstance driver;
        private PastMusicWrite(DriverInstance driver) { this.driver = Objects.requireNonNull(driver); }
        DriverInstance driver() { return driver; }
        int queueSlot() { return 0; }
        int value() { return PAST_MUSIC; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof PastMusicWrite value
                    && Objects.equals(driver, value.driver);
        }
        @Override public int hashCode() {
            return Objects.hash(driver);
        }

    }

    static final class Unsupported extends UnsupportedOperationException {
        private final Boundary boundary;
        private final State completedPrefix;
        private Unsupported(Boundary boundary, State completedPrefix) {
            super(boundary.name());
            this.boundary = boundary;
            this.completedPrefix = completedPrefix;
        }
        Boundary boundary() { return boundary; }
        State completedPrefix() { return completedPrefix; }
    }

    private static void requireUnsigned(int value, int maximum, String field) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(field + " is outside its unsigned field");
        }
    }
}
