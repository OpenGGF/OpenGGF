package com.openggf.game.sonic2.competition.view;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Ordered semantic display work for one represented level VInt. */
public final class Sonic2CompetitionVideoFrame {
    public record Sample(int firstFactors, int secondFactors, int[] horizontal,
                         CameraSnapshot firstCamera, CameraSnapshot secondCamera,
                         List<Integer> paletteWords, Sonic2CompetitionDisplayState displayState) {
        public Sample(int firstFactors, int secondFactors, int[] horizontal, CameraSnapshot firstCamera,
                      CameraSnapshot secondCamera, List<Integer> paletteWords, int timer, boolean paused) {
            this(firstFactors, secondFactors, horizontal, firstCamera, secondCamera, paletteWords,
                    new Sonic2CompetitionDisplayState() {
                        public int teleportTimer() { return timer; }
                        public boolean gamePaused() { return paused; }
                    });
        }
        public Sample { horizontal = horizontal.clone(); paletteWords = List.copyOf(paletteWords); }
        @Override public int[] horizontal() { return horizontal.clone(); }
    }
    private final Sonic2CompetitionArtResidency art;
    private final Sonic2CompetitionPlaneScroll planeScroll;
    private final Sonic2CompetitionPlanes planes;
    private final Sonic2CompetitionPlaneDraw planeDraw;
    private final Sonic2CompetitionSpritePages spritePages;
    private final Sonic2CompetitionViewRegisters registers;
    private final Supplier<Sample> source;
    private Sonic2CompetitionDisplaySources spriteSource;
    private Sonic2CompetitionDisplayState displayState = Sonic2CompetitionDisplayState.INACTIVE;
    private java.util.function.Consumer<Boolean> cameraCopies = title -> { };
    public void bindCameraCopies(java.util.function.Consumer<Boolean> copies) { cameraCopies = Objects.requireNonNull(copies); }
    private Supplier<List<Sonic2CompetitionDisplayInputs.Admission>> titleCardSprites = List::of;
    public void setTitleCardSprites(Supplier<List<Sonic2CompetitionDisplayInputs.Admission>> sprites) {
        titleCardSprites = Objects.requireNonNull(sprites);
    }
    private Runnable titleCardPlanes = () -> { };
    private Runnable hudPatterns = () -> { };

    public Sonic2CompetitionVideoFrame(Sonic2CompetitionArtResidency art, Sonic2CompetitionPlaneScroll planeScroll,
                                      Sonic2CompetitionPlanes planes, Sonic2CompetitionPlaneDraw planeDraw,
                                      Sonic2CompetitionSpritePages spritePages, Sonic2CompetitionViewRegisters registers,
                                      Supplier<Sample> source) {
        this.art = Objects.requireNonNull(art); this.planeScroll = Objects.requireNonNull(planeScroll);
        this.planes = Objects.requireNonNull(planes); this.planeDraw = Objects.requireNonNull(planeDraw);
        this.spritePages = Objects.requireNonNull(spritePages); this.registers = Objects.requireNonNull(registers);
        this.source = Objects.requireNonNull(source);
    }
    /** B's read-only clock port can be installed without transferring its lifecycle ownership. */
    public void bindDisplayState(Sonic2CompetitionDisplayState state) { displayState = Objects.requireNonNull(state); }
    public Sonic2CompetitionDisplayState displayState() { return displayState; }
    public void bindSpriteSource(com.openggf.data.RomByteReader rom) {
        spriteSource = new Sonic2CompetitionDisplaySources(Objects.requireNonNull(rom));
    }
    public void prepareSprites(com.openggf.game.session.GameplayModeContext context,
            com.openggf.game.sonic2.competition.Sonic2CompetitionSession session,
            com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile profile) {
        var objects = new java.util.ArrayList<>(titleCardSprites.get());
        objects.addAll(Sonic2CompetitionObjectDisplay.admissions(context.getObjectManager(),
                context.getWorldSession().getGameModule().getObjectArtProvider()));
        spritePages.prepare(Objects.requireNonNull(spriteSource, "Native sprite inputs").current(
                context, session, profile, objects, displayState));
        // BuildSprites_2P merges P1/P2 visibility. Replace the generic primary-
        // camera result for the actual admitted humans; omitted bodies retain it.
        for (var visibility : spritePages.visibility())
            if (visibility.slot() < 2)
                session.player(visibility.slot()).setRenderFlagOnScreen(visibility.onScreen());
            else for (var owner:context.getObjectManager().getActiveObjects())
                if(owner instanceof com.openggf.level.objects.AbstractObjectInstance object
                        && object.getSlotIndex()==visibility.slot())
                    com.openggf.level.objects.ObjectDrawInternalAccess.commitNativeDisplayVisibility(
                            object,visibility.onScreen());
    }
    public void setTitleCardPlanes(Runnable draw) { titleCardPlanes = Objects.requireNonNull(draw); }
    public void setHudPatterns(Runnable update) { hudPatterns = Objects.requireNonNull(update); }
    public static boolean accepts(PlcLifecyclePhase phase) {
        return phase == PlcLifecyclePhase.ORDINARY_LEVEL || phase == PlcLifecyclePhase.NORMAL_PAUSE
                || phase == PlcLifecyclePhase.LEVEL_TITLE_CARD || phase == PlcLifecyclePhase.LAG
                || phase == PlcLifecyclePhase.PALETTE_FADE;
    }
    /** Vint_Level (s2.asm:781-808), Vint_TitleCard (s2.asm:1046-1057), Vint_Fade (s2.asm:1068-1071). */
    public void serviceVBlank(PlcLifecyclePhase phase) {
        if (!accepts(phase)) throw new IllegalArgumentException("Not a level display interrupt");
        var input = source.get();
        registers.publish(phase, input.firstFactors(), input.secondFactors(), input.horizontal(),
                input.firstCamera(), input.secondCamera(), spritePages.completed(), input.paletteWords(),
                input.displayState().teleportTimer(), input.displayState().gamePaused());
        boolean ordinary = phase == PlcLifecyclePhase.ORDINARY_LEVEL || phase == PlcLifecyclePhase.NORMAL_PAUSE;
        boolean title = phase == PlcLifecyclePhase.LEVEL_TITLE_CARD;
        if (ordinary || title) art.serviceDmaQueueVBlank();
        if (title) titleCardPlanes.run();
        if (ordinary || title) { planeScroll.copyAtVInt(title); cameraCopies.accept(title); }
        if (ordinary) planeScroll.drawForeground(planes, planeDraw);
        if (ordinary) hudPatterns.run(); // Do_Updates (s2.asm:805-808): planes, HudUpdate, PLC.
        if (ordinary || title || phase == PlcLifecyclePhase.PALETTE_FADE) art.servicePlc(phase);
    }
    public Sonic2CompetitionViewRegisters registers() { return registers; }
    public Sonic2CompetitionPlanes planes() { return planes; }
    public Sonic2CompetitionPlaneScroll planeScroll() { return planeScroll; }
    public Sonic2CompetitionSpritePages spritePages() { return spritePages; }
    public Sonic2CompetitionArtResidency art() { return art; }
}
