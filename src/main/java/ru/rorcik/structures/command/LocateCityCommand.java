package ru.rorcik.structures.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.rorcik.core.zone.ZoneRegistrySavedData;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.planning.CityCandidate;
import ru.rorcik.structures.planning.CityZoneProviderListener;
import ru.rorcik.structures.planning.RandomCityZoneProvider;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LocateCityCommand {
    private static final int DEFAULT_RADIUS = 10000;
    private static final int MAX_RADIUS = 30000;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("locatecity")
                        .executes(ctx -> locateCity(ctx, DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1000, MAX_RADIUS))
                                .executes(ctx -> locateCity(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))
        );
    }

    private static int locateCity(CommandContext<CommandSourceStack> ctx, int radius) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos origin = BlockPos.containing(ctx.getSource().getPosition());
        ZoneRegistrySavedData registry = ZoneRegistrySavedData.get(level);

        NearestCity nearest = null;

        RandomCityZoneProvider provider = CityZoneProviderListener.currentProvider();
        if (provider != null) {
            for (CityCandidate candidate : provider.candidatesNear(origin.getX(), origin.getZ(), radius)) {
                long distance = distanceSquared(origin, candidate.centerX(), candidate.centerZ());
                if (nearest == null || distance < nearest.distanceSquared()) {
                    nearest = new NearestCity(
                            candidate.centerX(), candidate.centerZ(), candidate.type().id,
                            registry.isReserved(candidate.centerX(), candidate.centerZ()), distance
                    );
                }
            }
        }

        for (ZoneRegistrySavedData.ReservedZone zone : registry.getAllReservedZones()) {
            long distance = distanceSquared(origin, zone.x(), zone.z());
            if (nearest == null || distance < nearest.distanceSquared()) {
                nearest = new NearestCity(zone.x(), zone.z(), zone.rules().id(), true, distance);
            }
        }

        if (nearest == null) {
            ctx.getSource().sendFailure(Component.literal("No city found within " + radius + " blocks"));
            return 0;
        }

        NearestCity result = nearest;
        long distance = Math.round(Math.sqrt(result.distanceSquared()));
        String status = result.reserved() ? "reserved" : "candidate";

        Component teleport = Component.literal("[TP]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND,
                        "/tp " + result.x() + " ~ " + result.z()
                ))
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Teleport to " + result.x() + ", " + result.z())
                ))
        );

        ctx.getSource().sendSuccess(() -> Component.literal(
                        "Nearest " + result.type() + " city (" + status + ") at "
                                + result.x() + ", " + result.z()
                                + ", distance " + distance + " blocks "
                ).append(teleport),
                false
        );
        return 1;
    }

    private static long distanceSquared(BlockPos origin, int x, int z) {
        long dx = (long) x - origin.getX();
        long dz = (long) z - origin.getZ();
        return dx * dx + dz * dz;
    }

    private record NearestCity(int x, int z, String type, boolean reserved, long distanceSquared) {
    }
}
