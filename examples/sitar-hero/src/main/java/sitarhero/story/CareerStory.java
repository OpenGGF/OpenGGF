package sitarhero.story;

import sitarhero.model.Roster;

import java.util.ArrayList;
import java.util.List;

import static sitarhero.model.Roster.*;

/** Original, self-contained ROM tours. Scripts are built on demand: creator code has no static object collections. */
public final class CareerStory {
    private CareerStory() { }

    public record Line(Roster speaker, String text, boolean action) {
        public Line {
            display(text, 150);
            if (speaker == null && !action) throw new IllegalArgumentException("A stage direction must be an action");
        }
    }

    public record Scene(String id, String title, List<Line> lines) {
        public Scene {
            display(id, 80);
            display(title, 50);
            lines = List.copyOf(lines);
        }
    }

    /** An unavailable optional performer contributes no line; the tour's own ROM is always required. */
    public static Scene scene(String sceneId, Roster performer, List<String> installedGames) {
        if (sceneId == null || installedGames == null) throw new IllegalArgumentException("Scene and ROM list required");
        String game = sceneId.startsWith("s1-") ? "s1" : sceneId.startsWith("s2-") ? "s2"
                : sceneId.startsWith("s3k-") ? "s3k" : "";
        if (!installedGames.contains(game)) throw new IllegalArgumentException("Unavailable tour scene: " + sceneId);
        return switch (game) {
            case "s1" -> sonicOne(sceneId, performer, installedGames);
            case "s2" -> sonicTwo(sceneId, performer, installedGames);
            case "s3k" -> sonicThree(sceneId, performer, installedGames);
            default -> throw new IllegalArgumentException("Unknown scene: " + sceneId);
        };
    }

    public static String retryQuip(Roster performer) {
        return switch (performer) {
            case SONIC -> "One more? I heard a good groove in there.";
            case ROBOTNIK -> "A rehearsal. Obviously. Let us keep the good bits.";
            case TAILS -> "I know where I lost it. Can we try that again?";
            case SILVER_SONIC -> "Timing log saved. Feet repositioned. Ready.";
            case KNUCKLES -> "That passage needs another go. So do I.";
            case MECHA_SONIC -> "Playback reviewed. A claw taps the opening count.";
            case EGG_ROBO -> "The sticks are retrieved. The stool is adjusted.";
        };
    }

    public static String successQuip(Roster performer) {
        return switch (performer) {
            case SONIC -> "Hear that crowd? Let's give them another one.";
            case ROBOTNIK -> "That applause has excellent taste.";
            case TAILS -> "Yes! That little change in the bridge worked!";
            case SILVER_SONIC -> "A precise bow. One foot keeps tapping.";
            case KNUCKLES -> "Good show. I might stay for the next set.";
            case MECHA_SONIC -> "The final note fades. The bow lasts a beat longer.";
            case EGG_ROBO -> "A cymbal salute. The bouquet is carefully shelved.";
        };
    }

    private static void display(String text, int bound) {
        if (text == null || text.isBlank() || text.length() > bound
                || text.chars().anyMatch(c -> c < 32 || c > 126))
            throw new IllegalArgumentException("Unsupported story text");
    }

    private static Line say(Roster speaker, String text) { return new Line(speaker, text, false); }
    private static Line act(Roster speaker, String text) { return new Line(speaker, text, true); }

    /** The selected actor gets one authored, local contribution before the scene's closing beat. */
    private static Scene make(String id, String title, Roster performer, List<String> games,
                              String sonic, String robotnik, String tails, String silver,
                              String knuckles, String mecha, String egg, Line... authored) {
        var lines = new ArrayList<>(List.of(authored));
        if (performer != null && performer.available(games)) {
            String text = switch (performer) {
                case SONIC -> sonic;
                case ROBOTNIK -> robotnik;
                case TAILS -> tails;
                case SILVER_SONIC -> silver;
                case KNUCKLES -> knuckles;
                case MECHA_SONIC -> mecha;
                case EGG_ROBO -> egg;
            };
            boolean action = performer == SILVER_SONIC || performer == MECHA_SONIC || performer == EGG_ROBO;
            lines.add(lines.size() - 1, new Line(performer, text, action));
        }
        return new Scene(id, title, lines);
    }

    private static Scene sonicOne(String id, Roster p, List<String> games) {
        return switch (id) {
            case "s1-green-hill-intro" -> make(id, "A stage with no fence", p, games,
                    "Leave that front patch clear. The little ones can't see over the monitors.",
                    "My opening solo is worth the walk. Even for people who insist on walking slowly.",
                    "The hill carries sound well. I'll check the far side myself.",
                    "Silver Sonic unfolds a tiny stool, tests it, then offers it to the shortest spectator.",
                    "A public show? Then that path stays public.",
                    "Mecha Sonic shades the front row with a broad speaker panel.",
                    "Egg Robo turns the ticket booth around. It now dispenses programmes.",
                    act(null, "Robotnik unveils a gleaming stage. Sonic rolls the fence away before the ribbon is cut."),
                    say(ROBOTNIK, "My genius deserves a proper audience. Preferably one that knows when to applaud."),
                    say(SONIC, "Free shows, open paths, and you actually play. I'll bring the crowd."),
                    say(ROBOTNIK, "You suspect a scheme, yet you volunteer?"),
                    say(SONIC, "Sure. I also want to hear that guitar. I'll keep an eye on the rest."),
                    say(ROBOTNIK, "Then try to keep both ears on it. I did not build stereo for nothing."));
            case "s1-marble-intro" -> make(id, "Room for the quiet notes", p, games,
                    "I'll take the slow entrance. Let them hear the place first.",
                    "That echo requires space. My solo shall demonstrate restraint. Briefly.",
                    "There's a second echo under the first. We could answer it.",
                    "Silver Sonic lowers its volume dial and waits for the echo to finish before moving.",
                    "Use the room. Don't shake pieces off it.",
                    "Mecha Sonic catches a wobbling stone and sets it level, without breaking the count.",
                    "Egg Robo lays felt beneath the drum stand, then tests one soft tap.",
                    act(null, "A soft note returns from the ancient walls. Sonic lets it finish."),
                    say(ROBOTNIK, "You usually race ahead. Has a corridor finally defeated you?"),
                    say(SONIC, "Listen. The room's got a part."),
                    say(ROBOTNIK, "A competent observation. Turn my speakers inward; the ceiling is stealing the bass."),
                    act(null, "They turn the heavy cabinet together. The next chord reaches the back row clearly."),
                    say(SONIC, "Nice. Keep that bit. Even if the ceiling won't applaud."));
            case "s1-spring-yard-intro" -> make(id, "The audience has a rhythm", p, games,
                    "The late clap is my favourite. It keeps surprising the snare.",
                    "If they insist on playing, I shall give them a better entrance.",
                    "We can shift the bridge around their clap. We don't have to correct it.",
                    "Silver Sonic's perfect clap drifts half a beat, deliberately, toward the crowd's rhythm.",
                    "I can keep the count. You can keep the surprise.",
                    "Mecha Sonic holds a bouncing spring still until a spectator finishes tying a shoe.",
                    "Egg Robo points its flashing count-in lamp toward the back row.",
                    act(null, "The crowd claps a crooked rhythm. Robotnik's conducting baton stops midair."),
                    say(ROBOTNIK, "That is not the rhythm I supplied."),
                    say(SONIC, "It's the one they brought. Can you put something under it?"),
                    act(null, "Robotnik tries a bass figure. The odd clap suddenly fits."),
                    say(ROBOTNIK, "Naturally. A lesser composer would have blamed the audience."),
                    say(SONIC, "Good thing we booked you, then."));
            case "s1-labyrinth-intro" -> make(id, "Keep a dry corner", p, games,
                    "I'll play from here. Nobody needs to wade in just to get a good view.",
                    "I have waterproofed the cabinet. I would prefer to receive praise before the splash test.",
                    "A raised cable run and a dry exit. Then we can enjoy the weird acoustics.",
                    "Silver Sonic lifts both feet onto its stool and resumes the count with its elbows.",
                    "The exit comes before the encore. Keep it clear.",
                    "Mecha Sonic holds a shelter panel over the tuning pegs, not over itself.",
                    "Egg Robo swaps the soggy programme stack for a dry one from inside its hat.",
                    act(null, "Water creeps toward the stage. Sonic starts moving the front benches uphill."),
                    say(ROBOTNIK, "The set is about to begin."),
                    say(SONIC, "Then give them a minute. I'd like everybody listening, not rescuing their shoes."),
                    say(ROBOTNIK, "My cabinet floats. Put the small instruments on it."),
                    say(SONIC, "You thought of that?"),
                    say(ROBOTNIK, "I travel with expensive instruments and you. I think of a great many things."));
            case "s1-star-light-intro" -> make(id, "An honest sound check", p, games,
                    "Let that last note hang. Nobody here seems in a hurry.",
                    "The quiet passage stays. The applause will simply have to wait.",
                    "No extra lights for this one. We can hear the stars just fine.",
                    "Silver Sonic dims its eye lamps, leaving a small pool of light over the strings.",
                    "A late show can still end gently.",
                    "Mecha Sonic lowers its heavy hands onto the railing without making a sound.",
                    "Egg Robo folds the spotlight shutters until the moon is brighter than the stage.",
                    act(null, "Before the gates open, Robotnik picks a quiet melody. Sonic sits on a railing to listen."),
                    say(SONIC, "Didn't know you played that way."),
                    say(ROBOTNIK, "I do not put every idea on a billboard."),
                    say(SONIC, "Put this one in the set. People at the back have been asking for something slower."),
                    say(ROBOTNIK, "You have been taking requests?"),
                    say(SONIC, "Between solos. Yours take a while."));
            case "s1-scrap-brain-intro" -> make(id, "Follow the other cable", p, games,
                    "The band line stays. That control line comes out.",
                    "Mind the soldering! That cable took longer than your entire sound check.",
                    "Two circuits. One for music, one for the gates. We only agreed to the first.",
                    "Silver Sonic traces the control cable with a finger, then disconnects only that plug.",
                    "I'll watch the doors. Keep the musicians playing.",
                    "Mecha Sonic braces the open gate with one heel while reaching for the stage socket.",
                    "Egg Robo labels the two plugs: SOUND and NOT SOUND.",
                    act(null, "Sonic follows a second cable from the speakers to a bank of gate controls."),
                    say(SONIC, "Your bass cabinet floats. Does the applause switch lock people in?"),
                    say(ROBOTNIK, "It retains an enthusiastic audience."),
                    say(SONIC, "A crowd gets to leave, Doc. We had a deal."),
                    say(ROBOTNIK, "Disconnecting that line will spoil my spectacular finale."),
                    say(SONIC, "It'll spoil the lock. Your music can handle the finale."));
            case "s1-finale-intro" -> make(id, "A finale worth hearing", p, games,
                    "I want to hear the ending you wrote. All of it.",
                    "My hands are quite capable of a finale without assistance from a gate switch.",
                    "The sound line's clean. The whole stage is yours.",
                    "Silver Sonic tests the open gate, gives the stage a green signal, then takes its place.",
                    "Doors open. Audience ready. Play.",
                    "Mecha Sonic lifts the final loose control lead clear of the performers' feet.",
                    "Egg Robo replaces the remote-control pedestal with a music stand.",
                    say(ROBOTNIK, "You removed my triumphant control circuit. Why are you still here?"),
                    say(SONIC, "We promised them a show. You've got a good ending."),
                    say(ROBOTNIK, "A good ending? It has three key changes."),
                    say(SONIC, "I noticed. I'll leave room for the third."),
                    act(null, "Robotnik checks the tuning himself. The open gates frame a waiting crowd."),
                    say(ROBOTNIK, "Very well. Watch the downbeat, not my moustache."));
            case "s1-encore-intro" -> make(id, "The song they brought back", p, games,
                    "They're singing the Star Light bit. Let's start there.",
                    "I wrote a quiet melody, and they have made it loud. Remarkably good judgment.",
                    "Their harmony's different from the record. I like it.",
                    "Silver Sonic listens to the audience's melody, then plays a gentle answer instead of a correction.",
                    "No speeches. They've already started the song.",
                    "Mecha Sonic turns its stage monitor outward so the far benches can hear the quiet opening.",
                    "Egg Robo hands its spare pick to the front row and retrieves a smaller one.",
                    act(null, "The crowd asks for the Star Light melody. Robotnik pretends to inspect a tuning peg."),
                    say(ROBOTNIK, "They remember that one?"),
                    say(SONIC, "Yeah. Turns out you don't need a billboard for everything."),
                    say(ROBOTNIK, "I shall allow a tasteful singalong."),
                    say(SONIC, "They already started."),
                    say(ROBOTNIK, "Then I shall make a tasteful entrance."));
            case "s1-outro" -> make(id, "Open gates, full house", p, games,
                    "Same deal next time: a good show, and everyone gets to choose to come.",
                    "I earned that ovation with my hands. Do not confuse it with the control circuit.",
                    "I've saved the crowd's version of the melody. We should play it again.",
                    "Silver Sonic carries the little stool back to the front row for the next show.",
                    "You left the paths open. I'll help carry the stage.",
                    "Mecha Sonic rolls the fence farther from the stage, then bows toward the remaining audience.",
                    "Egg Robo keeps one programme and packs the rest beside the instruments.",
                    act(null, "The show ends with an ovation. Outside, the disconnected control box receives none."),
                    say(SONIC, "That third key change was worth it."),
                    say(ROBOTNIK, "Of course. My takeover circuit was sabotaged; my concert was magnificent."),
                    say(SONIC, "People stayed with the gates open. Keep that part in your next plan."),
                    say(ROBOTNIK, "I have several next plans. One even concerns music."),
                    say(SONIC, "Great. I'll bring the crowd. And check the cables."));
            default -> throw new IllegalArgumentException("Unknown scene: " + id);
        };
    }

    private static Scene sonicTwo(String id, Roster p, List<String> games) {
        return switch (id) {
            case "s2-emerald-hill-intro" -> make(id, "Bring your own rhythm", p, games,
                    "Tails picked the first arrangement. Wait till you hear the bridge.",
                    "My musicians may offer suggestions. Excellent ones may survive my pencil.",
                    "I want a bridge the crowd can answer. Not just a longer solo.",
                    "Silver Sonic sets its count-in display beside a handwritten sign: EVERYONE WELCOME.",
                    "That's a big stage. Leave room for people who aren't on it.",
                    "Mecha Sonic checks the rear speaker, then moves it so the picnic benches can hear.",
                    "Egg Robo plants the free-show sign firmly and tests whether it obscures the drummer.",
                    say(ROBOTNIK, "Welcome to my perfectly engineered tour. Free admission; admiration strongly encouraged."),
                    say(SONIC, "Free shows and open exits. I'll bring people. You'll keep any scheme away from the music."),
                    say(TAILS, "I brought an arrangement. Can we try it before you declare everything perfect?"),
                    say(ROBOTNIK, "A handwritten bridge? My machinery could supply one instantly."),
                    say(TAILS, "Sure. But I'd like to hear mine."),
                    say(SONIC, "Me too. You take the count, Tails."));
            case "s2-chemical-plant-intro" -> make(id, "The manual switch", p, games,
                    "I'll follow the new count. Try not to look so surprised.",
                    "The bridge is clever. The blue solvent near my guitar is considerably less so.",
                    "Manual switch fitted. Next time Sonic improvises, we can follow him.",
                    "Silver Sonic offers Tails the correct spanner before the tool request is finished.",
                    "Label that switch clearly. Nobody should have to guess in the middle of a show.",
                    "Mecha Sonic steadies the cabinet while Tails reaches its back panel.",
                    "Egg Robo catches a dropped screw, then proudly presents the entire tray of spare screws.",
                    act(null, "Tails opens a sequencer cabinet. Robotnik leans in, despite trying to look uninterested."),
                    say(TAILS, "This counterpoint's yours? It leaves a neat space for the melody."),
                    say(ROBOTNIK, "At last, somebody inspecting my work instead of running on it."),
                    say(SONIC, "Can it leave space if I change the entrance?"),
                    say(TAILS, "Yes. If you tell us first. Surprising the audience is different from surprising the band."),
                    say(SONIC, "Fair. I'll give you a nod. A normal-sized one."));
            case "s2-aquatic-ruin-intro" -> make(id, "A tune without a plug", p, games,
                    "I'll wait for the second echo. You were right about giving it space.",
                    "Ancient builders understood resonance. Their cable management leaves something to be desired.",
                    "I'd rather leave the amplifier off. The arch already does what we need.",
                    "Silver Sonic plays one quiet test note, counts both echoes, and puts the amplifier plug away.",
                    "That arch has stood a long time. Let's give it a tune instead of another hole.",
                    "Mecha Sonic positions its music stand in the shade without touching the old carvings.",
                    "Egg Robo exchanges its mallets for soft brushes and holds up the discarded plug.",
                    say(ROBOTNIK, "We shall attach a speaker to that arch."),
                    say(TAILS, "Let's hear it first. I don't want to drill into something we haven't understood."),
                    act(null, "Tails plays a phrase. Two soft echoes answer from the far steps."),
                    say(SONIC, "Okay, you've sold me on the unplugged set."),
                    say(ROBOTNIK, "I was, naturally, about to propose an acoustic demonstration."),
                    say(TAILS, "Good. Then you can carry the unplugged speaker back."));
            case "s2-casino-night-intro" -> make(id, "House band, house lights", p, games,
                    "Let the wheel have its little dance. We can still keep time.",
                    "One flourish, then a precise landing. The difference between drama and a dropped pick.",
                    "The lights need a slower pattern. The band shouldn't have to chase them.",
                    "Silver Sonic's perfect bow spins a loose roulette wheel. It watches, then bows at the wheel's final click.",
                    "I like the bass line. I don't need the ceiling to flash along with it.",
                    "Mecha Sonic catches a rolling chip beneath one claw and returns it to the startled front row.",
                    "Egg Robo collects three flashing bulbs and replaces them with steady warm lamps.",
                    act(SILVER_SONIC, "Silver Sonic gives a flawless count-in. A loose wheel adds seven unexpected clicks."),
                    say(TAILS, "Can we keep those? They sound good against the bass."),
                    say(ROBOTNIK, "My performer was not designed to accompany furniture."),
                    act(SILVER_SONIC, "Silver Sonic tilts its head toward the wheel, then repeats the phrase around its clicks."),
                    say(SONIC, "Looks like it just designed the next bit."),
                    say(ROBOTNIK, "Clearly my design has excellent taste in furniture."));
            case "s2-hill-top-intro" -> make(id, "A bridge in the open air", p, games,
                    "My fast entrance can wait. That little bridge wants a run at it.",
                    "A smaller cabinet? An outrage. A clearer tone? A persuasive outrage.",
                    "This is the sound I wanted back at Emerald Hill. Hear how the bridge opens up?",
                    "Silver Sonic weights the loose score with its palm and points to Tails' handwritten bridge.",
                    "Wind's from the west. Put the quiet players on this side.",
                    "Mecha Sonic turns its shoulders into the wind to shelter the music stand.",
                    "Egg Robo anchors three page corners, then discovers it needs another hand for the fourth.",
                    act(null, "On the windy ridge, Tails brings out a small amplifier instead of Robotnik's giant cabinet."),
                    say(ROBOTNIK, "You have reduced my presence by four speakers."),
                    say(TAILS, "Listen before we add them back."),
                    act(null, "The new tone carries a clear little melody over the hill. Sonic stops fiddling with his strap."),
                    say(SONIC, "That's your bridge. Let's leave it room."),
                    say(ROBOTNIK, "Two speakers may remain in reserve. For artistic emergencies."));
            case "s2-mystic-cave-intro" -> make(id, "The back row answers", p, games,
                    "There they are. I'll count them in from this end.",
                    "I can hear them perfectly. They are applauding before my solo; an understandable mistake.",
                    "We can start the answer from the far bench. They'll know they're part of it.",
                    "Silver Sonic redirects its count-in lamp toward the distant benches instead of the front row.",
                    "I'll check the side passage before the lights go down.",
                    "Mecha Sonic angles a reflector so a forgotten row becomes visible from the stage.",
                    "Egg Robo carries a lantern to the back bench and gives its occupants the opening signal.",
                    say(SONIC, "Those lights don't reach the people behind the bend. I'll move a speaker."),
                    say(TAILS, "No need. They can hear; they can't see your count. Let me turn the lamp."),
                    act(null, "The back bench lights up. A shy clap joins the opening rhythm."),
                    say(ROBOTNIK, "That answer would make an effective refrain."),
                    say(TAILS, "The crowd's bridge, then. Mine can lead into it."),
                    say(SONIC, "Sounds like we got a bigger band without a bigger stage."));
            case "s2-oil-ocean-intro" -> make(id, "Hands on the controls", p, games,
                    "I'll keep the count while you fix it. Take your time.",
                    "A clean tone requires a clean contact. Pass the cloth, not the enthusiastic commentary.",
                    "That repair's neat. I'm still keeping the manual switch.",
                    "Silver Sonic holds the contact lamp steady while Robotnik repairs the connector.",
                    "I'll move the waiting crowd out of the smoke. Call when the stage is ready.",
                    "Mecha Sonic shelters the open electronics beneath its metal forearm.",
                    "Egg Robo produces a spotless cloth from a compartment marked EXTREMELY IMPORTANT CLOTHS.",
                    act(null, "Salt and grime silence the lead cabinet. Robotnik takes the soldering iron himself."),
                    say(TAILS, "I can bypass it, if you want."),
                    say(ROBOTNIK, "No. That tone is mine. I want to hear it properly."),
                    say(SONIC, "We'll wait. They've got Tails' bridge to clap to."),
                    act(null, "The repaired cabinet sings. Tails checks the joint and gives Robotnik a pleased nod."),
                    say(ROBOTNIK, "Now that is a useful review. Much shorter than your usual solos, hedgehog."));
            case "s2-metropolis-intro" -> make(id, "A rest that won't obey", p, games,
                    "My stop signal is a hand in the air. Even a genius can afford to look up.",
                    "The machinery must learn to wait for my entrance. I do not rush for machinery.",
                    "A rest is part of the tune. If your system can't stop, it can't play it.",
                    "Silver Sonic raises its stop lamp and stays completely still through the unscripted silence.",
                    "That exit motor started on the downbeat. Why is it part of the band?",
                    "Mecha Sonic touches the moving gate belt, then points toward its separate control socket.",
                    "Egg Robo pulls the drum machine's paper roll free and pins it beneath the live score.",
                    say(ROBOTNIK, "My automatic backing will maintain perfection through any pause."),
                    say(TAILS, "Let's test a pause."),
                    act(null, "Tails raises a hand. The players stop; the backing machine marches on alone."),
                    say(SONIC, "Sounds lonely."),
                    say(ROBOTNIK, "It follows the score exactly."),
                    say(TAILS, "The score says rest. We're keeping the manual switch."));
            case "s2-sky-chase-intro" -> make(id, "Follow the pilot's count", p, games,
                    "Your lead, Tails. I've got the little answer after the turn.",
                    "I can play seated. My dramatic standing solo has been postponed by aviation.",
                    "I'll call the turns and the count. No surprise leaps until we're back on a stage.",
                    "Silver Sonic clips its feet to the deck, checks the clip, then plays a buoyant two-note answer.",
                    "The pilot calls the turns. The rest of us listen.",
                    "Mecha Sonic locks its heavy case into a deck bracket and tests the balance twice.",
                    "Egg Robo fastens a strap around its cymbal before saluting the pilot with a stick.",
                    say(SONIC, "I could leap up for the opening chord."),
                    say(TAILS, "You could. I'd rather not correct the balance while you're showing off."),
                    say(SONIC, "Right. Your lead, Tails. Give me a place to come in."),
                    say(ROBOTNIK, "For once, a sensible request from the blue section."),
                    say(TAILS, "You too, Doctor. That cabinet stays tied down."),
                    say(ROBOTNIK, "It is restrained by excellent engineering. I am restrained by this seat belt."));
            case "s2-wing-fortress-intro" -> make(id, "The perfect substitute", p, games,
                    "They're waiting for us. Not just for something that sounds like us.",
                    "That substitute reproduced my phrasing beautifully. Its bow is regrettably modest.",
                    "You can help us play. You don't have to replace us.",
                    "Silver Sonic closes the substitute score and moves its chair into the band's semicircle.",
                    "A recording doesn't notice someone asking for one more verse.",
                    "Mecha Sonic turns a playback monitor toward the band, then waits for their live count.",
                    "Egg Robo lowers a cardboard Sonic, folds it neatly and wheels in a real music stand.",
                    act(SILVER_SONIC, "Silver Sonic demonstrates an automatic replacement set, including a precisely timed mechanical bow."),
                    say(ROBOTNIK, "An entire tour without arguments over the bridge."),
                    say(TAILS, "It plays my notes. It doesn't know why I changed them at Hill Top."),
                    say(SONIC, "Or when the back row wants another go."),
                    act(SILVER_SONIC, "Silver Sonic looks toward the waiting crowd and holds the next note for their uneven clap."),
                    say(ROBOTNIK, "A surprisingly useful deviation. I shall inspect it after the show."));
            case "s2-death-egg-intro" -> make(id, "One switch too many", p, games,
                    "Keep the sound powered. We promised a final show, not a final lock-in.",
                    "Please stop calling my command transmitter a switch. It is a considerably larger achievement.",
                    "I built the manual backup. I can route the music around this command circuit.",
                    "Silver Sonic pulls the COMMAND lead, then holds the MUSIC lead up for Tails to inspect.",
                    "Those seats belong to the audience. So does the choice to leave them.",
                    "Mecha Sonic plants one foot against the sliding door and holds the gap open.",
                    "Egg Robo covers the command button with a bowl of complimentary earplugs.",
                    act(null, "Tails finds the automatic backing linked to the doors and a transmitter marked GLOBAL COMMAND."),
                    say(TAILS, "This isn't keeping the band together. You're planning to control the whole tour."),
                    say(ROBOTNIK, "A world listening to one conductor. Consider the efficiency."),
                    say(SONIC, "You needed us because people wanted a live show. Let them choose what they hear."),
                    say(TAILS, "The manual circuit carries music without commands. I kept it for a reason."),
                    say(ROBOTNIK, "Yes. Your irritatingly thorough reason."));
            case "s2-finale-intro" -> make(id, "The band takes a breath", p, games,
                    "Give us the pause. We know how to come back in together.",
                    "The command circuit is disconnected. My solo remains entirely operational.",
                    "There's room for your big solo and my little bridge. I'll signal the handoff.",
                    "Silver Sonic shuts the perfect-timing display and watches the conductor's hands instead.",
                    "The doors work. The band works. That's enough machinery for tonight.",
                    "Mecha Sonic folds the unused control pedestal and makes space for the live players.",
                    "Egg Robo turns the huge countdown dial over, revealing a handwritten READY sign.",
                    say(ROBOTNIK, "You intend to risk my finale on an unscheduled silence?"),
                    say(TAILS, "A breath. We'll watch each other and come back in."),
                    say(SONIC, "Like the count on the plane. It worked when I listened."),
                    act(SILVER_SONIC, "Silver Sonic raises its stop lamp, then waits for Tails' hand before lowering it."),
                    say(ROBOTNIK, "Very well. I reserve the right to make a spectacular entrance after the breath."),
                    say(TAILS, "That's the idea."));
            case "s2-encore-intro" -> make(id, "Two bridges and a wheel", p, games,
                    "Start with Tails' bridge. They've earned a turn before my solo.",
                    "I shall contribute the bass figure. Even your improvised furniture cannot do everything.",
                    "My bridge, their answer, then the Doctor's bass. That's the version I want to keep.",
                    "Silver Sonic rolls the little roulette wheel onstage and waits for the last click to count in.",
                    "That wheel's coming home with the band, isn't it?",
                    "Mecha Sonic braces the little wheel's stand, then releases it for the opening spin.",
                    "Egg Robo unfolds the handwritten bridge, carefully leaving the new pencilled notes intact.",
                    say(TAILS, "Remember my handwritten bridge? I want to use the crowd's answer after it."),
                    say(SONIC, "It's your arrangement. I'm in."),
                    say(ROBOTNIK, "A sound structure. It does require my bass figure."),
                    act(SILVER_SONIC, "Silver Sonic produces the loose roulette wheel from a carefully padded case."),
                    say(TAILS, "And that, apparently."),
                    say(ROBOTNIK, "I refuse to be billed below furniture."));
            case "s2-outro" -> make(id, "A band that can change its mind", p, games,
                    "Next time I get an idea, you'll get the nod before the leap.",
                    "My performance earned that applause. The failed takeover is a separate engineering discussion.",
                    "I'll keep the arrangement. We changed it together, and it got better.",
                    "Silver Sonic saves the live performance, including the silence its old score did not contain.",
                    "You left room for the crowd. They noticed.",
                    "Mecha Sonic closes the empty command cabinet and carries an instrument case instead.",
                    "Egg Robo carefully packs the wheel beside the drums, leaving room for a future surprise.",
                    act(null, "The concert ends on a shared chord. The command transmitter remains dark."),
                    say(TAILS, "The manual switch saved the show. But we still had to choose what to play."),
                    say(SONIC, "Good bridge. Good call on the plane, too."),
                    say(ROBOTNIK, "My takeover failed. My band, on the other hand, was worth hearing."),
                    act(SILVER_SONIC, "Silver Sonic makes its precise bow, then adds a small uneven foot tap toward the crowd."),
                    say(TAILS, "Let's keep that bit."));
            default -> throw new IllegalArgumentException("Unknown scene: " + id);
        };
    }

    private static Scene sonicThree(String id, Roster p, List<String> games) {
        return switch (id) {
            case "s3k-angel-island-intro" -> make(id, "The guardian's sound check", p, games,
                    "Free shows, clear paths. I'll help bring people without trampling the place.",
                    "My music draws the audience. Your caution may keep them from stepping on my leads.",
                    "I'll inspect the stage wiring. Knuckles knows the paths better than we do.",
                    "Silver Sonic places its instrument case outside the marked nesting patch and waits for a nod.",
                    "I'll check the perimeter route myself. Nobody gets a shortcut through the nesting grounds.",
                    "Mecha Sonic lifts the stage leg away from a nest, then lowers it onto bare stone.",
                    "Egg Robo carries the security route map with both hands and stops at the first closed path.",
                    say(ROBOTNIK, "My island tour offers free music and a temporary security line for the crowded paths."),
                    say(KNUCKLES, "The perimeter route's sensible. No digging, no closed trails, and I inspect the line."),
                    say(SONIC, "I'm here for the free shows. He's here for my audiences. I'm also watching his other plans."),
                    say(KNUCKLES, "Then watch the stage. I'll watch the island."),
                    say(TAILS, "And I'd like to hear how this forest answers a chord."),
                    say(KNUCKLES, "Softly, at first. There's a nest behind your amplifier."));
            case "s3k-hydrocity-intro" -> make(id, "Nobody fixes it alone", p, games,
                    "I'll hold the stand. Knuckles, you call when the water's right.",
                    "That turn of the wheel is almost musical. Do not get ambitious; that is my department.",
                    "Knuckles handles the water; I'll handle the contact. We need both steady.",
                    "Silver Sonic holds the dry lead high, then lowers it only after both workers signal.",
                    "I've used this channel before. The half turn is enough; don't force it.",
                    "Mecha Sonic braces the dripping cabinet while keeping the open connector above the splash line.",
                    "Egg Robo offers a towel to Tails, then a much larger one to the cabinet.",
                    act(null, "A waterwheel rattles the sound cabinet. Tails reaches for it; Knuckles stops the wheel instead."),
                    say(KNUCKLES, "You fix the contact. I'll keep the pressure steady."),
                    say(TAILS, "Thanks. I couldn't hold it and tune this at the same time."),
                    say(ROBOTNIK, "Half a turn clockwise. That wheel used to run the pumps; I measured its load."),
                    act(null, "Knuckles eases the wheel. The buzzing becomes a clean, low chord."),
                    say(KNUCKLES, "That's better. Now it sounds like the place instead of arguing with it."));
            case "s3k-marble-garden-intro" -> make(id, "A jewel of a speaker", p, games,
                    "We can build around the roots. The stage doesn't have to be square.",
                    "An irregular stage. Very well. My solos have never depended on right angles.",
                    "That smaller stand keeps the carvings visible. I'll use it.",
                    "Silver Sonic measures a gap between the roots, then carefully folds one leg of its stand.",
                    "Your speaker stays here. The real Emerald doesn't join the tour.",
                    "Mecha Sonic lifts the green cabinet over a root and settles it on the approved stone slab.",
                    "Egg Robo turns the huge green cabinet sideways, revealing the garden it had blocked.",
                    act(null, "Robotnik unveils an emerald-green cabinet. Knuckles moves its stand clear of the old roots."),
                    say(ROBOTNIK, "A jewel of a speaker. It would make an excellent Master Emerald."),
                    say(KNUCKLES, "It makes a fine speaker. The real one stays where it is."),
                    say(ROBOTNIK, "You have strong opinions for somebody who has not joined my band."),
                    say(KNUCKLES, "I'm the venue."),
                    say(SONIC, "And he's making room for us. Let's return the favour."));
            case "s3k-carnival-night-intro" -> make(id, "The guardian picks a chorus", p, games,
                    "We'll do the chorus twice. Everybody up there should get a turn.",
                    "Two choruses? Acceptable. I shall make the second bass line better.",
                    "I'll slow the lights down. More room for the tune, less work for everybody's eyes.",
                    "Silver Sonic watches a child's hesitant clap and gives the next count-in directly to that row.",
                    "I'd like the chorus again. Not because I missed it. Because I liked it.",
                    "Mecha Sonic lowers the front monitor so the smallest spectators can see the hands playing.",
                    "Egg Robo balances a programme on its head while guiding a family toward the clear viewing space.",
                    say(KNUCKLES, "The small crowd can't see from down here. Can you turn the stage toward that ledge?"),
                    say(SONIC, "You pick the safe spot. I'll shift the monitors."),
                    say(ROBOTNIK, "If we turn, the bass will reach the whole carousel. A useful improvement."),
                    act(null, "The stage turns. Knuckles starts tapping the chorus on the rail."),
                    say(TAILS, "Want us to play that part again?"),
                    say(KNUCKLES, "Yes. And don't make a production out of asking me."));
            case "s3k-icecap-intro" -> make(id, "Cold fingers, warm tone", p, games,
                    "I'll take the first warm-up. Save your fingers for that chorus you wanted.",
                    "These heaters are for the instruments. Musicians may benefit incidentally.",
                    "The quieter voicing carries better off the snow. Let's keep it.",
                    "Silver Sonic warms a tuning peg between its metal palms, then hands the instrument back gently.",
                    "I'll play the slow part. I know the echo from up here.",
                    "Mecha Sonic rotates a heater toward the shared tuning bench and leaves its own spot unheated.",
                    "Egg Robo distributes warm pick pouches, saving the last for the smallest instrument case.",
                    act(null, "The cold pulls every instrument out of tune. Robotnik installs small heaters under the stands."),
                    say(SONIC, "Good idea, Doc. Knuckles, you want the warm spot?"),
                    say(KNUCKLES, "Give it to Tails. He's doing the fiddly passage."),
                    say(TAILS, "Then you take the slow answer. You know how this ridge sounds."),
                    say(KNUCKLES, "I do. I'd like that."),
                    say(ROBOTNIK, "Excellent. An arrangement settled before all four of us freeze."));
            case "s3k-launch-base-intro" -> make(id, "A stage is not a launch pad", p, games,
                    "A fast ending, then a slow walk out. Give people time to carry their kids.",
                    "That vibration is deliberately balanced. I would appreciate applause after the balancing.",
                    "Your stabiliser's clever. I'm keeping its power separate from the stage lights.",
                    "Silver Sonic times the platform's sway and moves the loose chairs clear of its edge.",
                    "The approved security line follows the outer path. This extra trunk goes somewhere else.",
                    "Mecha Sonic grips the wobbling platform rail and waits until the drum stand is secured.",
                    "Egg Robo unfolds a seating plan, then draws a larger aisle across the middle.",
                    act(null, "A platform shudders beneath the set. Robotnik tunes its stabiliser while Tails steadies the lights."),
                    say(TAILS, "Nice counterweight. It follows the movement instead of fighting it."),
                    say(ROBOTNIK, "My equipment occasionally attracts informed praise. A welcome development."),
                    say(KNUCKLES, "The platform can move. The exit route can't. People need it after the show."),
                    say(SONIC, "We'll walk them out first. You can test your big exit with an empty house."),
                    say(ROBOTNIK, "An empty house is tragically short of witnesses. Very well."));
            case "s3k-mushroom-hill-intro" -> make(id, "Take the long path", p, games,
                    "Your route, Knuckles. A few extra minutes won't hurt the show.",
                    "I ordered a procession, not a nature walk. Though the view is tolerable.",
                    "That offbeat creak's great. Can we leave it in the opening?",
                    "Silver Sonic detects a soft bridge creak and taps a quiet reply against its own case.",
                    "This bridge carries the weight. The short path doesn't. I've checked both.",
                    "Mecha Sonic waits at the bridge until the lighter cases have crossed, then follows the marked boards.",
                    "Egg Robo rotates the directional arrow toward the longer, sturdier path without covering the trail sign.",
                    say(SONIC, "I can get the cases over that shortcut."),
                    say(KNUCKLES, "You can. The rest of the crowd can't. Take the bridge with me."),
                    say(SONIC, "Fair point. You lead."),
                    act(null, "The cases cross one by one. A springy board adds an offbeat creak to Tails' humming."),
                    say(ROBOTNIK, "I will not add a mushroom to the percussion roster."),
                    say(TAILS, "Good. It's a bridge. It already has a job."));
            case "s3k-flying-battery-intro" -> make(id, "Keep the band together", p, games,
                    "I'll change the entrance after your signal. The wind can have the first surprise.",
                    "My bass figure can cover that pause. A small concession to the atmosphere.",
                    "Wait for my hand before the turn. I want to try the soft entrance this time.",
                    "Silver Sonic widens its stance and turns the music stand into the wind before the count begins.",
                    "The rails are sound. I'll keep an eye on the loose cases, too.",
                    "Mecha Sonic catches the skidding monitor with its foot and slides it back into the semicircle.",
                    "Egg Robo clips a loose page to its hat, then remembers to put the hat on the music stand.",
                    act(null, "A gust rolls through the flying stage. Sonic starts an entrance and stops at Tails' raised hand."),
                    say(TAILS, "Let the gust pass. Then the softer chord will carry."),
                    say(SONIC, "Got it. Your call."),
                    say(ROBOTNIK, "I can hold the bass underneath. We need not leave the audience with wind alone."),
                    act(null, "The gust fades. The chord lands cleanly, with Robotnik's low note still beneath it."),
                    say(TAILS, "That's the version. Keep that one."));
            case "s3k-sandopolis-intro" -> make(id, "A courteous opening", p, games,
                    "Soft opening, then their answer. Nobody needs to shout to be part of this.",
                    "A quiet audience is not necessarily unimpressed. I am choosing to believe that.",
                    "The little rattle makes a nice answer. I'll leave a space for it.",
                    "Silver Sonic lowers its foot before it clanks, then marks the count with a silent hand.",
                    "These halls answer slowly. Wait for them. I've heard them before.",
                    "Mecha Sonic rests its heavy case on a folded cloth and tests the floor with one careful tap.",
                    "Egg Robo pulls the fanfare banner halfway down and raises a smaller WELCOME sign.",
                    say(KNUCKLES, "Start quietly. These halls have had a long sleep."),
                    say(ROBOTNIK, "My entrance normally includes a fanfare."),
                    say(SONIC, "Try the little melody first. You can still surprise them later."),
                    act(null, "Robotnik plays softly. A distant rattle answers; he waits and plays the phrase again."),
                    say(TAILS, "They're answering you."),
                    say(ROBOTNIK, "Naturally. Though I appreciate their unusual restraint with the applause."));
            case "s3k-lava-reef-intro" -> make(id, "What the cable actually does", p, games,
                    "Take the command line out. Keep the music. The people upstairs still deserve their show.",
                    "That amplifier had an exquisite response curve. You might have admired it before dismantling it.",
                    "I'll route the sound through the spare cabinet. Knuckles, show me where the load comes in.",
                    "Silver Sonic compares the SECURITY label with the amplifier terminals, then lays the false label aside.",
                    "You can have this back after the show. Somewhere that isn't my island.",
                    "Mecha Sonic holds the disconnected Emerald lead above the floor while Tails secures the sound circuit.",
                    "Egg Robo removes the SECURITY label and places it beside the exposed command diagram.",
                    act(null, "Knuckles follows a concealed cable to an Emerald amplifier. Its diagram sends command pulses with the final broadcast."),
                    say(KNUCKLES, "This isn't the perimeter alarm. You've run it toward the Master Emerald."),
                    say(ROBOTNIK, "An island-wide audience is merely the rehearsal for a world-wide audience."),
                    act(null, "Knuckles removes the coupling and puts it in his own case. Tails checks the separate sound line."),
                    say(KNUCKLES, "I took your word about the alarm. My mistake. This comes with me."),
                    say(SONIC, "You take the island. We'll keep the show running on its own power."));
            case "s3k-sky-sanctuary-intro" -> make(id, "An answer, not a copy", p, games,
                    "Leave that gap. I want to hear what comes back.",
                    "My replica has learned phrasing. I will accept this compliment to my design.",
                    "The answer's different each time. We should play it like a conversation.",
                    "Silver Sonic angles its count lamp toward Mecha Sonic and waits for the raised claw.",
                    "The supports are clear. I'll stay for that quiet answer.",
                    "Mecha Sonic lowers its raised claw, plays one spare note, and waits for the live response.",
                    "Egg Robo replaces the old PERFECT REPLICA placard with a plain performer name card.",
                    act(MECHA_SONIC, "Mecha Sonic repeats Sonic's flourish exactly, then pauses with one claw still raised."),
                    say(SONIC, "You don't have to match mine. What would you put in that gap?"),
                    act(MECHA_SONIC, "A slow, spare phrase answers. The metal hand settles gently over the strings."),
                    say(TAILS, "That leaves a lovely space for the rest of us."),
                    say(ROBOTNIK, "A tasteful variation. My construction is plainly more sophisticated than its critics admit."),
                    say(KNUCKLES, "Then let it finish. I want to hear the answer."));
            case "s3k-death-egg-intro" -> make(id, "Two leads, two decisions", p, games,
                    "We stay because the audience is waiting. The command socket can wait forever.",
                    "I could conduct a world. For tonight I shall settle for a very demanding band.",
                    "Sound's ready. Knuckles has the command coupling; nobody can quietly plug it back in.",
                    "Silver Sonic compares both sockets, then puts a clear green signal above SOUND only.",
                    "I've checked the actual circuit. The broadcast carries music. Nothing else.",
                    "Mecha Sonic lifts the empty command rack out of the performance space.",
                    "Egg Robo caps the command socket, checks the cap, then turns its page to the opening bar.",
                    act(EGG_ROBO, "Egg Robo presents two carefully coiled leads: SOUND and COMMAND. Knuckles keeps the second."),
                    say(ROBOTNIK, "You are taking a remarkably literal approach to my labels."),
                    say(KNUCKLES, "I traced the wiring. The labels are just catching up."),
                    say(TAILS, "The sound line doesn't touch the Emerald. We can broadcast the concert on ordinary power."),
                    say(SONIC, "Then let's do what people came for."),
                    say(ROBOTNIK, "They came for my finale. I intend to make that an excellent decision."));
            case "s3k-finale-intro" -> make(id, "A world can listen freely", p, games,
                    "I'll follow your big entrance. Then leave room for Mecha's answer.",
                    "The takeover is postponed. My solo is not.",
                    "We don't need the Emerald to carry this. Every part has room on the ordinary line.",
                    "Silver Sonic dims its exact-timing display and takes the opening count from the band.",
                    "The island's safe. Now I can listen instead of watching the cables.",
                    "Mecha Sonic raises a claw for its quiet answer and holds it until the preceding note has faded.",
                    "Egg Robo clears the abandoned command score away and smooths the concert's final page.",
                    say(ROBOTNIK, "You have removed the Emerald amplifier and my command channel. Still you offer me the lead?"),
                    say(SONIC, "You wrote a good finale. Play it. I'm saving a spot for the big entrance."),
                    say(KNUCKLES, "I checked the circuit. The music can go through."),
                    say(TAILS, "I'll give you the count. The quiet answer comes after the big passage."),
                    act(null, "Robotnik rests his hand on the strings and listens to the count."),
                    say(ROBOTNIK, "Then let us give them something difficult to dislike."));
            case "s3k-encore-intro" -> make(id, "One more on the island", p, games,
                    "Knuckles picks the slow opening. The rest of us can follow it home.",
                    "I am contributing the quiet melody, not an apology. Kindly hear the distinction.",
                    "The forest leaves a little echo after the chorus. Let's wait for it this time.",
                    "Silver Sonic carries the smallest speaker back toward the nesting patch, leaving a respectful gap.",
                    "Softly, at first. The nest is still behind your amplifier.",
                    "Mecha Sonic angles the smallest monitor away from the nest and toward the returning audience.",
                    "Egg Robo packs the enormous spotlight and sets a small lantern beside the first music stand.",
                    say(KNUCKLES, "One more set on the island. The small stage, by the clear path."),
                    say(SONIC, "You inviting us back?"),
                    say(KNUCKLES, "I'm asking for the slow chorus. You never did it here."),
                    say(TAILS, "We can open with it. No big cabinet this time."),
                    say(ROBOTNIK, "The small cabinet is also mine. I accept your enthusiasm for it."),
                    say(KNUCKLES, "Good. You can carry it."));
            case "s3k-outro" -> make(id, "The music gets an encore", p, games,
                    "Next visit, we'll ask where the stage belongs before we roll it in.",
                    "The ovation was for the concert. My failed takeover will not be receiving an encore.",
                    "I kept the quiet answer in the score. We don't have to fill every space.",
                    "Silver Sonic bows to the crowd, then turns and gives the same careful bow to the band.",
                    "The island's quiet again. That last chorus can stay a little longer.",
                    "Mecha Sonic plays the spare answering phrase once more, then closes its case without rushing.",
                    "Egg Robo returns the final lantern to the clear path and rolls away the empty cable reel.",
                    act(null, "The concert earns its applause. The Emerald stays home, and the command equipment leaves unplugged."),
                    say(KNUCKLES, "Good show. No takeover. That's a result I can live with."),
                    say(ROBOTNIK, "My playing was magnificent. You need not sound surprised."),
                    say(SONIC, "I'm not. That's why I wanted to hear the finale."),
                    say(TAILS, "The little answer after it was good, too."),
                    say(KNUCKLES, "Leave a space. You'll hear it from the forest."));
            default -> throw new IllegalArgumentException("Unknown scene: " + id);
        };
    }
}
