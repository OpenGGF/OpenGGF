package starpost.people.cast;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;
import static starpost.people.cast.Tunes.FRI;
import static starpost.people.cast.Tunes.SAT;
import static starpost.people.cast.Tunes.TUE;

import starpost.core.Kind;
import starpost.people.Cast;
import starpost.people.People;
import starpost.people.Taste;
import starpost.people.VillagerDef;
import starpost.scene.Sfx;

/**
 * Tails: the earnest inventor in the workshop under the wrecked Tornado. Hero worship when
 * Sonic farms, wary teamwork when Knuckles does (when Tails farms, he is not a villager and
 * Sonic's 2-heart event brings the Chirp Translator instead). Partner at 10 hearts.
 */
public final class Tails {
    private Tails() {
    }

    public static void define(Cast cast) {
        VillagerDef v = cast.villager("tails", "TAILS")
                .about("INVENTOR. LIVES UNDER THE WRECKED TORNADO.")
                .body("hero:tails").home("workshop").birthday(FALL, 6).hero()
                .partner("TUNES YOUR WATER SHIELD EVERY MORNING")
                .loves("scrap", "chili_dog", "oil_can")
                .likes("sunflower", "loop_berry", "palm_coconut", "bluesphere_berry")
                .kind(Kind.MATERIAL, Taste.LIKE).kind(Kind.MINERAL, Taste.LIKE)
                .dislikes("hill_daffodil", "spring_tulip")
                .hates("egg_plant");
        schedule(v);
        lines(v);
        events(cast);
    }

    private static void schedule(VillagerDef v) {
        v.plan().inside(600, "workshop").at(830, "workshop_yard").inside(1200, "inn").at(1300, "workshop_yard")
                .at(1600, "plaza", 30).inside(1730, "workshop");
        v.plan().weekdays(TUE, SAT).inside(600, "workshop").farm(930, "field", 60, 26).at(1300, "workshop_yard")
                .inside(1730, "workshop");
        v.plan().weekdays(FRI).inside(600, "workshop").at(830, "workshop_yard").inside(1200, "inn")
                .at(1300, "workshop_yard").inside(1830, "inn").inside(2300, "workshop");
        v.plan().rain().inside(600, "workshop").inside(1200, "inn").inside(1300, "workshop");
    }

    private static void lines(VillagerDef v) {
        v.line("SONIC! YOU'RE STAYING? IN ONE PLACE? FOR A WHOLE SEASON? THIS IS THE BEST DAY EVER.")
                .first().farmer("sonic");
        v.line("KNUCKLES? FARMING? I MEAN... SURE! GROUND IS GROUND. JUST DON'T PUNCH THE SEEDS.")
                .first().farmer("knuckles");
        v.line("I TAKE BADNIKS APART TO SEE HOW THEY WORK. MOSTLY THEY WORK BADLY.");
        v.line("EVERY BADNIK HAD AN ANIMAL INSIDE FOR POWER. THAT'S WHY I LIKE TAKING THEM APART.");
        v.line("PIP DELIVERED MY PARTS ORDER TO THE INN AGAIN. CLEMENTINE MADE SOUP WITH THE BOLTS.");
        v.line("DID YOU KNOW A RING WEIGHS EXACTLY... HM. I'VE NEVER CAUGHT ONE STANDING STILL.").below(3);
        v.line("I CAN'T UNDERSTAND THE ANIMALS EITHER. YET. GIVE ME A FEW DAYS AND A LOT OF WIRE.")
                .without(People.TRANSLATOR).hearts(1);
        v.line("THE SUNFLOWERS TURN TO FACE THE SUN. I BUILT ONE THAT DOES THAT. IT FACES ROBOTNIK'S CARAVAN.")
                .spring();
        v.line("SPRING MEANS POLLEN. POLLEN MEANS SNEEZING. I SPIN WHEN I SNEEZE. IT'S A WHOLE THING.").spring();
        v.line("TOO HOT TO WELD. I'M DOING MATHS INSTEAD. MATHS DOESN'T GET HOT.").summer();
        v.line("THE TORNADO'S WINGS ARE FINE. IT'S THE ENGINE, THE PROPELLER AND THE FLYING PART THAT NEED WORK.")
                .summer();
        v.line("LEAVES KEEP BLOWING INTO THE WORKSHOP. I BUILT A LEAF BLOWER. NOW THE LEAVES ARE IN THE INN.").fall();
        v.line("ROBOTNIK ASKED TO BORROW A SPANNER. I SAID NO. NOW I'M MISSING A DIFFERENT SPANNER.").fall();
        v.line("MY TAILS MAKE GREAT EARMUFFS. I JUST CAN'T WEAR THEM AND FLY AT THE SAME TIME.").winter();
        v.line("SNOW IS JUST WATER THAT'S WAITING. I READ THAT SOMEWHERE. I THINK I WROTE IT.").winter();
        v.line("RAIN! GOOD. I CAN TEST WHICH PARTS OF THE ROOF LEAK. ALL OF THEM. THAT TEST IS DONE.").rain();
        v.line("THE WORKSHOP SMELLS LIKE WET OIL. DON'T TELL CLEMENTINE I SAID IT SMELLS NICE.").rain();
        v.line("SONIC, IF YOU NEED A HAND ON THE FARM, I'VE GOT TWO. PLUS TWO TAILS. THAT'S FOUR THINGS.")
                .farmer("sonic");
        v.line("WHEN YOU SPIN DASH DOWN A ROW THE SOIL FLIES UP LIKE A ROOSTER TAIL. I TIMED IT. 0.8 SECONDS!")
                .farmer("sonic").hearts(2);
        v.line("I USED TO FOLLOW YOU EVERYWHERE. NOW YOU STAY PUT AND I'M THE ONE WHO KEEPS VISITING. FUNNY.")
                .farmer("sonic").hearts(4);
        v.line("KNUCKLES, YOU DIG FASTER THAN MY DRILL. I'M NOT JEALOUS. I'M TAKING NOTES.").farmer("knuckles");
        v.line("SONIC SAYS HI. ACTUALLY HE SAID YOUR TOMATOES ARE LUMPY. I'M PASSING IT ON NICER.")
                .farmer("knuckles").hearts(3);
        v.line("YOU KNOW YOU'RE ALLOWED TO LEAVE THE EMERALD SOMETIMES? A FARM IS A GOOD EXCUSE.")
                .farmer("knuckles").hearts(5);
        v.line("I FOUND A SCHEMATIC IN A BUZZ BOMBER'S HEAD. IT'S A SPRINKLER IF YOU READ IT UPSIDE DOWN.")
                .hearts(3);
        v.line("SOMETIMES I THINK ABOUT WHAT ROBOTNIK COULD BUILD IF HE WASN'T ROBOTNIK. THEN I BUILD A TOASTER.")
                .hearts(5);
        v.line("THANKS FOR VISITING SO MUCH. THE WORKSHOP GETS QUIET WHEN THE ENGINE ISN'T RUNNING.").hearts(6);
        v.line("WHEN THE TORNADO FLIES AGAIN, YOU GET THE FRONT SEAT. THAT'S NOT A QUESTION. IT'S ENGINEERING.")
                .hearts(8);
        v.line("THE TRANSLATOR'S HOLDING UP! DANDEL SAYS IT MAKES HER SOUND TOO ANXIOUS. IT'S VERY ACCURATE.")
                .flag(People.TRANSLATOR);
        v.line("FUN FACT: MOTO HAS BEEN SAYING GOOD MORNING THIS WHOLE TIME. IN BINARY. VERY LOUDLY.")
                .flag(People.TRANSLATOR);
        v.line("IT'S JUKEBOX NIGHT AT THE INN! I REWIRED THE JUKEBOX. NOW IT ONLY PLAYS ONE SONG. A GOOD ONE.")
                .weekdays(FRI).between(1500, 2600);
        v.line("MORNING, PARTNER! I TUNED THE WATER SHIELD. TEN MORE SPLASHES IN IT TODAY.").flag("partner_tails");
        v.line("THE RING HUNT IS TOMORROW! I'VE GOT A RING DETECTOR. IT'S MOSTLY A METAL DETECTOR. IT FOUND A SPOON.")
                .on(SPRING, 12);
        v.line("TOMORROW'S THE GREAT VALLEY RACE. I'M NOT GOING TO WIN. I'M GOING TO FLY. THAT'S DIFFERENT.")
                .on(SUMMER, 10);
        v.line("IS THE TRANSLATOR WORKING? TELL ME EVERYTHING THE ANIMALS SAY. ESPECIALLY ABOUT ME.")
                .after("tails_2", 3);
        v.line("I'VE STARTED ON THE SPRINKLER. SO FAR IT ONLY SPRINKLES ME. PROGRESS!").after("tails_4", 4);
        v.line("I DREAMT ABOUT THE ENGINE AGAIN. IT WAS HUMMING. I THINK IT MISSES US.").after("tails_6", 5);
        v.line("YOU REMEMBERED! ONE YEAR OLDER AND ONE PROPELLER CLOSER TO FLYING!").birthday();

        v.again("I'M STILL HERE! THE TORNADO DOESN'T FIX ITSELF.");
        v.again("BACK ALREADY? CAN YOU HOLD THIS? ...THANKS. YOU CAN PUT IT DOWN NOW.");
        v.again("IF YOU SEE A SPANNER WITH A BITE MARK IN IT, IT'S MINE.");

        v.gift(Taste.LOVE, "WOW! THIS IS EXACTLY WHAT I WANTED. HOW DID YOU KNOW? WAS IT THE DIAGRAMS?");
        v.gift(Taste.LOVE, "OH WOW. I'M GOING TO BUILD SOMETHING AMAZING WITH THIS. OR EAT IT. DEPENDS WHAT IT IS.");
        v.gift(Taste.LIKE, "NEAT! THANKS, {FARMER}! I'LL FIND A USE FOR IT. OR TAKE IT APART.");
        v.gift(Taste.NEUTRAL, "THANKS! I'LL PUT IT ON THE SHELF WITH THE OTHER... THINGS.");
        v.gift(Taste.DISLIKE, "ACHOO! SORRY. I THINK I'M ALLERGIC. TO THIS. SPECIFICALLY.");
        v.gift(Taste.HATE, "IT'S... IT'S GOT A MOUSTACHE, {FARMER}. WHY DOES IT HAVE A MOUSTACHE?");
        v.birthdayGift("A BIRTHDAY PRESENT? I'M KEEPING IT IN THE TORNADO'S GLOVEBOX. WHEN IT HAS A GLOVEBOX.");
        v.thanks("DEAR {FARMER}, THANK YOU FOR THE PRESENT! I MEASURED IT. IT'S PERFECT. - TAILS");
        v.thanks("{FARMER}! I USED YOUR GIFT ALREADY. DON'T ASK HOW. IT WAS VERY SCIENTIFIC. - TAILS");
    }

    private static void events(Cast cast) {
        cast.event("tails_2", "tails", 2).near("workshop", 140).between(830, 1800).dry()
                .summary("The Chirp Translator: animals speak in words; Moto is reprogrammed.")
                .enter("tails", 1, 230, 40)
                .say("tails", "{FARMER}! THERE YOU ARE! I'VE BEEN WAITING ALL MORNING. OKAY, TEN MINUTES.")
                .say("tails", "YOU KNOW HOW THE ANIMALS ONLY CHIRP? AND WE NOD AND HOPE IT WASN'T IMPORTANT?")
                .emote("farmer", "?")
                .say("tails", "EVERY BADNIK HAD AN ANIMAL INSIDE IT. ROBOTNIK MUST HAVE TALKED TO THEM SOMEHOW.")
                .say("tails", "SO I TOOK APART A BUZZ BOMBER'S EARS. AND BUILT... THIS!")
                .pose("tails", "happy").sfx(Sfx.REGISTER).pause(30)
                .say("tails", "THE CHIRP TRANSLATOR! IT CLIPS ON YOUR EAR. OR YOUR QUILLS. TRY IT!")
                .flag(People.TRANSLATOR).pose("tails", "idle")
                .enter("pip", -1, 220, 60)
                .say("pip", "...HELLO? CAN YOU HEAR ME? OH MY FEATHERS. {FARMER} CAN HEAR US! EVERYBODY!")
                .emote("tails", "heart")
                .say("tails", "IT WORKS! IT WORKS! OKAY. NOBODY TELL ME WHAT YOU'VE BEEN SAYING ABOUT ME.")
                .say("pip", "HE TALKS IN HIS SLEEP. ABOUT PROPELLERS.")
                .emote("tails", "sweat")
                .say("tails", "...THE TRANSLATOR MIGHT NEED SOME ADJUSTMENT.")
                .say("tails", "OH! I REPROGRAMMED MOTO WHILE I WAS AT IT. HE'S NOT ANGRY BEEPING ANY MORE. HE'S HAPPY BEEPING.")
                .flag("moto_reprogrammed")
                .leave("pip", -260)
                .leave("tails", 260);

        cast.event("tails_4", "tails", 4).onFarm().between(900, 1600).dry().requires("tails_2")
                .summary("The Buzz Bomber sprinkler blueprint.")
                .enter("tails", 1, 220, 34)
                .say("tails", "{FARMER}! I WAS FLYING PAST AND SAW YOU WATERING ONE SPLASH AT A TIME.")
                .say("tails", "THAT'S VERY OLD-FASHIONED AND COMPLETELY UNACCEPTABLE.")
                .emote("farmer", "sweat")
                .say("tails", "A BUZZ BOMBER'S STINGER IS A NOZZLE. IT ALWAYS WAS. ROBOTNIK JUST POINTED IT AT PEOPLE.")
                .say("tails", "HERE'S A BLUEPRINT. BRING ME BADNIK SCRAP AND I'LL BUILD A SPRINKLER THAT WATERS EIGHT PLOTS.")
                .flag("blueprint_buzz_sprinkler").sfx(Sfx.PERFECT)
                .narrate("YOU GOT THE BUZZ BOMBER SPRINKLER BLUEPRINT.")
                .say("tails", "IT DOESN'T HAVE A NAME YET. I WAS THINKING... THE DRIZZLER.")
                .hop("tails")
                .leave("tails", 260);

        cast.event("tails_6", "tails", 6).near("ruins", 160).between(1000, 1800).dry().requires("tails_4")
                .summary("Tails hears the Tornado's engine humming deep in the Marble Ruins.")
                .enter("tails", -1, 220, 44)
                .say("tails", "{FARMER}, SHH. LISTEN. HEAR THAT HUM? THAT'S A TORNADO ENGINE. MY TORNADO ENGINE.")
                .say("tails", "WHEN WE CRASHED IT CAME LOOSE. I THOUGHT IT SANK IN THE LAKE. IT DIDN'T. IT ROLLED. DOWN THERE.")
                .enter("pud", 1, 200, 46)
                .say("pud", "IT'S IN THE DEEP PART. CHAMBER TEN, MAYBE. I HEAR IT AT NIGHT. IT SOUNDS LONELY.")
                .say("tails", "PUD WILL SHOW US THE WAY. WHEN THE RUINS ARE SAFE.")
                .emote("pud", "sweat")
                .say("pud", "I SAID I'D THINK ABOUT IT.")
                .say("tails", "I KNOW IT'S JUST AN ENGINE. BUT IT GOT US HERE. I WANT IT BACK, {FARMER}.")
                .emote("tails", "...")
                .flag("tornado_engine_heard")
                .leave("pud", 240)
                .leave("tails", -260);

        cast.event("tails_8", "tails", 8).near("workshop", 140).between(2000, 2500).dry().requires("tails_6")
                .summary("A night under the Tornado's wing: Tails is afraid the valley is only a pit stop.")
                .music(Tunes.S1, Tunes.SLZ)
                .enter("tails", 1, 200, 36)
                .say("tails", "CAN'T SLEEP EITHER? I WAS COUNTING RIVETS. THERE ARE 2,112. I COUNTED TWICE.")
                .face("tails", 1).pause(40)
                .say("tails", "CAN I TELL YOU SOMETHING DUMB? WHEN THE TORNADO FLIES, I'M SCARED YOU'LL LEAVE.")
                .sayIf("sonic", "tails", "YOU ALWAYS LEAVE, SONIC. THAT'S WHAT YOU DO. YOU RUN. I USED TO RUN AFTER YOU.")
                .sayIf("knuckles", "tails", "YOU'LL GO BACK TO YOUR ISLAND. YOU HAVE A JOB. I GET IT. I JUST... GOT USED TO YOU.")
                .face("tails", 0)
                .say("tails", "BUT THIS VALLEY IS THE FIRST PLACE I'VE BEEN THAT FEELS LIKE IT'S WAITING FOR US TO COME BACK.")
                .emote("farmer", "heart")
                .say("tails", "...OKAY. THAT WASN'T DUMB. GOOD. GOODNIGHT, {FARMER}.")
                .leave("tails", 120);

        cast.event("tails_10", "tails", 10).near("workshop", 160).between(1700, 2000).dry().requires("tails_8")
                .partner()
                .summary("The engine roars: Tails asks to be Partners and moves his bench onto the farm.")
                .enter("tails", 1, 220, 40)
                .say("tails", "{FARMER}! STAND BACK. NO, FURTHER. FURTHER. OKAY, THAT'S TOO FAR, COME BACK.")
                .sfx(Sfx.SPINDASH).pause(30).sfx(Sfx.DASH).pause(20)
                .music(Tunes.S1, Tunes.ENDING)
                .say("tails", "HEAR THAT? SHE'S RUNNING! THE TORNADO IS RUNNING!")
                .hop("tails").hop("tails")
                .say("tails", "I'VE BEEN THINKING. A WORKSHOP NEEDS A FIELD TO FLY FROM. AND A FIELD NEEDS SOMEONE TO FIX THINGS.")
                .say("tails", "SO... PARTNERS? I MOVE MY BENCH TO YOUR FARM, AND EVERY MORNING I TUNE YOUR GEAR.")
                .emote("farmer", "heart")
                .say("tails", "THAT'S A YES. I'M COUNTING THAT AS A YES. I'M NOT EVEN LETTING YOU TAKE IT BACK.")
                .flag("partner_tails")
                .narrate("TAILS IS YOUR PARTNER. EVERY MORNING HE TUNES YOUR WATER SHIELD.")
                .leave("tails", 260);
    }
}
