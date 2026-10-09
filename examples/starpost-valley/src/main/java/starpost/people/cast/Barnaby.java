package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;
import static starpost.people.cast.Tunes.FRI;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;
import starpost.scene.Sfx;

/**
 * Barnaby, a Rocky seal: the old fisherman on the lake jetty with exactly one story (the giant
 * Chopper that ate his hat). Speaks in pictures until the Chirp Translator.
 */
public final class Barnaby {
    private Barnaby() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("barnaby", "BARNABY")
                .about("ROCKY. OLD FISHERMAN ON THE JETTY. ONE STORY.")
                .body("animal:rocky").home("jetty").birthday(FALL, 3).animal()
                .loves("ice_egg", "frost_ring", "palm_coconut")
                .likes("snow_spud", "loop_berry")
                .kind(Kind.FISH, Taste.LOVE).kind(Kind.FORAGE, Taste.LIKE)
                .dislikes("fire_pepper", "spring_yard_hops")
                .hates("eggman_pumpkin");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().inside(600, "lake").at(630, "jetty", 6).at(1200, "lake", -50).at(1300, "jetty", 6)
                .inside(2100, "lake");
        v.plan().weekdays(FRI).inside(600, "lake").at(630, "jetty", 6).at(1600, "inn_porch", 30)
                .inside(1800, "inn").inside(2300, "lake");
        v.plan().rain().inside(600, "lake").at(800, "jetty", 6).inside(1800, "lake");
    }

    private static void lines(VillagerDef v) {
        v.line("AHOY. NAME'S BARNABY. SIT DOWN. HAVE I TOLD YOU ABOUT MY HAT? NO? SIT DOWN.").first()
                .pic("who:barnaby", "fish", "!");
        v.line("THE FISH IN THIS LAKE ARE CLEVER. THE CHOPPERS ARE CLEVERER. THE BIG ONE IS THE CLEVEREST.")
                .pic("fish", "badnik", "!");
        v.line("I'VE BEEN FISHING THIS JETTY SINCE BEFORE THE BADNIKS. THEN DURING THE BADNIKS. NOW AFTER.")
                .pic("fish", "clock", "badnik", "heart");
        v.line("THE TRICK TO FISHING IS PATIENCE. THE OTHER TRICK IS A BIGGER ROD. I PREFER THE OTHER TRICK.")
                .pic("fish", "clock", "!");
        v.line("A SEAL ON A JETTY IS LIKE A SPRING ON A HILL. IT'S WHERE IT'S MEANT TO BE.").pic("house", "heart");
        v.line("I DON'T SWIM ANY MORE. I FLOAT. WITH DIGNITY.").pic("fish", "zzz");
        v.line("SPRING. THE FISH ARE WAKING UP. SO AM I. SLOWLY. BOTH OF US.").spring().pic("sun", "fish", "zzz");
        v.line("HOT ONE. THE LAKE'S LIKE BATHWATER. I'M IN IT. I'M NOT GETTING OUT.").summer()
                .pic("sun", "fish", "heart");
        v.line("THE CHOPPERS ARE BITING. NOT THE FISH. THE CHOPPERS. KEEP YOUR FEET UP.").summer()
                .pic("badnik", "fish", "sweat", "!");
        v.line("THE LEAVES ON THE WATER LOOK LIKE LITTLE BOATS. I NAME THEM. THAT ONE'S DORIS.").fall()
                .pic("fish", "heart", "...");
        v.line("WINTER! NOW THIS IS FISHING WEATHER. COLD FINGERS, WARM HEART, SOMETHING ON THE LINE.").winter()
                .pic("snow", "fish", "heart", "!");
        v.line("RAIN ON THE LAKE. THE FISH CAN'T TELL. THEY'RE ALREADY WET.").rain().pic("rain", "fish", "...");
        v.line("THE HAT. LET ME TELL YOU ABOUT THE HAT. IT WAS A GOOD HAT. RED. FEATHER IN IT. THEN...")
                .hearts(1).pic("sad", "badnik", "...");
        v.line("YOU'VE GOT THE PATIENCE OF A HERON, YOU. COME BY ANY TIME. BRING BAIT.").hearts(3)
                .pic("farmer", "fish", "heart");
        v.line("THE RED CHOPPER'S STILL DOWN THERE. I SEE HIS SHADOW SOME MORNINGS. HE'S WEARING MY HAT.")
                .hearts(4).flag("red_chopper_story").pic("badnik", "sad", "anger");
        v.line("NOW YOU CAN UNDERSTAND ME, I'LL TELL YOU THE HAT STORY PROPERLY. FROM THE BEGINNING. SIT.")
                .flag(People.TRANSLATOR).after("tails_2", 6);
        v.line("FRIDAY. I TELL MY STORY AT THE INN. SAME STORY. THEY STILL CLAP. GOOD CROWD.").weekdays(FRI)
                .pic("house", "note", "heart");
        v.line("MY BIRTHDAY. AT MY AGE YOU STOP COUNTING. YOU START FISHING.").birthday().pic("gift", "fish", "heart");

        v.line("THE TRICK WITH CHOPPERS IS THEY JUMP. THE TRICK WITH ME IS I DON'T. WE HAVE AN UNDERSTANDING.")
                .pic("badnik", "fish", "...");
        v.line("PIP TRIED TO DELIVER A LETTER TO A FISH. IT WAS ADDRESSED TO ME. CLOSE ENOUGH.")
                .pic("flicky", "fish", "?");
        v.line("BEFORE THE BADNIKS THIS LAKE HAD A FERRY. I WAS THE FERRY. I SWAM PEOPLE ACROSS. FOR A FISH EACH.")
                .hearts(3).pic("house", "fish", "heart");
        v.line("THE ICE IS OFF THE LAKE. THE FISH ARE HUNGRY. SO AM I. WE'LL SEE WHO WINS.").spring()
                .pic("sun", "fish", "!");
        v.line("THE FISH GET FAT IN FALL. SO DO I. IT'S ONLY FAIR.").fall().pic("fish", "food", "heart");
        v.line("ICE EGGS. PECKY EGGS, LAID IN WINTER. I'D TRADE MY JETTY FOR A BASKET. ALMOST.").winter()
                .pic("snow", "item:ice_egg", "heart");
        v.line("THE BLUE ONE. YOU RAN OVER MY LAKE ONCE. DIDN'T EVEN GET WET. SHOWING OFF.").farmer("sonic")
                .pic("farmer", "fish", "anger");
        v.line("THE FOX WHO FLIES. YOU COULD SPOT FISH FROM UP THERE. HAVE YOU THOUGHT ABOUT THAT? THINK ABOUT THAT.")
                .farmer("tails").pic("farmer", "fish", "?");
        v.line("AN ECHIDNA. YOUR LOT CAN'T SWIM EITHER, CAN YOU? WE SHOULD START A CLUB. ON THE JETTY.")
                .farmer("knuckles").pic("farmer", "house", "heart");
        v.line("I'VE NEVER TOLD ANYONE THIS. I DON'T NEED THE HAT BACK. I WANT TO SEE THAT CHOPPER CAUGHT. ONCE.")
                .hearts(6).pic("badnik", "fish", "heart");
        v.line("TOMORROW THE FLICKIES FLY OVER MY LAKE. BEST NIGHT OF THE YEAR TO BE A SEAL ON A JETTY.")
                .on(SUMMER, 27).pic("flicky", "flicky", "moon", "heart");
        v.line("ICE CAP FESTIVAL TOMORROW. FISHING CONTEST. I WIN EVERY YEAR. NOBODY ELSE ENTERS.").on(WINTER, 7)
                .pic("snow", "fish", "!");

        v.again("STILL HERE. STILL FISHING. STILL NO HAT.").pic("fish", "sad");
        v.again("SHH. SOMETHING'S NIBBLING.").pic("fish", "!");

        v.gift(Taste.LOVE, "NOW THAT'S A GIFT. THAT'S A PROPER GIFT. YOU'VE A GOOD HEART, YOU.")
                .pic("heart", "heart", "!");
        v.gift(Taste.LIKE, "MUCH OBLIGED. I'LL ENJOY THAT ON THE JETTY.").pic("house", "heart");
        v.gift(Taste.NEUTRAL, "WELL, THAT'S KIND. CAN'T EAT IT. CAN'T FISH WITH IT. STILL KIND.").pic("?", "heart");
        v.gift(Taste.DISLIKE, "TOO HOT FOR AN OLD SEAL. MY WHISKERS ARE CURLING.").pic("sun", "no");
        v.gift(Taste.HATE, "HIS FACE. ON A PUMPKIN. YOU'RE GIVING ME HIS FACE. THE MAN WHO STOLE MY LAKE.")
                .pic("robotnik", "anger", "no");
        v.birthdayGift("FOR MY BIRTHDAY? WELL I NEVER. I'LL TELL THIS STORY INSTEAD OF THE HAT ONE. FOR A WEEK.")
                .pic("gift", "heart", "!");
        v.thanks("THANKS FOR THE GIFT. CAUGHT A BIG ONE THE NEXT DAY. YOU'RE LUCKY, YOU ARE. - BARNABY")
                .pic("gift", "fish", "heart");
    }

    private static void events(Cast cast) {
        cast.event("barnaby_2", "barnaby", 2).near("lake", 200).between(630, 1300).dry()
                .summary("A fishing lesson on the jetty: the Bubble Bar.")
                .place("barnaby", 30).face("barnaby", 0)
                .say("barnaby", "YOU. COME HERE. YOU'VE BEEN LOOKING AT MY LAKE LIKE YOU WANT TO FISH IT.",
                        "farmer", "fish", "?")
                .say("barnaby", "WHEN A FISH BITES, IT FIGHTS. YOU KEEP IT IN THE BUBBLE. HOLD TO RISE. LET GO TO SINK.",
                        "fish", "sparkle", "arrow")
                .say("barnaby", "TOO MUCH PULL AND THE BUBBLE SHRINKS. LOSE THE BUBBLE, LOSE THE FISH. SIMPLE.",
                        "sparkle", "sad", "fish", "no")
                .emote("farmer", "?")
                .say("barnaby", "...YOU'LL GET IT. EVERYONE GETS IT. I GOT IT IN FORTY YEARS. YOU'RE FASTER THAN ME.",
                        "clock", "farmer", "heart")
                .flag("barnaby_fishing_lesson");

        cast.event("barnaby_4", "barnaby", 4).near("lake", 200).between(1600, 2100).dry().requires("barnaby_2")
                .summary("The hat story, properly: the Red Chopper under the jetty.")
                .music(Tunes.S1, Tunes.SLZ)
                .place("barnaby", 30).face("barnaby", 1)
                .say("barnaby", "SIT. IT'S TIME YOU HEARD IT PROPERLY. THE HAT.", "sad", "...")
                .say("barnaby", "RED HAT. FEATHER IN IT. MY FATHER'S. I WAS ON THIS JETTY THE DAY THE BADNIKS CAME.",
                        "house", "heart", "badnik", "!")
                .say("barnaby", "AND UP OUT OF THE WATER CAME A CHOPPER. BIGGEST I EVER SAW. RED AS A LOBSTER.",
                        "fish", "badnik", "!", "!")
                .say("barnaby", "IT TOOK MY HAT. JUST MY HAT. CLEAN OFF MY HEAD. AND IT WENT BACK DOWN. IT'S STILL DOWN THERE.",
                        "badnik", "sad", "arrow")
                .face("barnaby", 0)
                .say("barnaby", "THE RED CHOPPER. NOBODY'S EVER CAUGHT HIM. MAYBE YOU WILL. I'D LIKE THAT HAT BACK.",
                        "farmer", "fish", "badnik", "heart", "?")
                .emote("farmer", "!")
                .flag("red_chopper_story");
    }
}
