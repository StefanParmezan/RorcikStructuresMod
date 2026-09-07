package ru.rorcik.structures.generation.city.plains.cities.roads;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.ModPaths;
import ru.rorcik.structures.common.RorcikStructuresMod;
import ru.rorcik.structures.data.StructureCache;
import ru.rorcik.structures.data.city.plains.config_loaders.RoadConfigLoader;
import ru.rorcik.structures.data.city.plains.containers.RoadConfig;
import ru.rorcik.structures.data.city.plains.containers.RoadDecoration;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadSegment;
import ru.rorcik.structures.generation.StructureUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoadDecorationsPlacer {
    private static final Logger LOGGER = RorcikStructuresMod.LOGGER;
    public static final String[] DECOR_NAMES = {"a", "b", "c", "d", "e", "f", "g"};

    public static void collectDecorationActions(
            ServerLevel level,
            RoadNetwork network,
            RandomSource random,
            List<Runnable> actions
    ) {
        int totalDecorations = 0;

        for (RoadSegment segment : network.getSegments()) {
            RoadConfig config = RoadConfigLoader.getConfig(segment.type());
            if (config == null) continue;

            List<RoadDecoration> decorations = config.decorations();
            if (decorations == null || decorations.isEmpty()) continue;

            for (RoadDecoration decor : decorations) {
                totalDecorations++;
                actions.add(() -> placeSingleDecoration(level, segment, config, decor, random));
            }
        }

        LOGGER.info("[ROAD-DECOR] Collected {} decoration actions from {} road segments",
                totalDecorations, network.size());
    }

    private static void placeSingleDecoration(
            ServerLevel level,
            RoadSegment segment,
            RoadConfig roadConfig,
            RoadDecoration decor,
            RandomSource random
    ) {
        String decorName = DECOR_NAMES[random.nextInt(DECOR_NAMES.length)];
        String nbtPath = ModPaths.roadDecorNbtPath(decorName);

        ResourceLocation location = ResourceLocation.tryParse(nbtPath);
        if (location == null) {
            LOGGER.warn("[ROAD-DECOR] Invalid decor path: {}", nbtPath);
            return;
        }

        if (StructureCache.get(location).isEmpty()) {
            LOGGER.debug("[ROAD-DECOR] Decor '{}' not found in cache, skipping", nbtPath);
            return;
        }

        int sizeX = roadConfig.size()[0];
        int sizeZ = roadConfig.size()[2];

        int[] rotatedOffset = StructureUtils.rotatePositionInStructure(
                decor.offset()[0], decor.offset()[1], decor.offset()[2],
                segment.rotation(), sizeX, sizeZ
        );

        BlockPos decorPos = segment.position().offset(
                rotatedOffset[0], rotatedOffset[1], rotatedOffset[2]
        );

        Rotation decorRotation = combineRotations(segment.rotation(), decor.rotation());

        StructureUtils.placeStructure(level, decorPos, nbtPath, decorRotation, false, false);

        LOGGER.debug("[ROAD-DECOR] Placed '{}' at {} (road type: {}, road rotation: {}, decor rotation: {})",
                decorName, decorPos.toShortString(), segment.type(), segment.rotation(), decorRotation);
    }

    private static Rotation combineRotations(Rotation roadRotation, int decorRotationDegrees) {
        if (decorRotationDegrees == 0) return roadRotation;

        int roadDegrees = roadRotation.ordinal() * 90;
        int totalDegrees = (roadDegrees + decorRotationDegrees) % 360;
        return Rotation.values()[totalDegrees / 90];
    }

    public static List<ResourceLocation> collectRequiredDecorTemplates(RoadNetwork network) {
        Set<ResourceLocation> templates = new HashSet<>();
        Set<String> usedTypes = new HashSet<>();

        for (RoadSegment segment : network.getSegments()) {
            RoadConfig config = RoadConfigLoader.getConfig(segment.type());
            if (config == null) continue;

            List<RoadDecoration> decorations = config.decorations();
            if (decorations == null || decorations.isEmpty()) continue;

            usedTypes.add(segment.type());
        }

        if (!usedTypes.isEmpty()) {
            for (String decorName : DECOR_NAMES) {
                ResourceLocation loc = ResourceLocation.tryParse(ModPaths.roadDecorNbtPath(decorName));
                if (loc != null) {
                    templates.add(loc);
                }
            }
        }

        return new ArrayList<>(templates);
    }

    public static void placeDecorationsForSingleRoad(
            ServerLevel level,
            RoadSegment segment,
            RandomSource random
    ) {
        RoadConfig config = RoadConfigLoader.getConfig(segment.type());
        if (config == null) {
            LOGGER.warn("[ROAD-DECOR] No config for road type '{}'", segment.type());
            return;
        }

        List<RoadDecoration> decorations = config.decorations();
        if (decorations == null || decorations.isEmpty()) {
            LOGGER.info("[ROAD-DECOR] No decorations configured for road type '{}'", segment.type());
            return;
        }

        LOGGER.info("[ROAD-DECOR] Placing {} decorations for road '{}' at {} (rotation: {})",
                decorations.size(), segment.type(), segment.position().toShortString(), segment.rotation());

        for (RoadDecoration decor : decorations) {
            placeSingleDecoration(level, segment, config, decor, random);
        }
    }
}