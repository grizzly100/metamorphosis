package org.grizzlytech.metamorphosis.util;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class TimeUtilTest {

    private static final Logger LOG = LoggerFactory.getLogger(TimeUtilTest.class);

    @Test
    void withinAnHour() {

        Instant baseline = Instant.parse("2018-07-31T17:03:03.905Z");
        Instant baseline_plus_30mm = baseline.plus(30, ChronoUnit.MINUTES);
        Instant baseline_plus_01hr = baseline.plus(1, ChronoUnit.HOURS);
        Instant baseline_plus_01hr_pE = baseline_plus_01hr.plus(50, ChronoUnit.SECONDS);
        Instant baseline_plus_01hr_mE = baseline_plus_01hr.minus(50, ChronoUnit.SECONDS);
        Instant baseline_plus_48hr = baseline.plus(48, ChronoUnit.HOURS);

        // Equal or approx one hour
        assertTrue(TimeUtil.withinAnHour(baseline, baseline_plus_01hr) == 0);
        assertTrue(TimeUtil.withinAnHour(baseline, baseline_plus_01hr_pE) == 0);
        assertTrue(TimeUtil.withinAnHour(baseline, baseline_plus_01hr_mE) == 0);
        // Less than an hour
        assertTrue(TimeUtil.withinAnHour(baseline, baseline) == -1);
        assertTrue(TimeUtil.withinAnHour(baseline, baseline_plus_30mm) == -1);
        // More than an hour
        assertTrue(TimeUtil.withinAnHour(baseline, baseline_plus_48hr) == 1);

        Instant baseline_correct = TimeUtil.correctZoneOffset(baseline, ZoneId.systemDefault());
        assertEquals(baseline_correct, baseline.minus(1, ChronoUnit.HOURS));

        // mediaDate [2018-08-26T14:01:44.040Z] != contentDate [2018-08-26T15:01:44Z] withinAnHour [0]
        Instant baseline2 = Instant.parse("2018-08-26T15:01:44Z");
        LOG.info("{} = {}", baseline2, TimeUtil.correctZoneOffset(baseline2, ZoneId.systemDefault()));
    }
}