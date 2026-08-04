package ru.rorcik.structures.data.city.plains;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

public class RoadNode {
    private final BlockPos position;
    private final Direction incoming;
    private final int exitChoice;
    private final int depth;
    private final List<RoadNode> children = new ArrayList<>();

    public RoadNode(BlockPos position, Direction incoming, int exitChoice, int depth) {
        this.position = position;
        this.incoming = incoming;
        this.exitChoice = exitChoice;
        this.depth = depth;
    }

    public BlockPos position() { return position; }
    public Direction incoming() { return incoming; }
    public int exitChoice() { return exitChoice; }
    public int depth() { return depth; }
    public List<RoadNode> children() { return children; }

    public void addChild(RoadNode child) {
        children.add(child);
    }
}