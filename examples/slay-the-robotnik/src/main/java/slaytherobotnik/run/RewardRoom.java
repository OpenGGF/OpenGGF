package slaytherobotnik.run;

import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Reward;

/** The reward screen after a fight, chest or event. Each reward can be claimed once. */
public final class RewardRoom implements Room {
    private final Run run;
    private final String title;
    private final List<Reward> rewards;
    private final boolean[] claimed;

    RewardRoom(Run run, String title, List<Reward> rewards) {
        this.run = run;
        this.title = title;
        this.rewards = new ArrayList<>(rewards);
        this.claimed = new boolean[rewards.size()];
    }

    public String title() { return title; }
    public List<Reward> rewards() { return List.copyOf(rewards); }
    public boolean claimed(int index) { return claimed[index]; }

    boolean[] claimedFlags() {
        return claimed;
    }

    /**
     * Claims rings, a potion or a relic. Returns false for card and boss-relic choices (use
     * {@link #pickCard} / {@link #pickBossRelic}) and when the potion belt is full.
     */
    public boolean claim(int index) {
        if (claimed[index]) {
            return false;
        }
        Reward reward = rewards.get(index);
        switch (reward) {
            case Reward.Rings r -> run.state().gainRings(r.amount());
            case Reward.Potion p -> {
                if (!run.state().addPotion(p.potion())) {
                    return false;
                }
            }
            case Reward.RelicReward r -> run.state().obtainRelic(r.relicId());
            case Reward.CardChoice c -> {
                return false;
            }
            case Reward.BossRelicChoice b -> {
                return false;
            }
        }
        claimed[index] = true;
        run.autosave();
        return true;
    }

    /** Takes one card from a card choice. */
    public boolean pickCard(int index, Card card) {
        if (claimed[index] || !(rewards.get(index) instanceof Reward.CardChoice choice)
                || !choice.cards().contains(card)) {
            return false;
        }
        run.state().addCard(card);
        claimed[index] = true;
        run.autosave();
        return true;
    }

    /** Skips a card choice (the "Skip" button); it stays visible but done. */
    public void skip(int index) {
        claimed[index] = true;
    }

    /** Takes one relic from a boss relic choice. */
    public boolean pickBossRelic(int index, String relicId) {
        if (claimed[index] || !(rewards.get(index) instanceof Reward.BossRelicChoice choice)
                || !choice.relicIds().contains(relicId)) {
            return false;
        }
        run.state().obtainRelic(relicId);
        claimed[index] = true;
        run.autosave();
        return true;
    }

    /** True when nothing is left to claim. */
    public boolean allClaimed() {
        for (boolean c : claimed) {
            if (!c) {
                return false;
            }
        }
        return true;
    }

    /** Leaves the reward screen. */
    public void proceed() {
        run.leaveRoom();
    }
}
