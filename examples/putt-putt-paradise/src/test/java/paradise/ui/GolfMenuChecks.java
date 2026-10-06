package paradise.ui;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.TitleScreenProvider;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;

import java.util.ArrayList;
import java.util.List;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import static org.lwjgl.glfw.GLFW.*;

/** Real title-provider inputs and queued UI values; no ROM or native display is required. */
public final class GolfMenuChecks {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void same(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
    private static final class Fixture {
        final InputHandler input = new InputHandler();
        final List<GolfMenu.Selection> launches = new ArrayList<>();
        final GolfMenu menu;
        Fixture() {
            menu = new GolfMenu(null, () -> 320, choice -> {
                same(TitleScreenProvider.State.ACTIVE, menuState(), "selection reaches consumer before title exits");
                launches.add(choice);
            });
            menu.initialize();
        }
        TitleScreenProvider.State menuState() { return menu.getState(); }
        void tick() { input.refreshLogicalSnapshot(); menu.update(input); input.update(); }
        void tap(int key) {
            input.handleKeyEvent(key, GLFW_PRESS); tick();
            input.handleKeyEvent(key, GLFW_RELEASE); tick();
        }
        void text(String value) { value.codePoints().forEach(cp -> MenuInput.handleCharEvent(input, cp)); tick(); }
        void mode(int index) { for (int i = 0; i < index; i++) tap(GLFW_KEY_DOWN); tap(GLFW_KEY_ENTER); }
        void focus(GolfMenu.Field field) {
            for (int i = 0; i < 10 && menu.focusedField() != field; i++) tap(GLFW_KEY_DOWN);
            same(field, menu.focusedField(), "field reachable");
        }
        GolfMenu.Selection launch() { focus(GolfMenu.Field.START); tap(GLFW_KEY_ENTER); return launches.getLast(); }
    }

    public static void mouseMenu() throws Exception {
        var f = new Fixture();
        // This is the logical pointer that the shared engine viewport mapper supplies.
        f.menu.updatePointer(new MenuInput.Pointer(35, 146, true, true, false));
        same(GolfMenu.Field.PLAYER_ONE, f.menu.focusedField(), "click chooses Local and opens setup");
        // Local setup: P1, P2, viewport, hole/turn rewinds, ready, back.
        f.menu.updatePointer(new MenuInput.Pointer(40, 124, true, true, false));
        f.menu.updatePointer(new MenuInput.Pointer(40, 169, false, true, false));
        same(0, f.launches.size(), "letterbox clicks cannot launch");
        f.menu.updatePointer(new MenuInput.Pointer(40, 169, true, true, false));
        same(1, f.launches.size(), "one left click launches exactly once");
        same(GolfMenu.Mode.LOCAL, f.launches.getFirst().mode(), "clicked Local match");
        same(GolfMenu.CharacterChoice.TAILS, f.launches.getFirst().playerOne(), "click cycles a setting");
        f.menu.updatePointer(new MenuInput.Pointer(40, 169, true, true, false));
        same(1, f.launches.size(), "an exiting menu ignores more clicks");
        f.menu.initialize();
        f.menu.updatePointer(new MenuInput.Pointer(35, 129, true, true, false));
        f.menu.updatePointer(new MenuInput.Pointer(35, 124, true, false, true));
        same(GolfMenu.Field.MODE, f.menu.focusedField(), "right click returns to mode picker");
    }

    public static void practice() {
        var f = new Fixture(); f.mode(0);
        f.focus(GolfMenu.Field.ACT); f.tap(GLFW_KEY_RIGHT);
        var selected = f.launch();
        same(GolfMenu.Mode.PRACTICE, selected.mode(), "practice choice");
        same(1, selected.actIndex(), "practice selects EHZ2");
        same(1, f.menu.startActIndex(), "title launch uses selected practice act");
        same(0, f.menu.startZoneIndex(), "native EHZ zone");
        same(TitleScreenProvider.State.EXITING, f.menu.getState(), "standard exit");
        same(TitleScreenProvider.TitleScreenAction.ONE_PLAYER, f.menu.consumeExitAction(), "one live native golfer");
        same(TitleScreenProvider.TitleScreenAction.OTHER, f.menu.consumeExitAction(), "exit action consumed once");
        f.tap(GLFW_KEY_ENTER); same(1, f.launches.size(), "exiting title cannot double launch");
    }

    public static void characters() {
        for (boolean tailsOne : List.of(false, true)) for (boolean tailsTwo : List.of(false, true)) {
            var f = new Fixture(); f.mode(1);
            f.focus(GolfMenu.Field.PLAYER_ONE); if (tailsOne) f.tap(GLFW_KEY_RIGHT);
            f.focus(GolfMenu.Field.PLAYER_TWO); if (!tailsTwo) f.tap(GLFW_KEY_RIGHT);
            var selected = f.launch();
            same(GolfMenu.Mode.LOCAL, selected.mode(), "local mode");
            same(tailsOne ? "tails" : "sonic", selected.playerOne().code(), "P1 independent selection");
            same(tailsTwo ? "tails" : "sonic", selected.playerTwo().code(), "P2 independent selection including duplicates");
            same(0, selected.actIndex(), "competition starts EHZ1");
        }
    }

    public static void rewindSettings() {
        for (int mode = 0; mode < 4; mode++) {
            var defaults = new Fixture(); defaults.mode(mode);
            same(paradise.model.RewindAllowance.Rules.defaults(), defaults.launch().rewinds(), "default allowance in every mode");
            for (int hole : new int[]{0, 3, 5, -1}) for (int turn : new int[]{1, 3, -1}) {
                var f = new Fixture(); f.mode(mode); f.focus(GolfMenu.Field.REWINDS_HOLE);
                for (int n = 0; n < 4; n++) {
                    // Navigate each explicit cycle position from the default three.
                    if (new int[]{3, 5, -1, 0}[n] == hole) break;
                    f.tap(GLFW_KEY_RIGHT);
                }
                f.focus(GolfMenu.Field.REWINDS_TURN);
                for (int n = 0; new int[]{1, 3, -1}[n] != turn; n++) f.tap(GLFW_KEY_RIGHT);
                same(new paradise.model.RewindAllowance.Rules(hole, turn), f.launch().rewinds(), "selected rewind rules");
            }
        }
    }

    public static void heldAccept() {
        var f = new Fixture();
        f.input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_PRESS); f.tick();
        for (int i = 0; i < 120; i++) f.tick();
        require(f.launches.isEmpty(), "held accept cannot launch from setup");
        same(TitleScreenProvider.State.ACTIVE, f.menu.getState(), "held accept keeps menu active");
        same(GolfMenu.Field.PLAYER_ONE, f.menu.focusedField(), "focus remains first setup row");
        f.input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_RELEASE); f.tick();
        f.launch(); same(1, f.launches.size(), "new edge deliberately launches");
    }

    public static void joinValidation() {
        var f = new Fixture(); f.mode(3);
        f.focus(GolfMenu.Field.ADDRESS); f.tap(GLFW_KEY_ENTER); f.text("   "); f.tap(GLFW_KEY_ENTER);
        require(f.menu.editing() && !f.menu.errorMessage().isBlank(), "blank host cannot finish editor");
        require(f.launches.isEmpty(), "invalid host never launches");
        f.text("localhost"); f.tap(GLFW_KEY_ENTER);
        // After a validation error the next typed text replaces the rejected value.
        require(!f.menu.editing(), "valid host accepts");
        f.focus(GolfMenu.Field.PORT); f.tap(GLFW_KEY_ENTER); f.text("70000"); f.tap(GLFW_KEY_ENTER);
        require(f.menu.editing() && !f.menu.errorMessage().isBlank(), "out-of-range port rejected");
        f.text("65535"); f.tap(GLFW_KEY_ENTER);
        var selected = f.launch();
        same("localhost", selected.address(), "join hostname retained");
        same(65535, selected.port(), "upper port boundary accepted");
        same(GolfMenu.Mode.JOIN, selected.mode(), "join mode");
        var low = new Fixture(); low.mode(3); low.focus(GolfMenu.Field.PORT);
        low.tap(GLFW_KEY_ENTER); low.text("0"); low.tap(GLFW_KEY_ENTER);
        require(low.menu.editing(), "zero port rejected");
        low.text("1"); low.tap(GLFW_KEY_ENTER); same(1, low.launch().port(), "lower port boundary accepted");
    }

    public static void hostEditing() {
        var f = new Fixture(); f.mode(2); f.focus(GolfMenu.Field.PORT);
        f.tap(GLFW_KEY_ENTER); f.text("12345"); f.tap(GLFW_KEY_ESCAPE);
        require(!f.menu.editing(), "escape exits text editor");
        f.tap(GLFW_KEY_ENTER); f.tap(GLFW_KEY_BACKSPACE); f.text("9"); f.tap(GLFW_KEY_ENTER);
        var selected = f.launch();
        same(20509, selected.port(), "cancel restores20502 then backspace changes final digit");
        same("127.0.0.1", selected.address(), "default direct address");
        same(GolfMenu.Mode.HOST, selected.mode(), "host mode");
    }

    public static void backAndReset() {
        var f = new Fixture(); f.mode(3); f.tap(GLFW_KEY_ESCAPE);
        same(GolfMenu.Field.MODE, f.menu.focusedField(), "back returns to mode picker");
        require(f.launches.isEmpty(), "back cannot launch");
        f.text("unwanted-host-text"); f.tap(GLFW_KEY_ENTER);
        f.focus(GolfMenu.Field.ADDRESS); f.tap(GLFW_KEY_ENTER); f.tap(GLFW_KEY_ENTER);
        same("127.0.0.1", f.launch().address(), "text outside editor is discarded");
        f.menu.reset(); same(TitleScreenProvider.State.INACTIVE, f.menu.getState(), "reset inactive");
        f.menu.initialize(); require(f.menu.getSelection() == null, "reinitialize clears previous launch receipt");
        same(TitleScreenProvider.State.ACTIVE, f.menu.getState(), "reinitialize active");
    }

    private static final class CaptureGraphics extends GraphicsManager {
        final List<GLCommandable> queued = new ArrayList<>();
        @Override public void registerCommand(GLCommandable command) { queued.add(command); }
    }

    private static void bounds(CaptureGraphics graphics, int width) {
        for (var primitive : graphics.queued) {
            var rect = (GolfText.Rect) primitive;
            require(rect.x() >= 0 && rect.y() >= 0 && rect.x() + rect.width() <= width
                    && rect.y() + rect.height() <= 224, "geometry outside selected viewport");
        }
        require(graphics.queued.size() < 6000, "codeglyph drawing has bounded frame cost");
    }

    /** Queued-value raster preview, not a claim of native framebuffer verification. */
    private static void preview(CaptureGraphics graphics, int width, String name) throws Exception {
        var image = new BufferedImage(width, 224, BufferedImage.TYPE_INT_RGB);
        var canvas = image.createGraphics();
        try {
            for (var primitive : graphics.queued) {
                var rect = (GolfText.Rect) primitive;
                canvas.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, rect.alpha()));
                canvas.setColor(new Color(rect.rgb()));
                canvas.fillRect(rect.x(), rect.y(), rect.width(), rect.height());
            }
        } finally { canvas.dispose(); }
        Path directory = Path.of("target/golf-ui-preview");
        Files.createDirectories(directory);
        ImageIO.write(image, "png", directory.resolve(name + "-" + width + ".png").toFile());
    }

    public static void renderWidths() throws Exception {
        for (var viewport : GolfMenu.Viewport.values()) {
            int width = viewport.pixelWidth();
            var graphics = new CaptureGraphics();
            var menu = new GolfMenu(graphics, () -> width, choice -> { }); menu.initialize(); menu.draw();
            require(!graphics.queued.isEmpty(), "title must queue visible UI");
            preview(graphics, width, "menu");
            for (var primitive : graphics.queued) {
                var rect = (GolfText.Rect) primitive;
                require(rect.x() >= 0 && rect.y() >= 0 && rect.x() + rect.width() <= width
                        && rect.y() + rect.height() <= 224, "menu geometry outside selected viewport");
            }
            require(graphics.queued.size() < 6000, "codeglyph drawing has bounded frame cost");
            graphics.queued.clear();
            var view = new GolfOverlay.View("LOCAL", "SPIN", 45, -1, 650, -50, 50, 75, 1,
                    List.of(new GolfOverlay.PlayerScore("SONIC", 3, 1, true, false),
                            new GolfOverlay.PlayerScore("TAILS", 2, 0, false, false)), 1,
                    "UP/DOWN HIT POINT  A STOP MARKER", false, false);
            GolfOverlay.draw(graphics, width, view);
            require(!graphics.queued.isEmpty(), "HUD queues visible values");
            preview(graphics, width, "hud");
            for (var primitive : graphics.queued) {
                var rect = (GolfText.Rect) primitive;
                require(rect.x() >= 0 && rect.y() >= 0 && rect.x() + rect.width() <= width
                        && rect.y() + rect.height() <= 224, "HUD geometry outside selected viewport");
            }
            graphics.queued.clear();
            for(String stage:List.of("POWER","FEEDBACK","PRE_RELEASE","WATCH")) {
                var full=new GolfOverlay.View("PRACTICE",stage,90,1,1000,100,100,1000,0,
                        List.of(new GolfOverlay.PlayerScore("SONIC",1,0,false,false)),0,
                        "A STOP POWER - ONE RISE AND FALL",false,false);
                GolfOverlay.draw(graphics,width,full);
                require(graphics.queued.stream().map(p->(GolfText.Rect)p).anyMatch(r->r.rgb()==GolfText.PINK),
                        "full-power gauge turns pink in every release stage");
                for(var primitive:graphics.queued) {
                    var rect=(GolfText.Rect)primitive;
                    require(rect.x()>=0&&rect.y()>=0&&rect.x()+rect.width()<=width&&rect.y()+rect.height()<=224,
                            "power/contact panel geometry outside selected viewport");
                }
                graphics.queued.clear();
            }
            var input = new InputHandler();
            for (int i = 0; i < 3; i++) {
                input.handleKeyEvent(GLFW_KEY_DOWN, GLFW_PRESS); input.refreshLogicalSnapshot(); menu.update(input); input.update();
                input.handleKeyEvent(GLFW_KEY_DOWN, GLFW_RELEASE); input.refreshLogicalSnapshot(); menu.update(input); input.update();
            }
            input.handleKeyEvent(GLFW_KEY_ENTER, GLFW_PRESS); input.refreshLogicalSnapshot(); menu.update(input); input.update();
            menu.draw(); bounds(graphics, width); preview(graphics, width, "join");
            graphics.queued.clear();
            GolfOverlay.draw(graphics, width, new GolfOverlay.View("ONLINE", "RESULTS", 0, 1, 0, 0, 0, 0, 0,
                    List.of(new GolfOverlay.PlayerScore("SONIC", 12, 2, true, false),
                            new GolfOverlay.PlayerScore("TAILS", 13, 1, true, false)), 1, "DRAW - BOTH PLAYERS 14", true, true));
            bounds(graphics, width); preview(graphics, width, "scorecard");
        }
    }
}
