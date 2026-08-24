package com.hireme.platform.common.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

/**
 * Recognized-holiday lookup for the holiday-pay multiplier in the pricing
 * engine (spec §2.A). This demo implementation computes the standard US
 * federal holiday set for a given year; a production deployment should
 * instead source this per-jurisdiction (and let Admin configure org-specific
 * observed holidays), since "holiday pay" rules vary significantly by state
 * and by collective bargaining agreement.
 */
@Component
public class HolidayCalendar {

    public boolean isHoliday(LocalDate date) {
        return federalHolidays(Year.of(date.getYear())).contains(date);
    }

    private Set<LocalDate> federalHolidays(Year year) {
        int y = year.getValue();
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(y, 1, 1));                                   // New Year's Day
        holidays.add(nthWeekdayOfMonth(y, 1, java.time.DayOfWeek.MONDAY, 3));   // MLK Day
        holidays.add(nthWeekdayOfMonth(y, 2, java.time.DayOfWeek.MONDAY, 3));   // Presidents' Day
        holidays.add(lastWeekdayOfMonth(y, 5, java.time.DayOfWeek.MONDAY));    // Memorial Day
        holidays.add(LocalDate.of(y, 6, 19));                                  // Juneteenth
        holidays.add(LocalDate.of(y, 7, 4));                                   // Independence Day
        holidays.add(nthWeekdayOfMonth(y, 9, java.time.DayOfWeek.MONDAY, 1));   // Labor Day
        holidays.add(nthWeekdayOfMonth(y, 11, java.time.DayOfWeek.THURSDAY, 4)); // Thanksgiving
        holidays.add(LocalDate.of(y, 12, 25));                                 // Christmas
        return holidays;
    }

    private LocalDate nthWeekdayOfMonth(int year, int month, java.time.DayOfWeek dow, int n) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate firstMatch = first.with(TemporalAdjusters.firstInMonth(dow));
        return firstMatch.plusWeeks(n - 1);
    }

    private LocalDate lastWeekdayOfMonth(int year, int month, java.time.DayOfWeek dow) {
        LocalDate last = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
        return last.with(TemporalAdjusters.lastInMonth(dow));
    }
}
