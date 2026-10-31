package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.animation.InitialLevelAnimationPass;
import com.openggf.level.objects.InitialObjectDispatchScope;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.sprites.managers.ProcessSpritesEpoch;
import com.openggf.sprites.managers.SpriteManager;

/**
 * Adapts runtime owners to the ROM-ordered initial {@code Process_Sprites}
 * coordinator without making {@link LevelManager} own the dispatch details.
 */
final class InitialProcessSpritesExecutor {
    void execute(
            GameModule gameModule,
            SpriteManager spriteManager,
            ObjectManager objectManager,
            Camera camera,
            ZoneFeatureProvider zoneFeatureProvider,
            AnimatedPatternManager animatedPatternManager,
            int frameCounter) {
        var original = ObjectManagerInternalAccess.captureInitialDispatch(objectManager);
        // Optional game/profile ownership: S3K Level/loc_6310 deforms the
        // initial scene before its loc_6468 player/object setup walk. This
        // executor is reached only by an admitted fresh-assembly token.
        var profile = gameModule.getLevelInitProfile();
        original.requireCurrent();
        if (profile instanceof com.openggf.game.internal.InitialLevelScrollPreparation preparation) {
            preparation.prepareInitialLevelScroll();
            original.requireCurrent();
        }
        InitialFixedSstDispatcher fixed =
                gameModule.createInitialFixedSstDispatcher(
                        spriteManager, objectManager, zoneFeatureProvider);
        original.requireCurrent();
        InitialDynamicSstDispatcher dynamic = dynamicDispatcher(objectManager, spriteManager, camera, original);
        CollisionListSstDispatcher collision = collisionDispatcher(objectManager);
        int objectOrdinal = objectManager.getFrameCounter() + 1;
        new InitialProcessSpritesCoordinator(original::requireCurrent).execute(new InitialProcessSpritesContext(
                new InitialProcessSpritesStages(dynamic, spriteManager, collision, fixed),
                new ProcessSpritesEpoch(frameCounter, objectOrdinal, false)));
        original.requireCurrent();
        // S3K loc_6468: Process_Sprites / Render_Sprites / Animate_Tiles precede
        // LevelLoop. Optional semantic ownership excludes palette and frame clocks.
        if (animatedPatternManager instanceof InitialLevelAnimationPass initialAnimation) {
            initialAnimation.runInitialLevelAnimationPass();
            original.requireCurrent();
        }
    }

    private static InitialDynamicSstDispatcher dynamicDispatcher(
            ObjectManager objectManager, SpriteManager spriteManager, Camera camera,
            ObjectManagerInternalAccess.InitialDispatch original) {
        return new InitialDynamicSstDispatcher() {
            private InitialObjectDispatchScope scope;

            @Override
            public InitialObjectDispatchScope begin(ProcessSpritesEpoch epoch) {
                original.requireCurrent();
                int cameraX = camera.getX();
                original.requireCurrent();
                var player = spriteManager.getMainPlayable();
                original.requireCurrent();
                var sidekicks = spriteManager.getSidekicks();
                original.requireCurrent();
                scope = original.begin(cameraX, player, sidekicks);
                return scope;
            }

            @Override
            public void loadSprites() {
                objectManager.loadInitialDynamicSlots(scope);
            }

            @Override
            public void processAbsoluteDynamicSlot3() {
                objectManager.processInitialAbsoluteDynamicSlot3(scope);
            }

            @Override
            public void processManagedDynamicSlots4Through93() {
                objectManager.processInitialDynamicSlots(scope);
            }
        };
    }

    private static CollisionListSstDispatcher collisionDispatcher(ObjectManager objectManager) {
        return new CollisionListSstDispatcher() {
            @Override
            public void freezePreviousReadView() {
                objectManager.freezeInitialCollisionResponseReadView();
            }

            @Override
            public void resetCurrentBuild() {
                objectManager.resetInitialCollisionResponseBuild();
            }

            @Override
            public void markDynamicBuildComplete() {
                objectManager.markInitialDynamicCollisionBuildComplete();
            }

            @Override
            public void captureCompletedBuild() {
                objectManager.captureInitialCollisionResponseBuild();
            }
        };
    }
}
