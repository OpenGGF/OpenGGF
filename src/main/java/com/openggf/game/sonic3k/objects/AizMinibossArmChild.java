package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ miniboss arm child.
 *
 * ROM: loc_6870A / loc_68720
 * - Offset from ChildObjDat_6905C: (-0x24, +8)
 * - mapping_frame=6
 * - purely visual (no collision)
 */
public class AizMinibossArmChild extends AizMinibossComponent implements RewindRecreatable {
    private static final int X_OFFSET = -0x24;
    private static final int Y_OFFSET = 8;

    public AizMinibossArmChild(AbstractBossInstance parent) {
        // ROM: word_69012 priority $0200 → $200/$80 = bucket 4
        super(parent, "AIZMinibossArm", 4, 0);
        initializeAdjustedPosition(parent, X_OFFSET, Y_OFFSET);
        updateDynamicSpawn();
    }

    private AizMinibossArmChild(ObjectSpawn spawn) {
        super(spawn, "AIZMinibossArm", 4);
    }

    @Override public AizMinibossArmChild recreateForRewind(RewindRecreateContext ctx) {
        return new AizMinibossArmChild(ctx.spawn());
    }

    @Override public void syncPositionWithParent() { refreshAdjustedPosition(X_OFFSET, Y_OFFSET); }

    @Override public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!beginOwnDispatch(vIntRunCount)) return;
        syncPositionWithParent();
        finishNativeDrawTail();
    }

    @Override
    public boolean isHighPriority() {
        // ROM: make_art_tile(ArtTile_AIZMiniboss,0,1) — priority bit = 1
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (nativeRetirementPending || isDestroyed()) return;
        ObjectRenderManager rm = services().renderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        boolean hFlip = (nativeRenderFlags & 1) != 0;
        renderer.drawFrameIndex(6, currentX, currentY, hFlip, false);
    }
}
