package com.openggf.game.sonic3k.objects;

import com.openggf.game.OscillationManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.util.List;

/** SKL $4A, Obj_DEZFloatingPlatform / loc_25A7E (sonic3k.asm:51194-51238). */
public final class S3kDezFloatingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private int anchorX;
    private int anchorY;
    private int currentX;
    private int currentY;
    private int rampPosition;
    private int rampVelocity;
    private boolean rampReturning;
    private int mappingFrame;

    public S3kDezFloatingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZFloatingPlatform");
        anchorX = currentX = spawn.x();
        anchorY = currentY = spawn.y();
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        // word_25AB8 points to the same nine ROM movers as LRZ's off_258BC.
        // The high subtype nibble does not select art or dimensions in DEZ.
        switch (spawn.subtype() & 15) {
            case 0 -> { }
            case 1 -> currentX = anchorX + oscillation(0x08, 0x20);
            case 2 -> currentX = anchorX + oscillation(0x1C, 0x40);
            case 3 -> currentX = anchorX + ramp(0x5F) - 0x60;
            case 4 -> currentY = anchorY + oscillation(0x08, 0x20);
            case 5 -> currentY = anchorY + oscillation(0x1C, 0x40);
            case 6 -> currentY = anchorY + ramp(0x5F) - 0x60;
            case 7 -> currentX = anchorX + ramp(0x7F) - 0x80;
            case 8 -> currentY = anchorY + ramp(0x7F) - 0x80;
            default -> { } // outside the nine-entry ROM table; no shipped placement uses it
        }
        currentX &= 0xFFFF;
        currentY &= 0xFFFF;
        updateDynamicSpawn(currentX, currentY);
        mappingFrame = (mappingFrame + 1) & 1;
    }
    // OscillationManager excludes the ROM control word: $0A/$1E become $08/$1C.
    private int oscillation(int offset, int centre) {
        int displacement = (OscillationManager.getByte(offset) & 0xFF) - centre;
        return (spawn.renderFlags() & 1) == 0 ? displacement : -displacement;
    }
    private int ramp(int limit) {
        // sub_25974: word ADD accumulates 8.8 displacement, but MOVE.B and
        // CMP.B at $36 read its HIGH byte. Both word accumulators wrap.
        rampVelocity = (short) (rampVelocity + (rampReturning ? -4 : 4));
        rampPosition = (rampPosition + rampVelocity) & 0xFFFF;
        int displacement = rampPosition >>> 8;
        if (rampReturning) {
            if (limit > displacement) rampReturning = false;
        } else if (limit <= displacement) rampReturning = true;
        // Status X-flip reflects about d2, not about zero, for this mover.
        return (spawn.renderFlags() & 1) == 0 ? displacement : limit - displacement;
    }
    @Override public int getX() { return currentX; }
    @Override public int getY() { return currentY; }
    @Override public int romObjectCodePointerHighWord() { return 2; } // $25A1E..$25AB8 bank; FixBugs=0 solid carry
    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x2B,0x10,0x11); }
    @Override public SolidRoutineProfile getSolidRoutineProfile() { return SolidRoutineProfile.fullSolid(false); }
    @Override public boolean allowsObjectControlledSolidContacts() { return true; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return isCoarseXOutOfRange(anchorX,cameraX,coarseXCullRange()); }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }
    @Override public int getPriorityBucket() { return 4; } // priority=$200, art bit 15 clear
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_FLOATING_PLATFORM);
        // MOVE.B #4,render_flags clears both art flips; the original status
        // X-flip still reflects motion above. Mapping alternates every dispatch.
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame,getX(),getY(),false,false);
    }
    public int mappingFrameForTest() { return mappingFrame; }
    public int rampPositionForTest() { return rampPosition; }
    public int rampVelocityForTest() { return rampVelocity; }
    public boolean rampReturningForTest() { return rampReturning; }
}
