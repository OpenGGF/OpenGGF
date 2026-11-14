package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * The Palmtree Panic index entries whose routine is a bare {@code rts}: {@code ObjEarthquake} ($05) and
 * {@code ObjEarthquakeSet} ($08) (ObjEarthquakeSet and ObjEarthquake, Palmtree Panic/Objects/Effects/Main.asm:65-75) and the scrapped
 * {@code ObjMissile} ($23, ObjMissile, Missile/Main.asm:7-10). The object keeps its slot, never draws, never acts and has no
 * despawn check; it is deleted only when the level unloads it by a different route, which these never take, so a
 * layout entry holding one occupies an object slot for good. No shipped Palmtree Panic layout places any of
 * them ({@code TestPpzObjectsB} scans every layout).
 */
public final class PpzBlankObject extends CdObject implements ObjectVisualFacing.NonDirectional {
    private final boolean nullInBossAct;

    /** {@code nullInBossAct}: the act 3 index has {@code ObjNull} here ($05, $08), which deletes itself. */
    public PpzBlankObject(ObjectSpawn spawn, boolean nullInBossAct) {
        super(spawn, "Blank");
        this.nullInBossAct = nullInBossAct;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (nullInBossAct) PpzStage.standDown(services(), this);
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
