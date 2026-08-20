package com.haishihua.deliverybot.request;

import com.haishihua.deliverybot.config.PluginSettings;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RequestParser {
    private static final Pattern AMOUNT_AND_ITEM = Pattern.compile("^(\\d+|一)\\s*(个|份|组|盒|箱)?\\s*(.+)$");
    private static final Pattern SINGLE_ITEM = Pattern.compile("^单个\\s*(.+)$");
    private static final Pattern ITEM_SEPARATOR = Pattern.compile("\\s*(?:，|,|、|；|;|以及|还有|和)\\s*");
    private static final List<String> VERBS = List.of("送我", "给我", "拿来", "取", "要");

    private final PluginSettings settings;
    private final ItemResolver aliases;
    private final List<String> prefixes;

    public RequestParser(PluginSettings settings, ItemResolver aliases) {
        this.settings = settings;
        this.aliases = aliases;
        this.prefixes = settings.requestPrefixes().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    public ParseResult parse(String message) {
        String text = message.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        String matchedPrefix = prefixes.stream()
                .filter(prefix -> lower.startsWith(prefix.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
        if (matchedPrefix == null) {
            return ParseResult.ignored();
        }

        String remainder = text.substring(matchedPrefix.length()).trim();
        for (String verb : VERBS) {
            if (remainder.startsWith(verb)) {
                remainder = remainder.substring(verb.length()).trim();
                break;
            }
        }
        remainder = remainder.replaceAll("[。！!，,？?]+$", "").trim();
        if (remainder.isEmpty()) {
            return ParseResult.failure("请求格式：机器人 送我 <数量> <物品>[，<数量> <物品>...]。");
        }

        String[] parts = ITEM_SEPARATOR.split(remainder);
        List<RequestSpec> requests = new java.util.ArrayList<>();
        for (int index = 0; index < parts.length; index++) {
            String part = stripLeadingVerb(parts[index].trim());
            if (part.isEmpty()) {
                return ParseResult.failure("第 " + (index + 1) + " 项为空，请检查分隔符。");
            }
            ParseResult parsed = parseItem(part);
            if (!parsed.successful()) {
                return ParseResult.failure("第 " + (index + 1) + " 项：" + parsed.error());
            }
            requests.add(parsed.request());
        }
        return ParseResult.success(requests);
    }

    private ParseResult parseItem(String remainder) {

        int requestedUnits = 1;
        RequestUnit unit = RequestUnit.ITEM;
        String itemName = remainder;
        Matcher matcher = AMOUNT_AND_ITEM.matcher(remainder);
        if (matcher.matches()) {
            try {
                requestedUnits = matcher.group(1).equals("一") ? 1 : Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException exception) {
                return ParseResult.failure("数量太大或格式不正确。");
            }
            unit = parseUnit(matcher.group(2));
            itemName = matcher.group(3).trim();
        } else {
            Matcher singleMatcher = SINGLE_ITEM.matcher(remainder);
            if (singleMatcher.matches()) {
                itemName = singleMatcher.group(1).trim();
            }
        }

        if (requestedUnits < 1) {
            return ParseResult.failure("请求数量必须大于 0。");
        }

        String resolvedItemName = itemName;
        int resolvedUnits = requestedUnits;
        RequestUnit resolvedUnit = unit;
        return aliases.resolve(resolvedItemName)
                .map(material -> expand(material, resolvedUnits, resolvedUnit))
                .orElseGet(() -> ParseResult.failure("无法识别物品“" + resolvedItemName + "”，请在 item-aliases.yml 中添加别名。"));
    }

    private String stripLeadingVerb(String value) {
        for (String verb : VERBS) {
            if (value.startsWith(verb)) {
                return value.substring(verb.length()).trim();
            }
        }
        return value;
    }

    private ParseResult expand(org.bukkit.Material material, int units, RequestUnit unit) {
        long multiplier = switch (unit) {
            case ITEM -> 1L;
            case STACK -> aliases.maxStackSize(material);
            case BOX -> (long) aliases.maxStackSize(material) * settings.boxSlots();
        };
        long total = multiplier * units;
        if (total < 1 || total > settings.maxRequestAmount()) {
            return ParseResult.failure("换算后为 " + total + " 个；每次请求数量必须在 1 到 "
                    + settings.maxRequestAmount() + " 之间。");
        }
        return ParseResult.success(new RequestSpec(material, (int) total, units, unit));
    }

    private RequestUnit parseUnit(String unit) {
        if (unit == null || unit.equals("个") || unit.equals("份")) {
            return RequestUnit.ITEM;
        }
        if (unit.equals("组")) {
            return RequestUnit.STACK;
        }
        return RequestUnit.BOX;
    }
}
