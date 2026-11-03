package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.objects.AbstractS3kUprightEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

/**
 * HCZ's retained boss/results adapter for the shared upright {@code Obj_EggCapsule}.
 * Opening, animals, explosions, drawing and geometry belong to the common owner.
 * This adapter preserves the separate native button SST and the retained boss's
 * results/control/camera handoff rather than inventing a second capsule behavior.
 */
public class HczEndBossEggCapsuleInstance extends AbstractS3kUprightEggCapsuleInstance
        implements SpawnCoordinateRewindRecreatable, RomObjectCodePointerProvider {
    private final PlayerControlOwners resultControlOwners = new PlayerControlOwners();
    private boolean buttonSpawned;
    private boolean tailsEndingPoseApplied;

    public HczEndBossEggCapsuleInstance(int x, int y) {
        super(x, y, "HCZEggCapsule");
    }

    private HczEndBossEggCapsuleInstance() { this(0, 0); }

    @Override public int romObjectCodePointerHighWord() { return 0x0008; }

    @Override public void releasePlayersForVisualDirection() {
        for (var sprite : resultControlOwners.players()) {
            ObjectControlState.none().applyTo(sprite);
            sprite.setControlLocked(false);
        }
        resultControlOwners.clear();
        super.releasePlayersForVisualDirection();
    }

    // HCZ retains a real button slot: the parent contributes only its body solid.
    @Override public int getPieceCount() { return 1; }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.AUTO_AFTER_UPDATE; }

    @Override public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!buttonSpawned) {
            buttonSpawned = true;
            spawnChild(() -> new HczEndBossEggCapsuleButton(this, getX(), getY() - 0x24));
        }
        if (!opened) {
            if (isButtonTriggered()) openCapsule();
        } else if (!resultsStarted) {
            tickExplosionController();
            // sub_868F8 pre-decrements $40 and waits for signed underflow,
            // exactly as the shared capsule owner does (sonic3k.asm:181991-181993).
            if (--postOpenTimer < 0
                    && playerEntity instanceof AbstractPlayableSprite player && !player.getAir()) {
                startResults(player);
            }
        } else if (services().gameState().isEndOfLevelActive()) {
            advanceTailsEndingPoseCheck();
        }
    }

    void signalButtonPressed() { triggerButton(); }

    @Override protected void spawnCapsuleAnimal(ObjectSpawn animalSpawn, int delay, int artVariant) {
        spawnFreeChild(() -> new EggPrisonAnimalInstance(animalSpawn, delay, artVariant));
    }

    private void startResults(AbstractPlayableSprite player) {
        if (resultsStarted) return;
        resultsStarted = true;
        // HCZEndBoss_StartPostDefeat already owns _unkFAA8. Creating results
        // neither re-arms that global flag nor transfers the retained root's wait.
        // sub_868F8 calls Set_PlayerEndingPose in this dispatch, after the
        // player has animated (sonic3k.asm:181991-182009,182073-182084).
        // Publishing anim now retains its current mapping until the next
        // player pass; the CPU pose request likewise targets the next P2 slot.
        lockForResults(player);
        advanceTailsEndingPoseCheck();
        // sub_868F8 calls AllocateObject, which scans from the beginning of
        // Dynamic_object_RAM rather than allocating after the capsule. The
        // result owner therefore takes the lowest available SST and reaches
        // Obj_LevelResultsInit on the following Process_Sprites pass.
        spawnFreeChild(
                () -> new HczEndBossResultsScreenObjectInstance(
                        resolvePlayerCharacter(), services().currentAct()));
    }

    private void advanceTailsEndingPoseCheck() {
        if (tailsEndingPoseApplied) {
            return;
        }
        if (!(services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite sidekick)
                || sidekick.isPreventTailsRespawn()
                || sidekick.getAir()
                || sidekick.getDead()) {
            return;
        }

        // Check_TailsEndPose clears Ctrl_2_locked immediately before tail-calling
        // Set_PlayerEndingPose (sonic3k.asm:182067-182088).
        tailsEndingPoseApplied = true;
        if (sidekick.getCpuController() != null) {
            sidekickPoseOwners.add(sidekick);
            sidekick.getCpuController().queueNativeEndingPoseForNextPlayerSlot();
        }
    }

    private void lockForResults(AbstractPlayableSprite sprite) {
        resultControlOwners.add(sprite);
        ObjectControlState.nativeBit7FullControl().applyTo(sprite);
        // Set_PlayerEndingPose does not write Ctrl_1_locked/Ctrl_2_locked.
        sprite.setControlLocked(false);
        sprite.setSpindash(false);
        sprite.setPushing(false);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAnimationId(Sonic3kAnimationIds.VICTORY);
    }

    private static final class HczEndBossResultsScreenObjectInstance
            extends S3kResultsScreenObjectInstance {

        private HczEndBossResultsScreenObjectInstance(PlayerCharacter character, int act) {
            super(character, act);
        }

        private HczEndBossResultsScreenObjectInstance() {
            super(true);
        }

        @Override
        protected boolean skipsSameFrameUpdateAfterSpawn() {
            // sub_868F8 uses AllocateObject for Obj_LevelResults. Keep its
            // first Obj_LevelResultsInit dispatch on the following
            // Process_Sprites pass (sonic3k.asm:181991-182009,
            // 182027-182046).
            return true;
        }

        // Obj_LevelResultsInit submits art on its initialization dispatch
        // (sonic3k.asm:182118-182137); no additional HCZ dispatch is inserted.

        @Override
        protected boolean shouldRestorePlayerControlsOnExit() {
            // Obj_LevelResults only clears _unkFAA8. The retained HCZ root
            // restores controls when its actual SST dispatch observes that write.
            return false;
        }

        @Override
        protected boolean shouldRestoreCameraBoundsOnExit(int zone, int act) {
            // loc_6B154 retains the boss-arena X lock for the geyser handoff.
            return false;
        }

        @Override
        public HczEndBossResultsScreenObjectInstance recreateForRewind(RewindRecreateContext ctx) {
            return ObjectConstructionContext.construct(ctx.objectServices(),
                    HczEndBossResultsScreenObjectInstance::new);
        }
    }
}
