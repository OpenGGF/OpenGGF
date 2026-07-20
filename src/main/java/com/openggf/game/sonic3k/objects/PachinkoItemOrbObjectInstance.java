package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Object 0xED - Pachinko Item Orb.
 *
 * <p>ROM reference: {@code Obj_PachinkoItemOrb} (sonic3k.asm:96767-96811). The orb animates
 * until touched, then arms itself ({@code loc_4A218}) and waits for the player to break
 * contact before resolving a reward subtype from the orb's Y position and
 * {@code Level_frame_counter} ({@code loc_4A238}, sonic3k.asm:96789-96804), turning into the
 * shared {@link GumballItemObjectInstance} Pachinko reward object. A touch that never releases
 * (collision_property stays set) never converts — ROM re-checks the touch signal every pass
 * ({@code loc_4A274}) and only proceeds once it reads clear.
 */
public class PachinkoItemOrbObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener, SpawnRewindRecreatable {

    private static final int COLLISION_FLAGS = 0x40 | 0x17;
    private static final int[] ANIMATION = {0, 1, 2, 3, 4, 3, 2, 1};
    private static final int[] REWARD_TABLE = {
            1, 3, 1, 3, 8, 3, 8, 5, 1, 3, 6, 4, 1, 7, 6, 5, 8, 6, 4, 3,
            4, 3, 4, 5, 8, 4, 5, 3, 7, 3, 8, 3, 6, 5, 6, 7, 4, 3, 7, 5,
            6, 4, 6, 4, 7, 3, 3, 5, 4, 3, 4, 6, 3, 4, 3, 7, 4, 3, 4, 3,
            4, 3, 4, 3
    };
    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = TouchResponseProfile.standardEnemy();

    private int animationFrameCounter;

    /**
     * Mirrors ROM {@code collision_property}: set by {@link #onTouchResponse} whenever the
     * touch-response pass (which runs after this object's own {@link #update}) resolves an
     * overlap this frame, and consumed at the top of the NEXT {@link #update} call — matching
     * the ROM's one-frame-delayed collision-response-list signal.
     */
    private boolean touchedLastResolvedFrame;

    /**
     * Mirrors the ROM state split between {@code loc_4A218} (idle, waiting for the first touch)
     * and {@code loc_4A238} (armed, waiting for the player to RELEASE contact before the orb
     * converts). ROM: sonic3k.asm:96777-96786 (loc_4A218 arms on touch, does not convert same
     * frame) and sonic3k.asm:96789-96791 (loc_4A238 re-checks collision_property and stays armed
     * — loc_4A274 — for as long as the touch persists).
     */
    private boolean armed;
    private GumballItemObjectInstance rewardItem;

    public PachinkoItemOrbObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PachinkoItemOrb");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        animationFrameCounter = frameCounter;
        if (rewardItem != null) {
            rewardItem.update(frameCounter, playerEntity);
            if (rewardItem.isDestroyed()) {
                setDestroyed(true);
            }
            return;
        }

        // ROM sonic3k.asm:96790 (loc_4A238): tst.b collision_property(a0) / bne.s loc_4A274 —
        // conversion only proceeds once the touch signal reads clear (the player has broken
        // contact since arming). Consume the resolved signal now; onTouchResponse will set it
        // again if the touch-response pass (which runs after this update) finds a fresh overlap.
        boolean touchedNow = touchedLastResolvedFrame;
        touchedLastResolvedFrame = false;

        if (!armed) {
            // ROM sonic3k.asm:96778-96781 (loc_4A218): tst.b collision_property(a0) / beq.s
            // loc_4A228 — a nonzero property arms the orb but does NOT convert this frame.
            if (touchedNow) {
                armed = true;
            }
            return;
        }

        if (touchedNow) {
            // ROM loc_4A238->loc_4A274: still touching — stay armed, try again next frame.
            return;
        }

        // ROM loc_4A238 fallthrough (sonic3k.asm:96792-96804): touch has been released —
        // resolve the reward subtype from Level_frame_counter and convert.
        //
        // NOTE: this method's own `frameCounter` parameter is ObjectManager's internal
        // `vblaCounter` (ObjectManager.java: `instance.update(vblaCounter, player)`), which
        // carries a large constant bootstrap offset relative to ObjectManager's own
        // `frameCounter` field (empirically ~3071 frames in a representative trace run, not a
        // multiple of 4), corrupting the `&3` phase used by the REWARD_TABLE lookup below and
        // selecting an entirely wrong reward subtype. Other Level_frame_counter-driven S3K
        // objects read the ROM-aligned counter explicitly (e.g. HczMinibossInstance uses
        // services().objectManager().getFrameCounter()) instead of trusting a raw per-object
        // update() parameter — do the same here. (LevelManager also exposes a getFrameCounter(),
        // used by CnzBumperObjectInstance/AizFallingLogObjectInstance, but it is a separate
        // field from ObjectManager's and is not interchangeable with it — this call site needs
        // ObjectManager's counter, which is what advances alongside this object's own vblaCounter
        // dispatch.)
        int romFrameCounter = resolveRomFrameCounter(frameCounter);
        convertToReward(romFrameCounter);
    }

    /**
     * Resolves the ROM-aligned {@code Level_frame_counter} equivalent for subtype selection.
     * Falls back to the raw {@code update()} parameter only when the object manager is
     * unavailable (e.g. bare unit-test construction without full services wiring).
     */
    private int resolveRomFrameCounter(int updateParamFrameCounter) {
        try {
            return services().objectManager().getFrameCounter();
        } catch (Exception e) {
            return updateParamFrameCounter;
        }
    }

    @Override
    public int getCollisionFlags() {
        return rewardItem != null ? rewardItem.getCollisionFlags() : COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return rewardItem != null ? rewardItem.getCollisionProperty() : 0;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (rewardItem != null) {
            rewardItem.onTouchResponse(player, result, frameCounter);
            return;
        }
        touchedLastResolvedFrame = true;
    }

    @Override
    public int getX() {
        return rewardItem != null ? rewardItem.getX() : super.getX();
    }

    @Override
    public int getY() {
        return rewardItem != null ? rewardItem.getY() : super.getY();
    }

    @Override
    public ObjectSpawn getSpawn() {
        return rewardItem != null ? rewardItem.getSpawn() : super.getSpawn();
    }

    private void convertToReward(int frameCounter) {
        playSfx(Sonic3kSfx.BLUE_SPHERE);

        int rewardSubtype = resolveRewardSubtype(getY(), frameCounter);

        ObjectSpawn rewardSpawn = new ObjectSpawn(
                getX(), getY(), spawn.objectId() - 2, rewardSubtype,
                spawn.renderFlags(), false, 0, spawn.layoutIndex());
        rewardItem = GumballItemObjectInstance.createPachinkoItem(rewardSpawn);
        rewardItem.setServices(services());
    }

    static int resolveRewardSubtype(int yPos, int levelFrameCounter) {
        int rewardIndex = (((yPos & 0x0F) << 2) + (levelFrameCounter & 3)) & 0x3F;
        return REWARD_TABLE[rewardIndex];
    }

    private void playSfx(Sonic3kSfx sfx) {
        try {
            services().playSfx(sfx.id);
        } catch (Exception e) {
            // Keep gameplay logic independent from audio state.
        }
    }

    @Override
    public int getPriorityBucket() {
        return rewardItem != null ? rewardItem.getPriorityBucket() : RenderPriority.clamp(4);
    }

    @Override
    public boolean isHighPriority() {
        return rewardItem == null || rewardItem.isHighPriority();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (rewardItem != null) {
            rewardItem.appendRenderCommands(commands);
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.PACHINKO_ITEM_ORB);
        if (renderer == null) {
            return;
        }
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        int frame = ANIMATION[animationFrameCounter & 0x7];
        renderer.drawFrameIndex(frame, spawn.x(), spawn.y(), hFlip, vFlip);
    }
}
