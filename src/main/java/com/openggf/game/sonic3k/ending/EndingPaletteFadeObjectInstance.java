package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Independently allocated Obj_FadeSelectedFromBlack at loc_5F8FA. */
public final class EndingPaletteFadeObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    private int line;
    private int reload;
    private int timer;
    private int steps=7;
    private boolean initialized;
    public EndingPaletteFadeObjectInstance(int line,int delay) {
        this(new ObjectSpawn(0,0,0,0,0,false,0));this.line=line;reload=timer=delay;
    }
    private EndingPaletteFadeObjectInstance(ObjectSpawn spawn) { super(spawn,"EndingPaletteFade"); }
    @Override public EndingPaletteFadeObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new EndingPaletteFadeObjectInstance(context.spawn());
    }
    private record Extra(int line,int reload,int timer,int steps,boolean initialized)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }
    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new Extra(line,reload,timer,steps,initialized));
    }
    @Override public void restoreRewindState(PerObjectRewindSnapshot snapshot,RewindCaptureContext context) {
        super.restoreRewindState(snapshot,context);
        if(snapshot.objectSubclassExtra() instanceof Extra e) {
            line=e.line();reload=e.reload();timer=e.timer();steps=e.steps();initialized=e.initialized();
        }
    }
    @Override public void update(int vIntRunCount,PlayableEntity player) {
        var palette=services().paletteOwnershipRegistryOrNull();
        if(!initialized) { initialized=true;palette.setPaletteRotationDisabled(true); }
        if((timer=(short)(timer-1))>=0)return;
        timer=reload;S3kEndingPalette.towardsActualTarget(services(),line);
        if((steps=(byte)(steps-1))<0) {
            palette.setPaletteRotationDisabled(false);ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
