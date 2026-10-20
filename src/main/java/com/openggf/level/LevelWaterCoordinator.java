package com.openggf.level;

import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.rules.GameRules;
import com.openggf.data.Rom;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;

import java.io.IOException;

/**
 * Owns level water lifecycle and playable underwater state updates.
 */
final class LevelWaterCoordinator {
    private static final WaterDataProvider DRY_CONTRIBUTION = new WaterDataProvider() {
        public boolean hasWater(int zone, int act, PlayerCharacter character) { return false; }
        public int getStartingWaterLevel(int zone, int act) { return 0; }
        public Palette[] getUnderwaterPalette(Rom rom, int zone, int act, PlayerCharacter character) { return null; }
        public com.openggf.game.DynamicWaterHandler getDynamicHandler(int zone, int act, PlayerCharacter character) { return null; }
    };
    private final LevelManager levelManager;

    LevelWaterCoordinator(LevelManager levelManager) {
        this.levelManager = levelManager;
    }

    void initialize() throws IOException {
        initialize(false);
    }

    /**
     * ROM: CheckLevelForWater compares Apparent_zone_and_act to
     * Current_zone_and_act. During seamless transitions Apparent != Current,
     * which enables water in cases that a direct load would disable.
     */
    void initialize(boolean seamlessTransition) throws IOException {
        // BEGIN HCZ1_DIRECT_FF_SELECTED_LOAD_BEGIN: invalidate before any original selection can fail.
        var nemesisPublication = waterSchedulePublication();
        if (nemesisPublication != null) nemesisPublication.beginWaterScheduleSelection(true);
        var nemesisLevel = levelManager.level;
        // END HCZ1_DIRECT_FF_SELECTED_LOAD_BEGIN
        GameModule gameModule = levelManager.gameModule;
        WaterDataProvider waterProvider = levelManager.hasContributedZoneRuntime()
                ? java.util.Objects.requireNonNullElse(levelManager.contributedZoneRuntime.waterProviderOrNull(), DRY_CONTRIBUTION)
                : gameModule != null ? gameModule.getWaterDataProvider() : null;
        int featureZone = levelManager.getFeatureZoneId();
        int featureAct = levelManager.getFeatureActId();
        if (waterProvider != null) {
            Rom rom = levelManager.worldDataSource().rom().orElse(null);
            PlayerCharacter character = PlayerCharacter.SONIC_AND_TAILS;
            LevelEventProvider lep = gameModule.getLevelEventProvider();
            if (lep instanceof AbstractLevelEventManager alem) {
                character = alem.getPlayerCharacter();
            }
            levelManager.waterSystem.loadForLevelFromProvider(waterProvider, rom,
                    featureZone, featureAct, character, seamlessTransition);
        } else if (!levelManager.hasContributedZoneRuntime() && levelManager.zoneFeatureProvider != null
                && levelManager.zoneFeatureProvider.hasWater(featureZone)) {
            Rom rom = levelManager.worldDataSource().rom().orElse(null);
            @SuppressWarnings("deprecation")
            Runnable fallback = () -> levelManager.waterSystem.loadForLevel(
                    rom, featureZone, featureAct, levelManager.level.getObjects());
            if (!levelManager.waterSystem.hasWater(featureZone, featureAct)) {
                fallback.run();
            }
        }
        // BEGIN HCZ1_DIRECT_FF_SELECTED_LOAD_END: no remap/provider/water/camera requery.
        if (nemesisPublication != null)
            nemesisPublication.retainWaterScheduleSelection(nemesisLevel, featureZone, featureAct, true);
        // END HCZ1_DIRECT_FF_SELECTED_LOAD_END
    }

    void advanceDynamicWaterLevel() {
        // BEGIN HCZ1_DIRECT_FF_SELECTED_ORDINARY_BEGIN: the previous decision was consumed at VBlank.
        var nemesisPublication = waterSchedulePublication();
        if (nemesisPublication != null) nemesisPublication.beginWaterScheduleSelection(false);
        var nemesisLevel = levelManager.level;
        // END HCZ1_DIRECT_FF_SELECTED_ORDINARY_BEGIN
        int featureZone = levelManager.getFeatureZoneId();
        int featureAct = levelManager.getFeatureActId();
        if (levelManager.level != null
                && levelManager.waterSystem != null
                && levelManager.waterSystem.hasWater(featureZone, featureAct)) {
            levelManager.waterSystem.updateDynamic(
                    featureZone, featureAct, levelManager.camera.getX(), levelManager.camera.getY());
            levelManager.waterSystem.update();
        }
        // BEGIN HCZ1_DIRECT_FF_SELECTED_ORDINARY_END: carry the unconditional original locals, even without P1.
        if (nemesisPublication != null)
            nemesisPublication.retainWaterScheduleSelection(nemesisLevel, featureZone, featureAct, false);
        // END HCZ1_DIRECT_FF_SELECTED_ORDINARY_END
    }

    // BEGIN HCZ1_DIRECT_FF_SELECTED_OWNER: raw world-owned capability; no module/provider selection.
    private com.openggf.game.internal.WorldPlcPatternPublication waterSchedulePublication() {
        var world = levelManager.getWorldSession();
        return world != null && com.openggf.game.session.WorldPlcLifecycleAccess.owned(world)
                instanceof com.openggf.game.internal.WorldPlcPatternPublication publication ? publication : null;
    }
    // END HCZ1_DIRECT_FF_SELECTED_OWNER

    void advanceDynamicWaterLevelAfterPlayerPhysicsIfNeeded() {
        if (!levelManager.advanceWaterLevelBeforePlayerPhysics()) {
            advanceDynamicWaterLevel();
        }
    }

    void updatePlayableWaterStatesForCurrentLevel() {
        int featureZone = levelManager.getFeatureZoneId();
        int featureAct = levelManager.getFeatureActId();
        if (levelManager.level == null
                || levelManager.waterSystem == null
                || !levelManager.waterSystem.hasWater(featureZone, featureAct)) {
            return;
        }
        Sprite player = levelManager.spriteManager.getSprite(levelManager.resolveMainCharacterCode());
        AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
        if (playable == null) {
            return;
        }
        int waterY = levelManager.waterSystem.getGameplayWaterLevelY(featureZone, featureAct);
        updatePlayableWaterStates(playable, waterY);
    }

    void updatePlayableWaterStateForCurrentLevel(AbstractPlayableSprite playable) {
        int featureZone = levelManager.getFeatureZoneId();
        int featureAct = levelManager.getFeatureActId();
        if (levelManager.level == null
                || levelManager.waterSystem == null
                || !levelManager.waterSystem.hasWater(featureZone, featureAct)
                || playable == null) {
            return;
        }
        int waterY = levelManager.waterSystem.getGameplayWaterLevelY(featureZone, featureAct);
        updatePlayableWaterState(playable, waterY);
    }

    boolean shouldSuppressUnderwaterPalette(int zoneId, int actId) {
        return levelManager.zoneFeatureProvider != null
                && levelManager.zoneFeatureProvider.shouldSuppressUnderwaterPalette(zoneId, actId);
    }

    void restoreWaterFromCheckpoint(LevelLoadContext ctx) {
        if (!ctx.hasWaterState()) {
            return;
        }
        int featureZone = levelManager.getFeatureZoneId();
        int featureAct = levelManager.getFeatureActId();
        if (levelManager.waterSystem.hasWater(featureZone, featureAct)) {
            levelManager.waterSystem.setWaterLevelDirect(featureZone, featureAct, ctx.getCheckpointWaterLevel());
            levelManager.waterSystem.setWaterLevelTarget(featureZone, featureAct, ctx.getCheckpointWaterLevel());
        }
        if (levelManager.zoneFeatureProvider != null) {
            levelManager.zoneFeatureProvider.setWaterRoutine(ctx.getCheckpointWaterRoutine());
        }
    }

    private void updatePlayableWaterStates(AbstractPlayableSprite mainPlayable, int waterY) {
        updatePlayableWaterState(mainPlayable, waterY);
        for (AbstractPlayableSprite sidekick : levelManager.spriteManager.getSidekicks()) {
            if (sidekick != mainPlayable) {
                updatePlayableWaterState(sidekick, waterY);
            }
        }
    }

    void updatePlayableWaterState(AbstractPlayableSprite playable, int waterY) {
        if (playable == null) {
            return;
        }
        if (playable.isHurt() || isDeferredSidekickDeadFallWaterBypass(playable)
                || routineSkipsWaterCheck(playable)) {
            return;
        }
        // Only S3K's water routine skips the velocity change under object_control
        // (sonic3k.asm:22235, :27448). S1's Sonic_Water ("01 Sonic.asm":270-272) and
        // S2's Obj01_InWater (s2.asm:36393-36395) shift x_vel/y_vel unconditionally,
        // and Obj01_Control reaches bsr.w Sonic_Water outside the
        // btst #0,obj_control skip (s2.asm:36236-36251) -- so a character carried
        // into the water by the CPZ spin tube is still quartered.
        if (playable.isObjectControlSuppressesMovement()
                && playable.waterVelocityChangeGatedByObjectControl()) {
            playable.updateWaterStateObjectControlled(waterY);
            return;
        }
        playable.updateWaterState(waterY);
    }

    /**
     * True while a routine that never calls the water check owns this player,
     * so Status_Underwater keeps its value. Every game's death routine skips it:
     * S3K loc_12390/loc_1578E (sonic3k.asm:24518-24533, 29263-29289), S2
     * Obj01_Dead (s2.asm:38255-38270) and S1 Sonic_Death ("01 Sonic.asm":1967-1985).
     * S3K's drown routine $C also skips it, because loc_125AC/loc_15810 never
     * reach Sonic_Water/Tails_Water.
     */
    private static boolean routineSkipsWaterCheck(AbstractPlayableSprite playable) {
        if (playable.getDead()) {
            return true;
        }
        GameRules rules = playable.getGameRules();
        return playable.isDrowningPreDeath()
                && rules != null
                && rules.drowningBubble() != null
                && rules.drowningBubble().drownInstallsDedicatedPlayerRoutine();
    }

    private static boolean isDeferredSidekickDeadFallWaterBypass(AbstractPlayableSprite playable) {
        if (!playable.isCpuControlled()) {
            return false;
        }
        SidekickCpuController cpu = playable.getCpuController();
        return cpu != null && cpu.isDeferredDespawnDeadFallContinuingThisFrame();
    }
}
