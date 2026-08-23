package com.openggf.audio.smps;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable source-program timing for dialects whose YM busy polling spans
 * several managed helper calls.  Resolution is pure: it never reads or mutates
 * the live synthesizer.
 */
public final class YmSourceProgramTiming {
    public static final int MAX_WRITES = 31;
    public static final long MASTER_CYCLES_PER_YM_SAMPLE = 1_008;
    public static final int BUSY_YM_CYCLES_AFTER_WRITE = 47;
    public static final int BUSY_YM_CYCLES_PER_SAMPLE = 24;

    public enum FirstPathShape {
        VOICE_NOTE,
        VOICE_PAN_NOTE
    }

    public enum ProgramKind {
        S1_FM5_FIRST_VOICE_ATTACK
    }

    public record SourcePath(String citation) {
        public SourcePath {
            if (Objects.requireNonNull(citation, "citation").isBlank()) {
                throw new IllegalArgumentException("source citation cannot be blank");
            }
        }
    }

    public record ProgramVariant(int port, int carrierMask,
                                 FirstPathShape shape) {
        public ProgramVariant {
            if (port < 0 || port > 1) {
                throw new IllegalArgumentException("YM port must be 0 or 1");
            }
            if (carrierMask < 0 || (carrierMask & ~0xf) != 0) {
                throw new IllegalArgumentException("carrier mask must fit four operators");
            }
            Objects.requireNonNull(shape, "shape");
        }
    }

    public record ProgramWrite(
            YmServiceTimingProfile.SegmentKind section,
            int expectedPort,
            int expectedRegister,
            long fixedCyclesBeforeFirstStatusRead,
            long statusReadCycles,
            long takenBusyLoopCycles,
            long cyclesAfterReadyStatusToDataWrite,
            SourcePath sourcePath) {
        public ProgramWrite {
            Objects.requireNonNull(section, "section");
            Objects.requireNonNull(sourcePath, "sourcePath");
            if (expectedPort < 0 || expectedPort > 1) {
                throw new IllegalArgumentException("expected YM port must be 0 or 1");
            }
            if (expectedRegister < 0 || expectedRegister > 0xff) {
                throw new IllegalArgumentException("expected YM register must be a byte");
            }
            if (fixedCyclesBeforeFirstStatusRead < 0 || statusReadCycles < 0
                    || takenBusyLoopCycles < 0
                    || cyclesAfterReadyStatusToDataWrite < 0) {
                throw new IllegalArgumentException("source timing costs cannot be negative");
            }
            checkedTotal(fixedCyclesBeforeFirstStatusRead, statusReadCycles,
                    takenBusyLoopCycles, cyclesAfterReadyStatusToDataWrite);
        }
    }

    public record ProgramSection(YmServiceTimingProfile.SegmentKind kind,
                                 int firstWrite, int writeCount) {
        public ProgramSection {
            Objects.requireNonNull(kind, "kind");
            if (firstWrite < 0 || writeCount < 1
                    || firstWrite > MAX_WRITES - writeCount) {
                throw new IllegalArgumentException("program section span is invalid");
            }
        }
    }

    public record SourceProgram(ProgramKind kind, ProgramVariant variant,
                                List<ProgramWrite> writes,
                                List<ProgramSection> sections) {
        public SourceProgram {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(variant, "variant");
            writes = List.copyOf(Objects.requireNonNull(writes, "writes"));
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
            if (writes.isEmpty() || writes.size() > MAX_WRITES) {
                throw new IllegalArgumentException("source program must contain 1..31 writes");
            }
            if (sections.isEmpty()) {
                throw new IllegalArgumentException("source program requires sections");
            }
            int next = 0;
            Set<YmServiceTimingProfile.SegmentKind> kinds = new HashSet<>();
            for (ProgramSection section : sections) {
                if (section.firstWrite() != next) {
                    throw new IllegalArgumentException("program sections must be dense and ordered");
                }
                if (!kinds.add(section.kind())) {
                    throw new IllegalArgumentException("program section kind is duplicated");
                }
                int end = Math.addExact(section.firstWrite(), section.writeCount());
                for (int index = section.firstWrite(); index < end; index++) {
                    if (index >= writes.size() || writes.get(index).section() != section.kind()) {
                        throw new IllegalArgumentException("write does not belong to its section");
                    }
                }
                next = end;
            }
            if (next != writes.size()) {
                throw new IllegalArgumentException("program sections do not consume every write");
            }
            ProgramWrite first = writes.getFirst();
            if (first.fixedCyclesBeforeFirstStatusRead() != 0
                    || first.statusReadCycles() != 0
                    || first.takenBusyLoopCycles() != 0
                    || first.cyclesAfterReadyStatusToDataWrite() != 0) {
                throw new IllegalArgumentException("only a zero-cost row-zero anchor is supported");
            }
        }
    }

    public record VirtualYmBusyState(long appliedFrontierMasterCycle,
                                     int busyYmCyclesRemaining) {
        public VirtualYmBusyState {
            if (appliedFrontierMasterCycle < 0 || busyYmCyclesRemaining < 0
                    || busyYmCyclesRemaining > BUSY_YM_CYCLES_AFTER_WRITE) {
                throw new IllegalArgumentException("virtual YM busy state is invalid");
            }
        }
    }

    public record ProgramState(int nextWrite, VirtualYmBusyState busy,
                               long lastDueMasterCycle) {
        public ProgramState {
            if (nextWrite < 0 || nextWrite > MAX_WRITES) {
                throw new IllegalArgumentException("program write ordinal is invalid");
            }
            if (nextWrite == 0) {
                if (busy != null || lastDueMasterCycle != -1) {
                    throw new IllegalArgumentException("initial program state is inconsistent");
                }
            } else if (busy == null || lastDueMasterCycle < 0) {
                throw new IllegalArgumentException("active program state is incomplete");
            }
        }

        public static ProgramState initial() {
            return new ProgramState(0, null, -1);
        }

        public boolean complete(SourceProgram program) {
            return nextWrite == Objects.requireNonNull(program, "program").writes().size();
        }
    }

    public record ResolvedWrite(long dueMasterCycle, ProgramState nextState) {
        public ResolvedWrite {
            if (dueMasterCycle < 0) {
                throw new IllegalArgumentException("resolved due cycle cannot be negative");
            }
            Objects.requireNonNull(nextState, "nextState");
        }
    }

    public static final class YmSourceProgramResolver {
        private YmSourceProgramResolver() {
        }

        public static ResolvedWrite resolveNext(
                SourceProgram program,
                ProgramState state,
                YmServiceTimingProfile.SegmentKind actualSection,
                int actualPort,
                int actualRegister,
                long serviceCursorMasterCycle,
                long renderedYmFrontierMasterCycle) {
            Objects.requireNonNull(program, "program");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(actualSection, "actualSection");
            if (serviceCursorMasterCycle < 0 || renderedYmFrontierMasterCycle < 0
                    || renderedYmFrontierMasterCycle > serviceCursorMasterCycle) {
                throw new IllegalArgumentException("YM service/frontier cycles are invalid");
            }
            if (state.nextWrite() >= program.writes().size()) {
                throw new IllegalArgumentException("source program has no remaining write");
            }
            ProgramWrite write = program.writes().get(state.nextWrite());
            if (write.section() != actualSection || write.expectedPort() != actualPort
                    || write.expectedRegister() != actualRegister) {
                throw new IllegalArgumentException("hardware write differs from source program");
            }

            long due;
            VirtualYmBusyState nextBusy;
            if (state.nextWrite() == 0) {
                due = serviceCursorMasterCycle;
                nextBusy = afterDataWrite(due, renderedYmFrontierMasterCycle);
            } else {
                long statusCycle = Math.addExact(state.lastDueMasterCycle(),
                        write.fixedCyclesBeforeFirstStatusRead());
                BusyAtCycle observed = observeBusy(state.busy(), statusCycle);
                while (observed.busyYmCyclesRemaining() > 0) {
                    if (write.takenBusyLoopCycles() == 0) {
                        throw new IllegalArgumentException(
                                "busy source row has no positive taken-loop cost");
                    }
                    statusCycle = Math.addExact(statusCycle,
                            write.takenBusyLoopCycles());
                    observed = observeBusy(new VirtualYmBusyState(
                            observed.frontierMasterCycle(),
                            observed.busyYmCyclesRemaining()), statusCycle);
                }
                due = Math.addExact(statusCycle,
                        write.cyclesAfterReadyStatusToDataWrite());
                if (due <= state.lastDueMasterCycle()) {
                    throw new IllegalArgumentException("resolved due cycle did not advance");
                }
                nextBusy = afterDataWrite(due, observed.frontierMasterCycle());
            }
            return new ResolvedWrite(due, new ProgramState(
                    state.nextWrite() + 1, nextBusy, due));
        }

        private static BusyAtCycle observeBusy(VirtualYmBusyState state, long cycle) {
            if (cycle < state.appliedFrontierMasterCycle()) {
                return new BusyAtCycle(state.appliedFrontierMasterCycle(),
                        state.busyYmCyclesRemaining());
            }
            long elapsed = cycle - state.appliedFrontierMasterCycle();
            long samples = elapsed / MASTER_CYCLES_PER_YM_SAMPLE;
            long decrement = Math.multiplyExact(samples, BUSY_YM_CYCLES_PER_SAMPLE);
            int remaining = (int) Math.max(0,
                    (long) state.busyYmCyclesRemaining() - decrement);
            long frontier = Math.addExact(state.appliedFrontierMasterCycle(),
                    Math.multiplyExact(samples, MASTER_CYCLES_PER_YM_SAMPLE));
            return new BusyAtCycle(frontier, remaining);
        }

        private static VirtualYmBusyState afterDataWrite(long due, long phaseFrontier) {
            long applied = phaseFrontier;
            if (applied < due) {
                long distance = due - applied;
                long samples = Math.floorDiv(
                        Math.addExact(distance, MASTER_CYCLES_PER_YM_SAMPLE - 1),
                        MASTER_CYCLES_PER_YM_SAMPLE);
                applied = Math.addExact(applied,
                        Math.multiplyExact(samples, MASTER_CYCLES_PER_YM_SAMPLE));
            }
            return new VirtualYmBusyState(applied, BUSY_YM_CYCLES_AFTER_WRITE);
        }

        private record BusyAtCycle(long frontierMasterCycle,
                                   int busyYmCyclesRemaining) {
        }
    }

    private static long checkedTotal(long... values) {
        long total = 0;
        for (long value : values) {
            total = Math.addExact(total, value);
        }
        return total;
    }

    private YmSourceProgramTiming() {
    }
}
