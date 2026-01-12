package com.centneo.fintech.supportDeskSvc.util;

import java.time.*;

public class SlaDueComputeUtil {

    /**
     * Compute SLA due date/time by adding `slaDays` to the reference date and using `referenceTime`.
     *
     * - referenceInstant: the moment (ticket creation, event time, or now) to base the calculation on.
     * - slaDays: number of days to add (1,2,5,7, ...)
     * - referenceTimeOfDay: if non-null, use this LocalTime for the due time-of-day; otherwise use the time-of-day from referenceInstant.
     * - zone: used for ZonedDateTime variant to handle time-zones & DST properly.
     *
     * Returns a LocalDateTime (or ZonedDateTime) strictly after referenceInstant.
     */
    public static LocalDateTime computeSlaDue(LocalDateTime referenceInstant,
                                              int slaDays,
                                              LocalTime referenceTimeOfDay) {
        if (referenceInstant == null) {
            referenceInstant = LocalDateTime.now();
        }
        if (slaDays < 0) throw new IllegalArgumentException("slaDays must be >= 0");

        // choose time-of-day: provided or from referenceInstant
        LocalTime timeOfDay = referenceTimeOfDay != null ? referenceTimeOfDay : referenceInstant.toLocalTime();

        // base date = reference date + slaDays
        LocalDate baseDate = referenceInstant.toLocalDate().plusDays(slaDays);
        LocalDateTime candidate = LocalDateTime.of(baseDate, timeOfDay);

        // if candidate is less or equal to referenceInstant, advance by one day to ensure future
        if (!candidate.isAfter(referenceInstant)) {
            candidate = candidate.plusDays(1);
        }

        return candidate;
    }

    /**
     * Zoned version — handles time zone and DST properly.
     */
    public static ZonedDateTime computeSlaDueZoned(ZonedDateTime referenceInstant,
                                                   int slaDays,
                                                   LocalTime referenceTimeOfDay,
                                                   ZoneId zone) {
        if (referenceInstant == null) {
            referenceInstant = ZonedDateTime.now(zone);
        }
        if (zone == null) {
            zone = referenceInstant.getZone();
        }
        if (slaDays < 0) throw new IllegalArgumentException("slaDays must be >= 0");

        LocalTime timeOfDay = referenceTimeOfDay != null ? referenceTimeOfDay : referenceInstant.toLocalTime();
        LocalDate baseDate = referenceInstant.toLocalDate().plusDays(slaDays);

        // Create the candidate in the desired zone. Use ZonedDateTime.ofLocal to handle DST gaps/overlaps.
        LocalDateTime localCandidate = LocalDateTime.of(baseDate, timeOfDay);
        ZonedDateTime candidate = ZonedDateTime.of(localCandidate, zone);

        // if candidate is <= referenceInstant, shift to next day
        if (!candidate.isAfter(referenceInstant)) {
            LocalDateTime nextLocal = localCandidate.plusDays(1);
            candidate = ZonedDateTime.of(nextLocal, zone);
        }

        return candidate;
    }
}
