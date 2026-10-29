package com.openggf.game.soniccd;

import java.util.Objects;
import java.util.Optional;

/**
 * Inactive, pure value operations for native level-program Start initialization.
 * J b25e62cb94a2f3d7599bb0330e323373fde5e2d0;
 * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Initialization.asm:56-80; Functions (General).asm:39-47.
 *
 * <p>The actual module owns entry admission, native order, synchronous adoption,
 * once-only execution, the optional scratch clear and actual InitVDP. These values
 * carry no invocation, completion or replay authority. No program, controller,
 * presentation, audio or hardware operation executes here.
 */
final class CdProgramInitialization {
    private CdProgramInitialization() { }

    enum KnownBit { UNKNOWN, CLEAR, SET }

    /** Equality knowledge for the source's initFlag LONG comparison, not raw marker bytes. */
    enum MarkerKnowledge { UNKNOWN, EQUALS_INIT, DIFFERS_FROM_INIT }

    /** One finite BYTE and its exact known-bit mask; unknown storage is canonical zero. */
    record VersionCache(int value, int knownBits) {
        VersionCache {
            if ((value & ~0xFF) != 0 || (knownBits & ~0xFF) != 0) {
                throw new IllegalArgumentException("Version cache value and knowledge mask must be BYTEs");
            }
            if ((value & ~knownBits) != 0) {
                throw new IllegalArgumentException("Unknown version cache bits require canonical zero storage");
            }
        }
    }

    /** Only the three retained source facts; no Frame, phase, clock or owner token. */
    record State(KnownBit controllerSetupBit, MarkerKnowledge initMarker, VersionCache versionCache) {
        State {
            Objects.requireNonNull(controllerSetupBit, "controller setup bit knowledge");
            Objects.requireNonNull(initMarker, "init marker equality knowledge");
            Objects.requireNonNull(versionCache, "version cache knowledge");
        }
    }

    /** Explicit native VERSION & C0 input, independent of the software/disc variant. */
    record ExecutionRegion(int versionC0) {
        ExecutionRegion {
            if ((versionC0 & ~0xC0) != 0) {
                throw new IllegalArgumentException("Execution region must be exactly 00,40,80 or C0");
            }
        }
    }

    /** Empty means unknown, not a default region; the input is required only when reached. */
    record ExecutionEnvironment(Optional<ExecutionRegion> region) {
        ExecutionEnvironment {
            Objects.requireNonNull(region, "explicit execution region knowledge");
        }
    }

    enum Branch { DO_INIT, GAME_INIT }
    enum Read { CONTROLLER_SETUP_BIT, INIT_MARKER, EXECUTION_REGION }

    /** An indeterminate reached source read; no state change or resume capability. */
    static final class UnknownRead extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final Read read;

        private UnknownRead(Read read) {
            super(switch (read) {
                case CONTROLLER_SETUP_BIT -> "Start (Initialization.asm:57): unknown controller setup bit";
                case INIT_MARKER -> "Start (Initialization.asm:59): unknown init marker comparison";
                case EXECUTION_REGION -> "Start (Initialization.asm:72): unknown explicit execution region";
            });
            this.read = read;
        }

        Read read() { return read; }
    }

    /** Explicit lack of knowledge; this is not native cold-boot or known-zero ingress. */
    static State unknown() {
        return new State(KnownBit.UNKNOWN, MarkerKnowledge.UNKNOWN, new VersionCache(0, 0));
    }

    /** Start:57-60. A clear controller bit skips the marker read completely. */
    static Branch selectStartBranch(State current) {
        Objects.requireNonNull(current, "initialization state");
        return switch (current.controllerSetupBit()) {
            case UNKNOWN -> throw new UnknownRead(Read.CONTROLLER_SETUP_BIT);
            case CLEAR -> Branch.DO_INIT;
            case SET -> switch (current.initMarker()) {
                case UNKNOWN -> throw new UnknownRead(Read.INIT_MARKER);
                case EQUALS_INIT -> Branch.GAME_INIT;
                case DIFFERS_FROM_INIT -> Branch.DO_INIT;
            };
        };
    }

    /** Start:72. The caller reaches this only after the selected .DoInit scratch clear. */
    static ExecutionRegion requireExecutionRegion(ExecutionEnvironment explicit) {
        Objects.requireNonNull(explicit, "explicit execution environment");
        return explicit.region().orElseThrow(() -> new UnknownRead(Read.EXECUTION_REGION));
    }

    /** Start:72-74. The full BYTE write makes all lower six bits known zero. */
    static State writeVersionCache(State current, ExecutionRegion reachedInput) {
        Objects.requireNonNull(current, "initialization state");
        Objects.requireNonNull(reachedInput, "reached execution region input");
        return new State(current.controllerSetupBit(), current.initMarker(),
                new VersionCache(reachedInput.versionC0(), 0xFF));
    }

    /** Start:76. Records the marker equality fact without changing either other fact. */
    static State writeInitMarker(State current) {
        Objects.requireNonNull(current, "initialization state");
        return new State(current.controllerSetupBit(), MarkerKnowledge.EQUALS_INIT, current.versionCache());
    }

    /**
     * Pure value counterpart of InitControllers (Functions (General).asm:39-47).
     * Sets only the fact Start later tests; it samples or clears no logical input.
     * Calling this method alone is not evidence of actual controller setup.
     */
    static State initializeControllers(State current) {
        Objects.requireNonNull(current, "initialization state");
        return new State(KnownBit.SET, current.initMarker(), current.versionCache());
    }
}
