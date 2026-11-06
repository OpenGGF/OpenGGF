package com.openggf.game.rewind;

import com.openggf.game.GameMode;

import java.util.HashSet;
import java.util.Set;

/**
 * Player allowance, deliberately outside the rewind registry. Replaying gameplay
 * cannot restore spent time or re-award an already reached refill boundary.
 * Storage/history limits remain the rewind controller's separate responsibility.
 */
final class LiveRewindBudget {
    enum Scope {
        OFF, CHECKPOINT, ACT, ZONE;

        static Scope parse(String value) {
            return switch (value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)) {
                case "off" -> OFF;
                case "checkpoint" -> CHECKPOINT;
                case "zone" -> ZONE;
                default -> ACT;
            };
        }
    }

    private record Boundary(String game, int zone, int act, int checkpoint) { }
    private static final class Pool {
        int spentFrames;
        void refill() { spentFrames = 0; }
    }

    private final Pool main = new Pool();
    private Pool visit = new Pool();
    private final Set<Boundary> reached = new HashSet<>();
    private Object mainOwner;
    private Object visitIdentity;
    private GameMode mode = GameMode.LEVEL;
    private Scope scope = Scope.ACT;
    private int maximumFrames = 600;

    void configure(String scope, String seconds) { configure(scope, seconds, 60); }

    void configure(String scope, String seconds, int framesPerSecond) {
        Scope nextScope = Scope.parse(scope);
        if (nextScope != this.scope) reached.clear();
        this.scope = nextScope;
        maximumFrames = framesForSeconds(seconds, framesPerSecond);
    }

    static int framesForSeconds(String seconds) { return framesForSeconds(seconds, 60); }

    static int framesForSeconds(String seconds, int framesPerSecond) {
        if ("unlimited".equalsIgnoreCase(seconds)) return -1;
        try {
            long value = Long.parseLong(seconds);
            return value > 0 ? (int) (Math.min(Integer.MAX_VALUE / (long) framesPerSecond, value) * framesPerSecond) : 10 * framesPerSecond;
        } catch (NumberFormatException ignored) {
            return 10 * framesPerSecond;
        }
    }

    void enterMode(GameMode next, Object identity) {
        if (next == GameMode.BONUS_STAGE || next == GameMode.SPECIAL_STAGE) {
            if (mode != next || visitIdentity != identity) visit = new Pool();
            visitIdentity = identity;
        }
        mode = next;
    }

    void observeMain(Object owner, String game, int zone, int act, int checkpoint) {
        if (owner != mainOwner) {
            mainOwner = owner;
            reached.clear();
            main.refill();
        }
        if (scope == Scope.CHECKPOINT) {
            // Seed the act-start marker even when binding directly at a saved
            // checkpoint. A later death reload may clear that checkpoint; the
            // no-checkpoint sentinel is not a newly reached refill boundary.
            Boundary start = new Boundary(game, zone, act, -1);
            boolean fresh = reached.add(start);
            if (checkpoint >= 0) fresh |= reached.add(new Boundary(game, zone, act, checkpoint));
            if (fresh) main.refill();
            return;
        }
        Boundary boundary = switch (scope) {
            case ACT -> new Boundary(game, zone, act, -1);
            case ZONE -> new Boundary(game, zone, -1, -1);
            case CHECKPOINT, OFF -> null;
        };
        if (boundary != null && reached.add(boundary)) main.refill();
    }

    void startNewRun() {
        mainOwner = null;
        main.refill();
        reached.clear();
        visit = new Pool();
        visitIdentity = null;
        mode = GameMode.LEVEL;
    }

    void lifeLost(boolean refill) {
        if (refill) activePool().refill();
    }

    boolean off() { return scope == Scope.OFF; }
    boolean unlimited() { return maximumFrames < 0; }
    int maximumFrames() { return maximumFrames; }
    int remainingFrames() { return unlimited() ? -1 : Math.max(0, maximumFrames - activePool().spentFrames); }
    boolean empty() { return !unlimited() && remainingFrames() == 0; }

    int permittedSteps(int requested) {
        return unlimited() ? Math.max(0, requested) : Math.min(Math.max(0, requested), remainingFrames());
    }

    void spend(int completedFrames) {
        if (!unlimited() && completedFrames > 0) {
            Pool pool = activePool();
            pool.spentFrames = (int) Math.min(Integer.MAX_VALUE, (long) pool.spentFrames + completedFrames);
        }
    }

    private Pool activePool() {
        return mode == GameMode.BONUS_STAGE || mode == GameMode.SPECIAL_STAGE ? visit : main;
    }
}
