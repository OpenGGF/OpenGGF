package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * Native dispatch slot for Obj_MGZ2_BossTransition (sonic3k.asm:30240-30307).
 * The zone events own its captured timer/position publication; this object owns
 * admission and execution order, including a newborn slot later in the same pass.
 */
public final class MgzBossTransitionObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    public MgzBossTransitionObjectInstance() {
        this(new ObjectSpawn(0, 0, 0xFF, 0, 0, false, 0));
    }

    private MgzBossTransitionObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MGZ2BossTransition");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!(services().levelEventProvider() instanceof Sonic3kLevelEventManager manager)
                || manager.getMgzEvents() == null
                || !manager.getMgzEvents().updateBossTransitionNativeObject()) {
            setDestroyed(true);
        }
    }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public MgzBossTransitionObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new MgzBossTransitionObjectInstance(context.spawn());
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) { }
}
