package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/** FBZ2's fixed-position {@code Obj_EggCapsule}, kept as its native real-SST graph. */
public final class FbzEndEggCapsuleInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    private static final int[] FRAGMENT_X = {0, -0x10, 0x10, -0x18, 0x18};
    private static final int[] ANIMAL_X = {0, -8, 8, 0x10, -0x10, -0x18, 0x18, -4, 4};

    private boolean initialized;
    private boolean buttonSpawnAttempted;
    private boolean buttonPressed;
    private boolean opened;
    private boolean releaseGraphAttempted;
    private boolean resultsAllocationAttempted;
    private boolean tailsEndingPoseApplied;
    private int mappingFrame;
    private int postOpenTimer;
    private int fragmentAttempts;
    private int firstFragmentSlot = -2;

    public FbzEndEggCapsuleInstance(int x, int y) {
        this(new ObjectSpawn(x, y, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, 0));
    }

    public FbzEndEggCapsuleInstance(ObjectSpawn spawn) {
        super(spawn, "FBZEndEggCapsule");
    }

    @Override public void update(int frameCounter, PlayableEntity updatePlayer) {
        if (!initialized) {
            initialized = true;
            spawnButtonOnce();
            return;
        }
        if (!opened) {
            if (buttonPressed) openOnce();
            return;
        }
        if (!resultsAllocationAttempted) {
            postOpenTimer--;
            if (postOpenTimer < 0) tryStartResults(updatePlayer);
            return;
        }
        applyNativeP2EndingPoseHandshake();
    }

    private void spawnButtonOnce() {
        if (buttonSpawnAttempted) return;
        buttonSpawnAttempted = true;
        spawnAfterCurrentSibling(() -> new FbzEndEggCapsuleButtonInstance(
                new ObjectSpawn(getX(), getY() - 0x24, Sonic3kObjectIds.EGG_CAPSULE,
                        0, 0, false, 0), this));
    }

    void signalButtonPressed() { buttonPressed = true; }

    private void openOnce() {
        opened = true;
        mappingFrame = 1;
        postOpenTimer = 0x40;
        PlayableEntity candidate = services().playerQuery().nativeP2OrNull();
        if (candidate instanceof AbstractPlayableSprite p2 && p2.getCpuController() != null) {
            // sub_865DE: st Ctrl_2_locked. Check_TailsEndPose owns the later clear.
            p2.getCpuController().setController2SignedLocked(true);
        }
        spawnReleaseGraphOnce();
    }

    private void spawnReleaseGraphOnce() {
        if (releaseGraphAttempted) return;
        releaseGraphAttempted = true;
        for (int i = 0; i < FRAGMENT_X.length; i++) {
            int index = i;
            fragmentAttempts++;
            FbzEndEggCapsuleFragmentInstance fragment = spawnChild(() -> new FbzEndEggCapsuleFragmentInstance(new ObjectSpawn(
                    getX() + FRAGMENT_X[index], getY() - 8, Sonic3kObjectIds.EGG_CAPSULE,
                    index << 1, 0, false, 0)));
            if (i == 0) firstFragmentSlot = fragment.getSlotIndex();
        }
        for (int i = 0; i < ANIMAL_X.length; i++) {
            int index = i;
            spawnChild(() -> new FbzEndEggCapsuleAnimalInstance(new ObjectSpawn(
                    getX() + ANIMAL_X[index], getY() - 4, Sonic3kObjectIds.EGG_CAPSULE,
                    index << 1, 0, false, 0)));
        }
        spawnChild(() -> new FbzEndEggCapsuleExplosionController(new ObjectSpawn(
                getX(), getY(), Sonic3kObjectIds.EGG_CAPSULE, 8, 0, false, 0)));
    }

    private void tryStartResults(PlayableEntity updatePlayer) {
        AbstractPlayableSprite p1 = nativeP1(updatePlayer);
        if (!eligibleForEndingPose(p1)) return;
        resultsAllocationAttempted = true;
        setEndingPose(p1);
        if (services().gameState() != null) services().gameState().setEndOfLevelActive(true);
        PlayerCharacter character = services().configuration() == null
                ? PlayerCharacter.SONIC_ALONE
                : S3kRuntimeStates.resolvePlayerCharacter(
                        services().zoneRuntimeRegistry(), services().configuration());
        int act = services().currentAct();
        spawnFreeChild(() -> new S3kResultsScreenObjectInstance(character, act));
    }

    private AbstractPlayableSprite nativeP1(PlayableEntity fallback) {
        if (tryServices() != null && services().playerQuery() != null) {
            PlayableEntity queried = services().playerQuery().mainPlayerOrNull();
            if (queried instanceof AbstractPlayableSprite sprite) return sprite;
        }
        return fallback instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    private boolean eligibleForEndingPose(AbstractPlayableSprite player) {
        return player != null && !player.getDead() && !player.getAir();
    }

    private void applyNativeP2EndingPoseHandshake() {
        if (tailsEndingPoseApplied || services().gameState() == null
                || !services().gameState().isEndOfLevelActive()) return;
        PlayableEntity candidate = services().playerQuery().nativeP2OrNull();
        if (!(candidate instanceof AbstractPlayableSprite p2) || !eligibleForEndingPose(p2)) return;
        tailsEndingPoseApplied = true;
        if (p2.getCpuController() != null) {
            p2.getCpuController().setController2SignedLocked(false);
            p2.getCpuController().queueNativeEndingPoseForNextPlayerSlot();
        }
        else setEndingPose(p2);
    }

    private void setEndingPose(AbstractPlayableSprite sprite) {
        ObjectControlState.nativeBit7FullControl().applyTo(sprite);
        sprite.setSpindash(false);
        sprite.setPushing(false);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAnimationId(Sonic3kAnimationIds.VICTORY);
    }

    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x2B, 0x18, 0x18); }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return 4; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.EGG_CAPSULE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
        }
    }

    static int[] fragmentOffsets() { return FRAGMENT_X.clone(); }
    static int[] animalOffsets() { return ANIMAL_X.clone(); }
    void signalButtonPressedForTest() { signalButtonPressed(); }
    boolean resultsAllocationAttemptedForTest() { return resultsAllocationAttempted; }
    boolean isOpenedForTest() { return opened; }
    boolean isOpened() { return opened; }
    int fragmentAttemptsForTest() { return fragmentAttempts; }
    int firstFragmentSlotForTest() { return firstFragmentSlot; }
    boolean tailsEndingPoseAppliedForTest() { return tailsEndingPoseApplied; }
}
