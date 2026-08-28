package com.openggf.tools.audio.s3kparity;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSfxData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSfxData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded, comparison-only inventory model for authenticated S3K SMPS data.
 * Runtime playback must not consume this tooling model.
 */
public final class S3kSmpsReachabilityInventory {

    public static final InventoryLimits FIRST_SLICE_LIMITS =
            new InventoryLimits(131_072, 524_288, 16, 256);

    private S3kSmpsReachabilityInventory() {
    }

    public enum Dialect {
        LOCKED_ON_S3K_V4,
        STANDALONE_S3_V3_COMPAT;

        /** Returns true only for commands inventoried for this exact dialect. */
        public boolean ownsCommand(int command, int subcommand) {
            if (this != LOCKED_ON_S3K_V4 || command < 0xE0 || command > 0xFF) {
                return false;
            }
            return command != 0xFF || subcommand >= 0x00 && subcommand <= 0x07;
        }
    }

    public enum Status {
        EXACT,
        PARTIAL,
        MISSING,
        UNREACHABLE
    }

    public enum SourceBehavior {
        NORMAL,
        SHIPPED_BUG
    }

    public enum TimingStatus {
        EXACT,
        PARTIAL,
        UNAVAILABLE
    }

    public enum ExternalEvent {
        SERVICE_ENTRY,
        MUSIC_QUEUE,
        SFX_QUEUE,
        CONTINUOUS_UPDATE,
        CONTINUOUS_STOP,
        RING_SPEAKER_TOGGLE,
        PAUSE,
        FADE,
        JINGLE,
        STOP_ALL
    }

    public record InventoryLimits(
            int maxStates,
            int maxEdges,
            int maxCallDepth,
            int maxOverlayBytes) {

        private static final int ABSOLUTE_MAX_CALL_DEPTH = 16;
        private static final int ABSOLUTE_MAX_OVERLAY_BYTES = 256;
        private static final int ABSOLUTE_MAX_STATES = 131_072;
        private static final int ABSOLUTE_MAX_EDGES = 524_288;

        public InventoryLimits {
            if (maxStates <= 0 || maxStates > ABSOLUTE_MAX_STATES
                    || maxEdges <= 0 || maxEdges > ABSOLUTE_MAX_EDGES) {
                throw new IllegalArgumentException(
                        "inventory state/edge caps exceed the fixed tooling bounds");
            }
            if (maxCallDepth <= 0 || maxCallDepth > ABSOLUTE_MAX_CALL_DEPTH) {
                throw new IllegalArgumentException("inventory call-depth cap is outside 1..16");
            }
            if (maxOverlayBytes <= 0 || maxOverlayBytes > ABSOLUTE_MAX_OVERLAY_BYTES) {
                throw new IllegalArgumentException("inventory overlay cap is outside 1..256");
            }
        }
    }

    public record StreamRoot(
            Dialect dialect,
            String key,
            AbstractSmpsData data,
            int trackIndex,
            ExternalEvent event) {

        public StreamRoot {
            Objects.requireNonNull(dialect, "dialect");
            requireKey(key, "root key");
            Objects.requireNonNull(data, "data");
            if (trackIndex < 0) {
                throw new IllegalArgumentException("track index must be non-negative");
            }
            Objects.requireNonNull(event, "event");
        }
    }

    public record State(
            Dialect dialect,
            String rootKey,
            int bank,
            int pc,
            int trackType,
            List<Integer> callStack,
            Map<Integer, Integer> loopCounters,
            Map<String, Integer> sharedProjection,
            Map<Integer, Integer> overlay,
            ExternalEvent event) {

        public State {
            Objects.requireNonNull(dialect, "dialect");
            requireKey(rootKey, "root key");
            callStack = List.copyOf(callStack);
            loopCounters = Map.copyOf(loopCounters);
            sharedProjection = Map.copyOf(sharedProjection);
            overlay = Map.copyOf(overlay);
            Objects.requireNonNull(event, "event");
        }
    }

    public record Edge(
            int fromState,
            int toState,
            String kind,
            int sourcePc,
            String sourceCitation) {

        public Edge {
            if (fromState < 0 || toState < 0) {
                throw new IllegalArgumentException("edge state indexes must be non-negative");
            }
            requireKey(kind, "edge kind");
            requireKey(sourceCitation, "source citation");
        }
    }

    public record Frontier(int state, String reason, int sourcePc) {
        public Frontier {
            if (state < 0) {
                throw new IllegalArgumentException("frontier state index must be non-negative");
            }
            requireKey(reason, "frontier reason");
        }
    }

    public record Behavior(
            String key,
            Status status,
            SourceBehavior sourceBehavior,
            TimingStatus timingStatus,
            Set<String> roots,
            Set<String> trackTypes,
            String runtimeOwner,
            String sourceCitation,
            Set<String> evidenceIds) {

        public Behavior {
            requireKey(key, "behavior key");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(sourceBehavior, "sourceBehavior");
            Objects.requireNonNull(timingStatus, "timingStatus");
            roots = Set.copyOf(roots);
            trackTypes = Set.copyOf(trackTypes);
            requireKey(runtimeOwner, "runtime owner");
            requireKey(sourceCitation, "source citation");
            evidenceIds = Set.copyOf(evidenceIds);
        }
    }

    public record InventoryResult(
            List<State> states,
            List<Edge> edges,
            List<Frontier> frontiers,
            Set<Behavior> behaviors) {

        public InventoryResult {
            states = List.copyOf(states);
            edges = List.copyOf(edges);
            frontiers = List.copyOf(frontiers);
            behaviors = Set.copyOf(behaviors);
        }
    }

    /**
     * Decodes every track root in one authenticated program to a bounded fixed
     * point. Unknown or malformed edges remain explicit frontiers.
     */
    public static InventoryResult inventoryAll(
            Dialect dialect,
            String rootKey,
            AbstractSmpsData data,
            ExternalEvent event,
            InventoryLimits limits) {
        Objects.requireNonNull(dialect, "dialect");
        requireKey(rootKey, "root key");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(limits, "limits");
        return new Decoder(dialect, rootKey, data, event, limits).decode();
    }

    private static final class Decoder {
        private final Dialect dialect;
        private final String rootKey;
        private final AbstractSmpsData data;
        private final ExternalEvent event;
        private final InventoryLimits limits;
        private final byte[] bytes;
        private final int bankBase;
        private final boolean bankAddressed;
        private final List<State> states = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        private final List<Frontier> frontiers = new ArrayList<>();
        private final Set<Behavior> behaviors = new HashSet<>();
        private final Map<State, Integer> stateIndexes = new HashMap<>();
        private final ArrayDeque<State> work = new ArrayDeque<>();

        private Decoder(Dialect dialect, String rootKey, AbstractSmpsData data,
                ExternalEvent event, InventoryLimits limits) {
            this.dialect = dialect;
            this.rootKey = rootKey;
            this.data = data;
            this.event = event;
            this.limits = limits;
            if (data instanceof Sonic3kSmpsData music && music.getBankData() != null) {
                this.bytes = music.getBankData();
                this.bankBase = music.getBankZ80Base();
                this.bankAddressed = true;
            } else if (data instanceof Sonic3kSfxData) {
                this.bytes = data.getData();
                this.bankBase = data.getZ80StartAddress();
                this.bankAddressed = true;
            } else {
                this.bytes = data.getData();
                this.bankBase = data.getZ80StartAddress();
                this.bankAddressed = false;
            }
        }

        private InventoryResult decode() {
            List<RootPosition> roots = rootPositions();
            if (roots.isEmpty()) {
                frontiers.add(new Frontier(0, "program has no track roots", 0));
                return result();
            }
            for (RootPosition root : roots) {
                if (root.pc < 0 || root.pc >= bytes.length) {
                    frontiers.add(new Frontier(0,
                            "track root is outside authenticated program", root.pc));
                    continue;
                }
                addState(new State(dialect, rootKey + "#" + root.index, bankBase,
                        root.pc, root.trackType, List.of(), Map.of(), Map.of(), Map.of(), event),
                        -1, "root", root.pc, "SMPS header track pointer");
            }

            while (!work.isEmpty()) {
                State state = work.removeFirst();
                int from = stateIndexes.get(state);
                decodeState(from, state);
            }
            return result();
        }

        private InventoryResult result() {
            return new InventoryResult(states, edges, frontiers, behaviors);
        }

        private void decodeState(int from, State state) {
            int pos = state.pc();
            if (pos < 0 || pos >= bytes.length) {
                frontier(from, pos, "state reaches bank end");
                return;
            }
            int command = byteAt(state, pos);
            if (command < 0x80) {
                behavior("stream.duration", state, "SmpsSequencer",
                        "Z80 Sound Driver.asm:zTrackDurationUpdate");
                transition(from, state, pos + 1, state.callStack(), state.loopCounters(),
                        "duration", pos, "SMPS duration byte");
                return;
            }
            if (command < 0xE0) {
                behavior("stream.note", state, "SmpsSequencer",
                        "Z80 Sound Driver.asm:zGetNextNote");
                int next = pos + 1;
                if (next < bytes.length && byteAt(state, next) < 0x80) {
                    next++;
                }
                transition(from, state, next, state.callStack(), state.loopCounters(),
                        "note", pos, "SMPS note/duration");
                return;
            }

            behavior(commandKey(command), state, runtimeOwner(command), citation(command));
            switch (command) {
                case 0xE3, 0xF2 -> {
                    // Source terminal. No fall-through edge.
                }
                case 0xF6 -> pointerOnly(from, state, pos, 1, "jump");
                case 0xF7 -> loop(from, state, pos);
                case 0xF8 -> call(from, state, pos);
                case 0xF9 -> returnFromCall(from, state, pos);
                case 0xEB -> branchAndFallThrough(from, state, pos, 2, 4, "loop-exit");
                case 0xFC -> branchAndFallThrough(from, state, pos, 1, 3, "continuous");
                case 0xFF -> meta(from, state, pos);
                default -> {
                    int operands = parameterLength(command);
                    if (operands < 0) {
                        frontier(from, pos, "unknown S3K command 0x"
                                + Integer.toHexString(command));
                    } else {
                        transition(from, state, pos + 1 + operands, state.callStack(),
                                state.loopCounters(), "command", pos, citation(command));
                    }
                }
            }
        }

        private void pointerOnly(int from, State state, int pos, int pointerDelta,
                String kind) {
            int target = pointer(state, pos + pointerDelta);
            if (target < 0) {
                frontier(from, pos, kind + " target is outside authenticated program");
                return;
            }
            transition(from, state, target, state.callStack(), state.loopCounters(),
                    kind, pos, citation(byteAt(state, pos)));
        }

        private void call(int from, State state, int pos) {
            if (state.callStack().size() >= limits.maxCallDepth()) {
                frontier(from, pos, "call depth exceeds configured cap");
                return;
            }
            int target = pointer(state, pos + 1);
            if (target < 0) {
                frontier(from, pos, "call target is outside authenticated program");
                return;
            }
            List<Integer> stack = new ArrayList<>(state.callStack());
            stack.add(pos + 3);
            transition(from, state, target, stack, state.loopCounters(),
                    "call", pos, citation(0xF8));
        }

        private void returnFromCall(int from, State state, int pos) {
            if (state.callStack().isEmpty()) {
                behaviors.add(new Behavior("source-bug.return-underflow", Status.PARTIAL,
                        SourceBehavior.SHIPPED_BUG, TimingStatus.UNAVAILABLE,
                        Set.of(rootKey), Set.of(trackTypeName(state.trackType())),
                        "Sonic3kCoordFlagHandler", "Z80 Sound Driver.asm:cfJumpReturn",
                        Set.of()));
                return;
            }
            List<Integer> stack = new ArrayList<>(state.callStack());
            int target = stack.remove(stack.size() - 1);
            transition(from, state, target, stack, state.loopCounters(),
                    "return", pos, citation(0xF9));
        }

        private void loop(int from, State state, int pos) {
            if (pos + 4 >= bytes.length) {
                frontier(from, pos, "loop command is truncated");
                return;
            }
            int counter = byteAt(state, pos + 1);
            int count = byteAt(state, pos + 2);
            int target = pointer(state, pos + 3);
            if (target < 0) {
                frontier(from, pos, "loop target is outside authenticated program");
                return;
            }
            Map<Integer, Integer> branchCounters = new HashMap<>(state.loopCounters());
            branchCounters.put(counter, Math.max(0, count - 1));
            transition(from, state, target, state.callStack(), branchCounters,
                    "loop-target", pos, citation(0xF7));
            Map<Integer, Integer> exitCounters = new HashMap<>(state.loopCounters());
            exitCounters.put(counter, 0);
            transition(from, state, pos + 5, state.callStack(), exitCounters,
                    "loop-exit", pos, citation(0xF7));
        }

        private void branchAndFallThrough(int from, State state, int pos,
                int pointerDelta, int fallThroughDelta, String kind) {
            int target = pointer(state, pos + pointerDelta);
            if (target < 0) {
                frontier(from, pos, kind + " target is outside authenticated program");
            } else {
                transition(from, state, target, state.callStack(), state.loopCounters(),
                        kind + "-target", pos, citation(byteAt(state, pos)));
            }
            transition(from, state, pos + fallThroughDelta, state.callStack(),
                    state.loopCounters(), kind + "-fallthrough", pos,
                    citation(byteAt(state, pos)));
        }

        private void meta(int from, State state, int pos) {
            if (pos + 1 >= bytes.length) {
                frontier(from, pos, "FF command lacks subcommand");
                return;
            }
            int sub = byteAt(state, pos + 1);
            int operands = switch (sub) {
                case 0x00, 0x01, 0x02, 0x04 -> 1;
                case 0x03 -> 3;
                case 0x05 -> 4;
                case 0x06 -> 2;
                case 0x07 -> 0;
                default -> -1;
            };
            behavior("coord.ff." + String.format("%02x", sub), state,
                    "Sonic3kCoordFlagHandler", "Z80 Sound Driver.asm:cfMeta");
            if (operands < 0) {
                frontier(from, pos, "unknown S3K FF subcommand 0x"
                        + Integer.toHexString(sub));
                return;
            }
            Map<String, Integer> shared = state.sharedProjection();
            Map<Integer, Integer> overlay = state.overlay();
            int next = pos + 2 + operands;
            if (sub == 0x03) {
                int source = pointer(state, pos + 2);
                int count = byteAt(state, pos + 4);
                int destination = pos + 5;
                if (source < 0 || source + count > bytes.length
                        || destination + count > bytes.length) {
                    frontier(from, pos, "COPY_MEM source or destination is outside authenticated program");
                    return;
                }
                if (count > limits.maxOverlayBytes()
                        || overlay.size() + count > limits.maxOverlayBytes()) {
                    frontier(from, pos, "COPY_MEM overlay cap exceeded");
                    return;
                }
                shared = new HashMap<>(shared);
                shared.put("copy_mem_seen", 1);
                overlay = new HashMap<>(overlay);
                for (int index = 0; index < count; index++) {
                    int sourceOffset = source + index;
                    int value = byteAt(state, sourceOffset);
                    overlay.put(destination + index, value);
                }
                next = destination + count;
            }
            transition(from, new State(state.dialect(), state.rootKey(), state.bank(),
                            state.pc(), state.trackType(), state.callStack(),
                            state.loopCounters(), shared, overlay, state.event()),
                    next, state.callStack(), state.loopCounters(),
                    "meta", pos, "Z80 Sound Driver.asm:cfMeta");
        }

        private void transition(int from, State state, int target,
                List<Integer> callStack, Map<Integer, Integer> loopCounters,
                String kind, int sourcePc, String sourceCitation) {
            if (target < 0 || target >= bytes.length) {
                frontier(from, sourcePc, kind + " reaches bank end at 0x"
                        + Integer.toHexString(target));
                return;
            }
            State next = new State(state.dialect(), state.rootKey(), state.bank(), target,
                    state.trackType(), callStack, loopCounters, state.sharedProjection(),
                    state.overlay(), state.event());
            addState(next, from, kind, sourcePc, sourceCitation);
        }

        private void addState(State state, int from, String kind, int sourcePc,
                String sourceCitation) {
            if (from >= 0 && edges.size() >= limits.maxEdges()) {
                frontier(from, sourcePc, "edge cap exceeded");
                return;
            }
            Integer existing = stateIndexes.get(state);
            int target;
            if (existing != null) {
                target = existing;
            } else {
                if (states.size() >= limits.maxStates()) {
                    frontier(Math.max(from, 0), sourcePc, "state cap exceeded");
                    return;
                }
                target = states.size();
                states.add(state);
                stateIndexes.put(state, target);
                work.add(state);
            }
            if (from >= 0) {
                edges.add(new Edge(from, target, kind, sourcePc, sourceCitation));
            }
        }

        private void frontier(int state, int sourcePc, String reason) {
            frontiers.add(new Frontier(Math.max(state, 0), reason, sourcePc));
        }

        private int pointer(State state, int offset) {
            if (offset < 0 || offset + 1 >= bytes.length) {
                return -1;
            }
            int raw = byteAt(state, offset) | (byteAt(state, offset + 1) << 8);
            int relative = raw - bankBase;
            if (relative >= 0 && relative < bytes.length) {
                return relative;
            }
            return !bankAddressed && raw >= 0 && raw < bytes.length ? raw : -1;
        }

        private int byteAt(State state, int offset) {
            if (offset < 0 || offset >= bytes.length) {
                throw new IllegalArgumentException("program offset outside authenticated bytes: " + offset);
            }
            return state.overlay().getOrDefault(offset, bytes[offset] & 0xFF);
        }

        private List<RootPosition> rootPositions() {
            List<RootPosition> roots = new ArrayList<>();
            if (data instanceof SmpsSfxData sfx) {
                int index = 0;
                for (SmpsSfxData.SmpsSfxTrack track : sfx.getTrackEntries()) {
                    int type = (track.channelMask() & 0x80) != 0 ? 2
                            : (track.channelMask() == 0x10 || track.channelMask() == 0x16) ? 0 : 1;
                    roots.add(new RootPosition(index++, track.pointer(), type));
                }
                return roots;
            }
            int index = 0;
            if (data.getDacPointer() > 0) {
                roots.add(new RootPosition(index++, resolveRoot(data.getDacPointer()), 0));
            }
            for (int pointer : data.getFmPointers()) {
                if (pointer > 0) {
                    roots.add(new RootPosition(index++, resolveRoot(pointer), 1));
                }
            }
            for (int pointer : data.getPsgPointers()) {
                if (pointer > 0) {
                    roots.add(new RootPosition(index++, resolveRoot(pointer), 2));
                }
            }
            return roots;
        }

        private int resolveRoot(int pointer) {
            int relative = pointer - bankBase;
            if (relative >= 0 && relative < bytes.length) {
                return relative;
            }
            return pointer >= 0 && pointer < bytes.length ? pointer : -1;
        }

        private void behavior(String key, State state, String owner, String sourceCitation) {
            behaviors.add(new Behavior(key, Status.PARTIAL, SourceBehavior.NORMAL,
                    TimingStatus.UNAVAILABLE, Set.of(rootKey),
                    Set.of(trackTypeName(state.trackType())), owner, sourceCitation, Set.of()));
        }
    }

    private record RootPosition(int index, int pc, int trackType) {
    }

    private static String trackTypeName(int trackType) {
        return switch (trackType) {
            case 0 -> "DAC";
            case 1 -> "FM";
            case 2 -> "PSG";
            default -> "UNKNOWN";
        };
    }

    private static String commandKey(int command) {
        return switch (command) {
            case 0xE0 -> "coord.e0.pan";
            case 0xE8 -> "coord.e8.note-fill";
            case 0xEC -> "coord.ec.psg-volume";
            case 0xEF -> "coord.ef.voice";
            case 0xF0 -> "coord.f0.modulation";
            case 0xF1 -> "coord.f1.modulation-envelope";
            case 0xF2 -> "coord.f2.stop";
            case 0xF3 -> "coord.f3.psg-noise";
            case 0xF5 -> "coord.f5.psg-voice";
            case 0xF6 -> "coord.f6.jump";
            case 0xF7 -> "coord.f7.loop";
            case 0xF8 -> "coord.f8.call";
            case 0xF9 -> "coord.f9.return";
            case 0xFC -> "coord.fc.continuous";
            case 0xFF -> "coord.ff.meta";
            default -> "coord." + String.format("%02x", command);
        };
    }

    private static String runtimeOwner(int command) {
        return switch (command) {
            case 0xE8, 0xF0, 0xF1, 0xF3, 0xF5 -> "SmpsSequencer";
            default -> "Sonic3kCoordFlagHandler";
        };
    }

    private static String citation(int command) {
        return switch (command) {
            case 0xE0 -> "Z80 Sound Driver.asm:cfPanningAMSFMS";
            case 0xE8 -> "Z80 Sound Driver.asm:cfNoteFill";
            case 0xEC -> "Z80 Sound Driver.asm:cfChangePSGVolume";
            case 0xEF -> "Z80 Sound Driver.asm:cfSetVoice";
            case 0xF0 -> "Z80 Sound Driver.asm:cfModulation";
            case 0xF1 -> "Z80 Sound Driver.asm:cfModulationEnvelope";
            case 0xF2 -> "Z80 Sound Driver.asm:cfStopTrack";
            case 0xF3 -> "Z80 Sound Driver.asm:cfPSGform";
            case 0xF5 -> "Z80 Sound Driver.asm:cfSetPSGVoice";
            case 0xF6 -> "Z80 Sound Driver.asm:cfJumpTo";
            case 0xF7 -> "Z80 Sound Driver.asm:cfLoop";
            case 0xF8 -> "Z80 Sound Driver.asm:cfJumpToGosub";
            case 0xF9 -> "Z80 Sound Driver.asm:cfJumpReturn";
            case 0xFC -> "Z80 Sound Driver.asm:cfContinuousSFX";
            case 0xFF -> "Z80 Sound Driver.asm:cfMeta";
            default -> "Z80 Sound Driver.asm:coordflagLookup";
        };
    }

    private static int parameterLength(int command) {
        return switch (command) {
            case 0xE0, 0xE1, 0xE2, 0xE4, 0xE6, 0xE8, 0xEA, 0xEC, 0xED, 0xEF,
                    0xF3, 0xF4, 0xF5, 0xFB, 0xFD -> 1;
            case 0xE7, 0xE9, 0xFA -> 0;
            case 0xE5, 0xEE, 0xF1 -> 2;
            case 0xF0, 0xFE -> 4;
            default -> -1;
        };
    }

    private static void requireKey(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
}
