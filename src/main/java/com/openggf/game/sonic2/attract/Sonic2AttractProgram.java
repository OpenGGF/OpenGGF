package com.openggf.game.sonic2.attract;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.data.RomByteReader;
import com.openggf.game.internal.AttractDemoPlayback;
import com.openggf.game.internal.TitleAttractDemo;
import com.openggf.game.sonic2.competition.Sonic2CompetitionAttractVisit;

import java.io.IOException;

/** Cartridge-specific plans layered onto the existing ROM attract interpreter. */
public final class Sonic2AttractProgram implements TitleAttractDemo {
    private final Sonic2AttractService service;
    private final boolean overseas;

    public Sonic2AttractProgram(RomByteReader rom, boolean overseas) throws IOException {
        this(new Sonic2AttractService(new Sonic2AttractData(rom, false)), overseas);
    }

    public static Sonic2AttractProgram forLockOn(RomByteReader lockOn) throws IOException {
        return new Sonic2AttractProgram(Sonic2AttractService.forLockOn(lockOn), true);
    }

    private Sonic2AttractProgram(Sonic2AttractService service, boolean overseas) {
        this.service = service;
        this.overseas = overseas;
    }

    @Override public void enterTitle() { service.enterTitle(); }

    public boolean demoMode() {
        return switch (service.phase()) {
            case AWAITING_LEVEL_SETUP, PLAYBACK, FADING -> true;
            default -> false;
        };
    }

    @Override
    public Launch onTitleTick() {
        if (!service.onTitleVInt()) return null;
        var plan = service.requestNextLaunch(true).plan();
        Destination destination;
        if (plan.sessionKind() == Sonic2AttractService.SessionKind.COMPETITION) {
            destination = (Destination.NativeScreen) (host, playback) ->
                    new Sonic2CompetitionAttractVisit(host, playback, plan.nativeZone(), plan.actIndex(), overseas);
        } else {
            destination = new Destination.Level(campaignZone(plan.nativeZone()), plan.actIndex(), plan.team());
        }
        return new Launch(destination, new AttractDemoPlayback(new PlaybackProgram()), !plan.resetEmeraldProgress());
    }

    private static int campaignZone(int nativeZone) {
        return switch (nativeZone) {
            case 0 -> 0;    // Emerald Hill
            case 0x0D -> 1; // Chemical Plant
            case 0x0F -> 2; // Aquatic Ruin
            case 0x0C -> 3; // Casino Night
            default -> throw new IllegalArgumentException("Unknown attract level: " + nativeZone);
        };
    }

    private final class PlaybackProgram implements AttractDemoPlayback.Program {
        @Override public void begin() { service.beginPlayback(); }
        @Override public void reload() { service.restartPlaybackForLevelReload(); }
        @Override public void serviceLevelWait() { service.onLevelUpdates(); }
        @Override public LogicalInputSnapshot controllers(LogicalInputSnapshot physical) {
            return service.stepControllers(physical).controllers();
        }
        @Override public AttractDemoPlayback.Transition finishObjects(boolean inactive) {
            var transition = service.finishIteration(inactive);
            return switch (transition.event()) {
                case NONE -> AttractDemoPlayback.Transition.NONE;
                // Level_MainLoop (s2.asm:5135-5149) changes each colour on waits 1, 4, ..., 58.
                case BEGIN_FADE -> new AttractDemoPlayback.Transition(3, null);
                case COMPLETE -> new AttractDemoPlayback.Transition(0,
                        transition.target() == Sonic2AttractService.ReturnTarget.TITLE_SCREEN
                                ? AttractDemoPlayback.ReturnTarget.TITLE : AttractDemoPlayback.ReturnTarget.BOOT);
            };
        }
    }
}
