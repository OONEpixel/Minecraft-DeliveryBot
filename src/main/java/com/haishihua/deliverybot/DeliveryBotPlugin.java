package com.haishihua.deliverybot;

import com.haishihua.deliverybot.bot.BotEquipmentService;
import com.haishihua.deliverybot.bot.BotManager;
import com.haishihua.deliverybot.bot.leaves.LeavesBotGateway;
import com.haishihua.deliverybot.command.DeliveryBotCommand;
import com.haishihua.deliverybot.config.PluginSettings;
import com.haishihua.deliverybot.crafting.CraftPlanner;
import com.haishihua.deliverybot.crafting.RecipeIndex;
import com.haishihua.deliverybot.crafting.WorkstationScanner;
import com.haishihua.deliverybot.delivery.CargoService;
import com.haishihua.deliverybot.inventory.FulfillmentPlanner;
import com.haishihua.deliverybot.inventory.WarehouseRegistry;
import com.haishihua.deliverybot.job.DeliveryCoordinator;
import com.haishihua.deliverybot.navigation.FlightController;
import com.haishihua.deliverybot.persistence.BotRepository;
import com.haishihua.deliverybot.request.ChatRequestListener;
import com.haishihua.deliverybot.request.ItemAliasRegistry;
import com.haishihua.deliverybot.request.RequestParser;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeliveryBotPlugin extends JavaPlugin {
    private DeliveryCoordinator coordinator;
    private BotEquipmentService equipment;
    private FlightController flightController;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginSettings settings = PluginSettings.load(getConfig());

        BotManager botManager = new BotManager(settings, new BotRepository(this));
        equipment = new BotEquipmentService(this, botManager);
        WarehouseRegistry warehouses = new WarehouseRegistry(this, settings);
        ItemAliasRegistry aliases = new ItemAliasRegistry(this);

        RecipeIndex recipes = new RecipeIndex();
        recipes.reload();
        CraftPlanner craftPlanner = new CraftPlanner(recipes, settings.craftingMaxDepth());
        FulfillmentPlanner fulfillmentPlanner = new FulfillmentPlanner(
                warehouses,
                new WorkstationScanner(settings),
                craftPlanner
        );

        CargoService cargoService = new CargoService(warehouses, equipment);
        flightController = new FlightController(this, settings, equipment);
        coordinator = new DeliveryCoordinator(
                this,
                settings,
                botManager,
                fulfillmentPlanner,
                cargoService,
                flightController,
                aliases
        );

        LeavesBotGateway leaves = new LeavesBotGateway();
        DeliveryBotCommand commandHandler = new DeliveryBotCommand(
                this,
                settings,
                botManager,
                equipment,
                leaves,
                warehouses,
                recipes,
                aliases,
                coordinator
        );
        PluginCommand command = getCommand("deliverybot");
        if (command == null) {
            throw new IllegalStateException("plugin.yml 未注册 deliverybot 命令");
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getServer().getPluginManager().registerEvents(equipment, this);
        getServer().getPluginManager().registerEvents(
                new ChatRequestListener(this, settings, new RequestParser(settings, aliases), coordinator),
                this
        );
        equipment.start();

        String runtime = getServer().getVersion();
        if (!runtime.toLowerCase(java.util.Locale.ROOT).contains("leaves")) {
            getLogger().warning("当前服务端版本信息中未检测到 Leaves；创建假人的 /bot 集成功能可能不可用。");
        }
        getLogger().info("DeliveryBot 已启用。使用 /deliverybot help 完成机器人和仓库绑定。");
    }

    @Override
    public void onDisable() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
        if (flightController != null) {
            flightController.cancel(false);
        }
        if (equipment != null) {
            equipment.stop();
        }
    }
}
