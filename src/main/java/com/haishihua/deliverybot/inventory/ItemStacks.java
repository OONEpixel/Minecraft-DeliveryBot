package com.haishihua.deliverybot.inventory;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ItemStacks {
    private ItemStacks() {
    }

    public static List<ItemStack> split(ItemStack template, int amount) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        int max = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(max, remaining));
            result.add(stack);
            remaining -= stack.getAmount();
        }
        return result;
    }

    public static List<ItemStack> compact(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack sourceStack : source) {
            int remaining = sourceStack.getAmount();
            for (ItemStack target : result) {
                if (remaining == 0) {
                    break;
                }
                if (!target.isSimilar(sourceStack) || target.getAmount() >= target.getMaxStackSize()) {
                    continue;
                }
                int moved = Math.min(remaining, target.getMaxStackSize() - target.getAmount());
                target.setAmount(target.getAmount() + moved);
                remaining -= moved;
            }
            if (remaining > 0) {
                result.addAll(split(sourceStack, remaining));
            }
        }
        return List.copyOf(result);
    }
}
