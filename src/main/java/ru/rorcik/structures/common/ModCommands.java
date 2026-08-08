package ru.rorcik.structures.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.generation.city.plains.cities.CityGenerator;
import ru.rorcik.structures.generation.city.plains.cities.buildings.ParkTestGenerator;
import ru.rorcik.structures.generation.city.plains.houses.HouseGenerator;
import ru.rorcik.structures.generation.city.plains.cities.roads.RoadPlacer;

@Mod.EventBusSubscriber(modid = RorcikStructuresMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        registerHouseCommands(dispatcher);
        registerRoadCommands(dispatcher);
        registerCityCommands(dispatcher);
        registerParkCommands(dispatcher);
    }

    private static void registerHouseCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("testhouse")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> {
                                    String type = StringArgumentType.getString(ctx, "type");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());

                                    LOGGER.info("Generating house type: {} with random decor", type);

                                    HouseGenerator.generateHouse(level, pos, type, Rotation.NONE);

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

                                            HouseGenerator.generateHouse(level, pos, type, rotation);

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

                                                    HouseGenerator.generateHouse(level, pos, type, rotation, decor);

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

                                    HouseGenerator.generateRoomsOnly(level, pos, type, Rotation.NONE);

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

                                            HouseGenerator.generateRoomsOnly(level, pos, type, rotation);

                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Generating rooms only for house type " + type + " with rotation " + (rotValue * 90) + " degrees"), true);
                                            return 1;
                                        })
                                )
                        )
        );
    }

    private static void registerRoadCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("testroad")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> {
                                    String type = StringArgumentType.getString(ctx, "type");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                                    Rotation rotation = Rotation.NONE;

                                    LOGGER.info("Generating road type: {} with default rotation (NONE)", type);

                                    RoadPlacer.generateSingleRoad(level, pos, type, rotation, -1);

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Generating road type " + type + " at " + pos.toShortString()), true);
                                    return 1;
                                })
                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> {
                                            String type = StringArgumentType.getString(ctx, "type");
                                            int rotValue = IntegerArgumentType.getInteger(ctx, "rotation");
                                            Rotation rotation = Rotation.values()[rotValue];
                                            ServerLevel level = ctx.getSource().getLevel();
                                            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());

                                            LOGGER.info("Generating road type: {} with rotation: {} degrees", type, rotValue * 90);

                                            RoadPlacer.generateSingleRoad(level, pos, type, rotation, -1);

                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Generating road type " + type + " with rotation " + (rotValue * 90) + " degrees"), true);
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

                                                    LOGGER.info("Generating road type: {} with rotation: {} degrees and forced decor: {}",
                                                            type, rotValue * 90, decor);

                                                    RoadPlacer.generateSingleRoad(level, pos, type, rotation, decor);

                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("Generating road type " + type + " with rotation " + (rotValue * 90) + " degrees and decor " + decor), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );
    }

    private static void registerCityCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("testcity")
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                            long seed = level.getSeed();

                            LOGGER.info("Generating city at {}", pos.toShortString());

                            CityGenerator.generateCity(level, pos, seed);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("Generating city at " + pos.toShortString()), true);
                            return 1;
                        })
        );
    }

    private static void registerParkCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("testpark")
                        .then(Commands.argument("direction", IntegerArgumentType.integer(0, 3))
                                .then(Commands.argument("parkIndex", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            int dirValue = IntegerArgumentType.getInteger(ctx, "direction");
                                            int parkIndex = IntegerArgumentType.getInteger(ctx, "parkIndex");
                                            Direction roadDirection = Direction.from2DDataValue(dirValue);
                                            ServerLevel level = ctx.getSource().getLevel();
                                            BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
                                            long seed = level.getSeed();

                                            LOGGER.info("Testing park at {} (road direction: {}, park index: {})",
                                                    pos.toShortString(), roadDirection, parkIndex);

                                            ParkTestGenerator.generateTestPark(level, pos, seed, roadDirection, parkIndex);

                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Testing park (direction: " + roadDirection + ", park: " + parkIndex + ")"), true);
                                            return 1;
                                        })
                                )
                        )
        );
    }

    private static Rotation directionToRotation(Direction direction) {
        return switch (direction) {
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}