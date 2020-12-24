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

    private boolean logFallbacks = true;

    public FallbackHashMap(BiFunction<HashMap<K, V>, K, V> fallbackGetter) {
        this.fallbackGetter = fallbackGetter;
    }

    public FallbackHashMap(V fallbackValue) {
        this((m, k) -> fallbackValue);
    }

    @Override
    public V get(Object key) {
        V result = super.get(key);
        if (result == null) {
            // Fallback
            result = fallbackGetter.apply(this, (K) key);
            if (logFallbacks) {
                LOG.info("Map fallback [{}] == [{}]", key, result);
            }
        }

        return result;
    }

    public void setLogFallbacks(boolean logFallbacks) {
        this.logFallbacks = logFallbacks;
    }
}
