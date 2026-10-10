package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;
import static starpost.people.cast.Tunes.SAT;
import static starpost.people.cast.Tunes.SUN;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;
import starpost.scene.Sfx;

/**
 * Sonic, when Tails or Knuckles farms: he never stops, so his schedule is a lap of the valley.
 * A big brother to Tails, a needling rival to Knuckles. When Tails farms, Sonic's 2-heart event
 * is the one that brings the Chirp Translator (Tails builds it himself, at Sonic's prodding).
 */
public final class Sonic {
    private Sonic() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("sonic", "SONIC")
                .about("NEVER STOPS. NAPS ON THE MEADOW. LOVES CHILI DOGS.")
                .body("hero:sonic").home("meadow").birthday(SUMMER, 23).hero()
                .loves("chili_dog", "fire_pepper")
                .likes("loop_berry", "bluesphere_berry", "ring_radish", "palm_coconut")
                .kind(Kind.FOOD, Taste.LIKE)
                .dislikes("palm_bean", "spring_yard_hops")
                .hates("frost_ring", "snow_spud");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().at(600, "meadow").at(800, "slope", -40).at(930, "plaza").at(1100, "palms_east").at(1230, "inn_porch")
                .at(1400, "palms_far").at(1600, "gate", 40).at(1730, "plaza", -40).inside(1900, "inn").at(2130, "meadow");
        v.plan().weekdays(SAT, SUN).at(600, "meadow").farm(1000, "field", -30, 40).at(1300, "palms_far")
                .at(1600, "plaza", 20).inside(1900, "inn").at(2130, "meadow");
        v.plan().rain().inside(600, "inn");
    }

    private static void lines(VillagerDef v) {
        v.line("HEY BUDDY! A FARMER! LOOK AT YOU! PROUD OF YOU. ALSO, THERE'S DIRT ON YOUR NOSE.")
                .first().farmer("tails");
        v.line("KNUX! IN OVERALLS! OKAY, NO OVERALLS. BUT IN MY HEAD, OVERALLS. I'M NEVER LETTING THIS GO.")
                .first().farmer("knuckles");
        v.line("I DID TWELVE LAPS OF THE VALLEY BEFORE BREAKFAST. THEN I HAD BREAKFAST. THEN TWELVE MORE.");
        v.line("STANDING STILL? IT'S LIKE RUNNING, BUT YOU DON'T GO ANYWHERE. I DON'T GET IT.");
        v.line("THE LOOP BY THE TOTEMS IS THE BEST LOOP IN THE VALLEY. IT'S THE ONLY LOOP. STILL THE BEST.");
        v.line("IF ROBOTNIK SELLS YOU ANYTHING, CHECK IT FOR LASERS. TRUST ME.");
        v.line("CLEMENTINE SAYS I EAT TOO FAST. I SAY SHE COOKS TOO SLOW. WE'RE BOTH HAPPY.");
        v.line("FLOWERS EVERYWHERE. I RAN THROUGH THE MEADOW AND NOW I'M SNEEZING AT SUPERSONIC SPEED.").spring();
        v.line("SPRING'S GOOD RUNNING WEATHER. SO'S SUMMER. AND FALL. WINTER'S GOOD SLIDING WEATHER.").spring();
        v.line("HOT ENOUGH TO RUN ON THE LAKE. ALMOST. I TRIED. BARNABY FISHED ME OUT.").summer();
        v.line("SUMMER! THE SKY'S BIG, THE DAYS ARE LONG AND THE CHILI DOGS ARE OUTDOORS.").summer();
        v.line("PILES OF LEAVES ARE BASICALLY SPRINGS THAT DON'T BOUNCE. I LIKE THEM ANYWAY.").fall();
        v.line("THE VALLEY FAIR'S COMING UP. ROBOTNIK'S JUDGING. SO THE PRIZE GOES TO ROBOTNIK.").fall();
        v.line("SNOW. NOT A FAN. YOU CAN'T SEE THE RINGS AND YOUR FEET GET... SNOWY.").winter();
        v.line("ICE IS JUST THE GROUND WITHOUT FRICTION. WHICH IS GREAT. UNTIL THE STOPPING PART.").winter();
        v.line("RAIN. THE ONE THING FASTER THAN ME IS A CLOUD WITH A GRUDGE. I'M STAYING INSIDE.").rain();
        v.line("NO, I'M NOT SCARED OF WATER. I'M JUST... CAUTIOUS. AROUND WATER. ALL OF IT.").rain();
        v.line("TAILS, YOU BUILT A WHOLE FARM. I'D HAVE GOT BORED AT THE FIRST SEED. YOU'RE AMAZING, BUDDY.")
                .farmer("tails").hearts(2);
        v.line("REMEMBER WHEN YOU COULDN'T KEEP UP? NOW I CAN'T KEEP UP WITH YOUR TO-DO LIST.").farmer("tails");
        v.line("IF ANYONE GIVES YOU TROUBLE, YOU TELL ME. I'LL BE THERE BEFORE YOU FINISH THE SENTENCE.")
                .farmer("tails").hearts(4);
        v.line("YOU PUNCH THE ROCKS, YOU DIG THE HOLES... HONESTLY, KNUX, FARMING SUITS YOU.").farmer("knuckles");
        v.line("RACE YOU TO THE LOOP. NO? COME ON. YOU'RE STANDING STILL ANYWAY.").farmer("knuckles");
        v.line("I WOULDN'T SAY THIS IN FRONT OF ANYONE, BUT YOU'RE A GOOD GUY, KNUCKLES. STUBBORN. BUT GOOD.")
                .farmer("knuckles").hearts(4);
        v.line("SAVED YOU A CHILI DOG. THEN I GOT HUNGRY. SAVED YOU THE MEMORY OF A CHILI DOG.").hearts(3);
        v.line("FUNNY. I SPENT MY WHOLE LIFE RUNNING PAST PLACES LIKE THIS. TURNS OUT THEY'RE NICE.").hearts(5);
        v.line("WANNA KNOW A SECRET? THE ANIMALS SAY I SNORE. I DON'T. I WHOOSH.").flag(People.TRANSLATOR);
        v.line("TOMORROW'S THE GREAT VALLEY RACE. I'M GOING EASY ON EVERYONE. ...NAH.").on(SUMMER, 10);
        v.line("ANOTHER YEAR OLDER, ANOTHER YEAR FASTER. THANKS FOR REMEMBERING, PAL!").birthday();
        v.line("SEE? TOLD YOU YOU'D FIGURE IT OUT. YOU ALWAYS DO.").after("sonic_2t", 4);

        v.again("STILL HERE? ME TOO. WEIRD, RIGHT?");
        v.again("GOTTA GO. ...OKAY, I'LL STAY ONE MORE MINUTE.");

        v.gift(Taste.LOVE, "NOW WE'RE TALKING! YOU'RE THE BEST. DON'T TELL TAILS I SAID THAT. OR DO.");
        v.gift(Taste.LIKE, "HEY, THANKS! THIS'LL KEEP ME GOING FOR... A WHOLE LAP.");
        v.gift(Taste.NEUTRAL, "COOL. THANKS. I'LL HOLD ONTO IT. LIKE, LITERALLY. NO POCKETS.");
        v.gift(Taste.DISLIKE, "UH. SLOW FOOD. I CAN TELL. IT TASTES SLOW.");
        v.gift(Taste.HATE, "COLD AND WET. TWO OF MY LEAST FAVOURITE THINGS IN ONE. NICE TRY.");
        v.birthdayGift("A BIRTHDAY PRESENT? FOR ME? YOU SHOULDN'T HAVE. NO, SERIOUSLY. OKAY, GIMME.");
        v.thanks("YO! THANKS FOR THE PRESENT. IT WAS WAY PAST COOL. - S");
    }

    private static void events(Cast cast) {
        // When Tails farms, Sonic's prodding gets the Chirp Translator built.
        cast.event("sonic_2t", "sonic", 2).farmer("tails").onFarm().between(800, 1700).dry()
                .summary("When Tails farms: Sonic brings Buzz Bomber parts and Tails builds the Chirp Translator.")
                .enter("sonic", 1, 240, 36)
                .say("sonic", "HEY BUDDY. QUICK QUESTION. DO YOU KNOW WHAT THE ANIMALS ARE SAYING?")
                .emote("farmer", "?")
                .say("sonic", "ME NEITHER. PIP CHIRPED AT ME FOR TEN MINUTES. I NODDED. I THINK I AGREED TO SOMETHING.")
                .say("sonic", "BUT YOU? YOU COULD BUILD A THING. A TALKY THING. HERE, I GOT YOU SOME BUZZ BOMBER EARS.")
                .sfx(Sfx.GRAB)
                .narrate("TAILS TINKERS FOR AN HOUR. SONIC DOES FORTY LAPS OF THE FIELD.")
                .fadeOut().pause(20).fadeIn()
                .flag(People.TRANSLATOR).flag("moto_reprogrammed")
                .narrate("YOU BUILT THE CHIRP TRANSLATOR! AND TAUGHT MOTO TO BEEP HAPPILY WHILE YOU WERE AT IT.")
                .enter("pip", -1, 220, 56)
                .say("pip", "...THE PROMISE YOU AGREED TO WAS MY BIRTHDAY PARTY, SONIC. YOU'RE BRINGING CAKE.")
                .emote("sonic", "sweat")
                .say("sonic", "...SEE? THIS IS WHY WE NEEDED IT. NICE WORK, BUDDY.")
                .leave("pip", -260)
                .leave("sonic", 280);

        // When Knuckles farms: a race round the loop he refuses to run.
        cast.event("sonic_2", "sonic", 2).farmer("knuckles").near("palms_far", 220).between(900, 1800).dry()
                .summary("When Knuckles farms: Sonic dares him round the loop.")
                .enter("sonic", 1, 220, 40)
                .say("sonic", "KNUX! YOU'VE BEEN PLANTING SO LONG YOU'RE GROWING ROOTS. RACE ME ROUND THE LOOP.")
                .emote("farmer", "anger")
                .say("farmer", "I'M BUSY.")
                .say("sonic", "BUSY LOSING. READY? GO!")
                .leave("sonic", 400)
                .sfx(Sfx.DASH).pause(50)
                .enter("sonic", -1, 260, 40)
                .say("sonic", "...I WON. YOU DIDN'T MOVE. BUT I WON.")
                .say("sonic", "NEXT TIME, BRING YOUR GLIDING SHOES. IT WAS NO FUN WITHOUT YOU.")
                .leave("sonic", 280);

        cast.event("sonic_2s", "sonic", 2).farmer("tails").near("plaza", 200).between(1000, 1800).dry()
                .requires("sonic_2t")
                .summary("When Tails farms: Sonic shows the valley he's mapped at speed.")
                .enter("sonic", 1, 220, 40)
                .say("sonic", "BUDDY! I MAPPED THE WHOLE VALLEY. TOOK FOUR SECONDS. WANNA SEE?")
                .say("sonic", "WATERFALL, PALMS, TOWN, THE TOTEM LEDGE, THE LOOP, THE RUINS, THE LAKE. DONE. THAT'S IT.")
                .say("sonic", "SMALL, RIGHT? BUT EVERY TIME I LAP IT THERE'S SOMETHING NEW YOU PLANTED.")
                .emote("sonic", "heart")
                .leave("sonic", 280);

        cast.event("sonic_4", "sonic", 4).near("inn", 160).between(1800, 2300).dry()
                .summary("Chili dogs on the Inn's porch: Sonic admits the valley is nice to come back to.")
                .enter("sonic", -1, 220, 36)
                .say("sonic", "PSST. CLEMENTINE MADE TOO MANY CHILI DOGS. WHICH IS IMPOSSIBLE. BUT HERE WE ARE.")
                .give("chili_dog", 2)
                .say("sonic", "YOU KNOW WHAT'S WEIRD? I KEEP COMING BACK HERE. I NEVER COME BACK ANYWHERE.")
                .sayIf("tails", "sonic", "GUESS IT'S 'CAUSE YOU'RE HERE, BUDDY. DON'T MAKE IT A THING.")
                .sayIf("knuckles", "sonic", "GUESS SOMEBODY HAS TO KEEP AN EYE ON YOU. DON'T MAKE IT A THING, KNUX.")
                .emote("farmer", "heart")
                .say("sonic", "...YOU'RE MAKING IT A THING. OKAY. GOTTA GO FAST. NIGHT!")
                .leave("sonic", 300);
    }
}
