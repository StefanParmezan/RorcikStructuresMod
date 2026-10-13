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
                                        ctx.getSource().sendFailure(Component.literal("Неизвестный тип города: " + typeName));
                                        return 0;
                                    }

                                    ctx.getSource().sendSuccess(() -> Component.literal("Попытка зарезервировать " + type.id + " город на " + pos.toShortString()), true);

                                    boolean success = CityZonePlanner.tryReserveCity(level, pos.getX(), pos.getZ(), type);

                                    if (success) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("✅ Успешно! Зона зарезервирована через RorcikCore."), true);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("❌ Не удалось зарезервировать зону. Проверьте логи."));
                                    }

                                    return success ? 1 : 0;
                                })
                        )
        );
    }
}