package com.haishihua.deliverybot.bot;

import com.haishihua.deliverybot.config.PluginSettings;
import com.haishihua.deliverybot.persistence.BotRecord;
import com.haishihua.deliverybot.persistence.BotRepository;
import com.haishihua.deliverybot.persistence.LocationData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class BotManager {
    private final PluginSettings settings;
    private final BotRepository repository;
    private BotRecord record;

    public BotManager(PluginSettings settings, BotRepository repository) {
        this.settings = settings;
        this.repository = repository;
        this.record = repository.load().orElse(null);
    }

    public Optional<Player> bot() {
        if (record == null) {
            return Optional.empty();
        }

        Player byUuid = Bukkit.getPlayer(record.uuid());
        if (byUuid != null) {
            return Optional.of(byUuid);
        }

        Player byName = Bukkit.getPlayerExact(record.name());
        if (byName != null) {
            refreshIdentity(byName);
            return Optional.of(byName);
        }
        return Optional.empty();
    }

    public Optional<Location> home() {
        return record == null ? Optional.empty() : record.home().resolve();
    }

    public void bind(Player player) {
        record = new BotRecord(player.getUniqueId(), player.getName(), LocationData.from(player.getLocation()));
        repository.save(record);
    }

    public void unbind() {
        repository.clear();
        record = null;
    }

    public boolean refreshIfKnownName(Player player) {
        if (record == null || !record.name().equalsIgnoreCase(player.getName())) {
            return false;
        }
        refreshIdentity(player);
        return true;
    }

    public void setHome(Location home) {
        if (record == null) {
            throw new IllegalStateException("No bot is bound");
        }
        record = new BotRecord(record.uuid(), record.name(), LocationData.from(home));
        repository.save(record);
    }

    public boolean isBot(Player player) {
        return record != null && record.uuid().equals(player.getUniqueId());
    }

    public boolean isBot(UUID uuid) {
        return record != null && record.uuid().equals(uuid);
    }

    public boolean isBound() {
        return record != null;
    }

    public String configuredName() {
        return record == null ? settings.botName() : record.name();
    }

    public Optional<BotRecord> record() {
        return Optional.ofNullable(record);
    }

    private void refreshIdentity(Player player) {
        if (record.uuid().equals(player.getUniqueId())) {
            return;
        }
        record = new BotRecord(player.getUniqueId(), player.getName(), record.home());
        repository.save(record);
    }
}
