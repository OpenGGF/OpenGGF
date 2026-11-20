package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/**
 * Palmtree Panic's vertical spin tunnel door. ObjTunnelDoorV
 * (Level/Palmtree Panic/Objects/Tunnel Door/Main.asm:7-20).
 */
public final class PpzTunnelDoor extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional, SpawnRewindRecreatable {
    private enum Phase { INIT, READY, RESET }

    private Phase phase = Phase.INIT;
    private int resetFrames;
    private int mapFrame;

    public PpzTunnelDoor(ObjectSpawn spawn) {
        super(spawn, "Vertical tunnel door");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (phase) {
            case INIT -> {
                var provider = artProvider();
                if (provider == null) return;
                // ObjTunnelDoorV_Init (Level/Palmtree Panic/Objects/Tunnel Door/Main.asm:34-42) falls into Main.
                priority = 1;
                width = 4;
                yRadius = 0x18;
                tileWord = provider.objectTileId(PpzObjectArt.TILE_ID_TUNNEL_DOOR, subtype2());
                phase = Phase.READY;
                ready(player);
            }
            case READY -> ready(player);
            case RESET -> {
                // ObjTunnelDoorV_Reset (Level/Palmtree Panic/Objects/Tunnel Door/Main.asm:76-83):
                // the byte counter adds 8 until carry. Collision resumes one update after it closes.
                if (--resetFrames == 0) {
                    phase = Phase.READY;
                    mapFrame = 0;
                }
            }
        }
    }

    private void ready(PlayableEntity player) {
        // ObjTunnelDoorV_Main (Level/Palmtree Panic/Objects/Tunnel Door/Main.asm:47-71).
        if (player != null) {
            int dx = (x & 0xFFFF) - (player.getCentreX() & 0xFFFF);
            int dy = Math.abs((y & 0xFFFF) - (player.getCentreY() & 0xFFFF));
            if (dy < 0x20 && dx >= 0 && dx < 0x30) {
                phase = Phase.RESET;
                resetFrames = 32;
                if (renderFlagOnScreen()) {
                    services().playSfx(0xA4);
                    mapFrame = 1;
                }
            }
        }
        solidObject(Entry.SOLID);
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (phase != Phase.INIT && tileWord >= 0) {
            drawFrame(PpzObjectArt.TUNNEL_DOOR + tileWord, mapFrame, hFlip(), vFlip());
        }
    }

    public int mapFrame() { return mapFrame; }
}
