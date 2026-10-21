package com.openggf.stageplay;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Shared authoring session for the in-engine and desktop editors. Confined to the
 * editor thread; transport and selection are separate from document undo history.
 */
public final class StageplayController {
    private static final int HISTORY_LIMIT = 128;
    private final Deque<StageplayDocument> undo = new ArrayDeque<>();
    private final Deque<StageplayDocument> redo = new ArrayDeque<>();
    private StageplayDocument document;
    private StageplayDocument savedDocument;
    private int frame;
    private boolean playing;
    private String selectedActorId = "";

    public StageplayController(StageplayDocument document) {
        replace(document);
    }

    public StageplayDocument document() { return document; }

    /** Loads a document as saved and starts a fresh authoring/transport session. */
    public void replace(StageplayDocument value) {
        document = Objects.requireNonNull(value, "document");
        savedDocument = value;
        undo.clear();
        redo.clear();
        frame = 0;
        playing = false;
        selectedActorId = "";
    }

    /** One successful operation is one undo step; rejected/no-op edits preserve history. */
    public boolean edit(UnaryOperator<StageplayDocument> operation) {
        StageplayDocument next = Objects.requireNonNull(operation.apply(document), "edit result");
        if (next.equals(document)) return false;
        push(undo, document);
        redo.clear();
        document = next;
        reconcile();
        return true;
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        push(redo, document);
        document = undo.removeLast();
        reconcile();
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        push(undo, document);
        document = redo.removeLast();
        reconcile();
        return true;
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public void markSaved() { savedDocument = document; }
    public boolean dirty() { return !document.equals(savedDocument); }
    /** Host resource roots must remain valid for every reachable undo/redo revision. */
    public boolean anyRevision(java.util.function.Predicate<StageplayDocument> predicate) {
        Objects.requireNonNull(predicate);
        return predicate.test(document) || undo.stream().anyMatch(predicate) || redo.stream().anyMatch(predicate);
    }
    public int frame() { return frame; }
    public void seek(int value) { frame = Math.clamp(value, 0, document.durationFrames() - 1); }

    /** Playing again at the end restarts from frame zero. */
    public void play() {
        if (frame == document.durationFrames() - 1) frame = 0;
        playing = document.durationFrames() > 1;
    }

    public void pause() { playing = false; }
    public boolean playing() { return playing; }

    /** Advances one presentation frame; never mutates gameplay or commits outcomes. */
    public boolean advance() {
        if (!playing) return false;
        if (frame >= document.durationFrames() - 1) {
            playing = false;
            return false;
        }
        frame++;
        if (frame == document.durationFrames() - 1) playing = false;
        return true;
    }

    public String selectedActorId() { return selectedActorId; }

    /** Empty string clears selection. */
    public void selectActor(String id) {
        Objects.requireNonNull(id, "id");
        if (!id.isEmpty() && document.actors().stream().noneMatch(actor -> actor.id().equals(id))) {
            throw new IllegalArgumentException("Unknown actor: " + id);
        }
        selectedActorId = id;
    }

    private void reconcile() {
        playing = false;
        seek(frame);
        if (document.actors().stream().noneMatch(actor -> actor.id().equals(selectedActorId))) selectedActorId = "";
    }

    private static void push(Deque<StageplayDocument> history, StageplayDocument value) {
        if (history.size() == HISTORY_LIMIT) history.removeFirst();
        history.addLast(value);
    }
}
