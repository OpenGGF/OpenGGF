package paradise.ui;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.TitleScreenProvider;
import com.openggf.graphics.GraphicsManager;
import paradise.model.RewindAllowance;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;

/** Standalone creator title screen. Launch selections arrive before the standard one-player exit. */
public final class GolfMenu implements TitleScreenProvider {
    public enum Mode { PRACTICE, LOCAL, HOST, JOIN }
    public enum CharacterChoice {
        SONIC("sonic"), TAILS("tails");
        private final String code;
        CharacterChoice(String code) { this.code = code; }
        public String code() { return code; }
    }
    public enum Viewport {
        NATIVE_4_3(320), WIDE_16_10(352), WIDE_16_9(400), ULTRA_21_9(528), SUPER_32_9(800);
        private final int width;
        Viewport(int width) { this.width = width; }
        public int pixelWidth() { return width; }
        public String configName() { return name(); }
    }
    public enum Field { MODE, PLAYER_ONE, PLAYER_TWO, ACT, VIEWPORT, REWINDS_HOLE, REWINDS_TURN, ADDRESS, PORT, START, BACK }

    public record Selection(Mode mode, CharacterChoice playerOne, CharacterChoice playerTwo,
                            int actIndex, String address, int port, Viewport viewport, RewindAllowance.Rules rewinds) {
        public Selection(Mode mode, CharacterChoice playerOne, CharacterChoice playerTwo,
                         int actIndex, String address, int port, Viewport viewport) {
            this(mode, playerOne, playerTwo, actIndex, address, port, viewport, RewindAllowance.Rules.defaults());
        }
        public Selection {
            Objects.requireNonNull(mode, "mode"); Objects.requireNonNull(playerOne, "playerOne");
            Objects.requireNonNull(playerTwo, "playerTwo"); Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(rewinds, "rewinds");
            address = Objects.requireNonNull(address, "address").trim();
            if (address.isBlank() || address.length() > 253 || port < 1 || port > 65535 || actIndex < 0 || actIndex > 1
                    || (mode != Mode.PRACTICE && actIndex != 0)) throw new IllegalArgumentException("Invalid golf launch selection");
        }
    }

    private final GraphicsManager graphics;
    private final IntSupplier logicalWidth;
    private final Consumer<Selection> launchConsumer;
    private final GolfTitleArt titleArt;
    private long animationTick;
    private State state = State.INACTIVE;
    private Mode mode = Mode.PRACTICE;
    private boolean setup;
    private int selectedMode;
    private int row;
    private CharacterChoice playerOne = CharacterChoice.SONIC;
    private CharacterChoice playerTwo = CharacterChoice.TAILS;
    private int practiceAct;
    private Viewport viewport = Viewport.NATIVE_4_3;
    private RewindAllowance.Rules rewinds = RewindAllowance.Rules.defaults();
    private String address = "127.0.0.1";
    private String portText = "20502";
    private boolean editing;
    private String editBuffer = "";
    private boolean replaceOnType;
    private String error = "";
    private Selection selected;
    private TitleScreenAction exitAction = TitleScreenAction.OTHER;

    public GolfMenu(GraphicsManager graphics, IntSupplier logicalWidth, Consumer<Selection> launchConsumer) {
        this(graphics, logicalWidth, launchConsumer, null);
    }
    public GolfMenu(GraphicsManager graphics, IntSupplier logicalWidth, Consumer<Selection> launchConsumer,
                    GolfTitleArt titleArt) {
        this.graphics = graphics;
        this.logicalWidth = Objects.requireNonNull(logicalWidth, "logicalWidth");
        this.launchConsumer = Objects.requireNonNull(launchConsumer, "launchConsumer");
        this.titleArt = titleArt;
    }
    public GolfMenu(GraphicsManager graphics, Consumer<Selection> launchConsumer) { this(graphics, () -> 320, launchConsumer); }

    @Override public void initialize() {
        if (titleArt != null) titleArt.initialize();
        animationTick = 0;
        state = State.ACTIVE; setup = false; row = 0; editing = false; error = "";
        selected = null; exitAction = TitleScreenAction.OTHER;
    }
    @Override public void reset() { state = State.INACTIVE; editing = false; selected = null; exitAction = TitleScreenAction.OTHER; }
    @Override public State getState() { return state; }
    @Override public boolean isExiting() { return state == State.EXITING; }
    @Override public boolean isActive() { return state != State.INACTIVE; }
    @Override public int startZoneIndex() { return 0; }
    // Parent integration adds this semantic title-launch seam to TitleScreenProvider.
    public int startActIndex() { return selected == null ? 0 : selected.actIndex(); }
    @Override public TitleScreenAction consumeExitAction() {
        TitleScreenAction action = exitAction; exitAction = TitleScreenAction.OTHER; return action;
    }
    public Selection getSelection() { return selected; }
    public String errorMessage() { return error; }
    public boolean editing() { return editing; }
    public Field focusedField() { return setup ? fields().get(row) : Field.MODE; }

    @Override public void update(InputHandler input) {
        if (state == State.ACTIVE) animationTick++;
        if (input == null || state != State.ACTIVE) return;
        String text = MenuInput.consumeText(input); // Discard typing outside the editor; never replay it on later focus.
        if (editing) { updateEditor(input, text); return; }
        if (MenuInput.back(input)) {
            if (setup) { setup = false; row = 0; error = ""; }
            return;
        }
        int vertical = (MenuInput.down(input) ? 1 : 0) - (MenuInput.up(input) ? 1 : 0);
        int horizontal = (MenuInput.right(input) ? 1 : 0) - (MenuInput.left(input) ? 1 : 0);
        if (!setup) {
            if (vertical != 0 || horizontal != 0) selectedMode = Math.floorMod(selectedMode + (vertical != 0 ? vertical : horizontal), 4);
            if (MenuInput.accept(input)) { mode = Mode.values()[selectedMode]; setup = true; row = 0; error = ""; }
            return;
        }
        if (vertical != 0) { row = Math.floorMod(row + vertical, fields().size()); error = ""; }
        Field field = focusedField();
        if (horizontal != 0) change(field, horizontal);
        if (!MenuInput.accept(input)) return;
        switch (field) {
            case PLAYER_ONE, PLAYER_TWO, ACT, VIEWPORT, REWINDS_HOLE, REWINDS_TURN -> change(field, 1);
            case ADDRESS, PORT -> {
                editing = true; editBuffer = field == Field.ADDRESS ? address : portText;
                replaceOnType = true; error = "";
            }
            case START -> launch();
            case BACK -> { setup = false; row = 0; }
            default -> { }
        }
    }

    private void change(Field field, int direction) {
        switch (field) {
            case PLAYER_ONE -> playerOne = playerOne == CharacterChoice.SONIC ? CharacterChoice.TAILS : CharacterChoice.SONIC;
            case PLAYER_TWO -> playerTwo = playerTwo == CharacterChoice.SONIC ? CharacterChoice.TAILS : CharacterChoice.SONIC;
            case ACT -> practiceAct = 1 - practiceAct;
            case VIEWPORT -> viewport = Viewport.values()[Math.floorMod(viewport.ordinal() + direction, Viewport.values().length)];
            case REWINDS_HOLE -> rewinds = new RewindAllowance.Rules(cycle(new int[]{0, 3, 5, -1}, rewinds.perHole(), direction), rewinds.perTurn());
            case REWINDS_TURN -> rewinds = new RewindAllowance.Rules(rewinds.perHole(), cycle(new int[]{1, 3, -1}, rewinds.perTurn(), direction));
            default -> { }
        }
    }

    private static int cycle(int[] values, int current, int direction) {
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[Math.floorMod(i + direction, values.length)];
        throw new IllegalArgumentException("Unknown rewind setting");
    }

    private void updateEditor(InputHandler input, String text) {
        if (MenuInput.textBack(input)) { editing = false; error = ""; return; }
        if (MenuInput.textKeyRepeated(input, GLFW_KEY_BACKSPACE)) {
            if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            replaceOnType = false; error = "";
        }
        if (!text.isEmpty()) {
            if (replaceOnType) editBuffer = "";
            replaceOnType = false; error = "";
            int limit = focusedField() == Field.PORT ? 6 : 253;
            for (int i = 0; i < text.length() && editBuffer.length() < limit; i++) {
                char c = text.charAt(i);
                if (focusedField() == Field.PORT ? c >= '0' && c <= '9' : c >= 32 && c <= 126) editBuffer += c;
            }
        }
        if (!MenuInput.textAccept(input)) return;
        if (focusedField() == Field.ADDRESS) {
            if (editBuffer.isBlank()) { error = "ENTER AN IP OR HOSTNAME"; replaceOnType = true; return; }
            address = editBuffer.trim();
        } else {
            if (!validPort(editBuffer)) { error = "PORT MUST BE 1-65535"; replaceOnType = true; return; }
            portText = editBuffer;
        }
        editing = false; error = "";
    }

    private static boolean validPort(String text) {
        if (text.isEmpty() || text.length() > 5) return false;
        try { int port = Integer.parseInt(text); return port >= 1 && port <= 65535; }
        catch (NumberFormatException invalid) { return false; }
    }

    private void launch() {
        if (address.isBlank() || !validPort(portText)) { error = "CHECK ADDRESS AND PORT"; return; }
        Selection choice = new Selection(mode, playerOne, playerTwo, mode == Mode.PRACTICE ? practiceAct : 0,
                address, Integer.parseInt(portText), viewport, rewinds);
        launchConsumer.accept(choice);
        selected = choice; exitAction = TitleScreenAction.ONE_PLAYER; state = State.EXITING;
    }

    private List<Field> fields() {
        return switch (mode) {
            case PRACTICE -> List.of(Field.PLAYER_ONE, Field.ACT, Field.VIEWPORT, Field.REWINDS_HOLE, Field.REWINDS_TURN, Field.START, Field.BACK);
            case LOCAL -> List.of(Field.PLAYER_ONE, Field.PLAYER_TWO, Field.VIEWPORT, Field.REWINDS_HOLE, Field.REWINDS_TURN, Field.START, Field.BACK);
            case HOST -> List.of(Field.PLAYER_ONE, Field.VIEWPORT, Field.REWINDS_HOLE, Field.REWINDS_TURN, Field.PORT, Field.START, Field.BACK);
            case JOIN -> List.of(Field.PLAYER_ONE, Field.VIEWPORT, Field.REWINDS_HOLE, Field.REWINDS_TURN, Field.ADDRESS, Field.PORT, Field.START, Field.BACK);
        };
    }

    /** The ROM-backed backdrop covers the viewport without a direct GL clear-color call. */
    @Override public void setClearColor() { }

    @Override public void draw() {
        if (state == State.INACTIVE) return;
        Objects.requireNonNull(graphics, "Rendering the menu requires injected graphics");
        int width = Math.clamp(logicalWidth.getAsInt(), 320, 800);
        if (titleArt != null) titleArt.draw(width, animationTick);
        else backdrop(width); // Geometry-only preview and input fixtures need no ROM.
        int bannerLeft = (width - 236) / 2;
        GolfText.panel(graphics, bannerLeft, 2, 236, 22, 0xAC2638, 1);
        GolfText.frame(graphics, bannerLeft, 2, 236, 22, GolfText.GOLD);
        GolfText.centered(graphics, "PUTT PUTT PARADISE", width, 8, 2, 0x641B36);
        GolfText.centered(graphics, "PUTT PUTT PARADISE", width, 7, 2, GolfText.GOLD);
        GolfText.panel(graphics, (width - 112) / 2, 24, 112, 10, 0x15254F, 1);
        GolfText.centered(graphics, "SONIC 2 MINI GOLF", width, 26, 1, GolfText.CREAM);
        int panelWidth = Math.min(width - 24, 376), left = (width - panelWidth) / 2;
        GolfText.panel(graphics, left + 2, 109, panelWidth, 101, 0x06112B, 0.8f);
        GolfText.panel(graphics, left, 107, panelWidth, 101, 0x15254F, 0.96f);
        GolfText.frame(graphics, left, 107, panelWidth, 101, GolfText.GOLD);
        if (setup) drawSetup(left, panelWidth);
        else drawModes(left, panelWidth);
        GolfText.panel(graphics, (width - 222) / 2, 212, 222, 10, 0x15254F, 0.96f);
        GolfText.centered(graphics, "ARROWS CHOOSE  ENTER CONFIRM  ESC BACK", width, 214, 1, GolfText.CREAM);
    }

    private void backdrop(int width) {
        GolfText.panel(graphics, 0, 0, width, 224, GolfText.BLUE, 1);
        for (int y = 52; y < 78; y++) {
            int rise = y - 52;
            GolfText.panel(graphics, 0, y, width, 1, y < 62 ? 0x56A185 : 0x278B69, 1);
            int hillWidth = Math.min(width, 80 + rise * 9);
            GolfText.panel(graphics, (width - hillWidth) / 2, y, hillWidth, 1, 0x33835D, 1);
        }
        GolfText.panel(graphics, 0, 204, width, 20, 0x1F654E, 1);
        GolfText.panel(graphics, width - 29, 52, 2, 23, GolfText.CREAM, 1);
        for (int i = 0; i < 10; i++) GolfText.panel(graphics, width - 27, 52 + i, 12 - i, 1, GolfText.GOLD, 1);
        GolfText.panel(graphics, 23, 62, 7, 7, GolfText.CREAM, 1);
        GolfText.panel(graphics, 25, 64, 1, 1, 0xB4C9BD, 1);
        GolfText.panel(graphics, 28, 67, 1, 1, 0xB4C9BD, 1);
    }

    private static String modeLabel(Mode mode) {
        return switch (mode) { case PRACTICE -> "PRACTICE"; case LOCAL -> "LOCAL TWO PLAYERS"; case HOST -> "HOST ONLINE MATCH"; case JOIN -> "JOIN ONLINE MATCH"; };
    }

    private void drawModes(int left, int panelWidth) {
        GolfText.draw(graphics, "FULL EMERALD HILL ACTS 1 + 2", left + 12, 113, 1, GolfText.CREAM);
        for (int i = 0; i < 4; i++) {
            int y = 129 + i * 17;
            if (i == selectedMode) GolfText.panel(graphics, left + 7, y - 3, panelWidth - 14, 13, 0xAC2638, 1);
            GolfText.draw(graphics, (i == selectedMode ? "> " : "  ") + modeLabel(Mode.values()[i]), left + 12, y,
                    1, i == selectedMode ? GolfText.GOLD : GolfText.CREAM);
        }
        GolfText.draw(graphics, "PUTT. CHIP. ROLL TO THE FINISH.", left + 12, 197, 1, 0x90B9E7);
    }

    private void drawSetup(int left, int panelWidth) {
        GolfText.draw(graphics, modeLabel(mode), left + 12, 113, 1, GolfText.GOLD);
        List<Field> fields = fields();
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            int y = 124 + i * 9;
            if (i == row) GolfText.panel(graphics, left + 7, y - 2, panelWidth - 14, 9, 0xAC2638, 1);
            String label = switch (field) {
                case PLAYER_ONE -> mode == Mode.HOST || mode == Mode.JOIN ? "YOUR CHARACTER" : "PLAYER 1"; case PLAYER_TWO -> "PLAYER 2"; case ACT -> "PRACTICE ACT";
                case VIEWPORT -> "VIEWPORT"; case ADDRESS -> "HOST ADDRESS"; case PORT -> "PORT";
                case REWINDS_HOLE -> "REWINDS / HOLE"; case REWINDS_TURN -> "REWINDS / TURN";
                case START -> "READY - START"; case BACK -> "BACK TO MODES"; default -> "";
            };
            String value = switch (field) {
                case PLAYER_ONE -> playerOne.name(); case PLAYER_TWO -> playerTwo.name(); case ACT -> "EHZ " + (practiceAct + 1);
                case VIEWPORT -> viewport.pixelWidth() + " PX"; case ADDRESS -> address; case PORT -> portText; default -> "";
                case REWINDS_HOLE -> rewinds.holeLabel(); case REWINDS_TURN -> rewinds.turnLabel();
            };
            if (editing && i == row) value = editBuffer + "_";
            value = GolfText.fit(value, panelWidth - 120, 1);
            GolfText.draw(graphics, label, left + 12, y, 1, i == row ? GolfText.GOLD : GolfText.CREAM);
            GolfText.draw(graphics, value, left + panelWidth - 12 - GolfText.width(value, 1), y);
        }
        String help = !error.isEmpty() ? error : editing ? "TYPE TO REPLACE  BACKSPACE TO EDIT"
                : focusedField() == Field.REWINDS_HOLE || focusedField() == Field.REWINDS_TURN ? "* UNLIMITED  BUDGET PER GOLFER"
                : mode == Mode.JOIN || mode == Mode.HOST ? "MATCH ROM, VIEWPORT + REWIND RULES" : "LEFT/RIGHT CHANGES OPTIONS";
        GolfText.draw(graphics, GolfText.fit(help, panelWidth - 24, 1), left + 12, 198, 1,
                error.isEmpty() ? 0x90B9E7 : GolfText.GOLD);
    }
}
