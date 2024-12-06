package me.polymorphicagent.crystalspotlights;

import java.util.*;

public class MultiMap<K, V> {
    private final Map<K, List<V>> map = new HashMap<>();

    public void put(K key, V value) {
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    public List<V> get(K key) {
        return map.getOrDefault(key, Collections.emptyList());
    }

    public V getLast(K key) {
        return map.get(key).get(map.get(key).size()-1);
    }

    public List<V> entrySet() {
        List<V> set = new ArrayList<>();
        for(Map.Entry<K, List<V>> entry : map.entrySet()) {
            set.addAll(entry.getValue());
        }
        return set;
    }

    public boolean remove(K key, V value) {
        List<V> values = map.get(key);
        if (values != null) {
            boolean removed = values.remove(value);
            if (values.isEmpty()) {
                map.remove(key);
            }
            return removed;
        }
        return false;
    }

    public void remove(K key) {
        if(key == null) return;
        map.remove(key);
    }

    public Map<K, List<V>> asMap() {
        return map;
    }
}

