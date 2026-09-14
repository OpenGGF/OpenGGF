package com.openggf.game;

/**
 * Internal presentation policy for bonus coordinators with stage-owned player
 * priority. Providers without this policy retain the ordinary high-priority
 * bonus-player enforcement. This is not part of the Mod API.
 */
public interface BonusStagePlayerPriorityPolicy {
    boolean shouldForcePlayerHighPriority();
}
