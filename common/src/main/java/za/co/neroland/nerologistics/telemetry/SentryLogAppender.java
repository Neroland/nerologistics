package za.co.neroland.nerologistics.telemetry;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Log4j2 appender that feeds {@link NeroLogisticsTelemetry}. Minecraft routes essentially every failure
 * through log4j, so listening on the root logger catches NeroLogistics failures without mixins.
 * Filtering (NeroLogistics-only), de-dup, rate-limiting and PII scrubbing all happen in
 * {@link NeroLogisticsTelemetry}; this only selects candidate log events.
 */
final class SentryLogAppender extends AbstractAppender {

    SentryLogAppender() {
        super("NeroLogisticsSentry", null, null, false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        if (!NeroLogisticsTelemetry.isActive()) {
            return;
        }
        Level level = event.getLevel();
        if (!level.isMoreSpecificThan(Level.ERROR)) {
            return;
        }
        Throwable thrown = event.getThrown();
        if (thrown != null) {
            if (NeroLogisticsTelemetry.touchesNeroLogistics(thrown)) {
                NeroLogisticsTelemetry.capture(thrown);
            }
        } else if (level == Level.FATAL) {
            String message = event.getMessage() == null ? null : event.getMessage().getFormattedMessage();
            if (message != null && message.contains("za.co.neroland.nerologistics")) {
                NeroLogisticsTelemetry.captureMessage(message);
            }
        }
    }
}
