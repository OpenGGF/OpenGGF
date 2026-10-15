package chaos;

import com.openggf.control.InputHandler;
import com.openggf.game.TitleScreenProvider;

/** Native intro/backdrop, followed by an explicit saved choice and the native one-player route. */
public final class ChaosTitle implements TitleScreenProvider {
    private final TitleScreenProvider stock;
    private final ChaosMenu menu;
    private boolean offered,exiting;
    public ChaosTitle(TitleScreenProvider stock,ChaosMenu menu) {this.stock=java.util.Objects.requireNonNull(stock);this.menu=menu;}
    @Override public void initialize() {offered=exiting=false;menu.leaveTitle();stock.initialize();}
    @Override public void update(InputHandler input) {
        if(exiting)return;
        if(!offered && stock.getState()==State.ACTIVE) {offered=true;menu.openTitle();}
        if(offered) {menu.titleInput(input);if(menu.consumeLaunch())exiting=true;}
        else stock.update(input);
    }
    @Override public void draw() {stock.draw();}
    /** The title owns its modal; this path draws whether or not a gameplay world exists behind the title. */
    @Override public void drawOverlay(com.openggf.mods.ui.PixelCanvas canvas) {if(offered && menu.title())menu.draw(canvas);}
    @Override public void setClearColor() {stock.setClearColor();}
    @Override public void reset() {stock.reset();menu.leaveTitle();offered=exiting=false;}
    @Override public State getState() {return exiting?State.EXITING:offered?State.ACTIVE:stock.getState();}
    @Override public boolean isExiting() {return exiting;}
    @Override public boolean isActive() {return offered || stock.isActive();}
    @Override public boolean ownsEscapeInput() {return offered;}
    @Override public TitleScreenAction consumeExitAction() {return TitleScreenAction.ONE_PLAYER;}
    @Override public int startZoneIndex() {return stock.startZoneIndex();}
    @Override public int startActIndex() {return stock.startActIndex();}
    @Override public boolean supportsLevelSelectOverlay() {return stock.supportsLevelSelectOverlay();}
    @Override public void drawFrozenForLevelSelect() {stock.drawFrozenForLevelSelect();}
    @Override public void setExitToLevelHandler(Runnable handler) {stock.setExitToLevelHandler(handler);}
}
