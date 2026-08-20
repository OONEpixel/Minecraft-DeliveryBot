package com.haishihua.deliverybot.delivery;

import com.haishihua.deliverybot.bot.BotEquipmentService;
import com.haishihua.deliverybot.inventory.FulfillmentPlan;
import com.haishihua.deliverybot.inventory.WarehouseRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

public final class CargoService {
    private static final int FIRST_CARGO_SLOT = 1;
    private static final int LAST_CARGO_SLOT = 35;

    private final WarehouseRegistry warehouses;
    private final BotEquipmentService equipment;

    public CargoService(WarehouseRegistry warehouses, BotEquipmentService equipment) {
        this.warehouses = warehouses;
        this.equipment = equipment;
    }

    public boolean storageEmpty(Player bot) {
        for (ItemStack item : bot.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    public boolean canLoad(FulfillmentPlan plan) {
        return plan.deliveryItems().size() + plan.returnItems().size() <= LAST_CARGO_SLOT;
    }

    public CargoManifest load(Player bot, FulfillmentPlan plan) {
        if (!storageEmpty(bot) || !canLoad(plan)) {
            throw new IllegalStateException("Bot storage cannot accept the cargo");
        }

        PlayerInventory inventory = bot.getInventory();
        inventory.setHeldItemSlot(0);
        inventory.setItem(0, null);
        int slot = FIRST_CARGO_SLOT;
        List<Integer> deliverySlots = new ArrayList<>();
        List<Integer> returnSlots = new ArrayList<>();

        for (ItemStack item : plan.deliveryItems()) {
            inventory.setItem(slot, item.clone());
            deliverySlots.add(slot++);
        }
        for (ItemStack item : plan.returnItems()) {
            inventory.setItem(slot, item.clone());
            returnSlots.add(slot++);
        }
        return new CargoManifest(deliverySlots, returnSlots);
    }

    public void clearStorage(Player bot) {
        PlayerInventory inventory = bot.getInventory();
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            inventory.setItem(slot, null);
        }
        inventory.setHeldItemSlot(0);
    }

    public boolean dropSlot(Player bot, Player recipient, int sourceSlot) {
        PlayerInventory inventory = bot.getInventory();
        ItemStack cargo = inventory.getItem(sourceSlot);
        if (cargo == null || cargo.getType().isAir()) {
            return true;
        }

        faceRecipient(bot, recipient);
        inventory.setItem(sourceSlot, null);
        inventory.setHeldItemSlot(0);
        inventory.setItem(0, cargo);
        equipment.authorizeNextDrop(bot, recipient);
        boolean dropped = bot.dropItem(true);
        if (!dropped) {
            equipment.revokeDrop(bot);
            inventory.setItem(0, null);
            inventory.setItem(sourceSlot, cargo);
        }
        return dropped;
    }

    private void faceRecipient(Player bot, Player recipient) {
        Location facing = bot.getLocation();
        org.bukkit.util.Vector direction = recipient.getEyeLocation().toVector()
                .subtract(bot.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.0001) {
            return;
        }
        facing.setDirection(direction);
        bot.setRotation(facing.getYaw(), facing.getPitch());
    }

    public int depositAll(Player bot, Location home) {
        PlayerInventory inventory = bot.getInventory();
        int remainingAmount = 0;
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemStack remaining = warehouses.deposit(home, item);
            inventory.setItem(slot, remaining);
            if (remaining != null) {
                remainingAmount += remaining.getAmount();
            }
        }
        inventory.setHeldItemSlot(0);
        return remainingAmount;
    }
}
