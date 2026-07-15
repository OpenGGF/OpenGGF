package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.events.S3kFbzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RomWorldPositionedObject;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Transient {@code ChildObjDat_703EC} laser, {@code loc_70192-loc_702E4}. */
final class Fbz2SubbossLaserChild extends AbstractFbz2SubbossChild
        implements TouchResponseProvider, RewindRecreatable, RomWorldPositionedObject {
    private enum Phase { CHARGE, BEAM, RETRACT, WAIT_DELETE, DELETE_PENDING }

    private int phaseOrdinal;
    /** ROM animation byte {@code anim_frame_timer}. */
    private int animTimer;
    /** ROM animation byte {@code anim_frame}. */
    private int animCursor;
    /** ROM accelerating delay byte {@code $2E}. */
    private int acceleratingDelay;
    /** ROM zero-delay completion byte {@code $2F}. */
    private int acceleratingLoops;
    /** ROM status bit 5: Animate_RawGetFaster owns the script. */
    private boolean acceleratingOwned;
    /** ROM Obj_Wait word {@code $2E} after the charging callback. */
    private int waitWord;
    private int beamWaitStage;
    private int frame = 5;
    private int collisionFlags;
    private boolean nativePlayerHurt;
    private boolean impactAllocationsAttempted;
    private boolean visible = true;

    Fbz2SubbossLaserChild(Fbz2SubbossInstance root) {
        this(new ObjectSpawn(root.getX(), root.getY() + 8, 0xAB, 0, 0, false, 0));
        this.root = root;
        familySlot = root.getSlotIndex();
    }

    private Fbz2SubbossLaserChild(ObjectSpawn spawn) {
        super(spawn, "FBZ2SubbossLaser");
    }

    static int activeCollisionFlags() { return 0xAC; }
    int frameForTest() { return frame; }
    String phaseNameForTest() { return Phase.values()[phaseOrdinal].name(); }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        switch (Phase.values()[phaseOrdinal]) {
            case CHARGE -> updateCharge();
            case BEAM -> updateBeam();
            case RETRACT -> updateRetract();
            case WAIT_DELETE -> updateDeleteWait();
            case DELETE_PENDING -> ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    /** Exact {@code Animate_RawGetFaster} over {@code byte_70401}. */
    private void updateCharge() {
        if (root != null) {
            x = root.getX();
            y = root.getY() + 8;
        }
        if (!acceleratingOwned) {
            acceleratingOwned = true;
            acceleratingDelay = 0x0B;
            acceleratingLoops = 0;
        }
        if (--animTimer >= 0) return;

        int result = 1;
        animCursor++;
        if (animCursor == 1) {
            frame = 0x0A;
        } else {
            animCursor = 0;
            frame = 5;
            if (acceleratingDelay != 0) {
                acceleratingDelay--;
            } else {
                acceleratingLoops++;
                result = -1;
            }
        }
        animTimer = acceleratingDelay;

        if (result > 0 && acceleratingDelay == 4 && tryServices() != null)
            services().playSfx(Sonic3kSfx.CHARGING.id);
        if (result < 0 && acceleratingLoops == 0x20 && root != null)
            root.setControlBit(Fbz2SubbossInstance.CONTROL_LASER_READY);
        if (result < 0 && acceleratingLoops >= 0x40) startBeamSamePass();
    }

    /** {@code loc_701EE}, invoked within the 271st charging call. */
    private void startBeamSamePass() {
        acceleratingOwned = false;
        acceleratingLoops = 0;
        phaseOrdinal = Phase.BEAM.ordinal();
        frame = 6;
        y += 0x3C;
        animCursor = 0;
        animTimer = 0;
        waitWord = 0x1F;
        beamWaitStage = 0;
        if (tryServices() != null) services().playSfx(Sonic3kSfx.BOSS_LASER.id);
    }

    private void updateBeam() {
        // loc_70220 samples collision and P1/P2 routine 4 before advancing raw animation.
        collisionFlags = frame == 8 ? 0xAC : 0;
        sampleNativeHurt();
        advanceBeamRaw();
        if (--waitWord >= 0) return;
        if (beamWaitStage++ == 0) {
            waitWord = 0x1F;
            attemptImpactAllocations();
            return;
        }
        // loc_70294 installs byte_70412 without resetting the already-negative wait word.
        phaseOrdinal = Phase.RETRACT.ordinal();
        animCursor = 0;
        animTimer = 0;
        if (!nativePlayerHurt && root != null)
            root.setStatusBit(Fbz2SubbossInstance.STATUS_CHARACTER_FACE);
    }

    /** Exact reachable path through {@code byte_70406}, including its embedded jump loop. */
    private void advanceBeamRaw() {
        if (--animTimer >= 0) return;
        animCursor++;
        switch (animCursor) {
            case 1 -> frame = 6;
            case 2 -> frame = 0x0A;
            case 3 -> frame = 7;
            case 4 -> frame = 0x0A;
            case 5 -> {
                // $F8,8 jumps to the embedded 0,8,$A,$FC sequence and restarts it.
                animCursor = 6;
                frame = 8;
            }
            case 7 -> frame = 0x0A;
            default -> {
                // Embedded $FC restart.
                animCursor = 6;
                frame = 8;
            }
        }
        animTimer = 0;
    }

    /**
     * Only the first frame of byte_70412 is reachable. The stale negative Obj_Wait word
     * immediately invokes loc_702C0 in this same object update.
     */
    private void updateRetract() {
        collisionFlags = frame == 8 ? 0xAC : 0;
        sampleNativeHurt();
        frame = 7;
        animCursor = 1;
        animTimer = 0;
        phaseOrdinal = Phase.WAIT_DELETE.ordinal();
        waitWord = 0x1F;
        visible = true;
    }

    private void updateDeleteWait() {
        collisionFlags = 0;
        visible = false;
        if (--waitWord >= 0) return;
        if (root == null || root.cyclesRemaining() > 0)
            if (tryServices() != null)
                S3kFbzEventWriteSupport.setScreenShakeState(services(), false, 0, 0);
        phaseOrdinal = Phase.DELETE_PENDING.ordinal();
    }

    private void sampleNativeHurt() {
        if (tryServices() == null) return;
        for (PlayableEntity entity : services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (entity instanceof AbstractPlayableSprite sprite && sprite.isHurt()) {
                nativePlayerHurt = true;
                break;
            }
        }
    }

    /** {@code loc_70262}: free rumble allocation first, child explosion allocation second. */
    private void attemptImpactAllocations() {
        if (impactAllocationsAttempted) return;
        impactAllocationsAttempted = true;
        if (tryServices() == null || services().objectManager() == null) return;
        spawnFreeChild(Fbz2SubbossRumbleController::new);
        spawnAfterCurrentSibling(() -> new Fbz2SubbossExplosionController(x, y + 0x60, familySlot));
    }

    @Override public int getCollisionFlags() { return collisionFlags; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }
    @Override public TouchResponseProfile getTouchResponseProfile() {
        return Fbz2SubbossInstance.CONTINUOUS_ENEMY_TOUCH_PROFILE;
    }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return Fbz2SubbossInstance.CONTINUOUS_ENEMY_TOUCH_PROFILE;
    }
    @Override public Fbz2SubbossLaserChild recreateForRewind(RewindRecreateContext context) {
        return new Fbz2SubbossLaserChild(context.spawn());
    }
    @Override public int getPriorityBucket() { return 1; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) return;
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ2_SUBBOSS);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(frame, x, y, false, false);
    }
}
