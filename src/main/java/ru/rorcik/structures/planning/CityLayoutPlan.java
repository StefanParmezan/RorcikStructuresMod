package ru.rorcik.structures.planning;

import net.minecraft.core.BlockPos;
import ru.rorcik.core.city.CityZone;
import ru.rorcik.structures.city.CityType;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CityLayoutPlan(
        long seed,
        int centerX,
        int centerZ,
        CityType type,
        int baseY,
        int anchorChunkSize,
        Map<BlockPos, RoadNode> nodeMap,
        Set<BlockPos> straightPositions,
        List<BuildingSlot> slots,
        Set<Long> chunks,
        Set<Long> zoneChunks,
        CityZone zone
) {

    public CityLayoutPlan {
        nodeMap = Map.copyOf(nodeMap);
        straightPositions = Set.copyOf(straightPositions);
        slots = List.copyOf(slots);
        chunks = Set.copyOf(chunks);
        zoneChunks = Set.copyOf(zoneChunks);
    }
}
