package com.openggf.game.sonic3k.events;

import com.openggf.game.GameServices;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SozEventState;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;

/** SOZ screen and special events. Mutable native state belongs to the zone runtime. */
public final class Sonic3kSOZEvents extends Sonic3kZoneEvents {
    private SozZoneRuntimeState state() {
        return S3kRuntimeStates.currentSoz(zoneRuntimeRegistry()).orElseThrow();
    }

    @Override public void update(int act, int frameCounter) {
        var state = state();
        var events = state.events();
        var player = spriteManager().getMainPlayable();
        if (player == null) return;
        int playerX = player.getCentreX() & 0xFFFF;
        int playerY = player.getCentreY() & 0xFFFF;
        if (!events.initialized()) initializeScreen(act, playerX, events);
        camera().setYCopy((short) (camera().getYCopy() + events.screenShakeOffset()));
        if (act == 0) {
            // loc_55ACE / sub_55E96: establish the boss approach bounds before locking X.
            if (events.backgroundRoutine() == 0) {
                int bottom = playerX < 0x4000 ? 0xB20 : 0x960;
                camera().setMaxY((short) bottom);
                camera().setMaxYTarget((short) bottom);
                if (bottom == 0x960 && (camera().getY() & 0xFFFF) >= bottom) {
                    camera().setMinY((short) bottom);
                    if ((camera().getX() & 0xFFFF) >= 0x4310) {
                        camera().setMinX((short) 0x4180);
                    }
                }
            }
            return;
        }
        if (state.consumeSandCorkForegroundFlag() != 0) openAct2ForegroundPassage();
        updateAct2Sand(state, playerX, playerY);
    }

    private void initializeScreen(int act, int playerX, SozEventState events) {
        events.initialized(true);
        if (act == 0) {
            // SOZ1_BackgroundInit: Level_layout_main+2, row6, column13.
            zoneLayoutMutationPipeline().queue(context -> {
                context.surface().setBlockInMap(1, 13, 6, 0xFD);
                return MutationEffects.redrawAllTilemaps();
            });
        } else {
            // SOZ2_ScreenInit / BackgroundInit. These are load-position gates, not route guesses.
            camera().setVerticalWrapEnabled(true, 0x800);
            events.foregroundRoutine(8);
            events.backgroundRoutine(playerX < 0x2980 ? 0x10 : 0x20);
            zoneLayoutMutationPipeline().queue(context -> {
                // sub_5697E clears ten BG chunk references in rows14 and15.
                for (int row : new int[]{14, 15}) for (int x = 0x17; x < 0x21; x++)
                    context.surface().setBlockInMap(1, x, row, 0);
                return MutationEffects.redrawAllTilemaps();
            });
            if (playerX >= 0x4E80) openAct2ForegroundPassage();
        }
    }

    /** sub_5622C copies stored layout cells, including the four lower foreground rows. */
    private void openAct2ForegroundPassage() {
        zoneLayoutMutationPipeline().queue(context -> {
            var map = levelManager().getCurrentLevel().getMap();
            for (int x = 0; x < 4; x++)
                context.surface().setBlockInMap(0, 0x95 + x, 7, map.getValue(0, 0xAB + x, 7) & 255);
            for (int row = 10; row < 14; row++) for (int x = 0; x < 9; x++)
                context.surface().setBlockInMap(0, 0x8C + x, row, map.getValue(0, 0xAB + x, row) & 255);
            return MutationEffects.redrawAllTilemaps();
        });
    }

    private void updateAct2Sand(SozZoneRuntimeState state, int playerX, int playerY) {
        var events = state.events();
        int routine = events.backgroundRoutine();
        boolean exitSand = playerX >= 0x2A00 && playerY >= 0x140 && playerY <= 0x180;
        if ((routine == 0x10 || routine == 0x14 || routine == 0x18) && exitSand) {
            events.savedBackgroundX((short) (camera().getXCopy() - 0x1930));
            events.savedBackgroundY((short) (camera().getYCopy() + 0x2E0 + events.sandHeight()));
            events.backgroundRoutine(0x1C);
            events.redrawRemaining(0x1F);
            events.specialRoutine(0);
            events.backgroundCollision(false);
            gameState().setBackgroundCollisionFlag(false);
            state.consumeSandCorkBackgroundFlag();
            return;
        }
        switch (routine) {
            case 0x10 -> {
                if (state.consumeSandCorkBackgroundFlag() != 0) {
                    boolean upperRoom = playerY < 0x400;
                    events.backgroundRoutine(upperRoom ? 0x14 : 0x18);
                    events.sandPosition((upperRoom ? 0x80 : 0x3E0) << 16);
                    events.specialRoutine(0x10);
                }
            }
            case 0x14 -> {
                if (state.consumeSandCorkBackgroundFlag() != 0) {
                    events.backgroundRoutine(0x18);
                    events.specialRoutine(0x10);
                } else if ((events.sandHeight() & 0xFFFF) >= 0x400) events.specialRoutine(0);
            }
            case 0x18 -> {
                if ((events.sandHeight() & 0xFFFF) >= 0xA00) events.specialRoutine(0);
            }
            case 0x1C -> {
                events.redrawRemaining(events.redrawRemaining() - 1);
                if (events.redrawRemaining() < 0) {
                    events.savedBackgroundX(0);
                    events.backgroundRoutine(0x20);
                }
            }
            default -> { }
        }
    }

    /** SpecialEvents loc_569BA runs before player physics, with 16.16 sand movement. */
    public void updateSpecialEvents(int act) {
        if (act != 1) return;
        var events = state().events();
        if (events.specialRoutine() == 0x10) {
            events.sandPosition(events.sandPosition() + 0xA000);
            events.backgroundCollision(true);
            gameState().setBackgroundCollisionFlag(true);
        }
    }
}
