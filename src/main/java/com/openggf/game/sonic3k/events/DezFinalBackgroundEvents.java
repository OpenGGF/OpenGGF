package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;

/** DEZ3_BackgroundEvent ($5A542): native branch order, retained rows and child publications. */
public final class DezFinalBackgroundEvents {
    private DezFinalBackgroundEvents() { }

    /** The event's ROM layout and object operations, supplied by the zone event owner. */
    public interface Surface {
        int descriptor(int x, int y);
        void writeChunk(int column, int row, int chunk);
        boolean spawnOpeningCollapse();
        boolean spawnChaseCollapse(int x);
        void updateLaser();
    }

    /** Caller has published sub_5A508/sub_5A76C; oldPlaneX is _unkEE8E. */
    public static void update(DezFinalBossZoneRuntimeState state, int cameraX, int oldPlaneX, Surface surface) {
        var plane = state.plane();
        switch (state.backgroundRoutine()) {
            case 0:
                if (state.eventsFg5() == 0) break;
                state.eventsFg5(0);
                surface.writeChunk(0xD, 2, 0x1A);
                // Refresh_PlaneDirect is fifteen rows of twenty-one blocks, not Refresh_PlaneFull.
                for (int row = 0; row < 15; row++)
                    plane.writeRow(state.planeX(), state.planeY() + row * 16, 21, surface::descriptor);
                state.backgroundRoutine(4);
                return; // loc_5A57C goes directly to deformation, skipping DrawBGAsYouMove.
            case 4:
                if (state.screenShake().flag() != 0 && surface.spawnOpeningCollapse()) {
                    state.breakFrontier(0x2C0);
                    state.backgroundRoutine(8);
                }
                break;
            case 8:
                if (state.windowBase() == 0x6C0) break;
                beginRedraw(state, oldPlaneX);
                state.backgroundRoutine(state.backgroundRoutine() + 4);
                // Native fallthrough into loc_5A606.
            case 0xC:
                plane.advanceRedraw(surface::descriptor);
                if (!plane.redrawing()) {
                    state.retainedPlaneX(0);
                    state.backgroundRoutine(state.backgroundRoutine() + 4);
                }
                // Even an incomplete redraw falls through to loc_5A61A.
            case 0x10:
                if (state.windowBase() == 0x2C0) break;
                beginRedraw(state, oldPlaneX);
                int frontier = cameraX & 0xFFE0;
                if (surface.spawnChaseCollapse((frontier - 0x10) & 0xFFFF)) state.breakFrontier(frontier);
                state.backgroundRoutine(state.backgroundRoutine() + 4);
                // Native fallthrough into loc_5A662.
            case 0x14:
                plane.advanceRedraw(surface::descriptor);
                if (!plane.redrawing()) {
                    state.retainedPlaneX(0);
                    state.backgroundRoutine(state.backgroundRoutine() + 4);
                }
                // Even an incomplete redraw falls through to loc_5A676.
            case 0x18:
                if (state.windowBase() != 0) {
                    surface.updateLaser();
                    if (state.eventsFg5() != 0) {
                        state.eventsFg5(0);
                        int phase = state.mouthPhase();
                        int chunks = switch (phase) {
                            case 0, 4 -> 0x603;
                            case 2 -> 0x903;
                            case 6 -> 0x807;
                            default -> throw new IllegalStateException("DEZ3 mouth phase " + phase);
                        };
                        state.mouthPhase(phase == 6 ? 0 : phase + 2);
                        surface.writeChunk(0xE, 2, chunks & 0xFF);
                        surface.writeChunk(0xE, 3, chunks >>> 8);
                        int limit = ((state.planeY() & 0xFFF0) + 0xE0) & 0xFFFF;
                        int y = 0x160;
                        for (int row = 0; row < 10; row++) {
                            plane.writeRow(0x700, y, 8, surface::descriptor);
                            y += 16;
                            if (y > limit) break;
                        }
                    }
                    break;
                }
                beginRedraw(state, oldPlaneX);
                state.backgroundRoutine(state.backgroundRoutine() + 4);
                // Native fallthrough into loc_5A71A.
            case 0x1C:
                plane.advanceRedraw(surface::descriptor);
                if (!plane.redrawing()) {
                    state.retainedPlaneX(0);
                    state.backgroundRoutine(state.backgroundRoutine() + 4);
                }
                break;
            case 0x20:
                break;
            default:
                throw new IllegalStateException("DEZ3 background routine " + state.backgroundRoutine());
        }
        plane.drawAsYouMove(state.planeX(), state.planeY(), surface::descriptor);
    }

    private static void beginRedraw(DezFinalBossZoneRuntimeState state, int oldPlaneX) {
        state.plane().resetDrawPosition(state.planeX(), state.planeY());
        state.plane().beginRedraw();
        state.retainedPlaneX(oldPlaneX);
    }
}
