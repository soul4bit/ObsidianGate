package ru.mcrpg.forgeauth.server;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.AbstractFilterable;

final class BiblioCraftWarningFilter extends AbstractFilter {

    private static final Pattern ALTERNATIVE_PREFIX_WARNING = Pattern.compile(
        "^Potentially Dangerous alternative prefix `minecraft` for name `([^`]+)`, expected `bibliocraft`\\."
    );

    private static final Set<String> BIBLIOCRAFT_TILE_NAMES = new HashSet<String>(Arrays.asList(
        "armorstand",
        "bell",
        "bibliolight",
        "bookcase",
        "case",
        "clipboard",
        "clock",
        "cookiejar",
        "desk",
        "dinnerplate",
        "discrack",
        "fancysign",
        "fancyworkbench",
        "framedchest",
        "furniturepaneler",
        "label",
        "mapframe",
        "markerpole",
        "paintingframeborderless",
        "paintingframefancy",
        "paintingframeflat",
        "paintingframemiddle",
        "paintingframesimple",
        "paintingpress",
        "potionshelf",
        "printingpress",
        "seat",
        "shelf",
        "swordpedestal",
        "table",
        "toolrack",
        "typewriter",
        "typesettingtable"
    ));

    private static volatile boolean installed;

    static void install() {
        if (installed) {
            return;
        }

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        BiblioCraftWarningFilter filter = new BiblioCraftWarningFilter();
        filter.start();
        configuration.addFilter(filter);
        configuration.getRootLogger().addFilter(filter);
        LoggerConfig fmlLogger = configuration.getLoggerConfig("FML");
        if (fmlLogger != configuration.getRootLogger()) {
            fmlLogger.addFilter(filter);
        }
        for (Appender appender : configuration.getAppenders().values()) {
            if (appender instanceof AbstractFilterable) {
                ((AbstractFilterable) appender).addFilter(filter);
            }
        }
        context.updateLoggers();
        installed = true;
    }

    @Override
    public Filter.Result filter(LogEvent event) {
        if (event == null || event.getMessage() == null) {
            return Filter.Result.NEUTRAL;
        }

        String loggerName = event.getLoggerName();
        String message = event.getMessage().getFormattedMessage();
        if (shouldSuppress(loggerName, event.getLevel(), message)) {
            return Filter.Result.DENY;
        }

        return Filter.Result.NEUTRAL;
    }

    static boolean shouldSuppress(String loggerName, Level level, String message) {
        if (level != Level.WARN || message == null) {
            return false;
        }

        Matcher matcher = ALTERNATIVE_PREFIX_WARNING.matcher(message);
        if (!matcher.find()) {
            return false;
        }

        String name = matcher.group(1).toLowerCase(Locale.ROOT);
        return BIBLIOCRAFT_TILE_NAMES.contains(name);
    }

    private BiblioCraftWarningFilter() {
        super(Filter.Result.NEUTRAL, Filter.Result.NEUTRAL);
    }
}
