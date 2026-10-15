package secondstrike.stages;

import com.openggf.game.sonic3k.difficulty.GumballSpringPolicy;

/** Native Gumball challenge; campaign board policy is owned by SecondStrikeCampaign. */
public final class SecondStrikeStages implements GumballSpringPolicy {
    /** Three consumable native-strength springs leave smaller recovery margins. */
    @Override
    public int[] springOffsets() {
        return new int[] {-32, 0, 32};
    }
}
