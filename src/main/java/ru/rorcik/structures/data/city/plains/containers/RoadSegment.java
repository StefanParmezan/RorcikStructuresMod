package ru.rorcik.structures.data.city.plains.containers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

public record RoadSegment(
        String type,
        BlockPos position,
        Rotation rotation,
        int decorVariant,
        List<Direction> connections
) {}