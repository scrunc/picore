package org.illustro.picore.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reusable list filtering and sorting controller for paginated menus.
 * Chains filter predicates and comparator sorting cleanly.
 */
public class PiFilter<T> {

    private final List<T> masterList;
    private Predicate<T> filter;
    private Comparator<T> comparator;

    public PiFilter(List<T> masterList) {
        this.masterList = masterList != null ? masterList : new ArrayList<>();
    }

    public static <T> PiFilter<T> of(List<T> masterList) {
        return new PiFilter<>(masterList);
    }

    public PiFilter<T> filter(Predicate<T> predicate) {
        this.filter = predicate;
        return this;
    }

    public PiFilter<T> sort(Comparator<T> comparator) {
        this.comparator = comparator;
        return this;
    }

    public List<T> apply() {
        return masterList.stream()
                .filter(filter != null ? filter : t -> true)
                .sorted(comparator != null ? comparator : (a, b) -> 0)
                .toList();
    }
}
