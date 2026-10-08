package com.openggf.game.timeattack;

/** What the menu launches: track + character + optional imported ghosts to race. */
public record TimeAttackLaunchRequest(String gameId, int zone, int act, String character,
                                      java.util.List<java.nio.file.Path> extraGhosts) {
}
