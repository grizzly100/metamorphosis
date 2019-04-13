package org.grizzlytech.metamorphosis.metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.function.BiFunction;

/**
 * A HashMap that supports a callback fallback getter method
 *
 * @param <K> key type
 * @param <V> value type
 */
public class FallbackHashMap<K, V> extends HashMap<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackHashMap.class);

    private BiFunction<HashMap<K, V>, K, V> fallbackGetter;

    public FallbackHashMap(BiFunction<HashMap<K, V>, K, V> fallbackGetter) {
        this.fallbackGetter = fallbackGetter;
    }

    @Override
    public V get(Object key) {
        V result = super.get(key);
        if (result == null) {
            result = fallbackGetter.apply(this, (K) key);
            LOG.info("Fallback for [{}] was [{}]", key, result);
        }
        return result;
    }
}
