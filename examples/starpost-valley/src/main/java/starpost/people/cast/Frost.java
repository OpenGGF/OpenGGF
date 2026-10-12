package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;

/**
 * Frost, a Pecky penguin: lives in an ice hut by the waterfall's pool, the only villager who
 * loves winter, and homesick for somewhere colder. Speaks in pictures until the Chirp Translator.
 */
public final class Frost {
    private Frost() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("frost", "FROST")
                .about("PECKY. ICE HUT BY THE WATERFALL. LOVES WINTER.")
                .body("animal:pecky").home("waterfall").birthday(WINTER, 12).animal()
                .loves("snow_spud", "frost_ring", "ice_egg")
                .likes("bluesphere_berry", "loop_berry")
                .kind(Kind.FISH, Taste.LIKE)
                .dislikes("sunflower", "chili_dog")
                .hates("fire_pepper");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().inside(600, "waterfall").at(800, "waterfall", 36).at(1200, "palms_west").at(1500, "waterfall", 36)
                .inside(2000, "waterfall");
        v.plan().seasons(WINTER).inside(600, "waterfall").at(700, "plaza", 30).at(1100, "path").at(1400, "plaza", 30)
                .at(1800, "waterfall", 36).inside(2300, "waterfall");
        v.plan().seasons(SUMMER).inside(600, "waterfall").at(1800, "waterfall", 36).inside(2200, "waterfall");
        v.plan().rain().inside(600, "waterfall").at(900, "waterfall", 36).inside(1900, "waterfall");
    }

    private static void lines(VillagerDef v) {
        v.line("OH. HELLO. I'M FROST. I'D SHAKE YOUR HAND BUT MY FLIPPERS ARE COLD. I LIKE THEM COLD.")
                .first().pic("who:frost", "snow", "heart");
        v.line("THE WATERFALL'S SPRAY KEEPS MY HUT COOL. IT'S THE ONLY COOL SPOT IN GREEN HILL.")
                .pic("house", "rain", "snow", "heart");
        v.line("I CAME FROM A PLACE WITH ICE CAPS. REAL ICE. ON TOP OF EVERYTHING.").pic("snow", "house", "sad");
        v.line("I SLIDE EVERYWHERE. ON MY BELLY. IT'S FASTER THAN WALKING AND MORE DIGNIFIED.")
                .pic("arrow", "heart", "!");
        v.line("EVERYONE ELSE COMPLAINS WHEN IT GETS COLD. I COMPLAIN WHEN IT DOESN'T.").pic("snow", "sun", "anger");
        v.line("SPRING. EVERYTHING MELTS. INCLUDING ME. EMOTIONALLY.").spring().pic("sun", "snow", "sad");
        v.line("FLOWERS ARE JUST SNOWFLAKES THAT GOT CONFUSED ABOUT THEIR COLOURS.").spring()
                .pic("item:hill_daffodil", "snow", "?");
        v.line("SUMMER. I AM STAYING INSIDE WITH MY FEET IN A BUCKET OF WATERFALL.").summer()
                .pic("sun", "house", "rain", "sad");
        v.line("IT'S HOT. IT'S SO HOT. WHY IS IT SO HOT. WHO DID THIS.").summer().pic("sun", "sun", "anger", "?");
        v.line("THE AIR'S GETTING CRISP. I CAN FEEL IT IN MY BEAK. WINTER'S COMING. YES.").fall()
                .pic("snow", "clock", "heart", "!");
        v.line("WINTER! FINALLY! LOOK AT IT! LOOK AT ALL OF IT! IT'S EVERYWHERE!").winter()
                .pic("snow", "snow", "heart", "!");
        v.line("SNOW SPUDS GROW UNDER THE SNOW. THEY'RE THE BEST FOOD. COLD AND CRUNCHY.").winter()
                .pic("item:snow_spud", "heart");
        v.line("THE ICE CAP FESTIVAL'S MY FAVOURITE DAY OF THE YEAR. I'M HOSTING. IT'S A LOT OF RESPONSIBILITY.")
                .winter().pic("snow", "note", "heart", "!");
        v.line("RAIN. IT'S JUST SNOW THAT DIDN'T TRY HARD ENOUGH.").rain().pic("rain", "snow", "...");
        v.line("YOU CAME BY AGAIN. MOST PEOPLE AVOID MY HUT. IT'S CHILLY. YOU DON'T SEEM TO MIND.").hearts(2)
                .pic("farmer", "house", "snow", "heart");
        v.line("I KEEP A PIECE OF ICE CAP ICE IN A BOX. IT NEVER MELTS. I DON'T KNOW WHY. I DON'T ASK.")
                .hearts(3).pic("snow", "sparkle", "house", "?");
        v.line("I'M STILL HOMESICK. BUT HOMESICK IS ALLOWED. YOU CAN MISS ONE PLACE AND LOVE ANOTHER.")
                .after("frost_4", 7).pic("sad", "house", "heart");
        v.line("NOW YOU CAN UNDERSTAND ME! I'VE BEEN SAYING 'TOO HOT' EVERY DAY SINCE SPRING.")
                .flag(People.TRANSLATOR).after("tails_2", 6);
        v.line("TOMORROW'S THE ICE CAP FESTIVAL! I'VE POLISHED EVERY SNOWFLAKE. PERSONALLY.").on(WINTER, 7)
                .pic("snow", "sparkle", "!");
        v.line("A WINTER BIRTHDAY. THE BEST KIND. EVERYTHING'S COLD AND PERFECT.").birthday()
                .pic("gift", "snow", "heart");

        v.line("I GAVE RUSTY A SNOWBALL I SAVED FROM LAST WINTER. HE SAID IT WAS THE COLDEST GIFT EVER. I KNOW.")
                .pic("who:rusty", "snow", "gift", "heart");
        v.line("YOU RUN SO FAST YOU MAKE WIND. COULD YOU RUN PAST MY HUT? IN SUMMER? A LOT?").farmer("sonic")
                .pic("farmer", "arrow", "house", "?");
        v.line("TAILS. YOUR TAILS ARE LIKE FANS. YOU COULD COOL THE WHOLE VALLEY. YOU WON'T. BUT YOU COULD.")
                .farmer("tails").pic("farmer", "snow", "?");
        v.line("KNUCKLES. YOUR ISLAND IS IN THE SKY. IS IT COLD UP THERE? PLEASE SAY IT'S COLD UP THERE.")
                .farmer("knuckles").pic("farmer", "house", "snow", "?");
        v.line("THE ICE IN MY BOX STILL HASN'T MELTED. I THINK IT'S BECAUSE I'M HAPPY. ICE KNOWS.").hearts(5)
                .pic("snow", "sparkle", "heart");
        v.line("I'VE STARTED PACKING SNOW FOR THE FESTIVAL. THERE'S NO SNOW YET. I'M PACKING THE IDEA OF SNOW.")
                .fall().pic("snow", "clock", "?");

        v.again("STILL HERE. STILL COLD. GOOD.").pic("snow", "heart");
        v.again("YOU'RE LETTING THE WARM IN.").pic("sun", "no");

        v.gift(Taste.LOVE, "IT'S SO COLD! IT'S PERFECT! YOU UNDERSTAND ME. NOBODY UNDERSTANDS ME.")
                .pic("snow", "heart", "heart", "!");
        v.gift(Taste.LIKE, "OH, NICE. THANK YOU. I'LL KEEP IT IN THE COLD BOX.").pic("house", "snow", "heart");
        v.gift(Taste.NEUTRAL, "THANKS. IT'S A BIT WARM. I'LL LET IT COOL DOWN.").pic("sun", "...");
        v.gift(Taste.DISLIKE, "IT'S VERY... SUNNY. THANK YOU. I'LL PUT IT IN THE SHADE.").pic("sun", "no");
        v.gift(Taste.HATE, "A FIRE SHIELD PEPPER?! ARE YOU TRYING TO MELT ME?").pic("item:fire_pepper", "anger", "!");
        v.birthdayGift("FOR MY BIRTHDAY? ON A COLD DAY? THIS IS THE BEST DAY OF THE YEAR TWICE.")
                .pic("gift", "snow", "heart", "!");
        v.thanks("THANKS FOR THE GIFT. I KEPT IT IN THE COLD BOX NEXT TO MY ICE. HIGHEST HONOUR. - FROST")
                .pic("gift", "house", "snow", "heart");
    }

    private static void events(Cast cast) {
        cast.event("frost_2", "frost", 2).near("waterfall", 200).between(800, 1800).dry()
                .summary("Frost shows you the coldest spot in the valley: behind the waterfall.")
                .place("frost", 40).face("frost", 0)
                .say("frost", "PSST. WANT TO SEE THE COLDEST SPOT IN THE VALLEY? IT'S A SECRET. FOLLOW ME. CAREFULLY.",
                        "snow", "house", "?")
                .walk("frost", -30, 6)
                .emote("frost", "heart")
                .say("frost", "BEHIND THE WATERFALL. FEEL THAT? FOUR DEGREES COLDER. I MEASURED. WITH MY BEAK.",
                        "rain", "snow", "heart", "!")
                .emote("farmer", "sweat")
                .say("frost", "YOU'RE SHIVERING. THAT MEANS IT'S WORKING. COME BACK IN SUMMER. YOU'LL THANK ME.",
                        "farmer", "snow", "sun", "heart")
                .flag("frost_cold_spot");

        cast.event("frost_4", "frost", 4).near("waterfall", 200).between(1800, 2300).dry().requires("frost_2")
                .summary("Frost admits he is homesick for Ice Cap, and shows the ice that never melts.")
                .music(Tunes.S1, Tunes.SLZ)
                .place("frost", 40).face("frost", -1)
                .pause(30)
                .say("frost", "I DIDN'T CHOOSE GREEN HILL. THE CAPSULE OPENED HERE. SO HERE IS WHERE I AM.",
                        "badnik", "house", "...")
                .say("frost", "THIS IS ICE FROM ICE CAP. IT NEVER MELTS. I THINK IT'S WAITING FOR ME TO GO BACK.",
                        "snow", "sparkle", "clock", "sad")
                .face("frost", 0)
                .say("frost", "BUT THEN SOMEONE COMES BY MY HUT. EVERY DAY. EVEN IN SUMMER.", "farmer", "house", "heart")
                .say("frost", "MAYBE THE ICE CAN WAIT A BIT LONGER.", "snow", "clock", "heart")
                .emote("farmer", "heart")
                .flag("frost_homesick");
    }
}
