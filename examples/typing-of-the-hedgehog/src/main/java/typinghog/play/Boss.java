package typinghog.play;

import com.openggf.game.GameServices;
import java.util.ArrayList;
import java.util.List;
import typinghog.audio.VoiceLine;
import typinghog.core.Prompt;
import typinghog.core.TypingField;

/** ROM-art boss puppets with typing telegraphs, source-backed attacks and strike windows. */
public final class Boss {
    public enum Style { WEAK_POINT, TALK_BACK, QUIZ }

    static final int HITS = 8;
    enum Phase { ENTRY, TELEGRAPH, ATTACK, OPEN, STRIKE, RECOVER }
    private final BossMotion motion;
    private Phase phase = Phase.ENTRY;
    private boolean dodged;
    private boolean attackHit;
    private int dodgeId = -1;
    private int penalty;
    static final int ENTRY_ROWS = 90;
    static final int RECOVER_ROWS = 12;
    /** Rows he explodes in place once beaten before fleeing. */
    static final int EXPLODE_ROWS = 120;
    static final int FLEE_ROWS = 80;
    static final int LAUGH_ROWS = 64;

    private final Style style;
    private final BossRig rig;
    /** Per rig part, its sprites (chain links first); they live on {@link #front} or {@link #behind}. */
    private final List<List<Stagehand.Sprite>> sprites = new ArrayList<>();
    private Stagehand.Visual front;
    private Stagehand.Visual behind;
    private boolean spawned;
    private boolean facingRight;
    /** +1 when the boss holds the screen's right side (Sonic on the left), -1 for the left. */
    private int side = 1;
    private BossRig.Mood mood = BossRig.Mood.IDLE;
    private int moodRow;
    private int hits;
    private int row;
    private int stateRow;
    private int windowRows;
    private int targetId = -1;
    private int[] answerIds = new int[0];
    private int correctId = -1;
    private String question = "";
    private int x;
    private int y;
    private int flash;
    private int laughRows;
    private int recoil;
    private boolean defeated;
    private int defeatRows;

    private Boss(Style style, BossRig rig, BossMotion.Kind kind) {
        this.style = style;
        this.rig = rig;
        motion = new BossMotion(kind);
    }

    /** The act's boss: style by zone position in the chapter, look from the zone's rig. */
    static Boss forAct(Course course) {
        var intent = course.intent();
        Style style = switch (Math.floorMod(intent.zone() * 7 + intent.act(), 3)) {
            case 0 -> Style.WEAK_POINT;
            case 1 -> Style.TALK_BACK;
            default -> Style.QUIZ;
        };
        BossRig rig = course.assets().cast(intent.game()).boss(intent.zone(), intent.act());
        if (rig == null) rig = BossRig.parse("* ship eggman 0 0 *=8:0").get("*");
        BossMotion.Kind kind = switch (intent.game()) {
            case "s1" -> intent.zone() == 0 ? BossMotion.Kind.GREEN_HILL
                    : intent.zone() == 6 ? BossMotion.Kind.FINAL_ZONE : BossMotion.Kind.SHIP;
            case "s2" -> intent.zone() == 10 ? BossMotion.Kind.DEATH_EGG
                    : intent.zone() == 0 ? BossMotion.Kind.EMERALD_HILL : BossMotion.Kind.SHIP;
            default -> BossMotion.Kind.SHIP;
        };
        return new Boss(style, rig, kind);
    }

    boolean defeated() { return defeated && defeatRows > EXPLODE_ROWS + FLEE_ROWS; }
    boolean owns(int target) {
        if (target == dodgeId || target == targetId) return true;
        for (int id : answerIds) if (id == target) return true;
        return false;
    }
    Style style() { return style; }
    int hits() { return hits; }
    int hitCount() { return motion.kind == BossMotion.Kind.DEATH_EGG ? 12 : HITS; }
    String phase() { return phase.name(); }
    String attack() { return motion.attack == null ? "" : motion.attack.name(); }
    boolean dodged() { return dodged; }
    int occupiedPillar() { return motion.occupied; }
    int dodgeTarget() { return dodgeId; }
    String question() { return question; }
    int windowRows() { return windowRows; }
    int stateRow() { return stateRow; }
    int x() { return x; }
    int y() { return y; }
    BossRig.Mood mood() { return mood; }
    int correctTarget() { return dodgeId >= 0 ? dodgeId : correctId; }

    void advance(Course course, Encounter encounter) {
        row++;
        stateRow++;
        int px = course.anchorX(), py = course.anchorY();
        var camera = GameServices.camera();
        if (!spawned) {
            // The boss takes the side of the screen away from Sonic: an arena can stop him at
            // its right edge (EHZ2), where a boss from the right would face away from him.
            side = px - camera.getX() > camera.getWidth() / 2 ? -1 : 1;
            facingRight = side < 0;
            spawn(course, side > 0 ? camera.getX() + camera.getWidth() + 64 : camera.getX() - 64,
                    inView(py - rig.height()));
            course.homing().face(side > 0 ? com.openggf.physics.Direction.RIGHT : com.openggf.physics.Direction.LEFT);
        }
        if (flash > 0) flash--;
        if (laughRows > 0) laughRows--;
        if (recoil > 0) recoil--;
        if (!motionReady) {
            motion.anchor(course, side);
            motionReady = true;
        }
        if (defeated) {
            defeatRows++;
            if (defeatRows <= EXPLODE_ROWS) {
                if (defeatRows % 6 == 1) {
                    encounter.effects().blast(course, x + course.random().nextInt(48) - 24,
                            y + course.random().nextInt(40) - 20);
                    if (defeatRows % 12 == 1) course.play(Sounds.Cue.BOSS_DEFEATED);
                }
            } else {
                facingRight = side > 0;
                x += 4 * side;
                if (!rig.grounded()) y -= defeatRows % 2;
            }
        } else {
            switch (phase) {
                case ENTRY -> {
                    x += Integer.signum(motion.homeX - x) * Math.min(3, Math.abs(motion.homeX - x));
                    y = motion.homeY;
                    if (stateRow >= ENTRY_ROWS) telegraph(course);
                }
                case TELEGRAPH -> {
                    if (--windowRows <= 0) {
                        if (dodgeId >= 0) course.field().remove(dodgeId);
                        dodgeId = -1;
                        phase = Phase.ATTACK;
                        stateRow = 0;
                        if (dodged) course.homing().dodge(motion.attack == BossMotion.Attack.SENSOR_SLAM
                                ? -side * 72 : -side * 40, motionRows());
                    }
                }
                case ATTACK -> {
                    boolean done = motion.advance(course);
                    x = motion.x;
                    y = motion.y;
                    if (motion.impact && !attackHit) {
                        attackHit = true;
                        if (motion.attack == BossMotion.Attack.SENSOR_SLAM || motion.attack == BossMotion.Attack.WALK) {
                            encounter.effects().blast(course, motion.sensorX, motion.sensorY);
                        }
                        if (!dodged) { course.hitSonic(); laughRows = LAUGH_ROWS; }
                    }
                    if (done) {
                        if (motion.attack == BossMotion.Attack.PLASMA || motion.attack == BossMotion.Attack.BOMBS) {
                            recover();
                        } else {
                            phase = Phase.OPEN;
                            openWindow(course);
                        }
                    }
                }
                case OPEN -> {
                    if (penalty > 0 && --penalty == 0) {
                        for (int id : answerIds) course.field().setTypeable(id, true);
                    }
                    if (--windowRows <= 0) { closeWindow(course); recover(); }
                }
                case STRIKE -> { if (!course.homing().busy()) recover(); }
                case RECOVER -> {
                    if (motion.kind == BossMotion.Kind.FINAL_ZONE && motion.cylinderTravel > 0) {
                        // EggmanCylinder_Top/Bottom retract at $20000 (2px per row).
                        motion.cylinderTravel = Math.max(0, motion.cylinderTravel - (2 << 8));
                    }
                    boolean positioned = motion.kind != BossMotion.Kind.FINAL_ZONE || motion.cylinderTravel == 0;
                    if (motion.kind == BossMotion.Kind.EMERALD_HILL) {
                        facingRight = side > 0;
                        x += Integer.signum(motion.homeX - x) * Math.min(2, Math.abs(motion.homeX - x));
                        positioned = x == motion.homeX;
                    }
                    if (stateRow >= RECOVER_ROWS && positioned && !course.homing().busy()) telegraph(course);
                }
            }
        }
        setMood(currentMood());
        place();
    }

    private boolean motionReady;

    private void spawn(Course course, int startX, int startY) {
        spawned = true;
        x = startX;
        y = startY;
        stateRow = 0;
        // One compound visual per layer keeps the rig's file order exact. The craft sits in the
        // ROM bosses' display list 3, behind Sonic (2); back parts one list further back.
        front = course.stagehand().add(rig.parts().isEmpty() ? "eggman" : rig.parts().getFirst().artKey(), x, y);
        front.bucket = 3;
        behind = course.stagehand().add(front.artKey, x, y);
        behind.bucket = 4;
        for (BossRig.Part part : rig.parts()) {
            Stagehand.Visual layer = part.back() ? behind : front;
            List<Stagehand.Sprite> list = new ArrayList<>();
            for (int i = 0; i < part.sprites(); i++) {
                boolean link = i < spritesOfLinks(part);
                Stagehand.Sprite sprite = layer.sprite(link ? part.links().artKey() : part.artKey());
                sprite.palette = link ? part.links().palette() : part.palette();
                list.add(sprite);
            }
            sprites.add(list);
        }
        course.say(switch (style) {
            case QUIZ -> VoiceLine.ROBOTNIK_INTRO_QUIZ;
            case TALK_BACK -> VoiceLine.ROBOTNIK_TALK_BACK;
            case WEAK_POINT -> new VoiceLine[] {VoiceLine.ROBOTNIK_INTRO_A, VoiceLine.ROBOTNIK_INTRO_B,
                    VoiceLine.ROBOTNIK_INTRO_C}[course.random().nextInt(3)];
        });
    }

    private static int spritesOfLinks(BossRig.Part part) {
        return part.links() == null ? 0 : part.links().distances().length;
    }

    /** Typing durations are mod design values, not native motion timers. */
    private void telegraph(Course course) {
        closeWindow(course);
        motion.begin(course);
        facingRight = side < 0;
        x = motion.x;
        y = motion.y;
        dodged = false;
        attackHit = false;
        phase = Phase.TELEGRAPH;
        stateRow = 0;
        windowRows = (int) (240 / course.intent().difficulty().speed);
        String word = switch (motion.attack) {
            case BALL -> "duck";
            case CYLINDERS -> "clear";
            case PLASMA -> "spark";
            case WALK -> "step";
            case SENSOR_SLAM -> "evade";
            case BOMBS -> "blast";
            case PUNCH -> "fist";
            case DRILL -> "vault";
        };
        dodgeId = course.field().add(Prompt.of(word, course.intent().capitals(), false), windowRows);
    }

    private int motionRows() {
        return switch (motion.attack) {
            case BALL -> 128;
            case CYLINDERS -> 81;
            case PLASMA -> Math.max(1, (int) Math.ceil((motion.sensorY - GameServices.camera().getY() - 72) / 1.25));
            case WALK -> 80;
            case SENSOR_SLAM -> 160;
            case BOMBS -> motion.bombRows();
            case PUNCH -> 80;
            case DRILL -> motion.chargeRows();
        };
    }

    private void recover() {
        phase = Phase.RECOVER;
        stateRow = 0;
        motion.cycle++;
    }

    /** Keeps a hovering boss inside the view, below the HUD and its hit pips. */
    private static int inView(int wantY) {
        var camera = GameServices.camera();
        return Math.max(camera.getY() + 64, Math.min(camera.getY() + 224 - 40, wantY));
    }

    private BossRig.Mood currentMood() {
        if (defeated) return defeatRows <= EXPLODE_ROWS ? BossRig.Mood.DEFEAT : BossRig.Mood.FLEE;
        if (flash > 0) return BossRig.Mood.HIT;
        if (phase == Phase.ATTACK || phase == Phase.TELEGRAPH) return BossRig.Mood.LUNGE;
        if (laughRows > 0) return BossRig.Mood.LAUGH;
        if (targetId >= 0 || answerIds.length > 0) return BossRig.Mood.OPEN;
        return BossRig.Mood.IDLE;
    }

    private void setMood(BossRig.Mood next) {
        if (next != mood) {
            mood = next;
            moodRow = 0;
        } else {
            moodRow++;
        }
    }

    private void place() {
        int mirror = facingRight ? -1 : 1;
        // A struck craft shudders back; a beaten one shakes while it burns.
        int ox = recoil * mirror / 2 + (defeated && defeatRows <= EXPLODE_ROWS ? (defeatRows & 2) - 1 : 0);
        for (Stagehand.Visual layer : new Stagehand.Visual[] {front, behind}) {
            layer.blink = flash;
            layer.x = x;
            layer.y = y;
        }
        for (int p = 0; p < sprites.size(); p++) {
            BossRig.Part part = rig.parts().get(p);
            List<Stagehand.Sprite> parts = sprites.get(p);
            BossRig.Anim anim = part.anim(mood);
            int ax = x + ox + part.dx() * mirror, ay = y + part.dy();
            int links = spritesOfLinks(part);
            double angle = 0;
            if (part.swing() != null) {
                angle = Math.toRadians(part.swing().degrees()
                        * Math.sin(2 * Math.PI * row / Math.max(1, part.swing().periodRows())));
            }
            for (int i = 0; i < parts.size(); i++) {
                Stagehand.Sprite visual = parts.get(i);
                visual.visible = anim != null;
                visual.flipX = facingRight ^ part.flip();
                if (i < links) {
                    int d = part.links().distances()[i];
                    if (motion.kind == BossMotion.Kind.GREEN_HILL) {
                        visual.x = x + (int) Math.round(Math.cos(motion.ballRadians()) * d) * side;
                        visual.y = y + 32 + (int) Math.round(Math.sin(motion.ballRadians()) * d);
                        visual.visible = !defeated && phase != Phase.ENTRY;
                    } else {
                        visual.x = ax + (int) Math.round(Math.sin(angle) * d) * mirror;
                        visual.y = ay + (int) Math.round(Math.cos(angle) * d);
                    }
                    visual.frame = part.links().frame();
                    continue;
                }
                if (anim != null) visual.frame = anim.frame(moodRow);
                if (motion.kind == BossMotion.Kind.GREEN_HILL && part.name().equals("ball")) {
                    visual.x = motion.ballX();
                    visual.y = motion.ballY();
                    visual.visible = !defeated && phase != Phase.ENTRY;
                } else if (part.swing() != null) {
                    visual.x = ax + (int) Math.round(Math.sin(angle) * part.swing().length()) * mirror;
                    visual.y = ay + (int) Math.round(Math.cos(angle) * part.swing().length());
                } else if (part.orbit() != null) {
                    var orbit = part.orbit();
                    double a = 2 * Math.PI * (row / (double) Math.max(1, orbit.periodRows())
                            + (i - links) / (double) orbit.count());
                    visual.x = ax + (int) Math.round(Math.cos(a) * orbit.radius());
                    visual.y = ay + (int) Math.round(Math.sin(a) * orbit.radius());
                } else {
                    visual.x = ax;
                    visual.y = ay;
                }
                choreograph(part, visual, i - links);
            }
        }
    }

    private void choreograph(BossRig.Part part, Stagehand.Sprite sprite, int copy) {
        if (motion.kind == BossMotion.Kind.FINAL_ZONE) {
            if (part.name().startsWith("pillar")) {
                int pillar = Integer.parseInt(part.name().substring(6));
                boolean active = pillar == motion.occupied || pillar == motion.partner;
                sprite.x = motion.pillarX(pillar);
                sprite.y = active ? motion.pillarY(pillar)
                        : GameServices.camera().getY() + (pillar < 2 ? 272 : -80);
                sprite.frame = active ? motion.pillarFrame(pillar) : 0;
                sprite.flipY = pillar >= 2;
                sprite.visible = !defeated;
            } else if (part.name().equals("robotnik")) {
                sprite.x = x;
                sprite.y = y;
                sprite.visible = !defeated && motion.attack == BossMotion.Attack.CYLINDERS
                        && (motion.cylinderTravel >> 8) >= 80;
            } else if (part.name().equals("plasma")) {
                sprite.x = motion.plasmaX(copy);
                sprite.y = motion.plasmaY();
                sprite.visible = !defeated && motion.attack == BossMotion.Attack.PLASMA
                        && (phase == Phase.TELEGRAPH || phase == Phase.ATTACK);
            } else if (part.name().equals("launcher")) {
                sprite.x = GameServices.camera().getX() + GameServices.camera().getWidth() / 2;
                sprite.y = GameServices.camera().getY() + 40;
                sprite.visible = !defeated;
            } else if (part.name().equals("panel")) {
                sprite.x = GameServices.camera().getX() + GameServices.camera().getWidth() - 32;
                sprite.y = GameServices.camera().getY() + 128;
                sprite.visible = !defeated;
            }
        } else if (motion.kind == BossMotion.Kind.DEATH_EGG) {
            boolean active = phase == Phase.TELEGRAPH || phase == Phase.ATTACK;
            if (part.name().equals("punch")) {
                sprite.visible = active && motion.attack == BossMotion.Attack.PUNCH;
                sprite.x = motion.punchX;
                sprite.y = motion.punchY;
            } else if (part.name().equals("forearm")) {
                sprite.visible = !defeated && !(active && motion.attack == BossMotion.Attack.PUNCH);
            } else if (part.name().equals("sensor")) {
                sprite.visible = active && motion.attack == BossMotion.Attack.SENSOR_SLAM;
                sprite.x = motion.sensorX;
                sprite.y = motion.sensorY;
            } else if (part.name().equals("bombs")) {
                sprite.visible = active && motion.attack == BossMotion.Attack.BOMBS;
                sprite.x = motion.sensorX + (copy * 2 - 1) * 24;
                sprite.y = Math.min(motion.sensorY, homeBombY() + (motion.flightY >> 8));
                if (motion.bombLandRow > 0 && motion.tick - motion.bombLandRow >= 64) sprite.visible = false;
            } else if (part.name().equals("jet")) {
                sprite.visible = active && motion.attack == BossMotion.Attack.SENSOR_SLAM && motion.tick < 128;
            } else if ((part.name().equals("leg") || part.name().equals("thigh"))
                    && active && motion.attack == BossMotion.Attack.WALK) {
                // Group-animation limb deltas are reduced to an alternating planted-foot pose.
                int lift = motion.tick <= 32 ? motion.tick / 4
                        : motion.tick <= 48 ? 8 + (motion.tick - 32) / 4
                        : motion.tick <= 64 ? 12 - (motion.tick - 48) / 4
                        : Math.max(0, 8 - (motion.tick - 64) / 2);
                sprite.y -= lift;
            }
        }
    }

    private int homeBombY() { return motion.homeY - 20; }

    /** World-space anchor for the prompt attached to this hazard/weak point. */
    int targetX(int target, int ordinal, int total) {
        if (target == dodgeId) return switch (motion.attack) {
            case BALL -> motion.ballX();
            case PUNCH -> motion.punchX;
            case SENSOR_SLAM, BOMBS -> motion.sensorX;
            case PLASMA -> {
                int nearest = 0;
                for (int i = 1; i < 4; i++) {
                    if (Math.abs(motion.plasmaX(i) - motion.sensorX)
                            < Math.abs(motion.plasmaX(nearest) - motion.sensorX)) nearest = i;
                }
                yield motion.plasmaX(nearest);
            }
            case CYLINDERS -> motion.pillarX(motion.occupied);
            case DRILL -> x - 54 * side;
            case WALK -> x;
        };
        if (motion.kind == BossMotion.Kind.FINAL_ZONE) return motion.pillarX(ordinal);
        return total == 1 ? x : GameServices.camera().getX() + GameServices.camera().getWidth() * (ordinal + 1) / (total + 1);
    }

    int targetBottom(int target, int ordinal, int total) {
        int cy = GameServices.camera().getY();
        if (target == dodgeId) {
            int hazardY = switch (motion.attack) {
                case BALL -> motion.ballY();
                case PUNCH -> motion.punchY;
                case PLASMA -> motion.plasmaY();
                case SENSOR_SLAM, BOMBS -> motion.sensorY;
                case CYLINDERS -> cy + 112;
                default -> y;
            };
            return Math.max(cy + 80, Math.min(cy + 190, hazardY - 24));
        }
        if (motion.kind == BossMotion.Kind.FINAL_ZONE) return cy + 96;
        return Math.max(cy + 80, total == 1 ? strikeY() - 28 : cy + 120);
    }

    private void openWindow(Course course) {
        var intent = course.intent();
        var field = course.field();
        var lexicon = course.assets().lexicon();
        int baseRows = (int) (480 / intent.difficulty().speed);
        if (motion.kind == BossMotion.Kind.FINAL_ZONE) {
            // Distinct leading letters make each physical pillar independently selectable.
            String[] words = {"bolt", "gear", "iron", "tube"};
            answerIds = new int[4];
            for (int i = 0; i < answerIds.length; i++) {
                answerIds[i] = field.add(Prompt.of(words[i], intent.capitals(), false), baseRows);
                if (i == motion.occupied) correctId = answerIds[i];
            }
            question = "HIT ROBOTNIK'S PILLAR";
            windowRows = baseRows;
            stateRow = 0;
            return;
        }
        switch (style) {
            case WEAK_POINT -> {
                String phrase = course.picker().pick(java.util.List.of("phrases"), 8, 26, lead -> true);
                targetId = field.add(Prompt.of(phrase == null ? "spin attack" : phrase, intent.capitals(), intent.punctuation()), baseRows);
                windowRows = baseRows;
            }
            case TALK_BACK -> {
                var taunts = lexicon.taunts().stream()
                        .filter(t -> t.tag().equals("any") || t.tag().equals(intent.game())).toList();
                // Robotnik says the line you must type back when it is one of his voiced taunts.
                var voiced = taunts.stream().filter(t -> VoiceLine.taunt(t.text()) != null).toList();
                var pool = !voiced.isEmpty() && course.random().nextInt(4) != 0 ? voiced : taunts;
                String text = pool.isEmpty() ? "You will never win, hedgehog!"
                        : pool.get(course.random().nextInt(pool.size())).text();
                course.say(VoiceLine.taunt(text));
                // Speak/show the entire taunt, echo a short clause. A 45–60 WPM typist
                // should spend the fight attacking, rather than retyping twelve speeches.
                question = text;
                targetId = field.add(Prompt.of(echo(text), intent.capitals(), intent.punctuation()), baseRows);
                windowRows = baseRows;
            }
            case QUIZ -> {
                var questions = lexicon.trivia().stream()
                        .filter(t -> t.game().equals("any") || t.game().equals(intent.game())).toList();
                if (questions.isEmpty()) {
                    targetId = field.add(Prompt.of("chaos emerald", intent.capitals(), intent.punctuation()), baseRows);
                    windowRows = baseRows;
                    return;
                }
                var q = questions.get(course.random().nextInt(questions.size()));
                question = q.question();
                String[] answers = {q.correct(), q.wrong1(), q.wrong2()};
                int shift = course.random().nextInt(3);
                answerIds = new int[3];
                for (int i = 0; i < 3; i++) {
                    String answer = answers[(i + shift) % 3];
                    answerIds[i] = field.add(Prompt.of(answer, intent.capitals(), intent.punctuation()), baseRows * 2);
                    if (answer.equals(q.correct())) correctId = answerIds[i];
                }
                windowRows = baseRows * 2;
            }
        }
        stateRow = 0;
    }

    void onKey(Course course, TypingField.Event event) { }

    /** Prefer a complete short clause; otherwise quote at most 20 characters at a word boundary. */
    private static String echo(String taunt) {
        for (String clause : taunt.split("[!,?.]")) {
            String text = clause.strip();
            if (text.length() >= 8 && text.length() <= 20) return text;
        }
        String text = taunt.substring(0, Math.min(20, taunt.length()));
        if (text.length() < taunt.length() && taunt.charAt(text.length()) != ' ') {
            int space = text.lastIndexOf(' ');
            if (space > 0) text = text.substring(0, space);
        }
        return text.strip();
    }

    void onComplete(Course course, Encounter encounter, int target) {
        if (target == dodgeId) {
            dodgeId = -1;
            dodged = true;
            windowRows = 0; // Successful typing releases the telegraph on the next combat row.
            return;
        }
        if (phase != Phase.OPEN || penalty > 0) return;
        boolean right = target == targetId || target == correctId;
        if (!right && motion.kind == BossMotion.Kind.FINAL_ZONE) {
            int pillar = 0;
            for (int i = 0; i < answerIds.length; i++) if (answerIds[i] == target) pillar = i;
            final int empty = pillar;
            penalty = 64; // Typing design: lost time and a laugh, never Sonic damage.
            laughRows = LAUGH_ROWS;
            for (int id : answerIds) course.field().setTypeable(id, false);
            course.homing().strike(() -> motion.pillarX(empty),
                    () -> GameServices.camera().getY() + 112, () -> { });
            return;
        }
        closeWindow(course);
        if (!right) {
            if (style == Style.QUIZ) course.say(VoiceLine.ROBOTNIK_QUIZ_WRONG);
            laughRows = LAUGH_ROWS;
            recover();
            return;
        }
        phase = Phase.STRIKE;
        stateRow = 0;
        course.homing().strike(() -> x, () -> strikeY(), () -> land(course, encounter));
    }

    private int strikeY() { return motion.kind == BossMotion.Kind.DEATH_EGG ? y - 52 : y; }

    /** A strike connects: one of the eight hits. */
    private void land(Course course, Encounter encounter) {
        hits++;
        flash = 24;
        recoil = 12;
        laughRows = 0;
        course.play(Sounds.Cue.BOSS_HIT);
        if (style == Style.QUIZ) course.say(VoiceLine.ROBOTNIK_QUIZ_RIGHT);
        else course.say(new VoiceLine[] {VoiceLine.ROBOTNIK_HURT_A, VoiceLine.ROBOTNIK_HURT_B,
                VoiceLine.ROBOTNIK_HURT_C, VoiceLine.ROBOTNIK_HURT_D}[course.random().nextInt(4)]);
        course.card().add(1000);
        encounter.effects().kill(course, x, y, 1000);
        if (hits >= hitCount()) {
            defeated = true;
            course.play(Sounds.Cue.BOSS_DEFEATED);
            course.say(VoiceLine.ANNOUNCER_BOSS_DEFEATED);
            course.say(new VoiceLine[] {VoiceLine.ROBOTNIK_DEFEAT_A, VoiceLine.ROBOTNIK_DEFEAT_B,
                    VoiceLine.ROBOTNIK_DEFEAT_C}[course.random().nextInt(3)]);
            course.hud().banner("BOSS DEFEATED!", 0xFFFFD000);
        }
        stateRow = 0;
    }

    private void closeWindow(Course course) {
        if (dodgeId >= 0) course.field().remove(dodgeId);
        dodgeId = -1;
        var field = course.field();
        if (targetId >= 0) field.remove(targetId);
        for (int id : answerIds) field.remove(id);
        targetId = -1;
        answerIds = new int[0];
        correctId = -1;
        question = "";
        windowRows = 0;
    }

    /** Prompts the boss currently shows, for the HUD (empty while closed). */
    int[] openTargets() {
        if (dodgeId >= 0) return new int[] {dodgeId};
        if (targetId >= 0) return new int[] {targetId};
        return answerIds.clone();
    }
}
