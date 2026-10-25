package typinghog.play;

import com.openggf.game.GameServices;
import com.openggf.stageplay.runtime.StageplayWorldPresentation;

/**
 * Draw-only projection of the held native world. Stageplay's owner samples private scroll from
 * the current camera and draws current native sprite poses instead of replaying the parked SAT.
 * The normal 400x224, zoom-one path needs no composition framebuffer or additional world.
 */
final class SectionPresentation implements AutoCloseable {
    private final StageplayWorldPresentation world = new StageplayWorldPresentation();
    private boolean closed;

    void draw() {
        if (closed) throw new IllegalStateException("Section presentation is closed");
        var graphics = GameServices.graphics();
        var level = GameServices.level();
        var camera = GameServices.camera();
        world.draw(graphics, level, camera.getX(), camera.getY(), 1, camera.getWidth(), camera.getHeight(), () -> {
            Runnable draw = () -> {
                level.drawCinematicWorld(GameServices.sprites());
                // Tile/sprite commands execute inside the private scroll/camera/fade scope.
                graphics.flush();
            };
            // As in Stageplay, a directed section owns its reveal. The held native
            // Pal_FromBlack timer and published CRAM generation remain unchanged.
            if (level.getAnimatedPaletteManager() instanceof com.openggf.game.internal.LevelPaletteFadeOwner fade
                    && fade.presentsLevelFadeFromBlack())
                com.openggf.graphics.PaletteUploadPresentation.withoutDisplayedFade(graphics, draw);
            else draw.run();
        });
    }

    @Override public void close() {
        if (closed) return;
        world.close();
        closed = true;
    }
}
