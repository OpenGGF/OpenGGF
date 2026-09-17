package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_655B2} ({@code ChildObjDat_6663E}, {@code ObjDat3_6653C}): the solid block the
 * altar collapse leaves at {@code ($17F0,$660)} beside the lower floor. It is
 * {@code SolidObjectFull} ({@code $1B}, {@code $20}, {@code $20}) until Knuckles' punch raises
 * {@code _unkFAA2}, then creates the 32 {@code loc_65602} fragments and deletes itself.
 */
public final class HpzCollapseBlockObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, SolidObjectProvider {
    static final int X = 0x17F0;
    static final int Y = 0x660;
    private static final int MAPPING_FRAME = 6;
    private static final SolidObjectParams SOLID = SolidObjectParams.of(0x1B, 0x20, 0x20);

    private boolean broken;

    public HpzCollapseBlockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HpzCollapseBlock");
    }

    @Override
    public HpzCollapseBlockObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzCollapseBlockObjectInstance(ctx.spawn());
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (broken) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        var solid = services().solidExecution();
        if (solid != null && !solid.isInert()) {
            solid.resolveSolidNowAll();
        }
        HpzZoneRuntimeState hpz = HpzKnucklesCutsceneSupport.hpz(services());
        if (hpz == null || !hpz.collapseBlockBreak()) {
            return;
        }
        // ChildObjDat_6664A: $20 x loc_65602, then Go_Delete_Sprite.
        for (int i = 0; i < 0x20; i++) {
            int subtype = i * 2;
            spawnChild(() -> new HpzCollapseBlockFragmentObjectInstance(
                    new ObjectSpawn(X, Y, 0, subtype, 0, false, 0)));
        }
        broken = true;
    }

    @Override public int getX() { return X; }
    @Override public int getY() { return Y; }
    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public boolean isSolidFor(PlayableEntity player) { return !broken; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 0; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_COLLAPSE_BLOCK);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(MAPPING_FRAME, X, Y, false, false);
        }
    }
}
