package com.haishihua.deliverybot.inventory;

import java.util.Optional;

public record PlanningResult(FulfillmentPlan plan, String failure) {
    public static PlanningResult success(FulfillmentPlan plan) {
        return new PlanningResult(plan, null);
    }

    public static PlanningResult failure(String failure) {
        return new PlanningResult(null, failure);
    }

    public boolean successful() {
        return plan != null;
    }

    public Optional<FulfillmentPlan> optionalPlan() {
        return Optional.ofNullable(plan);
    }
}
