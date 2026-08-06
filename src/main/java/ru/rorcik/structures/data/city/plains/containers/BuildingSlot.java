package ru.rorcik.structures.data.city.plains.containers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

public record BuildingSlot(
        BlockPos position,
        Rotation rotation,
        Direction roadSide,
        String category,
        String buildingName,
        int maxDepth
) {}