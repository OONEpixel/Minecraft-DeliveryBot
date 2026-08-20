package com.haishihua.deliverybot.inventory;

import com.haishihua.deliverybot.crafting.CraftingStep;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class FulfillmentPlan {
    private final List<ItemWithdrawal> withdrawals;
    private final List<ItemStack> deliveryItems;
    private final List<ItemStack> returnItems;
    private final Location pickupLocation;
    private final List<CraftingStep> craftingSteps;
    private boolean applied;

    public FulfillmentPlan(
            List<ItemWithdrawal> withdrawals,
            List<ItemStack> deliveryItems,
            List<ItemStack> returnItems,
            Location pickupLocation,
            List<CraftingStep> craftingSteps
    ) {
        this.withdrawals = List.copyOf(withdrawals);
        this.deliveryItems = List.copyOf(deliveryItems);
        this.returnItems = List.copyOf(returnItems);
        this.pickupLocation = pickupLocation.clone();
        this.craftingSteps = List.copyOf(craftingSteps);
    }

    public boolean apply() {
        if (applied) {
            return false;
        }
        for (ItemWithdrawal withdrawal : withdrawals) {
            ItemStack current = withdrawal.source().inventory().getItem(withdrawal.slot());
            if (current == null
                    || !current.isSimilar(withdrawal.expected())
                    || current.getAmount() < withdrawal.amount()) {
                return false;
            }
        }

        for (ItemWithdrawal withdrawal : withdrawals) {
            Inventory inventory = withdrawal.source().inventory();
            ItemStack current = inventory.getItem(withdrawal.slot());
            int remaining = current.getAmount() - withdrawal.amount();
            if (remaining == 0) {
                inventory.setItem(withdrawal.slot(), null);
            } else {
                current.setAmount(remaining);
                inventory.setItem(withdrawal.slot(), current);
            }
        }
        applied = true;
        return true;
    }

    /**
     * Restores every withdrawal if a later, pre-dispatch step fails. This method is intended to
     * run synchronously on the server thread immediately after {@link #apply()}.
     */
    public void rollback() {
        if (!applied) {
            return;
        }

        for (ItemWithdrawal withdrawal : withdrawals) {
            Inventory inventory = withdrawal.source().inventory();
            ItemStack restored = withdrawal.expected().clone();
            restored.setAmount(withdrawal.amount());
            ItemStack current = inventory.getItem(withdrawal.slot());

            if (current == null || current.getType().isAir()) {
                inventory.setItem(withdrawal.slot(), restored);
                continue;
            }
            if (current.isSimilar(restored)
                    && current.getAmount() + restored.getAmount() <= current.getMaxStackSize()) {
                current.setAmount(current.getAmount() + restored.getAmount());
                inventory.setItem(withdrawal.slot(), current);
                continue;
            }

            Map<Integer, ItemStack> leftovers = inventory.addItem(restored);
            Location location = withdrawal.source().location();
            if (location.getWorld() != null) {
                leftovers.values().forEach(item -> location.getWorld().dropItemNaturally(location, item));
            }
        }
        applied = false;
    }

    public List<ItemStack> deliveryItems() {
        return deliveryItems.stream().map(ItemStack::clone).toList();
    }

    public List<ItemStack> returnItems() {
        return returnItems.stream().map(ItemStack::clone).toList();
    }

    public Location pickupLocation() {
        return pickupLocation.clone();
    }

    public List<CraftingStep> craftingSteps() {
        return List.copyOf(craftingSteps);
    }
}
