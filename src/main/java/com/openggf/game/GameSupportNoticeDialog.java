package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.launch.GameSupportNotices;
import com.openggf.graphics.PixelFont;
import com.openggf.i18n.UiMessages;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * "Please note" dialog over the dimmed hub, shown before starting a game whose support is not
 * finished or has limited ROM data ({@link GameSupportNotices}). It offers Don't show again
 * beside Continue. Back returns to the hub without starting anything.
 *
 * <p>Long translations and tall scripts page the body (Left/Right or the wheel while it has more
 * than one page) instead of clipping it; the toggle and Continue stack when they cannot share a row.
 */
public final class GameSupportNoticeDialog {
    public enum Result { NONE, CONTINUE, BACK, OPEN_LINK }

    /** Focusable controls, in reading order. */
    enum Focus { DISCORD, REPORT_ISSUE, DONT_SHOW_AGAIN, CONTINUE }

    /** A link row: its icon and the address shown verbatim (never translated) and opened over https. */
    record Link(Focus focus, MenuChrome.Icon icon, String address) { }

    /** Where the bug-report wording sends players, one row each. */
    static final List<Link> LINKS = List.of(
            new Link(Focus.DISCORD, MenuChrome.Icon.CHAT, "openggf.com/discord"),
            new Link(Focus.REPORT_ISSUE, MenuChrome.Icon.BUG, "openggf.com/report-issue"));

    private static final int PAD = 12;
    private static final int BUTTON_H = 20;
    private static final int GAP = 8;
    private static final int MAX_PANEL_W = 272;
    private static final int LINK_GAP = 2;

    private final String gameName;
    private final GameSupportNotices.Stage stage;
    private final List<Link> links;
    private Focus focus = Focus.CONTINUE;
    private boolean dontShowAgain;
    private Result result = Result.NONE;
    private int bodyPage;
    private int frame;
    private int width = MasterTitleScreen.SCREEN_W;

    public GameSupportNoticeDialog(String gameName, GameSupportNotices.Stage stage) {
        this.gameName = Objects.requireNonNull(gameName, "gameName");
        this.stage = Objects.requireNonNull(stage, "stage");
        this.links = stage.showsSupportLinks() ? LINKS : List.of();
    }

    public GameSupportNotices.Stage stage() { return stage; }
    public boolean dontShowAgain() { return dontShowAgain; }
    Focus focus() { return focus; }

    /** The address of the focused link, which {@link Result#OPEN_LINK} asks the host to open. */
    public Optional<String> focusedLink() {
        return links.stream().filter(link -> link.focus() == focus).map(Link::address).findFirst();
    }

    /** The decision taken this frame, cleared once read. */
    public Result consumeResult() {
        Result taken = result;
        result = Result.NONE;
        return taken;
    }

    public void update(InputHandler input, MasterTitlePointer pointer) {
        frame++;
        Layout layout = layout();
        if (MenuInput.back(input) || pointer != null && (pointer.backClick()
                || pointer.click() && pointer.over(0, MenuChrome.FOOTER_Y, width, MenuChrome.FOOTER_H))) {
            result = Result.BACK;
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            return;
        }
        if (pointer != null) {
            for (Focus target : Focus.values()) {
                MasterTitleScreen.Rect bounds = layout.bounds(target);
                if (bounds == null || !bounds.contains(pointer)) continue;
                if (pointer.moved() && focus != target) { focus = target; MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE); }
                if (pointer.click()) { focus = target; activate(); return; }
            }
            if (pointer.wheel() != 0) turnPage(layout, -Integer.signum(pointer.wheel()));
        }
        if (layout.pages() > 1 && (MenuInput.left(input) || MenuInput.right(input))) {
            turnPage(layout, MenuInput.left(input) ? -1 : 1);
        } else if (MenuInput.up(input) || MenuInput.down(input)) {
            moveRow(layout, MenuInput.up(input) ? -1 : 1);
        } else if (MenuInput.left(input) || MenuInput.right(input)) {
            // Left/Right swap between the two controls; a link row has no neighbour beside it.
            if (focus == Focus.CONTINUE || focus == Focus.DONT_SHOW_AGAIN) {
                focusOn(focus == Focus.CONTINUE ? Focus.DONT_SHOW_AGAIN : Focus.CONTINUE);
            }
        } else if (MenuInput.accept(input)) {
            activate();
        }
    }

    /** Up/Down step through the rows: each link, then the controls (one row, or two when stacked). */
    private void moveRow(Layout layout, int delta) {
        List<List<Focus>> rows = focusRows(layout.stacked() || (layout.pages() > 1 && links.isEmpty()));
        int row = 0;
        while (!rows.get(row).contains(focus)) row++;
        int target = Math.clamp(row + delta, 0, rows.size() - 1);
        if (target == row) return;
        // Arriving on the shared controls row lands on Continue, the primary action.
        focusOn(rows.get(target).contains(Focus.CONTINUE) ? Focus.CONTINUE : rows.get(target).getFirst());
    }

    private List<List<Focus>> focusRows(boolean separateControls) {
        List<List<Focus>> rows = new java.util.ArrayList<>();
        for (Link link : links) rows.add(List.of(link.focus()));
        if (separateControls) {
            rows.add(List.of(Focus.DONT_SHOW_AGAIN));
            rows.add(List.of(Focus.CONTINUE));
        } else {
            rows.add(List.of(Focus.DONT_SHOW_AGAIN, Focus.CONTINUE));
        }
        return rows;
    }

    private void focusOn(Focus target) {
        if (focus == target) return;
        focus = target;
        MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
    }

    private void activate() {
        if (focusedLink().isPresent()) {
            result = Result.OPEN_LINK;
        } else if (focus == Focus.DONT_SHOW_AGAIN) {
            dontShowAgain = !dontShowAgain;
            MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        } else {
            result = Result.CONTINUE;
        }
    }

    private void turnPage(Layout layout, int delta) {
        int next = Math.clamp(bodyPage + delta, 0, layout.pages() - 1);
        if (next != bodyPage) { bodyPage = next; MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE); }
    }

    public void setViewportWidth(int width) {
        this.width = Math.max(MasterTitleScreen.SCREEN_W, width);
    }

    public void render(PixelFont font, InputHandler menuInput) {
        Layout l = layout();
        font.beginMegaBatch();
        MenuChrome.fill(font, 0, 0, width, MenuChrome.FOOTER_Y, new float[] {0, 0, 0}, .62f);
        MenuChrome.fill(font, l.x(), l.y(), l.w(), l.h(), MenuChrome.NAVY, .98f);
        MenuChrome.outline(font, l.x(), l.y(), l.w(), l.h(), MenuChrome.GOLD, 1f, 1);

        // Title and stage tag centred together as one run; the tag carries the emphasis.
        String title = UiMessages.text("hub.notice.title_caps");
        String tag = stage.stageTag();
        int run = MenuChrome.labelWidth(title) + 6 + MenuChrome.tagWidth(tag);
        int titleX = l.x() + (l.w() - run) / 2;
        MenuChrome.label(font, title, titleX, l.titleY() + labelAbove(), MenuChrome.GOLD);
        MenuChrome.tag(font, titleX + MenuChrome.labelWidth(title) + 6,
                l.titleY() + (MenuChrome.labelLineHeight() - tagHeight()) / 2, tag, MenuChrome.AMBER);

        int y = l.bodyY();
        for (String line : l.page(bodyPage)) {
            if (!line.isEmpty()) MenuChrome.compact(font, line, l.x() + (l.w() - MenuChrome.compactWidth(line)) / 2,
                    y + MenuChrome.compactMetrics().above(), MenuChrome.TEXT);
            y += line.isEmpty() ? GAP / 2 : bodyPitch();
        }
        String pageText = (bodyPage + 1) + "/" + l.pages();
        if (l.pages() > 1 && !links.isEmpty()) {
            MenuChrome.compact(font, pageText, l.x() + l.w() - PAD - MenuChrome.compactWidth(pageText),
                    l.bounds(links.getFirst().focus()).y() + (linkHeight() - MenuChrome.compactLineHeight()) / 2 + 1
                            + MenuChrome.compactMetrics().above(), MenuChrome.DIM);
        }
        for (Link link : links) {
            MasterTitleScreen.Rect r = l.bounds(link.focus());
            boolean focused = focus == link.focus();
            if (focused) MenuChrome.focus(font, r.x(), r.y(), r.w(), r.h(), frame);
            MenuChrome.icon(font, link.icon(), r.x() + 6, r.y() + (r.h() - MenuChrome.ICON_SIZE) / 2,
                    focused ? MenuChrome.GOLD : MenuChrome.CYAN);
            MenuChrome.compact(font, link.address(), r.x() + 6 + MenuChrome.ICON_SIZE + 4,
                    r.y() + (r.h() - MenuChrome.compactLineHeight()) / 2 + 1 + MenuChrome.compactMetrics().above(),
                    focused ? MenuChrome.WHITE : MenuChrome.CYAN);
        }

        MasterTitleScreen.Rect toggle = l.bounds(Focus.DONT_SHOW_AGAIN);
        if (focus == Focus.DONT_SHOW_AGAIN) MenuChrome.focus(font, toggle.x(), toggle.y(), toggle.w(), toggle.h(), frame);
        else MenuChrome.panel(font, toggle.x(), toggle.y(), toggle.w(), toggle.h());
        MenuChrome.toggle(font, toggle.x() + 6, toggle.y() + (toggle.h() - 7) / 2, dontShowAgain, true);
        MenuChrome.compact(font, dontShowAgainLabel(), toggle.x() + 6 + MenuChrome.TOGGLE_W + 6,
                toggle.y() + (toggle.h() - MenuChrome.compactLineHeight()) / 2 + 1 + MenuChrome.compactMetrics().above(),
                focus == Focus.DONT_SHOW_AGAIN ? MenuChrome.WHITE : MenuChrome.TEXT);
        MasterTitleScreen.Rect button = l.bounds(Focus.CONTINUE);
        MenuChrome.button(font, button.x(), button.y(), button.w(), button.h(), continueLabel(),
                focus == Focus.CONTINUE, true, frame);

        List<MenuChrome.Hint> hints = new java.util.ArrayList<>();
        if (l.pages() > 1) {
            String label = UiMessages.text("menu.details.page");
            hints.add(MenuChrome.leftRight(menuInput, links.isEmpty() ? label + " " + pageText : label));
        }
        hints.add(MenuChrome.hint(MenuChrome.confirmKey(menuInput),
                UiMessages.text(focusedLink().isPresent() ? "hub.ui.open_caps" : "hub.ui.select_caps")));
        hints.add(MenuChrome.hint(MenuChrome.backKey(menuInput), UiMessages.text("hub.ui.back_caps")));
        MenuChrome.footer(font, width, null, hints, MenuChrome.controller(menuInput), frame);
        font.endMegaBatch();
    }

    /** Hit-test and draw geometry, measured from the current language's text. */
    record Layout(int x, int y, int w, int h, int titleY, int bodyY, List<List<String>> bodyPages,
                  List<MasterTitleScreen.Rect> links, MasterTitleScreen.Rect toggle, MasterTitleScreen.Rect button,
                  boolean stacked) {
        int pages() { return bodyPages.size(); }
        List<String> page(int index) { return bodyPages.get(Math.clamp(index, 0, bodyPages.size() - 1)); }
        MasterTitleScreen.Rect bounds(Focus focus) {
            return switch (focus) {
                case CONTINUE -> button;
                case DONT_SHOW_AGAIN -> toggle;
                default -> focus.ordinal() < links.size() ? links.get(focus.ordinal()) : null;
            };
        }
    }

    Layout layout() {
        int w = Math.min(width - 24, MAX_PANEL_W);
        int inner = w - 2 * PAD;
        int toggleW = 6 + MenuChrome.TOGGLE_W + 6 + MenuChrome.compactWidth(dontShowAgainLabel()) + 8;
        int buttonW = Math.max(88, MenuChrome.compactWidth(continueLabel()) + 24);
        boolean stacked = toggleW + GAP + buttonW > inner;
        int controlsH = stacked ? 2 * BUTTON_H + GAP : BUTTON_H;
        int linksH = links.isEmpty() ? 0 : links.size() * linkHeight() + (links.size() - 1) * LINK_GAP + GAP;
        int fixed = PAD + MenuChrome.labelLineHeight() + GAP + GAP + linksH + controlsH + PAD;
        int maxBodyH = MenuChrome.FOOTER_Y - 8 - fixed;
        List<String> lines = MenuStyle.wrap(bodyText(), inner, MenuStyle.COMPACT);
        List<List<String>> pages = paginate(lines, maxBodyH);
        int bodyH = pages.stream().mapToInt(GameSupportNoticeDialog::pageHeight).max().orElse(0);
        int h = fixed + bodyH;
        int x = (width - w) / 2;
        int y = Math.max(4, (MenuChrome.FOOTER_Y - h) / 2);
        int titleY = y + PAD;
        int bodyY = titleY + MenuChrome.labelLineHeight() + GAP;
        int linkY = bodyY + bodyH + GAP;
        List<MasterTitleScreen.Rect> linkBounds = new java.util.ArrayList<>();
        for (Link link : links) {
            int linkW = Math.min(inner, 6 + MenuChrome.ICON_SIZE + 4 + MenuChrome.compactWidth(link.address()) + 6);
            linkBounds.add(new MasterTitleScreen.Rect(x + (w - linkW) / 2, linkY, linkW, linkHeight()));
            linkY += linkHeight() + LINK_GAP;
        }
        int controlsY = bodyY + bodyH + GAP + linksH;
        MasterTitleScreen.Rect toggle;
        MasterTitleScreen.Rect button;
        if (stacked) {
            toggle = new MasterTitleScreen.Rect(x + (w - Math.min(toggleW, inner)) / 2, controlsY, Math.min(toggleW, inner), BUTTON_H);
            button = new MasterTitleScreen.Rect(x + (w - buttonW) / 2, controlsY + BUTTON_H + GAP, buttonW, BUTTON_H);
        } else {
            toggle = new MasterTitleScreen.Rect(x + PAD, controlsY, toggleW, BUTTON_H);
            button = new MasterTitleScreen.Rect(x + w - PAD - buttonW, controlsY, buttonW, BUTTON_H);
        }
        return new Layout(x, y, w, h, titleY, bodyY, pages, List.copyOf(linkBounds), toggle, button, stacked);
    }

    private static List<List<String>> paginate(List<String> lines, int maxHeight) {
        List<List<String>> pages = new java.util.ArrayList<>();
        List<String> page = new java.util.ArrayList<>();
        int height = 0;
        for (String line : lines) {
            int pitch = line.isEmpty() ? GAP / 2 : bodyPitch();
            if (!page.isEmpty() && height + pitch > maxHeight) {
                pages.add(List.copyOf(page));
                page.clear();
                height = 0;
                if (line.isEmpty()) continue; // a paragraph break never starts a page
            }
            page.add(line);
            height += pitch;
        }
        if (!page.isEmpty() || pages.isEmpty()) pages.add(List.copyOf(page));
        return List.copyOf(pages);
    }

    private static int pageHeight(List<String> page) {
        return page.stream().mapToInt(line -> line.isEmpty() ? GAP / 2 : bodyPitch()).sum();
    }

    private static int bodyPitch() { return Math.max(MenuChrome.DESCRIPTION_PITCH + 1, MenuChrome.compactLineHeight() + 1); }
    private static int linkHeight() { return Math.max(MenuChrome.ICON_SIZE, MenuChrome.compactLineHeight()) + 6; }
    private static int tagHeight() { return Math.max(10, MenuChrome.compactLineHeight() + 2); }
    private static int labelAbove() { return Math.max(0, MenuChrome.labelLineHeight() - 10); }

    String bodyText() { return stage.bodyText(gameName); }

    private static String dontShowAgainLabel() { return UiMessages.text("hub.notice.dont_show_again"); }
    private static String continueLabel() { return UiMessages.text("hub.notice.continue"); }
}
