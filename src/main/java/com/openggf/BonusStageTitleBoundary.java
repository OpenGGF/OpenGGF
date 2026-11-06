package com.openggf;

import com.openggf.game.TitleCardProvider;
import com.openggf.game.internal.FreshLevelTitleBoundaryPublication;
import com.openggf.level.FreshLevelBoundaryAccess;
import com.openggf.level.NativeLevelLoadAccess;

/** Stack-local source presentation retained through a bonus title's destination assembly. */
record BonusStageTitleBoundary(short cameraX, short cameraY, int rings) {
    static BonusStageTitleBoundary capture(GameLoopBonusStageSession.Host host) {
        short cameraX = host.camera().getX();
        short cameraY = host.camera().getY();
        int rings = host.level().getLevelGamestate() != null
                ? host.level().getLevelGamestate().getRings() : 0;
        return new BonusStageTitleBoundary(cameraX, cameraY, rings);
    }

    void holdDestinationPlayers(GameLoopBonusStageSession.Host host, TitleCardProvider titleCard) {
        if (titleCard instanceof FreshLevelTitleBoundaryPublication
                && !NativeLevelLoadAccess.isPending(host.level())) {
            // Level: clears Object_RAM at loc_60DE, places players after the
            // title loop at loc_6310, then creates them at loc_6468
            // (sonic3k.asm:7649-7651, 7789-7792, 7880-7915).
            FreshLevelBoundaryAccess.beginAfterSetup(host.level(), cameraX, cameraY, rings);
        }
    }
}
