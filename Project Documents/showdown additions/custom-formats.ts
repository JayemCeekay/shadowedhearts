export const Formats: FormatList = [

	{
		section: "Cobblemon",
	},
	{
		name: "Cobblemon Singles",
		threads: [],

		mod: 'cobblemon',
		ruleset: [],
		gameType: "singles"
	},
	{
		name: "Cobblemon Doubles",
		threads: [],

		mod: 'cobblemon',
		ruleset: [],
		gameType: "doubles"
	},
	{
		name: "Cobblemon Triples",
		threads: [],

		mod: 'cobblemon',
		ruleset: [],
		gameType: "triples"
	},
	{
		name: "Cobblemon Rotation",
		threads: [],

		mod: 'cobblemon',
		ruleset: [],
		gameType: "rotation"
	},
	{
		name: "Cobblemon Multi",
		threads: [],

		mod: 'cobblemon',
		ruleset: [],
		gameType: "multi"
	},
	{
		name: "Cobblemon Free for All",
		threads: [],

		mod: 'cobblemon',
		ruleset: [],
		gameType: "freeforall"
	},
    {
        section: "Shadowed Hearts",
    },
    {
        name: "[Gen 9] Micro",
        desc: "Internal one-action micro battle for overworld interactions; not for human play.",
        mod: 'micro',
        gameType: 'singles',
        searchShow: false,
        challengeShow: false,
        tournamentShow: false,
        rated: false,
        ruleset: [
            'Obtainable', 'Species Clause', 'HP Percentage Mod', 'Cancel Mod', 'Illusion Level Mod', 'Endless Battle Clause',
            'Picked Team Size = 1', 'Max Team Size = 1', 'Min Team Size = 1',
        ],
        banlist: [
            // Hazards and delayed-turn moves
            'Stealth Rock', 'Spikes', 'Toxic Spikes', 'Sticky Web',
            'Future Sight', 'Doom Desire', 'Perish Song',
            // Pivots and passers (avoid switch flow)
            'Baton Pass', 'Parting Shot',
            // Two-turn/charge/invuln moves
            'Sky Drop', 'Dive', 'Dig', 'Bounce', 'Fly', 'Phantom Force', 'Shadow Force', 'Solar Beam', 'Solar Blade', 'Skull Bash', 'Freeze Shock', 'Ice Burn', 'Razor Wind', 'Geomancy',
        ],
        onBegin() {
            // State should be injected by the micro-battle runner; nothing to do here.
        },
        onResidual() {
            // Prevent format-level residual effects; core statuses/weather may still apply if present.
        },
    },
];
