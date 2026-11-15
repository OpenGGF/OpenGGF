package com.openggf.game.soniccd.objects.boss;

import com.openggf.game.soniccd.objects.CdObjectRegistration;

/**
 * One zone's boss: the object fragment the registry consults first on that zone's act 3 stages
 * ({@link CdBossObjects#bossStage}), its sheets, and the act 3 branch of the zone's level events
 * ({@code LevEvents_<zone>3}, Level/_Events.asm), which {@link CdBossLevelEvents} runs where
 * {@code RunLevelEvents} runs. A boss lane owns its fragment class and package only.
 */
public interface CdBossFragment extends CdObjectRegistration {
    /** The zone's act 3 level events, once per level frame; the default stage has none. */
    default void levelEvents(CdBossLevelEvents.Context context) { }

    /**
     * True once {@link #levelEvents} hosts the zone's act 3 branch of {@code RunLevelEvents}: the bottom-boundary
     * target ({@code destBottomBound}, the camera's max-Y target) is then whatever the boss and these events left,
     * and the shared tail ({@code CdStageEvents}) only moves the boundary toward it. False keeps the shared
     * fallback branch for the zone's act 3.
     */
    default boolean hostsLevelEvents() { return false; }
}
