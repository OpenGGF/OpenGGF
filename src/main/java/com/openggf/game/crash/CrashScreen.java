package com.openggf.game.crash;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuDetailsScreen;
import com.openggf.game.MenuFeedback;
import com.openggf.game.MenuStyle;
import com.openggf.graphics.PixelFont;
import com.openggf.i18n.UiMessages;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The page an engine fault ends on: MICA's gag for the kind of fault, a plain headline, the exact
 * error, and the ways out. The session it broke is already gone when this opens, so Return to
 * games only closes the page over the master title.
 *
 * <p>Nothing here retries or resumes the failed work. The player can file the pre-filled issue
 * form, copy the report, read every fact, or leave. The picture turns into a QR code of the form
 * (the details key, or choosing the picture) for reporting from another device.
 */
public final class CrashScreen {
    /** The window-side effects; a test records them. */
    public interface Host {
        /** Opens the issue form with {@code query}; false when no browser could be started. */
        boolean openIssue(String query);
        /** Puts {@code text} on the clipboard; false when the clipboard is unavailable. */
        boolean copy(String text);
        /** Closes the page, leaving the master title the teardown already showed. */
        void returnToGames();
    }

    enum Choice { REPORT, COPY, DETAILS, RETURN }

    record Button(Choice choice, int x, int y, int width) { }
    record Box(int x, int y, int width, int height) { }

    static final int ART_SIZE = 112;
    private static final int TOP = 35;
    private static final int LEFT = 8;
    private static final int GAP = 6;
    private static final int MAX_ERROR_LINES = 3;
    private static final float[] QR_DARK = {0f, 0f, 0f};

    private final CrashReport report;
    private final Optional<Path> file;
    private final Host host;
    private int selected;
    /** The picture is focused instead of a button; it sits left of the button grid. */
    private boolean pictureFocused;
    private boolean showingQr;
    private CrashQrCode qr;
    private int ticks;
    private int width = 320;
    private String status;
    private MenuDetailsScreen details;
    private InputHandler input;
    private List<Button> buttons = List.of();

    public CrashScreen(CrashReport report, Optional<Path> file, Host host) {
        this.report = Objects.requireNonNull(report, "report");
        this.file = Objects.requireNonNull(file, "file");
        this.host = Objects.requireNonNull(host, "host");
        layout();
    }

    public CrashReport report() { return report; }
    public Optional<Path> file() { return file; }
    String status() { return status; }
    Choice selected() { return Choice.values()[selected]; }
    boolean detailsOpen() { return details != null; }
    boolean pictureFocused() { return pictureFocused; }
    /** True while the picture box shows the QR code instead of MICA. */
    public boolean showingQr() { return showingQr; }

    /** The issue form's QR code, built on first use. */
    CrashQrCode qrCode() {
        if (qr == null) qr = CrashQrCode.of(report, ART_SIZE);
        return qr;
    }

    void toggleQr() { showingQr = !showingQr; }

    /** Reads one frame of input; the caller closes the page after {@link Host#returnToGames()}. */
    public void update(InputHandler input) {
        update(input, MenuInput.pointer(input, width, MenuChrome.SCREEN_H));
    }

    void update(InputHandler input, MenuInput.Pointer pointer) {
        if (input == null) return;
        this.input = input;
        ticks++;
        if (details != null) {
            if (details.update(input)) details = null;
            return;
        }
        if (MenuInput.textBack(input) || pointer.inside() && pointer.rightPressed()) {
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            host.returnToGames();
            return;
        }
        if (MenuInput.details(input)) {
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
            toggleQr();
            return;
        }
        int count = Choice.values().length;
        int previous = selected;
        boolean previousPicture = pictureFocused;
        // A two-by-two grid with the picture to its left: across steps through picture and the
        // two columns, up and down swap the row.
        if (MenuInput.textLeft(input)) across(-1);
        if (MenuInput.textRight(input)) across(1);
        if (!pictureFocused && (MenuInput.textUp(input) || MenuInput.textDown(input))) selected = (selected + 2) % count;
        boolean clicked = false;
        boolean pictureClicked = false;
        for (Button button : buttons) {
            if (pointer.over(button.x(), button.y(), button.width(), buttonHeight()) && pointer.leftPressed()) {
                selected = button.choice().ordinal();
                pictureFocused = false;
                clicked = true;
            }
        }
        Box art = artBox();
        if (pointer.over(art.x(), art.y(), art.width(), art.height()) && pointer.leftPressed()) {
            pictureFocused = true;
            pictureClicked = true;
        }
        if (previous != selected || previousPicture != pictureFocused) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        boolean accepted = MenuInput.textAccept(input);
        if (pictureClicked || accepted && pictureFocused) {
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
            toggleQr();
        } else if (accepted || clicked) {
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
            run(Choice.values()[selected]);
        }
    }

    /** Moves focus one place across a row: picture, left button, right button, wrapping. */
    void across(int direction) {
        int row = selected / 2;
        if (pictureFocused) {
            pictureFocused = false;
            selected = row * 2 + (direction > 0 ? 0 : 1);
            return;
        }
        int column = selected % 2 + direction;
        if (column < 0 || column > 1) {
            pictureFocused = true;
        } else {
            selected = row * 2 + column;
        }
    }

    void run(Choice choice) {
        switch (choice) {
            case REPORT -> fileIssue();
            case COPY -> status = host.copy(report.fullText())
                    ? UiMessages.text("crash.status.copied") : UiMessages.text("crash.status.copy_failed");
            case DETAILS -> details = new MenuDetailsScreen(detailsContent());
            case RETURN -> host.returnToGames();
        }
    }

    private void fileIssue() {
        String name = file.map(path -> path.getFileName().toString()).orElse("");
        if (host.openIssue(CrashIssueLink.query(report, name))) {
            status = UiMessages.text("crash.status.opened");
        } else if (host.copy(CrashIssueLink.address(report, name))) {
            status = UiMessages.text("crash.status.link_copied");
        } else {
            status = UiMessages.text("crash.status.unavailable");
        }
    }

    MenuDetailsScreen.Content detailsContent() {
        List<MenuDetailsScreen.Row> fault = new ArrayList<>();
        fault.add(new MenuDetailsScreen.Row(UiMessages.text("crash.field.error"), report.redact(report.error())));
        if (!report.rootCause().isEmpty()) {
            fault.add(new MenuDetailsScreen.Row(UiMessages.text("crash.field.cause"), report.redact(report.rootCause())));
        }
        fault.add(new MenuDetailsScreen.Row(UiMessages.text("crash.field.where"), report.where()));
        fault.add(new MenuDetailsScreen.Row(UiMessages.text("crash.field.report"), reportLocation()));
        List<MenuDetailsScreen.Section> sections = List.of(
                new MenuDetailsScreen.Section("", fault, false),
                new MenuDetailsScreen.Section(UiMessages.text("crash.details.session"), rows(report.sessionFacts()), false),
                new MenuDetailsScreen.Section(UiMessages.text("crash.details.build"), rows(report.buildFacts()), false));
        return new MenuDetailsScreen.Content(UiMessages.text("crash.details.title"),
                headline(report.kind()), meaning(), MenuDetailsScreen.Tone.ERROR, sections,
                report.shortTrace(Integer.MAX_VALUE), List.of(), "");
    }

    private static List<MenuDetailsScreen.Row> rows(List<CrashReport.Field> fields) {
        return fields.stream().map(f -> new MenuDetailsScreen.Row(f.label(), f.value())).toList();
    }

    private String meaning() {
        return file.isPresent() ? UiMessages.text("crash.meaning") : UiMessages.text("crash.meaning_unsaved");
    }

    private String reportLocation() {
        return file.map(path -> report.redact(path.toString())).orElse(UiMessages.text("crash.field.report_unsaved"));
    }

    static String shortLocation(Path path) {
        Path parent = path.getParent();
        String name = path.getFileName().toString();
        return parent == null || parent.getFileName() == null ? name : parent.getFileName() + "/" + name;
    }

    /** Where the renderer draws MICA's gag, in menu coordinates (y down). */
    Box artBox() { return new Box(LEFT + 2, TOP + 2, ART_SIZE, ART_SIZE); }

    List<Button> buttons() { return buttons; }

    public void render(PixelFont font, int width) {
        if (font == null) return;
        if (details != null) {
            details.render(font, width);
            return;
        }
        this.width = Math.max(320, width);
        layout();
        MenuChrome.page(font, this.width, UiMessages.text("crash.title").toUpperCase(UiMessages.locale()), null, null);
        drawGagColumn(font);
        drawFacts(font);
        for (Button button : buttons) {
            MenuChrome.button(font, button.x(), button.y(), button.width(), buttonHeight(),
                    label(button.choice()), !pictureFocused && button.choice().ordinal() == selected,
                    button.choice() == Choice.REPORT, ticks);
        }
        List<MenuChrome.Hint> hints = List.of(
                MenuChrome.upDown(input, ""),
                MenuChrome.leftRight(input, UiMessages.text("menu.details.move")),
                MenuChrome.hint(MenuChrome.confirmKey(input), UiMessages.text("menu.details.choose")),
                MenuChrome.hint(MenuChrome.detailsKey(input),
                        showingQr ? UiMessages.text("crash.hint.picture") : UiMessages.text("crash.hint.qr")),
                MenuChrome.hint(MenuChrome.backKey(input), UiMessages.text("crash.hint.return")));
        MenuChrome.footer(font, this.width, status, hints, MenuChrome.controller(input), ticks);
    }

    private void drawGagColumn(PixelFont font) {
        Box art = artBox();
        MenuChrome.panel(font, art.x() - 2, art.y() - 2, art.width() + 4, art.height() + 4);
        if (pictureFocused) MenuChrome.focus(font, art.x(), art.y(), art.width(), art.height(), ticks);
        if (showingQr) drawQr(font, art);
        int bombs = report.kind().bombs();
        int pitch = MenuChrome.ICON_SIZE + 3;
        int rowWidth = bombs * pitch - 3;
        int bombX = art.x() + (art.width() - rowWidth) / 2;
        int bombY = art.y() + art.height() + 7;
        for (int i = 0; i < bombs; i++) MenuChrome.icon(font, MenuChrome.Icon.BOMB, bombX + i * pitch, bombY, MenuChrome.WHITE);
        int y = bombY + MenuChrome.ICON_SIZE + 6;
        String caption = showingQr ? UiMessages.text("crash.caption.qr") : caption(report.kind());
        for (String line : MenuStyle.wrap(caption, art.width() + 4, MenuStyle.COMPACT)) {
            MenuChrome.compact(font, line, art.x() - 2, y + MenuChrome.compactMetrics().above(), MenuChrome.MUTED);
            y += pitch();
        }
    }

    /** Dark modules on a white tile centred in the art box, whole pixels per module. */
    private void drawQr(PixelFont font, Box art) {
        CrashQrCode code = qrCode();
        int scale = Math.max(1, code.scale(art.width()));
        int tile = (code.size() + 2 * CrashQrCode.QUIET) * scale;
        int tileX = art.x() + (art.width() - tile) / 2;
        int tileY = art.y() + (art.height() - tile) / 2;
        MenuChrome.fill(font, tileX, tileY, tile, tile, MenuChrome.WHITE);
        int left = tileX + CrashQrCode.QUIET * scale;
        int top = tileY + CrashQrCode.QUIET * scale;
        for (int y = 0; y < code.size(); y++) {
            for (int x = 0; x < code.size(); x++) {
                if (code.dark(x, y)) MenuChrome.fill(font, left + x * scale, top + y * scale, scale, scale, QR_DARK);
            }
        }
    }

    private void drawFacts(PixelFont font) {
        int x = textLeft();
        int textWidth = this.width - x - LEFT;
        int y = TOP + 2;
        List<String> headline = MenuStyle.wrap(headline(report.kind()), textWidth - 12, MenuStyle.COMPACT);
        MenuChrome.icon(font, MenuChrome.Icon.WARNING, x, y + MenuChrome.compactMetrics().above(), MenuChrome.RED);
        for (String line : headline) {
            MenuChrome.compact(font, line, x + 12, y + MenuChrome.compactMetrics().above(), MenuChrome.RED);
            y += pitch();
        }
        y += 4;
        y = paragraph(font, meaning(), x, y, textWidth, MenuChrome.TEXT, Integer.MAX_VALUE);
        y += 4;
        int column = x + labelColumn();
        y = field(font, UiMessages.text("crash.field.error"), report.redact(report.error()), x, column, y, MAX_ERROR_LINES);
        y = field(font, UiMessages.text("crash.field.where"), report.where(), x, column, y, 2);
        // The page names the folder and file; Details carries the whole path.
        String location = file.map(CrashScreen::shortLocation).orElseGet(this::reportLocation);
        List<String> path = file.isPresent()
                ? MenuStyle.wrapPath(location, this.width - column - LEFT, MenuStyle.COMPACT)
                : MenuStyle.wrap(location, this.width - column - LEFT, MenuStyle.COMPACT);
        MenuChrome.compact(font, UiMessages.text("crash.field.report"), x, y + MenuChrome.compactMetrics().above(), MenuChrome.CYAN);
        for (String line : path) {
            if (y + pitch() > buttonTop() - 4) break;
            MenuChrome.compact(font, line, column, y + MenuChrome.compactMetrics().above(), MenuChrome.DIM);
            y += pitch();
        }
    }

    private int field(PixelFont font, String label, String value, int x, int column, int y, int maxLines) {
        MenuChrome.compact(font, label, x, y + MenuChrome.compactMetrics().above(), MenuChrome.CYAN);
        return paragraph(font, value, column, y, this.width - column - LEFT, MenuChrome.TEXT, maxLines);
    }

    /** Wraps by measured width; a value cut short ends in an ellipsis, and Details shows all of it. */
    private int paragraph(PixelFont font, String text, int x, int y, int width, float[] color, int maxLines) {
        List<String> lines = MenuStyle.wrap(MenuDetailsScreen.readable(text), width, MenuStyle.COMPACT);
        int shown = Math.min(lines.size(), maxLines);
        for (int i = 0; i < shown; i++) {
            String line = lines.get(i);
            if (i == shown - 1 && shown < lines.size()) line = MenuStyle.fit(line + " …", width);
            MenuChrome.compact(font, line, x, y + MenuChrome.compactMetrics().above(), color);
            y += pitch();
        }
        return y + 2;
    }

    private int labelColumn() {
        int widest = 0;
        for (String label : List.of(UiMessages.text("crash.field.error"), UiMessages.text("crash.field.where"),
                UiMessages.text("crash.field.report"))) {
            widest = Math.max(widest, MenuChrome.compactWidth(label));
        }
        return widest + 8;
    }

    private int textLeft() { return LEFT + ART_SIZE + 4 + GAP; }

    /** Two columns of buttons under the facts, clear of MICA's column and caption. */
    private void layout() {
        int left = textLeft();
        int columnWidth = (width - left - LEFT - 4) / 2;
        int top = MenuChrome.FOOTER_Y - 5 - 2 * buttonHeight() - 4;
        List<Button> placed = new ArrayList<>();
        for (Choice choice : Choice.values()) {
            int column = choice.ordinal() % 2;
            int row = choice.ordinal() / 2;
            placed.add(new Button(choice, left + column * (columnWidth + 4), top + row * (buttonHeight() + 4), columnWidth));
        }
        buttons = List.copyOf(placed);
    }

    private int buttonTop() { return buttons.isEmpty() ? MenuChrome.FOOTER_Y : buttons.get(0).y(); }

    /** The plain headline for a kind of fault; never taken from the exception's message. */
    static String headline(FaultKind kind) {
        return switch (kind) {
            case REWIND -> UiMessages.text("crash.headline.rewind");
            case LEVEL_LOAD -> UiMessages.text("crash.headline.level_load");
            case ENGINE -> UiMessages.text("crash.headline.engine");
        };
    }

    /** The line under MICA's gag that says what the picture shows. */
    static String caption(FaultKind kind) {
        return switch (kind) {
            case REWIND -> UiMessages.text("crash.caption.rewind");
            case LEVEL_LOAD -> UiMessages.text("crash.caption.level_load");
            case ENGINE -> UiMessages.text("crash.caption.engine");
        };
    }

    private static String label(Choice choice) {
        return switch (choice) {
            case REPORT -> UiMessages.text("crash.action.report");
            case COPY -> UiMessages.text("crash.action.copy");
            case DETAILS -> UiMessages.text("crash.action.details");
            case RETURN -> UiMessages.text("crash.action.return");
        };
    }

    private static int buttonHeight() { return Math.max(12, MenuChrome.compactLineHeight() + 3); }

    private static int pitch() { return Math.max(10, MenuChrome.compactLineHeight() + 1); }
}
