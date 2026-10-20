package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import ru.rorcik.structures.common.RorcikStructuresModConfig;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;

import java.util.*;

public class ChunkGrouper {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static List<AnchorChunkTask> groupIntoAnchorChunks(
            Map<BlockPos, RoadNode> nodeMap, Set<BlockPos> straightPositions,
            List<BuildingSlot> slots, PhaseBuilder phaseBuilder, int cityCenterX, int cityCenterZ) {

        Map<Long, List<SlotWork>> grouped = new LinkedHashMap<>();

        for (BuildingSlot slot : slots) {
            BlockPos pos = slot.position();
            long anchorKey = anchorKeyForBlock(pos.getX(), pos.getZ());
            SlotWork work = phaseBuilder.buildForBuildingSlot(slot);
            grouped.computeIfAbsent(anchorKey, k -> new ArrayList<>()).add(work);
        }

        for (Map.Entry<BlockPos, RoadNode> entry : nodeMap.entrySet()) {
            BlockPos pos = entry.getKey();
            RoadNode node = entry.getValue();
            long anchorKey = anchorKeyForBlock(pos.getX(), pos.getZ());
            SlotWork work = phaseBuilder.buildForRoadNode(node, pos);
            grouped.computeIfAbsent(anchorKey, k -> new ArrayList<>()).add(work);
        }

        for (BlockPos pos : straightPositions) {
            long anchorKey = anchorKeyForBlock(pos.getX(), pos.getZ());
            SlotWork work = phaseBuilder.buildForStraightRoad(pos);
            grouped.computeIfAbsent(anchorKey, k -> new ArrayList<>()).add(work);
        }

        List<AnchorChunkTask> tasks = new ArrayList<>();
        for (Map.Entry<Long, List<SlotWork>> entry : grouped.entrySet()) {
            long key = entry.getKey();
            int anchorChunkX = (int) (key >> 32);
            int anchorChunkZ = (int) (key & 0xFFFFFFFFL);
            tasks.add(new AnchorChunkTask(anchorChunkX, anchorChunkZ, entry.getValue(), cityCenterX, cityCenterZ));
        }

        if (RorcikStructuresModConfig.enableLogging) {
            LOGGER.info("Info: [CHUNK-GROUPER] Grouped {} slots into {} anchor chunks (4x4) for city [{}, {}]",
                    nodeMap.size() + straightPositions.size() + slots.size(), tasks.size(), cityCenterX, cityCenterZ);
        }
        return tasks;
    }

    private static long anchorKeyForBlock(int blockX, int blockZ) {
        int anchorChunkX = (blockX >> 6) << 2;
        int anchorChunkZ = (blockZ >> 6) << 2;
        return ((long) anchorChunkX << 32) | (anchorChunkZ & 0xFFFFFFFFL);
    }
}