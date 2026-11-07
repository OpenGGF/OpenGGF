package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.player.CdPlayerMath;

/** Finite joins to existing live owners. Values returned here confer no World authority. */
public interface CdCczEnemyPorts {
    enum Global { GOOD_FUTURE, TIME_ZONE, WARP_DIRECTION, CAMERA_X, INVINCIBLE }
    enum Player { X, Y, FLAGS, X_VELOCITY }
    enum Sound { SICKLE, EXPLOSION }
    enum Mapping { KAMA_NORMAL, KAMA_DAMAGED, SICKLE, GA_NORMAL, GA_DAMAGED, RING,
        TENTOU_NORMAL, TENTOU_DAMAGED, BOMB, CHAIN_PARENT, CHAIN_LINK }
    enum Animation { KAMA, SICKLE, GA, DUST, TENTOU, BOMB }
    /** Borrowed authenticated bytes, never a new source/disc owner or a decoded fallback. */
    record MappingView(CdObjectMappings.DeclaredTable table) {
        public MappingView { java.util.Objects.requireNonNull(table); }
        public int address() { return table.tableAddressBits(); }
        public CdAssetBytes bytes() { return table.bytes(); }
    }
    interface Assets {
        MappingView mapping(Mapping which);
        CdObjectAnimation.AnimationTable animation(Animation which);
        /** Original compile-time STAGE_TIME+2: Past=2, Present=3, Future=4. */
        int chainLinkCount();
    }
    int global(Global field);
    int player(Player field);
    /** Calls the existing authenticated source math kernel; do not synthesize a sine table. */
    CdPlayerMath.SineCosine sine(int angleByte);
    /** Existing sound producer publishes exactly at this synchronous reached call. */
    void sound(SoundCall call);
    /** Original angle owner compares/stores at this synchronous operation; no broad mutation port. */
    void primaryAngle(AngleCall call);
    /** Actual DrawObject owns mutations; caller reloads the physical slot after return. */
    void draw(CdObjectPool pool, CdObjectState.Identity identity);

    final class SoundCall {
        private final CdCczEnemySupport owner;
        private final Sound sound;
        private final CdObjectPlacementRuntime.Snapshot callCut;
        private final CdObjectState expectedSource;
        SoundCall(CdCczEnemySupport owner, Sound sound) {
            this.owner = owner; this.sound = sound; callCut = owner.placement.capture(); expectedSource = owner.s;
        }
        public Sound sound() { return sound; }
        public CdObjectPlacementRuntime.Snapshot callCut() { return callCut; }
        public CdObjectState expectedSource() { return expectedSource; }
        public boolean isCurrent() { return owner.currentSound == this && owner.running; }
    }
    final class AngleCall {
        private final CdCczEnemySupport owner;
        private final int value;
        private final CdObjectPlacementRuntime.Snapshot callCut;
        private final CdObjectState expectedSource;
        AngleCall(CdCczEnemySupport owner, int value) {
            this.owner = owner; this.value = value; callCut = owner.placement.capture(); expectedSource = owner.s;
        }
        public CdObjectPlacementRuntime.Snapshot callCut() { return callCut; }
        public CdObjectState expectedSource() { return expectedSource; }
        public int value() { return value; }
        /** Component-local synchronous receipt only. Root must also check its actual World cut. */
        public boolean isCurrent() { return owner.currentAngle == this && owner.running; }
    }
}
