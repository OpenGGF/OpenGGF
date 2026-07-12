package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;

import java.util.List;

/** Separate top-button SST slot created by {@code Obj_EggCapsule}. */
public final class HczEndBossEggCapsuleButton extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnCoordinateRewindRecreatable {
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x1B, 4, 6);

    public HczEndBossEggCapsuleButton(int x, int y) {
        super(new ObjectSpawn(x, y, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, y),
                "HCZEggCapsuleButton");
    }

    private HczEndBossEggCapsuleButton() {
        this(0, 0);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}
