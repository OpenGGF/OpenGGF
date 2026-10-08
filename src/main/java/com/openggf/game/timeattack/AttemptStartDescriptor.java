package com.openggf.game.timeattack;

/** Canonical start-state descriptor embedded in every attempt recording (security spec §6.2). */
public record AttemptStartDescriptor(String gameId, int zone, int act, String character,
                                     String fingerprint) {
}
