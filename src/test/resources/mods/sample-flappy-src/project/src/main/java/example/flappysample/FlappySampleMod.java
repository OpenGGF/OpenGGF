package example.flappysample;

import com.openggf.control.PlayerInputState;
import com.openggf.game.CharacterKey;
import com.openggf.game.ZoneKey;
import com.openggf.level.objects.HudLabel;
import com.openggf.level.objects.HudMetric;
import com.openggf.level.objects.HudProfile;
import com.openggf.level.objects.HudRow;
import com.openggf.level.objects.HudWarningPolicy;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.BakedSheetRef;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import com.openggf.mods.code.ModHudProfileContribution;
import com.openggf.mods.code.ModInputFilterContribution;
import com.openggf.mods.code.ModLaunchTeamContribution;
import com.openggf.mods.code.ModZoneContribution;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

public final class FlappySampleMod implements GgfMod {
    private static final int HORIZONTAL = AbstractPlayableSprite.INPUT_LEFT
            | AbstractPlayableSprite.INPUT_RIGHT;

    @Override
    public void register(ModContext context) {
        ZoneKey flappy = ZoneKey.mod("sample-flappy", "flappy-garden");
        context.registerObject("controller", (spawn, registry) -> new FlappyController(spawn));
        context.registerObject("pipe", (spawn, registry) -> new FlappyPipe(spawn));
        context.registerObjectArt("pipe", new BakedSheetRef("art/pipe.ggfs"));
        context.registerObjectPreview("pipe", "pipe");
        context.registerZone(new ModZoneContribution("flappy-garden",
                new BakedLevelRef("levels/flappy/level.json"), null, null, true));
        context.registerLaunchTeam(new ModLaunchTeamContribution(
                flappy, CharacterKey.TAILS, List.of()));
        context.registerInputFilter(new ModInputFilterContribution(
                flappy, FlappySampleMod::suppressHorizontal));
        context.registerHudProfile(new ModHudProfileContribution(flappy, flappyHud()));
    }

    static PlayerInputState suppressHorizontal(PlayerInputState input) {
        return PlayerInputState.of(
                input.heldMask() & ~HORIZONTAL,
                input.pressedMask() & ~HORIZONTAL,
                input.actionHeldMask(),
                input.actionPressedMask(),
                input.startHeld(),
                input.startPressed());
    }

    private static HudProfile flappyHud() {
        return new HudProfile(List.of(
                new HudRow(false, HudLabel.SCORE, HudMetric.SCORE,
                        16, 8, 64, 8, 6, HudWarningPolicy.NONE),
                new HudRow(true, HudLabel.TIME, HudMetric.TIME,
                        16, 24, 56, 24, 4, HudWarningPolicy.TIMER_FLASH),
                new HudRow(true, HudLabel.SCORE, HudMetric.RINGS,
                        16, 40, 64, 40, 3, HudWarningPolicy.NONE),
                new HudRow(true, HudLabel.LIVES, HudMetric.LIVES,
                        16, 200, 56, 208, 2, HudWarningPolicy.NONE)));
    }
}
