package com.haishihua.deliverybot.request;

import java.util.List;

public record ParseResult(boolean addressed, List<RequestSpec> requests, String error) {
    public ParseResult {
        requests = requests == null ? List.of() : List.copyOf(requests);
    }

    public static ParseResult ignored() {
        return new ParseResult(false, List.of(), null);
    }

    public static ParseResult success(RequestSpec request) {
        return success(List.of(request));
    }

    public static ParseResult success(List<RequestSpec> requests) {
        return new ParseResult(true, requests, null);
    }

    public static ParseResult failure(String error) {
        return new ParseResult(true, List.of(), error);
    }

    public RequestSpec request() {
        return requests.isEmpty() ? null : requests.getFirst();
    }

    public boolean successful() {
        return !requests.isEmpty();
    }
}
