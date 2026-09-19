package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Subtype {@code $24}, {@code CutsceneKnux_LRZ2} ({@code sonic3k.asm:131110-131169}). */
public final class CutsceneKnucklesLrz2Instance extends AbstractObjectInstance
        implements RewindRecreatable {
    private int stage;
    private int timer;
    private int mappingFrame = 0x56;
    private record Extra(int stage, int timer, int mappingFrame)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public CutsceneKnucklesLrz2Instance(ObjectSpawn spawn) { super(spawn, "CutsceneKnucklesLRZ2"); }

    @Override public void update(int v, PlayableEntity p) {
        if (!(services().zoneRuntimeState() instanceof LrzZoneRuntimeState lrz)) return;
        if (stage == 0 && lrz.cutsceneFlag(1)) {
            stage = 1; timer = 46; mappingFrame = 0xDE;
        } else if (stage == 1 && --timer <= 0) {
            stage = 2; timer = 18; lrz.setCutsceneFlag(2); mappingFrame = 0xDE;
        } else if (stage == 2 && --timer <= 0) {
            stage = 3; mappingFrame = 0x56;
        }
    }
    @Override public int getX() { return 0x3A38; }
    @Override public int getY() { return 0x00EC; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer r=getRenderer(Sonic3kObjectArtKeys.CUTSCENE_KNUCKLES);
        if(r!=null) r.drawFrameIndex(mappingFrame,getX(),getY(),true,false);
    }
    @Override public CutsceneKnucklesLrz2Instance recreateForRewind(RewindRecreateContext c){return new CutsceneKnucklesLrz2Instance(c.spawn());}
    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext c){return super.captureRewindState(c).withObjectSubclassExtra(new Extra(stage,timer,mappingFrame));}
    @Override public void restoreRewindState(PerObjectRewindSnapshot s,RewindCaptureContext c){super.restoreRewindState(s,c);if(s.objectSubclassExtra() instanceof Extra e){stage=e.stage();timer=e.timer();mappingFrame=e.mappingFrame();}}
}
