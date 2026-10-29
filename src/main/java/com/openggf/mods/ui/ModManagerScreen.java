package com.openggf.mods.ui;

import com.openggf.i18n.UiMessages;
import com.openggf.mods.InvalidModEntry;
import com.openggf.mods.ModCatalog;
import com.openggf.mods.ModCatalogEntry;
import com.openggf.mods.ModCompatibilityDiagnostic;
import com.openggf.mods.EffectiveCatalogBuilder;
import com.openggf.mods.ModDependency;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModEligibility;
import com.openggf.mods.ModFinding;
import com.openggf.mods.ModFindingSeverity;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModState;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.PendingModStateEditor;
import com.openggf.mods.RepositoryScanFailure;
import com.openggf.game.session.PatternWindowState;
import com.openggf.mods.code.ModPatternWindowAllocator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Pending-state editor for the current catalog. The host activates saved changes
 * at the title screen, where no gameplay session owns mod resources.
 */
public final class ModManagerScreen {
    public enum Feedback { NAVIGATE, CONFIRM, CANCEL, ERROR }
    private boolean feedbackError;

    public static final int MAX_VISIBLE_ROWS = 6;
    public static final int MAX_VISIBLE_DETAILS = 12;
    private static final float SCALE = 2f / 3f;
    private static final int LINE_HEIGHT = 11;
    private static final int MAX_RENDER_CHARS = 50;
    private static final int CONFIRM_VISIBLE_LINES = 8;
    private static final int CONFIRM_ACTION_Y = 174;
    /** Geometry shared by render and pointer hit tests. */
    private static final int LIST_Y = 52;
    private static final int ROW_HEIGHT = 14;
    private static final int ACTION_Y = 178;
    private static final int ACTION_HEIGHT = 16;
    private static final int[] EDITABLE_ACTION_X = {8, 86, 146, 226};
    private static final int[] EDITABLE_ACTION_W = {74, 56, 76, 86};
    private static final int[] FIXED_ACTION_X = {8, 112, 216};
    private static final int[] FIXED_ACTION_W = {96, 96, 96};
    private static final int PROMPT_Y = 112;
    private static final int PROMPT_HEIGHT = 22;
    private static final int[] PROMPT_X = {33, 87, 168};
    private static final int[] PROMPT_W = {46, 73, 118};

    private final ModCatalog catalog;
    private final PendingModStateEditor editor;
    private final ModRuntimeFindingStore runtimeFindings;
    private final TextSink text;
    private final Map<String, ModDescriptor> descriptorsById;
    private final Map<String, Integer> descriptorCounts;
    private final List<ModDescriptor> descriptorsInScanOrder;
    private final List<InvalidModEntry> invalidEntries;
    private final List<RepositoryScanFailure> repositoryFailures;
    private final PatternWindowState patternWindows;
    private final Set<Path> bundledSourcePaths;

    private List<Row> rows = List.of();
    private String gameFilter;
    private int selectedIndex;
    private int scrollOffset;
    private int detailScrollOffset;
    private MenuInput previousInput = MenuInput.NEUTRAL;
    private boolean previousEscape;
    private boolean suppressInputUntilNeutral;
    private ArmedCascade armedCascade;
    private ArmedTrust armedTrust;
    private String statusMessage = "";
    /** Severity travels with the diagnostic, independent of translated wording. */
    private final Map<String, Tone> readingTones = new HashMap<>();
    private String saveFailure;
    private String reloadFailure;
    private boolean closeRequested;
    private boolean activationRequested;
    private boolean actionFocus;
    private boolean discardPrompt;
    /** Leave prompt choice, shared with Settings and Game Setup: 0 Save, 1 Discard, 2 Keep editing. */
    private int promptChoice;
    /** Save from the leave prompt closes once the reload has had a frame to report failure. */
    private boolean closeAfterApply;
    private int selectedAction;
    private boolean detailsPage;
    private boolean noticesPage;
    private boolean orderPage;
    private int pageOffset;
    private String confirmLabel = "Enter";
    private String backLabel = "Esc";
    private String directionLabel = "Arrows";
    private boolean controller;

    public ModManagerScreen(ModCatalog catalog, PendingModStateEditor editor,
                            ModRuntimeFindingStore runtimeFindings, TextSink text) {
        this(catalog, editor, runtimeFindings, text, PatternWindowState.EMPTY);
    }

    public ModManagerScreen(ModCatalog catalog, PendingModStateEditor editor,
                            ModRuntimeFindingStore runtimeFindings, TextSink text,
                            PatternWindowState patternWindows) {
        this(catalog, editor, runtimeFindings, text, patternWindows, Set.of());
    }

    /**
     * {@code bundledSourcePaths} names the catalog entries shipped with this engine build. They
     * carry a BUNDLED badge and can be enabled or disabled like any mod, but not uninstalled.
     */
    public ModManagerScreen(ModCatalog catalog, PendingModStateEditor editor,
                            ModRuntimeFindingStore runtimeFindings, TextSink text,
                            PatternWindowState patternWindows,
                            Set<Path> bundledSourcePaths) {
        this.bundledSourcePaths = Set.copyOf(Objects.requireNonNull(bundledSourcePaths, "bundledSourcePaths"));
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.editor = Objects.requireNonNull(editor, "editor");
        this.runtimeFindings = Objects.requireNonNull(runtimeFindings, "runtimeFindings");
        this.text = text;
        this.patternWindows = Objects.requireNonNull(patternWindows, "patternWindows");
        Map<String, ModDescriptor> descriptors = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<ModDescriptor> scannedDescriptors = new ArrayList<>();
        List<InvalidModEntry> invalid = new ArrayList<>();
        List<RepositoryScanFailure> failures = new ArrayList<>();
        for (ModCatalogEntry entry : catalog.scanned()) {
            if (entry instanceof ModDescriptor descriptor) {
                scannedDescriptors.add(descriptor);
                descriptors.putIfAbsent(descriptor.manifest().id(), descriptor);
                counts.merge(descriptor.manifest().id(), 1, Integer::sum);
            } else if (entry instanceof InvalidModEntry invalidEntry) {
                invalid.add(invalidEntry);
            } else if (entry instanceof RepositoryScanFailure failure) {
                failures.add(failure);
            }
        }
        descriptorsById = Map.copyOf(descriptors);
        descriptorCounts = Map.copyOf(counts);
        descriptorsInScanOrder = List.copyOf(scannedDescriptors);
        invalidEntries = List.copyOf(invalid);
        repositoryFailures = List.copyOf(failures);
        normalizeDependencyOrder();
        rebuildRows(null);
        actionFocus = rows.isEmpty();
    }

    /** Engine-neutral state transition used by live composition and focused tests. */
    public void update(MenuInput input) { update(input, event -> { }); }

    /** Neutral feedback events are adapted by the host; no engine audio dependency. */
    public void update(MenuInput input, java.util.function.Consumer<Feedback> feedback) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(feedback, "feedback");
        var before = feedbackState();
        boolean back = (input.escape() && !previousEscape) || (input.menuBack() && !previousInput.menuBack())
                || input.pointerBack();
        boolean accept = (input.menuAccept() && !previousInput.menuAccept()) || input.pointerClick();
        feedbackError = false;
        updateInput(input);
        if (feedbackError) feedback.accept(Feedback.ERROR);
        else if (!before.equals(feedbackState())) feedback.accept(back ? Feedback.CANCEL : accept ? Feedback.CONFIRM : Feedback.NAVIGATE);
    }

    private List<Object> feedbackState() {
        return List.of(selectedIndex, detailScrollOffset, actionFocus, selectedAction, detailsPage,
                noticesPage, orderPage, pageOffset, closeRequested, discardPrompt, promptChoice, statusMessage, editor.pendingState(),
                java.util.Optional.ofNullable(armedTrust), java.util.Optional.ofNullable(armedCascade));
    }

    private void updateInput(MenuInput input) {
        Objects.requireNonNull(input, "input");
        confirmLabel = input.confirmLabel();
        backLabel = input.backLabel();
        directionLabel = input.directionLabel();
        controller = input.controller();
        boolean escapeEdge = input.escape() && !previousEscape;
        previousEscape = input.escape();
        if (suppressInputUntilNeutral) {
            previousInput = input;
            if (isMenuNeutral(input)) suppressInputUntilNeutral = false;
            return;
        }
        boolean up = input.menuUp() && !previousInput.menuUp();
        boolean down = input.menuDown() && !previousInput.menuDown();
        boolean left = input.menuLeft() && !previousInput.menuLeft();
        boolean right = input.menuRight() && !previousInput.menuRight();
        boolean accept = input.menuAccept() && !previousInput.menuAccept();
        boolean back = (input.menuBack() && !previousInput.menuBack()) || input.pointerBack();
        previousInput = input;
        // Mouse: hovering a row, action or prompt button focuses it and clicking runs it;
        // the wheel scrolls and the right button goes back.
        boolean click = input.pointerClick();
        boolean hover = click || input.pointerMoved();
        int px = input.pointerX();
        int py = input.pointerY();
        int wheel = input.wheel();

        if (closeAfterApply && !activationRequested) {
            closeAfterApply = false;
            if (reloadFailure == null && saveFailure == null) { closeRequested = true; return; }
        }
        if (discardPrompt) {
            for (int i = 0; hover && i < PROMPT_X.length; i++) {
                if (!over(px, py, PROMPT_X[i], PROMPT_Y, PROMPT_W[i], PROMPT_HEIGHT)) continue;
                promptChoice = i;
                accept |= click;
            }
            if (left) promptChoice = Math.floorMod(promptChoice - 1, 3);
            if (right) promptChoice = (promptChoice + 1) % 3;
            if (escapeEdge || back) discardPrompt = false;
            else if (accept) {
                discardPrompt = false;
                switch (promptChoice) {
                    case 0 -> {
                        applyDraft();
                        closeAfterApply = activationRequested;
                    }
                    case 1 -> { editor.discardDraft(); closeRequested = true; }
                    default -> { }
                }
            }
            return;
        }
        if (escapeEdge || back) {
            if (detailsPage || noticesPage || orderPage) { detailsPage = false; noticesPage = false; orderPage = false; return; }
            // Keyboard Back steps from the buttons to the list; right-click goes straight back.
            if (actionFocus && !rows.isEmpty() && !input.pointerBack()) { actionFocus = false; return; }
            handleBack();
            return;
        }
        if (orderPage) {
            if (wheel != 0) { reorderSelected(wheel > 0 ? -1 : 1); return; }
            accept |= click;
            if (up || down || left || right) reorderSelected(down || right ? 1 : -1);
            if (accept) orderPage = false;
            return;
        }
        if (detailsPage || noticesPage) {
            // A click leaves a reading page; its keyboard-only actions stay on Enter.
            if (click) { detailsPage = false; noticesPage = false; return; }
            if (wheel != 0) { up = wheel > 0; down = wheel < 0; }
            if (detailsPage) {
                if (accept && editor.editable() && selectedEnabledCodeNeedsTrust()) {
                    detailsPage = false;
                    toggleSelected(false);
                    return;
                }
                if (up || down || left || right) scrollDetails(down ? 1 : up ? -1
                        : right ? visibleDetailCapacity() : -visibleDetailCapacity());
            } else {
                if (up || down || left || right) pageOffset = Math.clamp(pageOffset
                        + (down ? 1 : up ? -1 : right ? visibleDetailCapacity() : -visibleDetailCapacity()),
                        0, Math.max(0, noticeLines().size() - visibleDetailCapacity()));
                if (accept && (saveFailure != null || reloadFailure != null)) applyDraft();
            }
            return;
        }
        if (armedTrust != null || armedCascade != null) {
            if (click && over(px, py, 164, CONFIRM_ACTION_Y, 148, 18)) {
                cancelArmedConfirmation();
                return;
            }
            // A second click confirms, as a second Enter does; the wheel pages.
            accept |= click;
            if (wheel != 0) { left = wheel > 0; right = wheel < 0; }
        } else {
            int[] actionX = editor.editable() ? EDITABLE_ACTION_X : FIXED_ACTION_X;
            int[] actionW = editor.editable() ? EDITABLE_ACTION_W : FIXED_ACTION_W;
            for (int i = 0; hover && i < actionX.length; i++) {
                if (!over(px, py, actionX[i], ACTION_Y, actionW[i], ACTION_HEIGHT)) continue;
                actionFocus = true;
                selectedAction = i;
                accept |= click;
            }
            int end = Math.min(rows.size(), scrollOffset + visibleRowCapacity());
            for (int index = scrollOffset; hover && index < end; index++) {
                if (!over(px, py, 8, LIST_Y - 2 + (index - scrollOffset) * rowHeight(), 304, rowHeight())) continue;
                if (actionFocus || index != selectedIndex) {
                    actionFocus = false;
                    selectedIndex = index;
                    detailScrollOffset = 0;
                    statusMessage = "";
                }
                accept |= click;
            }
            if (wheel != 0 && !rows.isEmpty()) {
                actionFocus = false;
                moveSelection(-wheel);
                return;
            }
        }
        if ((armedTrust != null || armedCascade != null) && (left || right)) {
            pageOffset = Math.clamp(pageOffset + (right ? confirmationCapacity() : -confirmationCapacity()),
                    0, Math.max(0, confirmationLines().size() - confirmationCapacity()));
            return;
        }
        if (actionFocus || rows.isEmpty()) {
            actionFocus = true;
            if (left || right) selectedAction = Math.floorMod(selectedAction + (right ? 1 : -1),
                    editor.editable() ? 4 : 3);
            if ((up || down) && !rows.isEmpty()) { actionFocus = false; return; }
            if (accept) {
                pageOffset = 0;
                if (selectedAction == 0) { detailsPage = true; detailScrollOffset = 0; }
                else if (!editor.editable()) {
                    if (selectedAction == 1) noticesPage = true;
                    else closeRequested = true;
                }
                else if (selectedAction == 1) orderPage = true;
                else if (selectedAction == 2) noticesPage = true;
                else applyDraft();
            }
            return;
        }
        if (input.startHeld() && (up || down)) {
            cancelArmedConfirmation();
            detailsPage = true;
            scrollDetails(down ? 1 : -1);
            return;
        }
        if (up || down || left || right) {
            cancelArmedConfirmation();
            if ((up && selectedIndex == 0) || (down && selectedIndex == rows.size() - 1)) {
                actionFocus = true;
                selectedAction = 0;
            } else if (up) moveSelection(-1);
            else if (down) moveSelection(1);
            else if (left) reorderSelected(-1);
            else { actionFocus = true; selectedAction = 0; }
            return;
        }
        if (accept) {
            pageOffset = 0;
            if (editor.editable()) toggleSelected();
            else { detailsPage = true; detailScrollOffset = 0; }
        }
    }

    public void render() {
        if (text == null) return;
        text.begin();
        try {
            if (discardPrompt) {
                text.page(UiMessages.text("menu.mods.ui.mod_manager_caps"), UiMessages.text("menu.mods.merge.unsaved_changes"));
                text.panel(20, 70, 280, 74);
                drawPrimary(UiMessages.text("menu.mods.ui.save_mod_changes"), 32, 80, 1, .84f, .3f);
                draw(UiMessages.text("menu.mods.merge.your_mod_choices_have_not_been_saved_yet"), 32, 95, .72f, .77f, .9f);
                // Same three choices, order and default as Settings and Game Setup.
                String[] choices = {UiMessages.text("menu.mods.ui.save"), UiMessages.text("menu.mods.ui.discard"), UiMessages.text("menu.mods.ui.keep_editing")};
                for (int i = 0; i < choices.length; i++)
                    text.button(PROMPT_X[i], PROMPT_Y, PROMPT_W[i], PROMPT_HEIGHT, choices[i], promptChoice == i, i == 0);
                text.footer(List.of(new Hint(horizontalKey(), UiMessages.text("menu.mods.merge.choose")), new Hint(confirmLabel, UiMessages.text("menu.mods.merge.select")),
                        new Hint(backLabel, UiMessages.text("menu.mods.merge.keep_editing"))), controller);
                return;
            }
            if (armedTrust != null || armedCascade != null) {
                renderConfirmationPage();
                return;
            }
            if (orderPage) {
                renderOrderPage();
                return;
            }
            if (detailsPage) {
                List<Hint> hints = new ArrayList<>();
                hints.add(new Hint(verticalKey(), UiMessages.text("menu.mods.merge.read")));
                hints.add(new Hint(horizontalKey(), UiMessages.text("menu.mods.merge.page")));
                if (editor.editable() && selectedEnabledCodeNeedsTrust())
                    hints.add(new Hint(confirmLabel, UiMessages.text("menu.mods.merge.disable_without_trust")));
                hints.add(new Hint(backLabel, UiMessages.text("menu.mods.merge.back")));
                renderReadingPage(UiMessages.text("menu.mods.ui.mod_details_caps"), detailReadingLines(), detailScrollOffset, hints);
                return;
            }
            if (noticesPage) {
                List<ReadingLine> lines = noticeLines();
                List<Hint> hints = new ArrayList<>();
                hints.add(new Hint(verticalKey(), UiMessages.text("menu.mods.merge.read")));
                if (saveFailure != null || reloadFailure != null) hints.add(new Hint(confirmLabel, UiMessages.text("menu.mods.merge.retry_apply")));
                hints.add(new Hint(backLabel, UiMessages.text("menu.mods.merge.back")));
                renderReadingPage(UiMessages.text("menu.mods.ui.mod_notices_caps"), lines, pageOffset, hints);
                return;
            }
            int enabledCount = (int) rows().stream().filter(RowView::enabled).count();
            text.page(UiMessages.text("menu.mods.ui.mod_manager_caps"), editor.editable()
                    ? UiMessages.text("menu.mods.merge.installed_count", Map.of("count", rows.size(), "enabled", enabledCount))
                    : UiMessages.text("menu.mods.merge.mods_are_fixed_for_this_launch"));
            text.panel(6, 48, 308, listPanelHeight());
            int y = LIST_Y;
            if (rows.isEmpty()) draw(gameFilter == null ? UiMessages.text("menu.mods.ui.no_mods_apply_to_rescan_jars")
                    : UiMessages.text("menu.mods.ui.no_mods_apply_to_this_game"), 12, y + 1, 1f, 1f, 1f);
            for (RowView row : visibleRows()) {
                boolean selected = rows.get(selectedIndex).identity().equals(row.identity());
                if (selected && !actionFocus) text.focus(8, y - 2, 304, rowHeight());
                drawRow(row, y, selected && !actionFocus);
                y += rowHeight();
            }
            text.scrollbar(310, 50, visibleRowCapacity() * rowHeight() - 4, scrollOffset, visibleRowCapacity(), rows.size());
            String position = (rows.isEmpty() ? "0" : Integer.toString(selectedIndex + 1)) + " / " + rows.size();
            draw(patternBudgetLine(), 9, budgetY(), .53f, .58f, .74f);
            draw(position, 311 - measuredWidth(position, SCALE), budgetY(), .53f, .58f, .74f);
            List<String> banners = bannerLines();
            boolean failure = reloadFailure != null || saveFailure != null;
            String banner = reloadFailure != null ? UiMessages.text("menu.mods.reload_failed", Map.of("error", reloadFailure))
                    : saveFailure != null ? UiMessages.text("menu.mods.save_failed", Map.of("error", saveFailure))
                    : editor.dirty() ? UiMessages.text("menu.mods.ui.draft_choose_apply_to_save")
                    : banners.isEmpty() ? UiMessages.text("menu.mods.merge.no_unsaved_changes") : banners.getFirst();
            text.status(banner, 9, budgetY() + lineHeight() + 1, failure ? Tone.ERROR : editor.dirty() || !banners.isEmpty() ? Tone.WARNING : Tone.GOOD);
            if (!statusMessage.isBlank()) text.status(statusMessage, 9, budgetY() + 2 * (lineHeight() + 1), Tone.WARNING);
            String[] actions = editor.editable() ? new String[]{UiMessages.text("menu.mods.ui.details"), UiMessages.text("menu.mods.ui.order"), UiMessages.text("menu.mods.ui.notices"), UiMessages.text("menu.mods.ui.apply")}
                    : new String[]{UiMessages.text("menu.mods.ui.details"), UiMessages.text("menu.mods.ui.notices"), UiMessages.text("menu.mods.ui.back")};
            int[] actionX = editor.editable() ? EDITABLE_ACTION_X : FIXED_ACTION_X;
            int[] actionWidth = editor.editable() ? EDITABLE_ACTION_W : FIXED_ACTION_W;
            for (int i = 0; i < actions.length; i++) {
                boolean primary = editor.editable() && i == actions.length - 1 && editor.dirty();
                text.button(actionX[i], ACTION_Y, actionWidth[i], ACTION_HEIGHT, actions[i],
                        actionFocus && selectedAction == i, primary);
            }
            List<Hint> hints = new ArrayList<>();
            if (actionFocus) {
                hints.add(new Hint(horizontalKey(), UiMessages.text("menu.mods.merge.choose")));
                hints.add(new Hint(verticalKey(), UiMessages.text("menu.mods.merge.list")));
                hints.add(new Hint(confirmLabel, UiMessages.text("menu.mods.merge.open")));
            } else {
                hints.add(new Hint(verticalKey(), UiMessages.text("menu.mods.merge.select")));
                hints.add(new Hint(horizontalKey(), UiMessages.text("menu.mods.merge.actions")));
                hints.add(new Hint(confirmLabel, editor.editable() ? UiMessages.text("menu.mods.merge.toggle") : UiMessages.text("menu.mods.merge.details")));
            }
            hints.add(new Hint(backLabel, UiMessages.text("menu.mods.merge.back")));
            text.footer(hints, controller);
        } finally {
            text.end();
        }
    }

    private static final int ORDER_VISIBLE = 8, ORDER_TOP = 51, ORDER_ROW = 12;

    /**
     * The load order as a numbered list with the moving mod focused, so a move is seen in place;
     * the rules sit below it and the position is in the header.
     */
    private void renderOrderPage() {
        text.page(UiMessages.text("menu.mods.ui.mod_order_caps"), rows.isEmpty() ? "" : UiMessages.text("menu.mods.merge.order_position", Map.of("position", selectedIndex + 1, "count", rows.size())));
        List<ReadingLine> rules = readingLines(List.of(UiMessages.text("menu.mods.ui.up_moves_earlier_down_moves_later"),
                UiMessages.text("menu.mods.ui.dependencies_must_stay_before_their_dependents"), UiMessages.text("menu.mods.merge.choose_apply_to_save_the_new_order")), false);
        int pitch = Math.max(ORDER_ROW, lineHeight());
        int ruleRoom = rules.size() * lineHeight() + (statusMessage.isBlank() ? 0 : lineHeight() + 4);
        int visible = Math.min(ORDER_VISIBLE, Math.max(1, Math.min(ORDER_VISIBLE * ORDER_ROW,
                190 - ORDER_TOP - 14 - ruleRoom) / pitch));
        text.panel(6, 47, 308, visible * pitch + 8);
        if (rows.isEmpty()) draw(UiMessages.text("menu.mods.ui.no_mods_to_reorder"), 12, ORDER_TOP + 1, .72f, .77f, .9f);
        int first = Math.clamp(selectedIndex - visible / 2, 0, Math.max(0, rows.size() - visible));
        for (int i = first; i < Math.min(rows.size(), first + visible); i++) {
            int y = ORDER_TOP + (i - first) * pitch;
            boolean moving = i == selectedIndex;
            if (moving) text.focus(9, y - 2, 296, pitch);
            String number = Integer.toString(i + 1);
            draw(number, 26 - text.width(number, SCALE), y + 1, .53f, .58f, .74f);
            RowView row = toView(rows.get(i));
            String gameId = row.gameLabel().replaceAll("[\\[\\]\\s]", "").toLowerCase(java.util.Locale.ROOT);
            if (gameId.contains("/")) gameId = "all";
            int x = 32 + text.gameBadge(32, y - 1, gameId) + 5;
            int right = moving ? 300 - text.tagWidth(UiMessages.text("menu.mods.merge.moving")) : 300;
            draw(fit(safeText(row.label()), right - x - 4, SCALE), x, y + 1, moving ? 1f : .91f, moving ? 1f : .93f, 1f);
            if (moving) text.tag(right, y - 1, UiMessages.text("menu.mods.merge.moving"), Tone.WARNING);
        }
        text.scrollbar(308, 50, visible * pitch + 2, first, visible, rows.size());
        int y = 47 + visible * pitch + 14;
        for (ReadingLine line : rules) { drawReadingLine(line, y); y += lineHeight(); }
        if (!statusMessage.isBlank()) text.status(statusMessage, 9, y + 2, Tone.WARNING);
        text.footer(List.of(new Hint(verticalKey(), UiMessages.text("menu.mods.merge.move")), new Hint(backLabel, UiMessages.text("menu.mods.merge.actions"))), controller);
    }

    /** One mod: on/off switch, game badge, name, and its most actionable status tags at the right. */
    private void drawRow(RowView row, int y, boolean selected) {
        boolean live = row.valid() && !row.notLoaded();
        text.toggle(12, y + 2, row.enabled() && live, live);
        String gameId = row.gameLabel().replaceAll("[\\[\\]\\s]", "").toLowerCase(java.util.Locale.ROOT);
        if (gameId.contains("/")) gameId = "all";
        int x = 32 + text.gameBadge(32, y, gameId) + 5;
        int right = 306;
        List<String> badges = new ArrayList<>(row.badges());
        badges.sort(java.util.Comparator.comparingInt(ModManagerScreen::badgePriority));
        List<String> shown = new ArrayList<>();
        int used = 0;
        for (String badge : badges) {
            String label = badgeLabel(badge);
            int w = text.tagWidth(label) + 3;
            if (right - used - w < x + 60) break;
            shown.add(badge);
            used += w;
        }
        int hidden = badges.size() - shown.size();
        if (hidden > 0 && !shown.isEmpty()) used += text.tagWidth("+" + hidden) + 3;
        int nameRoom = right - used - x - 4;
        String name = safeText(row.label());
        name = fit(name, nameRoom, SCALE);
        float bright = !live ? .53f : row.enabled() || selected ? 1f : .91f;
        draw(name, x, y + 1, bright, !live ? .58f : bright, !live ? .74f : 1f);
        int tagX = right - used;
        for (String badge : shown) {
            tagX += text.tag(tagX, y, badgeLabel(badge), badgeTone(badge)) + 3;
        }
        if (hidden > 0 && !shown.isEmpty()) text.tag(tagX, y, "+" + hidden, Tone.MUTED);
    }

    private static int rowHeight() { return Math.max(ROW_HEIGHT, com.openggf.game.MenuChrome.compactLineHeight() + 3); }
    private static int lineHeight() { return Math.max(LINE_HEIGHT, com.openggf.game.MenuChrome.compactLineHeight() + 2); }
    private static int budgetY() { return ACTION_Y - 3 * lineHeight() - 4; }
    private static int listPanelHeight() { return Math.max(rowHeight() + 4, budgetY() - 5 - 48); }
    private static int visibleRowCapacity() { return Math.min(MAX_VISIBLE_ROWS, Math.max(1, (listPanelHeight() - 4) / rowHeight())); }
    private static int visibleDetailCapacity() { return Math.min(MAX_VISIBLE_DETAILS, Math.max(1, 138 / lineHeight())); }
    private static int confirmationCapacity() { return Math.min(CONFIRM_VISIBLE_LINES, Math.max(1, 87 / lineHeight())); }

    private static int badgePriority(String badge) {
        return switch (badge) {
            case "TRUST REQUIRED" -> 0;
            case "UNSUPPORTED", "INVALID" -> 1;
            case "PLAY BLOCKED" -> 2;
            case "BLOCKED" -> 3;
            default -> 4;
        };
    }

    private static String badgeLabel(String badge) {
        return switch (badge) {
            case "TRUST REQUIRED" -> UiMessages.text("menu.mods.merge.needs_trust_caps");
            case "UNSUPPORTED" -> UiMessages.text("menu.mods.merge.unsupported_caps");
            case "INVALID" -> UiMessages.text("menu.mods.merge.invalid_caps");
            case "BUNDLED" -> UiMessages.text("menu.mods.ui.bundled_caps");
            case "BLOCKED" -> UiMessages.text("menu.mods.ui.blocked_caps");
            case "PLAY BLOCKED" -> UiMessages.text("menu.mods.ui.play_blocked_caps");
            case "ERROR" -> UiMessages.text("menu.mods.ui.error_caps");
            case "WARN" -> UiMessages.text("menu.mods.ui.warn_caps");
            case "RUNTIME ERROR" -> UiMessages.text("menu.mods.ui.runtime_error_caps");
            case "RUNTIME WARN" -> UiMessages.text("menu.mods.ui.runtime_warn_caps");
            case "OVERLAP" -> UiMessages.text("menu.mods.ui.overlap_caps");
            case "POSSIBLE CONFLICT" -> UiMessages.text("menu.mods.ui.possible_conflict_caps");
            case "RELATED BLOCKER" -> UiMessages.text("menu.mods.ui.related_blocker_caps");
            default -> badge;
        };
    }

    private static Tone badgeTone(String badge) {
        return switch (badge) {
            case "TRUST REQUIRED", "OVERLAP", "POSSIBLE CONFLICT", "WARN", "RUNTIME WARN" -> Tone.WARNING;
            case "BLOCKED", "PLAY BLOCKED", "RELATED BLOCKER", "INVALID", "UNSUPPORTED", "ERROR", "RUNTIME ERROR" -> Tone.ERROR;
            default -> Tone.INFO;
        };
    }

    private String verticalKey() { return controller ? directionLabel : KEYS_UP_DOWN; }
    private String horizontalKey() { return controller ? directionLabel : KEYS_LEFT_RIGHT; }

    private void renderReadingPage(String title, List<ReadingLine> lines, int requestedOffset, List<Hint> hints) {
        int offset = Math.clamp(requestedOffset, 0, Math.max(0, lines.size() - visibleDetailCapacity()));
        text.page(title, lines.size() <= visibleDetailCapacity() ? "" : UiMessages.text("menu.mods.merge.line_position", Map.of("first", offset + 1, "last", Math.min(lines.size(), offset + visibleDetailCapacity()), "count", lines.size())));
        text.panel(6, 47, 308, 143);
        for (int i = 0; i < visibleDetailCapacity() && offset + i < lines.size(); i++)
            drawReadingLine(lines.get(offset + i), 52 + i * lineHeight());
        text.scrollbar(308, 50, 137, offset, visibleDetailCapacity(), lines.size());
        text.footer(hints, controller);
    }

    private List<ReadingLine> noticeLines() {
        List<String> lines = new ArrayList<>();
        if (!statusMessage.isBlank()) lines.add(statusMessage);
        lines.addAll(bannerLines(true));
        if (lines.isEmpty()) lines.add(editor.editable() ? UiMessages.text("menu.mods.ui.no_notices_apply_reloads_mods_at_title")
                : UiMessages.text("menu.mods.ui.no_notices_mods_are_fixed_for_this_launch"));
        return readingLines(lines, false);
    }

    private void renderConfirmationPage() {
        boolean trust = armedTrust != null;
        List<ReadingLine> lines = confirmationLines();
        int offset = Math.clamp(pageOffset, 0, Math.max(0, lines.size() - confirmationCapacity()));
        text.page(trust ? UiMessages.text("menu.mods.merge.trust_and_enable_caps") : UiMessages.text("menu.mods.ui.confirm_mod_changes_caps"), UiMessages.text("menu.mods.merge.review_before_changing_your_draft"));
        text.panel(6, 48, 308, 114);
        text.lead(trust ? UiMessages.text("menu.mods.merge.full_permissions_required") : armedCascade.enable()
                ? UiMessages.text("menu.mods.merge.enable_required_mods") : UiMessages.text("menu.mods.merge.disable_dependent_mods"), 13, 55, trust, Tone.WARNING);
        for (int i = 0; i < confirmationCapacity() && offset + i < lines.size(); i++)
            drawReadingLine(lines.get(offset + i), 72 + i * lineHeight());
        text.scrollbar(308, 70, 87, offset, confirmationCapacity(), lines.size());
        // Enter/controller accept still confirms and Back/Esc still cancels.
        // The Cancel button also has an explicit pointer target.
        text.button(8, CONFIRM_ACTION_Y, 148, 18, trust ? UiMessages.text("menu.mods.merge.trust_and_enable") : UiMessages.text("menu.mods.merge.confirm_changes"), false, true);
        text.button(164, CONFIRM_ACTION_Y, 148, 18, UiMessages.text("menu.mods.merge.cancel_button"), false, false);
        List<Hint> hints = new ArrayList<>();
        if (lines.size() > confirmationCapacity()) hints.add(new Hint(horizontalKey(), UiMessages.text("menu.mods.merge.read_all")));
        hints.add(new Hint(confirmLabel, UiMessages.text("menu.mods.merge.confirm")));
        hints.add(new Hint(backLabel, UiMessages.text("menu.mods.merge.cancel")));
        text.footer(hints, controller);
    }

    private List<ReadingLine> confirmationLines() {
        List<ReadingLine> lines = new ArrayList<>();
        Set<String> affected = armedTrust != null ? armedTrust.enableIds() : armedCascade.ids();
        addReadingLines(lines, affected.size() == 1 ? UiMessages.text("menu.mods.merge.affected_mod") : UiMessages.text("menu.mods.merge.affected_mods", Map.of("count", affected.size())),
                ReadingStyle.SECTION, Tone.INFO);
        // Pending order is stable even though the armed sets do not preserve order.
        for (ModState.Entry entry : editor.pendingState().entries()) {
            if (!affected.contains(entry.id())) continue;
            ModDescriptor descriptor = descriptorsById.get(entry.id());
            addReadingLines(lines, descriptor == null ? entry.id() : descriptor.manifest().name(),
                    ReadingStyle.TITLE, Tone.INFO);
            addReadingLines(lines, UiMessages.text("menu.mods.merge.id", Map.of("id", entry.id())), ReadingStyle.FIELD, Tone.MUTED);
        }
        addReadingLines(lines, UiMessages.text("menu.mods.merge.what_changes"), ReadingStyle.SECTION, Tone.INFO);
        if (armedTrust != null) {
            addReadingLines(lines, UiMessages.text("menu.mods.merge.this_mod_contains_code_and_runs_with_full_permissions"), ReadingStyle.BODY, Tone.WARNING);
            addReadingLines(lines, UiMessages.text("menu.mods.merge.confirm_trusts_this_jar_and_enables_these_mods"), ReadingStyle.BODY, Tone.MUTED);
        } else {
            addReadingLines(lines, armedCascade.enable() ? UiMessages.text("menu.mods.merge.the_listed_mods_will_be_enabled_together")
                    : UiMessages.text("menu.mods.merge.the_listed_mods_will_be_disabled_together"), ReadingStyle.BODY, Tone.MUTED);
        }
        addReadingLines(lines, UiMessages.text("menu.mods.merge.apply_saves_and_reloads_cancel_keeps_your_draft"), ReadingStyle.BODY, Tone.MUTED);
        return List.copyOf(lines);
    }

    /** {@code column} is the value column of an aligned field, or 0 for an inline line. */
    private record ReadingLine(String value, ReadingStyle style, Tone tone, int column) {
        ReadingLine(String value, ReadingStyle style, Tone tone) { this(value, style, tone, 0); }
    }

    private void drawReadingLine(ReadingLine line, int y) {
        if (line.column() > 0 && line.style() == ReadingStyle.FIELD) text.readingField(line.value(), 12, y, line.column(), line.tone());
        else if (line.style() == ReadingStyle.FIELD_VALUE) text.readingLine(line.value(), 12 + line.column(), y, line.style(), line.tone());
        else text.readingLine(line.value(), 12, y, line.style(), line.tone());
    }

    private static final int MAX_FIELD_COLUMN = 94;

    /**
     * Details read as a label column: every field value starts at one column after the widest
     * short label and wraps within it. Labels too long for the column stay inline.
     */
    private List<ReadingLine> detailReadingLines() {
        List<ReadingLine> inline = readingLines(detailLines(), true);
        int column = 0;
        for (String value : detailLines()) {
            int split = value.indexOf(": ");
            if (split <= 0 || findingTone(value) != null) continue;
            int width = measuredWidth(value.substring(0, split + 1), SCALE) + 6;
            if (width <= MAX_FIELD_COLUMN) column = Math.max(column, width);
        }
        if (column == 0) return inline;
        List<ReadingLine> result = new ArrayList<>();
        List<String> source = detailLines();
        for (int i = 0; i < source.size(); i++) {
            String value = source.get(i);
            int split = value.indexOf(": ");
            boolean field = i > 0 && split > 0 && findingTone(value) == null
                    && measuredWidth(value.substring(0, split + 1), SCALE) + 6 <= column;
            if (!field) { result.addAll(readingLines(List.of(value), i == 0)); continue; }
            String label = value.substring(0, split + 2);
            List<String> parts = wrapped(List.of(value.substring(split + 2)), 294 - column);
            result.add(new ReadingLine(label + parts.getFirst(), ReadingStyle.FIELD, Tone.MUTED, column));
            for (int j = 1; j < parts.size(); j++)
                result.add(new ReadingLine(parts.get(j), ReadingStyle.FIELD_VALUE, Tone.MUTED, column));
        }
        return List.copyOf(result);
    }

    private List<ReadingLine> readingLines(List<String> source, boolean titleFirst) {
        List<ReadingLine> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            String value = source.get(i);
            Tone tone = findingTone(value);
            ReadingStyle style = i == 0 && titleFirst ? ReadingStyle.TITLE
                    : tone != null ? ReadingStyle.FINDING
                    : value.contains(": ") ? ReadingStyle.FIELD : ReadingStyle.BODY;
            addReadingLines(result, value, style, tone == null ? Tone.MUTED : tone);
        }
        return List.copyOf(result);
    }

    private Tone findingTone(String value) {
        Tone semantic = readingTones.get(value);
        if (semantic != null) return semantic;
        if (value.startsWith("ERROR ") || value.startsWith("Runtime error")
                || value.startsWith("Repository error:") || value.startsWith("Save failed:")
                || value.startsWith("Reload failed:") || value.startsWith(UiMessages.text("menu.mods.ui.verified_blocker") + " ")) return Tone.ERROR;
        if (value.startsWith("WARNING ") || value.startsWith("Runtime warning")
                || value.startsWith(UiMessages.text("menu.mods.ui.suspected_incompatibility") + " ")) return Tone.WARNING;
        if (value.startsWith("INFO ") || value.startsWith(UiMessages.text("menu.mods.ui.observed_overlap") + " ")) return Tone.INFO;
        return null;
    }

    private void addTonedLine(List<String> lines, String value, Tone tone) {
        lines.add(value);
        readingTones.put(normalizeText(value), tone);
    }

    private static Tone findingSeverity(ModFindingSeverity severity) {
        return switch (severity) {
            case ERROR -> Tone.ERROR;
            case WARNING -> Tone.WARNING;
            case INFO -> Tone.INFO;
        };
    }

    private void addReadingLines(List<ReadingLine> target, String value, ReadingStyle style, Tone tone) {
        // Reserve the scrollbar gutter even on pages that currently fit. Findings
        // also reserve eleven pixels for their icon; continuations keep that indent.
        List<String> lines = wrapped(List.of(value), style == ReadingStyle.FINDING ? 282 : 294);
        for (int i = 0; i < lines.size(); i++) target.add(new ReadingLine(lines.get(i),
                i > 0 && style == ReadingStyle.FINDING ? ReadingStyle.CONTINUATION : style, tone));
    }

    /** Wrap compact text to a pixel width measured by the host sink. */
    private List<String> wrapped(List<String> source, int maxWidth) {
        List<String> result = new ArrayList<>();
        for (String line : source) {
            String remaining = normalizeText(line);
            while (measuredWidth(remaining, SCALE) > maxWidth) {
                int fit = fittingPrefix(remaining, maxWidth, SCALE);
                int split = remaining.lastIndexOf(' ', fit);
                if (split <= 0) split = Math.max(fit, com.openggf.util.Graphemes.next(remaining, 0));
                result.add(remaining.substring(0, split));
                remaining = remaining.substring(split).stripLeading();
            }
            result.add(remaining);
        }
        return List.copyOf(result);
    }

    public List<RowView> rows() {
        List<RowView> views = new ArrayList<>(rows.size());
        for (Row row : rows) views.add(toView(row));
        return List.copyOf(views);
    }

    public List<RowView> visibleRows() {
        int end = Math.min(rows.size(), scrollOffset + visibleRowCapacity());
        List<RowView> views = new ArrayList<>(Math.max(0, end - scrollOffset));
        for (int index = scrollOffset; index < end; index++) views.add(toView(rows.get(index)));
        return List.copyOf(views);
    }

    public List<String> detailLines() {
        readingTones.clear();
        if (rows.isEmpty()) return List.of(UiMessages.text("menu.mods.ui.no_mods_discovered"));
        Row selected = rows.get(selectedIndex);
        if (selected.invalid() != null) return invalidDetailLines(selected.invalid(),
                isBundled(selected.invalid()));
        return descriptorDetailLines(selected.descriptor());
    }

    public List<String> visibleDetailLines() {
        List<String> details = detailReadingLines().stream().map(ReadingLine::value).toList();
        int maximum = Math.max(0, details.size() - visibleDetailCapacity());
        detailScrollOffset = Math.max(0, Math.min(detailScrollOffset, maximum));
        int end = Math.min(details.size(), detailScrollOffset + visibleDetailCapacity());
        return List.copyOf(details.subList(detailScrollOffset, end));
    }

    public List<String> bannerLines() {
        return bannerLines(false);
    }

    private List<String> bannerLines(boolean includeCatalogCompatibility) {
        readingTones.clear();
        List<String> banners = new ArrayList<>();
        if (!editor.editable()) banners.add(UiMessages.text("menu.mods.ui.mods_are_fixed_for_this_launch"));
        for (RepositoryScanFailure failure : repositoryFailures) {
            for (ModFinding finding : failure.findings()) {
                addTonedLine(banners, UiMessages.text("menu.mods.repository_error", java.util.Map.of("error", finding.message())), Tone.ERROR);
            }
        }
        Set<String> catalogOwners = descriptorsById.keySet();
        runtimeFindings.snapshot().forEach((owner, findings) -> {
            if (catalogOwners.contains(owner)) return;
            for (ModFinding finding : findings) {
                addTonedLine(banners, UiMessages.text("menu.mods.runtime_finding", java.util.Map.of("severity", finding.severity().name(), "owner", owner, "code", finding.code(), "message", finding.message())), findingSeverity(finding.severity()));
            }
        });
        List<ModCompatibilityDiagnostic> compatibility = runtimeFindings.compatibilitySnapshot().stream()
                .filter(diagnostic -> includeCatalogCompatibility || diagnostic.participants().stream()
                        .anyMatch(owner -> !catalogOwners.contains(owner)))
                .toList();
        appendCompatibility(banners, compatibility);
        if (editor.restartRequired()) banners.add(UiMessages.text("menu.mods.ui.changes_await_apply"));
        if (saveFailure != null) addTonedLine(banners, UiMessages.text("menu.mods.save_failed", java.util.Map.of("error", saveFailure)), Tone.ERROR);
        if (reloadFailure != null) addTonedLine(banners, UiMessages.text("menu.mods.reload_failed", java.util.Map.of("error", reloadFailure)), Tone.ERROR);
        return immutableSafeLines(banners);
    }

    public int selectedIndex() { return selectedIndex; }

    public int scrollOffset() { return scrollOffset; }

    public int detailScrollOffset() { return detailScrollOffset; }

    public boolean cascadeArmed() { return armedCascade != null; }

    public boolean trustArmed() { return armedTrust != null; }

    public boolean restartRequired() { return editor.restartRequired(); }

    public ModState pendingState() { return editor.pendingState(); }

    public String statusMessage() { return statusMessage; }

    public boolean consumeActivationRequested() {
        boolean requested = activationRequested;
        activationRequested = false;
        return requested;
    }

    public void activationFailed(String message) {
        reloadFailure = message;
        statusMessage = UiMessages.text("menu.mods.saved_reload_failed", java.util.Map.of("error", message));
    }

    public String patternBudgetLine() {
        return UiMessages.text("menu.mods.merge.art_slots", Map.of("used", patternWindows.totalWindows(), "total", ModPatternWindowAllocator.MAX_WINDOWS));
    }

    public boolean closeRequested() { return closeRequested; }

    public boolean consumeCloseRequested() {
        boolean requested = closeRequested;
        closeRequested = false;
        return requested;
    }

    /** Prevents the action that opened this screen from toggling its first row. */
    public void suppressInputUntilNeutral() {
        suppressInputUntilNeutral = true;
        previousInput = MenuInput.NEUTRAL;
    }

    /** Filters presentation only; pending state and dependency operations retain the full catalog. */
    public void setGameFilter(String gameId) {
        gameFilter = gameId;
        selectedIndex = 0;
        scrollOffset = 0;
        rebuildRows(null);
        actionFocus = rows.isEmpty();
    }

    /** Selects a visible catalog row without changing pending state. */
    public void select(int index) {
        if (index < 0 || index >= rows.size()) {
            throw new IllegalArgumentException("Selection outside catalog rows: " + index);
        }
        selectedIndex = index;
        actionFocus = false;
        detailsPage = false;
        noticesPage = false;
        orderPage = false;
        keepSelectionVisible();
        detailScrollOffset = 0;
        armedCascade = null;
        armedTrust = null;
    }

    private void toggleSelected() {
        toggleSelected(true);
    }

    private boolean selectedEnabledCodeNeedsTrust() {
        if (rows.isEmpty()) return false;
        ModDescriptor descriptor = rows.get(selectedIndex).descriptor();
        return descriptor != null && descriptor.containsCode()
                && isEnabled(descriptor.manifest().id()) && !isTrusted(descriptor);
    }

    private void toggleSelected(boolean preferTrustRenewal) {
        if (rows.isEmpty()) return;
        Row row = rows.get(selectedIndex);
        if (row.descriptor() == null) {
            statusMessage = UiMessages.text("menu.mods.ui.invalid_jars_cannot_be_enabled");
            feedbackError = true;
            return;
        }
        String id = row.descriptor().manifest().id();
        boolean enabled = isEnabled(id);
        boolean requiresTrust = preferTrustRenewal
                && row.descriptor().containsCode() && !isTrusted(row.descriptor());
        LinkedHashSet<String> cascade = enabled && !requiresTrust
                ? enabledDependentClosure(id) : disabledDependencyClosure(id);
        cascade.add(id);
        if (!enabled || requiresTrust) {
            String refusal = enableRefusal(cascade, id);
            if (refusal != null) {
                statusMessage = UiMessages.text("menu.mods.enable_refused", java.util.Map.of("id", id, "reason", refusal));
            feedbackError = true;
                armedCascade = null;
                armedTrust = null;
                return;
            }
            if (row.descriptor().containsCode() && !isTrusted(row.descriptor())) {
                if (armedTrust != null && armedTrust.id().equals(id)
                        && armedTrust.sha256().equals(row.descriptor().sha256())
                        && armedTrust.enableIds().equals(Set.copyOf(cascade))) {
                    editor.trust(id);
                    editor.setEnabledCascade(cascade, true);
                    armedTrust = null;
                    statusMessage = cascadeStatus(id, cascade, true, true);
                    saveFailure = null;
                    rebuildRows(id);
                    return;
                } else {
                    armedCascade = null;
                    armedTrust = new ArmedTrust(id, row.descriptor().sha256(), Set.copyOf(cascade));
                    statusMessage = UiMessages.text("menu.mods.trust_question", java.util.Map.of("mods", String.join(", ", cascade)));
                    return;
                }
            }
        }
        if (cascade.size() > 1) {
            if (armedCascade != null && armedCascade.id().equals(id)
                    && armedCascade.enable() == !enabled) {
                editor.setEnabledCascade(armedCascade.ids(), armedCascade.enable());
                statusMessage = armedCascade.enable()
                        ? cascadeStatus(id, armedCascade.ids(), true, false)
                        : cascadeStatus(id, armedCascade.ids(), false, false);
                armedCascade = null;
                armedTrust = null;
                saveFailure = null;
                rebuildRows(id);
            } else {
                armedCascade = new ArmedCascade(id, Set.copyOf(cascade), !enabled);
                armedTrust = null;
                statusMessage = UiMessages.text("menu.mods.cascade_question", java.util.Map.of("enabled", enabled ? "yes" : "no", "mods", String.join(", ", cascade)));
            }
            return;
        }
        editor.setEnabled(id, !enabled);
        armedTrust = null;
        statusMessage = UiMessages.text("menu.mods.toggled", java.util.Map.of("enabled", !enabled ? "yes" : "no", "id", id));
        saveFailure = null;
        rebuildRows(id);
    }

    private String enableRefusal(Collection<String> ids, String trustPromptId) {
        Map<String, ModEligibility> pendingEligibility = new EffectiveCatalogBuilder()
                .build(catalog.scanned(), proposedEnabledState(ids, trustPromptId)).eligibility();
        for (String id : ids) {
            ModDescriptor descriptor = descriptorsById.get(id);
            if (descriptor == null) return UiMessages.text("menu.mods.missing_dependency", java.util.Map.of("id", id));
            ModFinding error = descriptor.findings().stream()
                    .filter(finding -> finding.severity() == ModFindingSeverity.ERROR)
                    .findFirst().orElse(null);
            if (error != null) return error.message();
            ModEligibility frozen = catalog.eligibility().get(id);
            if (frozen != null && frozen.status() == ModEligibility.Status.BLOCKED) {
                String fixedReason = frozen.reasons().stream()
                        .filter(reason -> !Set.of("CODE_TRUST_REQUIRED", "DEPENDENCY_BLOCKED",
                                "DEPENDENCY_DISABLED", "PATTERN_WINDOW_BUDGET_EXCEEDED",
                                "ORDERING_CYCLE").contains(reason.code()))
                        .map(ModEligibility.Reason::message).findFirst().orElse(null);
                if (fixedReason != null) return fixedReason;
            }
            ModEligibility eligibility = pendingEligibility.get(id);
            if (eligibility != null && eligibility.status() == ModEligibility.Status.BLOCKED) {
                String blockingReason = eligibility.reasons().stream()
                        .filter(reason -> !reason.code().equals("CODE_TRUST_REQUIRED")
                                || (!id.equals(trustPromptId)
                                && !isTrusted(descriptorsById.get(id))))
                        .map(ModEligibility.Reason::message)
                        .findFirst().orElse(null);
                if (blockingReason != null) return blockingReason;
            }
        }
        return null;
    }

    private ModState proposedEnabledState(Collection<String> ids, String trustPromptId) {
        Set<String> enabledIds = Set.copyOf(ids);
        List<ModState.Entry> entries = editor.pendingState().entries().stream()
                .map(entry -> {
                    if (!enabledIds.contains(entry.id())) return entry;
                    ModDescriptor descriptor = descriptorsById.get(entry.id());
                    boolean proposedTrust = entry.id().equals(trustPromptId)
                            && descriptor != null && descriptor.containsCode();
                    return new ModState.Entry(entry.id(), true, entry.order(),
                            proposedTrust || entry.trusted(), proposedTrust
                            ? descriptor.sha256() : entry.trustedJarSha256());
                })
                .toList();
        return new ModState(ModState.CURRENT_FORMAT_VERSION, entries);
    }

    private LinkedHashSet<String> disabledDependencyClosure(String root) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        collectDisabledDependencies(root, result, new HashSet<>());
        return result;
    }

    private void collectDisabledDependencies(String id, LinkedHashSet<String> result, Set<String> visited) {
        if (!visited.add(id)) return;
        ModDescriptor descriptor = descriptorsById.get(id);
        if (descriptor == null) return;
        for (ModDependency dependency : descriptor.manifest().dependencies()) {
            collectDisabledDependencies(dependency.id(), result, visited);
            if (!isEnabled(dependency.id())) result.add(dependency.id());
        }
    }

    private LinkedHashSet<String> enabledDependentClosure(String root) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        List<ModDescriptor> orderedDescriptors = new ArrayList<>(descriptorsInScanOrder);
        Map<String, Integer> pendingOrder = pendingOrder();
        orderedDescriptors.sort(Comparator
                .comparingInt((ModDescriptor descriptor) -> pendingOrder.getOrDefault(
                        descriptor.manifest().id(), Integer.MAX_VALUE))
                .thenComparing(descriptor -> descriptor.manifest().id()));
        boolean changed;
        do {
            changed = false;
            for (ModDescriptor descriptor : orderedDescriptors) {
                String candidate = descriptor.manifest().id();
                if (!isEnabled(candidate) || candidate.equals(root) || result.contains(candidate)) continue;
                boolean depends = descriptor.manifest().dependencies().stream()
                        .anyMatch(dependency -> dependency.id().equals(root)
                                || result.contains(dependency.id()));
                if (depends) {
                    result.add(candidate);
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    private void cancelCascade() {
        armedCascade = null;
        statusMessage = UiMessages.text("menu.mods.ui.cascade_cancelled");
    }

    private void cancelArmedConfirmation() {
        if (armedTrust != null) {
            armedTrust = null;
            statusMessage = UiMessages.text("menu.mods.ui.trust_confirmation_cancelled");
        } else if (armedCascade != null) {
            cancelCascade();
        }
    }

    private void reorderSelected(int delta) {
        if (!editor.editable()) return;
        if (rows.isEmpty()) return;
        Row row = rows.get(selectedIndex);
        if (row.descriptor() == null) {
            statusMessage = UiMessages.text("menu.mods.ui.invalid_jars_cannot_be_reordered");
            feedbackError = true;
            return;
        }
        String id = row.descriptor().manifest().id();
        if (descriptorCounts.getOrDefault(id, 0) != 1) {
            statusMessage = UiMessages.text("menu.mods.ui.duplicate_id_jars_cannot_be_reordered");
            feedbackError = true;
            return;
        }
        List<ModState.Entry> current = editor.pendingState().entries();
        int from = indexOf(current, id);
        int target = from + delta;
        if (target < 0 || target >= current.size()) return;
        List<ModState.Entry> candidate = new ArrayList<>(current);
        ModState.Entry moved = candidate.remove(from);
        candidate.add(target, moved);
        if (!dependencyOrderValid(candidate)) {
            statusMessage = UiMessages.text("menu.mods.ui.move_refused_dependency_order_must_be_preserved");
            feedbackError = true;
            return;
        }
        editor.move(id, target);
        saveFailure = null;
        statusMessage = UiMessages.text("menu.mods.moved", java.util.Map.of("id", id));
        rebuildRows(id);
    }

    private boolean dependencyOrderValid(List<ModState.Entry> entries) {
        Map<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < entries.size(); index++) positions.put(entries.get(index).id(), index);
        for (ModDescriptor descriptor : descriptorsInScanOrder) {
            if (descriptorCounts.getOrDefault(descriptor.manifest().id(), 0) != 1) continue;
            Integer dependent = positions.get(descriptor.manifest().id());
            if (dependent == null) continue;
            for (ModDependency dependency : descriptor.manifest().dependencies()) {
                Integer required = positions.get(dependency.id());
                if (required != null && required >= dependent) return false;
            }
        }
        return true;
    }

    private void moveSelection(int delta) {
        if (rows.isEmpty()) return;
        selectedIndex = Math.max(0, Math.min(rows.size() - 1, selectedIndex + delta));
        keepSelectionVisible();
        detailScrollOffset = 0;
        statusMessage = "";
    }

    private void scrollDetails(int delta) {
        int maximum = Math.max(0, detailReadingLines().size() - visibleDetailCapacity());
        int next = Math.max(0, Math.min(maximum, detailScrollOffset + delta));
        if (next == detailScrollOffset) return;
        detailScrollOffset = next;
    }

    private void handleBack() {
        if (armedTrust != null || armedCascade != null) cancelArmedConfirmation();
        else if (editor.dirty()) { discardPrompt = true; promptChoice = 0; }
        else closeRequested = true;
    }

    private void applyDraft() {
        ModStateSaveResult result = editor.save();
        if (result instanceof ModStateSaveResult.Saved) {
            saveFailure = null;
            reloadFailure = null;
            statusMessage = UiMessages.text("menu.mods.ui.saved_reloading_mods");
            activationRequested = true;
            closeRequested = false;
        } else if (result instanceof ModStateSaveResult.Failed failed) {
            saveFailure = failed.message();
            feedbackError = true;
            statusMessage = UiMessages.text("menu.mods.ui.pending_changes_were_not_saved");
            closeRequested = false;
        }
    }

    private void rebuildRows(String preserveIdentity) {
        Map<String, Integer> pendingOrder = pendingOrder();
        List<ModDescriptor> ordered = new ArrayList<>(descriptorsInScanOrder);
        ordered.sort(Comparator.comparingInt(descriptor -> pendingOrder.getOrDefault(
                descriptor.manifest().id(), Integer.MAX_VALUE)));
        List<Row> rebuilt = new ArrayList<>(ordered.size() + invalidEntries.size());
        for (ModDescriptor descriptor : ordered) {
            if (gameFilter != null && !gameFilter.equals(descriptor.manifest().baseGame())
                    && !"any".equals(descriptor.manifest().baseGame())) continue;
            String id = descriptor.manifest().id();
            String identity = descriptorCounts.getOrDefault(id, 0) > 1
                    ? id + " (" + filename(descriptor.jarPath()) + ")" : id;
            rebuilt.add(new Row(descriptor, null, identity));
        }
        for (InvalidModEntry invalid : invalidEntries) {
            if (gameFilter != null) continue;
            rebuilt.add(new Row(null, invalid, filename(invalid.jarPath())));
        }
        rows = List.copyOf(rebuilt);
        if (rows.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            return;
        }
        if (preserveIdentity != null) {
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).identity().equals(preserveIdentity)) {
                    selectedIndex = index;
                    break;
                }
            }
        }
        selectedIndex = Math.min(selectedIndex, rows.size() - 1);
        keepSelectionVisible();
    }

    private Map<String, Integer> pendingOrder() {
        Map<String, Integer> pendingOrder = new HashMap<>();
        List<ModState.Entry> pendingEntries = editor.pendingState().entries();
        for (int index = 0; index < pendingEntries.size(); index++) {
            pendingOrder.put(pendingEntries.get(index).id(), index);
        }
        return pendingOrder;
    }

    private void keepSelectionVisible() {
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
        if (selectedIndex >= scrollOffset + visibleRowCapacity()) {
            scrollOffset = selectedIndex - visibleRowCapacity() + 1;
        }
        int maximum = Math.max(0, rows.size() - visibleRowCapacity());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maximum));
    }

    private RowView toView(Row row) {
        if (row.invalid() != null) {
            return new RowView(row.identity(), row.identity(), "", false, false, false,
                    isBundled(row.invalid()) ? List.of("BUNDLED", "ERROR") : List.of("ERROR"));
        }
        ModDescriptor descriptor = row.descriptor();
        String id = descriptor.manifest().id();
        LinkedHashSet<String> badges = new LinkedHashSet<>();
        if (isBundled(descriptor)) badges.add("BUNDLED");
        ModEligibility eligibility = catalog.eligibility().get(id);
        // Waiting only for trust is not blocked, before or after the draft trusts it (the catalog's
        // eligibility is from the last scan, so a just-trusted mod still lists CODE_TRUST_REQUIRED).
        boolean trustOnly = eligibility != null && !eligibility.reasons().isEmpty()
                && eligibility.reasons().stream().allMatch(reason -> reason.code().equals("CODE_TRUST_REQUIRED"))
                && descriptor.containsCode()
                && !descriptor.hasErrors();
        if (eligibility != null && eligibility.status() == ModEligibility.Status.BLOCKED && !trustOnly)
            badges.add("BLOCKED");
        if (descriptor.containsCode() && !isTrusted(descriptor)) badges.add("TRUST REQUIRED");
        // Current JVM and native Community builds both load code mods; no capability badge applies.
        addFindingBadges(badges, descriptor.findings(), false);
        addFindingBadges(badges, runtimeFindings.findingsFor(id), true);
        for (ModCompatibilityDiagnostic diagnostic : runtimeFindings.compatibilityFor(id)) {
            badges.add(switch (diagnostic.level()) {
                case OBSERVED_OVERLAP -> "OVERLAP";
                case SUSPECTED_INCOMPATIBILITY -> "POSSIBLE CONFLICT";
                case VERIFIED_BLOCKER -> diagnostic.blocksPlayFor(id) ? "PLAY BLOCKED" : "RELATED BLOCKER";
            });
        }
        String label = descriptorCounts.getOrDefault(id, 0) > 1
                ? descriptor.manifest().name() + " (" + filename(descriptor.jarPath()) + ")"
                : descriptor.manifest().name();
        return new RowView(row.identity(), label, "[" + gameLabel(descriptor.manifest()) + "] ",
                isEnabled(id), true, false,
                List.copyOf(badges));
    }

    private static String gameLabel(com.openggf.mods.ModManifest manifest) {
        if (manifest.type() == com.openggf.mods.ModType.STANDALONE) return UiMessages.text("menu.mods.ui.standalone");
        return switch (manifest.baseGame()) {
            case "s1" -> "S1";
            case "s2" -> "S2";
            case "s3k" -> "S3K";
            case "any" -> "S1/S2/S3K";
            default -> throw new IllegalStateException("Unexpected base game: " + manifest.baseGame());
        };
    }

    private static void addFindingBadges(Set<String> badges, List<ModFinding> findings, boolean runtime) {
        boolean error = findings.stream().anyMatch(f -> f.severity() == ModFindingSeverity.ERROR);
        boolean warning = findings.stream().anyMatch(f -> f.severity() == ModFindingSeverity.WARNING);
        if (error) badges.add(runtime ? "RUNTIME ERROR" : "ERROR");
        if (warning) badges.add(runtime ? "RUNTIME WARN" : "WARN");
    }

    /**
     * A cascade result in words: the chosen mod by name, then the others it switched with by name
     * when they fit the status line ("Switched on: Night music, Shared art library"), otherwise
     * their count ("+ 3 it needs"). The draft banner above already says Apply saves it.
     */
    private String cascadeStatus(String id, Collection<String> cascade, boolean enable, boolean trusted) {
        List<String> others = cascade.stream().filter(other -> !other.equals(id)).map(this::modName).toList();
        String chosen = UiMessages.text(trusted ? "menu.mods.merge.trusted_single"
                : enable ? "menu.mods.merge.enabled_single" : "menu.mods.merge.disabled_single", Map.of("mod", modName(id)));
        if (others.isEmpty()) return chosen;
        String names = modName(id) + ", " + String.join(", ", others);
        String named = UiMessages.text(trusted ? "menu.mods.merge.trusted_named"
                : enable ? "menu.mods.merge.enabled_named" : "menu.mods.merge.disabled_named", Map.of("mods", names));
        if (com.openggf.game.MenuChrome.compactWidth(named) <= STATUS_WIDTH) return named;
        return UiMessages.text(trusted ? "menu.mods.merge.trusted_count"
                : enable ? "menu.mods.merge.enabled_count" : "menu.mods.merge.disabled_count", Map.of("mod", modName(id), "count", others.size()));
    }

    /** The status line's room on the 320-wide page (ModManagerScreenHost fits it to W - x - 20). */
    private static final int STATUS_WIDTH = 320 - 9 - 20;

    private String modName(String id) {
        ModDescriptor descriptor = descriptorsById.get(id);
        return descriptor == null ? id : descriptor.manifest().name();
    }

    private List<String> descriptorDetailLines(ModDescriptor descriptor) {
        List<String> lines = new ArrayList<>();
        var manifest = descriptor.manifest();
        lines.add(manifest.name());
        lines.add(UiMessages.text("menu.mods.merge.version", Map.of("version", String.valueOf(manifest.version()))));
        lines.add(UiMessages.text("menu.mods.jar", Map.of("file", filename(descriptor.jarPath()))));
        if (isBundled(descriptor)) {
            lines.add(UiMessages.text("menu.mods.ui.bundled_with_openggf_enabled_by_default_and_trusted_by_this_build_s_manifest_you_can_"));
        }
        lines.add(UiMessages.text("menu.mods.merge.id", Map.of("id", manifest.id())));
        lines.add(UiMessages.text("menu.mods.merge.applies_to", Map.of("game", gameLabel(manifest))));
        int requestedWindows = manifest.patternWindows().orElse(1);
        String allocation = patternWindows.assignment(manifest.id())
                .map(value -> UiMessages.text("menu.mods.merge.allocated_at", Map.of("start", Integer.toHexString(value.base()), "end", Integer.toHexString(value.endExclusive()))))
                // The effective catalog assigns windows only to mods it loaded.
                .orElse(UiMessages.text("menu.mods.merge.none_assigned_until_it_loads"));
        lines.add(UiMessages.text("menu.mods.pattern_windows", Map.of("count",requestedWindows,"allocation",allocation)));
        lines.add(UiMessages.text("menu.mods.authors", Map.of("authors",String.join(", ",manifest.authors()))));
        lines.add(UiMessages.text("menu.mods.description", Map.of("description",manifest.description())));
        if (manifest.dependencies().isEmpty()) {
            lines.add(UiMessages.text("menu.mods.ui.dependencies_none"));
        } else {
            for (ModDependency dependency : manifest.dependencies()) {
                lines.add(UiMessages.text("menu.mods.dependency", java.util.Map.of("id", dependency.id(), "version", dependency.versionRange().toString())));
            }
        }
        ModEligibility eligibility = catalog.eligibility().get(manifest.id());
        if (eligibility != null) {
            for (ModEligibility.Reason reason : eligibility.reasons()) {
                // The reason's message already names the problem; its code stays in the catalog and logs.
                boolean trustedDraft = reason.code().equals("CODE_TRUST_REQUIRED") && isTrusted(descriptor);
                lines.add(UiMessages.text("menu.mods.merge.status", Map.of("status", trustedDraft
                        ? isEnabled(manifest.id()) ? UiMessages.text("menu.mods.merge.trusted_and_on_in_this_draft_choose_apply_to_save")
                                : UiMessages.text("menu.mods.merge.trusted_in_this_draft_switch_it_on_to_use_it")
                        : reason.message())));
            }
        }
        appendFindings(lines, descriptor.findings());
        appendFindings(lines, runtimeFindings.findingsFor(manifest.id()));
        appendCompatibility(lines, runtimeFindings.compatibilityFor(manifest.id()));
        return immutableSafeLines(lines);
    }

    private List<String> invalidDetailLines(InvalidModEntry invalid, boolean bundled) {
        List<String> lines = new ArrayList<>();
        lines.add(UiMessages.text("menu.mods.invalid_jar", java.util.Map.of("file", filename(invalid.jarPath()))));
        if (bundled) {
            lines.add(UiMessages.text("menu.mods.ui.bundled_with_openggf_but_not_loaded_reinstall_openggf_to_repair_it"));
        }
        appendFindings(lines, invalid.findings());
        return immutableSafeLines(lines);
    }

    private void appendFindings(List<String> lines, List<ModFinding> findings) {
        for (ModFinding finding : findings) {
            addTonedLine(lines, finding.severity() + " " + finding.code() + ": " + finding.message(), findingSeverity(finding.severity()));
        }
    }

    private void appendCompatibility(List<String> lines, List<ModCompatibilityDiagnostic> diagnostics) {
        if (diagnostics.isEmpty()) return;
        for (ModCompatibilityDiagnostic diagnostic : new LinkedHashSet<>(diagnostics)) {
            String level = switch (diagnostic.level()) {
                case OBSERVED_OVERLAP -> UiMessages.text("menu.mods.ui.observed_overlap");
                case SUSPECTED_INCOMPATIBILITY -> UiMessages.text("menu.mods.ui.suspected_incompatibility");
                case VERIFIED_BLOCKER -> UiMessages.text("menu.mods.ui.verified_blocker");
            };
            addTonedLine(lines, level + " " + diagnostic.code() + ": " + diagnostic.summary(), switch (diagnostic.level()) {
                case OBSERVED_OVERLAP -> Tone.INFO;
                case SUSPECTED_INCOMPATIBILITY -> Tone.WARNING;
                case VERIFIED_BLOCKER -> Tone.ERROR;
            });
            lines.add(UiMessages.text("menu.mods.target", java.util.Map.of("target", diagnostic.target().toString())));
            lines.add(UiMessages.text("menu.mods.participants", java.util.Map.of("participants", String.join(", ", diagnostic.participants()))));
            if (diagnostic.blocksPlay()) {
                lines.add(UiMessages.text("menu.mods.content_owners", java.util.Map.of("owners", String.join(", ", diagnostic.blockedOwners()))));
            }
            for (String evidence : diagnostic.evidence()) lines.add(UiMessages.text("menu.mods.evidence", java.util.Map.of("evidence", evidence)));
        }
        String context = runtimeFindings.compatibilityContext();
        lines.add(context.isBlank() ? UiMessages.text("menu.mods.ui.active_mod_configuration") : UiMessages.text("menu.mods.active_configuration", java.util.Map.of("context", context)));
        if (editor.restartRequired()) {
            lines.add(UiMessages.text("menu.mods.ui.compatibility_evidence_covers_active_mods_pending_changes_await_apply"));
        }
    }

    private boolean isBundled(ModCatalogEntry entry) {
        return bundledSourcePaths.contains(entry.sourcePath());
    }

    private boolean isEnabled(String id) {
        return editor.pendingState().entries().stream()
                .anyMatch(entry -> entry.id().equals(id) && entry.enabled());
    }

    private boolean isTrusted(ModDescriptor descriptor) {
        return editor.pendingState().entries().stream()
                .filter(entry -> entry.id().equals(descriptor.manifest().id()))
                .findFirst().map(entry -> entry.trustsSha256(descriptor.sha256())).orElse(false);
    }

    private static int indexOf(List<ModState.Entry> entries, String id) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).id().equals(id)) return index;
        }
        throw new IllegalStateException("Scanned mod missing from normalized pending state: " + id);
    }

    private void drawPrimary(String text, int x, int y, float r, float g, float b) {
        this.text.draw(fit(normalizeText(text), 320 - x - 6, 1), x, y, 1, r, g, b, 1);
    }

    /** Keyboard-only hosts have no drawing sink; retain the plain sink's cell metrics. */
    private int measuredWidth(String value, float scale) {
        return text == null ? value.length() * (scale < 1 ? 6 : 9) : text.width(value, scale);
    }

    /** Longest code-point prefix whose sink-measured width fits, possibly empty. */
    private int fittingPrefix(String value, int maxWidth, float scale) {
        int end = 0;
        while (end < value.length()) {
            int next = com.openggf.util.Graphemes.next(value, end);
            if (measuredWidth(value.substring(0, next), scale) > maxWidth) break;
            end = next;
        }
        return end;
    }

    /** Clip text to a pixel width with a trailing ellipsis, measured by the host sink. */
    private String fit(String value, int maxWidth, float scale) {
        if (measuredWidth(value, scale) <= maxWidth) return value;
        int room = maxWidth - measuredWidth("...", scale);
        if (room < 0) return "";
        return value.substring(0, fittingPrefix(value, room, scale)) + "...";
    }

    private void draw(String text, int x, int y, float r, float g, float b) {
        this.text.draw(safeText(text), x, y, SCALE, r, g, b, 1f);
    }

    private static List<String> immutableSafeLines(List<String> lines) {
        return lines.stream().map(ModManagerScreen::normalizeText).toList();
    }

    private static String safeText(String text) {
        String normalized = normalizeText(text);
        return com.openggf.game.MenuChrome.fitCompact(normalized, 302);
    }

    private static String primaryHint(String text) {
        String normalized = normalizeText(text);
        return com.openggf.game.MenuChrome.fitLabel(normalized, 302);
    }

    private static String normalizeText(String text) {
        return Objects.requireNonNull(text, "text").replaceAll("\\s+", " ").trim();
    }

    private static String filename(Path path) {
        Path filename = path.getFileName();
        return filename == null ? path.toString() : filename.toString();
    }

    public record RowView(String identity, String label, String gameLabel, boolean enabled, boolean valid,
                          boolean notLoaded, List<String> badges) {
        public RowView {
            badges = List.copyOf(badges);
        }
    }

    private record Row(ModDescriptor descriptor, InvalidModEntry invalid, String identity) { }

    private record ArmedCascade(String id, Set<String> ids, boolean enable) { }

    private record ArmedTrust(String id, String sha256, Set<String> enableIds) { }

    private static boolean over(int px, int py, int x, int y, int width, int height) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }

    private static boolean isMenuNeutral(MenuInput input) {
        return !input.menuUp() && !input.menuDown() && !input.menuLeft() && !input.menuRight()
                && !input.menuAccept() && !input.menuBack() && !input.startHeld() && !input.escape();
    }

    /** Semantic tone for tags and icons; hosts choose the palette. */
    /** {@code FIELD_VALUE} continues a wrapped field value in its value column. */
    public enum ReadingStyle { TITLE, SECTION, FIELD, BODY, FINDING, CONTINUATION, FIELD_VALUE }

    public enum Tone { INFO, GOOD, WARNING, ERROR, MUTED }

    /** One control hint: a key name and what it does here. */
    public record Hint(String key, String label) { }

    /** Key names for direction pairs; hosts draw them as arrow caps. */
    public static final String KEYS_UP_DOWN = "\u2191\u2193";
    public static final String KEYS_LEFT_RIGHT = "\u2190\u2192";

    public interface TextSink {
        void begin();
        void draw(String text, int x, int y, float scale, float r, float g, float b, float a);
        void end();
        /** Width of one line of text at {@code scale}; plain sinks use fixed 6- and 9-pixel cells. */
        default int width(String text, float scale) { return text.length() * (scale < 1 ? 6 : 9); }
        default void page(String title, String subtitle) {
            draw(title, 9, 7, 1, 1, 1, 1, 1);
            draw(safeText(subtitle), 9, 34, SCALE, .7f, .8f, .95f, 1);
        }
        default void panel(int x, int y, int width, int height) { }
        default void focus(int x, int y, int width, int height) { }
        /** Host-independent ten-pixel primary text cell, centered inside a row. */
        default int primaryTextY(int rowTop, int rowHeight) { return rowTop + (rowHeight - 10) / 2; }
        default void footer(String first, String second) {
            draw(primaryHint(first), 9, 200, 1, .5f, .9f, 1, 1);
            draw(primaryHint(second), 9, 212, 1, .8f, .86f, 1, 1);
        }
        /** Key-cap footer; plain sinks print the hints as one line. */
        default void footer(List<Hint> hints, boolean controller) {
            int x = 9;
            for (Hint hint : hints) {
                String text = hint.key() + " " + hint.label();
                draw(text, x, 210, SCALE, .8f, .86f, 1, 1);
                x += width(text, SCALE) + 12;
            }
        }
        /** A 15x7 on/off switch; plain sinks print its state. */
        default void toggle(int x, int y, boolean on, boolean enabled) {
            draw(on ? "ON" : "OFF", x, y - 1, SCALE, 1, 1, 1, 1);
        }
        /** A game badge for {@code gameId} (empty for every game); returns its width. */
        default int gameBadge(int x, int y, String gameId) {
            String text = gameId.isEmpty() ? "ALL" : gameId.toUpperCase(java.util.Locale.ROOT);
            draw(text, x + 3, y + 1, SCALE, 1, 1, 1, 1);
            return width(text, SCALE) + 6;
        }
        /** A status tag; returns its width. */
        default int tag(int x, int y, String text, Tone tone) {
            draw(text, x + 3, y + 1, SCALE, 1, 1, 1, 1);
            return tagWidth(text);
        }
        default int tagWidth(String text) { return width(text, SCALE) + 6; }
        /** Small status line with a leading icon for its tone. */
        default void status(String text, int x, int y, Tone tone) {
            draw(safeText(text), x + 11, y, SCALE, 1, .75f, .3f, 1);
        }
        /** A framed button with a centred label. */
        default void button(int x, int y, int width, int height, String label, boolean focused, boolean primary) {
            panel(x, y, width, height);
            if (focused) focus(x, y, width, height);
            draw(label, x + Math.max(4, (width - width(label, SCALE)) / 2), y + (height - 8) / 2 + 1, SCALE, 1, 1, 1, 1);
        }
        /** Icon-led warning; plain sinks retain the complete lead text. */
        default void lead(String value, int x, int y, boolean lock, Tone tone) {
            draw(value, x + 11, y, SCALE, 1, .72f, .25f, 1);
        }
        /**
         * A field whose value starts {@code column} pixels after {@code x}; {@code value} keeps its
         * "Label: " prefix, so a plain sink prints the complete line.
         */
        default void readingField(String value, int x, int y, int column, Tone tone) {
            readingLine(value, x, y, ReadingStyle.FIELD, tone);
        }
        /** A wrapped reading line. Styling never removes its text in a plain sink. */
        default void readingLine(String value, int x, int y, ReadingStyle style, Tone tone) {
            int indent = style == ReadingStyle.FINDING || style == ReadingStyle.CONTINUATION ? 11 : 0;
            draw(value, x + indent, y, SCALE, .91f, .93f, 1, 1);
        }
        /** A slim scrollbar; plain sinks draw nothing. */
        default void scrollbar(int x, int top, int height, int first, int visible, int total) { }
    }

    public interface MenuInput {
        MenuInput NEUTRAL = new MenuInput() { };

        default boolean menuUp() { return false; }
        default boolean menuDown() { return false; }
        default boolean menuLeft() { return false; }
        default boolean menuRight() { return false; }
        default boolean menuAccept() { return false; }
        default boolean menuBack() { return false; }
        default boolean startHeld() { return false; }
        default boolean escape() { return false; }
        default String confirmLabel() { return "Enter"; }
        default String backLabel() { return "Esc"; }
        default String directionLabel() { return "Arrows"; }
        /** True when the latest menu input came from a controller. */
        default boolean controller() { return false; }
        /** Pointer in the 320x224 page; negative without a mouse. */
        default int pointerX() { return -1; }
        default int pointerY() { return -1; }
        default boolean pointerMoved() { return false; }
        /** Left and right button presses of this frame only. */
        default boolean pointerClick() { return false; }
        default boolean pointerBack() { return false; }
        /** Wheel notches this frame, positive away from the user. */
        default int wheel() { return 0; }
    }

    private void normalizeDependencyOrder() {
        if (!editor.editable()) return;
        List<ModState.Entry> current = editor.pendingState().entries();
        List<String> currentIds = current.stream().map(ModState.Entry::id).toList();
        Set<String> currentSet = Set.copyOf(currentIds);
        Map<String, Integer> originalIndex = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (int index = 0; index < currentIds.size(); index++) {
            originalIndex.put(currentIds.get(index), index);
            indegree.put(currentIds.get(index), 0);
        }
        for (String id : currentIds) {
            ModDescriptor descriptor = descriptorCounts.getOrDefault(id, 0) == 1
                    ? descriptorsById.get(id) : null;
            if (descriptor == null) continue;
            for (ModDependency dependency : descriptor.manifest().dependencies()) {
                if (!currentSet.contains(dependency.id())) continue;
                indegree.put(id, indegree.get(id) + 1);
                dependents.computeIfAbsent(dependency.id(), ignored -> new ArrayList<>()).add(id);
            }
        }
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (String id : currentIds) if (indegree.get(id) == 0) ready.add(originalIndex.get(id));
        List<String> ordered = new ArrayList<>(currentIds.size());
        Set<String> emitted = new HashSet<>();
        while (!ready.isEmpty()) {
            String id = currentIds.get(ready.remove());
            if (!emitted.add(id)) continue;
            ordered.add(id);
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                int remaining = indegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) ready.add(originalIndex.get(dependent));
            }
        }
        for (String id : currentIds) if (emitted.add(id)) ordered.add(id); // blocked cycles stay stable
        for (int target = 0; target < ordered.size(); target++) {
            String id = ordered.get(target);
            if (descriptorsById.containsKey(id)
                    && indexOf(editor.pendingState().entries(), id) != target) {
                editor.move(id, target);
            }
        }
    }
}
