package za.co.neroland.nerologistics.telemetry;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;

import za.co.neroland.nerolandcore.platform.Services;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;

/**
 * Crash/error reporting for NeroLogistics via Sentry (EU ingest), matching the rest of the Neroland
 * family (cf. {@code NeroTechTelemetry} / {@code NerospaceTelemetry}). Built for the CurseForge
 * disclosure rule and POPIA/GDPR data-minimisation:
 *
 * <ul>
 *   <li><b>Opt-out:</b> gated on {@code telemetryEnabled} in {@link NeroLogisticsConfig} (default ON,
 *       client-local, disclosed). Set it false to stop reporting (takes effect on restart).</li>
 *   <li><b>NeroLogistics errors only:</b> {@code beforeSend} drops any event whose stack trace does not
 *       touch {@code za.co.neroland.nerologistics}.</li>
 *   <li><b>No personal data:</b> no IP, no hostname, no user identity; OS-account names are scrubbed
 *       from file paths. Payload: stack trace + mod/loader/OS/Java versions.</li>
 *   <li><b>Bounded volume:</b> per-session de-duplication + a hard cap of
 *       {@value #MAX_EVENTS_PER_SESSION} events per session.</li>
 * </ul>
 *
 * <p>{@link #init()} is called once per loader at bootstrap and reads loader facts through Core's
 * {@link Services#PLATFORM}. Full disclosure text: {@code PRIVACY.md}.
 */
public final class NeroLogisticsTelemetry {

    /**
     * Sentry DSN — a public client key (write-only ingest), safe to ship in the jar. NeroLogistics'
     * own Sentry project (EU region, {@code de.sentry.io}). If blanked, telemetry initialises to a
     * no-op so events are never sent to the wrong project.
     */
    private static final String DSN =
            "https://a3b4fb45c17c96d5b2787d8eb9bf9430@o4511183823241216.ingest.de.sentry.io/4511650772353104";

    /** Mirrors {@code mod_version} in gradle.properties (a public version string for the release tag). */
    private static final String MOD_VERSION = "0.0.1-alpha.1";
    private static final String PACKAGE_MARKER = "za.co.neroland.nerologistics";
    private static final int MAX_EVENTS_PER_SESSION = 10;
    private static final Pattern USER_PATH =
            Pattern.compile("(?i)(?:[A-Z]:)?[/\\\\](?:Users|home)[/\\\\][^/\\\\\\s:;,'\"]+");

    private static volatile boolean active;
    private static final AtomicInteger eventsSent = new AtomicInteger();
    private static final Set<String> seenFingerprints = ConcurrentHashMap.newKeySet();
    private static SentryLogAppender appender;

    private NeroLogisticsTelemetry() {
    }

    /** Called once per loader at bootstrap (after {@code NeroLogisticsCommon.init()} loads the config). */
    public static void init() {
        if (!NeroLogisticsConfig.telemetryEnabled()) {
            return;
        }
        start();
    }

    private static synchronized void start() {
        if (active) {
            return;
        }
        if (DSN.isBlank()) {
            NeroLogisticsCommon.LOGGER.info("[NeroLogistics] Telemetry is enabled but no Sentry DSN is "
                    + "configured; error reporting is inactive.");
            return;
        }
        boolean dev = Services.PLATFORM.isDevelopmentEnvironment();
        Sentry.init(options -> {
            options.setDsn(DSN);
            options.setRelease("nerologistics@" + MOD_VERSION);
            options.setEnvironment(dev ? "development" : environmentOf(MOD_VERSION));
            options.setSendDefaultPii(false);          // POPIA/GDPR: never the sender's IP/identity
            options.setAttachServerName(false);        // hostname is identifying
            options.setEnableUncaughtExceptionHandler(true);
            options.setEnableAutoSessionTracking(false);
            options.setBeforeSend((event, hint) -> filterAndScrub(event));
        });
        Sentry.configureScope(scope -> {
            scope.setTag("loader", Services.PLATFORM.getPlatformName().toLowerCase(Locale.ROOT));
            scope.setTag("dist", Services.PLATFORM.isClient() ? "client" : "dedicated_server");
            scope.setTag("runtime", dev ? "development" : "production");
            scope.setTag("cfg_player_attribution",
                    Boolean.toString(NeroLogisticsConfig.perPlayerThroughputAttribution()));
        });
        Sentry.startSession();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Sentry.endSession();
                Sentry.flush(2000L);
            } catch (RuntimeException ignored) {
                // best-effort flush on shutdown
            }
        }, "nerologistics-sentry-shutdown"));
        if (appender == null) {
            appender = new SentryLogAppender();
            appender.start();
            ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(appender);
        }
        active = true;
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] Telemetry enabled (anonymous error reports, EU "
                + "servers; opt out via telemetryEnabled=false in config/nerologistics.properties).");
    }

    /** A non-identifying breadcrumb that rides along with the next error report. No-op when off. */
    public static void breadcrumb(String category, String message) {
        if (!active) {
            return;
        }
        Breadcrumb crumb = new Breadcrumb();
        crumb.setType("default");
        crumb.setCategory(category);
        crumb.setLevel(SentryLevel.INFO);
        crumb.setMessage(scrub(message));
        Sentry.addBreadcrumb(crumb);
    }

    private static String environmentOf(String version) {
        String v = version.toLowerCase(Locale.ROOT);
        if (v.contains("-alpha")) {
            return "alpha";
        }
        if (v.contains("-beta")) {
            return "beta";
        }
        return "production";
    }

    static boolean isActive() {
        return active;
    }

    /** True if any frame of the throwable (or its causes/suppressed) is NeroLogistics code. */
    static boolean touchesNeroLogistics(Throwable t) {
        int depth = 0;
        while (t != null && depth++ < 16) {
            for (StackTraceElement el : t.getStackTrace()) {
                if (el.getClassName().startsWith(PACKAGE_MARKER)) {
                    return true;
                }
            }
            for (Throwable s : t.getSuppressed()) {
                if (touchesNeroLogistics(s)) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    static void capture(Throwable t) {
        if (!active || t == null) {
            return;
        }
        Sentry.captureException(t);
    }

    /** Capture a handled exception if it is still clearly from NeroLogistics code. */
    public static void captureHandledException(Throwable t) {
        if (t != null && touchesNeroLogistics(t)) {
            capture(t);
        }
    }

    static void captureMessage(String message) {
        if (!active) {
            return;
        }
        String scrubbed = scrub(message);
        if (scrubbed.length() > 4000) {
            scrubbed = scrubbed.substring(0, 4000) + "…[truncated]";
        }
        SentryEvent event = new SentryEvent();
        event.setLevel(SentryLevel.FATAL);
        Message msg = new Message();
        msg.setFormatted(scrubbed);
        event.setMessage(msg);
        Sentry.captureEvent(event);
    }

    private static SentryEvent filterAndScrub(SentryEvent event) {
        if (!isNeroLogisticsRelated(event)) {
            return null;
        }
        String fingerprint = fingerprintOf(event);
        if (!seenFingerprints.add(fingerprint)) {
            return null;
        }
        if (eventsSent.incrementAndGet() > MAX_EVENTS_PER_SESSION) {
            return null;
        }
        event.setUser(null);
        event.setServerName(null);
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (SentryException ex : exceptions) {
                String value = ex.getValue();
                if (value != null) {
                    ex.setValue(scrub(value));
                }
                SentryStackTrace st = ex.getStacktrace();
                List<SentryStackFrame> frames = st == null ? null : st.getFrames();
                if (frames != null) {
                    for (SentryStackFrame frame : frames) {
                        frame.setAbsPath(null);
                    }
                }
            }
        }
        Message message = event.getMessage();
        if (message != null && message.getFormatted() != null) {
            message.setFormatted(scrub(message.getFormatted()));
        }
        return event;
    }

    private static boolean isNeroLogisticsRelated(SentryEvent event) {
        Throwable t = event.getThrowable();
        if (t != null && touchesNeroLogistics(t)) {
            return true;
        }
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (SentryException ex : exceptions) {
                SentryStackTrace st = ex.getStacktrace();
                List<SentryStackFrame> frames = st == null ? null : st.getFrames();
                if (frames == null) {
                    continue;
                }
                for (SentryStackFrame frame : frames) {
                    String module = frame.getModule();
                    if (module != null && module.startsWith(PACKAGE_MARKER)) {
                        return true;
                    }
                }
            }
        }
        Message message = event.getMessage();
        String formatted = message == null ? null : message.getFormatted();
        return formatted != null && formatted.contains(PACKAGE_MARKER);
    }

    private static String fingerprintOf(SentryEvent event) {
        StringBuilder sb = new StringBuilder();
        List<SentryException> exceptions = event.getExceptions();
        Message message = event.getMessage();
        if (exceptions != null) {
            for (SentryException ex : exceptions) {
                sb.append(ex.getType()).append('|');
                SentryStackTrace st = ex.getStacktrace();
                List<SentryStackFrame> frames = st == null ? null : st.getFrames();
                if (frames != null) {
                    for (SentryStackFrame frame : frames) {
                        String module = frame.getModule();
                        if (module != null && module.startsWith(PACKAGE_MARKER)) {
                            sb.append(module).append(':').append(frame.getLineno()).append('|');
                        }
                    }
                }
            }
        } else if (message != null) {
            String formatted = message.getFormatted();
            if (formatted != null) {
                sb.append(formatted, 0, Math.min(200, formatted.length()));
            }
        }
        return sb.toString();
    }

    /** Replaces home-directory paths (which contain the OS account name) with a neutral marker. */
    static String scrub(String text) {
        return USER_PATH.matcher(text).replaceAll("/~");
    }
}
