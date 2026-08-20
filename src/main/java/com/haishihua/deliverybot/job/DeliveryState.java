package com.haishihua.deliverybot.job;

public enum DeliveryState {
    IDLE,
    PLANNING,
    MOVING_TO_WAREHOUSE,
    MOVING_TO_WORKSTATION,
    CRAFTING,
    MOVING_TO_PLAYER,
    DROPPING,
    RETURNING_HOME
}
