package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdR3PcmSource;
import com.openggf.game.soniccd.media.CdR4PcmSource;
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

    enum TimerInterrupt { UNKNOWN, DISABLED, ENABLED }
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

    // BEGIN authenticated original SPX initialization 20261107. A pure rule; actual invocation is private Boot.
    static State initializeOriginalSystem(State before,com.openggf.game.soniccd.media.CdSystemProgramSource source){
        Objects.requireNonNull(before);Objects.requireNonNull(source);
        if(!before.equals(unknown()))throw new IllegalStateException("Original system initialization requires its new unknown programme preimage");
        var clear=source.initializationClear();
        if(clear.instructionFileOffset()!=0x800||clear.targetAddress()!=0x7000||clear.byteCount()!=0x400
                ||clear.selectorOffset()!=0||clear.selectorBytes()!=4)throw new IllegalArgumentException("Original SPX clear relation differs");
        return initial(PcmKnowledge.KNOWN_ABSENT,SelectorKnowledge.CLEARED);
    }
    // END authenticated original SPX initialization.

    /** Explicit earlier DEV initial condition only; this does not execute native SPX startup. */
    static State developmentKnownAbsent() {
        return initial(PcmKnowledge.KNOWN_ABSENT, SelectorKnowledge.CLEARED);
    }

    private static State initial(PcmKnowledge pcm, SelectorKnowledge selector) {
        return new State(pcm, null, selector, null, OptionalInt.empty(), OptionalInt.empty(),
                new LoadAttempt(null, LoadPrefix.NONE, null), null, TimerInterrupt.UNKNOWN);
    }

    /**
     * The audio-value suffix following an actual level-file load, when its private owner invokes it.
     * Both semantic CDDA setting writes precede the actual installed-programme comparison.
     */
    static State loadLevelR1Pcm(State before, CdTimeWarpPrograms required) {
        Objects.requireNonNull(before, "programme-load value");
        Objects.requireNonNull(required, "actual authenticated programme bundle");
        return loadLevelPcm(before, new RequiredPcm(required, null, null));
    }

    // BEGIN R3PCM finite source choice, shared SPX suffix, no driver execution.
    static State loadLevelR3Pcm(State before, CdR3PcmSource required) {
        Objects.requireNonNull(before, "programme-load value");
        Objects.requireNonNull(required, "actual authenticated original R3PCM source");
        return loadLevelPcm(before, new RequiredPcm(null, required, null));
    }

    /** Literal SPX.PCMDrivers selection; BOSSPCM DATA/installation remains unqualified. */
    static CdNativeFile r4PcmFile(CdNativeFile programme) {
        return switch(Objects.requireNonNull(programme)) {
            case R41A,R41B,R41C,R41D,R42A,R42B,R42C,R42D -> CdNativeFile.R4PCM;
            case R43C,R43D -> CdNativeFile.BOSSPCM;
            default -> throw new IllegalArgumentException("Not an original R4 programme file");
        };
    }

    static State loadLevelR4Pcm(State before, CdR4PcmSource required) {
        Objects.requireNonNull(before, "programme-load value");
        Objects.requireNonNull(required, "actual authenticated original R4PCM source");
        return loadLevelPcm(before, new RequiredPcm(null, null, required));
    }

    /** Private finite choice; only genuine R1 or private-constructed R3/R4 custody enters. */
    private static final class RequiredPcm {
        private final CdTimeWarpPrograms r1;
        private final CdR3PcmSource r3;
        private final CdR4PcmSource r4;
        private RequiredPcm(CdTimeWarpPrograms r1, CdR3PcmSource r3, CdR4PcmSource r4) {
            if ((r1 == null ? 0 : 1) + (r3 == null ? 0 : 1) + (r4 == null ? 0 : 1) != 1) throw new IllegalArgumentException("One original PCM source choice");
            this.r1 = r1;
            this.r3 = r3;
            this.r4 = r4;
        }
        private Object key() { return r1 != null ? r1.source() : r3 != null ? r3.source() : r4.source(); }
        private CdAssetBytes bytes() { return r1 != null ? r1.r1Pcm() : r3 != null ? r3.bytes() : r4.bytes(); }
        private int fileBytes() { return r1 != null ? PCM_FILE_BYTES : r3 != null ? 242597 : 201562; }
        private LoadAttempt attempt(LoadPrefix prefix, Boundary stopped) {
            return new LoadAttempt(r1 != null ? r1.source() : null, r3 != null ? r3.source() : null, r4 != null ? r4.source() : null, prefix, stopped);
        }
        private RequestedPcm selector() {
            return new RequestedPcm(r1 != null ? r1.source() : null, r3 != null ? r3.source() : null, r4 != null ? r4.source() : null);
        }
        private DriverInstance driver(CdAssetBytes bytes) {
            return new DriverInstance(r1 != null ? r1.source() : null, r3 != null ? r3.source() : null, r4 != null ? r4.source() : null, bytes);
        }
    }

    private static State loadLevelPcm(State before, RequiredPcm required) {
        requireNoIncompleteRequest(before);
        var music = new State(before.pcmKnowledge, before.installed, before.selectorKnowledge,
                before.requestedSelector, OptionalInt.of(MUSIC_SETTING), before.masterSetting,
                required.attempt(LoadPrefix.CDDA_MUSIC_WRITTEN, null), before.lastPastWrite, before.timerInterrupt);
        var reset = new State(music.pcmKnowledge, music.installed, music.selectorKnowledge,
                music.requestedSelector, music.musicSetting, OptionalInt.of(MASTER_SETTING),
                required.attempt(LoadPrefix.CDDA_MASTER_WRITTEN, null), music.lastPastWrite, TimerInterrupt.DISABLED);
        // Both CDDA stores precede SPX758 timer-service disable, then comparison.
        // UNKNOWN retains that exact stopped prefix with service disabled; successful paths
        // re-enable service before returning Word RAM ownership. No hardware register is modeled.
        if (reset.pcmKnowledge == PcmKnowledge.UNKNOWN) {
            throw stopped(Boundary.PCM_KNOWLEDGE_UNKNOWN, reset);
        }
        if (reset.installed != null && reset.installed.driver.sourceKey().equals(required.key())) {
            return new State(reset.pcmKnowledge, reset.installed, reset.selectorKnowledge,
                    reset.requestedSelector, reset.musicSetting, reset.masterSetting,
                    required.attempt(LoadPrefix.MATCH_REUSED, null), reset.lastPastWrite, TimerInterrupt.ENABLED);
        }
        // SPX767 selects the filename before769 LoadFile. This is not InitDriver/UpdateDriver.
        var selected = new State(reset.pcmKnowledge, reset.installed, SelectorKnowledge.SELECTED,
                required.selector(), reset.musicSetting, reset.masterSetting,
                required.attempt(LoadPrefix.SELECTOR_WRITTEN, null), reset.lastPastWrite, reset.timerInterrupt);
        CdAssetBytes bytes = required.bytes();
        if (bytes.length() != required.fileBytes()) {
            throw stopped(Boundary.PCM_INSTALLATION_UNSUPPORTED, selected);
        }
        // All three independently authenticated original BOSS0 banks include the same literal $900-byte zero Variables template.
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
        var installed = new InstalledPcm(required.driver(bytes), variables);
        return new State(PcmKnowledge.LOADED, installed, selected.selectorKnowledge,
                selected.requestedSelector, selected.musicSetting, selected.masterSetting,
                required.attempt(LoadPrefix.SOURCE_INSTALLED, null), null, TimerInterrupt.ENABLED);
    }
    // END R3PCM finite source choice.

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
                before.lastLoad, new PastMusicWrite(old.driver), before.timerInterrupt);
    }

    /**
     * Pure finite/source consistency only. Equal genuine bundles are the same data identity.
     * This cannot authenticate a World, phase, capture owner, private invocation or exact-once use.
     */
    static void validateForCapture(State candidate, CdTimeWarpPrograms sessionPrograms) {
        Objects.requireNonNull(candidate, "programme-load value");
        Objects.requireNonNull(sessionPrograms, "actual authenticated session programme data");
        candidate.validateShape();
        // Legacy capture stays R1-only; R3/mixed prefixes use the explicitly supplied data pair.
        if ((candidate.installed != null && candidate.installed.driver.r3Source != null)
                || (candidate.requestedSelector != null && candidate.requestedSelector.r3Source != null)
                || candidate.lastLoad.r3Source != null
                || (candidate.installed != null && candidate.installed.driver.r4Source != null)
                || (candidate.requestedSelector != null && candidate.requestedSelector.r4Source != null)
                || candidate.lastLoad.r4Source != null) {
            throw new IllegalArgumentException("R3 source requires explicit capture data");
        }
        var source = sessionPrograms.source();
        if ((candidate.installed != null && !sameSource(candidate.installed.driver.source, source))
                || (candidate.requestedSelector != null && !sameSource(candidate.requestedSelector.source, source))
                || (candidate.lastLoad.requiredSource != null && !sameSource(candidate.lastLoad.requiredSource, source))) {
            throw new IllegalArgumentException("Programme-load value differs from the supplied source data");
        }
    }

    // BEGIN explicit R1/R3 finite capture consistency; no session or invocation authority.
    static void validateWithR3ForCapture(State candidate, CdTimeWarpPrograms sessionR1, CdR3PcmSource sessionR3) {
        Objects.requireNonNull(candidate, "programme-load value");
        Objects.requireNonNull(sessionR1, "actual retained R1 bundle");
        Objects.requireNonNull(sessionR3, "actual retained R3 source");
        candidate.validateShape();
        if (!sessionR1.source().wholeDiscSha256().equals(sessionR3.source().wholeDiscSha256())) {
            throw new IllegalArgumentException("PCM sources differ in complete-disc identity");
        }
        Object r1 = sessionR1.source(), r3 = sessionR3.source();
        for (Object key : new Object[]{candidate.installed == null ? null : candidate.installed.driver.sourceKey(),
                candidate.requestedSelector == null ? null : candidate.requestedSelector.sourceKey(),
                candidate.lastLoad.sourceKey()}) {
            if (key != null && !key.equals(r1) && !key.equals(r3)) {
                throw new IllegalArgumentException("Programme value differs from supplied original R1/R3 data");
            }
        }
    }
    // END explicit R1/R3 capture consistency.

    /** Value consistency only; the current Boot/Module still owns invocation and generation. */
    static void validateWithR4ForCapture(State candidate, CdTimeWarpPrograms sessionR1,
                                       CdR3PcmSource sessionR3, CdR4PcmSource sessionR4) {
        Objects.requireNonNull(candidate,"programme-load value");
        Objects.requireNonNull(sessionR1,"actual retained R1 bundle");
        Objects.requireNonNull(sessionR3,"actual retained R3 source");
        Objects.requireNonNull(sessionR4,"actual retained R4 source");
        candidate.validateShape();
        if(!sessionR1.source().wholeDiscSha256().equals(sessionR3.source().wholeDiscSha256())
                || !sessionR1.source().wholeDiscSha256().equals(sessionR4.source().wholeDiscSha256()))
            throw new IllegalArgumentException("PCM sources differ in complete-disc identity");
        for(Object key:new Object[]{candidate.installed==null?null:candidate.installed.driver.sourceKey(),
                candidate.requestedSelector==null?null:candidate.requestedSelector.sourceKey(),candidate.lastLoad.sourceKey()})
            if(key!=null && !key.equals(sessionR1.source()) && !key.equals(sessionR3.source()) && !key.equals(sessionR4.source()))
                throw new IllegalArgumentException("Programme value differs from supplied original PCM data");
    }

    private static boolean sameSource(CdTimeWarpPrograms.Source first, CdTimeWarpPrograms.Source second) {
        // Legacy R1 validation has already excluded R3. Source records alone cannot mint a driver.
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
                new LoadAttempt(prefix.lastLoad.requiredSource, prefix.lastLoad.r3Source, prefix.lastLoad.r4Source,
                        prefix.lastLoad.prefix, boundary), prefix.lastPastWrite, prefix.timerInterrupt);
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
        private final TimerInterrupt timerInterrupt;

        private State(PcmKnowledge pcmKnowledge, InstalledPcm installed, SelectorKnowledge selectorKnowledge,
                      RequestedPcm requestedSelector, OptionalInt musicSetting, OptionalInt masterSetting,
                      LoadAttempt lastLoad, PastMusicWrite lastPastWrite, TimerInterrupt timerInterrupt) {
            this.pcmKnowledge = Objects.requireNonNull(pcmKnowledge);
            this.installed = installed;
            this.selectorKnowledge = Objects.requireNonNull(selectorKnowledge);
            this.requestedSelector = requestedSelector;
            this.musicSetting = Objects.requireNonNull(musicSetting);
            this.masterSetting = Objects.requireNonNull(masterSetting);
            this.lastLoad = Objects.requireNonNull(lastLoad);
            this.lastPastWrite = lastPastWrite;
            this.timerInterrupt = Objects.requireNonNull(timerInterrupt);
            validateShape();
        }

        TimerInterrupt timerInterrupt() { return timerInterrupt; }
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
            if ((lastLoad.prefix == LoadPrefix.NONE && timerInterrupt != TimerInterrupt.UNKNOWN)
                    || ((lastLoad.prefix == LoadPrefix.MATCH_REUSED || lastLoad.prefix == LoadPrefix.SOURCE_INSTALLED)
                        && timerInterrupt != TimerInterrupt.ENABLED)
                    || ((lastLoad.prefix == LoadPrefix.CDDA_MASTER_WRITTEN || lastLoad.prefix == LoadPrefix.SELECTOR_WRITTEN)
                        && timerInterrupt != TimerInterrupt.DISABLED))
                throw new IllegalArgumentException("PCM timer interrupt disagrees with its source prefix");
            if (lastLoad.prefix == LoadPrefix.NONE) {
                if (lastLoad.sourceKey() != null || lastLoad.stoppedAt != null || installed != null
                        || requestedSelector != null || lastPastWrite != null
                        || musicSetting.isPresent() || masterSetting.isPresent()
                        || (pcmKnowledge == PcmKnowledge.UNKNOWN) != (selectorKnowledge == SelectorKnowledge.UNKNOWN)) {
                    throw new IllegalArgumentException("Initial programme value contains invented completed work");
                }
                return;
            }
            if (lastLoad.sourceKey() == null || musicSetting.orElse(-1) != MUSIC_SETTING
                    || (lastLoad.prefix != LoadPrefix.CDDA_MUSIC_WRITTEN
                        && masterSetting.orElse(-1) != MASTER_SETTING)) {
                throw new IllegalArgumentException("Programme request omits its source or ordered CDDA prefix");
            }
            if (lastLoad.prefix == LoadPrefix.MATCH_REUSED || lastLoad.prefix == LoadPrefix.SOURCE_INSTALLED) {
                if (lastLoad.stoppedAt != null || installed == null || requestedSelector == null
                        || !installed.driver.sourceKey().equals(lastLoad.sourceKey())
                        || !requestedSelector.sourceKey().equals(lastLoad.sourceKey())) {
                    throw new IllegalArgumentException("Completed programme request has no matching installation");
                }
            }
            if (lastLoad.prefix == LoadPrefix.SELECTOR_WRITTEN
                    && (requestedSelector == null || !requestedSelector.sourceKey().equals(lastLoad.sourceKey()))) {
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
                    && Objects.equals(lastPastWrite, value.lastPastWrite)
                    && timerInterrupt == value.timerInterrupt;
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
        private final CdR3PcmSource.Source r3Source;
        private final CdR4PcmSource.Source r4Source;
        private final CdAssetBytes originalSource;
        private DriverInstance(CdTimeWarpPrograms.Source source, CdAssetBytes originalSource) {
            this(Objects.requireNonNull(source), null, null, originalSource);
        }
        private DriverInstance(CdTimeWarpPrograms.Source source, CdR3PcmSource.Source r3Source,
                               CdR4PcmSource.Source r4Source, CdAssetBytes originalSource) {
            if ((source == null ? 0 : 1) + (r3Source == null ? 0 : 1) + (r4Source == null ? 0 : 1) != 1) throw new IllegalArgumentException("One PCM driver source");
            this.source = source;
            this.r3Source = r3Source;
            this.r4Source = r4Source;
            this.originalSource = Objects.requireNonNull(originalSource);
        }
        CdTimeWarpPrograms.Source source() {
            if (source == null) throw new IllegalStateException("R3 has an explicit r3Source identity");
            return source;
        }
        Optional<CdR3PcmSource.Source> r3Source() { return Optional.ofNullable(r3Source); }
        Optional<CdR4PcmSource.Source> r4Source() { return Optional.ofNullable(r4Source); }
        private Object sourceKey() { return source != null ? source : r3Source != null ? r3Source : r4Source; }
        CdNativeFile file() { return source != null ? CdNativeFile.R1PCM : r3Source != null ? CdNativeFile.R3PCM : CdNativeFile.R4PCM; }
        CdAssetBytes originalSource() { return originalSource; }
        // Private construction consumes a genuine authenticated bundle. Its complete-disc source
        // identity fixes the selected file; separate immutable views of that same data compare equal.
        // Native reuse still preserves this exact instance, checked with identity where required.

        @Override public boolean equals(Object other) {
            return this == other || other instanceof DriverInstance value
                    && Objects.equals(source, value.source) && Objects.equals(r3Source, value.r3Source) && Objects.equals(r4Source, value.r4Source);
        }
        @Override public int hashCode() {
            return r4Source != null ? Objects.hash(CdNativeFile.R4PCM, r4Source)
                    : r3Source == null ? Objects.hash(source) : Objects.hash(CdNativeFile.R3PCM, r3Source);
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
        private final CdR3PcmSource.Source r3Source;
        private final CdR4PcmSource.Source r4Source;
        private RequestedPcm(CdTimeWarpPrograms.Source source) { this(Objects.requireNonNull(source), null, null); }
        private RequestedPcm(CdTimeWarpPrograms.Source source, CdR3PcmSource.Source r3Source, CdR4PcmSource.Source r4Source) {
            if ((source == null ? 0 : 1) + (r3Source == null ? 0 : 1) + (r4Source == null ? 0 : 1) != 1) throw new IllegalArgumentException("One requested PCM source");
            this.source = source;
            this.r3Source = r3Source;
            this.r4Source = r4Source;
        }
        CdTimeWarpPrograms.Source source() {
            if (source == null) throw new IllegalStateException("R3 has an explicit r3Source identity");
            return source;
        }
        Optional<CdR3PcmSource.Source> r3Source() { return Optional.ofNullable(r3Source); }
        Optional<CdR4PcmSource.Source> r4Source() { return Optional.ofNullable(r4Source); }
        private Object sourceKey() { return source != null ? source : r3Source != null ? r3Source : r4Source; }
        CdNativeFile file() { return source != null ? CdNativeFile.R1PCM : r3Source != null ? CdNativeFile.R3PCM : CdNativeFile.R4PCM; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof RequestedPcm value
                    && Objects.equals(source, value.source) && Objects.equals(r3Source, value.r3Source) && Objects.equals(r4Source, value.r4Source);
        }
        @Override public int hashCode() {
            return r4Source != null ? Objects.hash(CdNativeFile.R4PCM, r4Source)
                    : r3Source == null ? Objects.hash(source) : Objects.hash(CdNativeFile.R3PCM, r3Source);
        }

    }

    static final class LoadAttempt {
        private final CdTimeWarpPrograms.Source requiredSource;
        private final CdR3PcmSource.Source r3Source;
        private final CdR4PcmSource.Source r4Source;
        private final LoadPrefix prefix;
        private final Boundary stoppedAt;
        private LoadAttempt(CdTimeWarpPrograms.Source requiredSource, LoadPrefix prefix, Boundary stoppedAt) {
            this(requiredSource, null, null, prefix, stoppedAt);
        }
        private LoadAttempt(CdTimeWarpPrograms.Source requiredSource, CdR3PcmSource.Source r3Source,
                            CdR4PcmSource.Source r4Source, LoadPrefix prefix, Boundary stoppedAt) {
            if ((requiredSource == null ? 0 : 1) + (r3Source == null ? 0 : 1) + (r4Source == null ? 0 : 1) > 1) throw new IllegalArgumentException("One load source identity");
            this.requiredSource = requiredSource;
            this.r3Source = r3Source;
            this.r4Source = r4Source;
            this.prefix = Objects.requireNonNull(prefix);
            this.stoppedAt = stoppedAt;
        }
        private Object sourceKey() { return requiredSource != null ? requiredSource : r3Source != null ? r3Source : r4Source; }
        Optional<CdR3PcmSource.Source> requiredR3Source() { return Optional.ofNullable(r3Source); }
        Optional<CdR4PcmSource.Source> requiredR4Source() { return Optional.ofNullable(r4Source); }
        Optional<CdTimeWarpPrograms.Source> requiredSource() { return Optional.ofNullable(requiredSource); }
        LoadPrefix prefix() { return prefix; }
        Optional<Boundary> stoppedAt() { return Optional.ofNullable(stoppedAt); }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof LoadAttempt value
                    && Objects.equals(requiredSource, value.requiredSource) && Objects.equals(r3Source, value.r3Source) && Objects.equals(r4Source, value.r4Source)
                    && prefix == value.prefix
                    && stoppedAt == value.stoppedAt;
        }
        @Override public int hashCode() {
            return r4Source != null ? Objects.hash(CdNativeFile.R4PCM, r4Source, prefix, stoppedAt)
                    : r3Source == null ? Objects.hash(requiredSource, prefix, stoppedAt)
                    : Objects.hash(CdNativeFile.R3PCM, r3Source, prefix, stoppedAt);
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
