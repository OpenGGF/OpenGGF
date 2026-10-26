package com.openggf.game.soniccd;

import java.util.Objects;

/** Stage identity excludes demos and special-stage parameters. */
public sealed interface CdStageKey permits CdStageKey.Traversal, CdStageKey.Boss {
    CdZone zone();

    int actNumber();

    CdPeriod period();

    default String nativeId() {
        return "R" + zone().nativeRound() + actNumber() + period().payloadSuffix();
    }

    enum TraversalAct {
        ACT_1(1), ACT_2(2);

        private final int number;

        TraversalAct(int number) {
            this.number = number;
        }

        public int number() {
            return number;
        }
    }

    enum FutureOutcome {
        GOOD(CdPeriod.GOOD_FUTURE), BAD(CdPeriod.BAD_FUTURE);

        private final CdPeriod period;

        FutureOutcome(CdPeriod period) {
            this.period = period;
        }

        public CdPeriod period() {
            return period;
        }
    }

    record Traversal(CdZone zone, TraversalAct act, CdPeriod period) implements CdStageKey {
        public Traversal {
            Objects.requireNonNull(zone, "zone");
            Objects.requireNonNull(act, "act");
            Objects.requireNonNull(period, "period");
        }

        @Override
        public int actNumber() {
            return act.number();
        }
    }

    record Boss(CdZone zone, FutureOutcome future) implements CdStageKey {
        public Boss {
            Objects.requireNonNull(zone, "zone");
            Objects.requireNonNull(future, "future");
        }

        @Override
        public int actNumber() {
            return 3;
        }

        @Override
        public CdPeriod period() {
            return future.period();
        }
    }

    static CdStageKey parse(String nativeId) {
        Objects.requireNonNull(nativeId, "nativeId");
        if (!nativeId.matches("R[1345678][123][ABCD]")) {
            throw new IllegalArgumentException("Invalid CD stage ID: " + nativeId);
        }
        CdZone zone = CdZone.fromNativeRound(nativeId.charAt(1) - '0');
        CdPeriod period = CdPeriod.fromPayloadSuffix(nativeId.charAt(3));
        return switch (nativeId.charAt(2)) {
            case '1' -> new Traversal(zone, TraversalAct.ACT_1, period);
            case '2' -> new Traversal(zone, TraversalAct.ACT_2, period);
            case '3' -> switch (period) {
                case GOOD_FUTURE -> new Boss(zone, FutureOutcome.GOOD);
                case BAD_FUTURE -> new Boss(zone, FutureOutcome.BAD);
                default -> throw new IllegalArgumentException("Act 3 has only future payloads: " + nativeId);
            };
            default -> throw new IllegalArgumentException("Invalid CD stage ID: " + nativeId);
        };
    }
}
