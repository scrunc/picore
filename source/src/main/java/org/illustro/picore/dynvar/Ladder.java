package org.illustro.picore.dynvar;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-column matrix ladder data structure.
 * Maps row indices to lists of column values.
 */
public class Ladder {

    private final String name;
    private final Map<Integer, List<Object>> rows = new ConcurrentHashMap<>();

    public Ladder(String name) {
        this.name = name.toLowerCase();
    }

    public String getName() {
        return name;
    }

    public Map<Integer, List<Object>> getRows() {
        return Collections.unmodifiableMap(rows);
    }

    public int getRowCount() {
        return rows.size();
    }

    public List<Object> getRow(int row) {
        return rows.get(row);
    }

    public void setRow(int row, List<Object> values) {
        if (values != null) {
            rows.put(row, new ArrayList<>(values));
        } else {
            rows.remove(row);
        }
    }

    public int addRow(List<Object> values) {
        int nextIndex = 0;
        while (rows.containsKey(nextIndex)) {
            nextIndex++;
        }
        setRow(nextIndex, values);
        return nextIndex;
    }

    /**
     * Retrieves column value at the specified row.
     * Supports both 1-based (col 1 = first element) and 0-based (col 0 = first element) lookups.
     */
    public Object get(int row, int col) {
        List<Object> rowList = rows.get(row);
        if (rowList == null || rowList.isEmpty()) {
            return null;
        }

        if (col == 0) {
            return rowList.get(0);
        }

        // 1-based lookup: col 1 corresponds to index 0, col 2 to index 1, etc.
        int index = col - 1;
        if (index >= 0 && index < rowList.size()) {
            return rowList.get(index);
        }

        // Fallback to direct 0-based index if within bounds
        if (col >= 0 && col < rowList.size()) {
            return rowList.get(col);
        }

        return null;
    }

    public String getString(int row, int col) {
        Object val = get(row, col);
        return val != null ? String.valueOf(val) : "";
    }

    public void set(int row, int col, Object value) {
        List<Object> rowList = rows.computeIfAbsent(row, r -> new ArrayList<>());
        // Ensure capacity
        int targetIndex = col == 0 ? 0 : col - 1;
        while (rowList.size() <= targetIndex) {
            rowList.add("");
        }
        rowList.set(targetIndex, value);
    }

    /**
     * Retrieves a column value using a numeric threshold/floor lookup.
     * Iterates through sorted row keys and matches the highest row key <= score.
     */
    public Object getFloor(double score, int col) {
        if (rows.isEmpty()) return null;
        List<Integer> sortedKeys = new ArrayList<>(rows.keySet());
        Collections.sort(sortedKeys);

        Integer matchedKey = null;
        for (Integer key : sortedKeys) {
            if (score >= key) {
                matchedKey = key;
            } else {
                break;
            }
        }
        if (matchedKey == null) {
            matchedKey = sortedKeys.get(0); // Fallback to lowest tier
        }
        return get(matchedKey, col);
    }

    public String getStringFloor(double score, int col) {
        Object val = getFloor(score, col);
        return val != null ? String.valueOf(val) : "";
    }
}

