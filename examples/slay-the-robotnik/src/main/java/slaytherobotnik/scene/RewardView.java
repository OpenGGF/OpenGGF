package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.Reward;
import slaytherobotnik.run.RewardRoom;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/** The reward screen: a list of rewards to claim, with card and boss-relic picks. */
final class RewardView implements RunScreen.RoomView {
    private final RewardRoom room;
    private final Hotspots spots = new Hotspots();
    /** Index of the card or boss-relic reward being chosen from, or -1. */
    private int open = -1;
    private String toast;
    private int toastTicks;

    RewardView(RewardRoom room) {
        this.room = room;
    }

    private void layout(Shell shell) {
        spots.clear();
        int w = shell.width();
        if (open >= 0) {
            Reward r = room.rewards().get(open);
            int count = r instanceof Reward.CardChoice cc ? cc.cards().size()
                    : ((Reward.BossRelicChoice) r).relicIds().size();
            int itemW = r instanceof Reward.CardChoice ? CardRenderer.BIG_W : 100;
            int gap = 12;
            int total = count * itemW + (count - 1) * gap;
            for (int i = 0; i < count; i++) {
                spots.add("pick" + i, (w - total) / 2 + i * (itemW + gap), 50, itemW,
                        r instanceof Reward.CardChoice ? CardRenderer.BIG_H : 70);
            }
            spots.add("skip", w / 2 - 40, 196, 80, 15, r instanceof Reward.CardChoice);
            return;
        }
        List<Reward> rewards = room.rewards();
        int y = 66;
        for (int i = 0; i < rewards.size(); i++) {
            if (!room.claimed(i)) {
                spots.add("r" + i, w / 2 - 90, y, 180, 16);
            }
            y += 18;
        }
        spots.add("proceed", w / 2 - 50, 196, 100, 16);
    }

    @Override
    public void update(Shell shell, RunScreen screen) {
        if (toastTicks > 0) {
            toastTicks--;
        }
        layout(shell);
        String picked = spots.update(shell.in);
        if (open >= 0 && (shell.in.back || shell.in.mouse.rightPressed())) {
            open = -1;
            return;
        }
        if (picked == null) {
            return;
        }
        if (open >= 0) {
            Reward r = room.rewards().get(open);
            if (picked.equals("skip")) {
                room.skip(open);
                open = -1;
                shell.sfx(Sounds.SFX_SWITCH);
                return;
            }
            int i = Integer.parseInt(picked.substring(4));
            if (r instanceof Reward.CardChoice cc) {
                room.pickCard(open, cc.cards().get(i));
                shell.sfx(Sounds.SFX_RING);
            } else if (r instanceof Reward.BossRelicChoice b) {
                room.pickBossRelic(open, b.relicIds().get(i));
                shell.sfx(Sounds.SFX_SUPER_EMERALD);
            }
            open = -1;
            return;
        }
        if (picked.equals("proceed")) {
            shell.sfx(Sounds.SFX_SPRING);
            room.proceed();
            return;
        }
        int i = Integer.parseInt(picked.substring(1));
        Reward r = room.rewards().get(i);
        if (r instanceof Reward.CardChoice || r instanceof Reward.BossRelicChoice) {
            open = i;
            shell.sfx(Sounds.SFX_SWITCH);
            return;
        }
        if (room.claim(i)) {
            shell.sfx(r instanceof Reward.Rings ? Sounds.SFX_RING : Sounds.SFX_SUPER_EMERALD);
        } else {
            toast = "POTION SLOTS ARE FULL";
            toastTicks = 80;
            shell.sfx(Sounds.SFX_ERROR);
        }
    }

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        int w = shell.width();
        // The fight's stage, dimmed behind the rewards.
        LevelStages.draw(shell, c, 134);
        c.fill(0, 0, w, shell.height(), 0x80000010);
        SmallFont f = shell.font;
        String title = room.title().toUpperCase();
        f.drawOutlined(c, title, (w - f.width(title) * 2) / 2, 38, Colors.GOLD, 2);
        layout(shell);
        if (open >= 0) {
            drawPick(shell, screen, c);
            return;
        }
        Gfx.panel(c, w / 2 - 100, 58, 200, Math.max(30, room.rewards().size() * 18 + 14));
        List<Reward> rewards = room.rewards();
        int y = 66;
        for (int i = 0; i < rewards.size(); i++) {
            boolean done = room.claimed(i);
            boolean focus = spots.isFocused("r" + i);
            Gfx.button(c, f, "", w / 2 - 90, y, 180, 16, focus, !done, shell.ticks);
            drawRewardLine(shell, c, rewards.get(i), w / 2 - 84, y + 5, done);
            y += 18;
        }
        Hotspots.Spot go = spots.spot("proceed");
        Gfx.button(c, f, room.allClaimed() ? "PROCEED" : "SKIP REST", go.x(), go.y(), go.w(), go.h(),
                spots.isFocused("proceed"), true, shell.ticks);
        if (toastTicks > 0) {
            f.drawOutlined(c, toast, (w - f.width(toast)) / 2, 182, Colors.TEXT_BAD, 1);
        }
    }

    private void drawRewardLine(Shell shell, SceneCanvas c, Reward r, int x, int y, boolean done) {
        SmallFont f = shell.font;
        int color = done ? Colors.TEXT_DIM : Colors.TEXT;
        switch (r) {
            case Reward.Rings rings -> {
                HudIcons.ring(shell, c, x, y - 4, shell.ticks);
                f.drawShadowed(c, rings.amount() + " RINGS", x + 16, y, done ? color : Colors.RING);
            }
            case Reward.Potion p -> {
                HudIcons.potion(shell, c, p.potion(), x, y - 4);
                f.drawShadowed(c, p.potion().name().toUpperCase(), x + 16, y, color);
            }
            case Reward.RelicReward rr -> {
                Relic relic = shell.catalog.newRelic(rr.relicId());
                HudIcons.relic(shell, c, relic, x, y - 4);
                f.drawShadowed(c, relic.name().toUpperCase(), x + 16, y, done ? color : Colors.GOLD);
            }
            case Reward.CardChoice cc -> {
                c.draw(shell.art.icon("ui_deck"), x, y - 3);
                f.drawShadowed(c, "ADD A CARD TO YOUR DECK", x + 16, y, color);
            }
            case Reward.BossRelicChoice b -> {
                c.draw(shell.art.icon("node_boss"), x - 2, y - 5, com.openggf.mods.scene.SceneDraw.plain().withScale(0.8f));
                f.drawShadowed(c, "CHOOSE A BOSS RELIC", x + 16, y, done ? color : Colors.GOLD);
            }
        }
    }

    private void drawPick(Shell shell, RunScreen screen, SceneCanvas c) {
        Reward r = room.rewards().get(open);
        SmallFont f = shell.font;
        if (r instanceof Reward.CardChoice cc) {
            for (int i = 0; i < cc.cards().size(); i++) {
                Hotspots.Spot s = spots.spot("pick" + i);
                boolean focus = spots.isFocused(s.id());
                Card card = cc.cards().get(i);
                screen.cards.drawBig(c, card, null, null, s.x(), s.y() - (focus ? 4 : 0), false);
                if (focus) {
                    Gfx.focusFrame(c, s.x(), s.y() - 4, s.w(), s.h(), shell.ticks);
                    shell.cardTips(card, s.x(), s.y() - 4, s.w());
                }
            }
            Hotspots.Spot skip = spots.spot("skip");
            Gfx.button(c, f, "SKIP", skip.x(), skip.y(), skip.w(), skip.h(), spots.isFocused("skip"), true, shell.ticks);
        } else if (r instanceof Reward.BossRelicChoice b) {
            for (int i = 0; i < b.relicIds().size(); i++) {
                Hotspots.Spot s = spots.spot("pick" + i);
                Relic relic = shell.catalog.newRelic(b.relicIds().get(i));
                boolean focus = spots.isFocused(s.id());
                Gfx.panel(c, s.x(), s.y(), s.w(), 110);
                HudIcons.relic(shell, c, relic, s.x() + s.w() / 2 - 8, s.y() + 4, 16);
                f.drawCentered(c, relic.name().toUpperCase(), s.x() + s.w() / 2, s.y() + 22, Colors.GOLD);
                int ly = s.y() + 32;
                for (String line : f.wrap(relic.description(), s.w() - 10)) {
                    f.drawMarkup(c, line, s.x() + 5, ly, Colors.TEXT);
                    ly += SmallFont.LINE;
                }
                if (focus) {
                    Gfx.focusFrame(c, s.x(), s.y(), s.w(), 110, shell.ticks);
                }
            }
        }
    }
}
