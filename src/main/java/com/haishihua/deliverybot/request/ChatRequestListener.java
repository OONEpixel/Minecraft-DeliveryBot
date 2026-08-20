package com.haishihua.deliverybot.request;

import com.haishihua.deliverybot.config.Messages;
import com.haishihua.deliverybot.config.PluginSettings;
import com.haishihua.deliverybot.job.DeliveryCoordinator;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatRequestListener implements Listener {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final RequestParser parser;
    private final DeliveryCoordinator coordinator;
    private final Map<UUID, Long> lastRequests = new ConcurrentHashMap<>();

    public ChatRequestListener(
            JavaPlugin plugin,
            PluginSettings settings,
            RequestParser parser,
            DeliveryCoordinator coordinator
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.parser = parser;
        this.coordinator = coordinator;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        ParseResult result = parser.parse(plain);
        if (!result.addressed()) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("deliverybot.request")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> Messages.error(player, "你没有请求配送的权限。"));
            return;
        }
        if (!result.successful()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> Messages.error(player, result.error()));
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = settings.requestCooldownSeconds() * 1000L;
        Long previous = lastRequests.get(player.getUniqueId());
        if (previous != null && now - previous < cooldownMillis) {
            long waitSeconds = Math.max(1, (cooldownMillis - (now - previous) + 999) / 1000);
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> Messages.error(player, "请求过快，请等待 " + waitSeconds + " 秒。"));
            return;
        }
        lastRequests.put(player.getUniqueId(), now);

        plugin.getServer().getScheduler().runTask(plugin,
                () -> coordinator.enqueue(new DeliveryRequest(
                        player.getUniqueId(),
                        result.requests(),
                        java.time.Instant.now()
                )));
    }
}
