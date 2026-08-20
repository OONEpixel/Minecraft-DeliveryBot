package com.haishihua.deliverybot.request;

public enum RequestUnit {
    ITEM("个"),
    STACK("组"),
    BOX("盒");

    private final String display;

    RequestUnit(String display) {
        this.display = display;
    }

    public String format(int units, int totalItems) {
        if (this == ITEM) {
            return totalItems + " 个";
        }
        return units + " " + display + "（共 " + totalItems + " 个）";
    }
}
