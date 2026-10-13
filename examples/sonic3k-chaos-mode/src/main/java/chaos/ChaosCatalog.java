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
        add(1, "bobble-head", "Bobble Head", Category.VISUAL, Severity.ORDINARY, 20, 60, "head", "fixed", true, "Pick a 150–200% head size and a supported leader/team target.");
        add(2, "pulse-head", "Throbbing Heads", Category.VISUAL, Severity.ORDINARY, 20, 60, "head", "pulse", true, "Smoothly pulse reviewed head pixels between normal and a sampled maximum.");
        add(3, "invisible-player", "Where Did Sonic Go?", Category.VISUAL, Severity.ORDINARY, 10, 60, "stealth", "hide", true, "Hide leader/team body and appendage; retain collision, HUD and sound.");
        add(4, "rainbow-player", "Disco Sonic", Category.VISUAL, Severity.ORDINARY, 20, 60, "sprite-presentation", "tint", true, "Cycle the playable sprite's presentation colours smoothly.");
        add(5, "negative-world", "Negative Zone", Category.VISUAL, Severity.ORDINARY, 15, 60, "scene-presentation", "negative", true, "Invert gameplay colours while the HUD and Chaos UI remain readable.");
        add(6, "mirror-view", "Mirror Universe", Category.VISUAL, Severity.DISRUPTIVE, 12, 15, "scene-presentation", "mirror", true, "Mirror the gameplay image horizontally; controls retain their normal meaning.");
        add(7, "upside-down-view", "Australian Sonic", Category.VISUAL, Severity.DISRUPTIVE, 8, 15, "scene-presentation", "upside-down", true, "Turn the gameplay image upside down without changing gravity.");
        add(8, "quake-camera", "Tiny Earthquake", Category.VISUAL, Severity.ORDINARY, 10, 60, "scene-presentation", "shake", true, "Add bounded camera shake without moving the collision world.");
        add(9, "zoom-pulse", "Breathing Camera", Category.VISUAL, Severity.ORDINARY, 15, 60, "scene-presentation", "zoom", true, "Gently zoom in/out about the player without changing viewport simulation or object admission.");
        add(10, "afterimages", "Too Many Sonics", Category.VISUAL, Severity.ORDINARY, 20, 60, "sprite-presentation", "trail", true, "Draw fading recent sprite poses behind the player.");
        add(11, "moon-gravity", "Moon Gravity", Category.PHYSICS, Severity.ORDINARY, 15, 60, "dry-gravity", "low", true, "Sample dry Sonic gravity at 25–60%; longer float, with native jump impulse and other movement owners.");
        add(12, "heavy-gravity", "Lead Shoes", Category.PHYSICS, Severity.ORDINARY, 10, 60, "dry-gravity", "high", true, "Sample dry Sonic gravity at 150–200%, making ordinary falls heavier.");
        add(13, "spring-legs", "Spring Legs", Category.PHYSICS, Severity.ORDINARY, 12, 60, "player-motion", "jump", true, "Increase normal jump impulse within a bounded range; preserve shield/character abilities.");
        add(14, "ice-shoes", "Ice Shoes", Category.PHYSICS, Severity.ORDINARY, 15, 60, "player-motion", "traction", true, "Reduce ordinary ground friction for slippery braking.");
        add(15, "sticky-shoes", "Sticky Shoes", Category.PHYSICS, Severity.ORDINARY, 10, 60, "player-motion", "traction", true, "Increase ground friction and braking; retain enough movement to traverse the route.");
        add(16, "fast-forward", "Fast Forward", Category.PHYSICS, Severity.ORDINARY, 10, 60, "pacing", "fast", true, "Sample 150–200% native gameplay pacing; optional audio follows.");
        add(17, "slow-motion", "Slow Motion", Category.PHYSICS, Severity.ORDINARY, 15, 60, "pacing", "slow", true, "Sample 50–75% native pacing for a brief helpful breather.");
        add(18, "reverse-controls", "Wrong Way!", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "input-control", "reverse", true, "Swap left/right at the gameplay input boundary; menus stay normal.");
        add(19, "bunny-hop", "Can't Stop Hopping", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "input-control", "auto-jump", true, "Request a fresh ordinary jump on eligible landings, with captured input edges.");
        add(20, "ring-confetti", "Ring Confetti", Category.PHYSICS, Severity.ORDINARY, 15, 60, "hurt-scatter", "full", true, "Hurt spills during the effect make the full carried inventory recoverable instead of only the native capped scatter. It still loses the full inventory.");
        add(21, "badnik-pogo", "Badnik Pogo", Category.PHYSICS, Severity.ORDINARY, 15, 60, "defeat-rebound", "boost", true, "Sample 150–250% native badnik-defeat rebound, with an 8–12 px/tick cap.");
        add(22, "wind-gust", "Surprise Gust", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "player-motion", "wind", true, "Add a mild, capped horizontal force with sampled direction; respect transport and scripted movement.");
        add(23, "gravity-hiccup", "Gravity Hiccup", Category.PHYSICS, Severity.DISRUPTIVE, 2, 3, "signed-gravity", "reverse", true, "Briefly reverse gravity once, then remove the reversal contribution.");
        add(24, "gravity-roulette", "Gravity Roulette", Category.PHYSICS, Severity.MAJOR, 4, 5, "signed-gravity", "pulse", false, "Play a captured short sequence of normal/reversed gravity pulses.");
        add(25, "checkpoint-roulette", "Starpost Roulette", Category.DISPLACEMENT, Severity.MAJOR, 0, 5, "relocation", "random-checkpoint", true, "Relocate to a random currently valid checkpoint destination in this act.");
        add(26, "last-starpost", "Back to the Starpost", Category.DISPLACEMENT, Severity.MAJOR, 0, 5, "relocation", "last-checkpoint", true, "Relocate to the last activated post without resetting rings, time or world state.");
        add(27, "blink-step", "Blink and You'll Miss It", Category.DISPLACEMENT, Severity.DISRUPTIVE, 0, 15, "relocation", "blink", true, "Relocate a short distance left/right to a host-validated nearby landing.");
        add(28, "buddy-swap", "Trading Places", Category.DISPLACEMENT, Severity.DISRUPTIVE, 0, 15, "relocation", "team-swap", true, "Atomically exchange nearby eligible leader/sidekick positions.");
        add(29, "rubber-band", "Rubber Band", Category.DISPLACEMENT, Severity.DISRUPTIVE, 6, 8, "relocation", "delayed-return", true, "Mark a safe point, then try to return there after a brief delay.");
        add(30, "glitch-step", "Glitch Step", Category.DISPLACEMENT, Severity.MAJOR, 4, 5, "relocation", "steps", false, "Perform occasional small, validated position skips with a strict travel budget; retain collision.");
        add(31, "dvd-hud", "DVD HUD", Category.VISUAL, Severity.ORDINARY, 20, 60, "hud-presentation", "bounce", true, "Reflow native readouts into a compact plaque that bounces off screen edges like a DVD logo.");
        add(32, "hud-orbit", "Orbiting HUD", Category.VISUAL, Severity.ORDINARY, 15, 60, "hud-presentation", "orbit", true, "Move the compact HUD plaque in a small orbit around the player's screen position, clamped onscreen.");
        add(33, "shy-hud", "Shy HUD", Category.VISUAL, Severity.ORDINARY, 15, 60, "hud-presentation", "evade", true, "Make the plaque drift away when the player approaches it onscreen; cap speed and reflect at edges.");
        add(34, "hud-scale", "Breathing HUD", Category.VISUAL, Severity.ORDINARY, 15, 60, "hud-presentation", "scale", true, "Pulse the native plaque between 75% and 125% scale without changing any counter value.");
        add(35, "hud-shuffle", "HUD Musical Chairs", Category.VISUAL, Severity.ORDINARY, 15, 60, "hud-presentation", "shuffle", true, "Shuffle the readout positions once, keeping each label attached to its matching value.");
        add(36, "hud-hide", "Where Are My Rings?", Category.VISUAL, Severity.DISRUPTIVE, 5, 6, "hud-presentation", "hide", true, "Briefly hide stock readouts; the Chaos countdown and menus remain visible.");
        add(37, "giant-badniks", "Big Trouble", Category.VISUAL, Severity.ORDINARY, 12, 60, "sprite-presentation", "scale", true, "Enlarge eligible badnik bodies visually, keeping native collision and AI.");
        add(38, "tiny-badniks", "Pocket Badniks", Category.VISUAL, Severity.ORDINARY, 12, 60, "sprite-presentation", "scale", true, "Shrink eligible badnik bodies visually to 75% size while retaining native collision.");
        add(39, "spinning-badniks", "Badnik Pinwheels", Category.VISUAL, Severity.ORDINARY, 10, 60, "sprite-presentation", "rotate", true, "Slowly spin eligible badnik artwork around its producer anchor; gameplay motion stays native.");
        add(40, "jelly-objects", "Jelly Objects", Category.VISUAL, Severity.ORDINARY, 12, 60, "sprite-presentation", "squash", true, "Gently squash and stretch eligible independent badniks and pickups without moving collision geometry.");
        add(41, "ring-magnet", "Ring Magnet", Category.PHYSICS, Severity.ORDINARY, 15, 60, "object-field", "attract", true, "Attract eligible nearby rings toward the leader without replacing the current shield.");
        add(42, "ring-repulsion", "Get Away, Rings!", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "object-field", "repel", true, "Apply a mild outward force to nearby eligible rings; inventory and normal pickup rules stay intact.");
        add(43, "badnik-magnet", "Badnik Magnet", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "object-field", "attract", true, "Gently pull a bounded set of qualified free-moving badniks toward the player.");
        add(44, "badnik-repulsion", "Personal Space", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "object-field", "repel", true, "Push qualified nearby moving badniks away; do not relocate anchored enemies or terrain.");
        add(45, "ring-orbit", "Ring Satellites", Category.PHYSICS, Severity.ORDINARY, 15, 60, "object-field", "orbit", true, "Gather a bounded set of eligible rings into a small orbit; native touch collection still works.");
        add(46, "projectile-magnet", "Bullet Magnet", Category.PHYSICS, Severity.MAJOR, 3, 4, "object-field", "attract", false, "Curve a few eligible damaging projectiles toward the player with strictly capped force.");
        add(47, "projectile-repulsion", "Bullet Repellent", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "object-field", "repel", true, "Nudge eligible projectiles away, preserving their native collision and shield-deflection state.");
        add(48, "super-springs", "Super Springs", Category.PHYSICS, Severity.DISRUPTIVE, 12, 15, "spring-response", "strong", true, "Boost qualified spring launches to 110–135%, respecting native directional caps.");
        add(49, "gentle-springs", "Sleepy Springs", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "spring-response", "weak", true, "Reduce qualified spring launches to 75–90%, respecting required-route minima.");
        add(50, "ring-rain", "It's Raining Rings", Category.PHYSICS, Severity.ORDINARY, 10, 60, "economy", "rain", true, "Emit a small shower of real, short-lived ROM-backed ring pickups near the viewport.");
        add(51, "bumper-ahead", "Bumper Ahead!", Category.DISPLACEMENT, Severity.DISRUPTIVE, 0, 15, "encounter", "bumper", true, "Spawn a native CNZ bumper just ahead within contact range, so normal collision pushes the leader away from it immediately.");
        add(52, "bumper-behind", "Rear Bumper!", Category.DISPLACEMENT, Severity.DISRUPTIVE, 0, 15, "encounter", "bumper", true, "Spawn the same bumper just behind within contact range for an immediate native bump in the opposite direction.");
        add(53, "projectile-overdrive", "Badnik Overdrive", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "enemy-rule", "fire-cadence", true, "Qualified firing badniks reload faster and emit their own native projectiles under a shared shot budget.");
        add(54, "soz-ghost-party", "Sandopolis Ghost Party", Category.PHYSICS, Severity.DISRUPTIVE, 12, 15, "encounter", "ghost", true, "Temporarily admit a small SOZ2 ghost encounter with its native art/AI and captured encounter-local state.");
        add(55, "windy-day", "Windy Day", Category.PHYSICS, Severity.MAJOR, 0, 5, "encounter", "gust", true, "Trigger one native Cluckoid pressure burst from a captured fixed source, with optional bounded native wind debris.");
        add(56, "time-moves-with-sonic", "Time Moves With Sonic", Category.PHYSICS, Severity.ORDINARY, 10, 60, "pacing", "motion", true, "Game pacing follows Sonic’s movement: moving quickly speeds time up, standing still slows it down. Use a nonzero pacing floor and an independent expiry clock; preserve responsive input.");
        add(57, "badnik-stasis", "Badnik Stasis", Category.PHYSICS, Severity.ORDINARY, 5, 60, "enemy-rule", "stasis", true, "Briefly freeze a small set of independent badniks while Sonic and the level continue. Qualify actor clocks and child graphs; exclude bosses, terrain and scripted transport.");
        add(58, "badnik-budding", "Badnik Budding", Category.PHYSICS, Severity.DISRUPTIVE, 0, 15, "enemy-rule", "budding", true, "One defeated badnik splits into two native replacements. One generation only, safe placement and a strict spawn budget; no recursive population growth.");
        add(59, "sonic-says", "Sonic Says", Category.PHYSICS, Severity.ORDINARY, 8, 60, "challenge", "task", true, "Give a quick feasible instruction: duck, jump, collect a ring or break a nearby monitor. Reward success; use only a mild declared penalty, and never ask for unavailable actions.");
        add(60, "monitor-breakout", "Monitor Breakout", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "encounter", "monitor-cage", true, "A few breakable monitors surround Sonic. Roll or spindash out of the surprise enclosure. Qualified flat ground only; expiry guarantees release and prevents crushes.");
        add(61, "vine-time", "Vine Time", Category.PHYSICS, Severity.ORDINARY, 12, 60, "encounter", "vine", true, "Place an accessible native AIZ vine for an unexpected swing and normal release. Prepare ROM art and anchor geometry; no forced attachment outside native grab range.");
        add(62, "badnik-bounty", "Badnik Bounty", Category.PHYSICS, Severity.ORDINARY, 15, 60, "economy", "bounty", true, "Defeating qualified badniks awards small ring bonuses during a short bounty window. Cap total income and capture defeat/reward identities so replay cannot duplicate awards.");
        add(63, "shield-surprise", "Shield Surprise", Category.PHYSICS, Severity.ORDINARY, 0, 60, "power-up", "shield", true, "Give a real native elemental shield, sampled from eligible fire, lightning and bubble shields. Use normal shield consumption; preserve existing protection and other owners’ contributions.");
        add(64, "counterfeit-monitor", "Counterfeit Monitor", Category.PHYSICS, Severity.DISRUPTIVE, 0, 15, "encounter", "fake-monitor", true, "A tempting optional monitor looks helpful but resolves native Eggman contents when broken. One contact, no forced collision; exclude mandatory pickups and provide a short warning.");
        add(65, "dont-look-back", "Don’t Look Back", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "enemy-rule", "look-gate", true, "A qualified badnik attacks only while Sonic faces toward it. Turn away to interrupt the threat. Selective attack gate only; keep normal movement/contact and omit stalking teleports.");
        add(66, "keep-moving", "Keep Moving", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "challenge", "keep-moving", true, "Maintain a modest reachable running speed for a short challenge. Suspend for mandatory stops and transport; failure costs a few rings, not an instant death.");
        add(67, "shoe-delivery", "Shoe Delivery", Category.PHYSICS, Severity.ORDINARY, 10, 60, "power-up", "speed-shoes", true, "Grant a short native speed-shoes burst with its normal movement tables and presentation. This changes character movement, not global pacing; preserve native power-up/audio ownership.");
        add(68, "ten-second-superstar", "Ten-Second Superstar", Category.PHYSICS, Severity.ORDINARY, 10, 60, "power-up", "invincibility", true, "Grant short native invincibility with ROM stars and normal attack/contact behavior. Native exceptions still apply: do not promise immunity to pits, crushes or scripted deaths.");
        add(69, "free-air", "Free Air", Category.PHYSICS, Severity.ORDINARY, 15, 60, "power-up", "air", true, "Give a brief underwater breathing reprieve while retaining native water movement. Arbitrate oxygen, warnings and music through the native owner; resume safely at expiry.");
        add(70, "emergency-platform", "Emergency Platform", Category.PHYSICS, Severity.ORDINARY, 5, 60, "encounter", "rescue-platform", true, "A visible native solid platform appears below a falling Sonic as a temporary rescue. Validate terrain and support; do not remove an occupied platform into an unavoidable death.");
        add(71, "delayed-controls", "Delayed Controls", Category.PHYSICS, Severity.DISRUPTIVE, 4, 15, "input-control", "delay", true, "A short captured input queue makes gameplay buttons arrive slightly late. Start with 100–150 ms, keep menus responsive, and forbid overlapping input interference.");
        add(72, "wrong-way-momentum", "Wrong-Way Momentum", Category.PHYSICS, Severity.DISRUPTIVE, 0, 15, "player-motion", "reverse", true, "Reverse ordinary horizontal momentum once while leaving button meanings unchanged. Reconcile X speed, ground speed and facing; exclude scripted launches and attachments.");
        add(73, "cruise-sonic", "Cruise Sonic", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "player-motion", "cruise", true, "Briefly run automatically at a bounded speed while the player still controls jumping. Qualified free terrain only; preserve menu control and provide immediate safe cancellation.");
        add(74, "short-fuse-shots", "Short Fuse Shots", Category.PHYSICS, Severity.ORDINARY, 10, 60, "projectile", "short-range", true, "Qualified badnik projectiles disappear after a shorter travel distance. Change lifetime only; preserve firing cadence and exclude boss or parent-constrained shots.");
        add(75, "ring-leak", "Ring Leak", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "economy", "leak", true, "Lose one ring at clear fixed intervals during a short leak. Retain at least one ring; no fake hurt spill or inventory restoration at expiry.");
        add(76, "animal-amnesty", "Animal Amnesty", Category.PHYSICS, Severity.ORDINARY, 10, 60, "challenge", "amnesty", true, "Pass a short section without defeating a badnik to earn a reward. Exclude bosses and mandatory defeat gates; failing ends the bonus instead of killing Sonic.");
        add(77, "badnik-blind-spot", "Badnik Blind Spot", Category.PHYSICS, Severity.ORDINARY, 8, 60, "enemy-rule", "blind-spot", true, "Qualified enemies keep patrolling but ignore player-triggered targeting for a while. Contact remains dangerous; pause only target acquisition, not native patrol clocks.");
        add(78, "tails-taxi", "Tails Taxi", Category.DISPLACEMENT, Severity.MAJOR, 8, 8, "transport", "carry", false, "A temporary helper uses a qualified native carry interaction to cross a small gap. End at an admitted landing; preserve team identity and reject existing attachments or event boundaries.");
        add(79, "rescue-buddy", "Rescue Buddy", Category.PHYSICS, Severity.ORDINARY, 15, 60, "transport", "companion", true, "Admit one temporary native sidekick helper for a short visit. Compatible teams only; capture roster/AI identity and remove only the leased helper.");
        add(80, "block-staircase", "Block Staircase", Category.PHYSICS, Severity.ORDINARY, 8, 60, "encounter", "block-stack", true, "A small temporary stack of native solid objects offers an optional new route upward. Do not move occupied solids; validate placement and reconcile support before cleanup.");
        add(81, "platform-hitchhike", "Platform Hitchhike", Category.DISPLACEMENT, Severity.DISRUPTIVE, 6, 15, "transport", "ride", true, "Briefly enter a nearby qualified moving platform’s native ride interaction. Native contact must admit the ride; retain normal detachment and a safe cutoff.");
        add(82, "flash-flood", "Flash Flood", Category.PHYSICS, Severity.MAJOR, 6, 6, "fluid", "flood", false, "A qualified local area briefly uses native underwater movement and breathable protection. Requires a coherent fluid context, water visuals and safe exit; never rewrite another event owner.");
        add(83, "rolling-rampage", "Rolling Rampage", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "player-motion", "roll", true, "Enter a bounded rolling attack mode for a short wrecking-ball run. Keep native collision and safe uncurl; qualify character behavior and free terrain.");
        add(84, "eggman-blaster", "Eggman Blaster", Category.PHYSICS, Severity.ORDINARY, 10, 60, "projectile", "player-fire", true, "Temporarily let an intentional gameplay button fire a few ROM-art shots at qualified badniks. New player-attack contract, capped ammunition and firing rate; do not repurpose hostile factories blindly.");
        add(85, "firework-shots", "Firework Shots", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "projectile", "impact-blast", true, "A few qualified projectile impacts produce bounded native-art blasts. Separate impact/child budget; preserve shield deflection, collision ownership and normal shot cadence.");
        add(86, "chain-reaction", "Chain Reaction", Category.PHYSICS, Severity.DISRUPTIVE, 10, 15, "enemy-rule", "chain-reaction", true, "A defeated badnik triggers a small blast that can defeat one nearby eligible enemy. No bosses, radial player launch or unlimited recursion; cap blast generations and rewards.");
        add(87, "falling-debris", "Falling Debris", Category.PHYSICS, Severity.DISRUPTIVE, 6, 15, "encounter", "debris", true, "Telegraphed native falling blocks or debris briefly threaten a small area. Warning and escape space required; bound hazards and retain their native collision/lifetime.");
        add(88, "speed-trap", "Speed Trap", Category.PHYSICS, Severity.DISRUPTIVE, 8, 15, "encounter", "speed-trap", true, "Fast movement arms a visible warning, then places one breakable native obstacle ahead. Safe encounter geometry only; discard the armed trigger at expiry and never persist it across loads.");
        add(89, "badnik-truce", "Badnik Truce", Category.PHYSICS, Severity.ORDINARY, 6, 60, "enemy-rule", "truce", true, "Ordinary badniks and players briefly cannot hurt one another; AI continues. Two-way contact policy, unlike player invincibility; exclude bosses and route-owned destructibles.");
        add(90, "grounded", "Grounded", Category.PHYSICS, Severity.DISRUPTIVE, 4, 15, "input-control", "jump-gate", true, "Fresh ordinary jumps are unavailable briefly; running, rolling and spindash remain. Only free movement on a flat floor qualifies; existing jumps and native object/scripted controls retain authority.");
        add(91, "brake-check", "Brake Check", Category.PHYSICS, Severity.DISRUPTIVE, 0, 15, "player-motion", "stop", true, "Cancel ordinary ground momentum once for a surprise stop. No airborne cancellation or repeated hold; exclude transport and mandatory-speed situations.");
        add(92, "landmark-hunt", "Landmark Hunt", Category.PHYSICS, Severity.ORDINARY, 30, 60, "challenge", "landmark", true, "Show a ROM-rendered landmark thumbnail and reward reaching the nearby location. Known reachable destination in the current act; no teleport, exit lock or permanent progression change.");
        add(93, "skill-chain", "Skill Chain", Category.PHYSICS, Severity.ORDINARY, 20, 60, "challenge", "skill-chain", true, "Link jumps, ring pickups and badnik defeats into a temporary combo challenge. Use native semantic events and a captured ledger; rewards buy only a bounded Chaos respite.");
        add(94, "jukebox-surprise", "Jukebox Surprise", Category.VISUAL, Severity.ORDINARY, 3, 3, "audio", "music", true, "Choose another S3K ROM track for at most 180 native ticks (three nominal seconds). Earlier native intent, warnings or source expiry can end it; native tick pacing affects duration. Resume current native intent.");
        add(95, "clang-landings", "Clang Landings", Category.VISUAL, Severity.ORDINARY, 15, 60, "audio", "clang", true, "Play an occasional comic native clang on eligible landings or completed spindashes. Use ROM-backed cues, event ownership and a strict rate limit; do not drown out native warning sounds.");
        add(96, "imaginary-chat", "Imaginary Chat", Category.VISUAL, Severity.ORDINARY, 20, 60, "chat", "simulated", true, "Fictional spectators react to pickups, falls and good moves with short on-screen messages. Label it simulated, keep the player visible and use deterministic text; no live service required.");
        add(97, "squashy-view", "Squashy View", Category.VISUAL, Severity.DISRUPTIVE, 8, 15, "scene-presentation", "input-squash", true, "Gameplay input gently deforms the whole gameplay image while physics remains native. Different from Jelly Objects; retain readable HUD and cap deformation without changing admission bounds.");
        add(98, "blueprint-zone", "Blueprint Zone", Category.VISUAL, Severity.ORDINARY, 12, 60, "scene-presentation", "outline", true, "Show simplified terrain outlines while players, hazards and required objects stay readable. Presentation only; do not substitute debug collision or alter terrain ownership.");
        add(99, "wrong-zone-scenery", "Wrong-Zone Scenery", Category.VISUAL, Severity.ORDINARY, 12, 60, "scene-presentation", "scenery", true, "Swap selected scenery artwork for surprising S3K ROM-backed donor artwork. Background/qualified scenery only; preserve terrain silhouettes, collision and object-scoped palette ownership.");
        add(100, "ring-bank-roulette", "Ring Bank Roulette", Category.PHYSICS, Severity.DISRUPTIVE, 0, 15, "economy", "balance", true, "Add or remove a randomly sampled zero to ten rings, retaining a nonnegative balance. Native award/loss semantics, no direct hurt or death; preserve checkpoint/world state and other policies.");
        add(101, "ring-pong", "Ring Pong", Category.VISUAL, Severity.ORDINARY, 20, 20, "pong", "pong", false, "Return the ring three times in a short native-art paddle challenge for five native rings. Safely hold the stock world; cancellation or failure resumes the act without death or teleport.");
        add(102, "ring-snake", "Ring Snake", Category.VISUAL, Severity.ORDINARY, 20, 20, "sidegame", "snake", false, "Collect rings on a small Snake board to earn a short quiet interval. Shared modal hold and hard deadline; no actual level pickups or persistent inventory farming.");
        add(103, "monitor-stack", "Monitor Stack", Category.VISUAL, Severity.ORDINARY, 20, 20, "sidegame", "stack", false, "Clear a few lines of monitor-shaped blocks to earn breathing room from Chaos. Shared safe world hold; cap rewards and return immediately on cancellation.");
        add(104, "shield-beat", "Shield Beat", Category.VISUAL, Severity.ORDINARY, 20, 20, "sidegame", "rhythm", false, "Match three controller buttons to a brief native-cue rhythm sequence. Capture timing/reward outcomes; preserve audio priorities and avoid gameplay-input bleed on return.");
        add(105, "badnik-defence", "Badnik Defence", Category.VISUAL, Severity.ORDINARY, 20, 20, "sidegame", "defence", false, "A short optional lane-defence sidegame uses ROM-backed badnik and spring artwork. New minigame mechanics; safe stock-world hold and no run penalty for losing.");
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
