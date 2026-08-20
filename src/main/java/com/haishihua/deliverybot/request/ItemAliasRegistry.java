package com.haishihua.deliverybot.request;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ItemAliasRegistry implements ItemResolver {
    private final JavaPlugin plugin;
    private final File file;
    private volatile Map<String, Material> aliases = Map.of();
    private volatile Map<Material, String> displayNames = Map.of();

    public ItemAliasRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "item-aliases.yml");
        if (!file.isFile()) {
            plugin.saveResource("item-aliases.yml", false);
        }
        reload();
    }

    public void reload() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Material> loaded = new HashMap<>();
        Map<Material, String> names = new EnumMap<>(Material.class);
        if (yaml.getBoolean("include-vanilla-zh-cn", true)) {
            loadVanillaChineseNames(loaded, names);
        }

        ConfigurationSection section = yaml.getConfigurationSection("aliases");
        if (section != null) {
            for (String alias : section.getKeys(false)) {
                String materialName = section.getString(alias);
                Material material = materialName == null ? null : Material.matchMaterial(materialName);
                if (material == null || material.isAir() || !material.isItem()) {
                    plugin.getLogger().warning("忽略无效物品别名 " + alias + ": " + materialName);
                    continue;
                }
                loaded.put(normalize(alias), material);
                names.putIfAbsent(material, alias);
            }
        }
        aliases = Map.copyOf(loaded);
        displayNames = Map.copyOf(names);
    }

    private void loadVanillaChineseNames(Map<String, Material> loaded, Map<Material, String> names) {
        try (Reader reader = new InputStreamReader(
                java.util.Objects.requireNonNull(plugin.getResource("vanilla-zh-cn.yml")),
                StandardCharsets.UTF_8
        )) {
            for (Map.Entry<Material, String> entry : VanillaChineseNames.load(reader).entrySet()) {
                Material material = entry.getKey();
                if (material.isAir() || !material.isItem()) {
                    continue;
                }
                String chineseName = entry.getValue();
                loaded.putIfAbsent(normalize(chineseName), material);
                names.putIfAbsent(material, chineseName);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("无法加载内置 Minecraft 中文物品名称: " + exception.getMessage());
        }
    }

    @Override
    public Optional<Material> resolve(String input) {
        String normalized = normalize(input);
        Material alias = aliases.get(normalized);
        if (alias != null) {
            return Optional.of(alias);
        }

        String materialName = normalized.startsWith("minecraft:")
                ? normalized.substring("minecraft:".length())
                : normalized;
        Material material = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
        if (material == null || material.isAir() || !material.isItem()) {
            return Optional.empty();
        }
        return Optional.of(material);
    }

    @Override
    public String display(Material material) {
        return displayNames.getOrDefault(material, material.getKey().getKey());
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('　', ' ');
    }
}
