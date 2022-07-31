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

package org.quartz.spi;

import java.util.Date;

import org.quartz.Calendar;
import org.quartz.JobDetail;

/**
 * <p>用于将执行时数据从 JobStore 返回到 QuartzSchedulerThread
 *
 * A simple class (structure) used for returning execution-time data from the
 * JobStore to the <code>QuartzSchedulerThread</code>.
 * </p>
 * 
 * @see org.quartz.core.QuartzSchedulerThread
 * 
 * @author James House
 */
public class TriggerFiredBundle implements java.io.Serializable {
  
    private static final long serialVersionUID = -6414106108306999265L;

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Data members.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    private JobDetail job; // 触发的job

    private OperableTrigger trigger; // 触发器

    private Calendar cal; // 触发器关联的日历

    private boolean jobIsRecovering; // job是否在恢复模式

    private Date fireTime; // 点火时间

    private Date scheduledFireTime; // 计划点火时间

    private Date prevFireTime; // 上一次点火时间

    private Date nextFireTime; // 下一次点火时间

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Constructors.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    public TriggerFiredBundle(JobDetail job, OperableTrigger trigger, Calendar cal,
            boolean jobIsRecovering, Date fireTime, Date scheduledFireTime,
            Date prevFireTime, Date nextFireTime) {
        this.job = job;
        this.trigger = trigger;
        this.cal = cal;
        this.jobIsRecovering = jobIsRecovering;
        this.fireTime = fireTime;
        this.scheduledFireTime = scheduledFireTime;
        this.prevFireTime = prevFireTime;
        this.nextFireTime = nextFireTime;
    }

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Interface.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    public JobDetail getJobDetail() {
        return job;
    }

    public OperableTrigger getTrigger() {
        return trigger;
    }

    public Calendar getCalendar() {
        return cal;
    }

    public boolean isRecovering() {
        return jobIsRecovering;
    }

    /**
     * @return Returns the fireTime.
     */
    public Date getFireTime() {
        return fireTime;
    }

    /**
     * @return Returns the nextFireTime.
     */
    public Date getNextFireTime() {
        return nextFireTime;
    }

    /**
     * @return Returns the prevFireTime.
     */
    public Date getPrevFireTime() {
        return prevFireTime;
    }

    /**
     * @return Returns the scheduledFireTime.
     */
    public Date getScheduledFireTime() {
        return scheduledFireTime;
    }

}