package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.util.List;

/** loc_65B70/loc_65BAE: decorative missiles from the rising SSZ Death Egg. */
public final class SszDeathEggMissile extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int xFixed, yFixed, xVelocity, yVelocity, frame;
    private boolean initialized, pendingDelete;

    public SszDeathEggMissile(ObjectSpawn spawn) {
        super(spawn, "SszDeathEggMissile");
        xFixed = spawn.x() << 16;
        yFixed = spawn.y() << 16;
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (!initialized) {
            initialized = true;
            int random = services().rng().nextRaw();
            int dx = (random & 0x3F) - 0x20;
            xFixed += dx << 16;
            yFixed += ((random >>> 16) & 0x1F) << 16;
            xVelocity = dx < 0 ? -0x100 : 0x100;
            yVelocity = 0x100;
            // loc_65B70 falls through: acceleration and movement happen on initialization.
        }
        frame = 4 + (vIntRunCount & 1);
        yVelocity = (short) (yVelocity - 0x10);
        xFixed += xVelocity << 8;
        yFixed += yVelocity << 8;
        var camera = services().camera();
        // Sprite_CheckDeleteXY uses unsigned inclusive $280/$200 windows. Extend
        // only the visible-width term in X for widescreen, as other object culls do.
        pendingDelete = isCoarseXOutOfRange(getX(), camera.getX(), coarseXCullRange())
                || ((getY() - camera.getY() + 0x80) & 0xFFFF) > 0x200;
        // Go_Delete_Sprite hides this pass and frees the slot on the next pass.
    }

    @Override public int getX() { return xFixed >>> 16; }
    @Override public int getY() { return yFixed >>> 16; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return 5; } // word_664DA: $280; hardware priority stays low.
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 8; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_DEATH_EGG_SMALL);
        if (initialized && !pendingDelete && renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexWithPaletteBase(frame, getX(), getY(), false, false, 0);
        }
    }
}
