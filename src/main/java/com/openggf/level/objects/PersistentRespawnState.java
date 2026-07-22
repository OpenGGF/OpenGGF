package com.openggf.level.objects;

/**
 * A snapshot of the engine's persistent respawn-remember state (its model of
 * the ROM {@code Object_respawn_table}): which layout spawns are remembered as
 * destroyed/collected ({@code remembered}) and which of those stay active as an
 * inert shell rather than despawning ({@code stayActive}, e.g. a broken
 * monitor).
 * <p>
 * Used to carry that state across a bonus-stage round-trip level reload. The ROM
 * preserves {@code Object_respawn_table} across the star-post-bonus
 * {@code Restart_level_flag} reload: the star-post bonus entry sets
 * {@code Respawn_table_keep=1} (docs/skdisasm/sonic3k.asm:61930) and the reload's
 * clear routines skip the respawn/ring tables while it is set (e.g.
 * {@code sub_EB1A} :18564-18570, the {@code Object_respawn_table} handling gated
 * at :37432-37434), so the live table simply survives. The engine rebuilds a
 * fresh {@link ObjectPlacementController} on reload, so it instead captures this
 * state at bonus entry and re-establishes it on return.
 *
 * @param rememberedBits {@link java.util.BitSet#toLongArray()} of the remembered set
 * @param stayActiveBits {@link java.util.BitSet#toLongArray()} of the stay-active set
 */
@com.openggf.game.ModApi
public record PersistentRespawnState(long[] rememberedBits, long[] stayActiveBits) {
    public PersistentRespawnState {
        rememberedBits = rememberedBits == null ? null : rememberedBits.clone();
        stayActiveBits = stayActiveBits == null ? null : stayActiveBits.clone();
    }

    @Override
    public long[] rememberedBits() {
        return rememberedBits == null ? null : rememberedBits.clone();
    }

    @Override
    public long[] stayActiveBits() {
        return stayActiveBits == null ? null : stayActiveBits.clone();
    }
}
