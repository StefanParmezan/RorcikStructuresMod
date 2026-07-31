package ru.rorcik.structures.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

                                    LOGGER.info("Generating house type: {} with random decor", type);

                                    StructureGenerator.generateHouse(level, pos, type, Rotation.NONE);

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Generating house type " + type + " at " + pos.toShortString()), true);
                                    return 1;
                                })
                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> {
                                            String type = StringArgumentType.getString(ctx, "type");
                                            int rotValue = IntegerArgumentType.getInteger(ctx, "rotation");
                                            Rotation rotation = Rotation.values()[rotValue];
                                            ServerLevel level = ctx.getSource().getLevel();
                                            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());

                                            LOGGER.info("Generating house type: {} with rotation: {} degrees and random decor",
                                                    type, rotValue * 90);

                                            StructureGenerator.generateHouse(level, pos, type, rotation);

                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Generating house type " + type + " with rotation " + (rotValue * 90) + " degrees"), true);
                                            return 1;
                                        })
                                        .then(Commands.argument("decor", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    String type = StringArgumentType.getString(ctx, "type");
                                                    int rotValue = IntegerArgumentType.getInteger(ctx, "rotation");
                                                    int decor = IntegerArgumentType.getInteger(ctx, "decor");
                                                    Rotation rotation = Rotation.values()[rotValue];
                                                    ServerLevel level = ctx.getSource().getLevel();
                                                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());

                                                    LOGGER.info("Generating house type: {} with rotation: {} degrees and forced decor: {}",
                                                            type, rotValue * 90, decor);

                                                    StructureGenerator.generateHouse(level, pos, type, rotation, decor);

                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("Generating house type " + type + " with rotation " + (rotValue * 90) + " degrees and decor " + decor), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );

        dispatcher.register(
                Commands.literal("testrooms")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> {
                                    String type = StringArgumentType.getString(ctx, "type");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());

                                    LOGGER.info("Generating rooms only for house type: {}", type);

                                    StructureGenerator.generateRoomsOnly(level, pos, type, Rotation.NONE);

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Generating rooms only for house type " + type + " at " + pos.toShortString()), true);
                                    return 1;
                                })
                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> {
                                            String type = StringArgumentType.getString(ctx, "type");
                                            int rotValue = IntegerArgumentType.getInteger(ctx, "rotation");
                                            Rotation rotation = Rotation.values()[rotValue];
                                            ServerLevel level = ctx.getSource().getLevel();
                                            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());

                                            LOGGER.info("Generating rooms only for house type: {} with rotation: {} degrees",
                                                    type, rotValue * 90);

                                            StructureGenerator.generateRoomsOnly(level, pos, type, rotation);

                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Generating rooms only for house type " + type + " with rotation " + (rotValue * 90) + " degrees"), true);
                                            return 1;
                                        })
                                )
                        )
        );
    }
}