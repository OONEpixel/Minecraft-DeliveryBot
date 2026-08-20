package com.haishihua.deliverybot.inventory;

import com.haishihua.deliverybot.config.PluginSettings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WarehouseRegistry {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final File file;
    private final Set<ContainerKey> keys = new LinkedHashSet<>();

    public WarehouseRegistry(JavaPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.file = new File(plugin.getDataFolder(), "warehouses.yml");
        load();
    }

    public AddResult add(Block block) {
        if (!settings.isAllowedContainer(block.getType())) {
            return AddResult.TYPE_NOT_ALLOWED;
        }
        BlockState state = block.getState();
        if (!(state instanceof InventoryHolder holder)) {
            return AddResult.NOT_A_CONTAINER;
        }
        boolean added = keys.add(keyFor(block, holder));
        if (added) {
            save();
            return AddResult.ADDED;
        }
        return AddResult.ALREADY_REGISTERED;
    }

    public boolean remove(Block block) {
        BlockState state = block.getState();
        ContainerKey key = state instanceof InventoryHolder holder
                ? keyFor(block, holder)
                : ContainerKey.from(block.getLocation());
        boolean removed = keys.remove(key);
        if (removed) {
            save();
        }
        return removed;
    }

    public BulkAddResult addNearby(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) {
            return new BulkAddResult(0, 0, 0, 0);
        }

        int radiusSquared = radius * radius;
        int scannedBlocks = 0;
        int foundContainers = 0;
        int addedContainers = 0;
        int alreadyRegistered = 0;
        Set<ContainerKey> discovered = new LinkedHashSet<>();

        int minimumY = Math.max(world.getMinHeight(), center.getBlockY() - radius);
        int maximumY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + radius);
        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = minimumY; y <= maximumY; y++) {
                    int deltaX = x - center.getBlockX();
                    int deltaY = y - center.getBlockY();
                    int deltaZ = z - center.getBlockZ();
                    if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > radiusSquared) {
                        continue;
                    }
                    scannedBlocks++;
                    Block block = world.getBlockAt(x, y, z);
                    if (!settings.isAllowedContainer(block.getType())) {
                        continue;
                    }
                    BlockState state = block.getState();
                    if (!(state instanceof InventoryHolder holder)) {
                        continue;
                    }
                    ContainerKey key = keyFor(block, holder);
                    if (!discovered.add(key)) {
                        continue;
                    }
                    foundContainers++;
                    if (keys.add(key)) {
                        addedContainers++;
                    } else {
                        alreadyRegistered++;
                    }
                }
            }
        }

        if (addedContainers > 0) {
            save();
        }
        return new BulkAddResult(scannedBlocks, foundContainers, addedContainers, alreadyRegistered);
    }

    public int size() {
        return keys.size();
    }

    public List<ContainerAccess> nearby(Location center) {
        if (center.getWorld() == null) {
            return List.of();
        }

        double maxDistanceSquared = (double) settings.warehouseScanRadius() * settings.warehouseScanRadius();
        List<ContainerAccess> result = new ArrayList<>();
        Set<Inventory> seenInventories = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ContainerKey> seenInventoryLocations = new LinkedHashSet<>();

        for (ContainerKey key : keys) {
            if (!key.world().equals(center.getWorld().getName())) {
                continue;
            }
            World world = center.getWorld();
            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }
            Location location = new Location(world, key.x() + 0.5, key.y() + 0.5, key.z() + 0.5);
            if (location.distanceSquared(center) > maxDistanceSquared) {
                continue;
            }

            BlockState state = world.getBlockAt(key.x(), key.y(), key.z()).getState();
            if (!(state instanceof InventoryHolder holder) || !settings.isAllowedContainer(state.getType())) {
                continue;
            }
            Inventory inventory = holder.getInventory();
            Location inventoryLocation = inventory.getLocation();
            ContainerKey inventoryKey = inventoryLocation == null
                    ? key
                    : ContainerKey.from(inventoryLocation);
            if (seenInventories.add(inventory) && seenInventoryLocations.add(inventoryKey)) {
                result.add(new ContainerAccess(key, location, inventory));
            }
        }
        return List.copyOf(result);
    }

    public ItemStack deposit(Location center, ItemStack input) {
        ItemStack remaining = input.clone();
        for (ContainerAccess access : nearby(center)) {
            Map<Integer, ItemStack> leftovers = access.inventory().addItem(remaining);
            if (leftovers.isEmpty()) {
                return null;
            }
            remaining = leftovers.values().iterator().next().clone();
        }
        return remaining;
    }

    private ContainerKey keyFor(Block block, InventoryHolder holder) {
        Location inventoryLocation = holder.getInventory().getLocation();
        if (inventoryLocation != null && inventoryLocation.getWorld() == block.getWorld()) {
            return ContainerKey.from(inventoryLocation);
        }
        return ContainerKey.from(block.getLocation());
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String value : yaml.getStringList("containers")) {
            try {
                keys.add(ContainerKey.parse(value));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("忽略 warehouses.yml 中的无效位置: " + value);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("containers", keys.stream().map(ContainerKey::serialize).toList());
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存 warehouses.yml: " + exception.getMessage());
        }
    }

    public enum AddResult {
        ADDED,
        ALREADY_REGISTERED,
        TYPE_NOT_ALLOWED,
        NOT_A_CONTAINER
    }

    public record BulkAddResult(
            int scannedBlocks,
            int foundContainers,
            int addedContainers,
            int alreadyRegistered
    ) {
    }
}
