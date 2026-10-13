package com.openggf.mods.mutators;

import java.util.List;

/** Native semantic timing, independent of creator catalogue labels or requested duration. */
final class TemporaryPolicyTiming {
    private TemporaryPolicyTiming() { }
    static String rejection(List<MutatorPolicy> policies,long duration) {
        boolean instant=policies.stream().anyMatch(TemporaryPolicyTiming::oneShot);
        boolean timed=policies.stream().anyMatch(policy -> !oneShot(policy));
        if(instant && timed)return "A temporary bundle cannot mix instantaneous and timed policies";
        if(instant && duration!=0)return "Instantaneous native policies require duration zero and acknowledgement";
        if(timed && duration==0)return "Timed policies require a positive bounded presentation duration";
        return "";
    }
    private static boolean oneShot(MutatorPolicy policy) {
        return switch(policy) {
            case PlayerMotionPolicy value -> value.mode()==PlayerMotionPolicy.Mode.MOMENTUM_PULSE || value.mode()==PlayerMotionPolicy.Mode.GROUND_STOP;
            case EncounterPolicy value -> value.kind()==EncounterPolicy.Kind.BUMPER || value.kind()==EncounterPolicy.Kind.GUST || value.kind()==EncounterPolicy.Kind.FAKE_MONITOR;
            case EnemyRulePolicy value -> value.kind()==EnemyRulePolicy.Kind.BUDDING;
            case NativePowerupPolicy value -> value.kind()==NativePowerupPolicy.Kind.SHIELD;
            case RingEconomyPolicy value -> value.kind()==RingEconomyPolicy.Kind.BALANCE;
            case RelocationPolicy value -> switch(value.kind()) {
                case RANDOM_CHECKPOINT,LAST_CHECKPOINT,BLINK,TEAM_SWAP -> true;
                case DELAYED_RETURN,STEPS -> false;
            };
            default -> false;
        };
    }
}
