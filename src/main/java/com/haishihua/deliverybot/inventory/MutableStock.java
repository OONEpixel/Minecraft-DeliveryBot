package com.haishihua.deliverybot.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class MutableStock {
    private final List<Slot> slots;

    private MutableStock(List<Slot> slots) {
        this.slots = slots;
    }

    public static MutableStock snapshot(List<ContainerAccess> containers) {
        List<Slot> slots = new ArrayList<>();
        for (ContainerAccess access : containers) {
            ItemStack[] contents = access.inventory().getStorageContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                    slots.add(new Slot(access, slot, item.clone(), 0));
                }
            }
        }
        return new MutableStock(slots);
    }

    public MutableStock copy() {
        return new MutableStock(slots.stream()
                .map(Slot::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
    }

    public void addVirtual(List<ItemStack> items) {
        int insertionIndex = 0;
        for (ItemStack item : ItemStacks.compact(items)) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                slots.add(insertionIndex++, new Slot(null, -1, item.clone(), 0));
            }
        }
    }

    public List<ItemStack> virtualRemainders() {
        List<ItemStack> result = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.source == null && slot.available() > 0) {
                ItemStack copy = slot.item.clone();
                copy.setAmount(slot.available());
                result.add(copy);
            }
        }
        return ItemStacks.compact(result);
    }

    public List<ItemStack> take(Predicate<ItemStack> matcher, int requested) {
        int[] checkpoint = usage();
        int remaining = requested;
        List<ItemStack> result = new ArrayList<>();
        for (Slot slot : slots) {
            if (remaining == 0 || !matcher.test(slot.item)) {
                continue;
            }
            int amount = Math.min(remaining, slot.available());
            if (amount <= 0) {
                continue;
            }
            slot.used += amount;
            ItemStack copy = slot.item.clone();
            copy.setAmount(amount);
            result.add(copy);
            remaining -= amount;
        }
        if (remaining == 0) {
            return result;
        }
        restore(checkpoint);
        return null;
    }

    public boolean allocate(Predicate<ItemStack> matcher, int requested) {
        int[] checkpoint = usage();
        int remaining = requested;
        for (Slot slot : slots) {
            if (remaining == 0 || !matcher.test(slot.item)) {
                continue;
            }
            int amount = Math.min(remaining, slot.available());
            slot.used += amount;
            remaining -= amount;
        }
        if (remaining == 0) {
            return true;
        }
        restore(checkpoint);
        return false;
    }

    public List<ItemStack> takeUpTo(Predicate<ItemStack> matcher, int requested) {
        int remaining = requested;
        List<ItemStack> result = new ArrayList<>();
        for (Slot slot : slots) {
            if (remaining == 0 || !matcher.test(slot.item)) {
                continue;
            }
            int amount = Math.min(remaining, slot.available());
            if (amount <= 0) {
                continue;
            }
            slot.used += amount;
            ItemStack copy = slot.item.clone();
            copy.setAmount(amount);
            result.add(copy);
            remaining -= amount;
        }
        return List.copyOf(result);
    }

    public int total(Predicate<ItemStack> matcher) {
        int total = 0;
        for (Slot slot : slots) {
            if (matcher.test(slot.item)) {
                total += slot.available();
            }
        }
        return total;
    }

    public Map<Material, Integer> consumptionSince(MutableStock base) {
        if (base.slots.size() != slots.size()) {
            throw new IllegalArgumentException("Stock snapshots do not share a source");
        }
        Map<Material, Integer> result = new HashMap<>();
        for (int index = 0; index < slots.size(); index++) {
            int consumed = slots.get(index).used - base.slots.get(index).used;
            if (consumed > 0) {
                result.merge(slots.get(index).item.getType(), consumed, Integer::sum);
            }
        }
        return result;
    }

    public List<ItemWithdrawal> withdrawals() {
        List<ItemWithdrawal> result = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.source != null && slot.used > 0) {
                result.add(new ItemWithdrawal(slot.source, slot.index, slot.item.clone(), slot.used));
            }
        }
        return List.copyOf(result);
    }

    private int[] usage() {
        int[] result = new int[slots.size()];
        for (int index = 0; index < slots.size(); index++) {
            result[index] = slots.get(index).used;
        }
        return result;
    }

    private void restore(int[] usage) {
        for (int index = 0; index < slots.size(); index++) {
            slots.get(index).used = usage[index];
        }
    }

    private static final class Slot {
        private final ContainerAccess source;
        private final int index;
        private final ItemStack item;
        private int used;

        private Slot(ContainerAccess source, int index, ItemStack item, int used) {
            this.source = source;
            this.index = index;
            this.item = item;
            this.used = used;
        }

        private int available() {
            return item.getAmount() - used;
        }

        private Slot copy() {
            return new Slot(source, index, item, used);
        }
    }
}
