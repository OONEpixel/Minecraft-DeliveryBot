package com.haishihua.deliverybot.persistence;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public final class BotRepository {
    private final JavaPlugin plugin;
    private final File file;

    public BotRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bot.yml");
    }

    public Optional<BotRecord> load() {
        if (!file.isFile()) {
            return Optional.empty();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String uuidText = yaml.getString("bot.uuid");
        String name = yaml.getString("bot.name");
        String world = yaml.getString("bot.home.world");
        if (uuidText == null || name == null || world == null) {
            return Optional.empty();
        }

        try {
            UUID uuid = UUID.fromString(uuidText);
            LocationData home = new LocationData(
                    world,
                    yaml.getDouble("bot.home.x"),
                    yaml.getDouble("bot.home.y"),
                    yaml.getDouble("bot.home.z"),
                    (float) yaml.getDouble("bot.home.yaw"),
                    (float) yaml.getDouble("bot.home.pitch")
            );
            return Optional.of(new BotRecord(uuid, name, home));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("bot.yml 中的 UUID 无效，将忽略绑定记录。");
            return Optional.empty();
        }
    }

    public void save(BotRecord record) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("bot.uuid", record.uuid().toString());
        yaml.set("bot.name", record.name());
        yaml.set("bot.home.world", record.home().world());
        yaml.set("bot.home.x", record.home().x());
        yaml.set("bot.home.y", record.home().y());
        yaml.set("bot.home.z", record.home().z());
        yaml.set("bot.home.yaw", record.home().yaw());
        yaml.set("bot.home.pitch", record.home().pitch());

        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存 bot.yml: " + exception.getMessage());
        }
    }

    public void clear() {
        if (!file.isFile()) {
            return;
        }
        try {
            java.nio.file.Files.delete(file.toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("无法删除机器人绑定记录 " + file.getPath(), exception);
        }
    }
}
