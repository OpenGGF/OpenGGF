package secondstrike;

import com.openggf.control.InputHandler;
import com.openggf.game.TitleScreenProvider;
import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.PixelCanvas;

import java.util.Objects;

/** Small identity badge over the interactive native title; native input and fades are unchanged. */
final class SecondStrikeTitle implements TitleScreenProvider {
    private final TitleScreenProvider nativeTitle;
    SecondStrikeTitle(TitleScreenProvider nativeTitle) {
        this.nativeTitle = Objects.requireNonNull(nativeTitle);
    }
    @Override public void initialize() { nativeTitle.initialize(); }
    @Override public void update(InputHandler input) { nativeTitle.update(input); }
    @Override public void draw() { nativeTitle.draw(); }
    @Override public void drawOverlay(PixelCanvas canvas) {
        nativeTitle.drawOverlay(canvas);
        if (nativeTitle.getState() != State.ACTIVE) return;
        int labelX = (canvas.width() - CompactFont.width("SECOND STRIKE", 1)) / 2;
        canvas.fill(labelX - 7, 195, 91, 12, 0xEF102438);
        CompactFont.draw(canvas, "SECOND STRIKE", labelX, 198, 1, 0xFFFFD685);
    }
    @Override public void setClearColor() { nativeTitle.setClearColor(); }
    @Override public void reset() { nativeTitle.reset(); }
    @Override public State getState() { return nativeTitle.getState(); }
    @Override public boolean isExiting() { return nativeTitle.isExiting(); }
    @Override public boolean isActive() { return nativeTitle.isActive(); }
    @Override public boolean ownsEscapeInput() { return nativeTitle.ownsEscapeInput(); }
    @Override public boolean supportsLevelSelectOverlay() { return nativeTitle.supportsLevelSelectOverlay(); }
    @Override public void drawFrozenForLevelSelect() { nativeTitle.drawFrozenForLevelSelect(); }
    @Override public TitleScreenAction consumeExitAction() { return nativeTitle.consumeExitAction(); }
    @Override public int startZoneIndex() { return nativeTitle.startZoneIndex(); }
    @Override public int startActIndex() { return nativeTitle.startActIndex(); }
    @Override public void setExitToLevelHandler(Runnable handler) { nativeTitle.setExitToLevelHandler(handler); }
}
