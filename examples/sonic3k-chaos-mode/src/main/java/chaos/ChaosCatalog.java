package chaos;

import java.util.*;
import com.openggf.mods.state.SnapshotRandom;

/** Explicit 105-effect catalogue from the 2026-10-13 Chaos design and ranked shortlist.
 * Effect identities are independent of the bounded, parameterized host definition keys.
 */
public final class ChaosCatalog {
    public enum Category { VISUAL, PHYSICS, DISPLACEMENT }
    public enum Severity { ORDINARY, DISRUPTIVE, MAJOR }
    public record Effect(int number, String id, String name, Category category, Severity severity,
                         int basePresentations, int maximumPresentations, String family, String mode,
                         boolean selectedByDefault, String description) {
        public boolean instant() { return basePresentations == 0; }
        public boolean sidegame() {return family.equals("pong") || family.equals("sidegame");}
        public int duration(int quarterMultiplier) {
            return instant() ? 0 : Math.min(maximumPresentations, Math.max(1, basePresentations * quarterMultiplier / 4));
        }
    }
    private final List<Effect> effects = new ArrayList<>();
    public ChaosCatalog() {
        add(1, "bobble-head", "Bobble Head", Category.VISUAL, Severity.ORDINARY, 20, 60, "head", "fixed", true, "Big head mode! Sonic, or the whole team, gets a 150-200% noggin. Purely cosmetic.");
        add(2, "pulse-head", "Throbbing Heads", Category.VISUAL, Severity.ORDINARY, 20, 60, "head", "pulse", true, "Heads swell and shrink in a slow, unsettling pulse. Hitboxes stay the same size.");
        add(3, "invisible-player", "Where Did Sonic Go?", Category.VISUAL, Severity.ORDINARY, 10, 60, "stealth", "hide", true, "Sonic turns invisible. He's still there, still solid and still grabbing rings.");
        add(4, "rainbow-player", "Disco Sonic", Category.VISUAL, Severity.ORDINARY, 20, 60, "sprite-presentation", "tint", true, "Sonic cycles through every colour of the rainbow. Purely cosmetic, deeply groovy.");
        add(5, "negative-world", "Negative Zone", Category.VISUAL, Severity.ORDINARY, 15, 60, "scene-presentation", "negative", true, "The world turns into a photo negative. Your HUD and the Chaos display stay readable.");
        add(6, "mirror-view", "Mirror Universe", Category.VISUAL, Severity.DISRUPTIVE, 12, 15, "scene-presentation", "mirror", true, "The picture flips left to right, but the controls don't. Right is still right.");
        add(7, "upside-down-view", "Australian Sonic", Category.VISUAL, Severity.DISRUPTIVE, 8, 15, "scene-presentation", "upside-down", true, "The whole picture turns upside down. Gravity doesn't, but your brain might.");
        add(8, "quake-camera", "Tiny Earthquake", Category.VISUAL, Severity.ORDINARY, 10, 60, "scene-presentation", "shake", true, "The camera rumbles. The ground under your feet doesn't actually move.");
        add(9, "zoom-pulse", "Breathing Camera", Category.VISUAL, Severity.ORDINARY, 15, 60, "scene-presentation", "zoom", true, "The camera slowly zooms in and out around Sonic, as if the level is breathing.");
        add(10, "afterimages", "Too Many Sonics", Category.VISUAL, Severity.ORDINARY, 20, 60, "sprite-presentation", "trail", true, "Up to 24 frozen afterimages flood the screen. Only one of them is the real Sonic.");
        add(11, "moon-gravity", "Moon Gravity", Category.PHYSICS, Severity.ORDINARY, 15, 60, "dry-gravity", "low", true, "Gravity drops to 25-60%. Jumps float for ages. Sonic only, out of water.");
        add(12, "heavy-gravity", "Lead Shoes", Category.PHYSICS, Severity.ORDINARY, 10, 60, "dry-gravity", "high", true, "Gravity rises to 150-200% and every fall comes down hard. Sonic only, out of water.");
        add(13, "spring-legs", "Spring Legs", Category.PHYSICS, Severity.ORDINARY, 12, 60, "player-motion", "jump", true, "Jumps get a 20% boost. Shields and character moves still work normally.");
        add(14, "ice-shoes", "Ice Shoes", Category.PHYSICS, Severity.ORDINARY, 15, 60, "player-motion", "traction", true, "Friction drops to 40%. Braking takes a while, so plan your stops early.");
        add(15, "sticky-shoes", "Sticky Shoes", Category.PHYSICS, Severity.ORDINARY, 10, 60, "player-motion", "traction", true, "Extra-grippy shoes: let go and Sonic stops almost on the spot.");
        add(16, "fast-forward", "Fast Forward", Category.PHYSICS, Severity.ORDINARY, 10, 60, "pacing", "fast", true, "The whole game runs at 150-200% speed. Everything. Including the badniks.");
        add(17, "slow-motion", "Slow Motion", Category.PHYSICS, Severity.ORDINARY, 15, 60, "pacing", "slow", true, "The game slows to 50-75% speed. Take your time and enjoy the view.");
        add(18, "reverse-controls", "Wrong Way!", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "input-control", "reverse", true, "Left is right and right is left. Menus are unaffected; gameplay is not.");
        add(19, "bunny-hop", "Can't Stop Hopping", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "input-control", "auto-jump", true, "Every time Sonic lands, he jumps again. Momentum still applies.");
        add(20, "ring-confetti", "Ring Confetti", Category.PHYSICS, Severity.ORDINARY, 15, 60, "hurt-scatter", "full", true, "Getting hit scatters every ring you carry instead of only 32. Grab them back fast!");
        add(21, "badnik-pogo", "Badnik Pogo", Category.PHYSICS, Severity.ORDINARY, 15, 60, "defeat-rebound", "boost", true, "Bopping a badnik bounces you 150-250% higher. Boing.");
        add(22, "wind-gust", "Surprise Gust", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "player-motion", "wind", true, "A steady wind pushes Sonic forwards or backwards. Lean into it.");
        add(23, "gravity-hiccup", "Gravity Hiccup", Category.PHYSICS, Severity.DISRUPTIVE, 2, 3, "signed-gravity", "reverse", true, "Gravity flips upside down for a moment, then snaps back.");
        add(24, "gravity-roulette", "Gravity Roulette", Category.PHYSICS, Severity.MAJOR, 4, 5, "signed-gravity", "pulse", false, "A short burst of gravity flips. Up, down, up... who knows?");
        add(25, "checkpoint-roulette", "Starpost Roulette", Category.DISPLACEMENT, Severity.MAJOR, 0, 5, "relocation", "random-checkpoint", true, "Warp to a random starpost in this act. Could be forwards. Could be... not.");
        add(26, "last-starpost", "Back to the Starpost", Category.DISPLACEMENT, Severity.MAJOR, 0, 5, "relocation", "last-checkpoint", true, "Warp back to the last starpost you touched. Your rings and time stay as they are.");
        add(27, "blink-step", "Blink and You'll Miss It", Category.DISPLACEMENT, Severity.DISRUPTIVE, 0, 15, "relocation", "blink", true, "Sonic blinks a short hop left or right onto safe ground.");
        add(28, "buddy-swap", "Trading Places", Category.DISPLACEMENT, Severity.DISRUPTIVE, 0, 15, "relocation", "team-swap", true, "Sonic and his partner swap places. Needs a sidekick nearby.");
        add(29, "rubber-band", "Rubber Band", Category.DISPLACEMENT, Severity.DISRUPTIVE, 6, 8, "relocation", "delayed-return", true, "Sonic remembers where he is now... and snaps back there a few seconds later.");
        add(30, "glitch-step", "Glitch Step", Category.DISPLACEMENT, Severity.MAJOR, 4, 5, "relocation", "steps", false, "Sonic skips a few pixels now and then, like a bad connection. Safe landings only.");
        add(31, "dvd-hud", "DVD HUD", Category.VISUAL, Severity.ORDINARY, 20, 60, "hud-presentation", "bounce", true, "Score, time and rings bounce around the screen. Will it hit the corner?");
        add(32, "hud-orbit", "Orbiting HUD", Category.VISUAL, Severity.ORDINARY, 15, 60, "hud-presentation", "orbit", true, "The HUD circles around Sonic like a tiny moon.");
        add(33, "shy-hud", "Shy HUD", Category.VISUAL, Severity.ORDINARY, 15, 60, "hud-presentation", "evade", true, "The HUD is shy. Get close and it drifts away from you.");
        add(34, "hud-scale", "Breathing HUD", Category.VISUAL, Severity.ORDINARY, 15, 60, "hud-presentation", "scale", true, "The HUD swells and shrinks between 75% and 125%. The numbers stay honest.");
        add(35, "hud-shuffle", "HUD Musical Chairs", Category.VISUAL, Severity.ORDINARY, 15, 60, "hud-presentation", "shuffle", true, "Score, time and rings swap places. Each label keeps its own number.");
        add(36, "hud-hide", "Where Are My Rings?", Category.VISUAL, Severity.DISRUPTIVE, 5, 6, "hud-presentation", "hide", true, "The HUD vanishes for a few seconds. Count your own rings.");
        add(37, "giant-badniks", "Big Trouble", Category.VISUAL, Severity.ORDINARY, 12, 60, "sprite-presentation", "scale", true, "Badniks look 150% bigger. Their hitboxes don't change.");
        add(38, "tiny-badniks", "Pocket Badniks", Category.VISUAL, Severity.ORDINARY, 12, 60, "sprite-presentation", "scale", true, "Badniks shrink to 75% size. They hit exactly as hard as before.");
        add(39, "spinning-badniks", "Badnik Pinwheels", Category.VISUAL, Severity.ORDINARY, 10, 60, "sprite-presentation", "rotate", true, "Badniks spin slowly in place. Their movement is completely normal.");
        add(40, "jelly-objects", "Jelly Objects", Category.VISUAL, Severity.ORDINARY, 12, 60, "sprite-presentation", "squash", true, "Badniks and pickups wobble like jelly. Purely cosmetic.");
        add(41, "ring-magnet", "Ring Magnet", Category.PHYSICS, Severity.ORDINARY, 15, 60, "object-field", "attract", true, "Nearby rings fly towards you. No lightning shield required.");
        add(42, "ring-repulsion", "Get Away, Rings!", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "object-field", "repel", true, "Nearby rings run away from you. Catch them if you can!");
        add(43, "badnik-magnet", "Badnik Magnet", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "object-field", "attract", true, "Free-roaming badniks drift towards you. Make sure you're rolling.");
        add(44, "badnik-repulsion", "Personal Space", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "object-field", "repel", true, "Free-roaming badniks get pushed away from you.");
        add(45, "ring-orbit", "Ring Satellites", Category.PHYSICS, Severity.ORDINARY, 15, 60, "object-field", "orbit", true, "Nearby rings gather into an orbit around you. Touch them to collect.");
        add(46, "projectile-magnet", "Bullet Magnet", Category.PHYSICS, Severity.MAJOR, 3, 4, "object-field", "attract", false, "Enemy shots curve towards you. Gently, but they curve.");
        add(47, "projectile-repulsion", "Bullet Repellent", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "object-field", "repel", true, "Enemy shots get nudged away from you. Shields still deflect normally.");
        add(48, "super-springs", "Super Springs", Category.PHYSICS, Severity.DISRUPTIVE, 12, 15, "spring-response", "strong", true, "Springs launch you 110-135% harder. Hold on tight.");
        add(49, "gentle-springs", "Sleepy Springs", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "spring-response", "weak", true, "Springs are tired: 75-90% strength. Required routes still get enough bounce.");
        add(50, "ring-rain", "It's Raining Rings", Category.PHYSICS, Severity.ORDINARY, 10, 60, "economy", "rain", true, "Real rings rain across the whole screen at a slant. Grab what you can!");
        add(51, "bumper-ahead", "Bumper Ahead!", Category.DISPLACEMENT, Severity.DISRUPTIVE, 0, 15, "encounter", "bumper", true, "A Carnival Night bumper appears right in front of you. Boing!");
        add(52, "bumper-behind", "Rear Bumper!", Category.DISPLACEMENT, Severity.DISRUPTIVE, 0, 15, "encounter", "bumper", true, "A Carnival Night bumper appears right behind you. Boing!");
        add(53, "projectile-overdrive", "Badnik Overdrive", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "enemy-rule", "fire-cadence", true, "Shooting badniks reload faster and fire more often.");
        add(54, "soz-ghost-party", "Sandopolis Ghost Party", Category.PHYSICS, Severity.DISRUPTIVE, 12, 15, "encounter", "ghost", true, "Sandopolis ghosts crash the party and drift after you for a while.");
        add(55, "windy-day", "Windy Day", Category.PHYSICS, Severity.MAJOR, 0, 5, "encounter", "gust", true, "A Cluckoid's gust of wind sends you flying. Hold on to your shoes.");
        add(56, "time-moves-with-sonic", "Time Moves With Sonic", Category.PHYSICS, Severity.ORDINARY, 10, 60, "pacing", "motion", true, "Time only moves when Sonic does. Run and the world speeds up; stop and it crawls.");
        add(57, "badnik-stasis", "Badnik Stasis", Category.PHYSICS, Severity.ORDINARY, 5, 60, "enemy-rule", "stasis", true, "A few badniks freeze in place while everything else keeps going.");
        add(58, "badnik-budding", "Badnik Budding", Category.PHYSICS, Severity.DISRUPTIVE, 0, 15, "enemy-rule", "budding", true, "The next badnik you defeat splits into two. Only once, thankfully.");
        add(59, "sonic-says", "Sonic Says", Category.PHYSICS, Severity.ORDINARY, 8, 60, "challenge", "task", true, "Sonic gives an order! Jump or duck when told to earn a short break from Chaos.");
        add(60, "monitor-breakout", "Monitor Breakout", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "encounter", "monitor-cage", true, "You're boxed in by monitors! Roll or spindash your way out.");
        add(61, "vine-time", "Vine Time", Category.PHYSICS, Severity.ORDINARY, 12, 60, "encounter", "vine", true, "An Angel Island vine appears nearby. Grab it for a swing.");
        add(62, "badnik-bounty", "Badnik Bounty", Category.PHYSICS, Severity.ORDINARY, 15, 60, "economy", "bounty", true, "Defeated badniks pay out bonus rings for a short while.");
        add(63, "shield-surprise", "Shield Surprise", Category.PHYSICS, Severity.ORDINARY, 0, 60, "power-up", "shield", true, "A free fire, lightning or bubble shield. Use it like any other shield.");
        add(64, "counterfeit-monitor", "Counterfeit Monitor", Category.PHYSICS, Severity.DISRUPTIVE, 0, 15, "encounter", "fake-monitor", true, "A tempting monitor appears... but Eggman is inside. Look before you pop it.");
        add(65, "dont-look-back", "Don't Look Back", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "enemy-rule", "look-gate", true, "A badnik only attacks while you face it. Turn away to stop it.");
        add(66, "keep-moving", "Keep Moving", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "challenge", "keep-moving", true, "Keep up a running pace for two seconds. Dawdle and it costs up to 3 rings.");
        add(67, "shoe-delivery", "Shoe Delivery", Category.PHYSICS, Severity.ORDINARY, 10, 60, "power-up", "speed-shoes", true, "A free pair of speed shoes. Go fast.");
        add(68, "ten-second-superstar", "Ten-Second Superstar", Category.PHYSICS, Severity.ORDINARY, 10, 60, "power-up", "invincibility", true, "Short invincibility with the real stars. Pits and crushes can still get you.");
        add(69, "free-air", "Free Air", Category.PHYSICS, Severity.ORDINARY, 15, 60, "power-up", "air", true, "Breathe easy underwater for a while. Water physics stay the same.");
        add(70, "emergency-platform", "Emergency Platform", Category.PHYSICS, Severity.ORDINARY, 5, 60, "encounter", "rescue-platform", true, "Falling? A temporary platform appears beneath you. It won't vanish under your feet.");
        add(71, "delayed-controls", "Delayed Controls", Category.PHYSICS, Severity.DISRUPTIVE, 4, 15, "input-control", "delay", true, "Your buttons arrive slightly late: 100-150 ms of lag. Menus stay instant.");
        add(72, "wrong-way-momentum", "Wrong-Way Momentum", Category.PHYSICS, Severity.DISRUPTIVE, 0, 15, "player-motion", "reverse", true, "Sonic's sideways momentum flips in an instant. Buttons still mean what they say.");
        add(73, "cruise-sonic", "Cruise Sonic", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "player-motion", "cruise", true, "Sonic runs on his own for a bit. You're still in charge of jumping.");
        add(74, "short-fuse-shots", "Short Fuse Shots", Category.PHYSICS, Severity.ORDINARY, 10, 60, "projectile", "short-range", true, "Enemy shots fizzle out after a shorter distance.");
        add(75, "ring-leak", "Leaky Pockets", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "economy", "leak", true, "Rings fall out of your pockets as you move. Turn back to grab them. You keep the last one.");
        add(76, "animal-amnesty", "Animal Amnesty", Category.PHYSICS, Severity.ORDINARY, 10, 60, "challenge", "amnesty", true, "Travel a short way without defeating a badnik to earn a Chaos break. Pacifists welcome.");
        add(77, "badnik-blind-spot", "Badnik Blind Spot", Category.PHYSICS, Severity.ORDINARY, 8, 60, "enemy-rule", "blind-spot", true, "Badniks keep patrolling but stop aiming at you. Touching them still hurts.");
        add(78, "tails-taxi", "Tails Taxi", Category.DISPLACEMENT, Severity.MAJOR, 8, 8, "transport", "carry", false, "A helper swoops in to carry you across a short gap.");
        add(79, "rescue-buddy", "Rescue Buddy", Category.PHYSICS, Severity.ORDINARY, 15, 60, "transport", "companion", true, "A temporary sidekick drops in to help for a short while.");
        add(80, "block-staircase", "Block Staircase", Category.PHYSICS, Severity.ORDINARY, 8, 60, "encounter", "block-stack", true, "A small stack of solid blocks appears, offering a way up.");
        add(81, "platform-hitchhike", "Platform Hitchhike", Category.DISPLACEMENT, Severity.DISRUPTIVE, 6, 15, "transport", "ride", true, "Sonic hops onto a nearby moving platform for a short ride.");
        add(82, "flash-flood", "Flash Flood", Category.PHYSICS, Severity.MAJOR, 6, 6, "fluid", "flood", false, "A local flood! Swim through with underwater physics. Don't worry, you can breathe.");
        add(83, "rolling-rampage", "Rolling Rampage", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "player-motion", "roll", true, "Sonic curls into a ball and stays rolling. Wreck everything.");
        add(84, "eggman-blaster", "Eggman Blaster", Category.PHYSICS, Severity.ORDINARY, 10, 60, "projectile", "player-fire", true, "Fire a few shots at badniks. Limited ammo, so pick your targets.");
        add(85, "firework-shots", "Firework Shots", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "projectile", "impact-blast", true, "Enemy shots burst into small explosions when they land.");
        add(86, "chain-reaction", "Chain Reaction", Category.PHYSICS, Severity.DISRUPTIVE, 10, 15, "enemy-rule", "chain-reaction", true, "Defeated badniks explode and can take a nearby badnik with them.");
        add(87, "falling-debris", "Falling Debris", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "encounter", "debris", true, "Watch out above! Debris falls nearby after a short warning.");
        add(88, "speed-trap", "Speed Trap", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "encounter", "speed-trap", true, "Go too fast and a breakable obstacle appears ahead. You'll get a warning.");
        add(89, "badnik-truce", "Badnik Truce", Category.PHYSICS, Severity.ORDINARY, 6, 60, "enemy-rule", "truce", true, "Badniks and players can't hurt each other for a while. Bosses never sign truces.");
        add(90, "grounded", "Grounded", Category.PHYSICS, Severity.DISRUPTIVE, 4, 15, "input-control", "jump-gate", true, "No new jumps for a moment. You can still run, roll and spindash.");
        add(91, "brake-check", "Brake Check", Category.PHYSICS, Severity.DISRUPTIVE, 0, 15, "player-motion", "stop", true, "Sonic slams on the brakes. Instantly.");
        add(92, "landmark-hunt", "Landmark Hunt", Category.PHYSICS, Severity.ORDINARY, 30, 60, "challenge", "landmark", true, "Reach the monitor pictured on screen to earn a short break from Chaos.");
        add(93, "skill-chain", "Skill Chain", Category.PHYSICS, Severity.ORDINARY, 20, 60, "challenge", "skill-chain", true, "Chain a jump, a ring and a badnik hit into a combo. Rewards buy a Chaos breather.");
        add(94, "jukebox-surprise", "Jukebox Surprise", Category.VISUAL, Severity.ORDINARY, 3, 3, "audio", "music", true, "Another S3K track cuts in for about three seconds.");
        add(95, "clang-landings", "Clang Landings", Category.VISUAL, Severity.ORDINARY, 15, 60, "audio", "clang", true, "Landings and spindashes occasionally make a comedy clang.");
        add(96, "imaginary-chat", "Imaginary Chat", Category.VISUAL, Severity.ORDINARY, 20, 60, "chat", "simulated", true, "A pretend audience reacts to your rings and landings. Not a real chat!");
        add(97, "squashy-view", "Squashy View", Category.VISUAL, Severity.DISRUPTIVE, 8, 15, "scene-presentation", "input-squash", true, "Your inputs squash and stretch the whole picture. Physics stay normal.");
        add(98, "blueprint-zone", "Blueprint Zone", Category.VISUAL, Severity.ORDINARY, 12, 60, "scene-presentation", "outline", true, "The level turns into a blueprint outline. Players and hazards stay visible.");
        add(99, "wrong-zone-scenery", "Wrong-Zone Scenery", Category.VISUAL, Severity.ORDINARY, 12, 60, "scene-presentation", "scenery", true, "The background is borrowed from another zone. The ground is still this one.");
        add(100, "ring-bank-roulette", "Ring Bank Roulette", Category.PHYSICS, Severity.DISRUPTIVE, 0, 15, "economy", "balance", true, "Gain or lose up to 10 rings at random. Never below zero.");
        add(101, "ring-pong", "Ring Pong", Category.VISUAL, Severity.ORDINARY, 20, 20, "pong", "pong", false, "A quick game of Pong with a ring. Return it three times for five rings.");
        add(102, "ring-snake", "Ring Snake", Category.VISUAL, Severity.ORDINARY, 20, 20, "sidegame", "snake", false, "A quick game of Snake. Eat rings to earn a break from Chaos.");
        add(103, "monitor-stack", "Monitor Stack", Category.VISUAL, Severity.ORDINARY, 20, 20, "sidegame", "stack", false, "Stack falling monitors and clear lines to earn a break from Chaos.");
        add(104, "shield-beat", "Shield Beat", Category.VISUAL, Severity.ORDINARY, 20, 20, "sidegame", "rhythm", false, "Hit A, B or C in time with the shield beats to earn a break from Chaos.");
        add(105, "badnik-defence", "Badnik Defence", Category.VISUAL, Severity.ORDINARY, 20, 20, "sidegame", "defence", false, "Fire springs down the lanes before badniks reach you. Earn a break from Chaos.");
        if (effects.size()!=105 || effects.stream().map(Effect::id).distinct().count()!=105
                || families().size()>32) throw new IllegalStateException("Invalid Chaos catalogue");
    }
    private void add(int number,String id,String name,Category category,Severity severity,int seconds,
                     int maximumSeconds,String family,String mode,boolean selected,String description) {
        effects.add(new Effect(number,id,name,category,severity,seconds*60,maximumSeconds*60,family,mode,selected,description));
    }
    public List<Effect> effects() { return List.copyOf(effects); }
    public Set<String> families() {
        var ids = new LinkedHashSet<String>(); effects.forEach(e -> ids.add(e.family()));
        return Collections.unmodifiableSet(ids);
    }
    public Effect effect(String id) {
        return effects.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown effect: "+id));
    }
    public Effect effect(int number) {
        return effects.stream().filter(e -> e.number()==number).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown effect number"));
    }
    public Set<String> defaults() {
        var selected = new LinkedHashSet<String>(); effects.stream().filter(Effect::selectedByDefault).forEach(e -> selected.add(e.id()));
        return Collections.unmodifiableSet(selected);
    }
    /** Sample once at activation. Dynamic envelopes subsequently replace only their admitted scalar. */
    public Map<String,Object> sample(Effect effect, SnapshotRandom random) {
        return sample(effect,random,-1);
    }
    /** Only detached ROM-backed backgrounds with a prepared native renderer enter the donor pool. */
    public Map<String,Object> sample(Effect effect, SnapshotRandom random,int nativeZone) {
        int n=effect.number(), percent=100, cap=0;
        String target="leader", side=n==52?"behind":"ahead";
        if(n==22)side=random.nextInt(2)==0?"ahead":"behind";
        if (n==1 || n==2) { percent=150+random.nextInt(6)*10; target=random.nextInt(2)==0?"leader":"all-team"; }
        else if(n==3) target=random.nextInt(2)==0?"leader":"all-team";
        else if(n==11) percent=25+random.nextInt(8)*5;
        else if(n==12) percent=150+random.nextInt(11)*5;
        else if(n==16) percent=150+random.nextInt(3)*25;
        else if(n==17) percent=50+random.nextInt(2)*25;
        else if(n==56) percent=25;
        else if(n==21) {percent=150+random.nextInt(5)*25; cap=8+random.nextInt(5);}
        else if(n==37) percent=150; else if(n==38) percent=75; else if(n==40) percent=115;
        else if(n==48) percent=110+random.nextInt(6)*5;
        else if(n==49) percent=75+random.nextInt(4)*5;
        else if(n==13) percent=120; else if(n==14) percent=40; else if(n==15) percent=160;
        String objectTarget=(n==41 || n==42 || n==45)?"ring":(n==46 || n==47)?"projectile":"badnik";
        var values=new LinkedHashMap<String,Object>();
        values.put("kind",effect.mode()); values.put("percent",percent); values.put("cap",cap);
        values.put("target",effect.family().equals("object-field")?objectTarget:n>=37 && n<=39?"badniks":n==40?"badniks-and-pickups":target);
        values.put("side",side); values.put("audio",false); values.put("full",n==20);
        values.put("amount",5);
        values.put("period",120+random.nextInt(5)*30); values.put("seed",random.nextInt(1_000_000));
        values.put("duration",Math.clamp(effect.basePresentations()==0?600:effect.basePresentations(),8,3600));
        values.put("cue",n==94?1+random.nextInt(23):0x49);
        values.put("delay",6+random.nextInt(4));
        values.put("magnitude",n==8?4:n==9?5:n==97?6:n==98?20:0);
        int[] scenery=java.util.stream.IntStream.of(0,1,6,11).filter(zone->zone!=nativeZone).toArray();
        values.put("scenery",n==99?scenery[random.nextInt(scenery.length)]:0);
        values.put("sequence",n==24?1+random.nextInt(15):1);
        return Map.copyOf(values);
    }
}
