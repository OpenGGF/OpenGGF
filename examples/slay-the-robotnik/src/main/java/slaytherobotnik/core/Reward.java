package slaytherobotnik.core;

import java.util.List;

/** One line on a reward screen. The player claims or skips each. */
public sealed interface Reward {
    record Rings(int amount) implements Reward { }

    record Potion(PotionDef potion) implements Reward { }

    record RelicReward(String relicId) implements Reward { }

    /** Pick one card (or skip). */
    record CardChoice(List<Card> cards) implements Reward { }

    /** Pick one of several relics (after a boss). */
    record BossRelicChoice(List<String> relicIds) implements Reward { }
}
