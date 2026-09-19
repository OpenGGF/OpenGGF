package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** ROM {@code Obj_583BE}: emits the eight-wide spiral-ramp debris rows during the SSZ launch. */
public final class SszLaunchBackgroundDebrisController extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    static final int TILE_ROWS_ADDR = 0x58894;
    private int nextY = 0x870;
    private int rowDelay;
    private int delayPhase;
    private int emittedPieces;

    public SszLaunchBackgroundDebrisController(ObjectSpawn spawn) {
        super(spawn, "SSZLaunchBackgroundDebrisController");
    }

    @Override public boolean isPersistent() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        SszZoneRuntimeState state = services().zoneRuntimeState() instanceof SszZoneRuntimeState s ? s : null;
        if (state == null || player == null) return;
        if ((services().levelManager().getFrameCounter() & 0x0F) == 0) {
            services().playSfx(Sonic3kSfx.BIG_RUMBLE.id);
        }
        if (rowDelay > 0) {
            rowDelay--;
            return;
        }
        int playerY = player.getCentreY() & 0xFFFF;
        if (playerY >= 0x4000) playerY = 0x5C0;
        if (playerY + 0x198 > nextY) return;
        if (nextY < 0x300) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        emitRow(nextY);
        nextY -= 0x10;
        rowDelay = 7;
        delayPhase += 3;
    }

    private void emitRow(int y) {
        int band = y < 0x380 ? 0 : y < 0x800 ? 1 : 2;
        int row = (y & 0x70) >>> 4;
        int address = TILE_ROWS_ADDR + band * 0x80 + row * 0x10;
        byte[] descriptors;
        try {
            descriptors = services().rom().readBytes(address, 0x10);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read SSZ launch debris table", failure);
        }
        for (int column = 0; column < 8; column++) {
            int descriptor = ((descriptors[column * 2] & 0xFF) << 8)
                    | (descriptors[column * 2 + 1] & 0xFF);
            if (descriptor == 0xFFFF) continue;
            int delay = switch ((delayPhase + column) & 7) {
                case 0 -> 0x1C;
                case 1 -> 8;
                case 2 -> 0x10;
                case 3 -> 0;
                case 4 -> 4;
                case 5 -> 0x14;
                case 6 -> 0x18;
                default -> 0x0C;
            };
            int x = 0x1A08 + column * 0x10;
            spawnChild(() -> new SszLaunchBackgroundDebrisPiece(
                    new ObjectSpawn(x, y - 0x178, 0, delay, 0, false, descriptor)));
            emittedPieces++;
        }
    }

    public int nextYForTest() { return nextY; }
    public int rowDelayForTest() { return rowDelay; }
    public int emittedPiecesForTest() { return emittedPieces; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
