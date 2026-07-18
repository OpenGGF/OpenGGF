package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.level.objects.RewindRecreateContext;

final class CnzEndBossRewindLinks {
    private CnzEndBossRewindLinks() { }

    static CnzEndBossInstance boss(RewindRecreateContext ctx) {
        if (ctx == null || ctx.objectManager() == null) return null;
        return ctx.objectManager().activeObjectsOfType(CnzEndBossInstance.class).stream()
                .filter(candidate -> !candidate.isDestroyed())
                .findFirst().orElse(null);
    }
}
