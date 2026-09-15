package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** SKL $3A, Obj_SOZPathSwap ($1D106): path swap with per-player crossing debounce. */
public final class SozPathSwapObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private final FbzParticipantStateTable participants = new FbzParticipantStateTable(3);
    public SozPathSwapObjectInstance(ObjectSpawn spawn) { super(spawn, "SOZPathSwap"); }
    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        boolean vertical = (spawn.subtype() & 4) != 0;
        int centre = vertical ? spawn.y() : spawn.x();
        int otherCentre = vertical ? spawn.x() : spawn.y();
        int extent = 0x20 << (spawn.subtype() & 3);
        if (leader instanceof AbstractPlayableSprite p && p.isDebugMode()) return;
        for (var entity : services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
            if (!(entity instanceof AbstractPlayableSprite p)) continue;
            int slot = participants.slot(p);
            int position = (vertical ? p.getCentreY() : p.getCentreX()) & 0xFFFF;
            int other = (vertical ? p.getCentreX() : p.getCentreY()) & 0xFFFF;
            if (!participants.flag(slot, 2)) {
                // Init is strictly greater; the routine's crossing test includes equality.
                participants.flag(slot, 0, position > centre);
                participants.flag(slot, 2, true);
            }
            boolean positive = position >= centre;
            if (positive == participants.flag(slot, 0)) continue;
            participants.flag(slot, 0, positive);
            if ((short) other < (short) (otherCentre - extent)
                    || (short) other >= (short) (otherCentre + extent)
                    || ((spawn.subtype() & 0x80) != 0 && p.getAir())
                    || Math.abs(position - centre) >= 0x40) continue;
            if ((spawn.renderFlags() & 1) == 0) {
                boolean secondary = (spawn.subtype() & (positive ? 8 : 16)) != 0;
                if (p.getTopSolidBit() == 0xC) {
                    participants.set(slot, 1, 0);
                    if (!secondary) setPath(p, false);
                } else {
                    int count = (participants.get(slot, 1) + 1) & 0xFF;
                    participants.set(slot, 1, count == 2 ? 0 : count);
                    if (count != 2) setPath(p, secondary);
                }
            }
            p.setHighPriority((spawn.subtype() & (positive ? 0x20 : 0x40)) != 0);
        }
    }
    private static void setPath(PlayableEntity p, boolean secondary) {
        p.setTopSolidBit((byte) (secondary ? 0xE : 0xC));
        p.setLrbSolidBit((byte) (secondary ? 0xF : 0xD));
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
