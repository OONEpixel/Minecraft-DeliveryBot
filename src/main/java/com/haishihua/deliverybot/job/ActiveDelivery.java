package com.haishihua.deliverybot.job;

import com.haishihua.deliverybot.delivery.CargoManifest;
import com.haishihua.deliverybot.inventory.FulfillmentPlan;
import com.haishihua.deliverybot.request.DeliveryRequest;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class ActiveDelivery {
    private final DeliveryRequest request;
    private final FulfillmentPlan plan;
    private final CargoManifest cargo;
    private final Player bot;
    private final Location home;
    private DeliveryState state;
    private int droppedStacks;

    public ActiveDelivery(
            DeliveryRequest request,
            FulfillmentPlan plan,
            CargoManifest cargo,
            Player bot,
            Location home
    ) {
        this.request = request;
        this.plan = plan;
        this.cargo = cargo;
        this.bot = bot;
        this.home = home.clone();
        this.state = DeliveryState.PLANNING;
    }

    public DeliveryRequest request() {
        return request;
    }

    public FulfillmentPlan plan() {
        return plan;
    }

    public CargoManifest cargo() {
        return cargo;
    }

    public Player bot() {
        return bot;
    }

    public Location home() {
        return home.clone();
    }

    public DeliveryState state() {
        return state;
    }

    public void state(DeliveryState state) {
        this.state = state;
    }

    public int droppedStacks() {
        return droppedStacks;
    }

    public void incrementDroppedStacks() {
        droppedStacks++;
    }
}
