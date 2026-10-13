package com.openggf.game.sonic3k.ending;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.util.List;

/** Independently allocated loc_5DF58, retained until the native clear-RAM owner erases it. */
public final class EndingMasterPaletteObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    @RewindTransient(reason="Captured explicitly as the object's additional palette-script state.")
    private final S3kEndingMasterPaletteState palette = new S3kEndingMasterPaletteState();
    public EndingMasterPaletteObjectInstance() { this(new ObjectSpawn(0,0,0,0,0,false,0)); }
    private EndingMasterPaletteObjectInstance(ObjectSpawn spawn) { super(spawn,"EndingMasterPalette"); }
    @Override public boolean isPersistent() { return true; }
    @Override public void update(int vInt, PlayableEntity player) {
        var state=(EndingZoneRuntimeState)services().zoneRuntimeState();
        int mode=(byte)state.screen().masterPaletteMode;
        if(state.ending().progress()>0 && mode!=0) palette.tick(services(),mode>0?2:1);
    }
    private record Extra(S3kEndingMasterPaletteState.Value palette) implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }
    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext c) {
        return super.captureRewindState(c).withObjectSubclassExtra(new Extra(palette.captureRewindStateValue()));
    }
    @Override public void restoreRewindState(PerObjectRewindSnapshot s, RewindCaptureContext c) {
        super.restoreRewindState(s,c);
        if(s.objectSubclassExtra() instanceof Extra e) palette.restoreRewindStateValue(e.palette());
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
