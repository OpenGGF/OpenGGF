package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.playable.Knuckles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Obj_RobotnikHead4 and Obj_RobotnikShipFlame for the SSZ2 crane ship. */
public final class SszCraneShipDecoration extends AbstractSszCraneChild {
    private int x;
    private int y;
    private int priority;
    private boolean initialized;
    private boolean eggRobo;
    private boolean visible;
    private boolean flipX;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private final SszRuntimeArtRequest art = new SszRuntimeArtRequest();
    private transient S3kRawAnimation script;

    public SszCraneShipDecoration(ObjectSpawn spawn, SszCraneShip ship) {
        super(spawn, "SszCraneShipDecoration", ship);
        x = spawn.x(); y = spawn.y();
    }

    @Override public SszCraneShipDecoration recreateForRewind(RewindRecreateContext context) {
        return new SszCraneShipDecoration(context.spawn(), null);
    }

    private boolean flame() { return getSpawn().subtype() != 0; }

    @Override public void update(int clock, PlayableEntity ignored) {
        visible = false;
        if (!(parent instanceof SszCraneShip ship) || parentGone()) {
            ObjectLifetimeOps.expireDynamic(this); return;
        }
        art.service(services());
        if (ship.parentFlag(flame() ? 4 : 5)) { ObjectLifetimeOps.expireDynamic(this); return; }
        flipX = ship.craneFlipped();
        x = (ship.getX() + (flame() ? (flipX ? -0x1E : 0x1E) : 0)) & 0xFFFF;
        y = (ship.getY() - (flame() ? 0 : 0x1C)) & 0xFFFF;
        priority = flame() ? 5 : ship.getPriorityBucket();
        if (!initialized) {
            initialized = true;
            if (flame()) return;
            priority = 5; // ObjDat_RobotnikHead overwrites the first Child_GetPriority copy.
            // Obj_RobotnikHead3Init selects the EggRobo table only for character_id=2.
            eggRobo = services().spriteManager().getMainPlayable() instanceof Knuckles;
            if (eggRobo) art.submit(services(), 0x15FDDC, 0x52E);
        } else if (!flame()) {
            int address = eggRobo ? 0x681D0 : 0x681CC;
            if (script == null) {
                try { script = S3kRawAnimation.load(services().romReader(), address, 4); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            }
            script.animateNoSst(animation, address, () -> { });
        }
        visible = !flame() || ((clock & 1) == 0 && ship.xVelocity() != 0);
        updateDynamicSpawn(x, y);
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return priority; }
    // CreateChild1_Normal copies the ship's high art bit. SetUp_ObjAttributes3
    // changes the flame's sprite bucket ($280), but never clears that art bit.
    @Override public boolean isHighPriority() { return true; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getOnScreenHalfWidth() { return flame() ? 8 : 0x10; }
    @Override public int getOnScreenHalfHeight() { return flame() ? 4 : 8; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var key = !flame() && eggRobo ? Sonic3kObjectArtKeys.EGG_ROBO_HEAD : Sonic3kObjectArtKeys.ROBOTNIK_SHIP;
        var renderer = getRenderer(key);
        if (visible && renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(flame() ? 6 : animation.mappingFrame, x, y, flipX, false);
        }
    }
}
