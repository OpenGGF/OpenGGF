package com.openggf.game.soniccd.objects.boss;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdZone;

/**
 * The boss fragment of each zone. Every zone's act 3 links its own object index with the boss and
 * its parts (e.g. Palmtree Panic/Object Index (Act 3).asm:7-71), so on an act 3 stage the registry
 * resolves an ID through the zone's boss fragment before the zone fragment, and zone objects whose
 * IDs the boss reuses never spawn there. Each zone's fragment is a class in its own package
 * ({@code boss/<zone>}); the lanes that have not hosted their boss yet register nothing.
 */
public final class CdBossObjects {
    private CdBossObjects() { }

    /** The zone's boss fragment. */
    public static CdBossFragment fragment(CdZone zone) {
        return switch (zone) {
            case PALMTREE_PANIC -> new com.openggf.game.soniccd.objects.boss.ppz.PpzBossObjects();
            case COLLISION_CHAOS -> new com.openggf.game.soniccd.objects.boss.ccz.CczBossObjects();
            case TIDAL_TEMPEST -> new com.openggf.game.soniccd.objects.boss.ttz.TtzBossObjects();
            case QUARTZ_QUADRANT -> new com.openggf.game.soniccd.objects.boss.qqz.QqzBossObjects();
            case WACKY_WORKBENCH -> new com.openggf.game.soniccd.objects.boss.wwz.WwzBossObjects();
            case STARDUST_SPEEDWAY -> new com.openggf.game.soniccd.objects.boss.ssz.SszBossObjects();
            case METALLIC_MADNESS -> new com.openggf.game.soniccd.objects.boss.mmz.MmzBossObjects();
        };
    }

    /** True on an act 3 stage whose boss fragment hosts its level events ({@link CdBossFragment#hostsLevelEvents}). */
    public static boolean hostsLevelEvents(CdStagePayload payload) {
        return bossStage(payload) && fragment(payload.stage().zone()).hostsLevelEvents();
    }

    /** Act 3 (the Good and Bad Future boss stages, R?3C and R?3D). */
    public static boolean bossStage(CdStagePayload payload) {
        return payload != null && payload.stage().actNumber() == 3;
    }
}
