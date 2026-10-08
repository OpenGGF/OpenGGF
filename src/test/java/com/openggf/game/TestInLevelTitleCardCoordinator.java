package com.openggf.game;

import org.junit.jupiter.api.Test;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;

class TestInLevelTitleCardCoordinator {

    /**
     * A results-return title card only locks control on entry. The
     * fresh-player prelude deliberately does not run here: the ROM's pass is
     * {@code Level_StartGame}'s, after the locked card loop drains its PLCs,
     * and the release path runs it via
     * {@link TitleCardProvider#shouldRunPlayerPreludeAtRelease()}.
     */
    @Test
    void resultsTitleCardOnlyLocksControlOnEntry() {
        @SuppressWarnings("unchecked")
        Consumer<Boolean> controlLock = mock(Consumer.class);

        InLevelTitleCardCoordinator.prepareResultsTransition(controlLock);

        verify(controlLock).accept(true);
        verifyNoMoreInteractions(controlLock);
    }

    @Test
    void productionResultsEntryOverloadDefersPlayerPreludeUntilRelease() {
        @SuppressWarnings("unchecked")
        Consumer<Boolean> controlLock = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Supplier<GameModule> moduleSupplier = mock(Supplier.class);
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        SpriteManager spriteManager = mock(SpriteManager.class);
        LevelManager levelManager = mock(LevelManager.class);
        GameModule module = mock(GameModule.class);
        LevelInitProfile profile = mock(LevelInitProfile.class);
        org.mockito.Mockito.when(moduleSupplier.get()).thenReturn(module);
        org.mockito.Mockito.when(module.getLevelInitProfile()).thenReturn(profile);
        org.mockito.Mockito.when(profile.freshMainPlayablePreludeFrames()).thenReturn(1);

        InLevelTitleCardCoordinator.prepareResultsTransition(
                player, controlLock, moduleSupplier, spriteManager, levelManager);

        verify(controlLock).accept(true);
        verifyNoMoreInteractions(controlLock);
        verifyNoInteractions(player, spriteManager, levelManager, moduleSupplier);
    }
}
