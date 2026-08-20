package com.haishihua.deliverybot.job;

import com.haishihua.deliverybot.bot.BotManager;
import com.haishihua.deliverybot.config.Messages;
import com.haishihua.deliverybot.config.PluginSettings;
import com.haishihua.deliverybot.crafting.CraftingStep;
import com.haishihua.deliverybot.delivery.CargoManifest;
import com.haishihua.deliverybot.delivery.CargoService;
import com.haishihua.deliverybot.inventory.FulfillmentPlan;
import com.haishihua.deliverybot.inventory.FulfillmentPlanner;
import com.haishihua.deliverybot.inventory.PlanningResult;
import com.haishihua.deliverybot.navigation.FlightController;
import com.haishihua.deliverybot.navigation.FlightResult;
import com.haishihua.deliverybot.navigation.MovementMode;
import com.haishihua.deliverybot.navigation.NavigationMath;
import com.haishihua.deliverybot.request.DeliveryRequest;
import com.haishihua.deliverybot.request.ItemAliasRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;

public final class DeliveryCoordinator {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final BotManager botManager;
    private final FulfillmentPlanner planner;
    private final CargoService cargoService;
    private final FlightController flightController;
    private final ItemAliasRegistry aliases;
    private final Deque<DeliveryRequest> queue = new ArrayDeque<>();
    private ActiveDelivery active;
    private boolean shuttingDown;

    public DeliveryCoordinator(
            JavaPlugin plugin,
            PluginSettings settings,
            BotManager botManager,
            FulfillmentPlanner planner,
            CargoService cargoService,
            FlightController flightController,
            ItemAliasRegistry aliases
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.botManager = botManager;
        this.planner = planner;
        this.cargoService = cargoService;
        this.flightController = flightController;
        this.aliases = aliases;
    }

    public void enqueue(DeliveryRequest request) {
        if (shuttingDown) {
            return;
        }
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) {
            return;
        }
        queue.addLast(request);
        Messages.info(requester, "请求已加入队列：" + describeOrder(request)
                + "（前方 " + (queue.size() - 1 + (active == null ? 0 : 1)) + " 单）。");
        startNext();
    }

    public boolean cancelActive(String reason) {
        if (active == null) {
            return false;
        }
        flightController.cancel(false);
        failAndReturn(reason);
        return true;
    }

    public DeliveryState state() {
        return active == null ? DeliveryState.IDLE : active.state();
    }

    public int queuedJobs() {
        return queue.size();
    }

    public MovementMode movementMode() {
        return flightController.mode();
    }

    public void shutdown() {
        shuttingDown = true;
        queue.clear();
        flightController.cancel(false);
        if (active != null) {
            Player bot = active.bot();
            Location home = active.home();
            if (bot.isOnline()) {
                bot.teleport(home);
                bot.setGliding(false);
                int leftovers = cargoService.depositAll(bot, home);
                if (leftovers > 0) {
                    plugin.getLogger().warning("停服回收后仍有 " + leftovers + " 个物品留在机器人背包中。");
                }
            }
            active = null;
        }
    }

    public void prepareForUnbind() {
        queue.clear();
        flightController.cancel(false);
        active = null;
    }

    private void startNext() {
        if (active != null || shuttingDown) {
            return;
        }
        DeliveryRequest request = queue.pollFirst();
        if (request == null) {
            return;
        }

        Player requester = Bukkit.getPlayer(request.requester());
        Player bot = botManager.bot().orElse(null);
        Location home = botManager.home().orElse(null);
        if (requester == null) {
            scheduleNext();
            return;
        }
        if (bot == null || home == null) {
            Messages.error(requester, "机器人未在线或尚未绑定，请联系管理员。");
            scheduleNext();
            return;
        }
        if (requester.getWorld() != home.getWorld()) {
            Messages.error(requester, "机器人不能跨世界配送；请在同一世界请求。");
            scheduleNext();
            return;
        }
        if (!cargoService.storageEmpty(bot)) {
            int leftovers = cargoService.depositAll(bot, home);
            if (leftovers > 0) {
                Messages.error(requester, "机器人背包中有 " + leftovers + " 个无法退库的物品，暂时无法接单。");
                scheduleNext();
                return;
            }
        }

        PlanningResult result;
        try {
            result = planner.plan(home, request.items());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "订单备货规划异常：" + describeOrder(request), exception);
            Messages.error(requester, "备货规划发生异常，本次订单未扣除库存；详细原因已写入服务端日志。");
            scheduleNext();
            return;
        }
        if (!result.successful()) {
            Messages.error(requester, result.failure());
            scheduleNext();
            return;
        }

        FulfillmentPlan plan = result.plan();
        if (!cargoService.canLoad(plan)) {
            Messages.error(requester, "这批物品需要的背包格数超过机器人容量，请减少数量。");
            scheduleNext();
            return;
        }
        if (!plan.apply()) {
            Messages.error(requester, "仓库在扣料前发生变化，本次请求未扣除任何物品，请重试。");
            scheduleNext();
            return;
        }

        CargoManifest cargo;
        try {
            cargo = cargoService.load(bot, plan);
        } catch (RuntimeException exception) {
            cargoService.clearStorage(bot);
            plan.rollback();
            plugin.getLogger().severe("机器人装货失败，库存已回滚: " + exception.getMessage());
            Messages.error(requester, "机器人装货失败，本次扣料已回滚，请检查服务端日志。");
            scheduleNext();
            return;
        }
        active = new ActiveDelivery(request, plan, cargo, bot, home);
        if (plan.craftingSteps().isEmpty()) {
            Messages.info(requester, "已从成品库存中备货。");
        } else {
            Messages.info(requester, "已锁定库存，将执行 " + plan.craftingSteps().size() + " 步合成。");
        }
        goToWarehouse();
    }

    private void goToWarehouse() {
        ActiveDelivery job = active;
        if (job == null) {
            return;
        }
        job.state(DeliveryState.MOVING_TO_WAREHOUSE);
        travelOrContinue(job.plan().pickupLocation(), this::goToWorkstation);
    }

    private void goToWorkstation() {
        goToWorkstation(0);
    }

    private void goToWorkstation(int stepIndex) {
        ActiveDelivery job = active;
        if (job == null) {
            return;
        }
        if (stepIndex >= job.plan().craftingSteps().size()) {
            goToPlayer();
            return;
        }

        CraftingStep step = job.plan().craftingSteps().get(stepIndex);
        job.state(DeliveryState.MOVING_TO_WORKSTATION);
        travelOrContinue(step.workstation(), () -> {
            ActiveDelivery current = active;
            if (current == null) {
                return;
            }
            current.state(DeliveryState.CRAFTING);
            current.bot().swingMainHand();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (active == current) {
                    goToWorkstation(stepIndex + 1);
                }
            }, settings.craftingAnimationTicks());
        });
    }

    private void goToPlayer() {
        ActiveDelivery job = active;
        if (job == null) {
            return;
        }
        Player requester = Bukkit.getPlayer(job.request().requester());
        if (requester == null || requester.getWorld() != job.home().getWorld()) {
            failAndReturn("玩家已离线或离开当前世界，物品将退回仓库。");
            return;
        }

        job.state(DeliveryState.MOVING_TO_PLAYER);
        Vector approachOffset = deliveryApproachOffset(job.bot(), requester);
        flightController.travel(job.bot(), () -> {
            Player movingTarget = Bukkit.getPlayer(job.request().requester());
            return movingTarget == null ? null : movingTarget.getLocation().add(approachOffset);
        }, settings.deliveryPositionTolerance(), result -> {
            if (result == FlightResult.ARRIVED) {
                startDropping();
            } else {
                failAndReturn("机器人无法抵达玩家，物品将退回仓库。");
            }
        });
    }

    private Vector deliveryApproachOffset(Player bot, Player requester) {
        Vector awayFromPlayer = bot.getLocation().toVector()
                .subtract(requester.getLocation().toVector())
                .setY(0);
        return NavigationMath.deliveryOffset(
                awayFromPlayer,
                requester.getLocation().getDirection(),
                settings.dropDistance()
        );
    }

    private void startDropping() {
        ActiveDelivery job = active;
        if (job == null) {
            return;
        }
        job.state(DeliveryState.DROPPING);
        dropNext(0);
    }

    private void dropNext(int index) {
        ActiveDelivery job = active;
        if (job == null) {
            return;
        }
        Player requester = Bukkit.getPlayer(job.request().requester());
        if (requester == null || requester.getWorld() != job.bot().getWorld()) {
            failAndReturn("玩家在交付期间离开，剩余物品将退回仓库。");
            return;
        }
        if (index >= job.cargo().deliverySlots().size()) {
            Messages.success(requester, "配送完成：" + describeOrder(job.request()) + "。机器人正在返航。");
            returnHome(null);
            return;
        }

        int slot = job.cargo().deliverySlots().get(index);
        if (!cargoService.dropSlot(job.bot(), requester, slot)) {
            failAndReturn("物品丢弃被其他插件阻止，剩余物品将退回仓库。");
            return;
        }
        job.incrementDroppedStacks();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (active == job) {
                dropNext(index + 1);
            }
        }, settings.dropIntervalTicks());
    }

    private void travelOrContinue(Location target, Runnable next) {
        ActiveDelivery job = active;
        if (job == null) {
            return;
        }
        if (job.bot().getWorld() == target.getWorld()
                && job.bot().getLocation().distanceSquared(target) <= settings.arrivalDistance() * settings.arrivalDistance()) {
            next.run();
            return;
        }
        flightController.travel(job.bot(), target, result -> {
            if (result == FlightResult.ARRIVED) {
                next.run();
            } else {
                failAndReturn("机器人移动失败，物品将退回仓库。");
            }
        });
    }

    private void failAndReturn(String reason) {
        ActiveDelivery job = active;
        if (job == null) {
            return;
        }
        Player requester = Bukkit.getPlayer(job.request().requester());
        if (requester != null) {
            Messages.error(requester, reason);
        }
        returnHome(reason);
    }

    private void returnHome(String failure) {
        ActiveDelivery job = active;
        if (job == null) {
            return;
        }
        flightController.cancel(false);
        job.state(DeliveryState.RETURNING_HOME);
        flightController.travel(job.bot(), job.home(), result -> {
            boolean snapToHome = result == FlightResult.ARRIVED;
            if (result != FlightResult.ARRIVED && settings.teleportHomeOnFailure()) {
                job.bot().teleport(job.home());
                snapToHome = true;
            }
            finishAtHome(job, failure, snapToHome);
        });
    }

    private void finishAtHome(ActiveDelivery job, String failure, boolean snapToHome) {
        if (active != job) {
            return;
        }
        if (snapToHome) {
            job.bot().teleport(job.home());
        }
        job.bot().setGliding(false);
        int leftovers = cargoService.depositAll(job.bot(), job.home());
        if (leftovers > 0) {
            plugin.getLogger().warning("任务结束后有 " + leftovers + " 个物品无法退入仓库，已保留在机器人背包中。"
                    + (failure == null ? "" : " 原因: " + failure));
        }
        active = null;
        scheduleNext();
    }

    private void scheduleNext() {
        if (!shuttingDown) {
            plugin.getServer().getScheduler().runTask(plugin, this::startNext);
        }
    }

    private String describeOrder(DeliveryRequest request) {
        return request.items().stream()
                .map(item -> item.unit().format(item.requestedUnits(), item.amount())
                        + aliases.display(item.material()))
                .collect(java.util.stream.Collectors.joining("、"));
    }
}
