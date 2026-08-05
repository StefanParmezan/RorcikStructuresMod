package ru.rorcik.structures.data.city.plains.containers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RoadNetwork {
    private final List<RoadSegment> segments = new ArrayList<>();
    private final Map<BlockPos, RoadSegment> segmentMap = new HashMap<>();

    public boolean addSegment(RoadSegment segment) {
        if (segmentMap.containsKey(segment.position())) {
            return false;
        }
        segments.add(segment);
        segmentMap.put(segment.position(), segment);
        return true;
    }

    public boolean hasSegmentAt(BlockPos position) {
        return segmentMap.containsKey(position);
    }

    public RoadSegment getSegmentAt(BlockPos position) {
        return segmentMap.get(position);
    }

    public List<RoadSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public int size() {
        return segments.size();
    }

    public static List<Direction> getRotatedConnections(RoadConfig config, Rotation rotation) {
        return config.connections().stream()
                .map(Direction::valueOf)
                .map(dir -> rotateDirection(dir, rotation))
                .collect(Collectors.toList());
    }

    public static Direction rotateDirection(Direction direction, Rotation rotation) {
        return switch (rotation) {
            case NONE -> direction;
            case CLOCKWISE_90 -> direction.getClockWise();
            case CLOCKWISE_180 -> direction.getOpposite();
            case COUNTERCLOCKWISE_90 -> direction.getCounterClockWise();
        };
    }

    public static BlockPos getNextPosition(BlockPos current, Direction direction, int step) {
        return current.offset(
                direction.getStepX() * step,
                direction.getStepY() * step,
                direction.getStepZ() * step
        );
    }
}