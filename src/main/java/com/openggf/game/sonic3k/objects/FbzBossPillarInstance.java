package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.events.S3kFbzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Locked-on {@code Obj_FBZBossPillar} (sonic3k.asm:109912-110000). */
public final class FbzBossPillarInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    private int x = 0x2DE0;
    private int y = 0x580;
    private int displacement;
    private boolean occupiedLastFrame;

    public FbzBossPillarInstance() {
        this(new ObjectSpawn(0x2DE0, 0x580, 0, 0, 0, false, 0));
    }

    public FbzBossPillarInstance(ObjectSpawn spawn) {
        super(spawn, "FBZBossPillar");
    }

    @Override public void update(int frameCounter, PlayableEntity mainPlayer) {
        int offsetX = S3kFbzEventWriteSupport.getBossBackgroundOffsetX(services());
        int offsetY = S3kFbzEventWriteSupport.getBossBackgroundOffsetY(services());
        x = (0x2DE0 + offsetX) & 0xFFFF;
        int baseY = (0x580 - offsetY) & 0xFFFF;
        int left = (x - 0x200) & 0xFFFF;
        int right = (x + (occupiedLastFrame ? 0x28 : 0)) & 0xFFFF;
        int top = (baseY + 0x80) & 0xFFFF;
        int bottom = (baseY + 0x100) & 0xFFFF;

        boolean occupied = false;
        ObjectPlayerQuery serviceQuery = services().playerQuery();
        ObjectPlayerQuery participants = new ObjectPlayerQuery(() -> mainPlayer, serviceQuery::sidekicks);
        for (PlayableEntity player : participants.playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            int px = player.getCentreX() & 0xFFFF;
            int py = player.getCentreY() & 0xFFFF;
            if (px >= left && px < right && py >= top && py < bottom
                    && !player.isTouchResponseSuppressedByObjectControl()) {
                occupied = true;
                break;
            }
        }
        occupiedLastFrame = occupied;
        if (occupied) displacement = nextDisplacement(displacement, true);
        else if (displacement != 0) {
            displacement = nextDisplacement(displacement, false);
            if (displacement == 0) services().playSfx(Sonic3kSfx.SPIKE_BALLS.id);
        }
        y = (short) ((baseY - displacement) & 0xFFFF);
        updateDynamicSpawn(x, y);
    }

    static int nextDisplacement(int current, boolean occupied) {
        return occupied ? Math.min(0x40, current + 8) : Math.max(0, current - 8);
    }

    static int nativeRightBoundOffset(boolean occupiedLastFrame) {
        return occupiedLastFrame ? 0x28 : 0;
    }

    // Obj_FBZBossPillar ends in SolidObjectFull + Draw_Sprite and has no native
    // out_of_range, MarkObjGone, or DeleteObject tail during the boss sequence.
    @Override public boolean isPersistent() { return true; }
    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x2B, 0x100, 0x100); }
    @Override public boolean allowsObjectControlledSolidContacts() { return true; }
    @Override public boolean rejectsBit7ObjectControlSideContact(PlayableEntity player) { return true; }
    @Override public boolean rejectsBit7ObjectControlNewSolidContact(PlayableEntity player) { return true; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    // SolidObject_cont uses bhi, so relX == d1*2 remains a valid zero-distance edge contact.
    @Override public boolean usesInclusiveRightEdge() { return true; }
    // SolidObjectFull_1P consumes an airborne stale standing bit and returns d4=0.
    @Override public boolean airborneStaleStandingBitReturnsNoContact(PlayableEntity player) { return true; }
    // The object reloads its current x_pos into d4 immediately before SolidObjectFull.
    @Override public boolean carriesRiderOnHorizontalMove(PlayableEntity player) { return false; }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 0xFF; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return 6; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_BOSS_PILLAR);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, x, y + 0x80, false, false);
            renderer.drawFrameIndex(0, x, y - 0x80, false, false);
        }
    }
}
