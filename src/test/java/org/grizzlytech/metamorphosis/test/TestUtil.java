package org.grizzlytech.metamorphosis.test;

import org.grizzlytech.metamorphosis.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

import java.lang.reflect.Field;

public class TestUtil {

    public static void enableDebug(String loggerClassName) {
        Logger log = LoggerFactory.getLogger(loggerClassName);

        if (log instanceof SimpleLogger) {
            enableDebug((SimpleLogger) log);
        } else {
            log.error("Unable to enable debugging for [{}]", loggerClassName);
        }
    }

    private static void enableDebug(SimpleLogger log) {
        final String CURRENT_LOG_LEVEL_FIELD = "currentLogLevel"; // int
        final int LOG_LEVEL_DEBUG = 10;
        try {
            Field f = log.getClass().getDeclaredField(CURRENT_LOG_LEVEL_FIELD);
            f.setAccessible(true);
            f.set(log, LOG_LEVEL_DEBUG);
            log.debug("Debug enabled");
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            log.error("Unable to access/set {} {}", CURRENT_LOG_LEVEL_FIELD, ex);
        }
    }

}
