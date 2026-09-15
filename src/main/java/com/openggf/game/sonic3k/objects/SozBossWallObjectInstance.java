package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** loc_56A34: eight invisible solids follow the opening background's row positions. */
public final class SozBossWallObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable {
    private int x = 0x7FFF;
    private int y;
    private int baseX;
    private boolean initialized;

    public SozBossWallObjectInstance(ObjectSpawn spawn) { super(spawn, "SOZBossWall"); }

    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        var runtime = services().zoneRuntimeRegistry().current();
        if (!(runtime instanceof SozZoneRuntimeState state) || state.actIndex() != 1) {
            setDestroyed(true);
            return;
        }
        if (!initialized) {
            try {
                baseX = services().rom().read16BitAddr(0x56AF4 + spawn.subtype() * 2);
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot load SOZ boss-wall solid position", failure);
            }
            initialized = true;
        }
        var event = state.events();
        int row = spawn.subtype();
        y = (row * 16 + 0x458 - (0x488 - event.bossY())) & 0xFFFF;
        x = event.bossWallRoutine() == 0x10
                ? (baseX - (0x1240 - event.bossX() + event.bossWall().rowOffset(row + 2))) & 0xFFFF
                : 0x7FFF;
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x4B, 8, 8); }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public void onSolidContact(PlayableEntity player, SolidContact contact, int frame) {
        // loc_56A7E: d6 bits16/17 are side contacts (standing bit + $D).
        if (contact.touchSide() && player instanceof AbstractPlayableSprite playable) {
            playable.setXSpeed((short) -0x300);
            playable.setYSpeed((short) -0x300);
        }
    }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
