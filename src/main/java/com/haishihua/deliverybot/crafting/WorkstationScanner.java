package com.haishihua.deliverybot.crafting;

import com.haishihua.deliverybot.config.PluginSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Optional;

public final class WorkstationScanner {
    private final PluginSettings settings;

    public WorkstationScanner(PluginSettings settings) {
        this.settings = settings;
    }

    public Workstations scan(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return new Workstations(Optional.empty(), Optional.empty());
        }

        int radius = settings.workstationRadius();
        double radiusSquared = (double) radius * radius;
        Location nearestStonecutter = null;
        Location nearestTable = null;
        double stoneDistance = Double.MAX_VALUE;
        double tableDistance = Double.MAX_VALUE;

        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = Math.max(world.getMinHeight(), center.getBlockY() - radius);
                     y <= Math.min(world.getMaxHeight() - 1, center.getBlockY() + radius); y++) {
                    Location location = new Location(world, x + 0.5, y + 1.0, z + 0.5);
                    double distance = location.distanceSquared(center);
                    if (distance > radiusSquared) {
                        continue;
                    }
                    Material material = world.getBlockAt(x, y, z).getType();
                    if (material == Material.STONECUTTER && distance < stoneDistance) {
                        nearestStonecutter = location;
                        stoneDistance = distance;
                    } else if (material == Material.CRAFTING_TABLE && distance < tableDistance) {
                        nearestTable = location;
                        tableDistance = distance;
                    }
                }
            }
        }

        return new Workstations(Optional.ofNullable(nearestStonecutter), Optional.ofNullable(nearestTable));
    }
}
