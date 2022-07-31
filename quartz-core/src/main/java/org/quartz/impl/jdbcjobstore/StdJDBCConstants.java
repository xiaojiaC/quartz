/* 
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not 
 * use this file except in compliance with the License. You may obtain a copy 
 * of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations 
 * under the License.
 * 
 */

package org.quartz.impl.jdbcjobstore;

import org.quartz.Trigger;

/**
 * <p>
 * 一堆表相关的sql模板语句常量
 *
 * This interface extends <code>{@link
 * org.quartz.impl.jdbcjobstore.Constants}</code>
 * to include the query string constants in use by the <code>{@link
 * org.quartz.impl.jdbcjobstore.StdJDBCDelegate}</code>
 * class.
 * </p>
 * 
 * @author <a href="mailto:jeff@binaryfeed.org">Jeffrey Wescott</a>
 */
public interface StdJDBCConstants extends Constants {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Constants.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    // table prefix substitution string
    String TABLE_PREFIX_SUBST = "{0}";

    // table prefix substitution string
    String SCHED_NAME_SUBST = "{1}";

    // QUERIES
    /**
     * <pre>
     * update {0}triggers
     * set trigger_state = ?
     * where sched_name = {1} and (trigger_state = ? or trigger_state = ?)
     * </pre>
     */
    String UPDATE_TRIGGER_STATES_FROM_OTHER_STATES = "UPDATE "
            + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS
            + " SET "
            + COL_TRIGGER_STATE
            + " = ?"
            + " WHERE "
            + COL_SCHEDULER_NAME 
            + " = " + SCHED_NAME_SUBST + " AND ("
            + COL_TRIGGER_STATE
            + " = ? OR "
            + COL_TRIGGER_STATE + " = ?)";

    /**
     * <pre>
     * select * from {0}triggers
     * where sched_name = {1} and not (misfire_instr = -1) and next_fire_time < ?
     * order by next_fire_time asc, priority desc
     * </pre>
     */
    String SELECT_MISFIRED_TRIGGERS = "SELECT * FROM "
        + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
        + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
        + " AND NOT ("
        + COL_MISFIRE_INSTRUCTION + " = " + Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY + ") AND " 
        + COL_NEXT_FIRE_TIME + " < ? "
        + "ORDER BY " + COL_NEXT_FIRE_TIME + " ASC, " + COL_PRIORITY + " DESC";

    /**
     * <pre>
     * select trigger_name, trigger_group from {0}triggers
     * where sched_name = {1} and trigger_state = ?
     * </pre>
     */
    String SELECT_TRIGGERS_IN_STATE = "SELECT "
            + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + " FROM "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST + " AND "
            + COL_TRIGGER_STATE + " = ?";

    /**
     * <pre>
     * select trigger_name, trigger_group from {0}triggers
     * where sched_name = {1} and not (misfire_instr = -1)
     *       and next_fire_time < ? and trigger_state = ?
     * order by next_fire_time asc, priority desc
     * </pre>
     */
    String SELECT_MISFIRED_TRIGGERS_IN_STATE = "SELECT "
        + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + " FROM "
        + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
        + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST + " AND NOT ("
        + COL_MISFIRE_INSTRUCTION + " = " + Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY + ") AND " 
        + COL_NEXT_FIRE_TIME + " < ? AND " + COL_TRIGGER_STATE + " = ? "
        + "ORDER BY " + COL_NEXT_FIRE_TIME + " ASC, " + COL_PRIORITY + " DESC";

    /**
     * <pre>
     * select count(trigger_name) from {0}triggers
     * where sched_name = {1} and not (misfire_instr = -1)
     *       and next_fire_time < ? and trigger_state = ?
     * </pre>
     */
    String COUNT_MISFIRED_TRIGGERS_IN_STATE = "SELECT COUNT("
        + COL_TRIGGER_NAME + ") FROM "
        + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
        + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST + " AND NOT ("
        + COL_MISFIRE_INSTRUCTION + " = " + Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY + ") AND " 
        + COL_NEXT_FIRE_TIME + " < ? " 
        + "AND " + COL_TRIGGER_STATE + " = ?";

    /**
     * <pre>
     * select trigger_name, trigger_group from {0}triggers
     * where sched_name = {1} and not (misfire_instr = -1)
     *       and next_fire_time < ? and trigger_state = ?
     * order by next_fire_time asc, priority desc
     * </pre>
     */
    String SELECT_HAS_MISFIRED_TRIGGERS_IN_STATE = "SELECT "
        + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + " FROM "
        + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
        + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST + " AND NOT ("
        + COL_MISFIRE_INSTRUCTION + " = " + Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY + ") AND " 
        + COL_NEXT_FIRE_TIME + " < ? " 
        + "AND " + COL_TRIGGER_STATE + " = ? "
        + "ORDER BY " + COL_NEXT_FIRE_TIME + " ASC, " + COL_PRIORITY + " DESC";

    /**
     * <pre>
     * select trigger_name from {0}triggers
     * where sched_name = {1} and not (misfire_instr = -1) and next_fire_time < ?
     *       and trigger_group = ? and trigger_state = ?
     * order by next_fire_time asc, priority desc
     * </pre>
     */
    String SELECT_MISFIRED_TRIGGERS_IN_GROUP_IN_STATE = "SELECT "
        + COL_TRIGGER_NAME
        + " FROM "
        + TABLE_PREFIX_SUBST
        + TABLE_TRIGGERS
        + " WHERE "
        + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST + " AND NOT ("
        + COL_MISFIRE_INSTRUCTION + " = " + Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY + ") AND " 
        + COL_NEXT_FIRE_TIME
        + " < ? AND "
        + COL_TRIGGER_GROUP
        + " = ? AND " + COL_TRIGGER_STATE + " = ? "
        + "ORDER BY " + COL_NEXT_FIRE_TIME + " ASC, " + COL_PRIORITY + " DESC";

    /**
     * <pre>
     * delete from {0}fired_triggers where sched_name = {1}
     * </pre>
     */
    String DELETE_FIRED_TRIGGERS = "DELETE FROM "
            + TABLE_PREFIX_SUBST + TABLE_FIRED_TRIGGERS
            + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;

    /**
     * <pre>
     * insert into {0}job_details(
     *  sched_name, job_name, job_group, description, job_class_name,
     *  is_durable, is_nonconcurrent, is_update_data, requests_recovery, job_data
     * ) values({1}, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     * </pre>
     */
    String INSERT_JOB_DETAIL = "INSERT INTO "
            + TABLE_PREFIX_SUBST + TABLE_JOB_DETAILS + " (" 
            + COL_SCHEDULER_NAME + ", " + COL_JOB_NAME
            + ", " + COL_JOB_GROUP + ", " + COL_DESCRIPTION + ", "
            + COL_JOB_CLASS + ", " + COL_IS_DURABLE + ", " 
            + COL_IS_NONCONCURRENT +  ", " + COL_IS_UPDATE_DATA + ", " 
            + COL_REQUESTS_RECOVERY + ", "
            + COL_JOB_DATAMAP + ") " + " VALUES(" + SCHED_NAME_SUBST + ", ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * <pre>
     * update {0}job_details
     * set description = ?, job_class_name = ?, is_durable = ?,
     *  is_nonconcurrent = ?, is_update_data = ?, requests_recovery = ?, job_data = ?
     * where sched_name = {1} and job_name = ? and job_group = ?
     * </pre>
     */
    String UPDATE_JOB_DETAIL = "UPDATE "
            + TABLE_PREFIX_SUBST + TABLE_JOB_DETAILS + " SET "
            + COL_DESCRIPTION + " = ?, " + COL_JOB_CLASS + " = ?, "
            + COL_IS_DURABLE + " = ?, " 
            + COL_IS_NONCONCURRENT + " = ?, " + COL_IS_UPDATE_DATA + " = ?, " 
            + COL_REQUESTS_RECOVERY + " = ?, "
            + COL_JOB_DATAMAP + " = ? " + " WHERE " 
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST 
            + " AND " + COL_JOB_NAME
            + " = ? AND " + COL_JOB_GROUP + " = ?";

    /**
     * <pre>
     * select trigger_name, trigger_group from {0}triggers
     * where sched_name = {1} and job_name = ? and job_group = ?
     * </pre>
     */
    String SELECT_TRIGGERS_FOR_JOB = "SELECT "
            + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + " FROM "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE " 
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST 
            + " AND " + COL_JOB_NAME
            + " = ? AND " + COL_JOB_GROUP + " = ?";

    /**
     * <pre>
     * select trigger_name, trigger_group from {0}triggers
     * where sched_name = {1} and calendar_name = ?
     * </pre>
     */
    String SELECT_TRIGGERS_FOR_CALENDAR = "SELECT "
        + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + " FROM "
        + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE " 
        + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST 
        + " AND " + COL_CALENDAR_NAME
        + " = ?";

    /**
     * <pre>
     * delete from {0}job_details
     * where sched_name = {1} and job_name = ? and job_group = ?
     * </pre>
     */
    String DELETE_JOB_DETAIL = "DELETE FROM "
            + TABLE_PREFIX_SUBST + TABLE_JOB_DETAILS + " WHERE " 
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST 
            + " AND " + COL_JOB_NAME
            + " = ? AND " + COL_JOB_GROUP + " = ?";

    /**
     * <pre>
     * select is_nonconcurrent from {0}job_details
     * where sched_name = {1} and job_name = ? and job_group = ?
     * </pre>
     */
    String SELECT_JOB_NONCONCURRENT = "SELECT "
            + COL_IS_NONCONCURRENT + " FROM " + TABLE_PREFIX_SUBST
            + TABLE_JOB_DETAILS + " WHERE " 
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST 
            + " AND " + COL_JOB_NAME
            + " = ? AND " + COL_JOB_GROUP + " = ?";

    /**
     * <pre>
     *  select job_name from {0}job_details
     *  where sched_name = {1} and job_name = ? and job_group = ?
     * </pre>
     */
    String SELECT_JOB_EXISTENCE = "SELECT " + COL_JOB_NAME
            + " FROM " + TABLE_PREFIX_SUBST + TABLE_JOB_DETAILS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST 
            + " AND " + COL_JOB_NAME
            + " = ? AND " + COL_JOB_GROUP + " = ?";

    /**
     * <pre>
     * update {0}job_details
     * set job_data = ?
     * where sched_name = {1} and job_name = ? and job_group = ?
     * </pre>
     */
    String UPDATE_JOB_DATA = "UPDATE " + TABLE_PREFIX_SUBST
            + TABLE_JOB_DETAILS + " SET " + COL_JOB_DATAMAP + " = ? "
            + " WHERE " 
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST 
            + " AND " + COL_JOB_NAME
            + " = ? AND " + COL_JOB_GROUP + " = ?";

    /**
     * <pre>
     * select * from {0}job_details
     * where sched_name = {1} and job_name = ? and job_group = ?
     * </pre>
     */
    String SELECT_JOB_DETAIL = "SELECT *" + " FROM "
            + TABLE_PREFIX_SUBST + TABLE_JOB_DETAILS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST 
            + " AND " + COL_JOB_NAME
            + " = ? AND " + COL_JOB_GROUP + " = ?";


    /**
     * <pre>
     * select count(job_name)  from {0}job_details
     * where sched_name = {1}
     * </pre>
     */
    String SELECT_NUM_JOBS = "SELECT COUNT(" + COL_JOB_NAME
            + ") " + " FROM " + TABLE_PREFIX_SUBST + TABLE_JOB_DETAILS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;


    /**
     * <pre>
     * select distinct(job_group) from {0}job_details
     * where sched_name = {1}
     * </pre>
     */
    String SELECT_JOB_GROUPS = "SELECT DISTINCT("
            + COL_JOB_GROUP + ") FROM " + TABLE_PREFIX_SUBST
            + TABLE_JOB_DETAILS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;

    /**
     * <pre>
     * select job_name, job_group from {0}job_details
     * where sched_name = {1} and job_group like ?
     * </pre>
     */
    String SELECT_JOBS_IN_GROUP_LIKE = "SELECT " + COL_JOB_NAME + ", " + COL_JOB_GROUP
            + " FROM " + TABLE_PREFIX_SUBST + TABLE_JOB_DETAILS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_JOB_GROUP + " LIKE ?";

    /**
     * <pre>
     * select job_name, job_group from {0}job_details
     * where sched_name = {1} and job_group = ?
     * </pre>
     */
    String SELECT_JOBS_IN_GROUP = "SELECT " + COL_JOB_NAME + ", " + COL_JOB_GROUP
            + " FROM " + TABLE_PREFIX_SUBST + TABLE_JOB_DETAILS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_JOB_GROUP + " = ?";

    /**
     * <pre>
     * insert into {0}triggers (
     * sched_name, trigger_name, trigger_group, job_name, job_group, description,
     * next_fire_time, prev_fire_time, trigger_state, trigger_type,
     * start_time, end_time, calendar_name, misfire_instr, job_data, priority
     * )  values({1}, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     * </pre>
     */
    String INSERT_TRIGGER = "INSERT INTO "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " (" + COL_SCHEDULER_NAME + ", " + COL_TRIGGER_NAME
            + ", " + COL_TRIGGER_GROUP + ", " + COL_JOB_NAME + ", "
            + COL_JOB_GROUP + ", " + COL_DESCRIPTION
            + ", " + COL_NEXT_FIRE_TIME + ", " + COL_PREV_FIRE_TIME + ", "
            + COL_TRIGGER_STATE + ", " + COL_TRIGGER_TYPE + ", "
            + COL_START_TIME + ", " + COL_END_TIME + ", " + COL_CALENDAR_NAME
            + ", " + COL_MISFIRE_INSTRUCTION + ", " + COL_JOB_DATAMAP + ", " + COL_PRIORITY + ") "
            + " VALUES(" + SCHED_NAME_SUBST + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * <pre>
     * insert into {0}simple_triggers (
     * sched_name, trigger_name, trigger_group, repeat_count, repeat_interval, times_triggered
     * )  values({1}, ?, ?, ?, ?, ?)
     * </pre>
     */
    String INSERT_SIMPLE_TRIGGER = "INSERT INTO "
            + TABLE_PREFIX_SUBST + TABLE_SIMPLE_TRIGGERS + " ("
            + COL_SCHEDULER_NAME + ", "
            + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + ", "
            + COL_REPEAT_COUNT + ", " + COL_REPEAT_INTERVAL + ", "
            + COL_TIMES_TRIGGERED + ") " + " VALUES(" + SCHED_NAME_SUBST + ", ?, ?, ?, ?, ?)";

    /**
     * <pre>
     * insert into {0}cron_triggers (
     * sched_name, trigger_name, trigger_group, cron_expression, time_zone_id
     * )  values({1}, ?, ?, ?, ?)
     * </pre>
     */
    String INSERT_CRON_TRIGGER = "INSERT INTO "
            + TABLE_PREFIX_SUBST + TABLE_CRON_TRIGGERS + " ("
            + COL_SCHEDULER_NAME + ", "
            + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + ", "
            + COL_CRON_EXPRESSION + ", " + COL_TIME_ZONE_ID + ") "
            + " VALUES(" + SCHED_NAME_SUBST + ", ?, ?, ?, ?)";

    /**
     * <pre>
     * insert into {0}blob_triggers (
     * sched_name, trigger_name, trigger_group, blob_data
     * )  values({1}, ?, ?, ?)
     * </pre>
     */
    String INSERT_BLOB_TRIGGER = "INSERT INTO "
            + TABLE_PREFIX_SUBST + TABLE_BLOB_TRIGGERS + " ("
            + COL_SCHEDULER_NAME + ", "
            + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + ", " + COL_BLOB
            + ") " + " VALUES(" + SCHED_NAME_SUBST + ", ?, ?, ?)";

    /**
     * <pre>
     * update {0}triggers
     * set job_name = ?, job_group = ?, description = ?, next_fire_time = ?, prev_fire_time = ?,
     * trigger_state = ?, trigger_type = ?, start_time = ?, end_time = ?, calendar_name = ?,
     * misfire_instr = ?, priority = ?
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String UPDATE_TRIGGER_SKIP_DATA = "UPDATE " + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS + " SET " + COL_JOB_NAME + " = ?, "
            + COL_JOB_GROUP + " = ?, " 
            + COL_DESCRIPTION + " = ?, " + COL_NEXT_FIRE_TIME + " = ?, "
            + COL_PREV_FIRE_TIME + " = ?, " + COL_TRIGGER_STATE + " = ?, "
            + COL_TRIGGER_TYPE + " = ?, " + COL_START_TIME + " = ?, "
            + COL_END_TIME + " = ?, " + COL_CALENDAR_NAME + " = ?, "
            + COL_MISFIRE_INSTRUCTION + " = ?, " + COL_PRIORITY 
            + " = ? WHERE " 
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME
            + " = ? AND " + COL_TRIGGER_GROUP + " = ?";

    /**
     * <pre>
     * update {0}triggers
     * set job_name = ?, job_group = ?, description = ?, next_fire_time = ?, prev_fire_time = ?,
     *  trigger_state = ?, trigger_type = ?, start_time = ?, end_time = ?, calendar_name = ?,
     *  misfire_instr = ?, priority = ?, job_data = ?
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String UPDATE_TRIGGER = "UPDATE " + TABLE_PREFIX_SUBST
        + TABLE_TRIGGERS + " SET " + COL_JOB_NAME + " = ?, "
        + COL_JOB_GROUP + " = ?, "
        + COL_DESCRIPTION + " = ?, " + COL_NEXT_FIRE_TIME + " = ?, "
        + COL_PREV_FIRE_TIME + " = ?, " + COL_TRIGGER_STATE + " = ?, "
        + COL_TRIGGER_TYPE + " = ?, " + COL_START_TIME + " = ?, "
        + COL_END_TIME + " = ?, " + COL_CALENDAR_NAME + " = ?, "
        + COL_MISFIRE_INSTRUCTION + " = ?, " + COL_PRIORITY + " = ?, " 
        + COL_JOB_DATAMAP + " = ? WHERE " 
        + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
        + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";

    /**
     * <pre>
     * update {0}simple_triggers
     * set repeat_count = ?, repeat_interval = ?, times_triggered = ?
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String UPDATE_SIMPLE_TRIGGER = "UPDATE "
            + TABLE_PREFIX_SUBST + TABLE_SIMPLE_TRIGGERS + " SET "
            + COL_REPEAT_COUNT + " = ?, " + COL_REPEAT_INTERVAL + " = ?, "
            + COL_TIMES_TRIGGERED + " = ? WHERE " 
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME
            + " = ? AND " + COL_TRIGGER_GROUP + " = ?";

    /**
     * <pre>
     * update {0}cron_triggers
     * set cron_expression = ?, time_zone_id = ?
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String UPDATE_CRON_TRIGGER = "UPDATE "
            + TABLE_PREFIX_SUBST + TABLE_CRON_TRIGGERS + " SET "
            + COL_CRON_EXPRESSION + " = ?, " + COL_TIME_ZONE_ID  
            + " = ? WHERE " 
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME
            + " = ? AND " + COL_TRIGGER_GROUP + " = ?";

    /**
     * <pre>
     * update {0}blob_triggers
     * set blob_data = ?
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String UPDATE_BLOB_TRIGGER = "UPDATE "
            + TABLE_PREFIX_SUBST + TABLE_BLOB_TRIGGERS + " SET " + COL_BLOB
            + " = ? WHERE " 
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND "
            + COL_TRIGGER_GROUP + " = ?";

    /**
     * <pre>
     * select trigger_name from {0}triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String SELECT_TRIGGER_EXISTENCE = "SELECT "
            + COL_TRIGGER_NAME + " FROM " + TABLE_PREFIX_SUBST + TABLE_TRIGGERS
            + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP
            + " = ?";

    /**
     * <pre>
     * select trigger_state from {0}triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String UPDATE_TRIGGER_STATE = "UPDATE "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " SET " + COL_TRIGGER_STATE
            + " = ?" + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND "
            + COL_TRIGGER_GROUP + " = ?";

    /**
     * <pre>
     * update {0}triggers
     * set trigger_state = ?
     * where sched_name = {1} and trigger_name = ? and trigger_group = ? and trigger_state = ?
     * </pre>
     */
    String UPDATE_TRIGGER_STATE_FROM_STATE = "UPDATE "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " SET " + COL_TRIGGER_STATE
            + " = ?" + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND "
            + COL_TRIGGER_GROUP + " = ? AND " + COL_TRIGGER_STATE + " = ?";

    /**
     * <pre>
     * update {0}triggers
     * set trigger_state = ?
     * where sched_name = {1} and trigger_group like ? and trigger_state = ?
     * </pre>
     */
    String UPDATE_TRIGGER_GROUP_STATE_FROM_STATE = "UPDATE "
            + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS
            + " SET "
            + COL_TRIGGER_STATE
            + " = ?"
            + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_GROUP
            + " LIKE ? AND "
            + COL_TRIGGER_STATE + " = ?";

    /**
     * <pre>
     * update {0}triggers
     * set trigger_state = ?
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     *   and (trigger_state = ? or trigger_state = ? or trigger_state = ?)
     * </pre>
     */
    String UPDATE_TRIGGER_STATE_FROM_STATES = "UPDATE "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " SET " + COL_TRIGGER_STATE
            + " = ?" + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND "
            + COL_TRIGGER_GROUP + " = ? AND (" + COL_TRIGGER_STATE + " = ? OR "
            + COL_TRIGGER_STATE + " = ? OR " + COL_TRIGGER_STATE + " = ?)";

    /**
     * <pre>
     * update {0}triggers
     * set trigger_state = ?
     * where sched_name = {1} and trigger_group like ?
     *   and (trigger_state = ? or trigger_state = ? or trigger_state = ?)
     * </pre>
     */
    String UPDATE_TRIGGER_GROUP_STATE_FROM_STATES = "UPDATE "
            + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS
            + " SET "
            + COL_TRIGGER_STATE
            + " = ?"
            + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_GROUP
            + " LIKE ? AND ("
            + COL_TRIGGER_STATE
            + " = ? OR "
            + COL_TRIGGER_STATE
            + " = ? OR "
            + COL_TRIGGER_STATE + " = ?)";

    /**
     * <pre>
     * update {0}triggers
     * set trigger_state = ?
     * where sched_name = {1} and job_name = ? and job_group = ?
     * </pre>
     */
    String UPDATE_JOB_TRIGGER_STATES = "UPDATE "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " SET " + COL_TRIGGER_STATE
            + " = ? WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_JOB_NAME + " = ? AND " + COL_JOB_GROUP
            + " = ?";

    /**
     * <pre>
     * update {0}triggers
     * set trigger_state = ?
     * where sched_name = {1} and job_name = ? and job_group = ? and trigger_state = ?
     * </pre>
     */
    String UPDATE_JOB_TRIGGER_STATES_FROM_OTHER_STATE = "UPDATE "
            + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS
            + " SET "
            + COL_TRIGGER_STATE
            + " = ? WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_JOB_NAME
            + " = ? AND "
            + COL_JOB_GROUP
            + " = ? AND " + COL_TRIGGER_STATE + " = ?";

    /**
     * <pre>
     * delete from {0}simple_triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String DELETE_SIMPLE_TRIGGER = "DELETE FROM "
            + TABLE_PREFIX_SUBST + TABLE_SIMPLE_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";

    /**
     * <pre>
     * delete from {0}cron_triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String DELETE_CRON_TRIGGER = "DELETE FROM "
            + TABLE_PREFIX_SUBST + TABLE_CRON_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";

    /**
     * <pre>
     * delete from {0}blob_triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String DELETE_BLOB_TRIGGER = "DELETE FROM "
            + TABLE_PREFIX_SUBST + TABLE_BLOB_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * delete from {0}triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String DELETE_TRIGGER = "DELETE FROM "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * select count(trigger_name) from {0}triggers
     * where sched_name = {1} and job_name = ? and job_group = ?
     * </pre>
     */
    String SELECT_NUM_TRIGGERS_FOR_JOB = "SELECT COUNT("
            + COL_TRIGGER_NAME + ") FROM " + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_JOB_NAME + " = ? AND "
            + COL_JOB_GROUP + " = ?";
    /**
     * <pre>
     * select j.job_name, j.job_group, j.is_durable, j.job_class_name,
     *    j.requests_recovery from {0}triggers t, {0}job_details j
     * where t.sched_name = {1} and j.sched_name = {1} and t.trigger_name = ?
     *   and t.trigger_group = ? and t.job_name = j.job_name and t.job_group = j.job_group
     * </pre>
     */
    String SELECT_JOB_FOR_TRIGGER = "SELECT J."
            + COL_JOB_NAME + ", J." + COL_JOB_GROUP + ", J." + COL_IS_DURABLE
            + ", J." + COL_JOB_CLASS + ", J." + COL_REQUESTS_RECOVERY + " FROM " + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS + " T, " + TABLE_PREFIX_SUBST + TABLE_JOB_DETAILS
            + " J WHERE T." + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST 
            + " AND J." + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST 
            + " AND T." + COL_TRIGGER_NAME + " = ? AND T."
            + COL_TRIGGER_GROUP + " = ? AND T." + COL_JOB_NAME + " = J."
            + COL_JOB_NAME + " AND T." + COL_JOB_GROUP + " = J."
            + COL_JOB_GROUP;
    /**
     * <pre>
     * select * from {0}triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String SELECT_TRIGGER = "SELECT * FROM "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";

    /**
     * <pre>
     * select job_data from {0}triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String SELECT_TRIGGER_DATA = "SELECT " + 
            COL_JOB_DATAMAP + " FROM "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * select trigger_state from {0}triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String SELECT_TRIGGER_STATE = "SELECT "
            + COL_TRIGGER_STATE + " FROM " + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND "
            + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * select trigger_state, next_fire_time, job_name, job_group
     * from {0}triggers where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String SELECT_TRIGGER_STATUS = "SELECT "
            + COL_TRIGGER_STATE + ", " + COL_NEXT_FIRE_TIME + ", "
            + COL_JOB_NAME + ", " + COL_JOB_GROUP + " FROM "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * select * from {0}simple_triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String SELECT_SIMPLE_TRIGGER = "SELECT *" + " FROM "
            + TABLE_PREFIX_SUBST + TABLE_SIMPLE_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * select * from {0}cron_triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String SELECT_CRON_TRIGGER = "SELECT *" + " FROM "
            + TABLE_PREFIX_SUBST + TABLE_CRON_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * select * from {0}blob_triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String SELECT_BLOB_TRIGGER = "SELECT *" + " FROM "
            + TABLE_PREFIX_SUBST + TABLE_BLOB_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * select count(trigger_name) from {0}triggers
     * where sched_name = {1}
     * </pre>
     */
    String SELECT_NUM_TRIGGERS = "SELECT COUNT("
            + COL_TRIGGER_NAME + ") " + " FROM " + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * select count(trigger_name) from {0}triggers
     * where sched_name = {1} and trigger_group = ?
     * </pre>
     */
    String SELECT_NUM_TRIGGERS_IN_GROUP = "SELECT COUNT("
            + COL_TRIGGER_NAME + ") " + " FROM " + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * select distinct(trigger_group) from {0}triggers
     * where sched_name = {1}
     * </pre>
     */
    String SELECT_TRIGGER_GROUPS = "SELECT DISTINCT("
            + COL_TRIGGER_GROUP + ") FROM " + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * select distinct(trigger_group) from {0}triggers
     * where sched_name = {1} and trigger_group like ?
     * </pre>
     */
    String SELECT_TRIGGER_GROUPS_FILTERED = "SELECT DISTINCT("
            + COL_TRIGGER_GROUP + ") FROM " + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST + " AND " + COL_TRIGGER_GROUP + " LIKE ?";
    /**
     * <pre>
     * select trigger_name, trigger_group from {0}triggers
     * where sched_name = {1} and trigger_group like ?
     * </pre>
     */
    String SELECT_TRIGGERS_IN_GROUP_LIKE = "SELECT "
            + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + " FROM " + TABLE_PREFIX_SUBST + TABLE_TRIGGERS
            + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_GROUP + " LIKE ?";
    /**
     * <pre>
     * select trigger_name, trigger_group from {0}triggers
     * where sched_name = {1} and trigger_group = ?
     * </pre>
     */
    String SELECT_TRIGGERS_IN_GROUP = "SELECT "
            + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + " FROM " + TABLE_PREFIX_SUBST + TABLE_TRIGGERS
            + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * insert into {0}calendars (sched_name, calendar_name, calendar) values({1}, ?, ?)
     * </pre>
     */
    String INSERT_CALENDAR = "INSERT INTO "
            + TABLE_PREFIX_SUBST + TABLE_CALENDARS + " (" + COL_SCHEDULER_NAME + ", " + COL_CALENDAR_NAME
            + ", " + COL_CALENDAR + ") " + " VALUES(" + SCHED_NAME_SUBST + ", ?, ?)";
    /**
     * <pre>
     * update {0}calendars set calendar = ?
     * where sched_name = {1} and calendar_name = ?
     * </pre>
     */
    String UPDATE_CALENDAR = "UPDATE " + TABLE_PREFIX_SUBST
            + TABLE_CALENDARS + " SET " + COL_CALENDAR + " = ? " + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_CALENDAR_NAME + " = ?";
    /**
     * <pre>
     * select calendar_name from {0}calendars
     * where sched_name = {1} and calendar_name = ?
     * </pre>
     */
    String SELECT_CALENDAR_EXISTENCE = "SELECT "
            + COL_CALENDAR_NAME + " FROM " + TABLE_PREFIX_SUBST
            + TABLE_CALENDARS + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_CALENDAR_NAME + " = ?";
    /**
     * <pre>
     * select * from {0}calendars
     * where sched_name = {1} and calendar_name = ?
     * </pre>
     */
    String SELECT_CALENDAR = "SELECT *" + " FROM "
            + TABLE_PREFIX_SUBST + TABLE_CALENDARS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_CALENDAR_NAME + " = ?";
    /**
     * <pre>
     * select calendar_name from {0}triggers
     * where sched_name = {1} and calendar_name = ?
     * </pre>
     */
    String SELECT_REFERENCED_CALENDAR = "SELECT "
            + COL_CALENDAR_NAME + " FROM " + TABLE_PREFIX_SUBST
            + TABLE_TRIGGERS + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_CALENDAR_NAME + " = ?";
    /**
     * <pre>
     * delete from {0}calendars
     * where sched_name = {1} and calendar_name = ?
     * </pre>
     */
    String DELETE_CALENDAR = "DELETE FROM "
            + TABLE_PREFIX_SUBST + TABLE_CALENDARS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_CALENDAR_NAME + " = ?";
    /**
     * <pre>
     * select count(calendar_name)  from {0}calendars
     * where sched_name = {1}
     * </pre>
     */
    String SELECT_NUM_CALENDARS = "SELECT COUNT("
            + COL_CALENDAR_NAME + ") " + " FROM " + TABLE_PREFIX_SUBST
            + TABLE_CALENDARS + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * select calendar_name from {0}calendars
     * where sched_name = {1}
     * </pre>
     */
    String SELECT_CALENDARS = "SELECT " + COL_CALENDAR_NAME
            + " FROM " + TABLE_PREFIX_SUBST + TABLE_CALENDARS
            + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * select min(next_fire_time) as alias_nxt_fr_tm from {0}triggers
     * where sched_name = {1} and trigger_state = ? and next_fire_time >= 0
     * </pre>
     */
    String SELECT_NEXT_FIRE_TIME = "SELECT MIN("
            + COL_NEXT_FIRE_TIME + ") AS " + ALIAS_COL_NEXT_FIRE_TIME
            + " FROM " + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_STATE + " = ? AND " + COL_NEXT_FIRE_TIME + " >= 0";
    /**
     * <pre>
     * select trigger_name, trigger_group from {0}triggers
     * where sched_name = {1} and trigger_state = ? and next_fire_time = ?
     * </pre>
     */
    String SELECT_TRIGGER_FOR_FIRE_TIME = "SELECT "
            + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + " FROM "
            + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_STATE + " = ? AND " + COL_NEXT_FIRE_TIME + " = ?";
    /**
     * <pre>
     * select trigger_name, trigger_group, next_fire_time, priority from {0}triggers
     * where sched_name = {1} and trigger_state = ? and next_fire_time <= ?
     *   and (misfire_instr = -1 or (misfire_instr != -1 and next_fire_time >= ?))
     * order by next_fire_time asc, priority desc
     * </pre>
     */
    String SELECT_NEXT_TRIGGER_TO_ACQUIRE = "SELECT "
        + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + ", "
        + COL_NEXT_FIRE_TIME + ", " + COL_PRIORITY + " FROM "
        + TABLE_PREFIX_SUBST + TABLE_TRIGGERS + " WHERE "
        + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
        + " AND " + COL_TRIGGER_STATE + " = ? AND " + COL_NEXT_FIRE_TIME + " <= ? " 
        + "AND (" + COL_MISFIRE_INSTRUCTION + " = -1 OR (" +COL_MISFIRE_INSTRUCTION+ " != -1 AND "+ COL_NEXT_FIRE_TIME + " >= ?)) "
        + "ORDER BY "+ COL_NEXT_FIRE_TIME + " ASC, " + COL_PRIORITY + " DESC";

    /**
     * <pre>
     * insert into {0}fired_triggers (
     * sched_name, entry_id, trigger_name, trigger_group, instance_name,
     * fired_time, sched_time, state, job_name, job_group, is_nonconcurrent,
     * requests_recovery, priority
     * ) values({1}, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     * </pre>
     */
    String INSERT_FIRED_TRIGGER = "INSERT INTO "
            + TABLE_PREFIX_SUBST + TABLE_FIRED_TRIGGERS + " (" + COL_SCHEDULER_NAME + ", " + COL_ENTRY_ID
            + ", " + COL_TRIGGER_NAME + ", " + COL_TRIGGER_GROUP + ", "
            + COL_INSTANCE_NAME + ", "
            + COL_FIRED_TIME + ", " + COL_SCHED_TIME + ", " + COL_ENTRY_STATE + ", " + COL_JOB_NAME
            + ", " + COL_JOB_GROUP + ", " + COL_IS_NONCONCURRENT + ", "
            + COL_REQUESTS_RECOVERY + ", " + COL_PRIORITY
            + ") VALUES(" + SCHED_NAME_SUBST + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    /**
     * <pre>
     * update {0}fired_triggers
     * set instance_name = ?, fired_time = ?, sched_time = ?, state = ?, job_name = ?,
     *     job_group = ?, is_nonconcurrent = ?, requests_recovery = ?
     * where sched_name = {1} and entry_id = ?
     * </pre>
     */
    String UPDATE_FIRED_TRIGGER = "UPDATE "
        + TABLE_PREFIX_SUBST + TABLE_FIRED_TRIGGERS + " SET " 
        + COL_INSTANCE_NAME + " = ?, "
        + COL_FIRED_TIME + " = ?, " + COL_SCHED_TIME + " = ?, " + COL_ENTRY_STATE + " = ?, " + COL_JOB_NAME
        + " = ?, " + COL_JOB_GROUP + " = ?, " + COL_IS_NONCONCURRENT + " = ?, "
        + COL_REQUESTS_RECOVERY + " = ? WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
        + " AND " + COL_ENTRY_ID + " = ?";
    /**
     * <pre>
     * select * from {0}fired_triggers
     * where sched_name = {1} and instance_name = ?
     * </pre>
     */
    String SELECT_INSTANCES_FIRED_TRIGGERS = "SELECT * FROM "
            + TABLE_PREFIX_SUBST
            + TABLE_FIRED_TRIGGERS
            + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_INSTANCE_NAME + " = ?";
    /**
     * <pre>
     * select * from {0}fired_triggers
     * where sched_name = {1} and instance_name = ? and requests_recovery = ?
     * </pre>
     */
    String SELECT_INSTANCES_RECOVERABLE_FIRED_TRIGGERS = "SELECT * FROM "
            + TABLE_PREFIX_SUBST
            + TABLE_FIRED_TRIGGERS
            + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_INSTANCE_NAME + " = ? AND " + COL_REQUESTS_RECOVERY + " = ?";


    /**
     * <pre>
     * select count(trigger_name) from {0}fired_triggers
     * where sched_name = {1} and job_name = ? and job_group = ?
     * </pre>
     */
    String SELECT_JOB_EXECUTION_COUNT = "SELECT COUNT("
            + COL_TRIGGER_NAME + ") FROM " + TABLE_PREFIX_SUBST
            + TABLE_FIRED_TRIGGERS + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_JOB_NAME + " = ? AND "
            + COL_JOB_GROUP + " = ?";
    /**
     * <pre>
     * select * from {0}fired_triggers
     * where sched_name = {1}
     * </pre>
     */
    String SELECT_FIRED_TRIGGERS = "SELECT * FROM "
            + TABLE_PREFIX_SUBST + TABLE_FIRED_TRIGGERS
            + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * select * from {0}fired_triggers
     * where sched_name = {1} and trigger_name = ? and trigger_group = ?
     * </pre>
     */
    String SELECT_FIRED_TRIGGER = "SELECT * FROM "
            + TABLE_PREFIX_SUBST + TABLE_FIRED_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_NAME + " = ? AND " + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * select * from {0}fired_triggers
     * where sched_name = {1} and trigger_group = ?
     * </pre>
     */
    String SELECT_FIRED_TRIGGER_GROUP = "SELECT * FROM "
            + TABLE_PREFIX_SUBST + TABLE_FIRED_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_GROUP + " = ?";
    /**
     * <pre>
     * select * from {0}fired_triggers
     * where sched_name = {1} and job_name = ? and job_group = ?
     * </pre>
     */
    String SELECT_FIRED_TRIGGERS_OF_JOB = "SELECT * FROM "
            + TABLE_PREFIX_SUBST + TABLE_FIRED_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_JOB_NAME + " = ? AND " + COL_JOB_GROUP + " = ?";
    /**
     * <pre>
     * select * from {0}fired_triggers
     * where sched_name = {1} and job_group = ?
     * </pre>
     */
    String SELECT_FIRED_TRIGGERS_OF_JOB_GROUP = "SELECT * FROM "
            + TABLE_PREFIX_SUBST
            + TABLE_FIRED_TRIGGERS
            + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_JOB_GROUP + " = ?";
    /**
     * <pre>
     * delete from {0}fired_triggers
     * where sched_name = {1} and entry_id = ?
     * </pre>
     */
    String DELETE_FIRED_TRIGGER = "DELETE FROM "
            + TABLE_PREFIX_SUBST + TABLE_FIRED_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_ENTRY_ID + " = ?";
    /**
     * <pre>
     * delete from {0}fired_triggers
     * where sched_name = {1} and instance_name = ?
     * </pre>
     */
    String DELETE_INSTANCES_FIRED_TRIGGERS = "DELETE FROM "
            + TABLE_PREFIX_SUBST + TABLE_FIRED_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_INSTANCE_NAME + " = ?";
    /**
     * <pre>
     * delete from {0}fired_triggers
     * where sched_name = {1} and instance_name = ?requests_recovery = ?
     * </pre>
     */
    String DELETE_NO_RECOVERY_FIRED_TRIGGERS = "DELETE FROM "
            + TABLE_PREFIX_SUBST
            + TABLE_FIRED_TRIGGERS
            + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_INSTANCE_NAME + " = ?" + COL_REQUESTS_RECOVERY + " = ?";
    /**
     * <pre>
     * delete from {0}simple_triggers
     * where sched_name = {1}
     * </pre>
     */
    String DELETE_ALL_SIMPLE_TRIGGERS = "DELETE FROM " + TABLE_PREFIX_SUBST + "SIMPLE_TRIGGERS " + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * delete from {0}simprop_triggers
     * where sched_name = {1}
     * </pre>
     */
    String DELETE_ALL_SIMPROP_TRIGGERS = "DELETE FROM " + TABLE_PREFIX_SUBST + "SIMPROP_TRIGGERS " + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * delete from {0}cron_triggers
     * where sched_name = {1}
     * </pre>
     */
    String DELETE_ALL_CRON_TRIGGERS = "DELETE FROM " + TABLE_PREFIX_SUBST + "CRON_TRIGGERS" + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * delete from {0}blob_triggers
     * where sched_name = {1}
     * </pre>
     */
    String DELETE_ALL_BLOB_TRIGGERS = "DELETE FROM " + TABLE_PREFIX_SUBST + "BLOB_TRIGGERS" + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * delete from {0}triggers
     * where sched_name = {1}
     * </pre>
     */
    String DELETE_ALL_TRIGGERS = "DELETE FROM " + TABLE_PREFIX_SUBST + "TRIGGERS" + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * delete from {0}job_details
     * where sched_name = {1}
     * </pre>
     */
    String DELETE_ALL_JOB_DETAILS = "DELETE FROM " + TABLE_PREFIX_SUBST + "JOB_DETAILS" + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * delete from {0}calendars
     * where sched_name = {1}
     * </pre>
     */
    String DELETE_ALL_CALENDARS = "DELETE FROM " + TABLE_PREFIX_SUBST + "CALENDARS" + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * delete from {0}paused_trigger_grps
     * where sched_name = {1}
     * </pre>
     */
    String DELETE_ALL_PAUSED_TRIGGER_GRPS = "DELETE FROM " + TABLE_PREFIX_SUBST + "PAUSED_TRIGGER_GRPS" + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * select distinct instance_name from {0}fired_triggers
     * where sched_name = {1}
     * </pre>
     */
    String SELECT_FIRED_TRIGGER_INSTANCE_NAMES = 
            "SELECT DISTINCT " + COL_INSTANCE_NAME + " FROM "
            + TABLE_PREFIX_SUBST
            + TABLE_FIRED_TRIGGERS
            + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * insert into {0}scheduler_state (
     * sched_name, instance_name, last_checkin_time, checkin_interval
     * ) values({1}, ?, ?, ?)
     * </pre>
     */
    String INSERT_SCHEDULER_STATE = "INSERT INTO "
            + TABLE_PREFIX_SUBST + TABLE_SCHEDULER_STATE + " ("
            + COL_SCHEDULER_NAME + ", "
            + COL_INSTANCE_NAME + ", " + COL_LAST_CHECKIN_TIME + ", "
            + COL_CHECKIN_INTERVAL + ") VALUES(" + SCHED_NAME_SUBST + ", ?, ?, ?)";
    /**
     * <pre>
     * select * from {0}scheduler_state
     * where sched_name = {1} and instance_name = ?
     * </pre>
     */
    String SELECT_SCHEDULER_STATE = "SELECT * FROM "
            + TABLE_PREFIX_SUBST + TABLE_SCHEDULER_STATE + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_INSTANCE_NAME + " = ?";

    /**
     * <pre>
     * select * from {0}scheduler_state
     * where sched_name = {1}
     * </pre>
     */
    String SELECT_SCHEDULER_STATES = "SELECT * FROM "
            + TABLE_PREFIX_SUBST + TABLE_SCHEDULER_STATE
            + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;

    /**
     * <pre>
     * delete from {0}scheduler_state
     * where sched_name = {1} and instance_name = ?
     * </pre>
     */
    String DELETE_SCHEDULER_STATE = "DELETE FROM "
        + TABLE_PREFIX_SUBST + TABLE_SCHEDULER_STATE + " WHERE "
        + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
        + " AND " + COL_INSTANCE_NAME + " = ?";

    /**
     * <pre>
     * update {0}scheduler_state
     * set last_checkin_time = ?
     * where sched_name = {1} and instance_name = ?
     * </pre>
     */
    String UPDATE_SCHEDULER_STATE = "UPDATE "
        + TABLE_PREFIX_SUBST + TABLE_SCHEDULER_STATE + " SET " 
        + COL_LAST_CHECKIN_TIME + " = ? WHERE "
        + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
        + " AND " + COL_INSTANCE_NAME + " = ?";

    /**
     * <pre>
     * insert into {0}paused_trigger_grps (
     * sched_name, trigger_group
     * ) values({1}, ?)
     * </pre>
     */
    String INSERT_PAUSED_TRIGGER_GROUP = "INSERT INTO "
            + TABLE_PREFIX_SUBST + TABLE_PAUSED_TRIGGERS + " ("
            + COL_SCHEDULER_NAME + ", "
            + COL_TRIGGER_GROUP + ") VALUES(" + SCHED_NAME_SUBST + ", ?)";

    /**
     * <pre>
     * select trigger_group from {0}paused_trigger_grps
     * where sched_name = {1} and trigger_group = ?
     * </pre>
     */
    String SELECT_PAUSED_TRIGGER_GROUP = "SELECT "
            + COL_TRIGGER_GROUP + " FROM " + TABLE_PREFIX_SUBST
            + TABLE_PAUSED_TRIGGERS + " WHERE " 
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_GROUP + " = ?";

    /**
     * <pre>
     * select trigger_group from {0}paused_trigger_grps
     * where sched_name = {1}
     * </pre>
     */
    String SELECT_PAUSED_TRIGGER_GROUPS = "SELECT "
        + COL_TRIGGER_GROUP + " FROM " + TABLE_PREFIX_SUBST
        + TABLE_PAUSED_TRIGGERS
        + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;
    /**
     * <pre>
     * delete from {0}paused_trigger_grps
     * where sched_name = {1} and trigger_group like ?
     * </pre>
     */
    String DELETE_PAUSED_TRIGGER_GROUP = "DELETE FROM "
            + TABLE_PREFIX_SUBST + TABLE_PAUSED_TRIGGERS + " WHERE "
            + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_TRIGGER_GROUP + " LIKE ?";
    /**
     * <pre>
     * delete from {0}paused_trigger_grps
     * where sched_name = {1}
     * </pre>
     */
    String DELETE_PAUSED_TRIGGER_GROUPS = "DELETE FROM "
            + TABLE_PREFIX_SUBST + TABLE_PAUSED_TRIGGERS
            + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST;

    //  CREATE TABLE qrtz_scheduler_state(INSTANCE_NAME VARCHAR2(80) NOT NULL,
    // LAST_CHECKIN_TIME NUMBER(13) NOT NULL, CHECKIN_INTERVAL NUMBER(13) NOT
    // NULL, PRIMARY KEY (INSTANCE_NAME));

}

// EOF
