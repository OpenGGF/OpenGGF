package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardTarget;
import slaytherobotnik.core.CardType;
import slaytherobotnik.core.ChoiceRequest;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.CombatEvent;
import slaytherobotnik.core.Creature;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.Intent;
import slaytherobotnik.core.IntentKind;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Power;
import slaytherobotnik.run.CombatRoom;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Ease;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/**
 * A fight. The rules in {@link Combat} resolve instantly; this view keeps its own presented
 * copy of HP, Block, powers, the hand and Energy, and replays the combat's events onto it
 * one at a time with animations, so the player sees each hit land in order. Input is only
 * accepted once the replay has caught up.
 */
final class CombatView implements RunScreen.RoomView {
    /** The screen row the stage's median floor sits on; creatures stand on the floor under them. */
    private static final int GROUND = 134;
    private static final int PLAYER_X = 84;
    private static final int HAND_Y = 162;

    private final CombatRoom room;
    private final Combat combat;
    private final Hotspots spots = new Hotspots();

    // Presented state.
    /** Each creature's numbers as the replay has shown them so far (they trail the model while events play). */
    private static final class Shown {
        int hp;
        int maxHp;
        int block;

        Shown(int hp, int maxHp, int block) {
            this.hp = hp;
            this.maxHp = maxHp;
            this.block = block;
        }
    }

    private final Map<Creature, Shown> stats = new IdentityHashMap<>();
    private final Map<Creature, Map<String, Integer>> powers = new IdentityHashMap<>();
    private final List<Card> hand = new ArrayList<>();
    private final List<Enemy> shown = new ArrayList<>();
    private final Map<Enemy, Integer> dying = new IdentityHashMap<>();
    private final Map<Enemy, Intent> intents = new IdentityHashMap<>();
    private final Set<Enemy> acted = new java.util.HashSet<>();
    private int energy;
    private int drawCount;
    private int discardCount;
    private int exhaustCount;

    // Effects.
    private final Map<Creature, Integer> flash = new IdentityHashMap<>();
    private final Map<Creature, Integer> lunge = new IdentityHashMap<>();
    private final Map<Creature, Integer> hurt = new IdentityHashMap<>();
    private final List<Popup> popups = new ArrayList<>();
    private final Map<Enemy, EnemyVisuals.Box> boxes = new IdentityHashMap<>();
    private String banner;
    private int bannerTicks;
    private String message;
    private int messageTicks;

    // Replay.
    private int cursor;
    private int wait;

    // Entrance: the hero runs in, the enemies arrive one after another (a boss drops in and is
    // named), and only then does the first turn's replay start.
    private static final int HERO_ENTRY = 22;
    private static final int ENEMY_ENTRY = 20;
    private static final int ENEMY_STAGGER = 6;
    private static final int BOSS_NAME_TICKS = 56;
    private final boolean bossFight;
    private final int entranceLength;
    private int entrance;

    // Interaction.
    private Card selected;
    private int potionMenu = -1;
    private int targetingPotion = -1;
    private final Set<Card> choicePicks = new LinkedHashSet<>();
    private CardGrid choiceGrid;
    private List<Card> choiceGridOptions = List.of();
    private int endTimer;

    /**
     * A short sprite effect: a ROM explosion, a boss explosion or a freed animal. Frames step
     * every {@code frameTicks}; it moves by its velocity and waits {@code delay} ticks first.
     */
    private static final class Fx {
        final String key;
        final int[] frames;
        final int frameTicks;
        final int life;
        float x;
        float y;
        float vx;
        float vy;
        int delay;
        int age;

        Fx(String key, int[] frames, int frameTicks, int life, float x, float y) {
            this.key = key;
            this.frames = frames;
            this.frameTicks = frameTicks;
            this.life = life;
            this.x = x;
            this.y = y;
        }
    }

    private final List<Fx> effects = new ArrayList<>();

    /** Where each hand card is drawn; it eases toward its slot so draws and plays slide. */
    private final Map<Card, float[]> handPos = new IdentityHashMap<>();

    /** A card in flight: played (to the centre, then the discard pile) or discarded. */
    private static final class Flight {
        final Card card;
        final float x0;
        final float y0;
        final float mx;
        final float my;
        final float x1;
        final float y1;
        final int life;
        int age;

        Flight(Card card, float x0, float y0, float mx, float my, float x1, float y1, int life) {
            this.card = card;
            this.x0 = x0;
            this.y0 = y0;
            this.mx = mx;
            this.my = my;
            this.x1 = x1;
            this.y1 = y1;
            this.life = life;
        }
    }

    private final List<Flight> flights = new ArrayList<>();

    /** A floating number or word. */
    private static final class Popup {
        final String text;
        final int color;
        final float x;
        float y;
        int age;
        final int life;
        final int scale;

        Popup(String text, int color, float x, float y, int life, int scale) {
            this.text = text;
            this.color = color;
            this.x = x;
            this.y = y;
            this.life = life;
            this.scale = scale;
        }
    }

    CombatView(Shell shell, CombatRoom room) {
        this.room = room;
        this.combat = room.combat();
        for (Enemy e : combat.enemies()) {
            if (e.isActive()) {
                shown.add(e);
            }
        }
        // The opening draw and start-of-combat effects replay from the first event.
        snapshotStart();
        bossFight = combat.roomType().equals("Boss");
        entranceLength = enemiesInAt() + (bossFight ? BOSS_NAME_TICKS : 0);
    }

    /** The frame of the entrance by which every enemy has arrived. */
    private int enemiesInAt() {
        return Math.max(HERO_ENTRY, ENEMY_STAGGER * (shown.size() - 1) + ENEMY_ENTRY);
    }

    /** How far the hero (index -1) or enemy {@code index} is through arriving, 0 to 1. */
    private float arrival(int index) {
        if (index < 0) {
            return Math.min(1f, entrance / (float) HERO_ENTRY);
        }
        return Math.max(0f, Math.min(1f, (entrance - index * ENEMY_STAGGER) / (float) ENEMY_ENTRY));
    }

    /** Presented state before any event: full starting values, empty hand. */
    private void snapshotStart() {
        for (Creature cr : creatures()) {
            int hpBefore = cr.hp();
            int block = 0;
            stats.put(cr, new Shown(hpBefore, cr.maxHp(), block));
            powers.put(cr, new LinkedHashMap<>());
        }
        // Undo what the events will redo: damage and Block from start-of-combat effects.
        for (CombatEvent e : combat.events()) {
            if (e instanceof CombatEvent.Damaged d && stats.containsKey(d.target())) {
                stats.get(d.target()).hp += d.hpLost();
            } else if (e instanceof CombatEvent.Healed h && stats.containsKey(h.target())) {
                stats.get(h.target()).hp -= h.amount();
            }
        }
        energy = 0;
        drawCount = combat.player().drawPile().size() + combat.player().hand().size();
        for (CombatEvent e : combat.events()) {
            if (e instanceof CombatEvent.CardCreated cc && !cc.pile().equals("draw")) {
                // Created cards never sat in the draw pile.
                drawCount -= cc.pile().equals("hand") ? 1 : 0;
            }
        }
        discardCount = 0;
        exhaustCount = 0;
        for (Enemy e : shown) {
            intents.put(e, e.intent(combat));
        }
    }

    private List<Creature> creatures() {
        List<Creature> list = new ArrayList<>();
        list.add(combat.player());
        list.addAll(combat.enemies());
        return list;
    }

    private Shown stat(Creature c) {
        return stats.computeIfAbsent(c, k -> new Shown(k.hp(), k.maxHp(), k.block()));
    }

    /** Copies the model into the presented state (when the replay is idle). */
    private void sync() {
        for (Creature cr : creatures()) {
            stats.put(cr, new Shown(cr.hp(), cr.maxHp(), cr.block()));
            Map<String, Integer> p = new LinkedHashMap<>();
            for (Power power : cr.powers()) {
                p.put(power.id(), power.amount());
            }
            powers.put(cr, p);
        }
        hand.clear();
        hand.addAll(combat.player().hand());
        energy = combat.player().energy();
        drawCount = combat.player().drawPile().size();
        discardCount = combat.player().discardPile().size();
        exhaustCount = combat.player().exhaustPile().size();
        for (Enemy e : combat.enemies()) {
            if (e.isActive()) {
                if (!shown.contains(e)) {
                    shown.add(e);
                }
                intents.put(e, e.intent(combat));
            }
        }
        shown.sort((a, b) -> Integer.compare(combat.enemies().indexOf(a), combat.enemies().indexOf(b)));
        acted.clear();
    }

    private boolean replaying() {
        return wait > 0 || cursor < combat.events().size();
    }

    // ------------------------------------------------------------------ update

    @Override
    public void update(Shell shell, RunScreen screen) {
        animateReadouts();
        tickEffects();
        easeHand(shell.width());
        boolean fast = shell.ctx.input().player1().actionHeldMask() != 0 || shell.in.mouse.leftDown();
        if (entrance < entranceLength) {
            int before = entrance;
            entrance = Math.min(entranceLength, entrance + (fast ? 3 : 1));
            if (bossFight && before < enemiesInAt() && entrance >= enemiesInAt()) {
                showBanner(room.encounterName().toUpperCase(), BOSS_NAME_TICKS);
            }
            return;
        }
        if (wait > 0) {
            wait -= (fast ? 3 : 1) * (shell.profile.get(SettingsScreen.FAST_COMBAT) == 1 ? 2 : 1);
            if (wait > 0) {
                return;
            }
            wait = 0;
        }
        while (cursor < combat.events().size() && wait == 0) {
            wait = apply(shell, combat.events().get(cursor++));
        }
        if (replaying()) {
            return;
        }
        if (combat.isOver()) {
            endTimer++;
            if (endTimer == 1) {
                finishBanner(shell);
            }
            if (endTimer > 70 || (endTimer > 20 && (shell.in.accept || shell.in.mouse.leftPressed()))) {
                room.finish();
            }
            return;
        }
        sync();
        if (combat.hasPendingChoice()) {
            updateChoice(shell);
            return;
        }
        updateTurn(shell, screen);
    }

    private void finishBanner(Shell shell) {
        if (combat.won()) {
            if (combat.roomType().equals("Boss")) {
                shell.music(Sounds.MUSIC_ACT_CLEAR);
            }
            showBanner("VICTORY!", 70);
            shell.sfx(Sounds.SFX_PERFECT);
        } else {
            showBanner("DEFEATED", 90);
            shell.stopMusic();
        }
    }

    private void tickEffects() {
        tickMap(flash);
        tickMap(lunge);
        tickMap(hurt);
        dying.replaceAll((k, v) -> v + 1);
        for (Fx fx : effects) {
            if (fx.delay > 0) {
                fx.delay--;
                continue;
            }
            fx.age++;
            fx.x += fx.vx;
            fx.y += fx.vy;
        }
        effects.removeIf(fx -> fx.age >= fx.life);
        flights.forEach(fl -> fl.age++);
        flights.removeIf(fl -> fl.age >= fl.life);
        popups.removeIf(p -> ++p.age > p.life);
        for (Popup p : popups) {
            p.y -= 0.35f;
        }
        if (bannerTicks > 0) {
            bannerTicks--;
        }
        if (messageTicks > 0) {
            messageTicks--;
        }
    }

    /** Moves each hand card a third of the way to its slot; new cards start at the draw pile. */
    private void easeHand(int w) {
        handPos.keySet().removeIf(card -> !hand.contains(card));
        for (int i = 0; i < hand.size(); i++) {
            float[] pos = handPos.computeIfAbsent(hand.get(i), k -> new float[] {6, HAND_Y + 30});
            pos[0] += (handX(i, hand.size(), w) - pos[0]) * 0.34f;
            pos[1] += (HAND_Y - pos[1]) * 0.34f;
        }
    }

    /** Where a hand card is drawn now (its eased position, or its slot). */
    private float[] handPosition(Card card, int w) {
        float[] pos = handPos.get(card);
        if (pos != null) {
            return pos;
        }
        int i = hand.indexOf(card);
        return new float[] {i < 0 ? w / 2f : handX(i, hand.size(), w), HAND_Y};
    }

    private static <K> void tickMap(Map<K, Integer> map) {
        map.replaceAll((k, v) -> v - 1);
        map.values().removeIf(v -> v <= 0);
    }

    // ------------------------------------------------------------------ replay

    /**
     * Applies one event to the presented state and returns how many frames the replay holds it
     * before the next one: this is the fight's pacing. Big moments get room to read (an enemy's
     * move 16, a death 18, the turn banners 18-22, a shuffle 10), cards flow quickly (a draw 3, a
     * discard 1-4, a play 7), and pure bookkeeping (energy, messages, the end) takes none.
     */
    private int apply(Shell shell, CombatEvent event) {
        return switch (event) {
            case CombatEvent.TurnStarted t -> {
                if (t.playerTurn()) {
                    acted.clear();
                    if (t.turn() > 1 || combat.turn() == 1) {
                        showBanner("YOUR TURN", 40);
                    }
                    yield 18;
                }
                showBanner("ENEMY TURN", 34);
                yield 22;
            }
            case CombatEvent.CardDrawn d -> {
                if (!hand.contains(d.card())) {
                    hand.add(d.card());
                }
                drawCount = Math.max(0, drawCount - 1);
                yield 3;
            }
            case CombatEvent.CardPlayed p -> {
                float[] from = handPosition(p.card(), shell.width());
                float midX = shell.width() / 2f - CardRenderer.SMALL_W / 2f;
                boolean power = p.card().type().equals(CardType.POWER);
                flights.add(new Flight(p.card(), from[0], from[1], midX, 72, power ? midX : shell.width() - 30,
                        power ? 40 : HAND_Y + 30, 24));
                hand.remove(p.card());
                if (p.card().type().equals(CardType.ATTACK)) {
                    lunge.put(combat.player(), 10);
                    shell.sfx(Sounds.SFX_ROLL);
                } else if (p.card().type().equals(CardType.POWER)) {
                    shell.sfx(Sounds.SFX_SUPER_EMERALD);
                } else {
                    shell.sfx(Sounds.SFX_SWITCH);
                }
                yield 7;
            }
            case CombatEvent.CardDiscarded d -> {
                if (hand.contains(d.card())) {
                    float[] from = handPosition(d.card(), shell.width());
                    flights.add(new Flight(d.card(), from[0], from[1], from[0], from[1] - 10, shell.width() - 30,
                            HAND_Y + 30, 14));
                }
                hand.remove(d.card());
                discardCount++;
                yield d.manual() ? 4 : 1;
            }
            case CombatEvent.CardExhausted x -> {
                hand.remove(x.card());
                exhaustCount++;
                popup(x.card().name().toUpperCase() + " EXHAUSTED", 0xFFFF9048, PLAYER_X + 40, HAND_Y - 20, 40, 1);
                yield 5;
            }
            case CombatEvent.CardCreated cc -> {
                switch (cc.pile()) {
                    case "hand" -> hand.add(cc.card());
                    case "draw" -> drawCount++;
                    default -> discardCount++;
                }
                yield 4;
            }
            case CombatEvent.Shuffled s -> {
                drawCount += s.cards();
                discardCount = 0;
                popup("SHUFFLE!", Colors.TEXT, 30, HAND_Y - 14, 30, 1);
                shell.sfx(Sounds.SFX_SPRING);
                yield 10;
            }
            case CombatEvent.Damaged d -> {
                Shown st = stat(d.target());
                st.hp = Math.max(0, st.hp - d.hpLost());
                st.block = Math.max(0, st.block - d.blocked());
                flash.put(d.target(), 6);
                float[] at = anchor(d.target());
                if (d.hpLost() > 0) {
                    popup(Integer.toString(d.hpLost()), Colors.TEXT_BAD, at[0], at[1], 36, 2);
                    if (d.target().isPlayer()) {
                        hurt.put(d.target(), 16);
                        shell.sfx(Sounds.SFX_HURT);
                    } else {
                        shell.sfx(d.target().maxHp() >= 150 ? Sounds.SFX_BOSS_HIT : Sounds.SFX_SPIKES);
                    }
                    if (d.hpLost() >= 15) {
                        shell.shake(10);
                    }
                } else if (d.blocked() > 0) {
                    popup("BLOCKED", Colors.BLOCK_BLUE, at[0], at[1], 30, 1);
                    shell.sfx(Sounds.SFX_SHIELD);
                }
                yield 8;
            }
            case CombatEvent.BlockGained b -> {
                stat(b.target()).block += b.amount();
                float[] at = anchor(b.target());
                popup("+" + b.amount(), Colors.BLOCK_BLUE, at[0], at[1] + 8, 26, 1);
                if (b.target().isPlayer()) {
                    shell.sfx(Sounds.SFX_INSTA_SHIELD);
                }
                yield 4;
            }
            case CombatEvent.PowerChanged pc -> {
                Map<String, Integer> p = powers.computeIfAbsent(pc.target(), k -> new LinkedHashMap<>());
                if (pc.removed()) {
                    p.remove(pc.powerId());
                } else {
                    p.merge(pc.powerId(), pc.delta(), Integer::sum);
                    if (p.get(pc.powerId()) == 0) {
                        p.remove(pc.powerId());
                    }
                    if (pc.delta() != 0) {
                        float[] at = anchor(pc.target());
                        Power live = pc.target().power(pc.powerId());
                        String name = live == null ? pc.powerId() : live.name();
                        popup((pc.delta() > 0 ? "+" : "") + pc.delta() + " " + name.toUpperCase(),
                                live != null && live.isDebuff() ? 0xFFDA90FF : Colors.TEXT_GOOD, at[0], at[1] - 10, 34, 1);
                    }
                }
                yield pc.removed() ? 1 : 5;
            }
            case CombatEvent.DebuffNegated n -> {
                float[] at = anchor(n.target());
                popup("NEGATED", Colors.WHITE, at[0], at[1], 30, 1);
                yield 4;
            }
            case CombatEvent.Healed h -> {
                Shown st = stat(h.target());
                st.hp = Math.min(st.maxHp, st.hp + h.amount());
                float[] at = anchor(h.target());
                popup("+" + h.amount(), Colors.TEXT_GOOD, at[0], at[1], 34, 2);
                shell.sfx(Sounds.SFX_RING);
                yield 6;
            }
            case CombatEvent.MaxHpChanged m -> {
                stat(m.target()).maxHp += m.delta();
                yield 2;
            }
            case CombatEvent.EnergyChanged e -> {
                energy = e.energy();
                yield 0;
            }
            case CombatEvent.EnemyMove m -> {
                lunge.put(m.enemy(), 14);
                acted.add(m.enemy());
                if (m.move() != null) {
                    float[] at = anchor(m.enemy());
                    popup(m.move().name().toUpperCase(), Colors.WHITE, at[0], at[1] - 24, 40, 1);
                }
                yield 16;
            }
            case CombatEvent.EnemySpawned s -> {
                if (!shown.contains(s.enemy())) {
                    shown.add(s.enemy());
                    shown.sort((a, b) -> Integer.compare(combat.enemies().indexOf(a), combat.enemies().indexOf(b)));
                }
                stats.put(s.enemy(), new Shown(s.enemy().hp(), s.enemy().maxHp(), s.enemy().block()));
                intents.put(s.enemy(), s.enemy().intent(combat));
                yield 10;
            }
            case CombatEvent.EnemyDied d -> {
                dying.put(d.enemy(), 0);
                explode(shell, d.enemy());
                yield 18;
            }
            case CombatEvent.EnemyEscaped x -> {
                dying.put(x.enemy(), 0);
                yield 8;
            }
            case CombatEvent.RelicTriggered r -> {
                popup(r.relic().name().toUpperCase(), Colors.GOLD, 40, RunScreen.HUD_HEIGHT + 6, 40, 1);
                yield 4;
            }
            case CombatEvent.PotionUsed p -> {
                float[] at = anchor(combat.player());
                popup(p.potion().name().toUpperCase(), Colors.GOLD, at[0], at[1] - 16, 40, 1);
                shell.sfx(Sounds.SFX_BREAK);
                yield 8;
            }
            case CombatEvent.RingsGained g -> {
                popup("+" + g.amount() + " RINGS", Colors.RING, PLAYER_X, GROUND - 60, 40, 1);
                shell.sfx(Sounds.SFX_RING);
                yield 6;
            }
            case CombatEvent.Message msg -> {
                showMessage(msg.text().toUpperCase());
                yield 0;
            }
            case CombatEvent.Cue cue -> {
                if (cue.key().equals("split") && cue.source() instanceof Enemy e) {
                    dying.put(e, 10);
                }
                yield 8;
            }
            case CombatEvent.CardRetained r -> 0;
            case CombatEvent.CardUpgraded u -> 2;
            case CombatEvent.Victory v -> 0;
            case CombatEvent.Defeat d -> 0;
        };
    }

    /**
     * A badnik bursts into the ROM's explosion and frees its animal, as in the games; a boss
     * goes up in a chain of boss explosions instead.
     */
    private void explode(Shell shell, Enemy enemy) {
        EnemyVisuals.Box box = boxes.get(enemy);
        float cx = box != null ? box.centerX() : enemyX(Math.max(0, shown.indexOf(enemy)));
        float cy = box != null ? box.y() + box.h() / 2f : feet(Math.round(cx)) - 20;
        boolean boss = combat.roomType().equals("Boss") && enemy.maxHp() >= 250;
        if (boss) {
            shell.sfx(Sounds.SFX_EXPLODE);
            for (int i = 0; i < 8; i++) {
                float ox = (box != null ? box.w() : 60) * ((i * 37 % 100) / 100f - 0.5f);
                float oy = (box != null ? box.h() : 60) * ((i * 61 % 100) / 100f - 0.5f);
                Fx fx = new Fx("boss_explosion", new int[] {0, 1, 2, 3, 4, 5}, 4, 24, cx + ox, cy + oy);
                fx.delay = i * 5;
                effects.add(fx);
            }
            return;
        }
        shell.sfx(Sounds.SFX_BREAK);
        effects.add(new Fx("explosion", new int[] {0, 1, 2, 3, 4}, 5, 25, cx, cy));
        // Released animal: falls out (frame 2) then flaps (0/1) off up and to the right.
        Fx flicky = new Fx("flicky", new int[] {2, 2, 2, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1},
                4, 84, cx, cy);
        flicky.delay = 10;
        flicky.vx = 1.6f;
        flicky.vy = -1.1f;
        effects.add(flicky);
    }

    private void popup(String text, int color, float x, float y, int life, int scale) {
        popups.add(new Popup(text, color, x, y, life, scale));
    }

    private void showBanner(String text, int ticks) {
        banner = text;
        bannerTicks = ticks;
    }

    private void showMessage(String text) {
        message = text;
        messageTicks = 70;
    }

    /** Screen point above a creature's head for popups. */
    private float[] anchor(Creature c) {
        if (c.isPlayer()) {
            return new float[] {PLAYER_X, feet(PLAYER_X) - 48};
        }
        EnemyVisuals.Box box = boxes.get(c);
        if (box == null) {
            int i = shown.indexOf(c);
            return new float[] {enemyX(i < 0 ? 0 : i), feet(enemyX(i < 0 ? 0 : i)) - 40};
        }
        return new float[] {box.centerX(), box.y() - 4};
    }

    // ------------------------------------------------------------------ input

    /** Enemy centres spread over the right of the screen, leaving room for wide badniks. */
    private int enemyX(int index) {
        int n = Math.max(1, shown.size());
        int spacing = Math.min(90, 200 / n);
        int centre = 292;
        return centre - spacing * (n - 1) / 2 + index * spacing;
    }

    private int handX(int index, int count, int width) {
        int spacing = Math.min(CardRenderer.SMALL_W + 4, 250 / Math.max(1, count));
        int total = spacing * (count - 1) + CardRenderer.SMALL_W;
        return (width - total) / 2 + index * spacing;
    }

    private void layout(Shell shell) {
        spots.clear();
        int w = shell.width();
        if (selected != null || targetingPotion >= 0) {
            for (int i = 0; i < shown.size(); i++) {
                Enemy e = shown.get(i);
                EnemyVisuals.Box box = boxes.get(e);
                if (e.isActive() && box != null) {
                    spots.add("enemy" + i, box.x(), box.y(), box.w(), box.h());
                }
            }
            return;
        }
        if (potionMenu >= 0) {
            int px = RunScreen.potionSlotX(shell, potionMenu);
            spots.add("puse", px, RunScreen.TOP_BAR + 2, 40, 12);
            spots.add("ptoss", px, RunScreen.TOP_BAR + 15, 40, 12);
            return;
        }
        for (int i = 0; i < hand.size(); i++) {
            spots.add("hand" + i, handX(i, hand.size(), w), HAND_Y, CardRenderer.SMALL_W, CardRenderer.SMALL_H);
        }
        spots.add("end", w - 62, HAND_Y + 2, 56, 16);
        spots.add("draw", 6, HAND_Y + 30, 22, 22);
        spots.add("discard", w - 30, HAND_Y + 30, 22, 22);
        if (exhaustCount > 0) {
            spots.add("exhaust", w - 30, HAND_Y + 18, 22, 12);
        }
        var state = shell.run.state();
        for (int i = 0; i < state.potionSlots(); i++) {
            if (state.potionAt(i) != null) {
                spots.add("potion" + i, RunScreen.potionSlotX(shell, i), 1, 12, 12);
            }
        }
    }

    private void updateTurn(Shell shell, RunScreen screen) {
        var in = shell.in;
        layout(shell);
        if (selected != null || targetingPotion >= 0) {
            String picked = spots.update(in);
            if (in.back || in.mouse.rightPressed()) {
                selected = null;
                targetingPotion = -1;
                shell.sfx(Sounds.SFX_SWITCH);
                return;
            }
            if (picked != null && picked.startsWith("enemy")) {
                Enemy target = shown.get(Integer.parseInt(picked.substring(5)));
                if (targetingPotion >= 0) {
                    combat.usePotion(targetingPotion, target);
                    targetingPotion = -1;
                } else {
                    Card card = selected;
                    selected = null;
                    combat.playCard(card, target);
                }
            }
            return;
        }
        if (potionMenu >= 0) {
            String picked = spots.update(in);
            if (in.back || in.mouse.rightPressed()) {
                potionMenu = -1;
                return;
            }
            if (picked != null) {
                usePotionChoice(shell, picked);
            }
            return;
        }
        if (in.endTurn) {
            endTurn(shell);
            return;
        }
        if (in.potionKey >= 0 && shell.run.state().potionAt(in.potionKey) != null) {
            potionMenu = in.potionKey;
            return;
        }
        String before = spots.focused();
        String picked = spots.update(in);
        if (before != null && !before.equals(spots.focused()) && spots.focused() != null
                && spots.focused().startsWith("hand")) {
            shell.sfx(Sounds.SFX_CURSOR);
        }
        if (picked == null) {
            return;
        }
        switch (picked) {
            case "end" -> endTurn(shell);
            case "draw" -> screen.openDeck(shell, "DRAW PILE", sortedDrawPile());
            case "discard" -> screen.openDeck(shell, "DISCARD PILE", combat.player().discardPile());
            case "exhaust" -> screen.openDeck(shell, "EXHAUSTED", combat.player().exhaustPile());
            default -> {
                if (picked.startsWith("potion")) {
                    potionMenu = Integer.parseInt(picked.substring(6));
                } else if (picked.startsWith("hand")) {
                    selectCard(shell, hand.get(Integer.parseInt(picked.substring(4))));
                }
            }
        }
    }

    /** The draw pile in a sorted order, so viewing it reveals no draw order (as in Slay the Spire). */
    private List<Card> sortedDrawPile() {
        List<Card> list = new ArrayList<>(combat.player().drawPile());
        list.sort((a, b) -> a.name().compareTo(b.name()));
        return list;
    }

    private void selectCard(Shell shell, Card card) {
        String why = combat.whyUnplayable(card);
        if (why != null) {
            showMessage(why.toUpperCase());
            shell.sfx(Sounds.SFX_ERROR);
            return;
        }
        if (CardTarget.needsChoice(card.target())) {
            if (combat.activeEnemies().size() == 1) {
                combat.playCard(card, combat.activeEnemies().get(0));
                return;
            }
            selected = card;
            shell.sfx(Sounds.SFX_CURSOR);
            return;
        }
        combat.playCard(card, null);
    }

    private void usePotionChoice(Shell shell, String picked) {
        int slot = potionMenu;
        potionMenu = -1;
        PotionDef potion = shell.run.state().potionAt(slot);
        if (potion == null) {
            return;
        }
        if (picked.equals("ptoss")) {
            shell.run.state().removePotion(slot);
            shell.sfx(Sounds.SFX_SWITCH);
            return;
        }
        if (potion.target().equals(PotionDef.AUTO)) {
            showMessage("THIS MONITOR BREAKS ITSELF WHEN NEEDED.");
            shell.sfx(Sounds.SFX_ERROR);
            return;
        }
        if (CardTarget.needsChoice(potion.target())) {
            if (combat.activeEnemies().size() == 1) {
                combat.usePotion(slot, combat.activeEnemies().get(0));
            } else {
                targetingPotion = slot;
            }
            return;
        }
        combat.usePotion(slot, null);
    }

    private void endTurn(Shell shell) {
        selected = null;
        if (combat.endTurn()) {
            shell.sfx(Sounds.SFX_DASH);
            shell.run.state();
        }
    }

    // ------------------------------------------------------------------ choices

    private void updateChoice(Shell shell) {
        ChoiceRequest request = combat.pendingChoice();
        List<Card> options = combat.pendingOptions();
        spots.clear();
        int w = shell.width();
        if (request.fromHand()) {
            for (int i = 0; i < hand.size(); i++) {
                if (options.contains(hand.get(i))) {
                    spots.add("opt" + options.indexOf(hand.get(i)), handX(i, hand.size(), w), HAND_Y,
                            CardRenderer.SMALL_W, CardRenderer.SMALL_H);
                }
            }
        } else if (options.size() > 4) {
            // Long piles (draw, discard, exhaust) scroll in a small-card grid.
            CardGrid g = choiceGrid(options);
            g.layout(spots, shell);
            if (g.scrollForMove(shell, spots)) {
                spots.clear();
                g.layout(spots, shell);
            }
        } else {
            int n = options.size();
            int gap = 8;
            int total = n * CardRenderer.BIG_W + (n - 1) * gap;
            for (int i = 0; i < n; i++) {
                spots.add("opt" + i, (w - total) / 2 + i * (CardRenderer.BIG_W + gap), 44, CardRenderer.BIG_W,
                        CardRenderer.BIG_H);
            }
        }
        boolean multi = request.max() > 1 || request.min() == 0;
        boolean grid = !request.fromHand() && options.size() > 4;
        if (multi) {
            boolean ready = choicePicks.size() >= Math.min(request.min(), options.size());
            if (grid) {
                spots.add("confirm", w - 100, shell.height() - 22, 86, 15, ready);
            } else {
                spots.add("confirm", w - 74, HAND_Y - 18, 68, 14, ready);
            }
        }
        String picked = spots.update(shell.in);
        if (grid) {
            choiceGrid(options).wheel(shell);
        }
        if (picked == null) {
            return;
        }
        if (picked.equals("confirm")) {
            if (combat.resolveChoice(new ArrayList<>(choicePicks))) {
                choicePicks.clear();
            }
            return;
        }
        Card card = options.get(Integer.parseInt(picked.substring(picked.startsWith("card") ? 4 : 3)));
        if (!multi) {
            combat.resolveChoice(List.of(card));
            return;
        }
        if (!choicePicks.remove(card) && choicePicks.size() < request.max()) {
            choicePicks.add(card);
        }
        shell.sfx(Sounds.SFX_CURSOR);
    }

    /** The scrolling grid for a long pile choice, kept while the same options are on offer. */
    private CardGrid choiceGrid(List<Card> options) {
        if (choiceGrid == null || !choiceGridOptions.equals(options)) {
            choiceGridOptions = List.copyOf(options);
            choiceGrid = new CardGrid(choiceGridOptions, 50, 2, 5);
        }
        return choiceGrid;
    }

    // ------------------------------------------------------------------ draw

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        int w = shell.width();
        int h = shell.height();
        int shake = shell.shakeOffset();
        stage = LevelStages.draw(shell, c, GROUND);
        // Shade the ground behind the hand so the cards and counters read over the level.
        Gfx.gradient(c, 0, HAND_Y - 20, w, h - HAND_Y + 20, 0x00000010, 0xA0000010);
        drawPlayer(shell, screen, c, shake);
        boxes.clear();
        for (int i = 0; i < shown.size(); i++) {
            drawEnemy(shell, screen, c, shown.get(i), i, shake);
        }
        for (Fx fx : effects) {
            if (fx.delay > 0) {
                continue;
            }
            int frame = fx.frames[Math.min(fx.frames.length - 1, fx.age / fx.frameTicks)];
            Poses.centre(c, shell.art.romFrame(fx.key, frame), fx.x + shake, fx.y,
                    com.openggf.mods.scene.SceneDraw.plain());
        }
        for (Popup p : popups) {
            float t = p.age / (float) p.life;
            int color = Colors.alpha(p.color, t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f);
            shell.font.drawOutlined(c, p.text, Math.round(p.x - shell.font.width(p.text) * p.scale / 2f),
                    Math.round(p.y), color, p.scale);
        }
        drawBottom(shell, screen, c);
        if (potionMenu >= 0) {
            drawPotionMenu(shell, c);
        }
        if (combat.hasPendingChoice() && !replaying()) {
            drawChoice(shell, screen, c);
        }
        if (selected != null || targetingPotion >= 0) {
            String hint = "CHOOSE A TARGET  (B TO CANCEL)";
            shell.font.drawOutlined(c, hint, (w - shell.font.width(hint)) / 2, RunScreen.HUD_HEIGHT + 4, Colors.GOLD, 1);
        }
        if (bannerTicks > 0 && banner != null) {
            float t = Math.min(1f, bannerTicks / 10f);
            int scale = shell.font.width(banner) * 3 + 24 <= w ? 3 : 2;
            int bw = shell.font.width(banner) * scale + 24;
            int bx = (w - bw) / 2;
            int by = 70;
            c.fill(0, by - 6, w, 27, Colors.alpha(0xFF000020, 0.7f * t));
            c.fill(0, by - 6, w, 1, Colors.alpha(Colors.GOLD, t));
            c.fill(0, by + 20, w, 1, Colors.alpha(Colors.GOLD, t));
            shell.font.drawOutlined(c, banner, bx + 12, by + (3 - scale) * 3, Colors.alpha(Colors.GOLD, t), scale);
        }
        if (messageTicks > 0 && message != null) {
            float t = Math.min(1f, messageTicks / 15f);
            shell.font.drawOutlined(c, message, (w - shell.font.width(message)) / 2, HAND_Y - 26,
                    Colors.alpha(Colors.WHITE, t), 1);
        }
    }

    private void drawPlayer(Shell shell, RunScreen screen, SceneCanvas c, int shake) {
        var player = combat.player();
        String id = shell.run.state().character().id();
        int lungeTicks = lunge.getOrDefault(player, 0);
        float lungeOffset = lungeTicks > 0 ? (float) Math.sin((14 - lungeTicks) / 14f * Math.PI) * 36 : 0;
        int anim;
        long animTicks = shell.ticks;
        if (stat(player).hp <= 0) {
            anim = Poses.DEATH;
        } else if (hurt.containsKey(player)) {
            anim = Poses.HURT;
        } else if (lungeTicks > 0) {
            anim = Poses.ROLL;
        } else if (combat.won() && endTimer > 0) {
            anim = Poses.VICTORY;
            animTicks = endTimer;
        } else {
            anim = Poses.WAIT;
        }
        int x = PLAYER_X + Math.round(lungeOffset) + shake;
        int ground = feet(PLAYER_X);
        float in = arrival(-1);
        if (in < 1f) {
            // Running in from the left edge, slowing to the spot.
            int runX = Math.round(-24 + (PLAYER_X + 24) * Ease.outCubic(in));
            Poses.hero(shell, c, id, Poses.RUN, shell.ticks, runX, feet(Math.max(0, runX)), SceneDraw.plain());
            return;
        }
        c.fill(x - 14, ground - 2, 28, 3, 0x50000000);
        float f = flash.getOrDefault(player, 0) / 6f;
        Poses.hero(shell, c, id, anim, animTicks, x, ground,
                SceneDraw.plain().withFlash(f > 0 ? Colors.alpha(Colors.WHITE, f) : 0));
        Shown st = stat(player);
        if (st.block > 0) {
            c.draw(shell.art.icon("ui_block"), PLAYER_X - 30, ground + 3);
            shell.font.drawOutlined(c, Integer.toString(st.block), PLAYER_X - 27 - shell.font.width(
                    Integer.toString(st.block)) / 2 + 3, ground + 4, Colors.WHITE, 1);
        }
        Gfx.hpBar(c, shell.font, PLAYER_X - 22, ground + 4, 50, st.hp, st.maxHp, st.block, ghostHp(player, st.hp));
        drawPowers(shell, c, player, PLAYER_X - 22, ground + 13);
        statTips(shell, screen, null, PLAYER_X - 22, ground, st);
    }

    /** HP and Block tips while the pointer is over a creature's bar or Block badge ({@code enemy} null for the hero). */
    private void statTips(Shell shell, RunScreen screen, Enemy enemy, int barX, int ground, Shown st) {
        var mouse = shell.in.mouse;
        if (st.block > 0 && mouse.over(barX - 8, ground + 3, 9, 9)) {
            screen.tooltip("BLOCK", "Stops the next " + st.block + " damage. Removed at the start of "
                    + (enemy == null ? "your" : "its") + " next turn.", barX - 8, ground + 24);
        } else if (mouse.over(barX - 1, ground + 3, 52, 7)) {
            String body = enemy == null
                    ? "Your health. The run ends if it reaches 0."
                    : enemy.name() + " is beaten when its HP reaches 0.";
            screen.tooltip("HP " + Math.max(0, st.hp) + "/" + st.maxHp, body + (st.block > 0 ? " Block is used up first." : ""),
                    barX, ground + 24);
        }
    }

    private void drawEnemy(Shell shell, RunScreen screen, SceneCanvas c, Enemy enemy, int index, int shake) {
        Integer deathTicks = dying.get(enemy);
        float alpha = deathTicks == null ? 1f : Math.max(0f, 1f - deathTicks / 18f);
        if (alpha <= 0f) {
            return;
        }
        int lungeTicks = lunge.getOrDefault(enemy, 0);
        float lungeOffset = lungeTicks > 0 ? -(float) Math.sin((14 - lungeTicks) / 14f * Math.PI) * 30 : 0;
        int x = enemyX(index) + Math.round(lungeOffset) + shake;
        float f = flash.getOrDefault(enemy, 0) / 6f;
        if (deathTicks != null) {
            f = 1f;
        }
        int ground = feet(enemyX(index));
        float in = arrival(index);
        if (in < 1f) {
            // Arriving: a boss drops in from above, everything else comes in from the right.
            float rest = 1f - Ease.outCubic(in);
            if (bossFight) {
                EnemyVisuals.draw(shell, c, enemy, x, Math.round(ground - rest * (ground + 40)), shell.ticks, 0f, 1f);
            } else {
                EnemyVisuals.draw(shell, c, enemy, x + Math.round(rest * (shell.width() + 60 - x)), ground,
                        shell.ticks + index * 17L, 0f, 1f);
            }
            return;
        }
        EnemyVisuals.Box box = EnemyVisuals.draw(shell, c, enemy, x, ground, shell.ticks + index * 17L, f, alpha);
        boxes.put(enemy, box);
        if (deathTicks != null) {
            return;
        }
        Shown st = stat(enemy);
        Gfx.hpBar(c, shell.font, x - 25, ground + 4, 50, st.hp, st.maxHp, st.block, ghostHp(enemy, st.hp));
        if (st.block > 0) {
            c.draw(shell.art.icon("ui_block"), x - 33, ground + 3);
            shell.font.drawOutlined(c, Integer.toString(st.block), x - 30 - shell.font.width(Integer.toString(st.block)) / 2
                    + 3, ground + 4, Colors.WHITE, 1);
        }
        drawPowers(shell, c, enemy, x - 25, ground + 13);
        if (!acted.contains(enemy)) {
            drawIntent(shell, c, intents.get(enemy), box.centerX(), Math.max(RunScreen.HUD_HEIGHT + 2, box.y() - 16));
        }
        String id = "enemy" + index;
        boolean targeted = spots.isFocused(id) && (selected != null || targetingPotion >= 0);
        if (targeted) {
            Gfx.focusFrame(c, box.x(), box.y(), box.w(), box.h(), shell.ticks);
            if (selected != null) {
                screen.cards.drawBig(c, selected, combat, enemy, 8, 36, false);
            }
        }
        if (shell.in.mouse.over(box.x(), box.y(), box.w(), box.h()) && selected == null) {
            screen.tooltip(enemy.name().toUpperCase(), intentText(intents.get(enemy)), box.x(), ground + 24);
        }
        statTips(shell, screen, enemy, x - 25, ground, st);
    }

    private static String intentText(Intent intent) {
        if (intent == null) {
            return "";
        }
        String kind = intent.kind();
        if (IntentKind.isAttack(kind)) {
            String dmg = intent.hits() > 1 ? intent.damage() + "x" + intent.hits() : Integer.toString(intent.damage());
            return "Intends to attack for " + dmg + " damage" + (kind.equals(IntentKind.ATTACK) ? "." : " and more.");
        }
        return switch (kind) {
            case IntentKind.DEFEND -> "Intends to defend.";
            case IntentKind.BUFF, IntentKind.DEFEND_BUFF -> "Intends to strengthen itself.";
            case IntentKind.DEBUFF, IntentKind.DEFEND_DEBUFF -> "Intends to weaken you.";
            case IntentKind.STRONG_DEBUFF -> "Intends to cripple you.";
            case IntentKind.SLEEP -> "Sleeping.";
            case IntentKind.STUN -> "Stunned.";
            case IntentKind.ESCAPE -> "Intends to flee.";
            default -> "Its intent is unknown.";
        };
    }

    private void drawIntent(Shell shell, SceneCanvas c, Intent intent, int x, int y) {
        if (intent == null) {
            return;
        }
        String kind = intent.kind();
        String icon = switch (kind) {
            case IntentKind.ATTACK, IntentKind.ATTACK_BUFF, IntentKind.ATTACK_DEBUFF, IntentKind.ATTACK_DEFEND ->
                    "intent_attack";
            case IntentKind.DEFEND, IntentKind.DEFEND_BUFF, IntentKind.DEFEND_DEBUFF -> "intent_defend";
            case IntentKind.BUFF -> "intent_buff";
            case IntentKind.DEBUFF -> "intent_debuff";
            case IntentKind.STRONG_DEBUFF -> "intent_strong_debuff";
            case IntentKind.SLEEP -> "intent_sleep";
            case IntentKind.STUN -> "intent_stun";
            case IntentKind.ESCAPE -> "intent_escape";
            default -> "intent_unknown";
        };
        float bob = (float) Math.sin(shell.ticks * 0.1) * 1.5f;
        SceneImage image = shell.art.icon(icon);
        int iy = Math.round(y + bob);
        c.draw(image, x - image.width() / 2f, iy);
        if (IntentKind.isAttack(kind)) {
            String dmg = intent.hits() > 1 ? intent.damage() + "X" + intent.hits() : Integer.toString(intent.damage());
            shell.font.drawOutlined(c, dmg, x + 5, iy + 7, Colors.WHITE, 1);
        }
        if (kind.equals(IntentKind.ATTACK_BUFF) || kind.equals(IntentKind.DEFEND_BUFF)) {
            c.draw(shell.art.icon("pow_buff"), x - 14, iy + 4);
        } else if (kind.equals(IntentKind.ATTACK_DEBUFF) || kind.equals(IntentKind.DEFEND_DEBUFF)) {
            c.draw(shell.art.icon("pow_debuff"), x - 14, iy + 4);
        } else if (kind.equals(IntentKind.ATTACK_DEFEND)) {
            c.draw(shell.art.icon("ui_block"), x - 14, iy + 4);
        }
    }

    private void drawPowers(Shell shell, SceneCanvas c, Creature creature, int x, int y) {
        Map<String, Integer> p = powers.get(creature);
        if (p == null) {
            return;
        }
        int px = x;
        for (Map.Entry<String, Integer> e : p.entrySet()) {
            Power live = creature.power(e.getKey());
            SceneImage icon = shell.art.icon(PowerIcons.icon(e.getKey(), live));
            c.draw(icon, px, y);
            if (live == null || live.showsAmount()) {
                String n = Integer.toString(e.getValue());
                shell.font.drawOutlined(c, n, px + 9 - shell.font.width(n) / 2, y + 5,
                        e.getValue() < 0 ? Colors.TEXT_BAD : Colors.WHITE, 1);
            }
            if (live != null && shell.in.mouse.over(px, y, 9, 9)) {
                tooltipPower(shell, live, px, y);
            }
            px += 11;
        }
    }

    private RunScreen tooltipScreen;
    /** Where this fight stands in the act's level, from the last draw; null before it or without ROM art. */
    private LevelStages.Placement stage;

    /** The energy the emerald last showed, and its pulse when that changed (grows on a gain, dips on a spend). */
    private static final int ENERGY_PULSE = 12;
    private int shownEnergy = -1;
    private int energyPulse;
    private boolean energyGained;

    /** Each creature's draining "ghost" HP and how long it holds before draining. */
    private final Map<Creature, float[]> ghosts = new java.util.HashMap<>();
    /** Ghost HP holds this many frames after a hit, then drains a fraction a frame. */
    private static final int GHOST_HOLD = 16;

    /** The pale stretch behind a creature's HP bar (advanced each frame by {@link #animateReadouts}). */
    private float ghostHp(Creature creature, int hp) {
        float[] g = ghosts.get(creature);
        return g == null ? hp : g[0];
    }

    /**
     * One frame of the readouts' own animation: each HP ghost holds after a hit, then drains to
     * the HP; the energy emerald pulses when the shown energy changes.
     */
    private void animateReadouts() {
        for (Creature creature : creatures()) {
            int hp = stat(creature).hp;
            float[] g = ghosts.computeIfAbsent(creature, k -> new float[] {hp, 0});
            if (hp >= g[0]) {
                g[0] = hp;
                g[1] = 0;
            } else if (g[1] < GHOST_HOLD) {
                g[1]++;
            } else {
                g[0] = Math.max(hp, g[0] - Math.max(0.25f, (g[0] - hp) * 0.12f));
            }
        }
        if (energy != shownEnergy) {
            energyPulse = shownEnergy < 0 ? 0 : ENERGY_PULSE;
            energyGained = energy > shownEnergy;
            shownEnergy = energy;
        } else if (energyPulse > 0) {
            energyPulse--;
        }
    }

    /** The screen row a creature at {@code screenX} stands on. */
    private int feet(int screenX) {
        return LevelStages.feet(stage, screenX, GROUND);
    }

    private void tooltipPower(Shell shell, Power power, int x, int y) {
        if (tooltipScreen != null) {
            tooltipScreen.tooltip(power.name().toUpperCase(), power.description(), x, y + 12);
        }
    }

    private void drawBottom(Shell shell, RunScreen screen, SceneCanvas c) {
        tooltipScreen = screen;
        int w = shell.width();
        SmallFont f = shell.font;
        // Energy: the green Chaos Emerald (the AIZ intro's emerald art), pulsing when energy
        // changes and dull when it runs out.
        int ex = 6;
        int ey = HAND_Y - 2;
        int max = combat.player().energyPerTurn();
        float pulse = energyPulse / (float) ENERGY_PULSE;
        SceneSprite gem = shell.art.romFrame("intro_emeralds", 0);
        if (gem != null) {
            // A dark round backing with a thin gold rim keeps the emerald readable on any zone.
            for (int dy = -14; dy <= 14; dy++) {
                int half = (int) Math.round(Math.sqrt(14 * 14 - dy * dy));
                c.fill(ex + 13 - half, ey + 13 + dy, half * 2 + 1, 1, Math.abs(dy) == 14 ? EventArt.GOLD_DARK : 0xE0081028);
                c.fill(ex + 13 - half, ey + 13 + dy, 1, 1, EventArt.GOLD_DARK);
                c.fill(ex + 13 + half, ey + 13 + dy, 1, 1, EventArt.GOLD_DARK);
            }
            float scale = 3f + (energyGained ? 0.6f : -0.4f) * pulse;
            Poses.centre(c, gem, ex + 13, ey + 13, SceneDraw.plain().withScale(scale)
                    .withTint(energy > 0 ? Colors.WHITE : 0xFF686868)
                    .withFlash(energyGained && pulse > 0 ? Colors.alpha(Colors.WHITE, pulse * 0.8f) : 0));
        } else {
            c.fill(ex + 2, ey, 22, 26, Colors.BLACK);
            c.fill(ex, ey + 2, 26, 22, Colors.BLACK);
            c.fill(ex + 2, ey + 2, 22, 22, energy > 0 ? 0xFF24DA6C : 0xFF305040);
        }
        String en = energy + "/" + max;
        f.drawOutlined(c, en, ex + 13 - f.width(en) / 2, ey + 11, Colors.WHITE, 1);
        var mouse = shell.in.mouse;
        if (mouse.over(ex, ey, 26, 26)) {
            screen.tooltipAbove("ENERGY", "Playing a card costs its number in Energy. You get " + max
                    + " at the start of each turn; Energy left over is lost.", ex, ey);
        }
        // Draw and discard piles.
        c.draw(shell.art.icon("ui_draw_pile"), 8, HAND_Y + 32);
        f.drawOutlined(c, Integer.toString(drawCount), 20, HAND_Y + 42, Colors.WHITE, 1);
        c.draw(shell.art.icon("ui_discard_pile"), w - 26, HAND_Y + 32);
        f.drawOutlined(c, Integer.toString(discardCount), w - 14, HAND_Y + 42, Colors.WHITE, 1);
        if (exhaustCount > 0) {
            c.draw(shell.art.icon("ui_exhaust_pile"), w - 26, HAND_Y + 18);
            f.drawOutlined(c, Integer.toString(exhaustCount), w - 14, HAND_Y + 26, Colors.WHITE, 1);
        }
        if (mouse.over(6, HAND_Y + 30, 22, 22)) {
            screen.tooltipAbove("DRAW PILE", "Each turn's hand is drawn from here. When it runs out, the discard "
                    + "pile is shuffled back in. Click to view.", 6, HAND_Y + 30);
        } else if (mouse.over(w - 30, HAND_Y + 30, 22, 22)) {
            screen.tooltipAbove("DISCARD PILE", "Cards you play or discard, and your hand at the end of each turn. "
                    + "Click to view.", w - 150, HAND_Y + 30);
        } else if (exhaustCount > 0 && mouse.over(w - 30, HAND_Y + 18, 22, 12)) {
            screen.tooltipAbove("EXHAUSTED", "Cards Exhausted this combat, out of play until it ends. Click to view.",
                    w - 150, HAND_Y + 18);
        } else if (mouse.over(w - 62, HAND_Y + 2, 56, 16)) {
            screen.tooltipAbove("END TURN (E)", "Discard your hand, except cards that Retain, and let the enemies act.",
                    w - 150, HAND_Y + 2);
        }
        // End turn button.
        boolean myTurn = combat.phase().equals(Combat.PLAYER_TURN) && !replaying() && !combat.isOver();
        Gfx.button(c, f, "END TURN", w - 62, HAND_Y + 2, 56, 16, spots.isFocused("end"), myTurn, shell.ticks);
        // Hand.
        boolean choosingFromHand = combat.hasPendingChoice() && combat.pendingChoice().fromHand() && !replaying();
        List<Card> options = choosingFromHand ? combat.pendingOptions() : List.of();
        Card focusCard = null;
        int focusX = 0;
        for (int i = 0; i < hand.size(); i++) {
            Card card = hand.get(i);
            float[] pos = handPosition(card, w);
            int x = Math.round(pos[0]);
            boolean focus = spots.isFocused("hand" + i)
                    || (choosingFromHand && options.contains(card) && spots.isFocused("opt" + options.indexOf(card)));
            boolean picked = choicePicks.contains(card) || card == selected;
            int y = Math.round(pos[1]) + (focus ? -8 : 0) + (picked ? -14 : 0);
            boolean playable = myTurn && combat.canPlay(card);
            boolean dim = choosingFromHand && !options.contains(card);
            screen.cards.drawSmall(c, card, combat, x, y, playable, dim);
            if (focus) {
                focusCard = card;
                focusX = x;
            }
        }
        for (Flight fl : flights) {
            // First half: to the middle point; second half: on to the pile.
            float t = fl.age / (float) fl.life;
            float fx;
            float fy;
            if (t < 0.5f) {
                float k = Ease.outCubic(t * 2);
                fx = fl.x0 + (fl.mx - fl.x0) * k;
                fy = fl.y0 + (fl.my - fl.y0) * k;
            } else {
                float k = Ease.inCubic((t - 0.5f) * 2);
                fx = fl.mx + (fl.x1 - fl.mx) * k;
                fy = fl.my + (fl.y1 - fl.my) * k;
            }
            screen.cards.drawSmall(c, fl.card, combat, Math.round(fx), Math.round(fy), true, false);
        }
        if (focusCard != null && selected == null) {
            int bx = Math.max(4, Math.min(w - CardRenderer.BIG_W - 4, focusX + CardRenderer.SMALL_W / 2
                    - CardRenderer.BIG_W / 2));
            screen.cards.drawBig(c, focusCard, combat, null, bx, HAND_Y - CardRenderer.BIG_H - 12, false);
            shell.cardTips(focusCard, bx, HAND_Y - CardRenderer.BIG_H - 12, CardRenderer.BIG_W);
        }
    }

    private void drawPotionMenu(Shell shell, SceneCanvas c) {
        int px = RunScreen.potionSlotX(shell, potionMenu);
        PotionDef potion = shell.run.state().potionAt(potionMenu);
        if (potion == null) {
            return;
        }
        Gfx.panel(c, px - 4, RunScreen.TOP_BAR, 120, 44);
        Gfx.button(c, shell.font, "USE", px, RunScreen.TOP_BAR + 2, 40, 12, spots.isFocused("puse"), true, shell.ticks);
        Gfx.button(c, shell.font, "DISCARD", px, RunScreen.TOP_BAR + 15, 40, 12, spots.isFocused("ptoss"), true,
                shell.ticks);
        var lines = shell.font.wrap(potion.describe(shell.run.state().potionPotency(potion)), 70);
        int ly = RunScreen.TOP_BAR + 3;
        for (String line : lines) {
            shell.font.drawShadowed(c, line, px + 44, ly, Colors.TEXT);
            ly += SmallFont.LINE;
        }
    }

    private void drawChoice(Shell shell, RunScreen screen, SceneCanvas c) {
        ChoiceRequest request = combat.pendingChoice();
        int w = shell.width();
        String prompt = request.prompt().toUpperCase();
        if (!request.fromHand()) {
            c.fill(0, RunScreen.HUD_HEIGHT, w, shell.height() - RunScreen.HUD_HEIGHT, 0xC0000010);
            List<Card> options = combat.pendingOptions();
            if (options.size() > 4) {
                CardGrid grid = choiceGrid(options);
                grid.draw(shell, screen.cards, c, spots, choicePicks);
                Card focus = grid.focusedCard(spots);
                if (focus != null) {
                    screen.cards.drawBig(c, focus, combat, null, w - CardRenderer.BIG_W - 6, 50, false);
                    shell.cardTips(focus, w - CardRenderer.BIG_W - 6, 50, CardRenderer.BIG_W);
                }
                options = List.of();
            }
            for (int i = 0; i < options.size() && i < 4; i++) {
                Hotspots.Spot s = spots.spot("opt" + i);
                if (s == null) {
                    continue;
                }
                boolean focus = spots.isFocused(s.id());
                screen.cards.drawBig(c, options.get(i), combat, null, s.x(), s.y() - (focus ? 3 : 0),
                        false);
                if (choicePicks.contains(options.get(i))) {
                    Gfx.outline(c, s.x() - 2, s.y() - 5, s.w() + 4, s.h() + 4, Colors.TEXT_GOOD);
                }
                if (focus) {
                    Gfx.focusFrame(c, s.x(), s.y() - 3, s.w(), s.h(), shell.ticks);
                    shell.cardTips(options.get(i), s.x(), s.y() - 3, s.w());
                }
            }
        }
        shell.font.drawOutlined(c, prompt, (w - shell.font.width(prompt)) / 2, RunScreen.HUD_HEIGHT + 6, Colors.GOLD, 1);
        Hotspots.Spot confirm = spots.spot("confirm");
        if (confirm != null) {
            Gfx.button(c, shell.font, "CONFIRM", confirm.x(), confirm.y(), confirm.w(), confirm.h(),
                    spots.isFocused("confirm"), confirm.enabled(), shell.ticks);
        }
    }
}
