package org.grizzlytech.metamorphosis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.function.Function;

/**
 * Utility methods that rely on Unsafe
 */
public class UnsafeUtil {

    private static final Logger LOG = LoggerFactory.getLogger(UnsafeUtil.class);

    /**
     * Swap a static object reference. Can be used to swap private/protected final fields!
     * <p>
     * There are no type safety checks so be careful
     *
     * @param field     the static field whose value is to be swapped
     * @param newObject the replacement object
     * @return the original object
     */
    public static Object staticFieldSwapObject(Field field, Object newObject) {
        Unsafe unsafe = getUnsafe();

        // base represents the object holding the static fields
        // the field will be an offset relative to the base
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        Object oldObject = unsafe.getObject(base, offset);
        unsafe.putObject(base, offset, newObject);

        Function<Object, String> safeClassName = (o) -> ((o != null) ? o.getClass().getName() : "NULL");
        LOG.info("Swapped [{}] for [{}]", safeClassName.apply(oldObject), safeClassName.apply(newObject));

        return oldObject;
    }

    protected static Unsafe getUnsafe() {
        Unsafe instance = null;
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            instance = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            LOG.error("Cannot obtain Unsafe", ex);
        }
        return instance;
    }
}
