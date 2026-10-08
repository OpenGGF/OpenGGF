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

    public static void hudShowsAuthoritativePhaseAndAvailableControls() {
        var meter = new paradise.model.ShotMeter();
        var aim = meter.snapshot();
        var hostShot = GolfHud.shot(aim, false, true, GolfHud.RemotePhase.WATCH, false, false, "R/LB");
        same("WATCH", hostShot.stage(), "observer uses authoritative host phase");
        require(!hostShot.showShotControls() && !hostShot.hint().contains("A SHOT"), "observer must not advertise unavailable controls");
        var waitingHost = GolfHud.shot(aim, false, false, GolfHud.RemotePhase.AIM, false, false, "R/LB");
        same("WAITING", waitingHost.stage(), "host waiting for guest must not show its stale aim meter");
        var guestFlight = GolfHud.shot(aim, true, true, GolfHud.RemotePhase.WATCH, false, true, "T/LB");
        require(guestFlight.hint().contains("T/LB REWIND") && !guestFlight.showShotControls(), "accepted guest shot uses authoritative flight and remapped rewind hint");
        var guestCharge = GolfHud.shot(aim, true, true, GolfHud.RemotePhase.CHARGING, false, false, "R/LB");
        same("CHARGING", guestCharge.stage(), "guest charge is authoritative");
        var undo = GolfHud.shot(aim, true, true, GolfHud.RemotePhase.REWINDING, false, true, "R/LB");
        same("REWIND", undo.stage(), "remote replay overrides the local meter");
        var view = GolfHud.overlay("JOIN", hostShot, aim, 0, 0,
                List.of(new GolfOverlay.PlayerScore("SONIC", 3, 1, false, false)), 0, hostShot.hint(), false, false);
        same(0, view.power(), "unknown remote power is neutralized");
        same("P1 4 (+1 PEN)", GolfHud.scoreLabel(0, view.players().getFirst()), "score names included penalty instead of an arithmetic puzzle");
        same("P1 3", GolfHud.scoreLabel(0, new GolfOverlay.PlayerScore("SONIC", 3, 0, false, false)), "ordinary score is simple");
        same("LB", GolfHud.rewindControl(-1), "an unbound key never advertises R");
        same("SPACE/LB", GolfHud.rewindControl(GLFW_KEY_SPACE), "space binding is named");
    }

    public static void menuPromptsFollowTheIntentionalInputDevice() {
        var devices = new java.util.concurrent.atomic.AtomicReference<List<com.openggf.control.GamepadStateSource.DeviceState>>(List.of());
        var config = com.openggf.configuration.SonicConfigurationService.createStandalone();
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.CONTROLLER_PLAYER2, "none");
        var input = new InputHandler(com.openggf.InputBindingFactory.supplier(config), devices::get);
        var menu = new GolfMenu(null, ignored -> { }); menu.initialize();
        devices.set(List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(0, "Xbox test", new boolean[15], 0, 0)));
        input.refreshLogicalSnapshot(); menu.update(input); input.update();
        require(menu.controlsHint().contains("ENTER"), "connection alone does not select controller hints");
        var buttons = new boolean[15]; buttons[GLFW_GAMEPAD_BUTTON_DPAD_DOWN] = true;
        devices.set(List.of(com.openggf.control.GamepadStateSource.DeviceState.connected(0, "Xbox test", buttons, 0, 0)));
        input.refreshLogicalSnapshot(); menu.update(input); input.update();
        same("D-Pad  A SELECT  B BACK", menu.controlsHint(), "menu uses shared controller button labels");
        input.handleKeyEvent(GLFW_KEY_UP, GLFW_PRESS); input.refreshLogicalSnapshot(); menu.update(input); input.update();
        require(menu.controlsHint().contains("ENTER") && menu.controlsHint().contains("MOUSE"), "intentional keyboard edge restores keyboard/pointer hints");
    }

    public static void scorecardHasNoStaleShotMeter() {
        var graphics = new CaptureGraphics();
        GolfOverlay.draw(graphics, 320, new GolfOverlay.View("PRACTICE", "WATCH", 60, 1, 1000, 0, 0, 1000, 0,
                List.of(new GolfOverlay.PlayerScore("SONIC", 10, 0, true, false)), 0,
                "HOLE COMPLETE - START FOR MENU", false, true));
        require(graphics.queued.stream().map(p -> (GolfText.Rect) p).noneMatch(r -> r.y() >= 180),
                "scorecard must hide the completed shot's meter and duplicate hint");
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

    public static void handoffPromptsNameTheIncomingPlayersBinding() {
        var local = GolfHud.handoff(true, false, "RIGHT SHIFT", 1, "TAILS");
        same("HANDOFF", local.stage(), "owner sees the ready stage");
        require(!local.showShotControls() && local.hint().startsWith("RIGHT SHIFT WHEN READY"), "owner prompt uses their binding");
        var spectator = GolfHud.handoff(false, false, "SPACE", 1, "TAILS");
        same("WAITING", spectator.stage(), "spectator waits");
        require(spectator.hint().contains("WAITING FOR P2 TAILS") && !spectator.hint().contains("SPACE"),
                "a spectator is never told to press their own key");
        require(GolfHud.handoff(true, true, "SQUARE", 1, "SONIC").hint().contains("WAITING FOR THE HOST"), "a sent request waits for the host");
        var card = new GolfCards.Handoff(1, "TAILS", true, "RIGHT SHIFT", false, false, 1);
        same("TAILS' TURN", card.title(), "apostrophe after S");
        same("SONIC'S TURN", new GolfCards.Handoff(0, "SONIC", true, "SPACE", false, true, 1).title(), "possessive");
        same("PRESS RIGHT SHIFT WHEN READY", card.prompt(), "incoming player's own key");
        same("WAITING FOR P2 TAILS", new GolfCards.Handoff(1, "TAILS", false, "SPACE", false, false, 1).prompt(), "spectator prompt");
        same("PRESS A WHEN READY", new GolfCards.Handoff(0, "SONIC", true, "", false, false, 1).prompt(), "unbound fallback names the logical button");
        same("R REWIND  START PAUSE", GolfHud.shot(new paradise.model.ShotMeter.State(paradise.model.ShotMeter.Stage.WATCH, 1, 0, 0, 500, 0, 0, 0,
                false, false, new paradise.model.GolfShot(1, 0, 500, 0), false), true, false, null, false, true, "R").hint(), "derived rewind label");
        same("START MENU TO REWIND", GolfHud.shot(new paradise.model.ShotMeter.State(paradise.model.ShotMeter.Stage.WATCH, 1, 0, 0, 500, 0, 0, 0,
                false, false, new paradise.model.GolfShot(1, 0, 500, 0), false), true, false, null, false, true, "").hint(), "unbound rewind keeps the menu path");
    }

    public static void feedbackTimelineIsDeterministicAndPauseFreezesIt() {
        var f = new GolfFeedback();
        f.handoff();
        same(List.of(GolfFeedback.Cue.HANDOFF), f.tick(true, false, 0), "handoff announces the next golfer");
        for (int i = 0; i < GolfFeedback.CARD_IN; i++) f.tick(true, false, 0);
        same(GolfFeedback.Card.HANDOFF, f.card(), "the card waits for readiness");
        require(Math.abs(f.cardIn() - 1) < 0.01f, "card has landed");
        long clock = f.clock(); float hud = f.hudShown();
        for (int i = 0; i < 30; i++) require(f.tick(false, true, 0).isEmpty(), "paused rows raise no cues");
        same(clock, f.clock(), "pause freezes presentation time");
        require(f.hudShown() == hud, "pause freezes the panel");
        var saved = f.snapshot();
        var first = new ArrayList<GolfFeedback.State>();
        f.ready();
        same(List.of(GolfFeedback.Cue.READY), f.tick(true, true, 0), "ready confirms with one cue");
        require(f.cardLeaving() && f.toast() == GolfFeedback.Toast.TEE_OFF, "card leaves as TEE OFF appears");
        float previous = -1;
        for (int i = 0; i < 60; i++) {
            f.tick(true, true, 0); first.add(f.snapshot());
            require(f.hudShown() >= previous - 1e-6f, "panel rises monotonically"); previous = f.hudShown();
        }
        same(GolfFeedback.Card.NONE, f.card(), "card retired");
        same(GolfFeedback.Toast.NONE, f.toast(), "toast retired");
        require(Math.abs(f.hudShown() - 1) < 1e-6f, "panel fully shown");
        f.restore(saved); f.ready(); f.tick(true, true, 0);
        for (int i = 0; i < 60; i++) { f.tick(true, true, 0); same(first.get(i), f.snapshot(), "restored timeline replays exactly at row " + i); }
        f.trail(10, 20); f.tick(true, false, 0); f.trail(11, 21);
        require(f.trailPoints().size() <= 4 && f.trailPoints().size() % 2 == 0, "trail keeps x/y pairs at its stride");
        for (int i = 0; i < 200; i++) { f.trail(i, i); f.tick(true, false, 0); }
        require(f.trailPoints().size() <= GolfFeedback.TRAIL_POINTS * 2, "trail is bounded");
    }

    public static void resultsFollowSonic2Order() {
        var f = new GolfFeedback();
        f.finished(0, 10, true);
        same(List.of(GolfFeedback.Cue.FINISH), f.tick(true, false, 0), "the signpost sound marks the finish");
        f.results(true);
        var cues = new ArrayList<GolfFeedback.Cue>(); long clearAt = -1, endAt = -1, lastTick = -1;
        for (int row = 1; row <= 400; row++) {
            for (var cue : f.tick(true, false, 10)) {
                cues.add(cue);
                if (cue == GolfFeedback.Cue.CLEAR) clearAt = row;
                if (cue == GolfFeedback.Cue.TALLY_END) endAt = row;
                if (cue == GolfFeedback.Cue.TALLY_TICK) lastTick = row;
            }
            if (row < GolfFeedback.CARD_DELAY) require(f.scorecardAge() < 0, "the card waits for the celebration");
        }
        same(1L, cues.stream().filter(c -> c == GolfFeedback.Cue.CLEAR).count(), "one Stage Clear jingle");
        same((long) GolfFeedback.CLEAR_DELAY, clearAt, "jingle after the signpost");
        same(1L, cues.stream().filter(c -> c == GolfFeedback.Cue.TALLY_END).count(), "one tally end");
        require(lastTick > 0 && lastTick < endAt && endAt > GolfFeedback.CARD_DELAY, "blips precede the tally end, after the card");
        same(10, f.tally(10), "tally reaches the total");
        require(f.tallyDone(), "tally completes");
        var conceded = new GolfFeedback(); conceded.results(false);
        require(conceded.scorecardAge() == 0, "uncelebrated results show the card at once");
        for (int i = 0; i < 200; i++) require(!conceded.tick(true, false, 3).contains(GolfFeedback.Cue.CLEAR), "no jingle for a concession");
    }

    public static void menuCuesFollowNavigation() {
        var cues = new ArrayList<GolfMenu.Cue>(); var launches = new ArrayList<GolfMenu.Selection>();
        var menu = new GolfMenu(null, () -> 320, launches::add, null, cues::add);
        var input = new InputHandler();
        menu.initialize();
        same(List.of(GolfMenu.Cue.MUSIC), cues, "menu music starts with the menu"); cues.clear();
        Runnable tick = () -> { input.refreshLogicalSnapshot(); menu.update(input); input.update(); };
        java.util.function.IntConsumer tap = key -> {
            input.handleKeyEvent(key, GLFW_PRESS); tick.run(); input.handleKeyEvent(key, GLFW_RELEASE); tick.run();
        };
        tap.accept(GLFW_KEY_DOWN); same(List.of(GolfMenu.Cue.MOVE), cues, "moving the selection blips"); cues.clear();
        tap.accept(GLFW_KEY_ENTER); same(List.of(GolfMenu.Cue.ENTER), cues, "opening setup confirms"); cues.clear();
        tap.accept(GLFW_KEY_RIGHT); same(List.of(GolfMenu.Cue.MOVE), cues, "changing an option blips"); cues.clear();
        tap.accept(GLFW_KEY_ESCAPE); same(List.of(GolfMenu.Cue.MOVE), cues, "returning blips"); cues.clear();
        for (int i = 0; i < 2; i++) tap.accept(GLFW_KEY_DOWN);
        tap.accept(GLFW_KEY_ENTER); cues.clear(); // Join setup.
        while (menu.focusedField() != GolfMenu.Field.PORT) tap.accept(GLFW_KEY_DOWN);
        cues.clear();
        tap.accept(GLFW_KEY_ENTER); same(List.of(GolfMenu.Cue.ENTER), cues, "editing a field confirms"); cues.clear();
        MenuInput.handleCharEvent(input, '0'); tick.run(); tap.accept(GLFW_KEY_ENTER);
        require(cues.contains(GolfMenu.Cue.ERROR), "a refused value sounds the error cue: " + cues); cues.clear();
        tap.accept(GLFW_KEY_ESCAPE); cues.clear();
        while (menu.focusedField() != GolfMenu.Field.START) tap.accept(GLFW_KEY_DOWN);
        cues.clear(); tap.accept(GLFW_KEY_ENTER);
        same(1, launches.size(), "launch"); require(cues.contains(GolfMenu.Cue.LAUNCH), "launch sounds the release");
    }

    public static void cardsAndToastsStayInsideEveryViewport() throws Exception {
        for (var viewport : GolfMenu.Viewport.values()) {
            int width = viewport.pixelWidth();
            var players = List.of(new GolfOverlay.PlayerScore("SONIC", 3, 1, false, false),
                    new GolfOverlay.PlayerScore("TAILS", 2, 0, false, false));
            var cases = new ArrayList<java.util.function.Consumer<GolfFeedback>>();
            cases.add(GolfFeedback::holeIntro);
            cases.add(GolfFeedback::handoff);
            cases.add(f -> f.penalty(GolfFeedback.Toast.PENALTY_LOST));
            cases.add(f -> f.finished(1, 12, false));
            cases.add(f -> f.finished(0, 9, true));
            cases.add(f -> f.refunded(2));
            cases.add(f -> { f.handoff(); f.ready(); });
            int index = 0;
            for (var event : cases) {
                for (int age : new int[]{2, 9, 30, 110}) {
                    var graphics = new CaptureGraphics(); var canvas = new GolfCanvas(graphics, width, 224);
                    var f = new GolfFeedback(); event.accept(f);
                    for (int i = 0; i < age; i++) f.tick(true, false, 0);
                    for (int i = 0; i < 40; i++) f.trail(i * 20 - 100, 100 + i);
                    GolfCards.trail(canvas, f.trailPoints(), 0, 0);
                    GolfCards.tag(canvas, f.clock(), width - 2, 30, 1, "TAILS");
                    GolfCards.hole(canvas, f, 1, "SONIC PRACTICE");
                    GolfCards.handoff(canvas, f, new GolfCards.Handoff(1, "TAILS", true, "RIGHT SHIFT", false, true, 2));
                    GolfCards.toast(canvas, f, players);
                    bounds(graphics, width);
                    if (age == 30) preview(graphics, width, "card-" + index);
                }
                index++;
            }
            var graphics = new CaptureGraphics(); var canvas = new GolfCanvas(graphics, width, 224);
            var view = new GolfOverlay.View("LOCAL", "WATCH", 30, 1, 1000, 0, 0, 1000, 1, players, 0,
                    "R REWIND  START PAUSE", false, false);
            GolfOverlay.draw(canvas, view, new GolfOverlay.Motion(0.4f, 0.5f, 1, 0, 33, 2, 0, false, 0, "(2 LEFT)",
                    new GolfOverlay.Progress(100, 9000, List.of(4000, 9500)), true));
            GolfOverlay.pause(canvas, List.of("RESUME", "REWIND UNAVAILABLE", "CONCEDE", "MAIN MENU"),
                    List.of(true, false, true, true), 1, "REWINDS LEFT  HOLE 2  TURN 1", 4);
            bounds(graphics, width); preview(graphics, width, "watch-pause");
        }
    }
}
