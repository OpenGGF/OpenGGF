package com.openggf.level;

import com.openggf.level.objects.InitialObjectDispatchScope;
import com.openggf.sprites.managers.InitialPlayableInput;

/**
 * Reconstructs the one initial S3K {@code Process_Sprites} walk across the
 * engine's separately-owned SST domains.
 *
 * <p>ROM order: {@code Load_Sprites}, Player 1, Player 2, collision-list reset,
 * absolute dynamic slot 3, managed dynamic slots 4-93, then fixed slots 94-109
 * (docs/skdisasm/sonic3k.asm:7880-7888,36005-36048;
 * docs/skdisasm/sonic3k.constants.asm:310-330).
 */
final class InitialProcessSpritesCoordinator {
    enum Checkpoint {
        AFTER_PLAYABLES_BEFORE_RESET,
        AFTER_DYNAMIC_BEFORE_FIXED
    }

    private final java.util.function.Consumer<Checkpoint> checkpoint;
    private final Runnable requireCurrent;

    InitialProcessSpritesCoordinator() {
        this(ignored -> { });
    }

    InitialProcessSpritesCoordinator(Runnable requireCurrent) {
        this(ignored -> { }, requireCurrent);
    }

    InitialProcessSpritesCoordinator(java.util.function.Consumer<Checkpoint> checkpoint) {
        this(checkpoint, () -> { });
    }

    private InitialProcessSpritesCoordinator(java.util.function.Consumer<Checkpoint> checkpoint,
            Runnable requireCurrent) {
        this.checkpoint = checkpoint;
        this.requireCurrent = requireCurrent;
    }

    void execute(InitialProcessSpritesContext context) {
        InitialProcessSpritesStages stages = context.stages();
        InitialDynamicSstDispatcher dynamic = stages.dynamic();
        try (InitialObjectDispatchScope ignored = dynamic.begin(context.epoch())) {
            stages.fixed().onInitialScopeAcquired();
            requireCurrent.run();
            stages.collisionList().freezePreviousReadView();
            requireCurrent.run();
            dynamic.loadSprites();
            requireCurrent.run();
            stages.playables().processInitialPlayableSlots(
                    context.epoch(), InitialPlayableInput.nativeNeutral());
            requireCurrent.run();
            checkpoint.accept(Checkpoint.AFTER_PLAYABLES_BEFORE_RESET);
            requireCurrent.run();
            stages.collisionList().resetCurrentBuild();
            requireCurrent.run();
            dynamic.processAbsoluteDynamicSlot3();
            requireCurrent.run();
            dynamic.processManagedDynamicSlots4Through93();
            requireCurrent.run();
            stages.collisionList().markDynamicBuildComplete();
            requireCurrent.run();
            checkpoint.accept(Checkpoint.AFTER_DYNAMIC_BEFORE_FIXED);
            requireCurrent.run();
            stages.fixed().processPostDynamicFixedSlots(context.epoch());
            requireCurrent.run();
            stages.collisionList().captureCompletedBuild();
            requireCurrent.run();
        }
    }
}
