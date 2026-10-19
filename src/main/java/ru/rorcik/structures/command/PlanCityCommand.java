package ru.rorcik.structures.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.rorcik.structures.city.CityType;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.planning.CityZonePlanner;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlanCityCommand {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("planycity")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("plains");
                                    builder.suggest("desert");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String typeName = StringArgumentType.getString(ctx, "type");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                                    CityType type;
                                    try {
                                        type = CityType.valueOf(typeName.toUpperCase());
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown city type: " + typeName));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("Attempting to reserve " + type.id + " city at " + pos.toShortString()), true);
                                    boolean success = CityZonePlanner.tryReserveCity(level, pos.getX(), pos.getZ(), type);
                                    if (success) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("Success! Zone reserved via RorcikCore."), true);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("Failed to reserve zone. Check logs."));
                                    }
                                    return success ? 1 : 0;
                                })
                        )
        );
    }
}