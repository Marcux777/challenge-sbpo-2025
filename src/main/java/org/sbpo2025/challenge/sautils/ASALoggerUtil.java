package org.sbpo2025.challenge.sautils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ASALoggerUtil {
    private static final Logger log = LoggerFactory.getLogger("ASA");

    public static void debug(String msg, Object... args) {
        if (log.isDebugEnabled()) {
            log.debug(msg, args);
        }
    }

    public static void info(String msg, Object... args) {
        log.info(msg, args);
    }

    public static void warn(String msg, Object... args) {
        log.warn(msg, args);
    }

    public static void error(String msg, Object... args) {
        log.error(msg, args);
    }
}
