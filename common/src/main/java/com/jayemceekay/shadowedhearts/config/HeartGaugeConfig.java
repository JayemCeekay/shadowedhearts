package com.jayemceekay.shadowedhearts.config;

import com.jayemceekay.shadowedhearts.common.shadow.ShadowPokemonProperties;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.jayemceekay.shadowedhearts.common.shadow.HeartGaugeClientState;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Bundled per-species Heart Gauge capacities, overridden by server configuration
 * or a customized legacy JSON table. Missing species fall back to DEFAULT_MAX.
 * Regenerate the bundled table with scripts/generate_heart_gauge_defaults.py.
 */
public final class HeartGaugeConfig {
    private HeartGaugeConfig() {}

    public static final int DEFAULT_MAX = 20000;
    // Even 256-character ASCII keys must fit Minecraft's 1 MiB clientbound custom-payload limit.
    public static final int MAX_OVERRIDE_ENTRIES = 3072;

    private static final Map<String, Integer> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("cobblemon:abomasnow", 10100);
        DEFAULTS.put("cobblemon:abra", 3400);
        DEFAULTS.put("cobblemon:absol", 10500);
        DEFAULTS.put("cobblemon:accelgor", 9100);
        DEFAULTS.put("cobblemon:aegislash", 13800);
        DEFAULTS.put("cobblemon:aerodactyl", 12900);
        DEFAULTS.put("cobblemon:aggron", 13600);
        DEFAULTS.put("cobblemon:aipom", 4400);
        DEFAULTS.put("cobblemon:alakazam", 12900);
        DEFAULTS.put("cobblemon:alcremie", 8700);
        DEFAULTS.put("cobblemon:alomomola", 9200);
        DEFAULTS.put("cobblemon:altaria", 9800);
        DEFAULTS.put("cobblemon:amaura", 6500);
        DEFAULTS.put("cobblemon:ambipom", 9600);
        DEFAULTS.put("cobblemon:amoonguss", 9800);
        DEFAULTS.put("cobblemon:ampharos", 12600);
        DEFAULTS.put("cobblemon:annihilape", 13100);
        DEFAULTS.put("cobblemon:anorith", 6500);
        DEFAULTS.put("cobblemon:appletun", 8800);
        DEFAULTS.put("cobblemon:applin", 3000);
        DEFAULTS.put("cobblemon:araquanid", 8500);
        DEFAULTS.put("cobblemon:arbok", 8500);
        DEFAULTS.put("cobblemon:arboliva", 13500);
        DEFAULTS.put("cobblemon:arcanine", 10300);
        DEFAULTS.put("cobblemon:arceus", 28900);
        DEFAULTS.put("cobblemon:archaludon", 11500);
        DEFAULTS.put("cobblemon:archen", 6500);
        DEFAULTS.put("cobblemon:archeops", 12600);
        DEFAULTS.put("cobblemon:arctibax", 9500);
        DEFAULTS.put("cobblemon:arctovish", 9100);
        DEFAULTS.put("cobblemon:arctozolt", 9100);
        DEFAULTS.put("cobblemon:ariados", 8200);
        DEFAULTS.put("cobblemon:armaldo", 12500);
        DEFAULTS.put("cobblemon:armarouge", 9700);
        DEFAULTS.put("cobblemon:aromatisse", 8900);
        DEFAULTS.put("cobblemon:aron", 3700);
        DEFAULTS.put("cobblemon:arrokuda", 3000);
        DEFAULTS.put("cobblemon:articuno", 22000);
        DEFAULTS.put("cobblemon:audino", 7700);
        DEFAULTS.put("cobblemon:aurorus", 12600);
        DEFAULTS.put("cobblemon:avalugg", 10100);
        DEFAULTS.put("cobblemon:axew", 5500);
        DEFAULTS.put("cobblemon:azelf", 22000);
        DEFAULTS.put("cobblemon:azumarill", 11100);
        DEFAULTS.put("cobblemon:azurill", 2500);
        DEFAULTS.put("cobblemon:bagon", 4800);
        DEFAULTS.put("cobblemon:baltoy", 3300);
        DEFAULTS.put("cobblemon:banette", 9800);
        DEFAULTS.put("cobblemon:barbaracle", 10200);
        DEFAULTS.put("cobblemon:barboach", 3100);
        DEFAULTS.put("cobblemon:barraskewda", 9100);
        DEFAULTS.put("cobblemon:basculegion", 10500);
        DEFAULTS.put("cobblemon:basculin", 5400);
        DEFAULTS.put("cobblemon:bastiodon", 11800);
        DEFAULTS.put("cobblemon:baxcalibur", 15700);
        DEFAULTS.put("cobblemon:bayleef", 9800);
        DEFAULTS.put("cobblemon:beartic", 10000);
        DEFAULTS.put("cobblemon:beautifly", 10500);
        DEFAULTS.put("cobblemon:beedrill", 10500);
        DEFAULTS.put("cobblemon:beheeyem", 10100);
        DEFAULTS.put("cobblemon:beldum", 4300);
        DEFAULTS.put("cobblemon:bellibolt", 8900);
        DEFAULTS.put("cobblemon:bellossom", 12800);
        DEFAULTS.put("cobblemon:bellsprout", 3300);
        DEFAULTS.put("cobblemon:bergmite", 3300);
        DEFAULTS.put("cobblemon:bewear", 9200);
        DEFAULTS.put("cobblemon:bibarel", 7600);
        DEFAULTS.put("cobblemon:bidoof", 3000);
        DEFAULTS.put("cobblemon:binacle", 3400);
        DEFAULTS.put("cobblemon:bisharp", 11000);
        DEFAULTS.put("cobblemon:blacephalon", 18400);
        DEFAULTS.put("cobblemon:blastoise", 14900);
        DEFAULTS.put("cobblemon:blaziken", 14900);
        DEFAULTS.put("cobblemon:blipbug", 3000);
        DEFAULTS.put("cobblemon:blissey", 14700);
        DEFAULTS.put("cobblemon:blitzle", 3300);
        DEFAULTS.put("cobblemon:boldore", 8200);
        DEFAULTS.put("cobblemon:boltund", 9100);
        DEFAULTS.put("cobblemon:bombirdier", 8300);
        DEFAULTS.put("cobblemon:bonsly", 3800);
        DEFAULTS.put("cobblemon:bouffalant", 9900);
        DEFAULTS.put("cobblemon:bounsweet", 3000);
        DEFAULTS.put("cobblemon:braixen", 9800);
        DEFAULTS.put("cobblemon:brambleghast", 8900);
        DEFAULTS.put("cobblemon:bramblin", 3000);
        DEFAULTS.put("cobblemon:braviary", 11200);
        DEFAULTS.put("cobblemon:breloom", 8600);
        DEFAULTS.put("cobblemon:brionne", 9900);
        DEFAULTS.put("cobblemon:bronzong", 9600);
        DEFAULTS.put("cobblemon:bronzor", 3300);
        DEFAULTS.put("cobblemon:brutebonnet", 18300);
        DEFAULTS.put("cobblemon:bruxish", 9400);
        DEFAULTS.put("cobblemon:budew", 3000);
        DEFAULTS.put("cobblemon:buizel", 3700);
        DEFAULTS.put("cobblemon:bulbasaur", 5500);
        DEFAULTS.put("cobblemon:buneary", 3900);
        DEFAULTS.put("cobblemon:bunnelby", 3000);
        DEFAULTS.put("cobblemon:burmy", 3000);
        DEFAULTS.put("cobblemon:butterfree", 10500);
        DEFAULTS.put("cobblemon:buzzwole", 18300);
        DEFAULTS.put("cobblemon:cacnea", 3800);
        DEFAULTS.put("cobblemon:cacturne", 9500);
        DEFAULTS.put("cobblemon:calyrex", 21200);
        DEFAULTS.put("cobblemon:camerupt", 9200);
        DEFAULTS.put("cobblemon:capsakid", 3300);
        DEFAULTS.put("cobblemon:carbink", 10100);
        DEFAULTS.put("cobblemon:carkol", 7900);
        DEFAULTS.put("cobblemon:carnivine", 9200);
        DEFAULTS.put("cobblemon:carracosta", 12300);
        DEFAULTS.put("cobblemon:carvanha", 3400);
        DEFAULTS.put("cobblemon:cascoon", 6500);
        DEFAULTS.put("cobblemon:castform", 6800);
        DEFAULTS.put("cobblemon:caterpie", 3000);
        DEFAULTS.put("cobblemon:celebi", 22700);
        DEFAULTS.put("cobblemon:celesteela", 18300);
        DEFAULTS.put("cobblemon:centiskorch", 9400);
        DEFAULTS.put("cobblemon:ceruledge", 9700);
        DEFAULTS.put("cobblemon:cetitan", 10100);
        DEFAULTS.put("cobblemon:cetoddle", 4300);
        DEFAULTS.put("cobblemon:chandelure", 13400);
        DEFAULTS.put("cobblemon:chansey", 9600);
        DEFAULTS.put("cobblemon:charcadet", 3500);
        DEFAULTS.put("cobblemon:charizard", 15000);
        DEFAULTS.put("cobblemon:charjabug", 7900);
        DEFAULTS.put("cobblemon:charmander", 5400);
        DEFAULTS.put("cobblemon:charmeleon", 9800);
        DEFAULTS.put("cobblemon:chatot", 7700);
        DEFAULTS.put("cobblemon:cherrim", 8800);
        DEFAULTS.put("cobblemon:cherubi", 3000);
        DEFAULTS.put("cobblemon:chesnaught", 14900);
        DEFAULTS.put("cobblemon:chespin", 5500);
        DEFAULTS.put("cobblemon:chewtle", 3000);
        DEFAULTS.put("cobblemon:chienpao", 21700);
        DEFAULTS.put("cobblemon:chikorita", 5500);
        DEFAULTS.put("cobblemon:chimchar", 5400);
        DEFAULTS.put("cobblemon:chimecho", 9400);
        DEFAULTS.put("cobblemon:chinchou", 3700);
        DEFAULTS.put("cobblemon:chingling", 3700);
        DEFAULTS.put("cobblemon:chiyu", 21700);
        DEFAULTS.put("cobblemon:cinccino", 8900);
        DEFAULTS.put("cobblemon:cinderace", 14900);
        DEFAULTS.put("cobblemon:clamperl", 4500);
        DEFAULTS.put("cobblemon:clauncher", 3700);
        DEFAULTS.put("cobblemon:clawitzer", 10000);
        DEFAULTS.put("cobblemon:claydol", 9800);
        DEFAULTS.put("cobblemon:clefable", 12900);
        DEFAULTS.put("cobblemon:clefairy", 7900);
        DEFAULTS.put("cobblemon:cleffa", 3100);
        DEFAULTS.put("cobblemon:clobbopus", 3400);
        DEFAULTS.put("cobblemon:clodsire", 8200);
        DEFAULTS.put("cobblemon:cloyster", 9300);
        DEFAULTS.put("cobblemon:coalossal", 12900);
        DEFAULTS.put("cobblemon:cobalion", 22000);
        DEFAULTS.put("cobblemon:cofagrigus", 9500);
        DEFAULTS.put("cobblemon:combee", 3000);
        DEFAULTS.put("cobblemon:combusken", 9800);
        DEFAULTS.put("cobblemon:comfey", 10300);
        DEFAULTS.put("cobblemon:conkeldurr", 13000);
        DEFAULTS.put("cobblemon:copperajah", 10200);
        DEFAULTS.put("cobblemon:corphish", 3400);
        DEFAULTS.put("cobblemon:corsola", 5000);
        DEFAULTS.put("cobblemon:corviknight", 13100);
        DEFAULTS.put("cobblemon:corvisquire", 7600);
        DEFAULTS.put("cobblemon:cosmoem", 16000);
        DEFAULTS.put("cobblemon:cosmog", 9000);
        DEFAULTS.put("cobblemon:cottonee", 3000);
        DEFAULTS.put("cobblemon:crabominable", 9900);
        DEFAULTS.put("cobblemon:crabrawler", 3800);
        DEFAULTS.put("cobblemon:cradily", 12500);
        DEFAULTS.put("cobblemon:cramorant", 9500);
        DEFAULTS.put("cobblemon:cranidos", 6500);
        DEFAULTS.put("cobblemon:crawdaunt", 9000);
        DEFAULTS.put("cobblemon:cresselia", 22000);
        DEFAULTS.put("cobblemon:croagunk", 3300);
        DEFAULTS.put("cobblemon:crobat", 13300);
        DEFAULTS.put("cobblemon:crocalor", 9800);
        DEFAULTS.put("cobblemon:croconaw", 9900);
        DEFAULTS.put("cobblemon:crustle", 9600);
        DEFAULTS.put("cobblemon:cryogonal", 10700);
        DEFAULTS.put("cobblemon:cubchoo", 3400);
        DEFAULTS.put("cobblemon:cubone", 3500);
        DEFAULTS.put("cobblemon:cufant", 4300);
        DEFAULTS.put("cobblemon:cursola", 10200);
        DEFAULTS.put("cobblemon:cutiefly", 3300);
        DEFAULTS.put("cobblemon:cyclizar", 11100);
        DEFAULTS.put("cobblemon:cyndaquil", 5400);
        DEFAULTS.put("cobblemon:dachsbun", 8900);
        DEFAULTS.put("cobblemon:darkrai", 22700);
        DEFAULTS.put("cobblemon:darmanitan", 9700);
        DEFAULTS.put("cobblemon:dartrix", 9900);
        DEFAULTS.put("cobblemon:darumaka", 3500);
        DEFAULTS.put("cobblemon:decidueye", 14800);
        DEFAULTS.put("cobblemon:dedenne", 8500);
        DEFAULTS.put("cobblemon:deerling", 3800);
        DEFAULTS.put("cobblemon:deino", 4800);
        DEFAULTS.put("cobblemon:delcatty", 8200);
        DEFAULTS.put("cobblemon:delibird", 5400);
        DEFAULTS.put("cobblemon:delphox", 15000);
        DEFAULTS.put("cobblemon:deoxys", 22700);
        DEFAULTS.put("cobblemon:dewgong", 9500);
        DEFAULTS.put("cobblemon:dewott", 9900);
        DEFAULTS.put("cobblemon:dewpider", 3000);
        DEFAULTS.put("cobblemon:dhelmise", 10800);
        DEFAULTS.put("cobblemon:dialga", 27500);
        DEFAULTS.put("cobblemon:diancie", 22700);
        DEFAULTS.put("cobblemon:diggersby", 8100);
        DEFAULTS.put("cobblemon:diglett", 3000);
        DEFAULTS.put("cobblemon:dipplin", 9000);
        DEFAULTS.put("cobblemon:ditto", 5100);
        DEFAULTS.put("cobblemon:dodrio", 9400);
        DEFAULTS.put("cobblemon:doduo", 3400);
        DEFAULTS.put("cobblemon:dolliv", 8600);
        DEFAULTS.put("cobblemon:dondozo", 11700);
        DEFAULTS.put("cobblemon:donphan", 9100);
        DEFAULTS.put("cobblemon:dottler", 6800);
        DEFAULTS.put("cobblemon:doublade", 10000);
        DEFAULTS.put("cobblemon:dracovish", 9100);
        DEFAULTS.put("cobblemon:dracozolt", 9100);
        DEFAULTS.put("cobblemon:dragalge", 10700);
        DEFAULTS.put("cobblemon:dragapult", 16500);
        DEFAULTS.put("cobblemon:dragonair", 9100);
        DEFAULTS.put("cobblemon:dragonite", 15600);
        DEFAULTS.put("cobblemon:drakloak", 11000);
        DEFAULTS.put("cobblemon:drampa", 10300);
        DEFAULTS.put("cobblemon:drapion", 10200);
        DEFAULTS.put("cobblemon:dratini", 3600);
        DEFAULTS.put("cobblemon:drednaw", 8800);
        DEFAULTS.put("cobblemon:dreepy", 5000);
        DEFAULTS.put("cobblemon:drifblim", 9300);
        DEFAULTS.put("cobblemon:drifloon", 3900);
        DEFAULTS.put("cobblemon:drilbur", 3700);
        DEFAULTS.put("cobblemon:drizzile", 9900);
        DEFAULTS.put("cobblemon:drowzee", 3700);
        DEFAULTS.put("cobblemon:druddigon", 10400);
        DEFAULTS.put("cobblemon:dubwool", 8800);
        DEFAULTS.put("cobblemon:ducklett", 3400);
        DEFAULTS.put("cobblemon:dudunsparce", 10400);
        DEFAULTS.put("cobblemon:dugtrio", 8800);
        DEFAULTS.put("cobblemon:dunsparce", 5400);
        DEFAULTS.put("cobblemon:duosion", 9300);
        DEFAULTS.put("cobblemon:duraludon", 6500);
        DEFAULTS.put("cobblemon:durant", 9500);
        DEFAULTS.put("cobblemon:dusclops", 9600);
        DEFAULTS.put("cobblemon:dusknoir", 13500);
        DEFAULTS.put("cobblemon:duskull", 3300);
        DEFAULTS.put("cobblemon:dustox", 10400);
        DEFAULTS.put("cobblemon:dwebble", 3600);
        DEFAULTS.put("cobblemon:eelektrik", 9500);
        DEFAULTS.put("cobblemon:eelektross", 13300);
        DEFAULTS.put("cobblemon:eevee", 4300);
        DEFAULTS.put("cobblemon:eiscue", 9300);
        DEFAULTS.put("cobblemon:ekans", 3100);
        DEFAULTS.put("cobblemon:eldegoss", 8500);
        DEFAULTS.put("cobblemon:electabuzz", 10100);
        DEFAULTS.put("cobblemon:electivire", 14500);
        DEFAULTS.put("cobblemon:electrike", 3900);
        DEFAULTS.put("cobblemon:electrode", 9400);
        DEFAULTS.put("cobblemon:elekid", 4500);
        DEFAULTS.put("cobblemon:elgyem", 3800);
        DEFAULTS.put("cobblemon:emboar", 14900);
        DEFAULTS.put("cobblemon:emolga", 8500);
        DEFAULTS.put("cobblemon:empoleon", 14900);
        DEFAULTS.put("cobblemon:enamorus", 22000);
        DEFAULTS.put("cobblemon:entei", 22000);
        DEFAULTS.put("cobblemon:escavalier", 9200);
        DEFAULTS.put("cobblemon:espathra", 10300);
        DEFAULTS.put("cobblemon:espeon", 10100);
        DEFAULTS.put("cobblemon:espurr", 4000);
        DEFAULTS.put("cobblemon:eternatus", 27900);
        DEFAULTS.put("cobblemon:excadrill", 9600);
        DEFAULTS.put("cobblemon:exeggcute", 3800);
        DEFAULTS.put("cobblemon:exeggutor", 9600);
        DEFAULTS.put("cobblemon:exploud", 13200);
        DEFAULTS.put("cobblemon:falinks", 9900);
        DEFAULTS.put("cobblemon:farfetchd", 4600);
        DEFAULTS.put("cobblemon:farigiraf", 9800);
        DEFAULTS.put("cobblemon:fearow", 8300);
        DEFAULTS.put("cobblemon:feebas", 3000);
        DEFAULTS.put("cobblemon:fennekin", 5400);
        DEFAULTS.put("cobblemon:feraligatr", 14500);
        DEFAULTS.put("cobblemon:ferroseed", 3400);
        DEFAULTS.put("cobblemon:ferrothorn", 10000);
        DEFAULTS.put("cobblemon:fezandipiti", 21100);
        DEFAULTS.put("cobblemon:fidough", 3400);
        DEFAULTS.put("cobblemon:finizen", 3500);
        DEFAULTS.put("cobblemon:finneon", 3700);
        DEFAULTS.put("cobblemon:flaaffy", 7400);
        DEFAULTS.put("cobblemon:flabebe", 3300);
        DEFAULTS.put("cobblemon:flamigo", 10000);
        DEFAULTS.put("cobblemon:flapple", 8800);
        DEFAULTS.put("cobblemon:flareon", 10100);
        DEFAULTS.put("cobblemon:fletchinder", 7600);
        DEFAULTS.put("cobblemon:fletchling", 3000);
        DEFAULTS.put("cobblemon:flittle", 3300);
        DEFAULTS.put("cobblemon:floatzel", 9100);
        DEFAULTS.put("cobblemon:floette", 7700);
        DEFAULTS.put("cobblemon:floragato", 9800);
        DEFAULTS.put("cobblemon:florges", 13000);
        DEFAULTS.put("cobblemon:fluttermane", 18400);
        DEFAULTS.put("cobblemon:flygon", 14900);
        DEFAULTS.put("cobblemon:fomantis", 3000);
        DEFAULTS.put("cobblemon:foongus", 3200);
        DEFAULTS.put("cobblemon:forretress", 9300);
        DEFAULTS.put("cobblemon:fraxure", 11000);
        DEFAULTS.put("cobblemon:frigibax", 3400);
        DEFAULTS.put("cobblemon:frillish", 3800);
        DEFAULTS.put("cobblemon:froakie", 5500);
        DEFAULTS.put("cobblemon:frogadier", 9800);
        DEFAULTS.put("cobblemon:froslass", 10100);
        DEFAULTS.put("cobblemon:frosmoth", 8100);
        DEFAULTS.put("cobblemon:fuecoco", 5400);
        DEFAULTS.put("cobblemon:furfrou", 9100);
        DEFAULTS.put("cobblemon:furret", 7800);
        DEFAULTS.put("cobblemon:gabite", 9800);
        DEFAULTS.put("cobblemon:gallade", 12600);
        DEFAULTS.put("cobblemon:galvantula", 9700);
        DEFAULTS.put("cobblemon:garbodor", 9700);
        DEFAULTS.put("cobblemon:garchomp", 16300);
        DEFAULTS.put("cobblemon:gardevoir", 12600);
        DEFAULTS.put("cobblemon:garganacl", 13100);
        DEFAULTS.put("cobblemon:gastly", 3400);
        DEFAULTS.put("cobblemon:gastrodon", 9300);
        DEFAULTS.put("cobblemon:genesect", 22700);
        DEFAULTS.put("cobblemon:gengar", 13000);
        DEFAULTS.put("cobblemon:geodude", 3300);
        DEFAULTS.put("cobblemon:gholdengo", 8100);
        DEFAULTS.put("cobblemon:gible", 4800);
        DEFAULTS.put("cobblemon:gigalith", 12900);
        DEFAULTS.put("cobblemon:gimmighoul", 3600);
        DEFAULTS.put("cobblemon:girafarig", 5600);
        DEFAULTS.put("cobblemon:giratina", 27500);
        DEFAULTS.put("cobblemon:glaceon", 10100);
        DEFAULTS.put("cobblemon:glalie", 10700);
        DEFAULTS.put("cobblemon:glameow", 3400);
        DEFAULTS.put("cobblemon:glastrier", 22000);
        DEFAULTS.put("cobblemon:gligar", 5200);
        DEFAULTS.put("cobblemon:glimmet", 4100);
        DEFAULTS.put("cobblemon:glimmora", 10100);
        DEFAULTS.put("cobblemon:gliscor", 10200);
        DEFAULTS.put("cobblemon:gloom", 8000);
        DEFAULTS.put("cobblemon:gogoat", 9900);
        DEFAULTS.put("cobblemon:golbat", 8500);
        DEFAULTS.put("cobblemon:goldeen", 3500);
        DEFAULTS.put("cobblemon:golduck", 9600);
        DEFAULTS.put("cobblemon:golem", 12800);
        DEFAULTS.put("cobblemon:golett", 3300);
        DEFAULTS.put("cobblemon:golisopod", 9700);
        DEFAULTS.put("cobblemon:golurk", 10200);
        DEFAULTS.put("cobblemon:goodra", 15200);
        DEFAULTS.put("cobblemon:goomy", 3600);
        DEFAULTS.put("cobblemon:gorebyss", 10000);
        DEFAULTS.put("cobblemon:gossifleur", 3000);
        DEFAULTS.put("cobblemon:gothita", 3200);
        DEFAULTS.put("cobblemon:gothitelle", 13200);
        DEFAULTS.put("cobblemon:gothorita", 8800);
        DEFAULTS.put("cobblemon:gougingfire", 18800);
        DEFAULTS.put("cobblemon:gourgeist", 9400);
        DEFAULTS.put("cobblemon:grafaiai", 9100);
        DEFAULTS.put("cobblemon:granbull", 8600);
        DEFAULTS.put("cobblemon:grapploct", 9800);
        DEFAULTS.put("cobblemon:graveler", 8200);
        DEFAULTS.put("cobblemon:greattusk", 18400);
        DEFAULTS.put("cobblemon:greavard", 3200);
        DEFAULTS.put("cobblemon:greedent", 8700);
        DEFAULTS.put("cobblemon:greninja", 14900);
        DEFAULTS.put("cobblemon:grimer", 3600);
        DEFAULTS.put("cobblemon:grimmsnarl", 13400);
        DEFAULTS.put("cobblemon:grookey", 5400);
        DEFAULTS.put("cobblemon:grotle", 9900);
        DEFAULTS.put("cobblemon:groudon", 27200);
        DEFAULTS.put("cobblemon:grovyle", 9800);
        DEFAULTS.put("cobblemon:growlithe", 4500);
        DEFAULTS.put("cobblemon:grubbin", 3300);
        DEFAULTS.put("cobblemon:grumpig", 9400);
        DEFAULTS.put("cobblemon:gulpin", 3000);
        DEFAULTS.put("cobblemon:gumshoos", 8000);
        DEFAULTS.put("cobblemon:gurdurr", 8400);
        DEFAULTS.put("cobblemon:guzzlord", 18300);
        DEFAULTS.put("cobblemon:gyarados", 9100);
        DEFAULTS.put("cobblemon:hakamoo", 10600);
        DEFAULTS.put("cobblemon:happiny", 3700);
        DEFAULTS.put("cobblemon:hariyama", 8700);
        DEFAULTS.put("cobblemon:hatenna", 3000);
        DEFAULTS.put("cobblemon:hatterene", 13400);
        DEFAULTS.put("cobblemon:hattrem", 8600);
        DEFAULTS.put("cobblemon:haunter", 8400);
        DEFAULTS.put("cobblemon:hawlucha", 10000);
        DEFAULTS.put("cobblemon:haxorus", 15800);
        DEFAULTS.put("cobblemon:heatmor", 9500);
        DEFAULTS.put("cobblemon:heatran", 22700);
        DEFAULTS.put("cobblemon:heliolisk", 8900);
        DEFAULTS.put("cobblemon:helioptile", 3100);
        DEFAULTS.put("cobblemon:heracross", 10800);
        DEFAULTS.put("cobblemon:herdier", 7500);
        DEFAULTS.put("cobblemon:hippopotas", 3700);
        DEFAULTS.put("cobblemon:hippowdon", 9900);
        DEFAULTS.put("cobblemon:hitmonchan", 9200);
        DEFAULTS.put("cobblemon:hitmonlee", 9200);
        DEFAULTS.put("cobblemon:hitmontop", 9200);
        DEFAULTS.put("cobblemon:honchkrow", 10000);
        DEFAULTS.put("cobblemon:honedge", 4200);
        DEFAULTS.put("cobblemon:hooh", 27500);
        DEFAULTS.put("cobblemon:hoopa", 22700);
        DEFAULTS.put("cobblemon:hoothoot", 3000);
        DEFAULTS.put("cobblemon:hoppip", 3000);
        DEFAULTS.put("cobblemon:horsea", 3300);
        DEFAULTS.put("cobblemon:houndoom", 9100);
        DEFAULTS.put("cobblemon:houndour", 3700);
        DEFAULTS.put("cobblemon:houndstone", 8900);
        DEFAULTS.put("cobblemon:huntail", 10000);
        DEFAULTS.put("cobblemon:hydrapple", 12800);
        DEFAULTS.put("cobblemon:hydreigon", 16500);
        DEFAULTS.put("cobblemon:hypno", 9000);
        DEFAULTS.put("cobblemon:igglybuff", 2500);
        DEFAULTS.put("cobblemon:illumise", 7900);
        DEFAULTS.put("cobblemon:impidimp", 3000);
        DEFAULTS.put("cobblemon:incineroar", 14800);
        DEFAULTS.put("cobblemon:indeedee", 9600);
        DEFAULTS.put("cobblemon:infernape", 15000);
        DEFAULTS.put("cobblemon:inkay", 3100);
        DEFAULTS.put("cobblemon:inteleon", 14900);
        DEFAULTS.put("cobblemon:ironboulder", 18800);
        DEFAULTS.put("cobblemon:ironbundle", 18300);
        DEFAULTS.put("cobblemon:ironcrown", 18800);
        DEFAULTS.put("cobblemon:ironhands", 18300);
        DEFAULTS.put("cobblemon:ironjugulis", 18400);
        DEFAULTS.put("cobblemon:ironleaves", 18900);
        DEFAULTS.put("cobblemon:ironmoth", 18400);
        DEFAULTS.put("cobblemon:ironthorns", 18400);
        DEFAULTS.put("cobblemon:irontreads", 18400);
        DEFAULTS.put("cobblemon:ironvaliant", 18800);
        DEFAULTS.put("cobblemon:ivysaur", 9800);
        DEFAULTS.put("cobblemon:jangmoo", 4800);
        DEFAULTS.put("cobblemon:jellicent", 10100);
        DEFAULTS.put("cobblemon:jigglypuff", 6900);
        DEFAULTS.put("cobblemon:jirachi", 22700);
        DEFAULTS.put("cobblemon:jolteon", 10100);
        DEFAULTS.put("cobblemon:joltik", 3500);
        DEFAULTS.put("cobblemon:jumpluff", 12100);
        DEFAULTS.put("cobblemon:jynx", 9900);
        DEFAULTS.put("cobblemon:kabuto", 6500);
        DEFAULTS.put("cobblemon:kabutops", 12500);
        DEFAULTS.put("cobblemon:kadabra", 7700);
        DEFAULTS.put("cobblemon:kakuna", 6500);
        DEFAULTS.put("cobblemon:kangaskhan", 11100);
        DEFAULTS.put("cobblemon:karrablast", 3500);
        DEFAULTS.put("cobblemon:kartana", 18300);
        DEFAULTS.put("cobblemon:kecleon", 8800);
        DEFAULTS.put("cobblemon:keldeo", 22000);
        DEFAULTS.put("cobblemon:kilowattrel", 8900);
        DEFAULTS.put("cobblemon:kingambit", 14500);
        DEFAULTS.put("cobblemon:kingdra", 13400);
        DEFAULTS.put("cobblemon:kingler", 9200);
        DEFAULTS.put("cobblemon:kirlia", 7200);
        DEFAULTS.put("cobblemon:klang", 9700);
        DEFAULTS.put("cobblemon:klawf", 9100);
        DEFAULTS.put("cobblemon:kleavor", 11900);
        DEFAULTS.put("cobblemon:klefki", 9200);
        DEFAULTS.put("cobblemon:klink", 3300);
        DEFAULTS.put("cobblemon:klinklang", 14000);
        DEFAULTS.put("cobblemon:koffing", 3800);
        DEFAULTS.put("cobblemon:komala", 9600);
        DEFAULTS.put("cobblemon:kommoo", 16100);
        DEFAULTS.put("cobblemon:koraidon", 27200);
        DEFAULTS.put("cobblemon:krabby", 3600);
        DEFAULTS.put("cobblemon:kricketot", 3000);
        DEFAULTS.put("cobblemon:kricketune", 7400);
        DEFAULTS.put("cobblemon:krokorok", 8400);
        DEFAULTS.put("cobblemon:krookodile", 13300);
        DEFAULTS.put("cobblemon:kubfu", 10000);
        DEFAULTS.put("cobblemon:kyogre", 27200);
        DEFAULTS.put("cobblemon:kyurem", 26800);
        DEFAULTS.put("cobblemon:lairon", 9100);
        DEFAULTS.put("cobblemon:lampent", 9300);
        DEFAULTS.put("cobblemon:landorus", 22700);
        DEFAULTS.put("cobblemon:lanturn", 9000);
        DEFAULTS.put("cobblemon:lapras", 11200);
        DEFAULTS.put("cobblemon:larvesta", 5000);
        DEFAULTS.put("cobblemon:larvitar", 4800);
        DEFAULTS.put("cobblemon:latias", 22700);
        DEFAULTS.put("cobblemon:latios", 22700);
        DEFAULTS.put("cobblemon:leafeon", 10100);
        DEFAULTS.put("cobblemon:leavanny", 12700);
        DEFAULTS.put("cobblemon:lechonk", 3000);
        DEFAULTS.put("cobblemon:ledian", 7900);
        DEFAULTS.put("cobblemon:ledyba", 3000);
        DEFAULTS.put("cobblemon:lickilicky", 10600);
        DEFAULTS.put("cobblemon:lickitung", 5300);
        DEFAULTS.put("cobblemon:liepard", 8300);
        DEFAULTS.put("cobblemon:lileep", 6500);
        DEFAULTS.put("cobblemon:lilligant", 8800);
        DEFAULTS.put("cobblemon:lillipup", 3000);
        DEFAULTS.put("cobblemon:linoone", 8200);
        DEFAULTS.put("cobblemon:litleo", 4200);
        DEFAULTS.put("cobblemon:litten", 5500);
        DEFAULTS.put("cobblemon:litwick", 3000);
        DEFAULTS.put("cobblemon:lokix", 8900);
        DEFAULTS.put("cobblemon:lombre", 7200);
        DEFAULTS.put("cobblemon:lopunny", 9400);
        DEFAULTS.put("cobblemon:lotad", 3000);
        DEFAULTS.put("cobblemon:loudred", 7700);
        DEFAULTS.put("cobblemon:lucario", 10500);
        DEFAULTS.put("cobblemon:ludicolo", 12300);
        DEFAULTS.put("cobblemon:lugia", 27500);
        DEFAULTS.put("cobblemon:lumineon", 9200);
        DEFAULTS.put("cobblemon:lunala", 27500);
        DEFAULTS.put("cobblemon:lunatone", 9100);
        DEFAULTS.put("cobblemon:lurantis", 9600);
        DEFAULTS.put("cobblemon:luvdisc", 5100);
        DEFAULTS.put("cobblemon:luxio", 8000);
        DEFAULTS.put("cobblemon:luxray", 13300);
        DEFAULTS.put("cobblemon:lycanroc", 8900);
        DEFAULTS.put("cobblemon:mabosstiff", 9400);
        DEFAULTS.put("cobblemon:machamp", 13000);
        DEFAULTS.put("cobblemon:machoke", 8600);
        DEFAULTS.put("cobblemon:machop", 3400);
        DEFAULTS.put("cobblemon:magby", 4500);
        DEFAULTS.put("cobblemon:magcargo", 9600);
        DEFAULTS.put("cobblemon:magearna", 22700);
        DEFAULTS.put("cobblemon:magikarp", 3000);
        DEFAULTS.put("cobblemon:magmar", 9500);
        DEFAULTS.put("cobblemon:magmortar", 13900);
        DEFAULTS.put("cobblemon:magnemite", 3600);
        DEFAULTS.put("cobblemon:magneton", 9300);
        DEFAULTS.put("cobblemon:magnezone", 13600);
        DEFAULTS.put("cobblemon:makuhita", 3000);
        DEFAULTS.put("cobblemon:malamar", 9300);
        DEFAULTS.put("cobblemon:mamoswine", 13400);
        DEFAULTS.put("cobblemon:manaphy", 22700);
        DEFAULTS.put("cobblemon:mandibuzz", 11200);
        DEFAULTS.put("cobblemon:manectric", 9700);
        DEFAULTS.put("cobblemon:mankey", 3400);
        DEFAULTS.put("cobblemon:mantine", 9500);
        DEFAULTS.put("cobblemon:mantyke", 4300);
        DEFAULTS.put("cobblemon:maractus", 8800);
        DEFAULTS.put("cobblemon:mareanie", 3400);
        DEFAULTS.put("cobblemon:mareep", 3000);
        DEFAULTS.put("cobblemon:marill", 6600);
        DEFAULTS.put("cobblemon:marowak", 8800);
        DEFAULTS.put("cobblemon:marshadow", 22700);
        DEFAULTS.put("cobblemon:marshtomp", 9800);
        DEFAULTS.put("cobblemon:maschiff", 3800);
        DEFAULTS.put("cobblemon:masquerain", 9200);
        DEFAULTS.put("cobblemon:maushold", 8900);
        DEFAULTS.put("cobblemon:mawile", 7400);
        DEFAULTS.put("cobblemon:medicham", 9300);
        DEFAULTS.put("cobblemon:meditite", 3200);
        DEFAULTS.put("cobblemon:meganium", 14600);
        DEFAULTS.put("cobblemon:melmetal", 22700);
        DEFAULTS.put("cobblemon:meloetta", 22700);
        DEFAULTS.put("cobblemon:meltan", 10500);
        DEFAULTS.put("cobblemon:meowscarada", 14900);
        DEFAULTS.put("cobblemon:meowstic", 8900);
        DEFAULTS.put("cobblemon:meowth", 3200);
        DEFAULTS.put("cobblemon:mesprit", 22000);
        DEFAULTS.put("cobblemon:metagross", 15600);
        DEFAULTS.put("cobblemon:metang", 9100);
        DEFAULTS.put("cobblemon:metapod", 6500);
        DEFAULTS.put("cobblemon:mew", 22700);
        DEFAULTS.put("cobblemon:mewtwo", 27500);
        DEFAULTS.put("cobblemon:mienfoo", 4500);
        DEFAULTS.put("cobblemon:mienshao", 11600);
        DEFAULTS.put("cobblemon:mightyena", 7900);
        DEFAULTS.put("cobblemon:milcery", 3000);
        DEFAULTS.put("cobblemon:milotic", 8700);
        DEFAULTS.put("cobblemon:miltank", 9900);
        DEFAULTS.put("cobblemon:mimejr", 3400);
        DEFAULTS.put("cobblemon:mimikyu", 10100);
        DEFAULTS.put("cobblemon:minccino", 3300);
        DEFAULTS.put("cobblemon:minior", 10800);
        DEFAULTS.put("cobblemon:minun", 7900);
        DEFAULTS.put("cobblemon:miraidon", 27200);
        DEFAULTS.put("cobblemon:misdreavus", 5400);
        DEFAULTS.put("cobblemon:mismagius", 10100);
        DEFAULTS.put("cobblemon:moltres", 22000);
        DEFAULTS.put("cobblemon:monferno", 9600);
        DEFAULTS.put("cobblemon:morelull", 3100);
        DEFAULTS.put("cobblemon:morgrem", 8600);
        DEFAULTS.put("cobblemon:morpeko", 8700);
        DEFAULTS.put("cobblemon:mothim", 8400);
        DEFAULTS.put("cobblemon:mrmime", 9000);
        DEFAULTS.put("cobblemon:mrrime", 14100);
        DEFAULTS.put("cobblemon:mudbray", 4400);
        DEFAULTS.put("cobblemon:mudkip", 5400);
        DEFAULTS.put("cobblemon:mudsdale", 9500);
        DEFAULTS.put("cobblemon:muk", 10000);
        DEFAULTS.put("cobblemon:munchlax", 4500);
        DEFAULTS.put("cobblemon:munkidori", 21100);
        DEFAULTS.put("cobblemon:munna", 3200);
        DEFAULTS.put("cobblemon:murkrow", 5000);
        DEFAULTS.put("cobblemon:musharna", 8900);
        DEFAULTS.put("cobblemon:nacli", 3000);
        DEFAULTS.put("cobblemon:naclstack", 7900);
        DEFAULTS.put("cobblemon:naganadel", 18000);
        DEFAULTS.put("cobblemon:natu", 3500);
        DEFAULTS.put("cobblemon:necrozma", 24700);
        DEFAULTS.put("cobblemon:nickit", 3000);
        DEFAULTS.put("cobblemon:nidoking", 13700);
        DEFAULTS.put("cobblemon:nidoqueen", 13700);
        DEFAULTS.put("cobblemon:nidoranf", 3600);
        DEFAULTS.put("cobblemon:nidoranm", 3500);
        DEFAULTS.put("cobblemon:nidorina", 8000);
        DEFAULTS.put("cobblemon:nidorino", 8000);
        DEFAULTS.put("cobblemon:nihilego", 18300);
        DEFAULTS.put("cobblemon:nincada", 3000);
        DEFAULTS.put("cobblemon:ninetales", 9100);
        DEFAULTS.put("cobblemon:ninjask", 8300);
        DEFAULTS.put("cobblemon:noctowl", 8400);
        DEFAULTS.put("cobblemon:noibat", 3300);
        DEFAULTS.put("cobblemon:noivern", 11600);
        DEFAULTS.put("cobblemon:nosepass", 4900);
        DEFAULTS.put("cobblemon:numel", 3400);
        DEFAULTS.put("cobblemon:nuzleaf", 7200);
        DEFAULTS.put("cobblemon:nymble", 3000);
        DEFAULTS.put("cobblemon:obstagoon", 13000);
        DEFAULTS.put("cobblemon:octillery", 8900);
        DEFAULTS.put("cobblemon:oddish", 3500);
        DEFAULTS.put("cobblemon:ogerpon", 21000);
        DEFAULTS.put("cobblemon:oinkologne", 8400);
        DEFAULTS.put("cobblemon:okidogi", 21100);
        DEFAULTS.put("cobblemon:omanyte", 6500);
        DEFAULTS.put("cobblemon:omastar", 12500);
        DEFAULTS.put("cobblemon:onix", 5300);
        DEFAULTS.put("cobblemon:oranguru", 8300);
        DEFAULTS.put("cobblemon:orbeetle", 12600);
        DEFAULTS.put("cobblemon:oricorio", 8000);
        DEFAULTS.put("cobblemon:orthworm", 9700);
        DEFAULTS.put("cobblemon:oshawott", 5400);
        DEFAULTS.put("cobblemon:overqwil", 10200);
        DEFAULTS.put("cobblemon:pachirisu", 7900);
        DEFAULTS.put("cobblemon:palafin", 9800);
        DEFAULTS.put("cobblemon:palkia", 27500);
        DEFAULTS.put("cobblemon:palossand", 10200);
        DEFAULTS.put("cobblemon:palpitoad", 8200);
        DEFAULTS.put("cobblemon:pancham", 3900);
        DEFAULTS.put("cobblemon:pangoro", 9600);
        DEFAULTS.put("cobblemon:panpour", 4100);
        DEFAULTS.put("cobblemon:pansage", 4100);
        DEFAULTS.put("cobblemon:pansear", 4100);
        DEFAULTS.put("cobblemon:paras", 3100);
        DEFAULTS.put("cobblemon:parasect", 8400);
        DEFAULTS.put("cobblemon:passimian", 8300);
        DEFAULTS.put("cobblemon:patrat", 3000);
        DEFAULTS.put("cobblemon:pawmi", 3000);
        DEFAULTS.put("cobblemon:pawmo", 7700);
        DEFAULTS.put("cobblemon:pawmot", 11600);
        DEFAULTS.put("cobblemon:pawniard", 3800);
        DEFAULTS.put("cobblemon:pecharunt", 22700);
        DEFAULTS.put("cobblemon:pelipper", 8800);
        DEFAULTS.put("cobblemon:perrserker", 8900);
        DEFAULTS.put("cobblemon:persian", 8900);
        DEFAULTS.put("cobblemon:petilil", 3000);
        DEFAULTS.put("cobblemon:phanpy", 3700);
        DEFAULTS.put("cobblemon:phantump", 3400);
        DEFAULTS.put("cobblemon:pheromosa", 18300);
        DEFAULTS.put("cobblemon:phione", 20000);
        DEFAULTS.put("cobblemon:pichu", 3000);
        DEFAULTS.put("cobblemon:pidgeot", 12800);
        DEFAULTS.put("cobblemon:pidgeotto", 7500);
        DEFAULTS.put("cobblemon:pidgey", 3000);
        DEFAULTS.put("cobblemon:pidove", 3000);
        DEFAULTS.put("cobblemon:pignite", 9900);
        DEFAULTS.put("cobblemon:pikachu", 7800);
        DEFAULTS.put("cobblemon:pikipek", 3000);
        DEFAULTS.put("cobblemon:piloswine", 9300);
        DEFAULTS.put("cobblemon:pincurchin", 8300);
        DEFAULTS.put("cobblemon:pineco", 3200);
        DEFAULTS.put("cobblemon:pinsir", 10800);
        DEFAULTS.put("cobblemon:piplup", 5500);
        DEFAULTS.put("cobblemon:plusle", 7900);
        DEFAULTS.put("cobblemon:poipole", 9000);
        DEFAULTS.put("cobblemon:politoed", 12800);
        DEFAULTS.put("cobblemon:poliwag", 3300);
        DEFAULTS.put("cobblemon:poliwhirl", 8200);
        DEFAULTS.put("cobblemon:poliwrath", 12900);
        DEFAULTS.put("cobblemon:poltchageist", 4000);
        DEFAULTS.put("cobblemon:polteageist", 9200);
        DEFAULTS.put("cobblemon:ponyta", 4700);
        DEFAULTS.put("cobblemon:poochyena", 3000);
        DEFAULTS.put("cobblemon:popplio", 5500);
        DEFAULTS.put("cobblemon:porygon", 5500);
        DEFAULTS.put("cobblemon:porygon2", 10600);
        DEFAULTS.put("cobblemon:porygonz", 14600);
        DEFAULTS.put("cobblemon:primarina", 14800);
        DEFAULTS.put("cobblemon:primeape", 9000);
        DEFAULTS.put("cobblemon:prinplup", 9800);
        DEFAULTS.put("cobblemon:probopass", 10400);
        DEFAULTS.put("cobblemon:psyduck", 3500);
        DEFAULTS.put("cobblemon:pumpkaboo", 3800);
        DEFAULTS.put("cobblemon:pupitar", 10200);
        DEFAULTS.put("cobblemon:purrloin", 3000);
        DEFAULTS.put("cobblemon:purugly", 9700);
        DEFAULTS.put("cobblemon:pyroar", 9800);
        DEFAULTS.put("cobblemon:pyukumuku", 7600);
        DEFAULTS.put("cobblemon:quagsire", 8200);
        DEFAULTS.put("cobblemon:quaquaval", 14900);
        DEFAULTS.put("cobblemon:quaxly", 5400);
        DEFAULTS.put("cobblemon:quaxwell", 9800);
        DEFAULTS.put("cobblemon:quilava", 9900);
        DEFAULTS.put("cobblemon:quilladin", 9800);
        DEFAULTS.put("cobblemon:qwilfish", 5400);
        DEFAULTS.put("cobblemon:raboot", 9900);
        DEFAULTS.put("cobblemon:rabsca", 8800);
        DEFAULTS.put("cobblemon:ragingbolt", 18800);
        DEFAULTS.put("cobblemon:raichu", 12700);
        DEFAULTS.put("cobblemon:raikou", 22000);
        DEFAULTS.put("cobblemon:ralts", 3000);
        DEFAULTS.put("cobblemon:rampardos", 11800);
        DEFAULTS.put("cobblemon:rapidash", 10200);
        DEFAULTS.put("cobblemon:raticate", 8000);
        DEFAULTS.put("cobblemon:rattata", 3000);
        DEFAULTS.put("cobblemon:rayquaza", 27500);
        DEFAULTS.put("cobblemon:regice", 22000);
        DEFAULTS.put("cobblemon:regidrago", 22000);
        DEFAULTS.put("cobblemon:regieleki", 22000);
        DEFAULTS.put("cobblemon:regigigas", 27200);
        DEFAULTS.put("cobblemon:regirock", 22000);
        DEFAULTS.put("cobblemon:registeel", 22000);
        DEFAULTS.put("cobblemon:relicanth", 9900);
        DEFAULTS.put("cobblemon:rellor", 3000);
        DEFAULTS.put("cobblemon:remoraid", 3300);
        DEFAULTS.put("cobblemon:reshiram", 27500);
        DEFAULTS.put("cobblemon:reuniclus", 13800);
        DEFAULTS.put("cobblemon:revavroom", 10700);
        DEFAULTS.put("cobblemon:rhydon", 11000);
        DEFAULTS.put("cobblemon:rhyhorn", 3900);
        DEFAULTS.put("cobblemon:rhyperior", 15000);
        DEFAULTS.put("cobblemon:ribombee", 8800);
        DEFAULTS.put("cobblemon:rillaboom", 14900);
        DEFAULTS.put("cobblemon:riolu", 4500);
        DEFAULTS.put("cobblemon:roaringmoon", 18800);
        DEFAULTS.put("cobblemon:rockruff", 3000);
        DEFAULTS.put("cobblemon:roggenrola", 3000);
        DEFAULTS.put("cobblemon:rolycoly", 3000);
        DEFAULTS.put("cobblemon:rookidee", 3000);
        DEFAULTS.put("cobblemon:roselia", 8100);
        DEFAULTS.put("cobblemon:roserade", 12900);
        DEFAULTS.put("cobblemon:rotom", 7200);
        DEFAULTS.put("cobblemon:rowlet", 5500);
        DEFAULTS.put("cobblemon:rufflet", 3900);
        DEFAULTS.put("cobblemon:runerigus", 9500);
        DEFAULTS.put("cobblemon:sableye", 6800);
        DEFAULTS.put("cobblemon:salamence", 16400);
        DEFAULTS.put("cobblemon:salandit", 3500);
        DEFAULTS.put("cobblemon:salazzle", 9600);
        DEFAULTS.put("cobblemon:samurott", 14900);
        DEFAULTS.put("cobblemon:sandaconda", 9700);
        DEFAULTS.put("cobblemon:sandile", 3200);
        DEFAULTS.put("cobblemon:sandshrew", 3300);
        DEFAULTS.put("cobblemon:sandslash", 8500);
        DEFAULTS.put("cobblemon:sandygast", 3500);
        DEFAULTS.put("cobblemon:sandyshocks", 18400);
        DEFAULTS.put("cobblemon:sawk", 9100);
        DEFAULTS.put("cobblemon:sawsbuck", 9500);
        DEFAULTS.put("cobblemon:scatterbug", 3000);
        DEFAULTS.put("cobblemon:sceptile", 14900);
        DEFAULTS.put("cobblemon:scizor", 11300);
        DEFAULTS.put("cobblemon:scolipede", 12400);
        DEFAULTS.put("cobblemon:scorbunny", 5400);
        DEFAULTS.put("cobblemon:scovillain", 9000);
        DEFAULTS.put("cobblemon:scrafty", 9900);
        DEFAULTS.put("cobblemon:scraggy", 3900);
        DEFAULTS.put("cobblemon:screamtail", 18300);
        DEFAULTS.put("cobblemon:scyther", 6500);
        DEFAULTS.put("cobblemon:seadra", 9200);
        DEFAULTS.put("cobblemon:seaking", 9400);
        DEFAULTS.put("cobblemon:sealeo", 8800);
        DEFAULTS.put("cobblemon:seedot", 3000);
        DEFAULTS.put("cobblemon:seel", 3600);
        DEFAULTS.put("cobblemon:seismitoad", 13000);
        DEFAULTS.put("cobblemon:sentret", 3000);
        DEFAULTS.put("cobblemon:serperior", 14900);
        DEFAULTS.put("cobblemon:servine", 9900);
        DEFAULTS.put("cobblemon:seviper", 9400);
        DEFAULTS.put("cobblemon:sewaddle", 3400);
        DEFAULTS.put("cobblemon:sharpedo", 9200);
        DEFAULTS.put("cobblemon:shaymin", 22700);
        DEFAULTS.put("cobblemon:shedinja", 7200);
        DEFAULTS.put("cobblemon:shelgon", 10300);
        DEFAULTS.put("cobblemon:shellder", 3400);
        DEFAULTS.put("cobblemon:shellos", 3600);
        DEFAULTS.put("cobblemon:shelmet", 3400);
        DEFAULTS.put("cobblemon:shieldon", 6500);
        DEFAULTS.put("cobblemon:shiftry", 12300);
        DEFAULTS.put("cobblemon:shiinotic", 8400);
        DEFAULTS.put("cobblemon:shinx", 3400);
        DEFAULTS.put("cobblemon:shroodle", 3200);
        DEFAULTS.put("cobblemon:shroomish", 3300);
        DEFAULTS.put("cobblemon:shuckle", 9400);
        DEFAULTS.put("cobblemon:shuppet", 3300);
        DEFAULTS.put("cobblemon:sigilyph", 9900);
        DEFAULTS.put("cobblemon:silcoon", 6500);
        DEFAULTS.put("cobblemon:silicobra", 3500);
        DEFAULTS.put("cobblemon:silvally", 21700);
        DEFAULTS.put("cobblemon:simipour", 9800);
        DEFAULTS.put("cobblemon:simisage", 9800);
        DEFAULTS.put("cobblemon:simisear", 9800);
        DEFAULTS.put("cobblemon:sinistcha", 9800);
        DEFAULTS.put("cobblemon:sinistea", 3400);
        DEFAULTS.put("cobblemon:sirfetchd", 11000);
        DEFAULTS.put("cobblemon:sizzlipede", 3400);
        DEFAULTS.put("cobblemon:skarmory", 10500);
        DEFAULTS.put("cobblemon:skeledirge", 14900);
        DEFAULTS.put("cobblemon:skiddo", 3900);
        DEFAULTS.put("cobblemon:skiploom", 7400);
        DEFAULTS.put("cobblemon:skitty", 3000);
        DEFAULTS.put("cobblemon:skorupi", 3700);
        DEFAULTS.put("cobblemon:skrelp", 3500);
        DEFAULTS.put("cobblemon:skuntank", 9600);
        DEFAULTS.put("cobblemon:skwovet", 3000);
        DEFAULTS.put("cobblemon:slaking", 13300);
        DEFAULTS.put("cobblemon:slakoth", 3000);
        DEFAULTS.put("cobblemon:sliggoo", 9900);
        DEFAULTS.put("cobblemon:slitherwing", 18400);
        DEFAULTS.put("cobblemon:slowbro", 9800);
        DEFAULTS.put("cobblemon:slowking", 9900);
        DEFAULTS.put("cobblemon:slowpoke", 3500);
        DEFAULTS.put("cobblemon:slugma", 3000);
        DEFAULTS.put("cobblemon:slurpuff", 9000);
        DEFAULTS.put("cobblemon:smeargle", 4000);
        DEFAULTS.put("cobblemon:smoliv", 3400);
        DEFAULTS.put("cobblemon:smoochum", 4300);
        DEFAULTS.put("cobblemon:sneasel", 5800);
        DEFAULTS.put("cobblemon:sneasler", 10900);
        DEFAULTS.put("cobblemon:snivy", 5400);
        DEFAULTS.put("cobblemon:snom", 3000);
        DEFAULTS.put("cobblemon:snorlax", 10700);
        DEFAULTS.put("cobblemon:snorunt", 3300);
        DEFAULTS.put("cobblemon:snover", 3700);
        DEFAULTS.put("cobblemon:snubbull", 3300);
        DEFAULTS.put("cobblemon:sobble", 5400);
        DEFAULTS.put("cobblemon:solgaleo", 27500);
        DEFAULTS.put("cobblemon:solosis", 3800);
        DEFAULTS.put("cobblemon:solrock", 9100);
        DEFAULTS.put("cobblemon:spearow", 3000);
        DEFAULTS.put("cobblemon:spectrier", 22000);
        DEFAULTS.put("cobblemon:spewpa", 6500);
        DEFAULTS.put("cobblemon:spheal", 3200);
        DEFAULTS.put("cobblemon:spidops", 7600);
        DEFAULTS.put("cobblemon:spinarak", 3000);
        DEFAULTS.put("cobblemon:spinda", 6600);
        DEFAULTS.put("cobblemon:spiritomb", 10200);
        DEFAULTS.put("cobblemon:spoink", 3700);
        DEFAULTS.put("cobblemon:sprigatito", 5400);
        DEFAULTS.put("cobblemon:spritzee", 3800);
        DEFAULTS.put("cobblemon:squawkabilly", 7600);
        DEFAULTS.put("cobblemon:squirtle", 5500);
        DEFAULTS.put("cobblemon:stakataka", 18400);
        DEFAULTS.put("cobblemon:stantler", 5800);
        DEFAULTS.put("cobblemon:staraptor", 12700);
        DEFAULTS.put("cobblemon:staravia", 7200);
        DEFAULTS.put("cobblemon:starly", 3000);
        DEFAULTS.put("cobblemon:starmie", 9500);
        DEFAULTS.put("cobblemon:staryu", 3800);
        DEFAULTS.put("cobblemon:steelix", 10600);
        DEFAULTS.put("cobblemon:steenee", 7100);
        DEFAULTS.put("cobblemon:stonjourner", 9300);
        DEFAULTS.put("cobblemon:stoutland", 12700);
        DEFAULTS.put("cobblemon:stufful", 3800);
        DEFAULTS.put("cobblemon:stunfisk", 9200);
        DEFAULTS.put("cobblemon:stunky", 3700);
        DEFAULTS.put("cobblemon:sudowoodo", 8500);
        DEFAULTS.put("cobblemon:suicune", 22000);
        DEFAULTS.put("cobblemon:sunflora", 7600);
        DEFAULTS.put("cobblemon:sunkern", 3000);
        DEFAULTS.put("cobblemon:surskit", 3500);
        DEFAULTS.put("cobblemon:swablu", 3400);
        DEFAULTS.put("cobblemon:swadloon", 7800);
        DEFAULTS.put("cobblemon:swalot", 8900);
        DEFAULTS.put("cobblemon:swampert", 15000);
        DEFAULTS.put("cobblemon:swanna", 9700);
        DEFAULTS.put("cobblemon:swellow", 8700);
        DEFAULTS.put("cobblemon:swinub", 3000);
        DEFAULTS.put("cobblemon:swirlix", 3800);
        DEFAULTS.put("cobblemon:swoobat", 8900);
        DEFAULTS.put("cobblemon:sylveon", 10100);
        DEFAULTS.put("cobblemon:tadbulb", 3000);
        DEFAULTS.put("cobblemon:taillow", 3000);
        DEFAULTS.put("cobblemon:talonflame", 12900);
        DEFAULTS.put("cobblemon:tandemaus", 3400);
        DEFAULTS.put("cobblemon:tangela", 5400);
        DEFAULTS.put("cobblemon:tangrowth", 9400);
        DEFAULTS.put("cobblemon:tapubulu", 21700);
        DEFAULTS.put("cobblemon:tapufini", 21700);
        DEFAULTS.put("cobblemon:tapukoko", 21700);
        DEFAULTS.put("cobblemon:tapulele", 21700);
        DEFAULTS.put("cobblemon:tarountula", 3000);
        DEFAULTS.put("cobblemon:tatsugiri", 9900);
        DEFAULTS.put("cobblemon:tauros", 9900);
        DEFAULTS.put("cobblemon:teddiursa", 3700);
        DEFAULTS.put("cobblemon:tentacool", 3800);
        DEFAULTS.put("cobblemon:tentacruel", 9600);
        DEFAULTS.put("cobblemon:tepig", 5400);
        DEFAULTS.put("cobblemon:terapagos", 20000);
        DEFAULTS.put("cobblemon:terrakion", 22000);
        DEFAULTS.put("cobblemon:thievul", 8100);
        DEFAULTS.put("cobblemon:throh", 9100);
        DEFAULTS.put("cobblemon:thundurus", 22000);
        DEFAULTS.put("cobblemon:thwackey", 9900);
        DEFAULTS.put("cobblemon:timburr", 3400);
        DEFAULTS.put("cobblemon:tinglu", 21700);
        DEFAULTS.put("cobblemon:tinkatink", 3300);
        DEFAULTS.put("cobblemon:tinkaton", 13100);
        DEFAULTS.put("cobblemon:tinkatuff", 8200);
        DEFAULTS.put("cobblemon:tirtouga", 6500);
        DEFAULTS.put("cobblemon:toedscool", 3800);
        DEFAULTS.put("cobblemon:toedscruel", 9400);
        DEFAULTS.put("cobblemon:togedemaru", 8700);
        DEFAULTS.put("cobblemon:togekiss", 14500);
        DEFAULTS.put("cobblemon:togepi", 3900);
        DEFAULTS.put("cobblemon:togetic", 9300);
        DEFAULTS.put("cobblemon:torchic", 5400);
        DEFAULTS.put("cobblemon:torkoal", 9200);
        DEFAULTS.put("cobblemon:tornadus", 22000);
        DEFAULTS.put("cobblemon:torracat", 9900);
        DEFAULTS.put("cobblemon:torterra", 14600);
        DEFAULTS.put("cobblemon:totodile", 5500);
        DEFAULTS.put("cobblemon:toucannon", 12300);
        DEFAULTS.put("cobblemon:toxapex", 9900);
        DEFAULTS.put("cobblemon:toxel", 2800);
        DEFAULTS.put("cobblemon:toxicroak", 9800);
        DEFAULTS.put("cobblemon:toxtricity", 9500);
        DEFAULTS.put("cobblemon:tranquill", 7700);
        DEFAULTS.put("cobblemon:trapinch", 3800);
        DEFAULTS.put("cobblemon:treecko", 5400);
        DEFAULTS.put("cobblemon:trevenant", 9000);
        DEFAULTS.put("cobblemon:tropius", 9400);
        DEFAULTS.put("cobblemon:trubbish", 3700);
        DEFAULTS.put("cobblemon:trumbeak", 7200);
        DEFAULTS.put("cobblemon:tsareena", 12500);
        DEFAULTS.put("cobblemon:turtonator", 10300);
        DEFAULTS.put("cobblemon:turtwig", 5500);
        DEFAULTS.put("cobblemon:tympole", 3200);
        DEFAULTS.put("cobblemon:tynamo", 3000);
        DEFAULTS.put("cobblemon:typenull", 10900);
        DEFAULTS.put("cobblemon:typhlosion", 15000);
        DEFAULTS.put("cobblemon:tyranitar", 16500);
        DEFAULTS.put("cobblemon:tyrantrum", 12600);
        DEFAULTS.put("cobblemon:tyrogue", 3200);
        DEFAULTS.put("cobblemon:tyrunt", 6500);
        DEFAULTS.put("cobblemon:umbreon", 10100);
        DEFAULTS.put("cobblemon:unfezant", 12600);
        DEFAULTS.put("cobblemon:unown", 4800);
        DEFAULTS.put("cobblemon:ursaluna", 14000);
        DEFAULTS.put("cobblemon:ursaring", 9500);
        DEFAULTS.put("cobblemon:urshifu", 21000);
        DEFAULTS.put("cobblemon:uxie", 22000);
        DEFAULTS.put("cobblemon:vanillish", 9000);
        DEFAULTS.put("cobblemon:vanillite", 3400);
        DEFAULTS.put("cobblemon:vanilluxe", 13900);
        DEFAULTS.put("cobblemon:vaporeon", 10100);
        DEFAULTS.put("cobblemon:varoom", 3900);
        DEFAULTS.put("cobblemon:veluza", 9400);
        DEFAULTS.put("cobblemon:venipede", 3000);
        DEFAULTS.put("cobblemon:venomoth", 9200);
        DEFAULTS.put("cobblemon:venonat", 3400);
        DEFAULTS.put("cobblemon:venusaur", 14600);
        DEFAULTS.put("cobblemon:vespiquen", 8700);
        DEFAULTS.put("cobblemon:vibrava", 9800);
        DEFAULTS.put("cobblemon:victini", 22700);
        DEFAULTS.put("cobblemon:victreebel", 12800);
        DEFAULTS.put("cobblemon:vigoroth", 8000);
        DEFAULTS.put("cobblemon:vikavolt", 12900);
        DEFAULTS.put("cobblemon:vileplume", 12800);
        DEFAULTS.put("cobblemon:virizion", 22000);
        DEFAULTS.put("cobblemon:vivillon", 10700);
        DEFAULTS.put("cobblemon:volbeat", 7900);
        DEFAULTS.put("cobblemon:volcanion", 22700);
        DEFAULTS.put("cobblemon:volcarona", 12600);
        DEFAULTS.put("cobblemon:voltorb", 3700);
        DEFAULTS.put("cobblemon:vullaby", 3400);
        DEFAULTS.put("cobblemon:vulpix", 3300);
        DEFAULTS.put("cobblemon:wailmer", 4600);
        DEFAULTS.put("cobblemon:wailord", 10200);
        DEFAULTS.put("cobblemon:walkingwake", 18900);
        DEFAULTS.put("cobblemon:walrein", 13700);
        DEFAULTS.put("cobblemon:wartortle", 9800);
        DEFAULTS.put("cobblemon:watchog", 8100);
        DEFAULTS.put("cobblemon:wattrel", 3000);
        DEFAULTS.put("cobblemon:weavile", 10800);
        DEFAULTS.put("cobblemon:weedle", 3000);
        DEFAULTS.put("cobblemon:weepinbell", 7900);
        DEFAULTS.put("cobblemon:weezing", 9800);
        DEFAULTS.put("cobblemon:whimsicott", 8800);
        DEFAULTS.put("cobblemon:whirlipede", 7800);
        DEFAULTS.put("cobblemon:whiscash", 9200);
        DEFAULTS.put("cobblemon:whismur", 3000);
        DEFAULTS.put("cobblemon:wigglytuff", 11600);
        DEFAULTS.put("cobblemon:wiglett", 3000);
        DEFAULTS.put("cobblemon:wimpod", 3000);
        DEFAULTS.put("cobblemon:wingull", 3000);
        DEFAULTS.put("cobblemon:wishiwashi", 4000);
        DEFAULTS.put("cobblemon:wobbuffet", 9100);
        DEFAULTS.put("cobblemon:wochien", 21700);
        DEFAULTS.put("cobblemon:woobat", 3600);
        DEFAULTS.put("cobblemon:wooloo", 3000);
        DEFAULTS.put("cobblemon:wooper", 3000);
        DEFAULTS.put("cobblemon:wormadam", 8400);
        DEFAULTS.put("cobblemon:wugtrio", 8800);
        DEFAULTS.put("cobblemon:wurmple", 3000);
        DEFAULTS.put("cobblemon:wynaut", 4000);
        DEFAULTS.put("cobblemon:wyrdeer", 10500);
        DEFAULTS.put("cobblemon:xatu", 8900);
        DEFAULTS.put("cobblemon:xerneas", 27500);
        DEFAULTS.put("cobblemon:xurkitree", 18300);
        DEFAULTS.put("cobblemon:yamask", 3300);
        DEFAULTS.put("cobblemon:yamper", 3000);
        DEFAULTS.put("cobblemon:yanma", 5200);
        DEFAULTS.put("cobblemon:yanmega", 10500);
        DEFAULTS.put("cobblemon:yungoos", 3000);
        DEFAULTS.put("cobblemon:yveltal", 27500);
        DEFAULTS.put("cobblemon:zacian", 26800);
        DEFAULTS.put("cobblemon:zamazenta", 26800);
        DEFAULTS.put("cobblemon:zangoose", 9400);
        DEFAULTS.put("cobblemon:zapdos", 22000);
        DEFAULTS.put("cobblemon:zarude", 22700);
        DEFAULTS.put("cobblemon:zebstrika", 9200);
        DEFAULTS.put("cobblemon:zekrom", 27500);
        DEFAULTS.put("cobblemon:zeraora", 22700);
        DEFAULTS.put("cobblemon:zigzagoon", 3000);
        DEFAULTS.put("cobblemon:zoroark", 10200);
        DEFAULTS.put("cobblemon:zorua", 4500);
        DEFAULTS.put("cobblemon:zubat", 3000);
        DEFAULTS.put("cobblemon:zweilous", 11000);
        DEFAULTS.put("cobblemon:zygarde", 24700);
    }

    // Canonical SHA-256 of the original generated 1,025-species table. Only that
    // complete, unmodified legacy file is superseded by newer bundled defaults.
    private static final String LEGACY_DEFAULTS_SHA256 =
            "a66570456c04053a48b7d0921661dc7b2377070e7afc09cc63fffa94f1f48d81";

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartGaugeConfig.class);
    private static final Pattern SPECIES_KEY = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");
    private static final int DEFAULT_GLOBAL_MAX = Math.max(30000,
            DEFAULTS.values().stream().mapToInt(Integer::intValue).max().orElse(DEFAULT_MAX));
    private static final HeartGaugeSettingsCache CACHE = new HeartGaugeSettingsCache(DEFAULTS, configPath(),
            (path, failure) -> LOGGER.warn("Could not reload Heart Gauge legacy settings from {}; keeping the last valid legacy values (or bundled defaults): {}",
                    path, failure.toString()));
    private static volatile boolean initialized;
    private static boolean legacyFallbackAllowed = true;

    private static Path configPath() {
        return Path.of("config", "shadowedhearts", "heart_gauge_max.json");
    }

    private static String speciesKey(Species species) {
        try {
            // Prefer fully-qualified id, e.g., "cobblemon:bulbasaur"
            String full = species.getResourceIdentifier().toString().toLowerCase(Locale.ROOT);
            return full;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String speciesAltKey(Species species) {
        try {
            // Fallback: just the path component (e.g., "bulbasaur")
            String path = species.getResourceIdentifier().getPath().toLowerCase(Locale.ROOT);
            return path;
        } catch (Throwable t) {
            return null;
        }
    }

    public static int getGlobalMax() {
        ensureLoaded();
        return CACHE.snapshot().globalMax();
    }

    public static int getGlobalMax(Pokemon pokemon) {
        return pokemon != null && pokemon.isClient$common()
                ? HeartGaugeClientState.INSTANCE.currentGlobalMax(HeartGaugeConfig::getGlobalMax)
                : getGlobalMax();
    }

    public static int defaultGlobalMax() { return DEFAULT_GLOBAL_MAX; }

    /** Immutable effective overrides for server synchronization; no parsing on the lookup path. */
    public static Map<String, Integer> currentOverrides() {
        ensureLoaded();
        return CACHE.snapshot().overrides();
    }

    public static void ensureLoaded() {
        if (!initialized) {
            synchronized (HeartGaugeConfig.class) {
                if (!initialized) reload();
            }
        }
    }

    /** Called by both loaders before load/reload; remote TOML must not use this client's JSON. */
    public static synchronized void setLegacyFallbackAllowed(boolean allowed) {
        legacyFallbackAllowed = allowed;
    }

    /** Config load/reload (including editor saves) atomically publishes a new validated snapshot. */
    public static synchronized void reload() {
        IShadowConfig config = ShadowedHeartsConfigs.getInstance().getShadowConfig();
        List<? extends String> values = config.isLoaded() ? config.heartGaugeMaxOverrides() : List.of();
        CACHE.reload(values, legacyFallbackAllowed,
                OrreHeartGaugeCapacities.forProfile(OrreProgressionConfig.INSTANCE.gaugeCapacityProfile()));
        initialized = true;
    }

    /** Do not reread a loader's still-attached server config while it is being unloaded. */
    public static synchronized void reset() {
        CACHE.clear();
        legacyFallbackAllowed = false;
        initialized = true;
    }

    static Map<String, Integer> configuredOverrides(List<? extends String> entries) {
        Map<String, Integer> values = new HashMap<>();
        if (entries == null) return values;
        boolean exceededLimit = false;
        for (String entry : entries) {
            if (entry == null) continue;
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) continue;
            String key = parts[0].toLowerCase(Locale.ROOT).trim();
            if (!isValidSpeciesKey(key)) continue;
            try {
                int value = Integer.parseInt(parts[1].trim());
                if (value > 0) {
                    if (values.size() < MAX_OVERRIDE_ENTRIES || values.containsKey(key)) values.put(key, value);
                    else exceededLimit = true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (exceededLimit) LOGGER.warn("Heart Gauge settings exceed {} species overrides; additional entries were ignored", MAX_OVERRIDE_ENTRIES);
        return values;
    }

    public static boolean isValidSpeciesKey(String key) {
        return key != null && key.length() <= 256 && SPECIES_KEY.matcher(key).matches();
    }

    /** Preserve customized legacy tables, without letting the old generated file pin defaults forever. */
    static Map<String, Integer> legacyOverrides(JsonElement root) {
        Map<String, Integer> values = new HashMap<>();
        if (root == null || !root.isJsonObject()) return values;
        JsonObject object = root.getAsJsonObject();
        boolean exceededLimit = false;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            key = key.toLowerCase(Locale.ROOT).trim();
            if (!isValidSpeciesKey(key)) continue;
            try {
                JsonElement element = entry.getValue();
                if (!element.isJsonPrimitive() || element.getAsJsonPrimitive().isBoolean()) continue;
                // Never truncate fractional numbers or wrap values outside the integer range.
                int value = element.getAsJsonPrimitive().isString()
                        ? Integer.parseInt(element.getAsString().trim()) : element.getAsBigDecimal().intValueExact();
                if (value > 0) {
                    if (values.size() < MAX_OVERRIDE_ENTRIES || values.containsKey(key)) values.put(key, value);
                    else exceededLimit = true;
                }
            } catch (NumberFormatException | ArithmeticException ignored) {
            }
        }
        if (exceededLimit) LOGGER.warn("Legacy Heart Gauge settings exceed {} species overrides; additional entries were ignored", MAX_OVERRIDE_ENTRIES);
        // Extra invalid entries or colliding normalized names also make the file
        // customized, even though the existing parser cannot apply those entries.
        return object.size() == values.size() && isPristineLegacyDefaults(values) ? Map.of() : values;
    }

    static boolean isPristineLegacyDefaults(Map<String, Integer> values) {
        // Normalize before sorting so formatting and JSON property order do not
        // affect recognition. Values are included exactly, not compared entry by entry.
        Map<String, Integer> sorted = new TreeMap<>();
        values.forEach((key, value) -> sorted.put(key.toLowerCase(Locale.ROOT).trim(), value));
        StringBuilder canonical = new StringBuilder();
        sorted.forEach((key, value) -> canonical.append(key).append('=').append(value).append('\n'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return LEGACY_DEFAULTS_SHA256.equals(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by the Java runtime", exception);
        }
    }

    /** Pure lookup shared by native Species and textual config keys. */
    static int maximumForKey(String key, Map<String, Integer> configured, Map<String, Integer> defaults) {
        if (key == null) return DEFAULT_MAX;
        String normalized = key.toLowerCase(Locale.ROOT).trim();
        int namespaceSeparator = normalized.indexOf(':');
        String full = namespaceSeparator >= 0 ? normalized : "cobblemon:" + normalized;
        String shortKey = namespaceSeparator >= 0 ? normalized.substring(namespaceSeparator + 1) : normalized;
        Integer value = configured.get(full);
        if (value != null) return value;
        value = configured.get(shortKey);
        if (value != null) return value;
        value = defaults.get(full);
        if (value != null) return value;
        value = defaults.get(shortKey);
        return value != null ? value : DEFAULT_MAX;
    }

    /**
     * Returns the maximum Heart Gauge for a given Pokemon.
     * First checks for a per-pokemon HeartGaugeMaxProperty override,
     * then falls back to species config or DEFAULT_MAX.
     */
    public static int getMax(Pokemon pokemon) {
        if (pokemon == null) return DEFAULT_MAX;
        int propertyMax = ShadowPokemonProperties.getHeartGaugeMaxFromProperty(pokemon);
        if (propertyMax > 0) return propertyMax;
        Species species = pokemon.getSpecies();
        if (species == null) return DEFAULT_MAX;
        if (pokemon.isClient$common()) {
            String full = speciesKey(species);
            return maximumForKey(full != null ? full : speciesAltKey(species),
                    HeartGaugeClientState.INSTANCE.currentOverrides(HeartGaugeConfig::currentOverrides), DEFAULTS);
        }
        return getMax(species);
    }

    /**
     * Returns the configured maximum Heart Gauge for a species.
     */
    public static int getMax(Species species) {
        String full = speciesKey(species);
        return maximumForKey(full != null ? full : speciesAltKey(species), currentOverrides(), DEFAULTS);
    }

    /**
     * Direct lookup by textual key. Keys are matched case-insensitively.
     */
    public static int getMaxByKey(String key) {
        return maximumForKey(key, currentOverrides(), DEFAULTS);
    }
}
