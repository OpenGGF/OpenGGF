package com.openggf.trace;

/**
 * Read-only projection of the engine's sidekick CPU state captured during trace
 * replay. Values are compared against diagnostic aux events only; replay must
 * never hydrate engine state from the recorded trace.
 */
public record EngineSidekickCpuState(
        int controlCounter,
        int respawnCounter,
        int interact,
        int cpuRoutine,
        int targetX,
        int targetY,
        int generatedHeld,
        int generatedPressed,
        int followHistorySlot,
        int jumpingFlag) {
}
