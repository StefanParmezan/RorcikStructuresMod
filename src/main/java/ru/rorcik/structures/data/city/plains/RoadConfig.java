package ru.rorcik.structures.data.city.plains;

import java.util.List;

public record RoadConfig(
        String type,
        int[] offset,
        int[] size,
        int decorCount,
        List<String> connections,
        List<Integer> stopVariants
) {}