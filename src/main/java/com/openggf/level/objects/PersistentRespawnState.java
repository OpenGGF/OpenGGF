package com.openggf.level.objects;

/**
 * A snapshot of the engine's persistent respawn-remember state (its model of
 * the ROM {@code Object_respawn_table}): which layout spawns are remembered as
 * destroyed/collected ({@code remembered}) and which of those stay active as an
 * inert shell rather than despawning ({@code stayActive}, e.g. a broken
 * monitor).
 * <p>
 * Used to carry that state across bonus-stage and S3K special-stage round-trip
 * level reloads. The ROM preserves {@code Object_respawn_table} when the
 * corresponding entry path sets {@code Respawn_table_keep=1}; the reload's
 * clear routines then skip the respawn/ring tables (e.g. the S3K special-stage
 * path at docs/skdisasm/sonic3k.asm:128446-128451 and the table handling gated
 * at :37457-37465). The engine rebuilds a fresh
 * {@link ObjectPlacementController} on reload, so it instead captures this
 * state at stage entry and re-establishes it before return placement.
 *
 * @param rememberedBits {@link java.util.BitSet#toLongArray()} of the remembered set
 * @param stayActiveBits {@link java.util.BitSet#toLongArray()} of the stay-active set
 */
public record PersistentRespawnState(long[] rememberedBits, long[] stayActiveBits) {
}
