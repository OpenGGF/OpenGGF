package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Locked-on {@code Obj_FBZFlamethrower} ($E4), $3CC9E-$3CFCA. */
public final class FbzFlamethrowerObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable {
    private final FbzParticipantStateTable riders = new FbzParticipantStateTable(1);
    private int mappingFrame;
    private int flameAngle;
    private int flameAnimationFrame;
    private int flameAnimationTimer;
    private int standingTimer;

    public FbzFlamethrowerObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZFlamethrower");
        mappingFrame = isInverted() ? 3 : 1;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity ignored) {
        updateFlameAnimation();
        int levelFrameCounter = resolveLevelFrameCounter(vIntRunCount) & 0xFF;
        if (mappingFrame != 2 && (levelFrameCounter & 3) == 0) {
            // loc_3CD4C/loc_3CDD0 read render_flags bit 7 from the previous
            // Render_Sprites pass. That flag uses the object's $10-pixel
            // render extents, not a centre-point viewport check.
            boolean renderBoxOnScreen = isWithinSolidContactBounds();
            if ((levelFrameCounter & 0xF) == 0 && (isLateral() || renderBoxOnScreen)) {
                services().playSfx(Sonic3kSfx.FLAMETHROWER_LOUD.id);
            }
            if (renderBoxOnScreen) spawnFlames();
        }
        applyStandingCheckpoint(checkpointAll());
    }

    private int resolveLevelFrameCounter(int fallbackFrameCounter) {
        ObjectServices objectServices = tryServices();
        // loc_3CD4C/loc_3CDD0 read the low byte at
        // (Level_frame_counter+1).w. ObjectManager's update clock is not that
        // byte and can be phase-shifted relative to the gameplay counter.
        return objectServices != null && objectServices.levelManager() != null
                ? objectServices.levelManager().getFrameCounter() + 1
                : fallbackFrameCounter;
    }

    private void updateFlameAnimation() {
        if (--flameAnimationTimer < 0) {
            flameAnimationTimer = 2;
            flameAnimationFrame = (flameAnimationFrame + 1) & 3;
        }
    }

    private void spawnFlames() {
        if (isLateral()) {
            flameAngle = (flameAngle + 2) & 0xFF;
            spawnAfterCurrentSibling(() -> FbzFlameObjectInstance.lateral(buildSpawnAt(spawn.x(), spawn.y() - 4),
                    flameAngle, flameAnimationFrame, (spawn.renderFlags() & 1) != 0));
            return;
        }
        flameAngle = (flameAngle - 4) & 0x7F;
        int firstAngle = flameAngle;
        spawnAfterCurrentSibling(() -> FbzFlameObjectInstance.rotating(
                buildSpawnAt(spawn.x(), spawn.y() - 4), firstAngle, flameAnimationFrame));
        int oppositeAngle = flameAngle | 0x80;
        spawnAfterCurrentSibling(() -> FbzFlameObjectInstance.rotating(
                buildSpawnAt(spawn.x(), spawn.y() - 4), oppositeAngle, flameAnimationFrame));
        flameAngle = oppositeAngle;
    }

    void applyStandingCheckpoint(SolidCheckpointBatch contacts) {
        for (int slot = 0; slot < riders.size(); slot++) {
            riders.flag(slot, 0, false);
        }
        ObjectManager objects = services().objectManager();
        contacts.perPlayer().forEach((player, contact) -> {
            // SolidObjectFull skips offscreen Player_2 before entering its
            // one-player helper, leaving the object's p2_standing_bit intact.
            // sub_3CE1A drives the trap timer from that persistent status bit,
            // not solely from this frame's helper result.
            boolean standing = contact.standingNow()
                    || (objects != null && objects.hasObjectStandingBit(player, this));
            riders.flag(riders.slot(player), 0, standing);
        });
        updateStandingTrap();
    }

    private void updateStandingTrap() {
        if (isInverted()) return;
        boolean anyRider = false;
        for (int i = 0; i < riders.size(); i++) anyRider |= riders.flag(i, 0);
        if (!anyRider) {
            standingTimer = 0;
            mappingFrame = 1;
            return;
        }
        if (standingTimer == 0) {
            standingTimer = 0x3C;
            mappingFrame = 2;
            flameAngle = 0;
            return;
        }
        if (--standingTimer == 0) {
            services().playerQuery().visitPlayers(
                    ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED,
                    this, FbzFlamethrowerObjectInstance::releaseRider);
        }
    }

    private static void releaseRider(FbzFlamethrowerObjectInstance flame, PlayableEntity player) {
        int slot = flame.riders.slot(player);
        if (!flame.riders.flag(slot, 0)) return;
        flame.riders.flag(slot, 0, false);
        ObjectManager objects = flame.services().objectManager();
        if (objects != null) {
            // sub_3CE1A clears this flamethrower's p1/p2 standing bit before
            // sub_3CBCE launches the corresponding player.
            objects.releaseRidingObject(player, flame);
        }
        FbzTrapSpringObjectInstance.launchPlayer(player, flame.spawn.subtype(), flame.services());
    }

    public boolean isLateral() { return (spawn.subtype() & 0x40) != 0; }
    public boolean isInverted() { return (spawn.subtype() & 0x80) != 0; }
    boolean renderFlipX() { return (spawn.renderFlags() & 1) != 0; }
    int mappingFrame() { return mappingFrame; }
    int standingTimer() { return standingTimer; }
    public int baseSpriteCount() { return isLateral() ? 2 : 3; }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    @Override public int getPriorityBucket() { return 3; }
    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x1B, 8, 9); }
    @Override public SolidRoutineProfile getSolidRoutineProfile() {
        // SolidObject_cont rejects relX only with unsigned BHI, so the exact
        // right boundary (relX == d1 * 2) remains solid.
        return SolidRoutineProfile.fullSolid(false, true, false);
    }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (contact.standing()) riders.flag(riders.slot(player), 0, true);
    }

    @Override
    public void onSolidContactCleared(PlayableEntity player, int frameCounter) {
        riders.flag(riders.slot(player), 0, false);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_FLAMETHROWER);
        if (renderer != null && renderer.isReady()) {
            boolean flipX = renderFlipX();
            renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), flipX, false);
            if (isLateral()) {
                boolean left = flipX;
                renderer.drawFrameIndex(left ? 0x10 : 0,
                        spawn.x() + (left ? -8 : 8), spawn.y(), flipX, false);
                renderer.drawFrameIndex(0x11, spawn.x(), spawn.y(), flipX, false);
            } else {
                // sub_3CE7A consumes d1 from GetSineCosine (cosine), matching
                // the component used by the emitted flames in sub_3CEC0.
                int cosine = com.openggf.physics.TrigLookupTable.cosHex(flameAngle) >> 5;
                int unsignedAngle = flameAngle & 0xFF;
                int firstFrame = unsignedAngle < 0xC0 ? 0x10 : 0;
                int secondFrame = unsignedAngle < 0xC0 ? 0 : 0x10;
                renderer.drawFrameIndex(firstFrame, spawn.x() + cosine, spawn.y(), flipX, false);
                renderer.drawFrameIndex(0x11, spawn.x(), spawn.y(), flipX, false);
                renderer.drawFrameIndex(secondFrame, spawn.x() - cosine, spawn.y(), flipX, false);
            }
        }
    }
}
