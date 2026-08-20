package com.haishihua.deliverybot.inventory;

import com.haishihua.deliverybot.crafting.CraftAttempt;
import com.haishihua.deliverybot.crafting.CraftPlanner;
import com.haishihua.deliverybot.crafting.CraftingStep;
import com.haishihua.deliverybot.crafting.WorkstationScanner;
import com.haishihua.deliverybot.crafting.Workstations;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import com.haishihua.deliverybot.request.RequestSpec;
import com.haishihua.deliverybot.request.RequestUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FulfillmentPlanner {
    private final WarehouseRegistry warehouses;
    private final WorkstationScanner workstationScanner;
    private final CraftPlanner craftPlanner;

    public FulfillmentPlanner(
            WarehouseRegistry warehouses,
            WorkstationScanner workstationScanner,
            CraftPlanner craftPlanner
    ) {
        this.warehouses = warehouses;
        this.workstationScanner = workstationScanner;
        this.craftPlanner = craftPlanner;
    }

    public PlanningResult plan(Location home, Material requested, int amount) {
        return plan(home, List.of(new RequestSpec(requested, amount, amount, RequestUnit.ITEM)));
    }

    public PlanningResult plan(Location home, List<RequestSpec> requests) {
        List<ContainerAccess> containers = warehouses.nearby(home);
        if (containers.isEmpty()) {
            return PlanningResult.failure("附近没有已登记且已加载的仓库容器。");
        }

        MutableStock stock = MutableStock.snapshot(containers);
        List<ItemStack> delivery = new ArrayList<>();
        List<CraftingStep> craftingSteps = new ArrayList<>();
        Workstations stations = null;

        for (RequestSpec request : requests) {
            Material requested = request.material();
            int amount = request.amount();
            int directAmount = Math.min(amount, stock.total(item -> item.getType() == requested));
            List<ItemStack> direct = directAmount == 0
                    ? List.of()
                    : stock.take(item -> item.getType() == requested, directAmount);
            if (direct == null) {
                return PlanningResult.failure("库存扫描结果在规划期间发生了变化，请重试。");
            }
            delivery.addAll(direct);

            int missing = amount - directAmount;
            if (missing > 0) {
                if (stations == null) {
                    stations = workstationScanner.scan(home);
                }
                if (stations.stonecutter().isEmpty() && stations.craftingTable().isEmpty()) {
                    return PlanningResult.failure(requested.name() + " 成品库存不足，附近也没有切石机或工作台。");
                }

                Optional<CraftAttempt> attempt = craftPlanner.plan(requested, missing, stock, stations);
                if (attempt.isEmpty()) {
                    return PlanningResult.failure(requested.name()
                            + " 成品库存不足，现有材料无法在递归深度限制内完成合成。");
                }

                CraftAttempt craft = attempt.get();
                stock = craft.stock();
                stock.addVirtual(craft.returnItems());
                delivery.addAll(ItemStacks.split(craft.result(), missing));
                craftingSteps.addAll(craft.steps());
            }
        }

        List<ItemWithdrawal> withdrawals = stock.withdrawals();
        Location pickup = withdrawals.isEmpty()
                ? home
                : accessiblePickup(withdrawals.getFirst().source().location(), home);
        FulfillmentPlan plan = new FulfillmentPlan(
                withdrawals,
                ItemStacks.compact(delivery),
                stock.virtualRemainders(),
                pickup,
                craftingSteps
        );
        return PlanningResult.success(plan);
    }

    private Location accessiblePickup(Location container, Location home) {
        World world = container.getWorld();
        if (world == null) {
            return container.clone();
        }

        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        int baseX = container.getBlockX();
        int baseY = container.getBlockY();
        int baseZ = container.getBlockZ();
        for (int radius = 1; radius <= 2; radius++) {
            for (int x = baseX - radius; x <= baseX + radius; x++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                    if (Math.max(Math.abs(x - baseX), Math.abs(z - baseZ)) != radius) {
                        continue;
                    }
                    for (int y = baseY - 1; y <= baseY + 2; y++) {
                        Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                        if (!canStandAt(candidate)) {
                            continue;
                        }
                        double distance = candidate.distanceSquared(home);
                        if (distance < bestDistance) {
                            best = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }

        Location above = new Location(world, baseX + 0.5, baseY + 1.0, baseZ + 0.5);
        return canStandAt(above) ? above : container.clone().add(0, 1.5, 0);
    }

    private boolean canStandAt(Location feet) {
        Block feetBlock = feet.getBlock();
        Block headBlock = feet.clone().add(0, 1, 0).getBlock();
        Block support = feet.clone().subtract(0, 1, 0).getBlock();
        return feetBlock.isPassable()
                && !feetBlock.isLiquid()
                && headBlock.isPassable()
                && !headBlock.isLiquid()
                && !support.isPassable()
                && !support.isLiquid();
    }
}
