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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 基于数据库行锁实现的信号量
 *
 * Internal database based lock handler for providing thread/resource locking 
 * in order to protect resources from being altered by multiple threads at the 
 * same time.
 * 
 * @author jhouse
 */
public class StdRowLockSemaphore extends DBSemaphore {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Constants.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    // select * from QRTZ_LOCKS where SCHED_NAME = QuartzScheduler and LOCK_NAME = ? for update
    // for update: 在读取行上（有索引）设置一个排他锁，防止其他session读取或者写入行数据
    public static final String SELECT_FOR_LOCK = "SELECT * FROM "
            + TABLE_PREFIX_SUBST + TABLE_LOCKS + " WHERE " + COL_SCHEDULER_NAME + " = " + SCHED_NAME_SUBST
            + " AND " + COL_LOCK_NAME + " = ? FOR UPDATE";

    // insert into QRTZ_LOCKS(SCHED_NAME, LOCK_NAME) values(QuartzScheduler, ?);
    // insert: 对应的索引记录上加一个排它锁（record lock），不会阻塞其他session在gap间隙里插入记录，
    // 在insert操作之前，还会加一种插入意向间隙锁，只要插入的记录不是gap间隙中的相同位置，则无需等待其他session就可完成
    public static final String INSERT_LOCK = "INSERT INTO "
        + TABLE_PREFIX_SUBST + TABLE_LOCKS + "(" + COL_SCHEDULER_NAME + ", " + COL_LOCK_NAME + ") VALUES (" 
        + SCHED_NAME_SUBST + ", ?)"; 

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Constructors.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    public StdRowLockSemaphore() {
        super(DEFAULT_TABLE_PREFIX, null, SELECT_FOR_LOCK, INSERT_LOCK);
    }

    public StdRowLockSemaphore(String tablePrefix, String schedName, String selectWithLockSQL) {
        super(tablePrefix, schedName, selectWithLockSQL != null ? selectWithLockSQL : SELECT_FOR_LOCK, INSERT_LOCK);
    }

    // Data Members

    // Configurable lock retry parameters
    private int maxRetry = 3; // 最大重试次数
    private long retryPeriod = 1000L; // 重试间隔

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public void setRetryPeriod(long retryPeriod) {
        this.retryPeriod = retryPeriod;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public long getRetryPeriod() {
        return retryPeriod;
    }

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Interface.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    /**
     * Execute the SQL select for update that will lock the proper database row.
     */
    @Override
    protected void executeSQL(Connection conn, final String lockName, final String expandedSQL, final String expandedInsertSQL) throws LockException {
        PreparedStatement ps = null;
        ResultSet rs = null;
        SQLException initCause = null;
        
        // attempt lock two times (to work-around possible race conditions in inserting the lock row the first time running)
        int count = 0;

        // Configurable lock retry attempts
        int maxRetryLocal = this.maxRetry;
        long retryPeriodLocal = this.retryPeriod;

        do {
            count++;
            try {
                ps = conn.prepareStatement(expandedSQL);
                ps.setString(1, lockName);
                
                if (getLog().isDebugEnabled()) {
                    getLog().debug(
                        "Lock '" + lockName + "' is being obtained: " + 
                        Thread.currentThread().getName());
                }
                rs = ps.executeQuery();
                if (!rs.next()) { // 先查后插（查不成功则插入）
                    getLog().debug(
                            "Inserting new lock row for lock: '" + lockName + "' being obtained by thread: " + 
                            Thread.currentThread().getName());
                    rs.close();
                    rs = null;
                    ps.close();
                    ps = null;
                    ps = conn.prepareStatement(expandedInsertSQL);
                    ps.setString(1, lockName);
    
                    int res = ps.executeUpdate();
                    
                    if(res != 1) { // 插入失败
                        if(count < maxRetryLocal) { // 前maxRetry-1次重试，需要休眠一会继续试
                            // pause a bit to give another thread some time to commit the insert of the new lock row
                            try {
                                Thread.sleep(retryPeriodLocal);
                            } catch (InterruptedException ignore) {
                                Thread.currentThread().interrupt();
                            }
                            // try again ...
                            continue;
                        }

                        // 已达到则抛异常
                        throw new SQLException(Util.rtp(
                            "No row exists, and one could not be inserted in table " + TABLE_PREFIX_SUBST + TABLE_LOCKS + 
                            " for lock named: " + lockName, getTablePrefix(), getSchedulerNameLiteral()));
                    }
                }
                
                return; // obtained lock, go
            } catch (SQLException sqle) {
                //Exception src =
                // (Exception)getThreadLocksObtainer().get(lockName);
                //if(src != null)
                //  src.printStackTrace();
                //else
                //  System.err.println("--- ***************** NO OBTAINER!");
    
                if(initCause == null)
                    initCause = sqle;
                
                if (getLog().isDebugEnabled()) {
                    getLog().debug(
                        "Lock '" + lockName + "' was not obtained by: " + 
                        Thread.currentThread().getName() + (count < maxRetryLocal ? " - will try again." : ""));
                }
                
                if(count < maxRetryLocal) {
                    // pause a bit to give another thread some time to commit the insert of the new lock row
                    try {
                        Thread.sleep(retryPeriodLocal);
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                    }
                    // try again ...
                    continue;
                }
                
                throw new LockException("Failure obtaining db row lock: "
                        + sqle.getMessage(), sqle);
            } finally {
                if (rs != null) { 
                    try {
                        rs.close();
                    } catch (Exception ignore) {
                    }
                }
                if (ps != null) {
                    try {
                        ps.close();
                    } catch (Exception ignore) {
                    }
                }
            }
        } while(count < (maxRetryLocal + 1));
        
        throw new LockException("Failure obtaining db row lock, reached maximum number of attempts. Initial exception (if any) attached as root cause.", initCause);
    }

    protected String getSelectWithLockSQL() {
        return getSQL();
    }

    public void setSelectWithLockSQL(String selectWithLockSQL) {
        setSQL(selectWithLockSQL);
    }
}
