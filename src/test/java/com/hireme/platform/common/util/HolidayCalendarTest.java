package com.hireme.platform.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HolidayCalendarTest {

    private HolidayCalendar holidayCalendar;

    @BeforeEach
    void setUp() {
        holidayCalendar = new HolidayCalendar();
    }

    @Test
    void testFederalHolidays() {
        // New Year's Day 2026
        assertTrue(holidayCalendar.isHoliday(LocalDate.of(2026, 1, 1)));

        // Juneteenth 2026
        assertTrue(holidayCalendar.isHoliday(LocalDate.of(2026, 6, 19)));

        // Independence Day 2026
        assertTrue(holidayCalendar.isHoliday(LocalDate.of(2026, 7, 4)));

        // Christmas 2026
        assertTrue(holidayCalendar.isHoliday(LocalDate.of(2026, 12, 25)));

        // Regular non-holiday business day
        assertFalse(holidayCalendar.isHoliday(LocalDate.of(2026, 8, 24)));
    }
}

