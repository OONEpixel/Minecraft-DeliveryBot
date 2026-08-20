package com.haishihua.deliverybot.bot;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BotEquipmentService implements Listener {
    private final JavaPlugin plugin;
    private final BotManager botManager;
    private final NamespacedKey botMarker;
    private final NamespacedKey elytraMarker;
    private final NamespacedKey rocketMarker;
    private final Map<UUID, UUID> authorizedDrops = new HashMap<>();
    private BukkitTask watchdog;

    public BotEquipmentService(JavaPlugin plugin, BotManager botManager) {
        this.plugin = plugin;
        this.botManager = botManager;
        this.botMarker = new NamespacedKey(plugin, "bot");
        this.elytraMarker = new NamespacedKey(plugin, "bot_elytra");
        this.rocketMarker = new NamespacedKey(plugin, "bot_rocket");
    }

    public void start() {
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> botManager.bot().ifPresent(this::ensureEquipment), 1L, 20L);
        botManager.bot().ifPresent(this::ensureEquipment);
    }

    public void stop() {
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        authorizedDrops.clear();
    }

    public void ensureEquipment(Player bot) {
        bot.getPersistentDataContainer().set(botMarker, PersistentDataType.BYTE, (byte) 1);
        bot.setInvulnerable(true);
        bot.setFoodLevel(20);
        bot.setSaturation(20.0f);
        bot.setCanPickupItems(false);

        if (!hasMarker(bot.getInventory().getChestplate(), elytraMarker)) {
            bot.getInventory().setChestplate(createElytra());
        }
        if (!hasMarker(bot.getInventory().getItemInOffHand(), rocketMarker)) {
            bot.getInventory().setItemInOffHand(createRocket());
        }
    }

    public void release(Player player) {
        authorizedDrops.remove(player.getUniqueId());
        player.getPersistentDataContainer().remove(botMarker);
        player.setInvulnerable(false);
        player.setCanPickupItems(true);
        player.setGliding(false);
        player.setSprinting(false);

        if (hasMarker(player.getInventory().getChestplate(), elytraMarker)) {
            player.getInventory().setChestplate(null);
        }
        if (hasMarker(player.getInventory().getItemInOffHand(), rocketMarker)) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    public ItemStack rocket() {
        return createRocket();
    }

    public void authorizeNextDrop(Player bot, Player recipient) {
        authorizedDrops.put(bot.getUniqueId(), recipient.getUniqueId());
    }

    public void revokeDrop(Player bot) {
        authorizedDrops.remove(bot.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (botManager.isBot(event.getPlayer()) && hasMarker(event.getItem(), elytraMarker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBoost(PlayerElytraBoostEvent event) {
        if (botManager.isBot(event.getPlayer())) {
            event.setShouldConsume(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && botManager.isBot(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && botManager.isBot(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && botManager.isBot(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!botManager.isBot(event.getPlayer())) {
            return;
        }

        UUID recipient = authorizedDrops.remove(event.getPlayer().getUniqueId());
        if (recipient == null) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        event.getItemDrop().setOwner(recipient);
        event.getItemDrop().setPickupDelay(0);
        Player target = Bukkit.getPlayer(recipient);
        if (target != null && target.getWorld() == event.getItemDrop().getWorld()) {
            Vector direction = target.getLocation().add(0, 0.8, 0).toVector()
                    .subtract(event.getItemDrop().getLocation().toVector());
            double distance = direction.length();
            if (distance > 0.001) {
                double speed = Math.min(0.48, 0.28 + distance * 0.04);
                direction.normalize().multiply(speed);
                direction.setY(Math.max(0.12, direction.getY()));
                event.getItemDrop().setVelocity(direction);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && botManager.isBot(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && botManager.isBot(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (botManager.isBot(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (botManager.refreshIfKnownName(event.getPlayer())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> ensureEquipment(event.getPlayer()));
        }
    }

    private ItemStack createElytra() {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        meta.setUnbreakable(true);
        meta.displayName(Component.text("配送机器人鞘翅", NamedTextColor.AQUA));
        meta.getPersistentDataContainer().set(elytraMarker, PersistentDataType.BYTE, (byte) 1);
        elytra.setItemMeta(meta);
        return elytra;
    }

    private ItemStack createRocket() {
        ItemStack rocket = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta meta = (FireworkMeta) rocket.getItemMeta();
        meta.setPower(1);
        meta.displayName(Component.text("无限飞行时间 1 烟花", NamedTextColor.AQUA));
        meta.getPersistentDataContainer().set(rocketMarker, PersistentDataType.BYTE, (byte) 1);
        rocket.setItemMeta(meta);
        return rocket;
    }

    private boolean hasMarker(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
