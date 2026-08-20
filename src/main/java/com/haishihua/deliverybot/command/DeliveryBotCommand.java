package com.haishihua.deliverybot.command;

import com.haishihua.deliverybot.bot.BotEquipmentService;
import com.haishihua.deliverybot.bot.BotManager;
import com.haishihua.deliverybot.bot.leaves.LeavesBotGateway;
import com.haishihua.deliverybot.config.Messages;
import com.haishihua.deliverybot.config.PluginSettings;
import com.haishihua.deliverybot.crafting.RecipeIndex;
import com.haishihua.deliverybot.inventory.WarehouseRegistry;
import com.haishihua.deliverybot.job.DeliveryCoordinator;
import com.haishihua.deliverybot.request.DeliveryRequest;
import com.haishihua.deliverybot.request.ItemAliasRegistry;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DeliveryBotCommand implements CommandExecutor, TabCompleter {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final PluginSettings settings;
    private final BotManager botManager;
    private final BotEquipmentService equipment;
    private final LeavesBotGateway leaves;
    private final WarehouseRegistry warehouses;
    private final RecipeIndex recipes;
    private final ItemAliasRegistry aliases;
    private final DeliveryCoordinator coordinator;

    public DeliveryBotCommand(
            org.bukkit.plugin.java.JavaPlugin plugin,
            PluginSettings settings,
            BotManager botManager,
            BotEquipmentService equipment,
            LeavesBotGateway leaves,
            WarehouseRegistry warehouses,
            RecipeIndex recipes,
            ItemAliasRegistry aliases,
            DeliveryCoordinator coordinator
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.botManager = botManager;
        this.equipment = equipment;
        this.leaves = leaves;
        this.warehouses = warehouses;
        this.recipes = recipes;
        this.aliases = aliases;
        this.coordinator = coordinator;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "bind" -> bind(sender, args);
            case "unbind" -> unbind(sender);
            case "recover" -> recover(sender, args);
            case "sethome" -> setHome(sender);
            case "warehouse" -> warehouse(sender, args);
            case "status" -> status(sender);
            case "cancel" -> cancel(sender);
            case "reloadrecipes" -> reloadRecipes(sender);
            case "request" -> request(sender, args);
            default -> Messages.error(sender, "未知子命令，使用 /deliverybot help 查看帮助。");
        }
        return true;
    }

    private void help(CommandSender sender) {
        Messages.info(sender, "/deliverybot create <名称> - 在当前位置创建并绑定 Leaves 假人");
        Messages.info(sender, "/deliverybot bind <在线假人名> confirm - 确认绑定已存在的 Leaves 假人");
        Messages.info(sender, "/deliverybot unbind - 解除绑定并恢复误绑玩家状态");
        Messages.info(sender, "/deliverybot recover <在线玩家名> - 单独恢复误绑玩家状态");
        Messages.info(sender, "/deliverybot sethome - 将机器人当前位置设为原位");
        Messages.info(sender, "/deliverybot warehouse add|remove - 登记准星所指容器");
        Messages.info(sender, "/deliverybot warehouse scan [半径] - 批量登记附近容器");
        Messages.info(sender, "/deliverybot request <数量> <物品> - 手动测试配送");
        Messages.info(sender, "/deliverybot status | cancel | reloadrecipes");
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.error(sender, "create 必须由游戏内玩家执行，以确定假人的世界和位置。");
            return;
        }
        String name = args.length >= 2 ? args[1] : settings.botName();
        if (!leaves.validName(name)) {
            Messages.error(sender, "假人名称必须是 3-16 位英文字母、数字或下划线。");
            return;
        }
        if (Bukkit.getPlayerExact(name) != null) {
            Messages.error(sender, "名称 " + name + " 已被在线玩家或假人占用；请改名或使用 bind。");
            return;
        }
        if (!leaves.createAtSender(sender, name)) {
            Messages.error(sender, "Leaves /bot 命令执行失败；请确认假人功能已启用且你有 bukkit.command.bot 权限。");
            return;
        }

        Messages.info(sender, "已请求 Leaves 创建 " + name + "，正在等待实体上线……");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player candidate = Bukkit.getPlayerExact(name);
            if (candidate == null) {
                Messages.error(sender, "没有找到新假人；请检查 Leaves 控制台输出，也可稍后用 bind 手动绑定。");
                return;
            }
            botManager.bind(candidate);
            equipment.ensureEquipment(candidate);
            Messages.success(sender, "已创建并绑定机器人 " + candidate.getName() + "。当前位置已记录为原位。");
        }, 10L);
    }

    private void bind(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Messages.error(sender, "用法：/deliverybot bind <在线假人名>");
            return;
        }
        Player candidate = Bukkit.getPlayerExact(args[1]);
        if (candidate == null) {
            Messages.error(sender, "没有找到在线玩家或假人 " + args[1] + "。");
            return;
        }
        if (sender instanceof Player player && candidate.getUniqueId().equals(player.getUniqueId())) {
            Messages.error(sender, "拒绝把命令执行者本人绑定为机器人；请填写 Leaves 假人的名称。");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            Messages.error(sender, "Paper API 无法可靠区分真人与 Leaves 假人。确认目标确实是假人后，请执行：/deliverybot bind "
                    + candidate.getName() + " confirm");
            return;
        }
        botManager.bind(candidate);
        equipment.ensureEquipment(candidate);
        Messages.success(sender, "已绑定 " + candidate.getName() + "，并把它的当前位置设为原位。");
    }

    private void unbind(CommandSender sender) {
        Player bound = botManager.bot().orElse(null);
        if (!botManager.isBound()) {
            Messages.info(sender, "当前没有机器人绑定记录。");
            return;
        }

        coordinator.prepareForUnbind();
        try {
            botManager.unbind();
        } catch (IllegalStateException exception) {
            Messages.error(sender, exception.getMessage());
            return;
        }
        if (bound != null) {
            equipment.release(bound);
        }

        if (bound == null) {
            Messages.success(sender, "已删除离线目标的绑定记录；该玩家下次上线后不会再被识别为机器人。");
        } else {
            Messages.success(sender, "已解除 " + bound.getName() + " 的机器人状态，并移除插件生成的鞘翅和烟花。");
        }
        Messages.info(sender, "为避免误搬真人背包，解绑不会自动退库；目标背包中的普通物品请人工检查。");
        Messages.info(sender, "注意：绑定时被覆盖的原胸甲和副手不在插件备份中，只能从服务器备份恢复。");
    }

    private void recover(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Messages.error(sender, "用法：/deliverybot recover <在线玩家名>");
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            Messages.error(sender, "玩家必须在线才能恢复实体状态。");
            return;
        }
        equipment.release(player);
        Messages.success(sender, "已恢复 " + player.getName() + " 的无敌、拾取和移动状态，并移除插件标记装备。");
        Messages.info(sender, "该命令不修改绑定记录，也不会移动普通背包物品。");
    }

    private void setHome(CommandSender sender) {
        Player bot = botManager.bot().orElse(null);
        if (bot == null) {
            Messages.error(sender, "机器人未绑定或不在线。");
            return;
        }
        botManager.setHome(bot.getLocation());
        Messages.success(sender, "已把机器人当前位置保存为原位。");
    }

    private void warehouse(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.error(sender, "仓库登记必须由游戏内玩家看向容器执行。");
            return;
        }
        if (args.length < 2) {
            Messages.error(sender, "用法：/deliverybot warehouse add|remove|scan [半径]");
            return;
        }
        if (args[1].equalsIgnoreCase("scan")) {
            scanWarehouses(player, args);
            return;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            Messages.error(sender, "6 格内没有找到你指向的方块。");
            return;
        }

        if (args[1].equalsIgnoreCase("add")) {
            switch (warehouses.add(target)) {
                case ADDED -> Messages.success(sender, "已登记 " + target.getType() + "，当前共 " + warehouses.size() + " 个仓库点。");
                case ALREADY_REGISTERED -> Messages.info(sender, "这个容器已经登记过了。");
                case TYPE_NOT_ALLOWED -> Messages.error(sender, "该容器类型未在 config.yml 的 allowed-containers 中启用。");
                case NOT_A_CONTAINER -> Messages.error(sender, "你指向的方块不是可读取的容器。");
            }
        } else if (args[1].equalsIgnoreCase("remove")) {
            if (warehouses.remove(target)) {
                Messages.success(sender, "已移除仓库点，当前剩余 " + warehouses.size() + " 个。");
            } else {
                Messages.error(sender, "这个方块没有登记为仓库。");
            }
        } else {
            Messages.error(sender, "用法：/deliverybot warehouse add|remove|scan [半径]");
        }
    }

    private void scanWarehouses(Player player, String[] args) {
        int maximumRadius = settings.warehouseBulkRegisterMaxRadius();
        int radius = Math.min(settings.warehouseBulkRegisterRadius(), maximumRadius);
        if (args.length >= 3) {
            try {
                radius = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                Messages.error(player, "扫描半径必须是整数。");
                return;
            }
        }
        if (radius < 1 || radius > maximumRadius) {
            Messages.error(player, "扫描半径必须在 1 到 " + maximumRadius + " 之间。");
            return;
        }

        WarehouseRegistry.BulkAddResult result = warehouses.addNearby(player.getLocation(), radius);
        Messages.success(player, "批量扫描完成：发现 " + result.foundContainers()
                + " 个容器，新增 " + result.addedContainers()
                + " 个，已登记 " + result.alreadyRegistered()
                + " 个。当前仓库点共 " + warehouses.size() + " 个。");
    }

    private void status(CommandSender sender) {
        String bot = botManager.record().map(record -> record.name() + " / " + record.uuid()).orElse("未绑定");
        Messages.info(sender, "机器人：" + bot);
        Messages.info(sender, "在线：" + botManager.bot().isPresent() + "，状态：" + coordinator.state()
                + "，移动：" + coordinator.movementMode().displayName()
                + "，排队：" + coordinator.queuedJobs() + "，仓库点：" + warehouses.size());
        botManager.home().ifPresent(home -> Messages.info(sender,
                "原位：" + home.getWorld().getName() + " " + home.getBlockX() + " " + home.getBlockY() + " " + home.getBlockZ()));
    }

    private void cancel(CommandSender sender) {
        if (coordinator.cancelActive("任务被管理员取消，未交付物品将退回仓库。")) {
            Messages.success(sender, "已取消当前任务并命令机器人返航。");
        } else {
            Messages.info(sender, "当前没有正在执行的任务。");
        }
    }

    private void reloadRecipes(CommandSender sender) {
        recipes.reload();
        aliases.reload();
        Messages.success(sender, "已重建配方索引并重新加载 item-aliases.yml。");
    }

    private void request(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.error(sender, "测试请求必须由游戏内玩家发起。");
            return;
        }
        if (args.length < 3) {
            Messages.error(sender, "用法：/deliverybot request <数量> <物品>");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            Messages.error(sender, "数量必须是整数。");
            return;
        }
        if (amount < 1 || amount > settings.maxRequestAmount()) {
            Messages.error(sender, "数量必须在 1 到 " + settings.maxRequestAmount() + " 之间。");
            return;
        }
        String itemName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        aliases.resolve(itemName).ifPresentOrElse(
                material -> coordinator.enqueue(new DeliveryRequest(player.getUniqueId(), material, amount)),
                () -> Messages.error(sender, "无法识别物品“" + itemName + "”。")
        );
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return filter(List.of("help", "create", "bind", "unbind", "recover", "sethome", "warehouse", "status", "cancel", "reloadrecipes", "request"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("warehouse")) {
            return filter(List.of("add", "remove", "scan"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("warehouse")
                && args[1].equalsIgnoreCase("scan")) {
            return filter(List.of(
                    Integer.toString(Math.min(settings.warehouseBulkRegisterRadius(), settings.warehouseBulkRegisterMaxRadius())),
                    Integer.toString(settings.warehouseBulkRegisterMaxRadius())
            ), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bind")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
