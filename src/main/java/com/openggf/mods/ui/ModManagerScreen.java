package com.openggf.mods.ui;

import com.openggf.mods.InvalidModEntry;
import com.openggf.mods.ModCatalog;
import com.openggf.mods.ModCatalogEntry;
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
 * Pending-state editor for the immutable process catalog. The screen deliberately
 * owns no activation seam: all changes are persisted for the next process start.
 */
public final class ModManagerScreen {
    public static final int MAX_VISIBLE_ROWS = 18;
    public static final int MAX_VISIBLE_DETAILS = 10;
    private static final float SCALE = 0.5f;
    private static final int LINE_HEIGHT = 6;
    private static final int MAX_RENDER_CHARS = 68;

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

    private List<Row> rows = List.of();
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
    private boolean closeRequested;

    public ModManagerScreen(ModCatalog catalog, PendingModStateEditor editor,
                            ModRuntimeFindingStore runtimeFindings, TextSink text) {
        this(catalog, editor, runtimeFindings, text, PatternWindowState.EMPTY);
    }

    public ModManagerScreen(ModCatalog catalog, PendingModStateEditor editor,
                            ModRuntimeFindingStore runtimeFindings, TextSink text,
                            PatternWindowState patternWindows) {
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
    }

    /** Engine-neutral state transition used by live composition and focused tests. */
    public void update(MenuInput input) {
        Objects.requireNonNull(input, "input");
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
        boolean back = input.menuBack() && !previousInput.menuBack();
        previousInput = input;

        if (escapeEdge || back) {
            handleBack();
            return;
        }
        if (input.startHeld() && (up || down)) {
            cancelArmedConfirmation();
            scrollDetails(down ? 1 : -1);
            return;
        }
        if (up || down || left || right) {
            cancelArmedConfirmation();
            if (up) moveSelection(-1);
            else if (down) moveSelection(1);
            else if (left) reorderSelected(-1);
            else reorderSelected(1);
            return;
        }
        if (accept) toggleSelected();
    }

    public void render() {
        if (text == null) return;
        text.begin();
        try {
            int y = 4;
            draw("MOD MANAGER", 6, y, 1f, 1f, 1f);
            y += LINE_HEIGHT;
            draw(patternBudgetLine(), 6, y, 0.72f, 0.72f, 0.72f);
            y += LINE_HEIGHT;
            for (String banner : bannerLines()) {
                draw(banner, 6, y, 1f, 0.55f, 0.35f);
                y += LINE_HEIGHT;
            }
            y = Math.max(y + 2, 20);
            for (RowView row : visibleRows()) {
                boolean selected = rows.get(selectedIndex).identity().equals(row.identity());
                String prefix = selected ? "> " : "  ";
                String enabled = row.valid() ? (row.enabled() ? "[ON] " : "[OFF]") : "[--] ";
                String badges = row.badges().isEmpty() ? "" : " [" + String.join(",", row.badges()) + "]";
                float brightness = selected ? 1f : 0.62f;
                draw(prefix + enabled + " " + row.label() + badges, 8, y,
                        brightness, brightness, brightness);
                y += LINE_HEIGHT;
            }
            y = Math.max(y + 3, 134);
            for (String line : visibleDetailLines()) {
                draw(line, 4, y, 0.88f, 0.88f, 0.88f);
                y += LINE_HEIGHT;
            }
            if (!statusMessage.isBlank()) {
                draw(statusMessage, 4, y, 1f, 0.72f, 0.25f);
                y += LINE_HEIGHT;
            }
            draw("A/ENTER Toggle  L/R Order  Start+Up/Down Details  Esc/B Back", 4, y,
                    0.62f, 0.62f, 0.62f);
        } finally {
            text.end();
        }
    }

    public List<RowView> rows() {
        List<RowView> views = new ArrayList<>(rows.size());
        for (Row row : rows) views.add(toView(row));
        return List.copyOf(views);
    }

    public List<RowView> visibleRows() {
        int end = Math.min(rows.size(), scrollOffset + MAX_VISIBLE_ROWS);
        List<RowView> views = new ArrayList<>(Math.max(0, end - scrollOffset));
        for (int index = scrollOffset; index < end; index++) views.add(toView(rows.get(index)));
        return List.copyOf(views);
    }

    public List<String> detailLines() {
        if (rows.isEmpty()) return List.of("No mods discovered.");
        Row selected = rows.get(selectedIndex);
        if (selected.invalid() != null) return invalidDetailLines(selected.invalid());
        return descriptorDetailLines(selected.descriptor());
    }

    public List<String> visibleDetailLines() {
        List<String> details = detailLines();
        int maximum = Math.max(0, details.size() - MAX_VISIBLE_DETAILS);
        detailScrollOffset = Math.max(0, Math.min(detailScrollOffset, maximum));
        int end = Math.min(details.size(), detailScrollOffset + MAX_VISIBLE_DETAILS);
        return List.copyOf(details.subList(detailScrollOffset, end));
    }

    public List<String> bannerLines() {
        List<String> banners = new ArrayList<>();
        for (RepositoryScanFailure failure : repositoryFailures) {
            for (ModFinding finding : failure.findings()) {
                banners.add("Repository error: " + finding.message());
            }
        }
        java.util.Set<String> catalogOwners = rows.stream()
                .filter(row -> row.descriptor() != null)
                .map(row -> row.descriptor().manifest().id())
                .collect(java.util.stream.Collectors.toSet());
        runtimeFindings.snapshot().forEach((owner, findings) -> {
            if (catalogOwners.contains(owner)) return;
            for (ModFinding finding : findings) {
                banners.add("Runtime " + finding.severity().name().toLowerCase(java.util.Locale.ROOT)
                        + " [" + owner + "] " + finding.code() + ": " + finding.message());
            }
        });
        if (editor.restartRequired()) banners.add("Restart required");
        if (saveFailure != null) banners.add("Save failed: " + saveFailure);
        return List.copyOf(banners);
    }

    public int selectedIndex() { return selectedIndex; }

    public int scrollOffset() { return scrollOffset; }

    public int detailScrollOffset() { return detailScrollOffset; }

    public boolean cascadeArmed() { return armedCascade != null; }

    public boolean trustArmed() { return armedTrust != null; }

    public boolean restartRequired() { return editor.restartRequired(); }

    public ModState pendingState() { return editor.pendingState(); }

    public String statusMessage() { return statusMessage; }

    public String patternBudgetLine() {
        return "Current pattern windows: " + patternWindows.totalWindows()
                + "/" + ModPatternWindowAllocator.MAX_WINDOWS;
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

    /** Selects a visible catalog row without changing pending state. */
    public void select(int index) {
        if (index < 0 || index >= rows.size()) {
            throw new IllegalArgumentException("Selection outside catalog rows: " + index);
        }
        selectedIndex = index;
        keepSelectionVisible();
        detailScrollOffset = 0;
        armedCascade = null;
        armedTrust = null;
    }

    private void toggleSelected() {
        if (rows.isEmpty()) return;
        Row row = rows.get(selectedIndex);
        if (row.descriptor() == null) {
            statusMessage = "Invalid jars cannot be enabled";
            return;
        }
        String id = row.descriptor().manifest().id();
        boolean enabled = isEnabled(id);
        LinkedHashSet<String> cascade = enabled
                ? enabledDependentClosure(id) : disabledDependencyClosure(id);
        cascade.add(id);
        if (!enabled) {
            String refusal = enableRefusal(cascade, id);
            if (refusal != null) {
                statusMessage = id + " cannot be enabled: " + refusal;
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
                    statusMessage = cascade.size() == 1 ? "Trusted and enabled " + id
                            : "Trusted and enabled dependency cascade";
                    saveFailure = null;
                    rebuildRows(id);
                    return;
                } else {
                    armedCascade = null;
                    armedTrust = new ArmedTrust(id, row.descriptor().sha256(), Set.copyOf(cascade));
                    statusMessage = "This mod contains code and runs with full permissions. "
                            + "Press accept again to trust and enable: "
                            + String.join(", ", cascade);
                    return;
                }
            }
        }
        if (cascade.size() > 1) {
            if (armedCascade != null && armedCascade.id().equals(id)
                    && armedCascade.enable() == !enabled) {
                editor.setEnabledCascade(armedCascade.ids(), armedCascade.enable());
                statusMessage = armedCascade.enable() ? "Enabled dependency cascade"
                        : "Disabled dependent cascade";
                armedCascade = null;
                armedTrust = null;
                saveFailure = null;
                rebuildRows(id);
            } else {
                armedCascade = new ArmedCascade(id, Set.copyOf(cascade), !enabled);
                armedTrust = null;
                statusMessage = "Press accept again to " + (enabled ? "disable " : "enable ")
                        + String.join(", ", cascade);
            }
            return;
        }
        editor.setEnabled(id, !enabled);
        armedTrust = null;
        statusMessage = !enabled ? "Enabled " + id : "Disabled " + id;
        saveFailure = null;
        rebuildRows(id);
    }

    private String enableRefusal(Collection<String> ids, String trustPromptId) {
        Map<String, ModEligibility> pendingEligibility = new EffectiveCatalogBuilder()
                .build(catalog.scanned(), proposedEnabledState(ids, trustPromptId)).eligibility();
        for (String id : ids) {
            ModDescriptor descriptor = descriptorsById.get(id);
            if (descriptor == null) return "missing dependency " + id;
            ModFinding error = descriptor.findings().stream()
                    .filter(finding -> finding.severity() == ModFindingSeverity.ERROR)
                    .findFirst().orElse(null);
            if (error != null) return error.message();
            ModEligibility frozen = catalog.eligibility().get(id);
            if (frozen != null && frozen.status() == ModEligibility.Status.BLOCKED) {
                String fixedReason = frozen.reasons().stream()
                        .filter(reason -> !Set.of("CODE_TRUST_REQUIRED", "DEPENDENCY_BLOCKED",
                                "DEPENDENCY_DISABLED").contains(reason.code()))
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
        statusMessage = "Cascade cancelled";
    }

    private void cancelArmedConfirmation() {
        if (armedTrust != null) {
            armedTrust = null;
            statusMessage = "Trust confirmation cancelled";
        } else if (armedCascade != null) {
            cancelCascade();
        }
    }

    private void reorderSelected(int delta) {
        if (rows.isEmpty()) return;
        Row row = rows.get(selectedIndex);
        if (row.descriptor() == null) {
            statusMessage = "Invalid jars cannot be reordered";
            return;
        }
        String id = row.descriptor().manifest().id();
        if (descriptorCounts.getOrDefault(id, 0) != 1) {
            statusMessage = "Duplicate-id jars cannot be reordered";
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
            statusMessage = "Move refused: dependency order must be preserved";
            return;
        }
        editor.move(id, target);
        saveFailure = null;
        statusMessage = "Moved " + id;
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
        int maximum = Math.max(0, detailLines().size() - MAX_VISIBLE_DETAILS);
        detailScrollOffset = Math.max(0, Math.min(maximum, detailScrollOffset + delta));
        statusMessage = maximum == 0 ? "All details are visible" : "Detail scroll";
    }

    private void handleBack() {
        if (armedTrust != null || armedCascade != null) cancelArmedConfirmation();
        else saveAndRequestClose();
    }

    private void saveAndRequestClose() {
        ModStateSaveResult result = editor.save();
        if (result instanceof ModStateSaveResult.Saved) {
            saveFailure = null;
            statusMessage = "Saved";
            closeRequested = true;
        } else if (result instanceof ModStateSaveResult.Failed failed) {
            saveFailure = failed.message();
            statusMessage = "Pending changes were not saved";
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
            String id = descriptor.manifest().id();
            String identity = descriptorCounts.getOrDefault(id, 0) > 1
                    ? id + " (" + filename(descriptor.jarPath()) + ")" : id;
            rebuilt.add(new Row(descriptor, null, identity));
        }
        for (InvalidModEntry invalid : invalidEntries) {
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
        if (selectedIndex >= scrollOffset + MAX_VISIBLE_ROWS) {
            scrollOffset = selectedIndex - MAX_VISIBLE_ROWS + 1;
        }
        int maximum = Math.max(0, rows.size() - MAX_VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maximum));
    }

    private RowView toView(Row row) {
        if (row.invalid() != null) {
            return new RowView(row.identity(), row.identity(), false, false, List.of("ERROR"));
        }
        ModDescriptor descriptor = row.descriptor();
        String id = descriptor.manifest().id();
        LinkedHashSet<String> badges = new LinkedHashSet<>();
        ModEligibility eligibility = catalog.eligibility().get(id);
        if (eligibility != null && eligibility.status() == ModEligibility.Status.BLOCKED) badges.add("BLOCKED");
        if (descriptor.containsCode() && !isTrusted(descriptor)) badges.add("TRUST REQUIRED");
        addFindingBadges(badges, descriptor.findings(), false);
        addFindingBadges(badges, runtimeFindings.findingsFor(id), true);
        String label = descriptorCounts.getOrDefault(id, 0) > 1
                ? descriptor.manifest().name() + " (" + filename(descriptor.jarPath()) + ")"
                : descriptor.manifest().name();
        return new RowView(row.identity(), label, isEnabled(id), true,
                List.copyOf(badges));
    }

    private static void addFindingBadges(Set<String> badges, List<ModFinding> findings, boolean runtime) {
        boolean error = findings.stream().anyMatch(f -> f.severity() == ModFindingSeverity.ERROR);
        boolean warning = findings.stream().anyMatch(f -> f.severity() == ModFindingSeverity.WARNING);
        if (error) badges.add(runtime ? "RUNTIME ERROR" : "ERROR");
        if (warning) badges.add(runtime ? "RUNTIME WARN" : "WARN");
    }

    private List<String> descriptorDetailLines(ModDescriptor descriptor) {
        List<String> lines = new ArrayList<>();
        var manifest = descriptor.manifest();
        lines.add(manifest.name() + "  " + manifest.version());
        lines.add("Jar: " + filename(descriptor.jarPath()));
        lines.add("Id: " + manifest.id() + "  Game: " + manifest.baseGame());
        int requestedWindows = manifest.patternWindows().orElse(1);
        String allocation = patternWindows.assignment(manifest.id())
                .map(value -> " at 0x" + Integer.toHexString(value.base())
                        + "-0x" + Integer.toHexString(value.endExclusive()))
                .orElse(" (not allocated in the effective catalog)");
        lines.add("Pattern windows: " + requestedWindows + allocation);
        lines.add("Authors: " + String.join(", ", manifest.authors()));
        lines.add("Description: " + manifest.description());
        if (manifest.dependencies().isEmpty()) {
            lines.add("Dependencies: none");
        } else {
            for (ModDependency dependency : manifest.dependencies()) {
                lines.add("Dependency: " + dependency.id() + " " + dependency.versionRange());
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
        return immutableSafeLines(lines);
    }

    private static List<String> invalidDetailLines(InvalidModEntry invalid) {
        List<String> lines = new ArrayList<>();
        lines.add("Invalid jar: " + filename(invalid.jarPath()));
        appendFindings(lines, invalid.findings());
        return immutableSafeLines(lines);
    }

    private static void appendFindings(List<String> lines, List<ModFinding> findings) {
        for (ModFinding finding : findings) {
            lines.add(finding.severity() + " " + finding.code() + ": " + finding.message());
        }
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

    private void draw(String text, int x, int y, float r, float g, float b) {
        this.text.draw(safeText(text), x, y, SCALE, r, g, b, 1f);
    }

    private static List<String> immutableSafeLines(List<String> lines) {
        return lines.stream().map(ModManagerScreen::safeText).toList();
    }

    private static String safeText(String text) {
        String normalized = Objects.requireNonNull(text, "text").replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_RENDER_CHARS ? normalized
                : normalized.substring(0, MAX_RENDER_CHARS - 3) + "...";
    }

    private static String filename(Path path) {
        Path filename = path.getFileName();
        return filename == null ? path.toString() : filename.toString();
    }

    public record RowView(String identity, String label, boolean enabled, boolean valid,
                          List<String> badges) {
        public RowView {
            badges = List.copyOf(badges);
        }
    }

    private record Row(ModDescriptor descriptor, InvalidModEntry invalid, String identity) { }

    private record ArmedCascade(String id, Set<String> ids, boolean enable) { }

    private record ArmedTrust(String id, String sha256, Set<String> enableIds) { }

    private static boolean isMenuNeutral(MenuInput input) {
        return !input.menuUp() && !input.menuDown() && !input.menuLeft() && !input.menuRight()
                && !input.menuAccept() && !input.menuBack() && !input.startHeld() && !input.escape();
    }

    public interface TextSink {
        void begin();
        void draw(String text, int x, int y, float scale, float r, float g, float b, float a);
        void end();
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
    }

    private void normalizeDependencyOrder() {
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
