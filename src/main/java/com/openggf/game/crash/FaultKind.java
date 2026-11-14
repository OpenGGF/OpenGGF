package com.openggf.game.crash;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * What the player was doing when an engine fault ended the session. The crash screen chooses its
 * headline and MICA's gag from this, never from the exception's message. Classification reads the
 * call stack: a rewind seek or a level load that fails anywhere beneath it is named after that work.
 *
 * <p>The bomb count is a nod to the Atari ST, whose row of bombs told the user which exception
 * the processor had taken. Here each kind of failure has its own count.
 */
public enum FaultKind {
    /** A rewind step, seek or snapshot failed. */
    REWIND(2, "crash/mica-rewind.png"),
    /** A level, zone or act failed to load. */
    LEVEL_LOAD(3, "crash/mica-level-load.png"),
    /** Any other engine fault during play or on a menu. */
    ENGINE(4, "crash/mica-engine.png");

    private static final String REWIND_PACKAGE = "com.openggf.game.rewind.";
    private static final String LEVEL_MANAGER = "com.openggf.level.LevelManager";

    private final int bombs;
    private final String artResource;

    FaultKind(int bombs, String artResource) {
        this.bombs = bombs;
        this.artResource = artResource;
    }

    /** Number of bombs drawn under MICA, after the Atari ST's exception bombs. */
    public int bombs() { return bombs; }

    /** Classpath PNG of MICA's gag for this kind of failure. */
    public String artResource() { return artResource; }


    /**
     * Rewind wins over level load: a seek that reloads a level was still a rewind to the player.
     * Every throwable in the cause and suppressed chain is read, so a wrapped failure is named by
     * the work that raised it.
     */
    public static FaultKind classify(Throwable failure) {
        boolean levelLoad = false;
        for (Throwable each : chain(failure)) {
            for (StackTraceElement frame : each.getStackTrace()) {
                String owner = frame.getClassName();
                if (owner.startsWith(REWIND_PACKAGE)) return REWIND;
                if (isLevelLoad(owner, frame.getMethodName())) levelLoad = true;
            }
        }
        return levelLoad ? LEVEL_LOAD : ENGINE;
    }

    private static boolean isLevelLoad(String owner, String method) {
        if (owner.equals(LEVEL_MANAGER)) return method.startsWith("loadLevel") || method.startsWith("loadZoneAndAct");
        int dot = owner.lastIndexOf('.');
        String simple = owner.substring(dot + 1);
        return owner.startsWith("com.openggf.") && simple.contains("LevelLoad");
    }

    /** The failure, then its causes and suppressed failures, each once, in discovery order. */
    static List<Throwable> chain(Throwable failure) {
        List<Throwable> seen = new ArrayList<>();
        Set<Throwable> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        if (failure != null) pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable each = pending.poll();
            if (!identities.add(each)) continue;
            seen.add(each);
            if (each.getCause() != null) pending.add(each.getCause());
            pending.addAll(List.of(each.getSuppressed()));
        }
        return seen;
    }

}
