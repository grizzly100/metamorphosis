package org.grizzlytech.metamorphosis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * An Index that supports the retrieval of key collisions.
 * <p>
 * This class is used to help find duplicates.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class Index<K, V> extends HashMap<K, List<V>> {

    private static final Logger LOG = LoggerFactory.getLogger(Index.class);

    /**
     * Insert a new Key, Value pair into the Index.
     * Values are held in a List, and if the key already exists, the list will be appended to.
     * Duplicate values for the same key are ignored
     *
     * @param k the key
     * @param v the value
     */
    public void insert(K k, V v) {
        List<V> entries = get(k);
        // Create a new list if this is the first value indexed against the key
        if (entries == null) {
            entries = getListFactory().get();
            put(k, entries);
        }
        // Add the new value (assuming it does not already exist)
        if (!entries.contains(v)) {
            entries.add(v);
            if (LOG.isDebugEnabled() && entries.size() > 1) {
                LOG.debug("Key {} has {} entries", k, entries.size());
            }
        }
    }

    public void retract(K k, V v) {
        List<V> entries = get(k);
        if ((entries != null) && entries.contains(v)) {
            entries.remove(v);
        } else {
            LOG.error("cannot retract k [{}] and v [{}]", k, v);
        }
    }

    /**
     * @return all values that collided (i.e, two of more values had the same key)
     */
    public List<V> getCollisions() {
        // Filter out any sub-lists that do not have collisions
        // Use flatMap to remove the grouping structure
        return values().stream().filter(v -> v.size() > 1).flatMap(Collection::stream)
                .collect(Collectors.toCollection(getListFactory()));
    }

    /**
     * @return all values that collided (i.e, two of more values had the same key), grouped (as lists) by key
     */
    public List<List<V>> getGroupedCollisions() {
        // Filter out any sub-lists that do not have collisions
        return values().stream().filter(v -> v.size() > 1)
                .collect(Collectors.toCollection(getGroupedListFactory()));
    }

    /**
     * @return Collection class to collect lists into
     */
    private Supplier<List<V>> getListFactory() {
        return LinkedList::new;
    }

    /**
     * @return Collection class to collect lists of lists into
     */
    private Supplier<List<List<V>>> getGroupedListFactory() {
        return LinkedList::new;
    }
}
