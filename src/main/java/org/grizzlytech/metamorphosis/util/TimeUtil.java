package org.grizzlytech.metamorphosis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;

/**
 * Utility functions for comparing Instants
 */
public class TimeUtil {
    private static final Logger LOG = LoggerFactory.getLogger(TimeUtil.class);

    /**
     * Compare two times for equality within an error margin
     *
     * @param time1 first time to compare
     * @param time2 second time to compare
     * @return true if the times are within an error margin
     */
    public static boolean equalWithinError(Instant time1, Instant time2) {
        final long ERROR = 100; // seconds
        return compareTimeDeltaToPeriod(time1, time2, 0, ERROR) == 0;
    }

    /**
     * Compare two times to determine if the period between them is within an hour
     *
     * @param time1 first time to compare
     * @param time2 second time to compare
     * @return 0 if equal to an hour (+/- error); 1 if greater time period (+error); -1 if less time period (-error)
     */
    public static int withinAnHour(Instant time1, Instant time2) {
        final long PERIOD = 60 * 60; // seconds
        final long ERROR = 100; // seconds
        return compareTimeDeltaToPeriod(time1, time2, PERIOD, ERROR);
    }

    /**
     * Compare two times to determine if the period between them is within an hour
     *
     * @param time1 first time to compare
     * @param time2 second time to compare
     * @return 0 if equal to a day (+/- error); 1 if greater time period (+error); -1 if less time period (-error)
     */
    public static int withinADay(Instant time1, Instant time2) {
        final long PERIOD = 24 * 60 * 60; // seconds
        final long ERROR = 100; // seconds
        return compareTimeDeltaToPeriod(time1, time2, PERIOD, ERROR);
    }

    /**
     * Compare two times to determine the earliest. Handle nulls safely.
     *
     * @param time1 first time to compare
     * @param time2 second time to compare
     * @return the earliest time
     */
    public static Instant earliest(Instant time1, Instant time2) {
        if (time2 == null) {
            return time1;
        }
        return ((time1 != null) && time1.isBefore(time2)) ? time1 : time2;
    }

    /**
     * Compare the difference between two Instants
     *
     * @param time1  first time to compare
     * @param time2  second time to compare
     * @param period time period in seconds
     * @param error  error margin in seconds
     * @return 0 if equal to the period (+/- error); 1 if greater time period (+error); -1 if less time period (-error)
     */
    public static int compareTimeDeltaToPeriod(Instant time1, Instant time2, long period, long error) {
        int cmp = 0; // assume time difference is equal to the time period (+/- the error margin)
        long delta = Math.abs(time1.getEpochSecond() - time2.getEpochSecond());
        if (delta > period + error) {
            cmp = 1;
        } else if (delta < period - error) {
            cmp = -1;
        }
        return cmp;
    }

    /**
     * Correct the timeAssumed with the timeAlternative only if it is materially (>1hr) earlier
     * For example, if the content creation date is materially before the media date
     *
     * @param timeAssumed     time that will be assumed
     * @param timeAlternative time that should be considered if materially earlier
     * @return the time to use
     */
    public static Instant correctIfAlternativeMateriallyEarlier(Instant timeAssumed, Instant timeAlternative,
                                                                String context) {
        Instant earliestDate = timeAssumed;
        // If both dates are provided but are materially different (>1hr) we need to handle the conflict
        if (timeAlternative != null && !equalWithinError(timeAssumed, timeAlternative)) {
            // A "material" time difference is considered a day or more
            // Times may be just an hour apart (due to DST issues) or even within a few (nano) seconds
            int material = withinADay(timeAssumed, timeAlternative);
            // Only pick the earlier time if it makes a material difference
            if (timeAlternative.isBefore(timeAssumed) && material == 1) {
                LOG.info("{} Assumed [{}] != Alternative [{}] material=[{}]", context, timeAssumed,
                        timeAlternative, material);
                earliestDate = timeAlternative;
            }
        }
        return earliestDate;
    }

    /**
     * Handles the case where a local time has been (incorrectly) recorded as being UTC.
     * For example, during BST, recording 5pm BST as 5pm UST.
     * (This appears to occur in TAG_QUICKTIME_CREATIONDATE of MOV files)
     *
     * @param time time that has been misrepresented as UTC
     * @param zone time zone that should have been recorded with the local time
     * @return correct UTC instant
     */
    public static Instant correctZoneOffset(Instant time, ZoneId zone) {
        // Extract the date/time assuming (incorrectly) UTC
        // E.g., 5pm on 2018-07-31 was saved as UTC, however it was really BST (+1) and hence should have been 4pm
        LocalDateTime ldt = LocalDateTime.ofInstant(time, ZoneOffset.UTC);
        // Correct the zone (e.g., to "Europe/London")
        ZonedDateTime zdt = ZonedDateTime.of(ldt, zone);
        // Converting back into a UTC Instant will correct the hour to 4pm
        return zdt.toInstant();
    }
}
