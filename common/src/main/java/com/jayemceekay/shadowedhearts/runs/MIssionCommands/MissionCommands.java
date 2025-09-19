package com.jayemceekay.shadowedhearts.runs.MIssionCommands;

import com.jayemceekay.shadowedhearts.core.ModItems;
import com.jayemceekay.shadowedhearts.generator.DungeonGenerator;
import com.jayemceekay.shadowedhearts.runs.RunId;
import com.jayemceekay.shadowedhearts.signals.MissionSignalItem;
import com.jayemceekay.shadowedhearts.world.RunBounds;
import com.jayemceekay.shadowedhearts.world.WorldspaceManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MissionCommands {
    private MissionCommands() {}

    public static int tpToMissions(CommandContext<CommandSourceStack> ctx, long runId) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getServer().getLevel(WorldspaceManager.MISSIONS_LEVEL_KEY);
        if (level == null) {
            src.sendFailure(Component.literal("Missions dimension not loaded or missing datapack: shadowedhearts:missions"));
            return 0;
        }
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }
        RunBounds bounds = WorldspaceManager.allocateBounds(new RunId(runId));
        BlockPos origin = bounds.origin();
        // Safety pad 3x3 at y=64
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(origin.offset(dx, 0, dz), Blocks.BEDROCK.defaultBlockState(), 3);
            }
        }
        player.teleportTo(level,
                origin.getX() + 0.5,
                origin.getY() + 1,
                origin.getZ() + 0.5,
                player.getYRot(),
                player.getXRot());
        src.sendSuccess(() -> Component.literal("Teleported to Missions runId=" + runId + " at " + origin.toShortString()), true);
        return 1;
    }

    public static int genPlaceStructure(CommandContext<CommandSourceStack> ctx, long runId, ResourceLocation structureId, Rotation rotation) {
        CommandSourceStack src = ctx.getSource();
        if (WorldspaceManager.missionsLevel() == null) {
            src.sendFailure(Component.literal("Missions dimension not loaded."));
            return 0;
        }
        boolean ok = DungeonGenerator.placeStructureAtRunOrigin(new RunId(runId), structureId, rotation);
        if (ok) {
            src.sendSuccess(() -> Component.literal("Placed template " + structureId + " at run " + runId + " with rotation " + rotation), true);
            return 1;
        } else {
            src.sendFailure(Component.literal("Failed to place template. Ensure structure NBT exists and id is correct."));
            return 0;
        }
    }

    public static int genDemo(CommandContext<CommandSourceStack> ctx, long runId) {
        CommandSourceStack src = ctx.getSource();
        if (WorldspaceManager.missionsLevel() == null) {
            src.sendFailure(Component.literal("Missions dimension not loaded."));
            return 0;
        }
        boolean ok = DungeonGenerator.demoBuildAtRunOrigin(new RunId(runId));
        if (ok) {
            src.sendSuccess(() -> Component.literal("Built demo room at run " + runId), true);
            return 1;
        } else {
            src.sendFailure(Component.literal("Failed to build demo room."));
            return 0;
        }
    }

    // --- Signals & Fragments helpers ---
    public static int giveFragment(CommandContext<CommandSourceStack> ctx, String type, String value) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try { player = src.getPlayerOrException(); } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }
        String idPath = "fragment_" + type.toLowerCase(Locale.ROOT) + "_" + value.toLowerCase(Locale.ROOT);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("shadowedhearts", idPath);
        Registry<Item> reg = src.getServer().registryAccess().registryOrThrow(Registries.ITEM);
        Item item = reg.getOptional(id).orElse(null);
        if (item == null) {
            src.sendFailure(Component.literal("Unknown fragment id: " + id));
            return 0;
        }
        ItemStack stack = new ItemStack(item);
        boolean added = player.getInventory().add(stack);
        if (!added) player.drop(stack, false);
        src.sendSuccess(() -> Component.literal("Gave fragment: " + id), true);
        return 1;
    }

    public static int giveSignal(CommandContext<CommandSourceStack> ctx, String themeStr, int tier, @org.jetbrains.annotations.Nullable String affixesCsv) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try { player = src.getPlayerOrException(); } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }
        ResourceLocation theme = parseResourceOrNamespace(themeStr);
        List<ResourceLocation> affixes = new ArrayList<>();
        if (affixesCsv != null && !affixesCsv.isEmpty()) {
            String[] parts = affixesCsv.split(",");
            for (String p : parts) {
                affixes.add(parseResourceOrNamespace(p.trim()));
            }
        }
        long seed = fnv1aSeed(theme, tier, affixes, player.getUUID().getLeastSignificantBits());
        ItemStack signal = MissionSignalItem.create(ModItems.MISSION_SIGNAL.get(), theme, tier, affixes, seed);
        boolean added = player.getInventory().add(signal);
        if (!added) player.drop(signal, false);
        src.sendSuccess(() -> Component.literal("Gave Mission Signal (theme=" + theme + ", tier=" + tier + ")"), true);
        return 1;
    }

    private static ResourceLocation parseResourceOrNamespace(String s) {
        if (s.indexOf(':') >= 0) {
            return ResourceLocation.parse(s);
        }
        return ResourceLocation.fromNamespaceAndPath("shadowedhearts", s);
    }

    private static long fnv1aSeed(ResourceLocation theme, int tier, List<ResourceLocation> affixes, long salt) {
        long hash = 0xcbf29ce484222325L; // FNV offset basis 64
        hash ^= theme.toString().hashCode(); hash *= 0x100000001b3L;
        hash ^= tier; hash *= 0x100000001b3L;
        for (ResourceLocation rl : affixes) { hash ^= rl.toString().hashCode(); hash *= 0x100000001b3L; }
        hash ^= salt; hash *= 0x100000001b3L;
        return hash;
    }

    // Start a minimal run for the executing player; optional demo build flag handled by command wiring
    public static int startRun(CommandContext<CommandSourceStack> ctx, boolean buildDemo) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getServer().getLevel(WorldspaceManager.MISSIONS_LEVEL_KEY);
        if (level == null) {
            src.sendFailure(Component.literal("Missions dimension not loaded or missing datapack: shadowedhearts:missions"));
            return 0;
        }
        ServerPlayer player;
        try { player = src.getPlayerOrException(); } catch (Exception e) {
            src.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }
        // Allocate a new run id by using player's UUID least bits as a temporary unique-ish id if no registry; reuse tp logic.
        long runId = Math.floorMod(player.getUUID().getLeastSignificantBits(), Long.MAX_VALUE - 1) + 1; // avoid 0
        RunBounds bounds = WorldspaceManager.allocateBounds(new RunId(runId));
        BlockPos origin = bounds.origin();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(origin.offset(dx, 0, dz), Blocks.BEDROCK.defaultBlockState(), 3);
            }
        }
        if (buildDemo) {
            DungeonGenerator.demoBuildAtRunOrigin(new RunId(runId));
        }
        ServerPlayer playerRef = player;
        player.teleportTo(level,
                origin.getX() + 0.5,
                origin.getY() + 1,
                origin.getZ() + 0.5,
                player.getYRot(),
                player.getXRot());
        src.sendSuccess(() -> Component.literal("Started run (temp id=" + runId + ") and teleported to " + origin.toShortString() + (buildDemo? " [demo built]":"")), true);
        return 1;
    }

    public static int seedStub(CommandContext<CommandSourceStack> ctx, long runId) {
        ctx.getSource().sendSuccess(() -> Component.literal("/shadowmission seed " + runId + ": not implemented yet (RunRegistry cfg pending)"), false);
        return 1;
    }

    public static int abortStub(CommandContext<CommandSourceStack> ctx, long runId) {
        ctx.getSource().sendSuccess(() -> Component.literal("/shadowmission abort " + runId + ": not implemented yet (cleanup pending)"), false);
        return 1;
    }

    public static Rotation parseRotation(String rot) {
        try {
            return Rotation.valueOf(rot.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Rotation.NONE;
        }
    }
}
