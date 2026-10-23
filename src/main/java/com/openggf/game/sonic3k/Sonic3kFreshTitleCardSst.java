package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.sonic3k.objects.S3kFreshTitleCardOwnerObjectInstance;
import com.openggf.level.objects.FixedSstSlotSink;

/** Load-scoped fixed-SST installation for Level/loc_62B6, before Load_Sprites. */
public final class Sonic3kFreshTitleCardSst {
    private boolean installing;

    void prepare(LevelLoadContext context) {
        // A host-placed/bootstrap load owns no native entry archives. Saved-origin
        // bonus returns still reach loc_62B6 and carry its owner to loc_64DC
        // (docs/skdisasm/sonic3k.asm:7575-7577,7762-7767,7906-7910).
        int zone=GameServices.level().getCurrentZone(), act=GameServices.level().getCurrentAct();
        installing=(context.isQueueFreshLevelRuntimeArt()
                || GameServices.level().isBonusStageReturn())
                && context.isIncludePostLoadAssembly()
                && context.getLoadMode()==LevelLoadMode.FULL
                && context.getAssemblyKind()==LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY;
        // loc_63C14/loc_7F310 set Act3_flag before StartNewLevel. loc_62B6
        // bypasses the whole fixed title graph, not only its presentation.
        if (com.openggf.level.LevelContinuationCarry.bypassesInitialPresentation(
                GameServices.level())) installing=false;
        // loc_62B6 bypasses $1701. Obj_TitleCardInit deletes at $D01 and
        // competition zones $E..$12 before creating archives or children.
        if ((zone==0x17 && act==1) || (zone==0x0D && act==1)
                || (zone>=0x0E && zone<=0x12)) installing=false;
        if (zone==0 && act==0 && !context.hasCheckpoint()
                && !GameServices.level().hasBigRingReturn()
                && !GameServices.level().isBonusStageReturn()
                && GameServices.module().getZoneFeatureProvider()
                        .shouldSuppressInitialTitleCard(zone,act)) installing=false;
    }

    void finishInstallation() { installing=false; }

    public void install(int zone, int act, FixedSstSlotSink slots) {
        if (!installing) return;
        // Dynamic_object_RAM is absolute SST3; Level writes +5, hence SST8.
        var owner=(S3kFreshTitleCardOwnerObjectInstance) slots.install(8,
                () -> new S3kFreshTitleCardOwnerObjectInstance(
                        new com.openggf.level.objects.ObjectSpawn(0,0,0,0,0,false,0)));
        owner.createNativeChildren(zone,act);
    }
}
