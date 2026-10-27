package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.core.BlockPos;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CityGenerationContext {
    private final String worldId;
    private final int centerX;
    private final int centerZ;
    private final String typeId;
    private final int targetHeight;
    private final Map<BlockPos, RoadNode> nodeMap;
    private final Set<BlockPos> straightPositions;
    private final List<BuildingSlot> slots;
    private final RoadNetwork roadNetwork;
    private final long seed;
    private final int baseY;
    private final int anchorChunkSize; 

    public CityGenerationContext(String worldId, int centerX, int centerZ, String typeId, int targetHeight,
                                 Map<BlockPos, RoadNode> nodeMap, Set<BlockPos> straightPositions,
                                 List<BuildingSlot> slots, RoadNetwork roadNetwork, long seed, int baseY, int anchorChunkSize) {
        this.worldId = worldId;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.typeId = typeId;
        this.targetHeight = targetHeight;
        this.nodeMap = nodeMap;
        this.straightPositions = straightPositions;
        this.slots = slots;
        this.roadNetwork = roadNetwork;
        this.seed = seed;
        this.baseY = baseY;
        this.anchorChunkSize = anchorChunkSize;
    }

    public String cityId() {
        return worldId + "_city@" + centerX + "," + centerZ;
    }

    public int centerX() { return centerX; }
    public int centerZ() { return centerZ; }
    public String typeId() { return typeId; }
    public int targetHeight() { return targetHeight; }
    public Map<BlockPos, RoadNode> nodeMap() { return nodeMap; }
    public Set<BlockPos> straightPositions() { return straightPositions; }
    public List<BuildingSlot> slots() { return slots; }
    public RoadNetwork roadNetwork() { return roadNetwork; }
    public long seed() { return seed; }
    public int baseY() { return baseY; }
    public String worldId() { return worldId; }
    public int anchorChunkSize() { return anchorChunkSize; } 
}