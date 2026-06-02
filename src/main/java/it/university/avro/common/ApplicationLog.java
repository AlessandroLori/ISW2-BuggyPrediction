package it.university.avro.common;

import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class ApplicationLog {

    private static final Logger LOGGER = Logger.getLogger("it.university.avro");

    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.INFO);

        for (Handler handler : LOGGER.getHandlers()) {
            LOGGER.removeHandler(handler);
        }

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(new PlainConsoleFormatter());

        LOGGER.addHandler(consoleHandler);
    }

    private ApplicationLog() {
    }

    public static void info(final String message) {
        LOGGER.info(message);
    }

    public static void warning(final String message) {
        LOGGER.warning(message);
    }

    public static void error(final String message, final Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
    }

    public static void infof(final String format, final Object... args) {
        infof(Locale.ROOT, format, args);
    }

    public static void infof(final Locale locale, final String format, final Object... args) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info(String.format(locale, format, args));
        }
    }

    private static final class PlainConsoleFormatter extends Formatter {

        @Override
        public String format(final LogRecord logRecord) {
            String message = logRecord.getMessage();
            if (message.endsWith(System.lineSeparator()) || message.endsWith("\n")) {
                return message;
            }
            return message + System.lineSeparator();
        }
    }
}
