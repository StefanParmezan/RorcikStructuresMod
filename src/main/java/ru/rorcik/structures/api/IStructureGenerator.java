package ru.rorcik.structures.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

public interface IStructureGenerator {
    void generate(ServerLevel level, BlockPos origin, String type, Rotation rotation);
}