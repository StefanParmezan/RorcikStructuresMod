package ru.rorcik.structures.data.city.plains;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

public class RoadNode {
    private final BlockPos position;
    private final List<Direction> entries = new ArrayList<>();
    private final int depth;
    private final List<Direction> exitDirections = new ArrayList<>();
    private final List<RoadNode> children = new ArrayList<>();
    private boolean isTerminal;

    public RoadNode(BlockPos position, Direction primaryEntry, int depth) {
        this.position = position;
        this.depth = depth;
        if (primaryEntry != null) {
            this.entries.add(primaryEntry.getOpposite());
        }
    }

    public void addEntry(Direction entryDir) {
        Direction entry = entryDir.getOpposite();
        if (!entries.contains(entry)) {
            entries.add(entry);
        }
    }

    public void addExit(Direction exitDir, RoadNode child) {
        if (!exitDirections.contains(exitDir)) {
            exitDirections.add(exitDir);
        }
        children.add(child);
    }

    public void markTerminal() {
        isTerminal = true;
    }

    public List<Direction> getAllConnections() {
        List<Direction> all = new ArrayList<>(entries);
        for (Direction exit : exitDirections) {
            if (!all.contains(exit)) {
                all.add(exit);
            }
        }
        return all;
    }

    public BlockPos position() {
        return position;
    }

    public int depth() {
        return depth;
    }

    public List<RoadNode> children() {
        return children;
    }

    public boolean isTerminal() {
        return isTerminal;
    }
}