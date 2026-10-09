package eggsky.core;

import com.openggf.mods.scene.SceneAudio;
import eggsky.game.Player;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** One composed system announcer: bounded queue, priorities, cooldowns and stale-warning removal. */
public final class Voice {
    private record Pending(VoiceLine line, long requested) { }
    private final List<Pending> queue = new ArrayList<>();
    private final EnumMap<VoiceLine, Long> lastPlayed = new EnumMap<>(VoiceLine.class);
    private boolean enabled = true;
    private long tick;
    private long busyUntil;
    private boolean hullLow;
    private boolean shieldEmpty;
    private boolean pulseLow;

    public boolean enabled() { return enabled; }

    public void enabled(boolean value) {
        enabled = value;
        if (!value) queue.clear();
    }

    public void reset() {
        queue.clear();
        lastPlayed.clear();
        hullLow = shieldEmpty = pulseLow = false;
        // A playing one-shot finishes; retain its lease to prevent overlap on a new expedition.
    }

    public void say(VoiceLine line) {
        if (!enabled || tick-lastPlayed.getOrDefault(line, Long.MIN_VALUE / 2) < line.cooldownTicks
                || queue.stream().anyMatch(p -> p.line == line)) return;
        if (line == VoiceLine.LIFE_EMPTY) cancel(VoiceLine.LIFE_LOW);
        if (line == VoiceLine.HAZARD_EMPTY) cancel(VoiceLine.HAZARD_LOW);
        if (isWanted(line)) queue.removeIf(p -> isWanted(p.line));
        if (queue.size() >= 8) {
            Pending lowest = queue.stream().min(java.util.Comparator.comparingInt(p -> p.line.priority)).orElseThrow();
            if (lowest.line.priority >= line.priority) return;
            queue.remove(lowest);
        }
        queue.add(new Pending(line, tick));
    }

    private static boolean isWanted(VoiceLine line) {
        return switch (line) {
            case WANTED_ONE, WANTED_TWO, WANTED_THREE, WANTED_FOUR, WANTED_FIVE, WANTED_CLEAR -> true;
            default -> false;
        };
    }

    public void cancel(VoiceLine line) { queue.removeIf(p -> p.line == line); }

    /** Called every scene tick, including menus, so confirmations finish without overlapping. */
    public void update(SceneAudio audio, long now) {
        tick = now;
        queue.removeIf(p -> now-p.requested > 900);
        if (!enabled || now < busyUntil || queue.isEmpty()) return;
        Pending next = queue.stream().max(java.util.Comparator.<Pending>comparingInt(p -> p.line.priority)
                .thenComparingLong(p -> -p.requested)).orElseThrow();
        queue.remove(next);
        lastPlayed.put(next.line, now);
        if (audio.playSfx(next.line.asset)) busyUntil = now+next.line.durationTicks+12;
    }

    /** Clear warnings as soon as their condition resolves; hysteresis keeps threshold chatter quiet. */
    public void vitals(Player p, boolean surface, boolean space, boolean live) {
        if (!surface || p.life > p.maxLife()*.30f) cancel(VoiceLine.LIFE_LOW);
        if (!surface || p.life > 0) cancel(VoiceLine.LIFE_EMPTY);
        if (!surface || p.hazard > p.maxHazard()*.30f) cancel(VoiceLine.HAZARD_LOW);
        if (!surface || p.hazard > 0) cancel(VoiceLine.HAZARD_EMPTY);
        if (p.launchFuel >= 25) cancel(VoiceLine.LAUNCH_EMPTY);
        if (p.hull > p.maxHull()*.35f) { hullLow = false; cancel(VoiceLine.HULL_CRITICAL); }
        if (!space || p.shipShield > p.maxShipShield()*.10f) { shieldEmpty = false; cancel(VoiceLine.SHIELD_EMPTY); }
        if (!space || p.pulse > 30) { pulseLow = false; cancel(VoiceLine.PULSE_LOW); }
        if (!live) return;
        if (p.hull > 0 && p.hull <= p.maxHull()*.25f && !hullLow) {
            hullLow = true;
            say(VoiceLine.HULL_CRITICAL);
        }
        if (space && p.shipShield <= 0 && !shieldEmpty) {
            shieldEmpty = true;
            say(VoiceLine.SHIELD_EMPTY);
        }
        if (space && p.pulse <= 20 && !pulseLow) {
            pulseLow = true;
            say(VoiceLine.PULSE_LOW);
        }
    }

    public void recharged(Player player, int which) {
        if (eggsky.game.Vitals.current(player, which) < eggsky.game.Vitals.max(player, which)-.5f) {
            say(VoiceLine.SYSTEM_RECHARGED);
            return;
        }
        say(switch (which) {
            case 0 -> VoiceLine.LIFE_RESTORED;
            case 1 -> VoiceLine.HAZARD_RESTORED;
            case 2 -> VoiceLine.LAUNCH_RESTORED;
            case 3 -> VoiceLine.HULL_REPAIRED;
            default -> VoiceLine.DEFLECTOR_RESTORED;
        });
    }
}
