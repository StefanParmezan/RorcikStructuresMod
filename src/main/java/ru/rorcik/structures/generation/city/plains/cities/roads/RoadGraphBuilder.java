package ru.rorcik.structures.generation.city.plains.cities.roads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import ru.rorcik.structures.data.city.plains.containers.RoadNetwork;
import ru.rorcik.structures.data.city.plains.containers.RoadNode;
import ru.rorcik.structures.generation.GeneratorConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RoadGraphBuilder {
    public static RoadNode buildGraph(
            RandomSource random,
            BlockPos startPos,
            Direction direction,
            int depth,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions
    ) {
        // >>> FIX: передаём origin (центр города) в рекурсию, чтобы можно было
        // запретить терминацию внутри города по расстоянию до центра.
        return buildGraphRecursive(random, startPos, direction, direction, depth,
                nodeMap, straightPositions, startPos);
    }

    private static RoadNode buildGraphRecursive(
            RandomSource random,
            BlockPos startPos,
            Direction direction,
            Direction initialDirection,
            int depth,
            Map<BlockPos, RoadNode> nodeMap,
            Set<BlockPos> straightPositions,
            BlockPos origin
    ) {
        if (depth > GeneratorConfig.MAX_GRAPH_DEPTH || nodeMap.size() >= GeneratorConfig.MAX_NODES) {
            RoadNode deadEnd = new RoadNode(startPos, direction, depth);
            deadEnd.markTerminal();
            return deadEnd;
        }

        if (straightPositions.contains(startPos)) {
            RoadNode deadEnd = new RoadNode(startPos, direction, depth);
            deadEnd.markTerminal();
            return deadEnd;
        }

        if (nodeMap.containsKey(startPos)) {
            RoadNode existing = nodeMap.get(startPos);
            existing.addEntry(direction);
            return existing;
        }

        int exitChoice = chooseExitWithDepth(random, depth, startPos, origin);
        RoadNode node = new RoadNode(startPos, direction, depth);
        nodeMap.put(startPos, node);

        if (exitChoice == 0) {
            node.markTerminal();
            return node;
        }

        List<Direction> exits = getExitsForChoice(exitChoice, direction);
        if (exits.isEmpty()) {
            node.markTerminal();
            return node;
        }

        if (depth == 0) {
            Direction opposite = initialDirection.getOpposite();
            if (!exits.contains(opposite)) {
                exits = new ArrayList<>(exits);
                exits.add(opposite);
            }
        }

        for (Direction exit : exits) {
            BlockPos currentPos = startPos;
            for (int i = 0; i < GeneratorConfig.STRAIGHT_BETWEEN_NODES; i++) {
                currentPos = RoadNetwork.getNextPosition(currentPos, exit, GeneratorConfig.ROAD_STEP);
                straightPositions.add(currentPos);
            }

            BlockPos childPos = RoadNetwork.getNextPosition(currentPos, exit, GeneratorConfig.ROAD_STEP);

            RoadNode child = buildGraphRecursive(random, childPos, exit, initialDirection,
                    depth + 1, nodeMap, straightPositions, origin);

            if (child != node) {
                node.addExit(exit, child);
            }
        }

        return node;
    }

    /**
     * Выбор стратегии выхода из узла:
     *  0 = терминал (st-дорога, переход в ландшафт)
     *  1..6 = продолжение (см. getExitsForChoice)
     *
     * Терминал запрещён:
     *  - на глубине 0 и 1 (принудительно продолжаем — иначе стоп-дороги
     *    появляются прямо у центра города)
     *  - если узел ближе чем MIN_TERMINAL_DISTANCE к origin
     *    (защита от преждевременной терминации на длинных диагоналях)
     */
    static int chooseExitWithDepth(RandomSource random, int depth, BlockPos pos, BlockPos origin) {
        // На максимальной глубине — терминал форсированно.
        if (depth >= GeneratorConfig.MAX_GRAPH_DEPTH) {
            return 0;
        }

        // Внутри «ядра» города — всегда продолжаем.
        if (depth < 2) {
            return 1 + random.nextInt(6);
        }

        // По расстоянию до центра — не терминалим близко к центру.
        int dx = pos.getX() - origin.getX();
        int dz = pos.getZ() - origin.getZ();
        long distSq = (long) dx * dx + (long) dz * dz;
        long minDist = GeneratorConfig.MIN_TERMINAL_DISTANCE;
        if (distSq < minDist * minDist) {
            return 1 + random.nextInt(6);
        }

        // На периметре — обычная вероятностная модель.
        double endChance = (double) depth / GeneratorConfig.MAX_GRAPH_DEPTH;
        if (random.nextDouble() < endChance) {
            return 0;
        }

        return 1 + random.nextInt(6);
    }

    static List<Direction> getExitsForChoice(int exitChoice, Direction direction) {
        Direction straight = direction;
        Direction left = direction.getCounterClockWise();
        Direction right = direction.getClockWise();

        return switch (exitChoice) {
            case 1 -> List.of(right);
            case 2 -> List.of(left);
            case 3 -> List.of(right, straight);
            case 4 -> List.of(left, straight);
            case 5 -> List.of(right, straight, left);
            case 6 -> List.of(straight);
            default -> List.of();
        };
    }
}