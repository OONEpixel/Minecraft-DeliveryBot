package com.haishihua.deliverybot.inventory;

import org.bukkit.inventory.ItemStack;

public record ItemWithdrawal(ContainerAccess source, int slot, ItemStack expected, int amount) {
}
