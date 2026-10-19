package ru.rorcik.structures.generation.city.plains.cities.scheduler;

import net.minecraft.core.BlockPos;
import ru.rorcik.structures.data.city.plains.containers.BuildingSlot;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CityGenerationContext {
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

    public CityGenerationContext(int centerX, int centerZ, String typeId, int targetHeight,
                                 Map<BlockPos, RoadNode> nodeMap, Set<BlockPos> straightPositions,
                                 List<BuildingSlot> slots, RoadNetwork roadNetwork, long seed, int baseY) {
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
    }

    public int[] getBoundingBox() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : nodeMap.keySet()) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        for (BlockPos pos : straightPositions) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        for (BuildingSlot slot : slots) {
            minX = Math.min(minX, slot.position().getX());
            maxX = Math.max(maxX, slot.position().getX());
            minZ = Math.min(minZ, slot.position().getZ());
            maxZ = Math.max(maxZ, slot.position().getZ());
        }

        return new int[]{minX, maxX, minZ, maxZ};
    }

    public int baseY() { return baseY; }
    public int centerX() { return centerX; }
    public int centerZ() { return centerZ; }
    public String typeId() { return typeId; }
    public int targetHeight() { return targetHeight; }
    public Map<BlockPos, RoadNode> nodeMap() { return nodeMap; }
    public Set<BlockPos> straightPositions() { return straightPositions; }
    public List<BuildingSlot> slots() { return slots; }
    public RoadNetwork roadNetwork() { return roadNetwork; }
    public long seed() { return seed; }
    public String cityId() { return "city@" + centerX + "," + centerZ; }
}