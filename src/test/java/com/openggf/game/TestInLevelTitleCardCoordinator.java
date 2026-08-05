package com.openggf.game;

import com.openggf.level.LevelManager;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TestInLevelTitleCardCoordinator {

    @Test
    void resultsTitleCardLocksControlBeforeDelegatingTheFreshPlayerPrelude() {
        GameModule module = mock(GameModule.class);
        LevelInitProfile profile = mock(LevelInitProfile.class);
        SpriteManager spriteManager = mock(SpriteManager.class);
        LevelManager levelManager = mock(LevelManager.class);
        AbstractPlayableSprite playable = mock(AbstractPlayableSprite.class);
        @SuppressWarnings("unchecked")
        Consumer<Boolean> controlLock = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Supplier<GameModule> moduleSupplier = mock(Supplier.class);
        when(moduleSupplier.get()).thenReturn(module);
        when(module.getLevelInitProfile()).thenReturn(profile);
        when(profile.freshMainPlayablePreludeFrames()).thenReturn(3);

        InLevelTitleCardCoordinator.prepareResultsTransition(
                playable, controlLock, moduleSupplier, spriteManager, levelManager);

        InOrder order = inOrder(controlLock, moduleSupplier, module, profile, spriteManager);
        order.verify(controlLock).accept(true);
        order.verify(moduleSupplier).get();
        order.verify(module).getLevelInitProfile();
        order.verify(profile).freshMainPlayablePreludeFrames();
        // A results return runs the prelude for real: the ROM's pass is a
        // BuildSprites whose player DPLC the recorder captures in the
        // transition gap, so it must not be art-priming only.
        order.verify(spriteManager)
                .warmUpFreshMainPlayableOnly(3, levelManager, playable, false);
    }

    @Test
    void resultsTitleCardStillLocksControlWhenTheMainSpriteIsNotPlayable() {
        Sprite sprite = mock(Sprite.class);
        SpriteManager spriteManager = mock(SpriteManager.class);
        LevelManager levelManager = mock(LevelManager.class);
        @SuppressWarnings("unchecked")
        Consumer<Boolean> controlLock = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Supplier<GameModule> moduleSupplier = mock(Supplier.class);

        InLevelTitleCardCoordinator.prepareResultsTransition(
                sprite, controlLock, moduleSupplier, spriteManager, levelManager);

        verify(controlLock).accept(true);
        verify(moduleSupplier, never()).get();
        verifyNoInteractions(spriteManager);
    }
}
