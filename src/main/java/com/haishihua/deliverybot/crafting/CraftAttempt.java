package com.haishihua.deliverybot.crafting;

import com.haishihua.deliverybot.inventory.MutableStock;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record CraftAttempt(
        MutableStock stock,
        ItemStack result,
        List<ItemStack> returnItems,
        List<CraftingStep> steps
) {
    public CraftAttempt {
        result = result.clone();
        returnItems = returnItems.stream().map(ItemStack::clone).toList();
        steps = List.copyOf(steps);
    }

    @Override
    public ItemStack result() {
        return result.clone();
    }

    @Override
    public List<ItemStack> returnItems() {
        return returnItems.stream().map(ItemStack::clone).toList();
    }
}
