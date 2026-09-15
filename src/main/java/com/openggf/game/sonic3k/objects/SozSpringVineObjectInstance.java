package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** SKL $3F, Obj_SOZSpringVine ($40786): shared tension, pixel slope and one display SST. */
public final class SozSpringVineObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, SolidObjectListener, SpawnRewindRecreatable,
        RomObjectCodePointerProvider {
    // ROM word_408D4; the +/-1 entries deliberately keep the oscillation alive.
    private static final int[] REBOUND = {
        -256,-192,-128,-64,-1,64,128,192,256,192,128,64,1,-32,-64,-96,
        -128,-96,-64,-32,-1,16,32,48,64,48,32,16,1,-8,-16,-24,-32,-24,
        -16,-8,-1,4,8,12,16,12,8,4,1,-2,-4,-6,-8,-6,-4,-2,0
    };
    // ROM byte_40AAA, tension leverage at each directional pixel coordinate.
    private static final int[] LEVERAGE = {
        0,0,1,1,2,3,3,4,5,5,6,7,7,8,9,9,10,11,11,12,
        13,13,14,15,15,16,17,17,18,19,19,20,21,21,22,23,23,24,25,25,
        26,27,27,28,29,29,30,31,31,32,33,33,34,35,35,36,37,37,38,39,
        39,40,39,39,38,37,36,35,34,33,32,31,30,29,28,27,26,25,24,23,
        22,21,20,19,18,17,16,15,14,13,12,11,10,9,8,7,0,0
    };
    // Columns: native object standing bit, signed crossing marker.
    private final FbzParticipantStateTable participants = new FbzParticipantStateTable(2);
    private final byte[] surface = new byte[0x60];
    private int pivot = 0x60;
    private int tension;
    private int reboundIndex;
    private boolean rebounding;
    private boolean initialized;
    private int displaySlot = -1;

    public SozSpringVineObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SOZSpringVine");
        rebuildSurface();
    }

    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        if (!initialized) {
            initialized = true;
            var child = spawnAfterCurrentSibling(() -> new Display(spawn));
            if (child != null) displaySlot = child.getSlotIndex();
        }
        var players = services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED);
        // Bind rewind participant indices in the same order as solid contact (P1 first).
        if (leader != null) participants.slot(leader);
        for (var player : players) if (player != leader) participants.slot(player);
        int target = 0;
        // sub_40878 processes native P2 before P1. Extra followers are P2 equivalents;
        // P1 therefore retains final ownership of the shared pivot when both stand.
        for (var player : players) if (player != leader && player instanceof AbstractPlayableSprite p) {
            if (updateRider(p)) target = -0x100;
        }
        if (leader instanceof AbstractPlayableSprite p && updateRider(p)) target = -0x100;
        if (rebounding) {
            tension = REBOUND[reboundIndex++];
            if (tension == 0) { rebounding = false; reboundIndex = 0; }
        } else if (tension != target) {
            tension = (short) (tension + (target >= tension ? 0x40 : -0x40));
        }
        rebuildSurface();
        var manager = services().objectManager();
        if (manager != null && displaySlot >= 0 && pivot != 0) {
            for (var object : manager.getActiveObjects()) {
                if (object instanceof Display display && display.getSlotIndex() == displaySlot) {
                    display.updateSurface(surface, flipped());
                    break;
                }
            }
        }
    }

    private boolean updateRider(AbstractPlayableSprite p) {
        int slot = participants.slot(p);
        if (!participants.flag(slot, 0)) {
            participants.set(slot, 1, 0);
            return false;
        }
        int x = (p.getCentreX() - spawn.x() + 0x30) & 0xFFFF;
        if (x >= 0x60) return false;
        if (flipped()) x = (~x + 0x60) & 0xFFFF;
        pivot = x;
        int side = participants.get(slot, 1);
        if (side == 0) {
            participants.set(slot, 1, x < 0x3C ? -1 : 1);
        } else {
            // loc_40996 uses NOT.W, not NEG.W: flipped correction is -25.
            if (side > 0) p.setGSpeed((short) (p.getGSpeed() - (flipped() ? ~0x18 : 0x18)));
            if ((side < 0 && x >= 0x3C) || (side > 0 && x < 0x3C)) {
                participants.set(slot, 1, 0);
                rebounding = true;
                p.setXSpeed((short) (flipped() ? 0xEF0 : -0xEF0));
                p.setYSpeed((short) -0xEF0);
                p.setDirection(flipped() ? Direction.RIGHT : Direction.LEFT);
                p.setAir(true);
                p.setOnObject(false);
                p.setJumping(false);
                p.setAnimationId(0x10);
                p.setHurt(false);
                p.setDoubleJumpFlag(0);
                services().playSfx(Sonic3kSfx.SPRING.id);
                // The following solid pass owns clearing the object's standing bit.
            }
        }
        return true;
    }

    private void rebuildSurface() {
        // sub_40A08 returns without touching either slope RAM or child Ys at pivot zero.
        if (pivot == 0) return;
        int bend = LEVERAGE[pivot] * tension + (pivot << 8);
        int step = (short) (bend / pivot) << 8;
        int accumulator = 0;
        for (int i = 0; i < pivot; i++) {
            surface[i] = (byte) (accumulator >> 16);
            accumulator += step;
        }
        if (pivot < 0x60) {
            step = (short) ((0x6000 - bend) / (0x60 - pivot)) << 8;
            for (int i = pivot; i < 0x60; i++) {
                surface[i] = (byte) (accumulator >> 16);
                accumulator += step;
            }
        }
    }

    private boolean flipped() { return (spawn.renderFlags() & 1) != 0; }
    @Override public int getY() { return (spawn.y() + 0x28) & 0xFFFF; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x30, 0, 0); }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public boolean usesCollisionHalfWidthForTopLanding() { return true; }
    @Override public boolean usesPlatformObjectLandingSnap() { return false; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public boolean clearsStandingBitOnContinuedRideExit(PlayableEntity p) { return true; }
    @Override public byte[] getSlopeData() { return surface.clone(); }
    @Override public int getSlopeBaseline() { return 0; }
    @Override public int getSlopeSampleShift() { return 0; }
    // loc_1E45A cmpi.w #-$10 / blo admits overlap 16 as well as 0..15.
    @Override public Integer getDirectTopLandingOverlapLimit() { return 0x11; }
    @Override public boolean isSlopeFlipped() { return flipped(); }
    @Override public void onSolidContact(PlayableEntity player, SolidContact contact, int frame) {
        participants.flag(participants.slot(player), 0, contact.standing());
    }
    @Override public void onSolidContactCleared(PlayableEntity player, int frame) {
        participants.flag(participants.slot(player), 0, false);
    }
    @Override public int getPriorityBucket() { return 5; }
    @Override public int getOnScreenHalfWidth() { return 0x2C; }
    @Override public int getOnScreenHalfHeight() { return 0x2C; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(spawn.x(), cameraX, coarseXCullRange());
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        // Delete_Sprite_If_Not_In_Range returns without Draw_Sprite: only the child draws.
    }

    /** loc_40872: independent display SST, eight frame-zero pieces, priority four. */
    public static final class Display extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private final int[] xs = new int[8];
        private final int[] ys = new int[8];
        public Display(ObjectSpawn spawn) {
            super(spawn, "SOZSpringVineDisplay");
            int x = (spawn.x() - 0x28) << 16;
            int y = (spawn.y() + 0x28) << 16;
            for (int i = 0; i < 8; i++) {
                xs[i] = (x >> 16) & 0xFFFF;
                ys[i] = (y >> 16) & 0xFFFF;
                x += 0xB504F; y -= 0xB504F;
            }
        }
        private void updateSurface(byte[] heights, boolean flipped) {
            for (int i = 0; i < 8; i++) ys[flipped ? 7-i : i] =
                    (spawn.y() + 0x28 + 6 - heights[6 + i * 12]) & 0xFFFF;
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public boolean requiresSameFrameUpdate() { return true; }
        @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) {
            return isCoarseXOutOfRange(spawn.x(), cameraX, coarseXCullRange());
        }
        @Override public int getPriorityBucket() { return 4; }
        @Override public int getOnScreenHalfWidth() { return 0x40; }
        @Override public int getOnScreenHalfHeight() { return 0x40; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            var renderer = getRenderer(Sonic3kObjectArtKeys.SOZ_SPRING_VINE);
            if (renderer != null && renderer.isReady()) for (int i = 0; i < 8; i++)
                renderer.drawFrameIndex(0, xs[i], ys[i], (spawn.renderFlags() & 1) != 0, false);
        }
    }
}
