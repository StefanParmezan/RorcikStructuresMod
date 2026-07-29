package ru.rorcik.structures.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.RorcikStructuresMod;
import ru.rorcik.structures.generator.StructureGenerator;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("testhouse")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> {
                                    String type = StringArgumentType.getString(ctx, "type");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());

                                    LOGGER.info("Generating house type: {}", type);

                                    StructureGenerator.generateHouse(level, pos, type, Rotation.NONE);

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Generating house type " + type + " at " + pos.toShortString()), true);
                                    return 1;
                                })
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> {
                                            String type = StringArgumentType.getString(ctx, "type");
                                            long seed = LongArgumentType.getLong(ctx, "seed");
                                            ServerLevel level = ctx.getSource().getLevel();
                                            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());

                                            LOGGER.info("Generating house type: {} with seed: {}", type, seed);

                                            StructureGenerator.generateHouse(level, pos, type, seed, Rotation.NONE);

                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Generating house type " + type + " with seed " + seed), true);
                                            return 1;
                                        })
                                        .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("0");
                                                    builder.suggest("1");
                                                    builder.suggest("2");
                                                    builder.suggest("3");
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    String type = StringArgumentType.getString(ctx, "type");
                                                    long seed = LongArgumentType.getLong(ctx, "seed");
                                                    int rotValue = IntegerArgumentType.getInteger(ctx, "rotation");
                                                    Rotation rotation = Rotation.values()[rotValue];
                                                    ServerLevel level = ctx.getSource().getLevel();
                                                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());

                                                    LOGGER.info("Generating house type: {} with seed: {} and rotation: {} ({} degrees)",
                                                            type, seed, rotValue, rotValue * 90);

                                                    StructureGenerator.generateHouse(level, pos, type, seed, rotation);

                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("Generating house type " + type + " with seed " + seed + " and rotation " + (rotValue * 90) + " degrees"), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
    }
}