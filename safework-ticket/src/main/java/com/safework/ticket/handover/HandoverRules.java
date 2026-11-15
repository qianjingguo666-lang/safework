package com.safework.ticket.handover;

import java.util.List;
import java.util.Set;

public final class HandoverRules {
    private static final Set<String> ITEMS = Set.of("siteCondition", "unfinished", "recheck");

    private HandoverRules() {}

    public static boolean allChecked(List<String> checked) {
        return checked != null && checked.size() == ITEMS.size() && checked.containsAll(ITEMS);
    }
}
