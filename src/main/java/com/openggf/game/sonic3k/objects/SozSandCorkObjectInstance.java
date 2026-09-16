package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** SKL $47, Obj_SOZSandCork ($41CC2): touch latch, released sand columns and six debris pieces. */
public final class SozSandCorkObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener, SpawnRewindRecreatable,
        RomObjectCodePointerProvider {
    private final FbzParticipantStateTable participants = new FbzParticipantStateTable(1);
    private int phase, xFixed, yFixed, xVelocity, yVelocity, travel, piece;
    private boolean initialized;
    public SozSandCorkObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SOZSandCork"); xFixed = spawn.x() << 16; yFixed = spawn.y() << 16;
    }
    @Override public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (phase == 0 && result.category() == TouchCategory.SPECIAL)
            participants.flag(participants.slot(player), 0, true);
    }
    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        if (!initialized) {
            initialized = true;
            var manager = services().objectManager();
            if (phase == 0 && manager != null && manager.isSpawnStateBitSet(spawn, 0)) {
                spawnColumns(true); ObjectLifetimeOps.deleteNoRespawn(this); return;
            }
        }
        if (phase == 0) {
            for (var entity : services().playerQuery().playersFor(
                    ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
                int slot = participants.slot(entity);
                boolean hit = participants.flag(slot, 0); participants.flag(slot, 0, false);
                if (!hit || !(entity instanceof AbstractPlayableSprite p) || p.getAnimationId() != 2) continue;
                p.setXSpeed((short) -p.getXSpeed()); p.setYSpeed((short) -p.getYSpeed());
                spawnColumns(false);
                phase = 1; piece = 0;
                // word_41E96 is ROM-owned debris velocity data; all six mapping pieces participate.
                try {
                    for (int i = 0; i < 6; i++) {
                        int vx = (short) services().rom().read16BitAddr(0x41E96 + i * 4);
                        int vy = (short) services().rom().read16BitAddr(0x41E98 + i * 4);
                        if (i == 0) { xVelocity = vx; yVelocity = vy; }
                        else {
                            int index = i;
                            services().objectManager().createDynamicObject(() -> {
                                var debris = new SozSandCorkObjectInstance(spawn);
                                debris.phase = 1; debris.initialized = true; debris.piece = index;
                                debris.xVelocity = vx; debris.yVelocity = vy;
                                return debris;
                            });
                        }
                    }
                } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                services().objectManager().setSpawnStateBit(spawn, 0);
                if (services().zoneRuntimeState() instanceof SozZoneRuntimeState state)
                    state.requestSandCorkRelease((spawn.subtype() & 0x80) != 0);
                services().playSfx(Sonic3kSfx.COLLAPSE.id);
                moveDebris(); // sub_41D5C's immediate MoveSprite2/gravity step.
                break;
            }
        } else if (phase == 1) {
            moveDebris();
            if (yVelocity >= 0x400 && !isOnScreen()) ObjectLifetimeOps.deleteNoRespawn(this);
        } else {
            if (travel != 0) { yFixed += 4 << 16; travel = (travel - 4) & 0xFFFF; }
            // Native loc_41E78 reads the low byte at a0-$1FB, an unrelated SST
            // byte, for its quiet skid sound. It does not read a frame clock.
        }
    }
    private void moveDebris() {
        xFixed += (short) xVelocity << 8; yFixed += (short) yVelocity << 8;
        yVelocity = (short) (yVelocity + 0x18);
    }
    private void spawnColumns(boolean restored) {
        int distance = (spawn.subtype() & 0x7F) << 4;
        int y = spawn.y() - 0x90 + (restored ? distance : 0);
        // Break uses SUB.W/BCC (including exact zero); reload uses SUB.W/BHI.
        int count = restored ? Math.max(1, (distance + 0xFF) / 0x100) : distance / 0x100 + 1;
        SozSandCorkObjectInstance last = null;
        for (int i = 0; i < count; i++, y -= 0x100) {
            int columnY = y;
            var allocated = services().objectManager().createDynamicObject(() -> {
                var column = new SozSandCorkObjectInstance(spawn);
                column.phase = 2; column.initialized = true;
                column.yFixed = columnY << 16; column.travel = restored ? 0 : distance;
                return column;
            });
            if (allocated != null) last = allocated;
        }
        if (last != null) services().objectManager().transferPlacementOwnership(this, last);
    }
    @Override public int getCollisionFlags() { return phase == 0 ? 0xC6 : 0; }
    @Override public int getCollisionProperty() { return 0; }
    // Native Draw_And_Touch_Sprite polls the collision property on every overlap.
    @Override public TouchResponseProfile getTouchResponseProfile() {
        return getTouchResponseProfile(false);
    }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return new TouchResponseProfile(TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY,
                true, true, multiRegionSource, TouchShieldDeflectCapability.NONE, 0, false,
                TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
                TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
                multiRegionSource ? TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_MAIN_ONLY
                        : TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);
    }
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return true; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public int getX() { return xFixed >>> 16; }
    @Override public int getY() { return yFixed >>> 16; }
    @Override public int getPriorityBucket() { return phase == 0 ? 5 : phase == 1 ? 0 : 1; }
    @Override public int getOnScreenHalfWidth() { return 0xC; }
    @Override public int getOnScreenHalfHeight() { return phase == 2 ? 0x80 : 8; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return phase != 1 && isCoarseXOutOfRange(getX(), cameraX, coarseXCullRange());
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.SOZ_SAND_CORK);
        if (renderer == null || !renderer.isReady()) return;
        if (phase == 1) renderer.drawFramePieceByIndex(1, piece, getX(), getY(), false, false);
        else renderer.drawFrameIndex(phase == 2 ? 2 : 0, getX(), getY(), false, false);
    }
}
