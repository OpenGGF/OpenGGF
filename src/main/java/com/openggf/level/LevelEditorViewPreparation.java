package com.openggf.level;

import com.openggf.game.session.WorldSession;
import com.openggf.graphics.PaletteUploadPresentation;

/** Selects the editor's render source and retires native presentation latches without gameplay ticks. */
final class LevelEditorViewPreparation {
    private LevelEditorViewPreparation() { }

    static void restoreSourceContext(LevelManager manager, WorldSession world) {
        manager.currentZone = world.getCurrentZone();
        manager.currentAct = world.getCurrentAct();
        manager.setApparentAct(world.getApparentAct());
        manager.gameModule = world.getGameModule();
    }

    static Level prepare(LevelManager manager, Level requested, WorldSession world) {
        manager.authoringView = true;
        // Editor rendering has no native V-int publication to release this latch or fade.
        PaletteUploadPresentation.release(manager.graphicsManager);
        manager.graphicsManager.clearPaletteFadePresentation();
        return requested != null ? requested : world.getCurrentLevel();
    }
}
