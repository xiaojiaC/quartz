/*
 * Copyright (C) 2022 Baidu, Inc. All Rights Reserved.
 */
package org.quartz.impl.calendar;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import junit.framework.TestCase;

/**
 * @author xiaojia
 * @since 1.0
 */
public class CronCalendarTest extends TestCase {

    public void test_isTimeIncluded() throws Exception {
        CronCalendar calendar = new CronCalendar("0 15 10 * * ? 2005");
        Calendar cal = Calendar.getInstance();
        cal.set(2005, Calendar.JUNE, 1, 10, 15, 0);
        assertEquals(false, calendar.isTimeIncluded(cal.getTimeInMillis()));
    }

    public void test_getNextIncludedTime() throws Exception {
        CronCalendar calendar = new CronCalendar("0 15 10 * * ? 2005");
        Calendar cal = Calendar.getInstance();
        cal.set(2005, Calendar.JUNE, 1, 10, 15, 0);
        Date nextDate = new Date(calendar.getNextIncludedTime(cal.getTimeInMillis()));
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(nextDate));
        Date nextDate2 = new Date(calendar.getNextIncludedTime(1117592102000L));
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(nextDate2));
    }

}
