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
            pageOffset = Math.clamp(pageOffset + (right ? visibleDetailCapacity() : -visibleDetailCapacity()),
                    0, Math.max(0, confirmationLines().size() - visibleDetailCapacity()));
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
                text.page(UiMessages.text("menu.mods.ui.mod_manager_caps"), UiMessages.text("menu.mods.ui.unsaved_changes_caps"));
                drawPrimary(UiMessages.text("menu.mods.ui.save_mod_changes"), 24, 80, 1, 1, 1);
                // Same three choices, order and default as Settings and Game Setup.
                text.focus(PROMPT_X[promptChoice], PROMPT_Y, PROMPT_W[promptChoice], PROMPT_HEIGHT);
                drawPrimary(UiMessages.text("menu.mods.ui.save"), PROMPT_X[0] + 5, 118, 1, 1, 1);
                drawPrimary(UiMessages.text("menu.mods.ui.discard"), PROMPT_X[1] + 5, 118, 1, .73f, .25f);
                drawPrimary(UiMessages.text("menu.mods.ui.keep_editing"), PROMPT_X[2] + 5, 118, 1, 1, 1);
                text.footer(UiMessages.text("menu.mods.ui.left_right_choose"), UiMessages.text("menu.mods.leave_controls", java.util.Map.of("confirm", confirmLabel, "back", backLabel)));
                return;
            }
            if (armedTrust != null || armedCascade != null) {
                renderReadingPage(armedTrust != null ? UiMessages.text("menu.mods.ui.trust_enable_caps") : UiMessages.text("menu.mods.ui.confirm_mod_changes_caps"),
                        confirmationLines(), pageOffset,
                        UiMessages.text("menu.mods.confirm_controls", java.util.Map.of("confirm", confirmLabel, "back", backLabel)), UiMessages.text("menu.mods.ui.left_right_read_all_affected_mods"));
                return;
            }
            if (orderPage) {
                List<String> orderLines = new ArrayList<>();
                orderLines.add(rows.isEmpty() ? UiMessages.text("menu.mods.ui.no_mods_to_reorder") : rows.get(selectedIndex).identity());
                orderLines.add(UiMessages.text("menu.mods.position", java.util.Map.of("position", rows.isEmpty() ? 0 : selectedIndex + 1, "count", rows.size())));
                orderLines.add(UiMessages.text("menu.mods.ui.up_moves_earlier_down_moves_later"));
                orderLines.add(UiMessages.text("menu.mods.ui.dependencies_must_stay_before_their_dependents"));
                if (!statusMessage.isBlank()) orderLines.add(statusMessage);
                renderReadingPage(UiMessages.text("menu.mods.ui.mod_order_caps"), wrapped(orderLines), 0,
                        UiMessages.text("menu.mods.move_controls", java.util.Map.of("directions", directionLabel, "back", backLabel)), UiMessages.text("menu.mods.ui.choose_apply_to_save_the_new_order"));
                return;
            }
            if (detailsPage) {
                renderReadingPage(UiMessages.text("menu.mods.ui.mod_details_caps"), wrapped(detailLines()), detailScrollOffset,
                        UiMessages.text("menu.mods.read_controls", java.util.Map.of("directions", directionLabel, "back", backLabel)),
                        editor.editable() && selectedEnabledCodeNeedsTrust()
                                ? UiMessages.text("menu.mods.disable_control", java.util.Map.of("confirm", confirmLabel)) : UiMessages.text("menu.mods.ui.left_right_page_up_down_line"));
                return;
            }
            if (noticesPage) {
                renderReadingPage(UiMessages.text("menu.mods.ui.mod_notices_caps"), noticeLines(), pageOffset,
                        UiMessages.text("menu.mods.read_controls", java.util.Map.of("directions", directionLabel, "back", backLabel)),
                        saveFailure != null || reloadFailure != null ? UiMessages.text("menu.mods.retry_control", java.util.Map.of("confirm", confirmLabel))
                                : editor.editable() ? UiMessages.text("menu.mods.ui.apply_reloads_mods_at_title") : UiMessages.text("menu.mods.ui.mods_are_fixed_for_this_launch"));
                return;
            }
            text.page(UiMessages.text("menu.mods.ui.mod_manager_caps"), patternBudgetLine());
            text.panel(6, 48, 308, 88);
            int y = LIST_Y;
            if (rows.isEmpty()) draw(gameFilter == null ? UiMessages.text("menu.mods.ui.no_mods_apply_to_rescan_jars")
                    : UiMessages.text("menu.mods.ui.no_mods_apply_to_this_game"), 12, y + 1, 1f, 1f, 1f);
            for (RowView row : visibleRows()) {
                boolean selected = rows.get(selectedIndex).identity().equals(row.identity());
                if (selected && !actionFocus) text.focus(8, y - 2, 304, rowHeight());
                String enabled = (!row.valid() || row.notLoaded()) ? UiMessages.text("menu.mods.ui.") : row.enabled() ? UiMessages.text("menu.mods.ui.on_caps") : UiMessages.text("menu.mods.ui.off_caps");
                String badges = row.badges().isEmpty() ? "" : " [" + String.join(",", row.badges()) + "]";
                float green = !row.valid() ? .4f : row.enabled() ? .75f : 1f;
                float blue = !row.valid() ? .4f : row.enabled() ? .25f : 1f;
                String rowText = enabled + row.gameLabel() + row.label() + badges;
                draw(rowText, 12, y + 1, 1f, green, blue);
                y += rowHeight();
            }
            draw(UiMessages.text("menu.mods.list_position", java.util.Map.of("position", rows.isEmpty() ? 0 : selectedIndex + 1, "count", rows.size())), 9, 141, .7f, .8f, .95f);
            List<String> banners = bannerLines();
            String banner = reloadFailure != null ? UiMessages.text("menu.mods.reload_failed", java.util.Map.of("error", reloadFailure))
                    : saveFailure != null ? UiMessages.text("menu.mods.save_failed", java.util.Map.of("error", saveFailure))
                    : editor.dirty() ? UiMessages.text("menu.mods.ui.draft_choose_apply_to_save")
                    : banners.isEmpty() ? UiMessages.text("menu.mods.ui.saved_choose_apply_to_reload") : banners.getFirst();
            draw(banner, 9, 153, 1f, .65f, .3f);
            if (!statusMessage.isBlank()) draw(statusMessage, 9, 165, 1f, .75f, .25f);
            String[] actions = editor.editable() ? new String[]{UiMessages.text("menu.mods.ui.details"), UiMessages.text("menu.mods.ui.order"), UiMessages.text("menu.mods.ui.notices"), UiMessages.text("menu.mods.ui.apply")}
                    : new String[]{UiMessages.text("menu.mods.ui.details"), UiMessages.text("menu.mods.ui.notices"), UiMessages.text("menu.mods.ui.back")};
            int[] actionX = editor.editable() ? EDITABLE_ACTION_X : FIXED_ACTION_X;
            int[] actionWidth = editor.editable() ? EDITABLE_ACTION_W : FIXED_ACTION_W;
            for (int i = 0; i < actions.length; i++) {
                int x = actionX[i];
                text.panel(x, ACTION_Y, actionWidth[i], ACTION_HEIGHT);
                if (actionFocus && selectedAction == i) text.focus(x, ACTION_Y, actionWidth[i], ACTION_HEIGHT);
                drawPrimary(actions[i], x + 4, text.primaryTextY(ACTION_Y, ACTION_HEIGHT), 1f, 1f, 1f);
            }
            text.footer(actionFocus ? UiMessages.text("menu.mods.action_controls", java.util.Map.of("confirm", confirmLabel, "back", backLabel, "empty", rows.isEmpty() ? "yes" : "no"))
                            : UiMessages.text("menu.mods.list_controls", java.util.Map.of("confirm", confirmLabel, "editable", editor.editable() ? "yes" : "no")),
                    actionFocus ? UiMessages.text("menu.mods.ui.left_right_choose_up_down_list") : UiMessages.text("menu.mods.select_controls", java.util.Map.of("back", backLabel)));
        } finally {
            text.end();
        }
    }

    private static int rowHeight() { return Math.max(ROW_HEIGHT, com.openggf.game.MenuChrome.compactLineHeight() + 3); }
    private static int lineHeight() { return Math.max(LINE_HEIGHT, com.openggf.game.MenuChrome.compactLineHeight() + 2); }
    private static int visibleRowCapacity() { return Math.min(MAX_VISIBLE_ROWS, Math.max(1, 88 / rowHeight())); }
    private static int visibleDetailCapacity() { return Math.min(MAX_VISIBLE_DETAILS, Math.max(1, 138 / lineHeight())); }

    private void renderReadingPage(String title, List<String> lines, int requestedOffset,
                                   String firstHint, String secondHint) {
        int offset = Math.clamp(requestedOffset, 0, Math.max(0, lines.size() - visibleDetailCapacity()));
        text.page(title, UiMessages.text("menu.mods.line_position", java.util.Map.of("first", lines.isEmpty() ? 0 : offset + 1, "last", Math.min(lines.size(), offset + visibleDetailCapacity()), "count", lines.size())));
        text.panel(6, 47, 308, 143);
        for (int i = 0; i < visibleDetailCapacity() && offset + i < lines.size(); i++)
            draw(lines.get(offset + i), 9, 52 + i * lineHeight(), 1f, 1f, 1f);
        text.footer(firstHint, secondHint);
    }

    private List<String> noticeLines() {
        List<String> lines = new ArrayList<>();
        if (!statusMessage.isBlank()) lines.add(statusMessage);
        lines.addAll(bannerLines(true));
        if (lines.isEmpty()) lines.add(editor.editable() ? UiMessages.text("menu.mods.ui.no_notices_apply_reloads_mods_at_title")
                : UiMessages.text("menu.mods.ui.no_notices_mods_are_fixed_for_this_launch"));
        return wrapped(lines);
    }

    private List<String> confirmationLines() {
        return wrapped(List.of(statusMessage));
    }

    private static List<String> wrapped(List<String> source) {
        List<String> result = new ArrayList<>();
        for (String line : source) result.addAll(com.openggf.graphics.text.HostText.shared().wrap(
                normalizeText(line), 302, com.openggf.graphics.text.TextFace.COMPACT,
                com.openggf.graphics.text.HostTextContext.current()));
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
        if (rows.isEmpty()) return List.of(UiMessages.text("menu.mods.ui.no_mods_discovered"));
        Row selected = rows.get(selectedIndex);
        if (selected.invalid() != null) return invalidDetailLines(selected.invalid(),
                isBundled(selected.invalid()));
        return descriptorDetailLines(selected.descriptor());
    }

    public List<String> visibleDetailLines() {
        List<String> details = wrapped(detailLines());
        int maximum = Math.max(0, details.size() - visibleDetailCapacity());
        detailScrollOffset = Math.max(0, Math.min(detailScrollOffset, maximum));
        int end = Math.min(details.size(), detailScrollOffset + visibleDetailCapacity());
        return List.copyOf(details.subList(detailScrollOffset, end));
    }

    public List<String> bannerLines() {
        return bannerLines(false);
    }

    private List<String> bannerLines(boolean includeCatalogCompatibility) {
        List<String> banners = new ArrayList<>();
        if (!editor.editable()) banners.add(UiMessages.text("menu.mods.ui.mods_are_fixed_for_this_launch"));
        for (RepositoryScanFailure failure : repositoryFailures) {
            for (ModFinding finding : failure.findings()) {
                banners.add(UiMessages.text("menu.mods.repository_error", java.util.Map.of("error", finding.message())));
            }
        }
        Set<String> catalogOwners = descriptorsById.keySet();
        runtimeFindings.snapshot().forEach((owner, findings) -> {
            if (catalogOwners.contains(owner)) return;
            for (ModFinding finding : findings) {
                banners.add(UiMessages.text("menu.mods.runtime_finding", java.util.Map.of("severity", finding.severity().name(), "owner", owner, "code", finding.code(), "message", finding.message())));
            }
        });
        List<ModCompatibilityDiagnostic> compatibility = runtimeFindings.compatibilitySnapshot().stream()
                .filter(diagnostic -> includeCatalogCompatibility || diagnostic.participants().stream()
                        .anyMatch(owner -> !catalogOwners.contains(owner)))
                .toList();
        appendCompatibility(banners, compatibility);
        if (editor.restartRequired()) banners.add(UiMessages.text("menu.mods.ui.changes_await_apply"));
        if (saveFailure != null) banners.add(UiMessages.text("menu.mods.save_failed", java.util.Map.of("error", saveFailure)));
        if (reloadFailure != null) banners.add(UiMessages.text("menu.mods.reload_failed", java.util.Map.of("error", reloadFailure)));
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
        return UiMessages.text("menu.mods.pattern_budget", java.util.Map.of("used", patternWindows.totalWindows(), "limit", ModPatternWindowAllocator.MAX_WINDOWS));
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
                    statusMessage = cascade.size() == 1 ? UiMessages.text("menu.mods.trusted_enabled", java.util.Map.of("id", id))
                            : UiMessages.text("menu.mods.ui.trusted_and_enabled_dependency_cascade");
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
                statusMessage = armedCascade.enable() ? UiMessages.text("menu.mods.ui.enabled_dependency_cascade")
                        : UiMessages.text("menu.mods.ui.disabled_dependent_cascade");
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
        int maximum = Math.max(0, wrapped(detailLines()).size() - visibleDetailCapacity());
        int next = Math.max(0, Math.min(maximum, detailScrollOffset + delta));
        if (next == detailScrollOffset) return;
        detailScrollOffset = next;
        statusMessage = UiMessages.text("menu.mods.ui.detail_scroll");
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
                    isBundled(row.invalid()) ? List.of(UiMessages.text("menu.mods.ui.bundled_caps"), UiMessages.text("menu.mods.ui.error_caps")) : List.of(UiMessages.text("menu.mods.ui.error_caps")));
        }
        ModDescriptor descriptor = row.descriptor();
        String id = descriptor.manifest().id();
        LinkedHashSet<String> badges = new LinkedHashSet<>();
        if (isBundled(descriptor)) badges.add(UiMessages.text("menu.mods.ui.bundled_caps"));
        ModEligibility eligibility = catalog.eligibility().get(id);
        if (eligibility != null && eligibility.status() == ModEligibility.Status.BLOCKED) badges.add(UiMessages.text("menu.mods.ui.blocked_caps"));
        if (descriptor.containsCode() && !isTrusted(descriptor)) badges.add(UiMessages.text("menu.mods.ui.trust_required_caps"));
        boolean notLoaded = false;
        addFindingBadges(badges, descriptor.findings(), false);
        addFindingBadges(badges, runtimeFindings.findingsFor(id), true);
        for (ModCompatibilityDiagnostic diagnostic : runtimeFindings.compatibilityFor(id)) {
            badges.add(switch (diagnostic.level()) {
                case OBSERVED_OVERLAP -> UiMessages.text("menu.mods.ui.overlap_caps");
                case SUSPECTED_INCOMPATIBILITY -> UiMessages.text("menu.mods.ui.possible_conflict_caps");
                case VERIFIED_BLOCKER -> diagnostic.blocksPlayFor(id) ? UiMessages.text("menu.mods.ui.play_blocked_caps") : UiMessages.text("menu.mods.ui.related_blocker_caps");
            });
        }
        String label = descriptorCounts.getOrDefault(id, 0) > 1
                ? descriptor.manifest().name() + " (" + filename(descriptor.jarPath()) + ")"
                : descriptor.manifest().name();
        return new RowView(row.identity(), label, "[" + gameLabel(descriptor.manifest()) + "] ",
                isEnabled(id), true, notLoaded,
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
        if (error) badges.add(runtime ? UiMessages.text("menu.mods.ui.runtime_error_caps") : UiMessages.text("menu.mods.ui.error_caps"));
        if (warning) badges.add(runtime ? UiMessages.text("menu.mods.ui.runtime_warn_caps") : UiMessages.text("menu.mods.ui.warn_caps"));
    }

    private List<String> descriptorDetailLines(ModDescriptor descriptor) {
        List<String> lines = new ArrayList<>();
        var manifest = descriptor.manifest();
        lines.add(manifest.name() + "  " + manifest.version());
        lines.add(UiMessages.text("menu.mods.jar", java.util.Map.of("file", filename(descriptor.jarPath()))));
        if (isBundled(descriptor)) {
            lines.add(UiMessages.text("menu.mods.ui.bundled_with_openggf_enabled_by_default_and_trusted_by_this_build_s_manifest_you_can_"));
        }
        lines.add(UiMessages.text("menu.mods.id_game", java.util.Map.of("id", manifest.id(), "game", gameLabel(manifest))));
        int requestedWindows = manifest.patternWindows().orElse(1);
        String allocation = patternWindows.assignment(manifest.id())
                .map(value -> UiMessages.text("menu.mods.pattern_allocation", java.util.Map.of("start", Integer.toHexString(value.base()), "end", Integer.toHexString(value.endExclusive()))))
                .orElse(UiMessages.text("menu.mods.ui.not_allocated_in_the_effective_catalog"));
        lines.add(UiMessages.text("menu.mods.pattern_windows", java.util.Map.of("count", requestedWindows, "allocation", allocation)));
        lines.add(UiMessages.text("menu.mods.authors", java.util.Map.of("authors", String.join(", ", manifest.authors()))));
        lines.add(UiMessages.text("menu.mods.description", java.util.Map.of("description", manifest.description())));
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
                lines.add(reason.code() + ": " + reason.message());
            }
        }
        appendFindings(lines, descriptor.findings());
        appendFindings(lines, runtimeFindings.findingsFor(manifest.id()));
        appendCompatibility(lines, runtimeFindings.compatibilityFor(manifest.id()));
        return immutableSafeLines(lines);
    }

    private static List<String> invalidDetailLines(InvalidModEntry invalid, boolean bundled) {
        List<String> lines = new ArrayList<>();
        lines.add(UiMessages.text("menu.mods.invalid_jar", java.util.Map.of("file", filename(invalid.jarPath()))));
        if (bundled) {
            lines.add(UiMessages.text("menu.mods.ui.bundled_with_openggf_but_not_loaded_reinstall_openggf_to_repair_it"));
        }
        appendFindings(lines, invalid.findings());
        return immutableSafeLines(lines);
    }

    private static void appendFindings(List<String> lines, List<ModFinding> findings) {
        for (ModFinding finding : findings) {
            lines.add(finding.severity() + " " + finding.code() + ": " + finding.message());
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
            lines.add(level + " " + diagnostic.code() + ": " + diagnostic.summary());
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
        String normalized = normalizeText(text);
        String visible = com.openggf.game.MenuChrome.fitLabel(normalized, 320 - x - 6);
        this.text.draw(visible, x, y, 1, r, g, b, 1);
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

    public interface TextSink {
        void begin();
        void draw(String text, int x, int y, float scale, float r, float g, float b, float a);
        void end();
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
