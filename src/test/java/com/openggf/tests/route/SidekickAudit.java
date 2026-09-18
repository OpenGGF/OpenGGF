package com.openggf.tests.route;

import com.openggf.game.GameServices;
import com.openggf.game.rules.GameRules;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Arrays;
import java.util.List;

/**
 * Per-frame audit of the configured CPU sidekick team along a route.
 *
 * <p>CPU sidekick deaths are shipped behaviour ({@code Tails_CPU_Control}
 * returns a dead Tails through the game's respawn wait position, the
 * {@code sidekickDespawnX} of its {@code SidekickCpuRules}). The audited
 * contract is that every death is followed by that respawn within
 * {@code respawnLimit} frames, that the team is alive at the milestones the
 * caller names (boss entry, exit) and that identity, CPU ownership and the
 * leader chain never change. The first death is kept as evidence for
 * reports. Frames on which the sprite manager reports no sidekicks (a
 * zone-level CPU suppression window) are skipped rather than read as an
 * identity change.
 *
 * <p>Inputs: the live sidekick list from {@code GameServices.sprites()}, the
 * leader, and the caller's excused-window and milestone predicates. Origin:
 * extracted from the FBZ2 route controller
 * ({@code TestFbzAct2TraversalPreboss}) in commit 610464952, whose
 * {@code SIDEKICK_RESPAWN_LIMIT} cites the BK2 rows that set the limit.
 */
public final class SidekickAudit {
    private final int respawnLimit;
    private List<AbstractPlayableSprite> identityOrder;
    private int auditFrames;
    private boolean identityOrderPreserved = true;
    private boolean respawnedAfterEveryDeath = true;
    private int deaths;
    private int longestDeadStreak;
    private int[] deadStreaks = new int[0];
    private boolean diedDuringExcusedWindow;
    private boolean milestoneObserved;
    private boolean aliveAtMilestone = true;
    private String deathEvidence = "none";
    private boolean controllerEveryFrame = true;
    private boolean leaderChainEveryFrame = true;

    /** @param respawnLimit longest dead streak the contract tolerates, in frames */
    public SidekickAudit(int respawnLimit) {
        this.respawnLimit = respawnLimit;
    }

    /**
     * Audits one frame.
     *
     * @param player the leader
     * @param excusedWindow true while deaths are expected shipped behaviour
     *        (a window the caller identifies from live objects); deaths there
     *        are still counted but flagged separately
     * @param milestone true on the frame a named milestone is first live; the
     *        team's alive state is sampled once, on the first such frame
     * @param cameraX camera x for the evidence string
     */
    public void observe(AbstractPlayableSprite player, boolean excusedWindow,
                        boolean milestone, int cameraX) {
        List<AbstractPlayableSprite> current = GameServices.sprites().getSidekicks();
        auditFrames++;
        if (current.isEmpty()) {
            // Suppressed or solo: the frame is audited (nothing to compare),
            // and an empty list must not be mistaken for a team identity change.
            return;
        }
        if (identityOrder == null) {
            identityOrder = List.copyOf(current);
        }
        if (milestone && !milestoneObserved) {
            milestoneObserved = true;
            aliveAtMilestone = current.stream().noneMatch(AbstractPlayableSprite::getDead);
        }
        identityOrderPreserved &= current.size() == identityOrder.size();
        int comparable = Math.min(current.size(), identityOrder.size());
        if (deadStreaks.length < comparable) {
            deadStreaks = Arrays.copyOf(deadStreaks, comparable);
        }
        int playerX = player.getCentreX() & 0xFFFF;
        for (int index = 0; index < comparable; index++) {
            AbstractPlayableSprite sidekick = current.get(index);
            identityOrderPreserved &= sidekick == identityOrder.get(index);
            if (sidekick.getDead()) {
                if (deadStreaks[index] == 0) {
                    deaths++;
                }
                deadStreaks[index]++;
                longestDeadStreak = Math.max(longestDeadStreak, deadStreaks[index]);
                respawnedAfterEveryDeath &= deadStreaks[index] <= respawnLimit;
            } else {
                deadStreaks[index] = 0;
            }
            if (sidekick.getDead() && "none".equals(deathEvidence)) {
                deathEvidence = "auditFrame=" + auditFrames
                        + " sidekick[" + index + "]=($"
                        + Integer.toHexString(sidekick.getCentreX() & 0xFFFF) + ",$"
                        + Integer.toHexString(sidekick.getCentreY() & 0xFFFF) + ")"
                        + " player=($" + Integer.toHexString(playerX) + ",$"
                        + Integer.toHexString(player.getCentreY() & 0xFFFF) + ")"
                        + " camera=($" + Integer.toHexString(cameraX) + ")";
            }
            diedDuringExcusedWindow |= sidekick.getDead() && excusedWindow;
            controllerEveryFrame &= sidekick.isCpuControlled()
                    && sidekick.getCpuController() != null;
            AbstractPlayableSprite expectedLeader = index == 0
                    ? player : current.get(index - 1);
            leaderChainEveryFrame &= sidekick.getCpuController() != null
                    && sidekick.getCpuController().getLeader() == expectedLeader;
        }
    }

    /**
     * True while {@code sidekick} sits at its game's respawn wait position
     * ({@code SidekickCpuRules.sidekickDespawnX}), i.e. between a death or
     * off-screen removal and the fly-in that returns it.
     */
    public static boolean awaitingRespawn(AbstractPlayableSprite sidekick) {
        GameRules rules = sidekick.getGameRules();
        return rules != null && rules.sidekickCpu() != null
                && (sidekick.getCentreX() & 0xFFFF) == (rules.sidekickCpu().sidekickDespawnX() & 0xFFFF);
    }

    /** Every configured sidekick is alive and placed (not in the respawn wait) now. */
    public static boolean allAliveNow() {
        return GameServices.sprites().getSidekicks().stream().allMatch(sidekick ->
                !sidekick.getDead() && !awaitingRespawn(sidekick));
    }

    /** Every live sidekick is grounded within {@code range} of {@code playerX} and not in the respawn wait. */
    public static boolean gatheredBeside(int playerX, int range) {
        for (AbstractPlayableSprite sidekick : GameServices.sprites().getSidekicks()) {
            if (sidekick.getDead()) continue;
            int sidekickX = sidekick.getCentreX() & 0xFFFF;
            if (awaitingRespawn(sidekick)
                    || Math.abs(sidekickX - playerX) > range
                    || sidekick.getAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Frames until every live sidekick has carried its touch box past
     * {@code objectTouchRight}: its own ordinary RIGHT run from where it
     * stands plus {@code lagFrames} per chain link. The caller names the lag
     * its game's CPU follow uses (S3K {@code Tails_CPU_Control} loc_13DA6
     * follows the leader's Pos_table entry 17 frames back; the engine's
     * {@code SidekickCpuController} keeps that delay private). Dead or
     * far-trailing sidekicks respawn by fly-in and are not part of the ground
     * route.
     */
    public static int followAllowance(int objectTouchRight, int touchHalfWidth,
                                      int maxTrail, int lagFrames, int simulationLimit) {
        List<AbstractPlayableSprite> sidekicks = GameServices.sprites().getSidekicks();
        int allowance = 0;
        for (int index = 0; index < sidekicks.size(); index++) {
            AbstractPlayableSprite sidekick = sidekicks.get(index);
            if (sidekick.getDead()) continue;
            int sidekickX = sidekick.getCentreX() & 0xFFFF;
            int distance = objectTouchRight - (sidekickX - touchHalfWidth) + 1;
            if (distance <= 0 || distance > maxTrail) continue;
            allowance = Math.max(allowance,
                    RouteSteering.ordinaryRightCrossingBudget(sidekick, distance, simulationLimit)
                            + lagFrames * (index + 1));
        }
        return allowance;
    }

    public int auditFrames() { return auditFrames; }
    public boolean identityOrderPreserved() { return identityOrderPreserved; }
    public boolean respawnedAfterEveryDeath() { return respawnedAfterEveryDeath; }
    public int deaths() { return deaths; }
    public int longestDeadStreak() { return longestDeadStreak; }
    public boolean diedDuringExcusedWindow() { return diedDuringExcusedWindow; }
    public boolean aliveAtMilestone() { return aliveAtMilestone; }
    public String deathEvidence() { return deathEvidence; }
    public boolean controllerEveryFrame() { return controllerEveryFrame; }
    public boolean leaderChainEveryFrame() { return leaderChainEveryFrame; }
}
