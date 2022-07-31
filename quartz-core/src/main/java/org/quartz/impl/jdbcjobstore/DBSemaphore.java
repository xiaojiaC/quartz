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
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DB信号量，基于数据库实现信号量
 *
 * Base class for database based lock handlers for providing thread/resource locking 
 * in order to protect resources from being altered by multiple threads at the 
 * same time.
 */
public abstract class DBSemaphore implements Semaphore, Constants,
    StdJDBCConstants, TablePrefixAware {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Data members.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    ThreadLocal<HashSet<String>> lockOwners = new ThreadLocal<HashSet<String>>(); // 锁持有者

    private String sql; // 原始查询模板sql
    private String insertSql; // 原始插入模板sql

    private String tablePrefix; // 表前缀
    
    private String schedName; // 调度器名称

    private String expandedSQL; // 查询sql（填充了表前缀和调度器名称）
    private String expandedInsertSQL; // 模板sql

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Constructors.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    public DBSemaphore(String tablePrefix, String schedName, String defaultSQL, String defaultInsertSQL) {
        this.tablePrefix = tablePrefix;
        this.schedName = schedName;
        setSQL(defaultSQL);
        setInsertSQL(defaultInsertSQL);
    }

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Interface.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    protected Logger getLog() {
        return log;
    }

    private HashSet<String> getThreadLocks() {
        HashSet<String> threadLocks = lockOwners.get();
        if (threadLocks == null) {
            threadLocks = new HashSet<String>();
            lockOwners.set(threadLocks);
        }
        return threadLocks;
    }

    /**
     * Execute the SQL that will lock the proper database row.
     */
    protected abstract void executeSQL(Connection conn, String lockName, String theExpandedSQL, String theExpandedInsertSQL) 
        throws LockException;
    
    /**
     * Grants a lock on the identified resource to the calling thread (blocking
     * until it is available).
     * 
     * @return true if the lock was obtained.
     */
    public boolean obtainLock(Connection conn, String lockName)
        throws LockException {

        if(log.isDebugEnabled()) {
            log.debug(
                "Lock '" + lockName + "' is desired by: "
                        + Thread.currentThread().getName());
        }
        if (!isLockOwner(lockName)) { // 未持有则尝试获取数据库锁

            executeSQL(conn, lockName, expandedSQL, expandedInsertSQL); // 获取锁
            
            if(log.isDebugEnabled()) {
                log.debug(
                    "Lock '" + lockName + "' given to: "
                            + Thread.currentThread().getName());
            }
            getThreadLocks().add(lockName); // 成功则记录持有，失败直接抛异常了到不了这行
            //getThreadLocksObtainer().put(lockName, new
            // Exception("Obtainer..."));
        } else if(log.isDebugEnabled()) { // 已持有（类似重入）
            log.debug(
                "Lock '" + lockName + "' Is already owned by: "
                        + Thread.currentThread().getName());
        }

        return true;
    }

       
    /**
     * Release the lock on the identified resource if it is held by the calling
     * thread.
     */
    public void releaseLock(String lockName) {

        if (isLockOwner(lockName)) { // 是持有则可释放
            if(getLog().isDebugEnabled()) {
                getLog().debug(
                    "Lock '" + lockName + "' returned by: "
                            + Thread.currentThread().getName());
            }
            getThreadLocks().remove(lockName); // 移除锁记录，数据库锁commit结束自动释放了
            //getThreadLocksObtainer().remove(lockName);
        } else if (getLog().isDebugEnabled()) { // 未持有则抛异常
            getLog().warn(
                "Lock '" + lockName + "' attempt to return by: "
                        + Thread.currentThread().getName()
                        + " -- but not owner!",
                new Exception("stack-trace of wrongful returner"));
        }
    }

    /**
     * Determine whether the calling thread owns a lock on the identified
     * resource.
     */
    public boolean isLockOwner(String lockName) {
        // 检查锁是否被当前线程持有
        return getThreadLocks().contains(lockName);
    }

    /**
     * This Semaphore implementation does use the database.
     */
    public boolean requiresConnection() {
        return true;
    }

    protected String getSQL() {
        return sql;
    }

    protected void setSQL(String sql) {
        if ((sql != null) && (sql.trim().length() != 0)) {
            this.sql = sql.trim();
        }
        
        setExpandedSQL();
    }

    protected void setInsertSQL(String insertSql) {
        if ((insertSql != null) && (insertSql.trim().length() != 0)) {
            this.insertSql = insertSql.trim();
        }
        
        setExpandedSQL();
    }

    private void setExpandedSQL() {
        if (getTablePrefix() != null && getSchedName() != null && sql != null && insertSql != null) {
            expandedSQL = Util.rtp(this.sql, getTablePrefix(), getSchedulerNameLiteral());
            expandedInsertSQL = Util.rtp(this.insertSql, getTablePrefix(), getSchedulerNameLiteral());
        }
    }
    
    private String schedNameLiteral = null;
    protected String getSchedulerNameLiteral() {
        if(schedNameLiteral == null)
            schedNameLiteral = "'" + schedName + "'";
        return schedNameLiteral;
    }

    public String getSchedName() {
        return schedName;
    }

    public void setSchedName(String schedName) {
        this.schedName = schedName;
        
        setExpandedSQL();
    }
    
    protected String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
        
        setExpandedSQL();
    }
}
