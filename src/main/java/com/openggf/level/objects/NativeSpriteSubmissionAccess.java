package com.openggf.level.objects;

import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.PlayableEntity;
import com.openggf.level.render.SpriteSlotEmission;

import java.util.List;
import java.util.function.IntFunction;

/** Trusted engine-internal DisplaySprite port; no ObjectServices/Mod API expansion. */
public final class NativeSpriteSubmissionAccess {
    public static final String REWIND_KEY = "object-native-sprite-submissions";

    /** A RAM-slot reference and the bucket selected at that particular call. */
    public record Admission(int slot, int bucket) { }

    /** Trusted engine-owned routine for reserved SSTs without an ObjectInstance. */
    public interface ExternalSlotDispatch {
        void beginPass();
        void dispatch(int slot, PlayableEntity player, int vIntRunCount, int cameraX);
    }

    private NativeSpriteSubmissionAccess() { }

    static boolean authoritative(ObjectInstance source) {
        return source instanceof NativeSpriteSubmissionSource nativeSource
                && nativeSource.nativeSpriteSubmissionsAuthoritative();
    }

    public static boolean submit(ObjectManager manager, ObjectInstance source, int priorityBucket) {
        if (ObjectConstructionContext.isProbeConstruction()
                || manager == null || !(source instanceof AbstractObjectInstance object)) return false;
        if (!(source instanceof NativeSpriteSubmissionSource)) {
            throw new IllegalArgumentException("Native DisplaySprite source must explicitly opt in");
        }
        int slot = object.getSlotIndex();
        return submit(manager, slot, priorityBucket);
    }

    /** Trusted raw-SST producer, including reserved slots without a Java object. */
    public static boolean submit(ObjectManager manager, int slot, int priorityBucket) {
        return manager != null && manager.nativeSpriteSubmissions().submit(slot, priorityBucket);
    }

    /**
     * S3K Draw_Sprite variant: a full selected list spills into the next list,
     * stopping after bucket 7. The ledger records the bucket actually admitted.
     */
    public static boolean submitSpilling(ObjectManager manager, int slot, int priorityBucket) {
        return manager != null && manager.nativeSpriteSubmissions().submitSpilling(slot, priorityBucket);
    }

    /**
     * Replays prior actor calls after admission resets, before external-slot begin
     * and dynamic dispatch. This separate binding preserves the external-slot owner.
     * Reset/resync unbind it; rewind neither invokes nor restores callback identity.
     * Passing null unbinds it.
     */
    public static void bindBeforePassPrefix(ObjectManager manager, Runnable prefix) {
        if (manager != null) manager.nativeSpriteSubmissions().bindBeforePassPrefix(prefix);
    }

    /**
     * Render-only late lookup of a currently reserved external slot. Real objects
     * take precedence. The callback and returned draw closures never enter rewind
     * state; bind again after full manager reset/resync. Passing null unbinds it.
     */
    public static void bindExternalResolver(ObjectManager manager,
            IntFunction<SpriteSlotEmission> resolver) {
        if (manager != null) manager.bindNativeSpriteExternalResolver(resolver);
    }

    /** Bind the live owner; full reset/resync unbinds it. Callback identity never rewinds. */
    public static void bindExternalSlotDispatch(ObjectManager manager, ExternalSlotDispatch dispatch) {
        if (manager != null) manager.bindNativeSpriteExternalSlotDispatch(dispatch);
    }

    /** Immutable call-order view, including orphan references and duplicates. */
    public static List<Admission> admissions(ObjectManager manager) {
        return manager == null ? List.of() : manager.nativeSpriteSubmissions().admissions();
    }

    /**
     * An actual object pass occurred in the captured frame, even if it admitted
     * no sprites. Consumers use this after rewind to suppress legacy fallback
     * for rejected or unvisited external slots. Submitting alone does not set it.
     */
    public static boolean nativePassPresent(ObjectManager manager) {
        return manager != null && manager.nativeSpriteSubmissions().nativePassPresent();
    }

    /** Register after the object graph so restored slot references resolve against that graph. */
    public static void registerRewind(ObjectManager manager, RewindRegistry registry) {
        if (registry == null) return;
        unregisterRewind(registry);
        if (manager != null) registry.register(manager.nativeSpriteSubmissions().rewindAdapter());
    }

    public static void unregisterRewind(RewindRegistry registry) {
        if (registry != null) registry.deregister(REWIND_KEY);
    }
}
