package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectManager;

/** Only the live parent words/bits consumed by Refresh_ChildPositionAdjusted and Child_Draw*Sprite2. */
record S3kChildDrawParentHeader(int x, int y, int renderFlags, int flags38, boolean defeated) {
    S3kChildDrawParentHeader {
        x &= 0xFFFF;
        y &= 0xFFFF;
        renderFlags &= 0xFF;
        flags38 &= 0xFF;
    }

    static S3kChildDrawParentHeader read(ObjectManager manager, int slot) {
        if (slot < 0) throw new IllegalStateException("AIZ child has no physical parent3");
        var owner = NativeSstSlotAccess.currentOwner(manager, slot);
        if (owner instanceof AbstractBossInstance boss) return fromBoss(boss);
        if (owner instanceof S3kBossDefeatSignpostFlow flow) return flow.retainedChildDrawHeader();
        // Delete_Current_Sprite clears this physical record; unknown nonempty owners
        // cannot be substituted with invented native header fields.
        if (owner == null) return new S3kChildDrawParentHeader(0, 0, 0, 0, false);
        throw new IllegalStateException("Unknown AIZ parent3 header at SST" + slot
                + ": " + owner.getClass().getName());
    }

    static S3kChildDrawParentHeader fromBoss(AbstractBossInstance boss) {
        var state = boss.getState();
        return new S3kChildDrawParentHeader(state.x, state.y, state.renderFlags,
                boss.getCustomFlag(0x38), state.defeated);
    }
}
