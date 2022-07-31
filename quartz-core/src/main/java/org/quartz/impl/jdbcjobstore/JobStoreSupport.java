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

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.quartz.Calendar;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobPersistenceException;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerConfigException;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.Trigger.TriggerState;
import org.quartz.TriggerKey;
import org.quartz.impl.DefaultThreadExecutor;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.impl.matchers.StringMatcher;
import org.quartz.impl.matchers.StringMatcher.StringOperatorName;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.spi.ClassLoadHelper;
import org.quartz.spi.JobStore;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.SchedulerSignaler;
import org.quartz.spi.ThreadExecutor;
import org.quartz.spi.TriggerFiredBundle;
import org.quartz.spi.TriggerFiredResult;
import org.quartz.utils.DBConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * <p>
 * Contains base functionality for JDBC-based JobStore implementations.
 * </p>
 * 
 * @author <a href="mailto:jeff@binaryfeed.org">Jeffrey Wescott</a>
 * @author James House
 */
public abstract class JobStoreSupport implements JobStore, Constants {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Constants.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    protected static final String LOCK_TRIGGER_ACCESS = "TRIGGER_ACCESS";

    protected static final String LOCK_STATE_ACCESS = "STATE_ACCESS";

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Data members.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    protected String dsName;

    protected String tablePrefix = DEFAULT_TABLE_PREFIX;

    protected boolean useProperties = false;

    protected String instanceId;

    protected String instanceName;
    
    protected String delegateClassName;

    protected String delegateInitString;
    
    protected Class<? extends DriverDelegate> delegateClass = StdJDBCDelegate.class;

    protected HashMap<String, Calendar> calendarCache = new HashMap<String, Calendar>();

    private DriverDelegate delegate;

    private long misfireThreshold = 60000L; // one minute

    private boolean dontSetAutoCommitFalse = false;

    private boolean isClustered = false;

    private boolean useDBLocks = false;
    
    private boolean lockOnInsert = true;

    private Semaphore lockHandler = null; // set in initialize() method...

    private String selectWithLockSQL = null;

    private long clusterCheckinInterval = 7500L;

    private ClusterManager clusterManagementThread = null;

    private MisfireHandler misfireHandler = null;

    private ClassLoadHelper classLoadHelper;

    private SchedulerSignaler schedSignaler;

    protected int maxToRecoverAtATime = 20;
    
    private boolean setTxIsolationLevelSequential = false;
    
    private boolean acquireTriggersWithinLock = false;
    
    private long dbRetryInterval = 15000L; // 15 secs
    
    private boolean makeThreadsDaemons = false;

    private boolean threadsInheritInitializersClassLoadContext = false;
    private ClassLoader initializersLoader = null;
    
    private boolean doubleCheckLockMisfireHandler = true;
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private ThreadExecutor threadExecutor = new DefaultThreadExecutor();
    
    private volatile boolean schedulerRunning = false;
    private volatile boolean shutdown = false;
    
    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Interface.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    /**
     * <p>
     * Set the name of the <code>DataSource</code> that should be used for
     * performing database functions.
     * </p>
     */
    public void setDataSource(String dsName) {
        this.dsName = dsName;
    }

    /**
     * <p>
     * Get the name of the <code>DataSource</code> that should be used for
     * performing database functions.
     * </p>
     */
    public String getDataSource() {
        return dsName;
    }

    /**
     * <p>
     * Set the prefix that should be pre-pended to all table names.
     * </p>
     */
    public void setTablePrefix(String prefix) {
        if (prefix == null) {
            prefix = "";
        }

        this.tablePrefix = prefix;
    }

    /**
     * <p>
     * Get the prefix that should be pre-pended to all table names.
     * </p>
     */
    public String getTablePrefix() {
        return tablePrefix;
    }

    /**
     * <p>
     * Set whether String-only properties will be handled in JobDataMaps.
     * </p>
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setUseProperties(String useProp) {
        if (useProp == null) {
            useProp = "false";
        }

        this.useProperties = Boolean.valueOf(useProp);
    }

    /**
     * <p>
     * Get whether String-only properties will be handled in JobDataMaps.
     * </p>
     */
    public boolean canUseProperties() {
        return useProperties;
    }

    /**
     * <p>
     * Set the instance Id of the Scheduler (must be unique within a cluster).
     * </p>
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * <p>
     * Get the instance Id of the Scheduler (must be unique within a cluster).
     * </p>
     */
    public String getInstanceId() {

        return instanceId;
    }

    /**
     * Set the instance name of the Scheduler (must be unique within this server instance).
     */
    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public void setThreadPoolSize(final int poolSize) {
        //
    }
    
    public void setThreadExecutor(ThreadExecutor threadExecutor) {
        this.threadExecutor = threadExecutor;
    }
    
    public ThreadExecutor getThreadExecutor() {
        return threadExecutor;
    }
    

    /**
     * Get the instance name of the Scheduler (must be unique within this server instance).
     */
    public String getInstanceName() {

        return instanceName;
    }

    public long getEstimatedTimeToReleaseAndAcquireTrigger() {
        return 70;
    }

    /**
     * <p>
     * Set whether this instance is part of a cluster.
     * </p>
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setIsClustered(boolean isClustered) {
        this.isClustered = isClustered;
    }

    /**
     * <p>
     * Get whether this instance is part of a cluster.
     * </p>
     */
    public boolean isClustered() {
        return isClustered;
    }

    /**
     * <p>
     * Get the frequency (in milliseconds) at which this instance "checks-in"
     * with the other instances of the cluster. -- Affects the rate of
     * detecting failed instances.
     * </p>
     */
    public long getClusterCheckinInterval() {
        return clusterCheckinInterval;
    }

    /**
     * <p>
     * Set the frequency (in milliseconds) at which this instance "checks-in"
     * with the other instances of the cluster. -- Affects the rate of
     * detecting failed instances.
     * </p>
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setClusterCheckinInterval(long l) {
        clusterCheckinInterval = l;
    }

    /**
     * <p>
     * 获取失火处理线程一次尝试恢复的失火触发器的最大数量（在一个事务内）。 默认值为 20。
     *
     * Get the maximum number of misfired triggers that the misfire handling
     * thread will try to recover at one time (within one transaction).  The
     * default is 20.
     * </p>
     */
    public int getMaxMisfiresToHandleAtATime() {
        return maxToRecoverAtATime;
    }

    /**
     * <p>
     * Set the maximum number of misfired triggers that the misfire handling
     * thread will try to recover at one time (within one transaction).  The
     * default is 20.
     * </p>
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setMaxMisfiresToHandleAtATime(int maxToRecoverAtATime) {
        this.maxToRecoverAtATime = maxToRecoverAtATime;
    }

    /**
     * @return Returns the dbRetryInterval.
     */
    public long getDbRetryInterval() {
        return dbRetryInterval;
    }
    /**
     * @param dbRetryInterval The dbRetryInterval to set.
     */
    public void setDbRetryInterval(long dbRetryInterval) {
        this.dbRetryInterval = dbRetryInterval;
    }
    
    /**
     * <p>
     * Set whether this instance should use database-based thread
     * synchronization.
     * </p>
     */
    public void setUseDBLocks(boolean useDBLocks) {
        this.useDBLocks = useDBLocks;
    }

    /**
     * <p>
     * Get whether this instance should use database-based thread
     * synchronization.
     * </p>
     */
    public boolean getUseDBLocks() {
        return useDBLocks;
    }

    public boolean isLockOnInsert() {
        return lockOnInsert;
    }
    
    /**
     * Whether or not to obtain locks when inserting new jobs/triggers.  
     * <p>
     * Defaults to <code>true</code>, which is safest. Some databases (such as 
     * MS SQLServer) seem to require this to avoid deadlocks under high load,
     * while others seem to do fine without.  Settings this to false means
     * isolation guarantees between job scheduling and trigger acquisition are
     * entirely enforced by the database.  Depending on the database and it's
     * configuration this may cause unusual scheduling behaviors.
     * 
     * <p>Setting this property to <code>false</code> will provide a 
     * significant performance increase during the addition of new jobs 
     * and triggers.</p>
     * 
     * @param lockOnInsert whether locking should be used when inserting new jobs/triggers
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setLockOnInsert(boolean lockOnInsert) {
        this.lockOnInsert = lockOnInsert;
    }
    
    public long getMisfireThreshold() {
        return misfireThreshold;
    }

    /**
     * The the number of milliseconds by which a trigger must have missed its
     * next-fire-time, in order for it to be considered "misfired" and thus
     * have its misfire instruction applied.
     * 
     * @param misfireThreshold the misfire threshold to use, in millis
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setMisfireThreshold(long misfireThreshold) {
        if (misfireThreshold < 1) {
            throw new IllegalArgumentException(
                    "Misfirethreshold must be larger than 0");
        }
        this.misfireThreshold = misfireThreshold;
    }

    public boolean isDontSetAutoCommitFalse() {
        return dontSetAutoCommitFalse;
    }

    /**
     * Don't call set autocommit(false) on connections obtained from the
     * DataSource. This can be helpful in a few situations, such as if you
     * have a driver that complains if it is called when it is already off.
     * 
     * @param b whether or not autocommit should be set to false on db connections
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setDontSetAutoCommitFalse(boolean b) {
        dontSetAutoCommitFalse = b;
    }

    public boolean isTxIsolationLevelSerializable() {
        return setTxIsolationLevelSequential;
    }

    /**
     * Set the transaction isolation level of DB connections to sequential.
     * 
     * @param b whether isolation level should be set to sequential.
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setTxIsolationLevelSerializable(boolean b) {
        setTxIsolationLevelSequential = b;
    }

    /**
     * Whether or not the query and update to acquire a Trigger for firing
     * should be performed after obtaining an explicit DB lock (to avoid 
     * possible race conditions on the trigger's db row).  This is the
     * behavior prior to Quartz 1.6.3, but is considered unnecessary for most
     * databases (due to the nature of the SQL update that is performed), 
     * and therefore a superfluous performance hit.     
     */
    public boolean isAcquireTriggersWithinLock() {
        return acquireTriggersWithinLock;
    }

    /**
     * Whether or not the query and update to acquire a Trigger for firing
     * should be performed after obtaining an explicit DB lock.  This is the
     * behavior prior to Quartz 1.6.3, but is considered unnecessary for most
     * databases, and therefore a superfluous performance hit.     
     * 
     * However, if batch acquisition is used, it is important for this behavior
     * to be used for all dbs.
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setAcquireTriggersWithinLock(boolean acquireTriggersWithinLock) {
        this.acquireTriggersWithinLock = acquireTriggersWithinLock;
    }

    
    /**
     * <p>
     * Set the JDBC driver delegate class.
     * </p>
     * 
     * @param delegateClassName
     *          the delegate class name
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setDriverDelegateClass(String delegateClassName)
        throws InvalidConfigurationException {
        synchronized(this) {
            this.delegateClassName = delegateClassName;
        }
    }

    /**
     * <p>
     * Get the JDBC driver delegate class name.
     * </p>
     * 
     * @return the delegate class name
     */
    public String getDriverDelegateClass() {
        return delegateClassName;
    }

    /**
     * <p>
     * Set the JDBC driver delegate's initialization string.
     * </p>
     * 
     * @param delegateInitString
     *          the delegate init string
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setDriverDelegateInitString(String delegateInitString)
        throws InvalidConfigurationException {
        this.delegateInitString = delegateInitString;
    }

    /**
     * <p>
     * Get the JDBC driver delegate's initialization string.
     * </p>
     * 
     * @return the delegate init string
     */
    public String getDriverDelegateInitString() {
        return delegateInitString;
    }

    public String getSelectWithLockSQL() {
        return selectWithLockSQL;
    }

    /**
     * <p>
     * set the SQL statement to use to select and lock a row in the "locks"
     * table.
     * </p>
     * 
     * @see StdRowLockSemaphore
     */
    public void setSelectWithLockSQL(String string) {
        selectWithLockSQL = string;
    }

    protected ClassLoadHelper getClassLoadHelper() {
        return classLoadHelper;
    }

    /**
     * Get whether the threads spawned by this JobStore should be
     * marked as daemon.  Possible threads include the <code>MisfireHandler</code> 
     * and the <code>ClusterManager</code>.
     * 
     * @see Thread#setDaemon(boolean)
     */
    public boolean getMakeThreadsDaemons() {
        return makeThreadsDaemons;
    }

    /**
     * Set whether the threads spawned by this JobStore should be
     * marked as daemon.  Possible threads include the <code>MisfireHandler</code> 
     * and the <code>ClusterManager</code>.
     *
     * @see Thread#setDaemon(boolean)
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setMakeThreadsDaemons(boolean makeThreadsDaemons) {
        this.makeThreadsDaemons = makeThreadsDaemons;
    }
    
    /**
     * Get whether to set the class load context of spawned threads to that
     * of the initializing thread.
     */
    public boolean isThreadsInheritInitializersClassLoadContext() {
        return threadsInheritInitializersClassLoadContext;
    }

    /**
     * Set whether to set the class load context of spawned threads to that
     * of the initializing thread.
     */
    public void setThreadsInheritInitializersClassLoadContext(
            boolean threadsInheritInitializersClassLoadContext) {
        this.threadsInheritInitializersClassLoadContext = threadsInheritInitializersClassLoadContext;
    }

    /**
     * Get whether to check to see if there are Triggers that have misfired
     * before actually acquiring the lock to recover them.  This should be 
     * set to false if the majority of the time, there are are misfired
     * Triggers.
     */
    public boolean getDoubleCheckLockMisfireHandler() {
        return doubleCheckLockMisfireHandler;
    }

    /**
     * Set whether to check to see if there are Triggers that have misfired
     * before actually acquiring the lock to recover them.  This should be 
     * set to false if the majority of the time, there are are misfired
     * Triggers.
     */
    @SuppressWarnings("UnusedDeclaration") /* called reflectively */
    public void setDoubleCheckLockMisfireHandler(
            boolean doubleCheckLockMisfireHandler) {
        this.doubleCheckLockMisfireHandler = doubleCheckLockMisfireHandler;
    }

    @Override
    public long getAcquireRetryDelay(int failureCount) {
        return dbRetryInterval;
    }

    //---------------------------------------------------------------------------
    // interface methods
    //---------------------------------------------------------------------------

    protected Logger getLog() {
        return log;
    }

    /**
     * <p>
     * Called by the QuartzScheduler before the <code>JobStore</code> is
     * used, in order to give it a chance to initialize.
     * </p>
     */
    public void initialize(ClassLoadHelper loadHelper,
            SchedulerSignaler signaler) throws SchedulerConfigException {

        if (dsName == null) { 
            throw new SchedulerConfigException("DataSource name not set."); 
        }

        classLoadHelper = loadHelper;
        if(isThreadsInheritInitializersClassLoadContext()) {
            log.info("JDBCJobStore threads will inherit ContextClassLoader of thread: " + Thread.currentThread().getName());
            initializersLoader = Thread.currentThread().getContextClassLoader();
        }
        
        this.schedSignaler = signaler;

        // If the user hasn't specified an explicit lock handler, then 
        // choose one based on CMT/Clustered/UseDBLocks.
        if (getLockHandler() == null) {
            
            // If the user hasn't specified an explicit lock handler, 
            // then we *must* use DB locks with clustering
            if (isClustered()) {
                setUseDBLocks(true);
            }
            
            if (getUseDBLocks()) {
                if(getDriverDelegateClass() != null && getDriverDelegateClass().equals(MSSQLDelegate.class.getName())) {
                    if(getSelectWithLockSQL() == null) {
                        String msSqlDflt = "SELECT * FROM {0}LOCKS WITH (UPDLOCK,ROWLOCK) WHERE " + COL_SCHEDULER_NAME + " = {1} AND LOCK_NAME = ?";
                        getLog().info("Detected usage of MSSQLDelegate class - defaulting 'selectWithLockSQL' to '" + msSqlDflt + "'.");
                        setSelectWithLockSQL(msSqlDflt);
                    }
                }
                getLog().info("Using db table-based data access locking (synchronization).");
                setLockHandler(new StdRowLockSemaphore(getTablePrefix(), getInstanceName(), getSelectWithLockSQL()));
            } else {
                getLog().info(
                    "Using thread monitor-based data access locking (synchronization).");
                setLockHandler(new SimpleSemaphore());
            }
        }

    }
   
    /**
     * @see org.quartz.spi.JobStore#schedulerStarted()
     */
    public void schedulerStarted() throws SchedulerException {

        if (isClustered()) {
            clusterManagementThread = new ClusterManager();
            if(initializersLoader != null)
                clusterManagementThread.setContextClassLoader(initializersLoader);
            clusterManagementThread.initialize();
        } else {
            try {
                recoverJobs();
            } catch (SchedulerException se) {
                throw new SchedulerConfigException(
                        "Failure occured during job recovery.", se);
            }
        }

        misfireHandler = new MisfireHandler();
        if(initializersLoader != null)
            misfireHandler.setContextClassLoader(initializersLoader);
        misfireHandler.initialize();
        schedulerRunning = true;
        
        getLog().debug("JobStore background threads started (as scheduler was started).");
    }
    
    public void schedulerPaused() {
        schedulerRunning = false;
    }
    
    public void schedulerResumed() {
        schedulerRunning = true;
    }
    
    /**
     * <p>
     * Called by the QuartzScheduler to inform the <code>JobStore</code> that
     * it should free up all of it's resources because the scheduler is
     * shutting down.
     * </p>
     */
    public void shutdown() {
        shutdown = true;
        
        if (misfireHandler != null) {
            misfireHandler.shutdown();
            try {
                misfireHandler.join();
            } catch (InterruptedException ignore) {
            }
        }

        if (clusterManagementThread != null) {
            clusterManagementThread.shutdown();
            try {
                clusterManagementThread.join();
            } catch (InterruptedException ignore) {
            }
        }

        try {
            DBConnectionManager.getInstance().shutdown(getDataSource());
        } catch (SQLException sqle) {
            getLog().warn("Database connection shutdown unsuccessful.", sqle);
        }        
        
        getLog().debug("JobStore background threads shutdown.");
    }

    public boolean supportsPersistence() {
        return true;
    }

    //---------------------------------------------------------------------------
    // helper methods for subclasses
    //---------------------------------------------------------------------------

    protected abstract Connection getNonManagedTXConnection()
        throws JobPersistenceException;

    /**
     * Wrap the given <code>Connection</code> in a Proxy such that attributes 
     * that might be set will be restored before the connection is closed 
     * (and potentially restored to a pool).
     */
    protected Connection getAttributeRestoringConnection(Connection conn) {
        return (Connection)Proxy.newProxyInstance( // 创建连接代理，执行某些方法时记录原始值，关闭前恢复（用于池化连接）
                Thread.currentThread().getContextClassLoader(),
                new Class[] { Connection.class },
                new AttributeRestoringConnectionInvocationHandler(conn));
    }
    
    protected Connection getConnection() throws JobPersistenceException {
        Connection conn;
        try {
            conn = DBConnectionManager.getInstance().getConnection(
                    getDataSource());
        } catch (SQLException sqle) {
            throw new JobPersistenceException(
                    "Failed to obtain DB connection from data source '"
                    + getDataSource() + "': " + sqle.toString(), sqle);
        } catch (Throwable e) {
            throw new JobPersistenceException(
                    "Failed to obtain DB connection from data source '"
                    + getDataSource() + "': " + e.toString(), e);
        }

        if (conn == null) { 
            throw new JobPersistenceException(
                "Could not get connection from DataSource '"
                + getDataSource() + "'"); 
        }

        // Protect connection attributes we might change.
        conn = getAttributeRestoringConnection(conn); // 保护连接属性，之后可能会改变它的自动提交和隔离级别

        // Set any connection connection attributes we are to override.
        try {
            if (!isDontSetAutoCommitFalse()) { // 不自动提交
                conn.setAutoCommit(false);
            }

            if(isTxIsolationLevelSerializable()) { // 串行隔离级别
                conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            }
        } catch (SQLException sqle) {
            getLog().warn("Failed to override connection auto commit/transaction isolation.", sqle);
        } catch (Throwable e) {
            try { conn.close(); } catch(Throwable ignored) {}
            
            throw new JobPersistenceException(
                "Failure setting up connection.", e);
        }
    
        return conn;
    }

    protected void releaseLock(String lockName, boolean doIt) {
        if (doIt) {
            try {
                getLockHandler().releaseLock(lockName);
            } catch (LockException le) {
                getLog().error("Error returning lock: " + le.getMessage(), le);
            }
        }
    }

    /**
     * Recover any failed or misfired jobs and clean up the data store as
     * appropriate.
     * 
     * @throws JobPersistenceException if jobs could not be recovered
     */
    protected void recoverJobs() throws JobPersistenceException {
        executeInNonManagedTXLock(
            LOCK_TRIGGER_ACCESS,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    recoverJobs(conn);
                }
            }, null);
    }
    
    /**
     * <p>
     * Will recover any failed or misfired jobs and clean up the data store as
     * appropriate.
     * </p>
     * 
     * @throws JobPersistenceException
     *           if jobs could not be recovered
     */
    protected void recoverJobs(Connection conn) throws JobPersistenceException {
        try {
            // update inconsistent job states
            int rows = getDelegate().updateTriggerStatesFromOtherStates(conn,
                    STATE_WAITING, STATE_ACQUIRED, STATE_BLOCKED);

            rows += getDelegate().updateTriggerStatesFromOtherStates(conn,
                        STATE_PAUSED, STATE_PAUSED_BLOCKED, STATE_PAUSED_BLOCKED);
            
            getLog().info(
                    "Freed " + rows
                            + " triggers from 'acquired' / 'blocked' state.");

            // clean up misfired jobs
            recoverMisfiredJobs(conn, true);
            
            // recover jobs marked for recovery that were not fully executed
            List<OperableTrigger> recoveringJobTriggers = getDelegate()
                    .selectTriggersForRecoveringJobs(conn);
            getLog()
                    .info(
                            "Recovering "
                                    + recoveringJobTriggers.size()
                                    + " jobs that were in-progress at the time of the last shut-down.");

            for (OperableTrigger recoveringJobTrigger: recoveringJobTriggers) {
                if (jobExists(conn, recoveringJobTrigger.getJobKey())) {
                    recoveringJobTrigger.computeFirstFireTime(null);
                    storeTrigger(conn, recoveringJobTrigger, null, false,
                            STATE_WAITING, false, true);
                }
            }
            getLog().info("Recovery complete.");

            // remove lingering 'complete' triggers...
            List<TriggerKey> cts = getDelegate().selectTriggersInState(conn, STATE_COMPLETE);
            for(TriggerKey ct: cts) {
                removeTrigger(conn, ct);
            }
            getLog().info(
                "Removed " + cts.size() + " 'complete' triggers.");
            
            // clean up any fired trigger entries
            int n = getDelegate().deleteFiredTriggers(conn);
            getLog().info("Removed " + n + " stale fired job entries.");
        } catch (JobPersistenceException e) {
            throw e;
        } catch (Exception e) {
            throw new JobPersistenceException("Couldn't recover jobs: "
                    + e.getMessage(), e);
        }
    }

    protected long getMisfireTime() {
        long misfireTime = System.currentTimeMillis();
        if (getMisfireThreshold() > 0) {
            misfireTime -= getMisfireThreshold();
        }

        return (misfireTime > 0) ? misfireTime : 0;
    }

    /**
     * Helper class for returning the composite result of trying
     * to recover misfired jobs.
     */
    protected static class RecoverMisfiredJobsResult {
        public static final RecoverMisfiredJobsResult NO_OP =
            new RecoverMisfiredJobsResult(false, 0, Long.MAX_VALUE);
        
        private boolean _hasMoreMisfiredTriggers;  // 还有更多失火的触发器待处理
        private int _processedMisfiredTriggerCount; // 当前处理的失火触发器个数
        private long _earliestNewTime; // 当前处理的失火触发器中最早的下一次点火时间
        
        public RecoverMisfiredJobsResult(
            boolean hasMoreMisfiredTriggers, int processedMisfiredTriggerCount, long earliestNewTime) {
            _hasMoreMisfiredTriggers = hasMoreMisfiredTriggers;
            _processedMisfiredTriggerCount = processedMisfiredTriggerCount;
            _earliestNewTime = earliestNewTime;
        }
        
        public boolean hasMoreMisfiredTriggers() {
            return _hasMoreMisfiredTriggers;
        }
        public int getProcessedMisfiredTriggerCount() {
            return _processedMisfiredTriggerCount;
        } 
        public long getEarliestNewTime() {
            return _earliestNewTime;
        } 
    }
    
    protected RecoverMisfiredJobsResult recoverMisfiredJobs(
        Connection conn, boolean recovering)
        throws JobPersistenceException, SQLException {

        // If recovering, we want to handle all of the misfired
        // triggers right away.
        int maxMisfiresToHandleAtATime = 
            (recovering) ? -1 : getMaxMisfiresToHandleAtATime();
        
        List<TriggerKey> misfiredTriggers = new LinkedList<TriggerKey>();
        long earliestNewTime = Long.MAX_VALUE;
        // We must still look for the MISFIRED state in case triggers were left 
        // in this state when upgrading to this version that does not support it.
        // 我们仍须寻找 MISFIRED 状态，以防在升级到不支持它的版本时触发器处于此状态。
        boolean hasMoreMisfiredTriggers =
            getDelegate().hasMisfiredTriggersInState(
                conn, STATE_WAITING, getMisfireTime(), 
                maxMisfiresToHandleAtATime, misfiredTriggers);

        if (hasMoreMisfiredTriggers) {
            getLog().info(
                "Handling the first " + misfiredTriggers.size() +
                " triggers that missed their scheduled fire-time.  " +
                "More misfired triggers remain to be processed.");
        } else if (misfiredTriggers.size() > 0) { 
            getLog().info(
                "Handling " + misfiredTriggers.size() + 
                " trigger(s) that missed their scheduled fire-time.");
        } else {
            getLog().debug(
                "Found 0 triggers that missed their scheduled fire-time.");
            return RecoverMisfiredJobsResult.NO_OP; 
        }

        for (TriggerKey triggerKey: misfiredTriggers) { // 遍历该批次失火触发
            
            OperableTrigger trig = 
                retrieveTrigger(conn, triggerKey);

            if (trig == null) {
                continue;
            }

            doUpdateOfMisfiredTrigger(conn, trig, false, STATE_WAITING, recovering); // 依据失火策略，恢复失火触发器到合适状态

            if(trig.getNextFireTime() != null && trig.getNextFireTime().getTime() < earliestNewTime)
                earliestNewTime = trig.getNextFireTime().getTime(); // 找到失火触发器中下一次点火时间最小的
        }

        return new RecoverMisfiredJobsResult(
                hasMoreMisfiredTriggers, misfiredTriggers.size(), earliestNewTime);
    }

    protected boolean updateMisfiredTrigger(Connection conn,
            TriggerKey triggerKey, String newStateIfNotComplete, boolean forceState)
        throws JobPersistenceException {
        try {

            OperableTrigger trig = retrieveTrigger(conn, triggerKey);

            long misfireTime = System.currentTimeMillis();
            if (getMisfireThreshold() > 0) {
                misfireTime -= getMisfireThreshold();
            }

            if (trig.getNextFireTime().getTime() > misfireTime) { // nextFireTime + misfireThreshold > currentTime 其实就是还没失火
                return false;
            }

            doUpdateOfMisfiredTrigger(conn, trig, forceState, newStateIfNotComplete, false); // 做失火变更

            return true;

        } catch (Exception e) {
            throw new JobPersistenceException(
                    "Couldn't update misfired trigger '" + triggerKey + "': " + e.getMessage(), e);
        }
    }

    private void doUpdateOfMisfiredTrigger(Connection conn, OperableTrigger trig, boolean forceState,
                                           String newStateIfNotComplete, boolean recovering) throws JobPersistenceException {
        Calendar cal = null;
        if (trig.getCalendarName() != null) {
            cal = retrieveCalendar(conn, trig.getCalendarName());
        }

        schedSignaler.notifyTriggerListenersMisfired(trig); // 通知触发器监听器该触发器失火

        trig.updateAfterMisfire(cal); // 依据失火策略更新下一次点火时间

        if (trig.getNextFireTime() == null) { // 寿终正寝了
            storeTrigger(conn, trig,
                null, true, STATE_COMPLETE, forceState, recovering);
            schedSignaler.notifySchedulerListenersFinalized(trig);
        } else { // 非完成态，需要继续履行使命
            storeTrigger(conn, trig, null, true, newStateIfNotComplete,
                    forceState, recovering);
        }
    }

    /**
     * <p>
     * Store the given <code>{@link org.quartz.JobDetail}</code> and <code>{@link org.quartz.Trigger}</code>.
     * </p>
     * 
     * @param newJob
     *          The <code>JobDetail</code> to be stored.
     * @param newTrigger
     *          The <code>Trigger</code> to be stored.
     * @throws ObjectAlreadyExistsException
     *           if a <code>Job</code> with the same name/group already
     *           exists.
     */
    public void storeJobAndTrigger(final JobDetail newJob,
            final OperableTrigger newTrigger) 
        throws JobPersistenceException {
        executeInLock(
            (isLockOnInsert()) ? LOCK_TRIGGER_ACCESS : null,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    storeJob(conn, newJob, false);
                    storeTrigger(conn, newTrigger, newJob, false,
                            Constants.STATE_WAITING, false, false);
                }
            });
    }
    
    /**
     * <p>
     * Store the given <code>{@link org.quartz.JobDetail}</code>.
     * </p>
     * 
     * @param newJob
     *          The <code>JobDetail</code> to be stored.
     * @param replaceExisting
     *          If <code>true</code>, any <code>Job</code> existing in the
     *          <code>JobStore</code> with the same name & group should be
     *          over-written.
     * @throws ObjectAlreadyExistsException
     *           if a <code>Job</code> with the same name/group already
     *           exists, and replaceExisting is set to false.
     */
    public void storeJob(final JobDetail newJob,
        final boolean replaceExisting) throws JobPersistenceException {
        executeInLock(
            (isLockOnInsert() || replaceExisting) ? LOCK_TRIGGER_ACCESS : null,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    storeJob(conn, newJob, replaceExisting);
                }
            });
    }
    
    /**
     * <p>
     * Insert or update a job.
     * </p>
     */
    protected void storeJob(Connection conn, 
            JobDetail newJob, boolean replaceExisting)
        throws JobPersistenceException {

        boolean existingJob = jobExists(conn, newJob.getKey());
        try {
            if (existingJob) {
                if (!replaceExisting) { 
                    throw new ObjectAlreadyExistsException(newJob); 
                }
                getDelegate().updateJobDetail(conn, newJob);
            } else {
                getDelegate().insertJobDetail(conn, newJob);
            }
        } catch (IOException e) {
            throw new JobPersistenceException("Couldn't store job: "
                    + e.getMessage(), e);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't store job: "
                    + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Check existence of a given job.
     * </p>
     */
    protected boolean jobExists(Connection conn, JobKey jobKey) throws JobPersistenceException {
        try {
            return getDelegate().jobExists(conn, jobKey);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't determine job existence (" + jobKey + "): " + e.getMessage(), e);
        }
    }


    /**
     * <p>
     * Store the given <code>{@link org.quartz.Trigger}</code>.
     * </p>
     * 
     * @param newTrigger
     *          The <code>Trigger</code> to be stored.
     * @param replaceExisting
     *          If <code>true</code>, any <code>Trigger</code> existing in
     *          the <code>JobStore</code> with the same name & group should
     *          be over-written.
     * @throws ObjectAlreadyExistsException
     *           if a <code>Trigger</code> with the same name/group already
     *           exists, and replaceExisting is set to false.
     */
    public void storeTrigger(final OperableTrigger newTrigger,
        final boolean replaceExisting) throws JobPersistenceException {
        executeInLock(
            (isLockOnInsert() || replaceExisting) ? LOCK_TRIGGER_ACCESS : null,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    storeTrigger(conn, newTrigger, null, replaceExisting,
                        STATE_WAITING, false, false);
                }
            });
    }
    
    /**
     * <p>
     * Insert or update a trigger.
     * </p>
     */
    @SuppressWarnings("ConstantConditions")
    protected void storeTrigger(Connection conn,
            OperableTrigger newTrigger, JobDetail job, boolean replaceExisting, String state,
            boolean forceState, boolean recovering)
        throws JobPersistenceException {

        boolean existingTrigger = triggerExists(conn, newTrigger.getKey());

        if ((existingTrigger) && (!replaceExisting)) { // 若触发器存在且不替换则抛异常
            throw new ObjectAlreadyExistsException(newTrigger); 
        }
        
        try {

            boolean shouldBepaused;

            if (!forceState) { // 不强制更新（则判断触发器组是否被暂停）
                shouldBepaused = getDelegate().isTriggerGroupPaused( // 该触发器组被暂停
                        conn, newTrigger.getKey().getGroup());

                if(!shouldBepaused) { // 未暂停该触发器组
                    shouldBepaused = getDelegate().isTriggerGroupPaused(conn, // 所有触发器组被暂停
                            ALL_GROUPS_PAUSED);

                    if (shouldBepaused) { // 插入暂停该触发器组
                        getDelegate().insertPausedTriggerGroup(conn, newTrigger.getKey().getGroup());
                    }
                }

                if (shouldBepaused && (state.equals(STATE_WAITING) || state.equals(STATE_ACQUIRED))) {
                    state = STATE_PAUSED;  // 该触发器组被暂停，如果当前状态为waiting/acquired则更新为paused
                }
            }

            if(job == null) { // 没传关联的job,则检索；传了则直接用
                job = retrieveJob(conn, newTrigger.getJobKey());
            }
            if (job == null) {
                throw new JobPersistenceException("The job ("
                        + newTrigger.getJobKey()
                        + ") referenced by the trigger does not exist.");
            }

            if (job.isConcurrentExectionDisallowed() && !recovering) { // job不允许并发执行且不在恢复模式，则检测其是否有已点火的触发器记录，若有则更改为阻塞状态
                state = checkBlockedState(conn, job.getKey(), state);
            }
            
            if (existingTrigger) {
                getDelegate().updateTrigger(conn, newTrigger, state, job); // 更新触发器
            } else {
                getDelegate().insertTrigger(conn, newTrigger, state, job); // 插入触发器
            }
        } catch (Exception e) {
            throw new JobPersistenceException("Couldn't store trigger '" + newTrigger.getKey() + "' for '" 
                    + newTrigger.getJobKey() + "' job:" + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Check existence of a given trigger.
     * </p>
     */
    protected boolean triggerExists(Connection conn, TriggerKey key) throws JobPersistenceException {
        try {
            return getDelegate().triggerExists(conn, key);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't determine trigger existence (" + key + "): " + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Remove (delete) the <code>{@link org.quartz.Job}</code> with the given
     * name, and any <code>{@link org.quartz.Trigger}</code> s that reference
     * it.
     * </p>
     * 
     * <p>
     * If removal of the <code>Job</code> results in an empty group, the
     * group should be removed from the <code>JobStore</code>'s list of
     * known group names.
     * </p>
     * 
     * @return <code>true</code> if a <code>Job</code> with the given name &
     *         group was found and removed from the store.
     */
    public boolean removeJob(final JobKey jobKey) throws JobPersistenceException {
        return (Boolean) executeInLock(
                LOCK_TRIGGER_ACCESS,
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        return removeJob(conn, jobKey) ?
                                Boolean.TRUE : Boolean.FALSE;
                    }
                });
    }
    
    protected boolean removeJob(Connection conn, final JobKey jobKey)
        throws JobPersistenceException {

        try {
            List<TriggerKey> jobTriggers = getDelegate().selectTriggerKeysForJob(conn, jobKey);
            for (TriggerKey jobTrigger: jobTriggers) {
                deleteTriggerAndChildren(conn, jobTrigger);
            }

            return deleteJobAndChildren(conn, jobKey);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't remove job: "
                    + e.getMessage(), e);
        }
    }

    public boolean removeJobs(final List<JobKey> jobKeys) throws JobPersistenceException {

        return (Boolean) executeInLock(
                LOCK_TRIGGER_ACCESS,
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        boolean allFound = true;

                        // FUTURE_TODO: make this more efficient with a true bulk operation...
                        for (JobKey jobKey : jobKeys)
                            allFound = removeJob(conn, jobKey) && allFound;

                        return allFound ? Boolean.TRUE : Boolean.FALSE;
                    }
                });
    }
        
    public boolean removeTriggers(final List<TriggerKey> triggerKeys)
            throws JobPersistenceException {
        return (Boolean) executeInLock(
                LOCK_TRIGGER_ACCESS,
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        boolean allFound = true;

                        // FUTURE_TODO: make this more efficient with a true bulk operation...
                        for (TriggerKey triggerKey : triggerKeys)
                            allFound = removeTrigger(conn, triggerKey) && allFound;

                        return allFound ? Boolean.TRUE : Boolean.FALSE;
                    }
                });
    }
        
    public void storeJobsAndTriggers(
            final Map<JobDetail, Set<? extends Trigger>> triggersAndJobs, final boolean replace)
            throws JobPersistenceException {

        executeInLock(
                (isLockOnInsert() || replace) ? LOCK_TRIGGER_ACCESS : null,
                new VoidTransactionCallback() {
                    public void executeVoid(Connection conn) throws JobPersistenceException {
                        
                        // FUTURE_TODO: make this more efficient with a true bulk operation...
                        for(JobDetail job: triggersAndJobs.keySet()) {
                            storeJob(conn, job, replace);
                            for(Trigger trigger: triggersAndJobs.get(job)) {
                                storeTrigger(conn, (OperableTrigger) trigger, job, replace,
                                        Constants.STATE_WAITING, false, false);
                            }
                        }
                    }
                });
    }    
    
    /**
     * Delete a job and its listeners.
     * 
     * @see #removeJob(java.sql.Connection, org.quartz.JobKey)
     * @see #removeTrigger(Connection, TriggerKey)
     */
    private boolean deleteJobAndChildren(Connection conn, JobKey key)
        throws NoSuchDelegateException, SQLException {

        return (getDelegate().deleteJobDetail(conn, key) > 0);
    }
    
    /**
     * Delete a trigger, its listeners, and its Simple/Cron/BLOB sub-table entry.
     * 
     * @see #removeJob(java.sql.Connection, org.quartz.JobKey)
     * @see #removeTrigger(Connection, TriggerKey)
     * @see #replaceTrigger(Connection, TriggerKey, OperableTrigger)
     */
    private boolean deleteTriggerAndChildren(Connection conn, TriggerKey key)
        throws SQLException, NoSuchDelegateException {

        return (getDelegate().deleteTrigger(conn, key) > 0);
    }
    
    /**
     * <p>
     * Retrieve the <code>{@link org.quartz.JobDetail}</code> for the given
     * <code>{@link org.quartz.Job}</code>.
     * </p>
     * 
     * @return The desired <code>Job</code>, or null if there is no match.
     */
    public JobDetail retrieveJob(final JobKey jobKey) throws JobPersistenceException {
        return (JobDetail)executeWithoutLock( // no locks necessary for read...
            new TransactionCallback() {
                public Object execute(Connection conn) throws JobPersistenceException {
                    return retrieveJob(conn, jobKey);
                }
            });
    }
    
    protected JobDetail retrieveJob(Connection conn, JobKey key) throws JobPersistenceException {
        try {

            return getDelegate().selectJobDetail(conn, key,
                    getClassLoadHelper());
        } catch (ClassNotFoundException e) {
            throw new JobPersistenceException(
                    "Couldn't retrieve job because a required class was not found: "
                            + e.getMessage(), e);
        } catch (IOException e) {
            throw new JobPersistenceException(
                    "Couldn't retrieve job because the BLOB couldn't be deserialized: "
                            + e.getMessage(), e);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't retrieve job: "
                    + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Remove (delete) the <code>{@link org.quartz.Trigger}</code> with the
     * given name.
     * </p>
     * 
     * <p>
     * If removal of the <code>Trigger</code> results in an empty group, the
     * group should be removed from the <code>JobStore</code>'s list of
     * known group names.
     * </p>
     * 
     * <p>
     * If removal of the <code>Trigger</code> results in an 'orphaned' <code>Job</code>
     * that is not 'durable', then the <code>Job</code> should be deleted
     * also.
     * </p>
     * 
     * @return <code>true</code> if a <code>Trigger</code> with the given
     *         name & group was found and removed from the store.
     */
    public boolean removeTrigger(final TriggerKey triggerKey) throws JobPersistenceException {
        return (Boolean) executeInLock(
                LOCK_TRIGGER_ACCESS,
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        return removeTrigger(conn, triggerKey) ?
                                Boolean.TRUE : Boolean.FALSE;
                    }
                });
    }
    
    protected boolean removeTrigger(Connection conn, TriggerKey key)
        throws JobPersistenceException {
        boolean removedTrigger;
        try {
            // this must be called before we delete the trigger, obviously
            JobDetail job = getDelegate().selectJobForTrigger(conn,
                    getClassLoadHelper(), key, false);

            removedTrigger = 
                deleteTriggerAndChildren(conn, key); // 删除触发器（会同时删除触发器扩展属性）

            if (null != job && !job.isDurable()) { // 触发器有关联job且为非持久化的
                int numTriggers = getDelegate().selectNumTriggersForJob(conn,
                        job.getKey());
                if (numTriggers == 0) { // 检查job没有其他的触发器关联，则删除job
                    // Don't call removeJob() because we don't want to check for
                    // triggers again.
                    deleteJobAndChildren(conn, job.getKey());
                }
            }
        } catch (ClassNotFoundException e) {
            throw new JobPersistenceException("Couldn't remove trigger: "
                    + e.getMessage(), e);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't remove trigger: "
                    + e.getMessage(), e);
        }

        return removedTrigger;
    }

    /** 
     * @see org.quartz.spi.JobStore#replaceTrigger(TriggerKey, OperableTrigger)
     */
    public boolean replaceTrigger(final TriggerKey triggerKey, 
            final OperableTrigger newTrigger) throws JobPersistenceException {
        return (Boolean) executeInLock(
                LOCK_TRIGGER_ACCESS,
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        return replaceTrigger(conn, triggerKey, newTrigger) ?
                                Boolean.TRUE : Boolean.FALSE;
                    }
                });
    }
    
    protected boolean replaceTrigger(Connection conn, 
            TriggerKey key, OperableTrigger newTrigger)
        throws JobPersistenceException {
        try {
            // this must be called before we delete the trigger, obviously
            JobDetail job = getDelegate().selectJobForTrigger(conn,
                    getClassLoadHelper(), key);

            if (job == null) {
                return false;
            }
            
            if (!newTrigger.getJobKey().equals(job.getKey())) {
                throw new JobPersistenceException("New trigger is not related to the same job as the old trigger.");
            }
            
            boolean removedTrigger = 
                deleteTriggerAndChildren(conn, key);
            
            storeTrigger(conn, newTrigger, job, false, STATE_WAITING, false, false);

            return removedTrigger;
        } catch (ClassNotFoundException e) {
            throw new JobPersistenceException("Couldn't remove trigger: "
                    + e.getMessage(), e);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't remove trigger: "
                    + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Retrieve the given <code>{@link org.quartz.Trigger}</code>.
     * </p>
     * 
     * @return The desired <code>Trigger</code>, or null if there is no
     *         match.
     */
    public OperableTrigger retrieveTrigger(final TriggerKey triggerKey) throws JobPersistenceException {
        return (OperableTrigger)executeWithoutLock( // no locks necessary for read...
            new TransactionCallback() {
                public Object execute(Connection conn) throws JobPersistenceException {
                    return retrieveTrigger(conn, triggerKey);
                }
            });
    }
    
    protected OperableTrigger retrieveTrigger(Connection conn, TriggerKey key)
        throws JobPersistenceException {
        try {

            return getDelegate().selectTrigger(conn, key);
        } catch (Exception e) {
            throw new JobPersistenceException("Couldn't retrieve trigger: "
                    + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Get the current state of the identified <code>{@link Trigger}</code>.
     * </p>
     * 
     * @see TriggerState#NORMAL
     * @see TriggerState#PAUSED
     * @see TriggerState#COMPLETE
     * @see TriggerState#ERROR
     * @see TriggerState#NONE
     */
    public TriggerState getTriggerState(final TriggerKey triggerKey) throws JobPersistenceException {
        return (TriggerState)executeWithoutLock( // no locks necessary for read...
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        return getTriggerState(conn, triggerKey);
                    }
                });
    }
    
    public TriggerState getTriggerState(Connection conn, TriggerKey key)
        throws JobPersistenceException {
        try {
            String ts = getDelegate().selectTriggerState(conn, key);

            if (ts == null) {
                return TriggerState.NONE;
            }

            if (ts.equals(STATE_DELETED)) {
                return TriggerState.NONE;
            }

            if (ts.equals(STATE_COMPLETE)) {
                return TriggerState.COMPLETE;
            }

            if (ts.equals(STATE_PAUSED)) {
                return TriggerState.PAUSED;
            }

            if (ts.equals(STATE_PAUSED_BLOCKED)) {
                return TriggerState.PAUSED;
            }

            if (ts.equals(STATE_ERROR)) {
                return TriggerState.ERROR;
            }

            if (ts.equals(STATE_BLOCKED)) {
                return TriggerState.BLOCKED;
            }

            return TriggerState.NORMAL;

        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't determine state of trigger (" + key + "): " + e.getMessage(), e);
        }
    }

    /**
     * Reset the current state of the identified <code>{@link Trigger}</code>
     * from {@link TriggerState#ERROR} to {@link TriggerState#NORMAL} or
     * {@link TriggerState#PAUSED} as appropriate.
     *
     * <p>Only affects triggers that are in ERROR state - if identified trigger is not
     * in that state then the result is a no-op.</p>
     *
     * <p>The result will be the trigger returning to the normal, waiting to
     * be fired state, unless the trigger's group has been paused, in which
     * case it will go into the PAUSED state.</p>
     */
    public void resetTriggerFromErrorState(final TriggerKey triggerKey) throws JobPersistenceException {
        executeInLock(
                LOCK_TRIGGER_ACCESS,
                new VoidTransactionCallback() {
                    public void executeVoid(Connection conn) throws JobPersistenceException {
                        resetTriggerFromErrorState(conn, triggerKey);
                    }
                });
    }

    void resetTriggerFromErrorState(Connection conn, final TriggerKey triggerKey)
        throws JobPersistenceException {

        try {
            String newState = STATE_WAITING;

            if(getDelegate().isTriggerGroupPaused(conn, triggerKey.getGroup())) {
                newState = STATE_PAUSED;
            }

            getDelegate().updateTriggerStateFromOtherState(conn, triggerKey, newState, STATE_ERROR);

            getLog().info("Trigger " + triggerKey + " reset from ERROR state to: " + newState);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't reset from error state of trigger (" + triggerKey + "): " + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Store the given <code>{@link org.quartz.Calendar}</code>.
     * </p>
     * 
     * @param calName
     *          The name of the calendar.
     * @param calendar
     *          The <code>Calendar</code> to be stored.
     * @param replaceExisting
     *          If <code>true</code>, any <code>Calendar</code> existing
     *          in the <code>JobStore</code> with the same name & group
     *          should be over-written.
     * @throws ObjectAlreadyExistsException
     *           if a <code>Calendar</code> with the same name already
     *           exists, and replaceExisting is set to false.
     */
    public void storeCalendar(final String calName,
        final Calendar calendar, final boolean replaceExisting, final boolean updateTriggers)
        throws JobPersistenceException {
        executeInLock(
            (isLockOnInsert() || updateTriggers) ? LOCK_TRIGGER_ACCESS : null,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    storeCalendar(conn, calName, calendar, replaceExisting, updateTriggers);
                }
            });
    }
    
    protected void storeCalendar(Connection conn, 
            String calName, Calendar calendar, boolean replaceExisting, boolean updateTriggers)
        throws JobPersistenceException {
        try {
            boolean existingCal = calendarExists(conn, calName);
            if (existingCal && !replaceExisting) { 
                throw new ObjectAlreadyExistsException(
                    "Calendar with name '" + calName + "' already exists."); 
            }

            if (existingCal) {
                if (getDelegate().updateCalendar(conn, calName, calendar) < 1) { 
                    throw new JobPersistenceException(
                        "Couldn't store calendar.  Update failed."); 
                }
                
                if(updateTriggers) {
                    List<OperableTrigger> trigs = getDelegate().selectTriggersForCalendar(conn, calName);
                    
                    for(OperableTrigger trigger: trigs) {
                        trigger.updateWithNewCalendar(calendar, getMisfireThreshold());
                        storeTrigger(conn, trigger, null, true, STATE_WAITING, false, false);
                    }
                }
            } else {
                if (getDelegate().insertCalendar(conn, calName, calendar) < 1) { 
                    throw new JobPersistenceException(
                        "Couldn't store calendar.  Insert failed."); 
                }
            }

            if (!isClustered) {
                calendarCache.put(calName, calendar); // lazy-cache
            }

        } catch (IOException e) {
            throw new JobPersistenceException(
                    "Couldn't store calendar because the BLOB couldn't be serialized: "
                            + e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            throw new JobPersistenceException("Couldn't store calendar: "
                    + e.getMessage(), e);
        }catch (SQLException e) {
            throw new JobPersistenceException("Couldn't store calendar: "
                    + e.getMessage(), e);
        }
    }
    
    protected boolean calendarExists(Connection conn, String calName)
        throws JobPersistenceException {
        try {
            return getDelegate().calendarExists(conn, calName);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't determine calendar existence (" + calName + "): "
                            + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Remove (delete) the <code>{@link org.quartz.Calendar}</code> with the
     * given name.
     * </p>
     * 
     * <p>
     * If removal of the <code>Calendar</code> would result in
     * <code>Trigger</code>s pointing to non-existent calendars, then a
     * <code>JobPersistenceException</code> will be thrown.</p>
     *       *
     * @param calName The name of the <code>Calendar</code> to be removed.
     * @return <code>true</code> if a <code>Calendar</code> with the given name
     * was found and removed from the store.
     */
    public boolean removeCalendar(final String calName)
        throws JobPersistenceException {
        return (Boolean) executeInLock(
                LOCK_TRIGGER_ACCESS,
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        return removeCalendar(conn, calName) ?
                                Boolean.TRUE : Boolean.FALSE;
                    }
                });
    }
    
    protected boolean removeCalendar(Connection conn, 
            String calName) throws JobPersistenceException {
        try {
            if (getDelegate().calendarIsReferenced(conn, calName)) { 
                throw new JobPersistenceException(
                    "Calender cannot be removed if it referenced by a trigger!"); 
            }

            if (!isClustered) {
                calendarCache.remove(calName);
            }

            return (getDelegate().deleteCalendar(conn, calName) > 0);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't remove calendar: "
                    + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Retrieve the given <code>{@link org.quartz.Trigger}</code>.
     * </p>
     * 
     * @param calName
     *          The name of the <code>Calendar</code> to be retrieved.
     * @return The desired <code>Calendar</code>, or null if there is no
     *         match.
     */
    public Calendar retrieveCalendar(final String calName)
        throws JobPersistenceException {
        return (Calendar)executeWithoutLock( // no locks necessary for read...
            new TransactionCallback() {
                public Object execute(Connection conn) throws JobPersistenceException {
                    return retrieveCalendar(conn, calName);
                }
            });
    }
    
    protected Calendar retrieveCalendar(Connection conn,
            String calName)
        throws JobPersistenceException {
        // all calendars are persistent, but we can lazy-cache them during run
        // time as long as we aren't running clustered.
        Calendar cal = (isClustered) ? null : calendarCache.get(calName);
        if (cal != null) {
            return cal;
        }

        try {
            cal = getDelegate().selectCalendar(conn, calName);
            if (!isClustered) {
                calendarCache.put(calName, cal); // lazy-cache...
            }
            return cal;
        } catch (ClassNotFoundException e) {
            throw new JobPersistenceException(
                    "Couldn't retrieve calendar because a required class was not found: "
                            + e.getMessage(), e);
        } catch (IOException e) {
            throw new JobPersistenceException(
                    "Couldn't retrieve calendar because the BLOB couldn't be deserialized: "
                            + e.getMessage(), e);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't retrieve calendar: "
                    + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Get the number of <code>{@link org.quartz.Job}</code> s that are
     * stored in the <code>JobStore</code>.
     * </p>
     */
    public int getNumberOfJobs()
        throws JobPersistenceException {
        return (Integer) executeWithoutLock( // no locks necessary for read...
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        return getNumberOfJobs(conn);
                    }
                });
    }
    
    protected int getNumberOfJobs(Connection conn)
        throws JobPersistenceException {
        try {
            return getDelegate().selectNumJobs(conn);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't obtain number of jobs: " + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Get the number of <code>{@link org.quartz.Trigger}</code> s that are
     * stored in the <code>JobsStore</code>.
     * </p>
     */
    public int getNumberOfTriggers()
        throws JobPersistenceException {
        return (Integer) executeWithoutLock( // no locks necessary for read...
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        return getNumberOfTriggers(conn);
                    }
                });
    }
    
    protected int getNumberOfTriggers(Connection conn)
        throws JobPersistenceException {
        try {
            return getDelegate().selectNumTriggers(conn);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't obtain number of triggers: " + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Get the number of <code>{@link org.quartz.Calendar}</code> s that are
     * stored in the <code>JobsStore</code>.
     * </p>
     */
    public int getNumberOfCalendars()
        throws JobPersistenceException {
        return (Integer) executeWithoutLock( // no locks necessary for read...
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        return getNumberOfCalendars(conn);
                    }
                });
    }
    
    protected int getNumberOfCalendars(Connection conn)
        throws JobPersistenceException {
        try {
            return getDelegate().selectNumCalendars(conn);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't obtain number of calendars: " + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Get the names of all of the <code>{@link org.quartz.Job}</code> s that
     * matcher the given groupMatcher.
     * </p>
     * 
     * <p>
     * If there are no jobs in the given group name, the result should be an empty Set
     * </p>
     */
    @SuppressWarnings("unchecked")
    public Set<JobKey> getJobKeys(final GroupMatcher<JobKey> matcher)
        throws JobPersistenceException {
        return (Set<JobKey>)executeWithoutLock( // no locks necessary for read...
            new TransactionCallback() {
                public Object execute(Connection conn) throws JobPersistenceException {
                    return getJobNames(conn, matcher);
                }
            });
    }
    
    protected Set<JobKey> getJobNames(Connection conn,
            GroupMatcher<JobKey> matcher) throws JobPersistenceException {
        Set<JobKey> jobNames;

        try {
            jobNames = getDelegate().selectJobsInGroup(conn, matcher);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't obtain job names: "
                    + e.getMessage(), e);
        }

        return jobNames;
    }
    
    
    /**
     * Determine whether a {@link Job} with the given identifier already 
     * exists within the scheduler.
     * 
     * @param jobKey the identifier to check for
     * @return true if a Job exists with the given identifier
     * @throws JobPersistenceException
     */
    public boolean checkExists(final JobKey jobKey) throws JobPersistenceException {
        return (Boolean)executeWithoutLock( // no locks necessary for read...
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        return checkExists(conn, jobKey);
                    }
                });
    }
   
    protected boolean checkExists(Connection conn, JobKey jobKey) throws JobPersistenceException {
        try {
            return getDelegate().jobExists(conn, jobKey);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't check for existence of job: "
                    + e.getMessage(), e);
        }
    }
    
    /**
     * Determine whether a {@link Trigger} with the given identifier already 
     * exists within the scheduler.
     * 
     * @param triggerKey the identifier to check for
     * @return true if a Trigger exists with the given identifier
     * @throws JobPersistenceException
     */
    public boolean checkExists(final TriggerKey triggerKey) throws JobPersistenceException {
        return (Boolean)executeWithoutLock( // no locks necessary for read...
                new TransactionCallback() {
                    public Object execute(Connection conn) throws JobPersistenceException {
                        return checkExists(conn, triggerKey);
                    }
                });
    }
    
    protected boolean checkExists(Connection conn, TriggerKey triggerKey) throws JobPersistenceException {
        try {
            return getDelegate().triggerExists(conn, triggerKey);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't check for existence of job: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Clear (delete!) all scheduling data - all {@link Job}s, {@link Trigger}s
     * {@link Calendar}s.
     * 
     * @throws JobPersistenceException
     */
    public void clearAllSchedulingData() throws JobPersistenceException {
        executeInLock(
                LOCK_TRIGGER_ACCESS,
                new VoidTransactionCallback() {
                    public void executeVoid(Connection conn) throws JobPersistenceException {
                        clearAllSchedulingData(conn);
                    }
                });
    }
    
    protected void clearAllSchedulingData(Connection conn) throws JobPersistenceException {
        try {
            getDelegate().clearData(conn);
        } catch (SQLException e) {
            throw new JobPersistenceException("Error clearing scheduling data: " + e.getMessage(), e);
        }
    }
    
    /**
     * <p>
     * Get the names of all of the <code>{@link org.quartz.Trigger}</code> s
     * that match the given group Matcher.
     * </p>
     * 
     * <p>
     * If there are no triggers in the given group name, the result should be a
     * an empty Set (not <code>null</code>).
     * </p>
     */
    @SuppressWarnings("unchecked")
    public Set<TriggerKey> getTriggerKeys(final GroupMatcher<TriggerKey> matcher)
        throws JobPersistenceException {
        return (Set<TriggerKey>)executeWithoutLock( // no locks necessary for read...
            new TransactionCallback() {
                public Object execute(Connection conn) throws JobPersistenceException {
                    return getTriggerNames(conn, matcher);
                }
            });
    }
    
    protected Set<TriggerKey> getTriggerNames(Connection conn,
            GroupMatcher<TriggerKey> matcher) throws JobPersistenceException {

        Set<TriggerKey> trigNames;

        try {
            trigNames = getDelegate().selectTriggersInGroup(conn, matcher);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't obtain trigger names: "
                    + e.getMessage(), e);
        }

        return trigNames;
    }


    /**
     * <p>
     * Get the names of all of the <code>{@link org.quartz.Job}</code>
     * groups.
     * </p>
     * 
     * <p>
     * If there are no known group names, the result should be a zero-length
     * array (not <code>null</code>).
     * </p>
     */
    @SuppressWarnings("unchecked")
    public List<String> getJobGroupNames()
        throws JobPersistenceException {
        return (List<String>)executeWithoutLock( // no locks necessary for read...
            new TransactionCallback() {
                public Object execute(Connection conn) throws JobPersistenceException {
                    return getJobGroupNames(conn);
                }
            });
    }
    
    protected List<String> getJobGroupNames(Connection conn)
        throws JobPersistenceException {

        List<String> groupNames;

        try {
            groupNames = getDelegate().selectJobGroups(conn);
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't obtain job groups: "
                    + e.getMessage(), e);
        }

        return groupNames;
    }

    /**
     * <p>
     * Get the names of all of the <code>{@link org.quartz.Trigger}</code>
     * groups.
     * </p>
     * 
     * <p>
     * If there are no known group names, the result should be a zero-length
     * array (not <code>null</code>).
     * </p>
     */
    @SuppressWarnings("unchecked")
    public List<String> getTriggerGroupNames()
        throws JobPersistenceException {
        return (List<String>)executeWithoutLock( // no locks necessary for read...
            new TransactionCallback() {
                public Object execute(Connection conn) throws JobPersistenceException {
                    return getTriggerGroupNames(conn);
                }
            });        
    }
    
    protected List<String> getTriggerGroupNames(Connection conn) throws JobPersistenceException {

        List<String> groupNames;

        try {
            groupNames = getDelegate().selectTriggerGroups(conn);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't obtain trigger groups: " + e.getMessage(), e);
        }

        return groupNames;
    }

    /**
     * <p>
     * Get the names of all of the <code>{@link org.quartz.Calendar}</code> s
     * in the <code>JobStore</code>.
     * </p>
     * 
     * <p>
     * If there are no Calendars in the given group name, the result should be
     * a zero-length array (not <code>null</code>).
     * </p>
     */
    @SuppressWarnings("unchecked")
    public List<String> getCalendarNames()
        throws JobPersistenceException {
        return (List<String>)executeWithoutLock( // no locks necessary for read...
            new TransactionCallback() {
                public Object execute(Connection conn) throws JobPersistenceException {
                    return getCalendarNames(conn);
                }
            });      
    }
    
    protected List<String> getCalendarNames(Connection conn)
        throws JobPersistenceException {
        try {
            return getDelegate().selectCalendars(conn);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't obtain trigger groups: " + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Get all of the Triggers that are associated to the given Job.
     * </p>
     * 
     * <p>
     * If there are no matches, a zero-length array should be returned.
     * </p>
     */
    @SuppressWarnings("unchecked")
    public List<OperableTrigger> getTriggersForJob(final JobKey jobKey) throws JobPersistenceException {
        return (List<OperableTrigger>)executeWithoutLock( // no locks necessary for read...
            new TransactionCallback() {
                public Object execute(Connection conn) throws JobPersistenceException {
                    return getTriggersForJob(conn, jobKey);
                }
            });
    }
    
    protected List<OperableTrigger> getTriggersForJob(Connection conn,
            JobKey key)
        throws JobPersistenceException {
        List<OperableTrigger> list;

        try {
            list = getDelegate()
                    .selectTriggersForJob(conn, key);
        } catch (Exception e) {
            throw new JobPersistenceException(
                    "Couldn't obtain triggers for job: " + e.getMessage(), e);
        }

        return list;
    }

    /**
     * <p>
     * Pause the <code>{@link org.quartz.Trigger}</code> with the given name.
     * </p>
     * 
     * @see #resumeTrigger(TriggerKey)
     */
    public void pauseTrigger(final TriggerKey triggerKey) throws JobPersistenceException {
        executeInLock(
            LOCK_TRIGGER_ACCESS,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    pauseTrigger(conn, triggerKey);
                }
            });
    }
    
    /**
     * <p>
     * Pause the <code>{@link org.quartz.Trigger}</code> with the given name.
     * </p>
     * 
     * @see #resumeTrigger(Connection, TriggerKey)
     */
    public void pauseTrigger(Connection conn, 
            TriggerKey triggerKey)
        throws JobPersistenceException {

        try {
            String oldState = getDelegate().selectTriggerState(conn,
                    triggerKey);

            if (oldState.equals(STATE_WAITING)
                    || oldState.equals(STATE_ACQUIRED)) {

                getDelegate().updateTriggerState(conn, triggerKey,
                        STATE_PAUSED);
            } else if (oldState.equals(STATE_BLOCKED)) {
                getDelegate().updateTriggerState(conn, triggerKey,
                        STATE_PAUSED_BLOCKED);
            }
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't pause trigger '"
                    + triggerKey + "': " + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Pause the <code>{@link org.quartz.Job}</code> with the given name - by
     * pausing all of its current <code>Trigger</code>s.
     * </p>
     * 
     * @see #resumeJob(JobKey)
     */
    public void pauseJob(final JobKey jobKey) throws JobPersistenceException {
        executeInLock(
            LOCK_TRIGGER_ACCESS,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    List<OperableTrigger> triggers = getTriggersForJob(conn, jobKey);
                    for (OperableTrigger trigger: triggers) {
                        pauseTrigger(conn, trigger.getKey());
                    }
                }
            });
    }
    
    /**
     * <p>
     * Pause all of the <code>{@link org.quartz.Job}s</code> matching the given
     * groupMatcher - by pausing all of their <code>Trigger</code>s.
     * </p>
     * 
     * @see #resumeJobs(org.quartz.impl.matchers.GroupMatcher)
     */
    @SuppressWarnings("unchecked")
    public Set<String> pauseJobs(final GroupMatcher<JobKey> matcher)
        throws JobPersistenceException {
        return (Set<String>) executeInLock(
            LOCK_TRIGGER_ACCESS,
            new TransactionCallback() {
                public Set<String> execute(final Connection conn) throws JobPersistenceException {
                    Set<String> groupNames = new HashSet<String>();
                    Set<JobKey> jobNames = getJobNames(conn, matcher);

                    for (JobKey jobKey : jobNames) {
                        List<OperableTrigger> triggers = getTriggersForJob(conn, jobKey);
                        for (OperableTrigger trigger : triggers) {
                            pauseTrigger(conn, trigger.getKey());
                        }
                        groupNames.add(jobKey.getGroup());
                    }

                    return groupNames;
                }
            }
            );
    }
    
    /**
     * Determines if a Trigger for the given job should be blocked.  
     * State can only transition to STATE_PAUSED_BLOCKED/BLOCKED from 
     * PAUSED/STATE_WAITING respectively.
     * 
     * @return STATE_PAUSED_BLOCKED, BLOCKED, or the currentState. 
     */
    protected String checkBlockedState(
            Connection conn, JobKey jobKey, String currentState)
        throws JobPersistenceException {

        // State can only transition to BLOCKED from PAUSED or WAITING.
        if ((!currentState.equals(STATE_WAITING)) &&
            (!currentState.equals(STATE_PAUSED))) { // 状态只能从 paused 或 waiting 转换为 blocked
            return currentState;
        }
        
        try {
            List<FiredTriggerRecord> lst = getDelegate().selectFiredTriggerRecordsByJob(conn,
                    jobKey.getName(), jobKey.getGroup()); // 查询该job关联的已点火的触发器记录

            if (lst.size() > 0) { // 如果该不可并发执行的作业存在一条已点火记录，则应阻塞给定作业的触发器
                FiredTriggerRecord rec = lst.get(0);
                if (rec.isJobDisallowsConcurrentExecution()) { // OLD_TODO: worry about failed/recovering/volatile job  states?
                    return (STATE_PAUSED.equals(currentState)) ? STATE_PAUSED_BLOCKED : STATE_BLOCKED;
                }
            }

            return currentState;
        } catch (SQLException e) {
            throw new JobPersistenceException(
                "Couldn't determine if trigger should be in a blocked state '"
                    + jobKey + "': "
                    + e.getMessage(), e);
        }

    }

    /**
     * <p>
     * Resume (un-pause) the <code>{@link org.quartz.Trigger}</code> with the
     * given name.
     * </p>
     * 
     * <p>
     * If the <code>Trigger</code> missed one or more fire-times, then the
     * <code>Trigger</code>'s misfire instruction will be applied.
     * </p>
     * 
     * @see #pauseTrigger(TriggerKey)
     */
    public void resumeTrigger(final TriggerKey triggerKey) throws JobPersistenceException {
        executeInLock(
            LOCK_TRIGGER_ACCESS,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    resumeTrigger(conn, triggerKey);
                }
            });
    }
    
    /**
     * <p>
     * Resume (un-pause) the <code>{@link org.quartz.Trigger}</code> with the
     * given name.
     * </p>
     * 
     * <p>
     * If the <code>Trigger</code> missed one or more fire-times, then the
     * <code>Trigger</code>'s misfire instruction will be applied.
     * </p>
     * 
     * @see #pauseTrigger(Connection, TriggerKey)
     */
    public void resumeTrigger(Connection conn, 
            TriggerKey key)
        throws JobPersistenceException {
        try {

            TriggerStatus status = getDelegate().selectTriggerStatus(conn,
                    key);

            if (status == null || status.getNextFireTime() == null) {
                return;
            }

            boolean blocked = false;
            if(STATE_PAUSED_BLOCKED.equals(status.getStatus())) {
                blocked = true;
            }

            String newState = checkBlockedState(conn, status.getJobKey(), STATE_WAITING); // 检查job看该触发器是否需要被blocked,不需要则返回waiting可被再次点火

            boolean misfired = false;

            if (schedulerRunning && status.getNextFireTime().before(new Date())) { // 调度器被暂停又重新运行，要再次检测触发器是否失火
                misfired = updateMisfiredTrigger(conn, key,
                    newState, true);
            }

            if(!misfired) {
                if(blocked) {
                    getDelegate().updateTriggerStateFromOtherState(conn,
                            key, newState, STATE_PAUSED_BLOCKED);
                } else {
                    getDelegate().updateTriggerStateFromOtherState(conn,
                            key, newState, STATE_PAUSED);
                }
            } 

        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't resume trigger '"
                    + key + "': " + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Resume (un-pause) the <code>{@link org.quartz.Job}</code> with the
     * given name.
     * </p>
     * 
     * <p>
     * If any of the <code>Job</code>'s<code>Trigger</code> s missed one
     * or more fire-times, then the <code>Trigger</code>'s misfire
     * instruction will be applied.
     * </p>
     * 
     * @see #pauseJob(JobKey)
     */
    public void resumeJob(final JobKey jobKey) throws JobPersistenceException {
        executeInLock(
            LOCK_TRIGGER_ACCESS,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    List<OperableTrigger> triggers = getTriggersForJob(conn, jobKey);
                    for (OperableTrigger trigger: triggers) {
                        resumeTrigger(conn, trigger.getKey());
                    }
                }
            });
    }
    
    /**
     * <p>
     * Resume (un-pause) all of the <code>{@link org.quartz.Job}s</code> in
     * the given group.
     * </p>
     * 
     * <p>
     * If any of the <code>Job</code> s had <code>Trigger</code> s that
     * missed one or more fire-times, then the <code>Trigger</code>'s
     * misfire instruction will be applied.
     * </p>
     * 
     * @see #pauseJobs(org.quartz.impl.matchers.GroupMatcher)
     */
    @SuppressWarnings("unchecked")
    public Set<String> resumeJobs(final GroupMatcher<JobKey> matcher)
        throws JobPersistenceException {
        return (Set<String>) executeInLock(
            LOCK_TRIGGER_ACCESS,
            new TransactionCallback() {
                public Set<String> execute(Connection conn) throws JobPersistenceException {
                    Set<JobKey> jobKeys = getJobNames(conn, matcher);
                    Set<String> groupNames = new HashSet<String>();

                    for (JobKey jobKey: jobKeys) {
                        List<OperableTrigger> triggers = getTriggersForJob(conn, jobKey);
                        for (OperableTrigger trigger: triggers) {
                            resumeTrigger(conn, trigger.getKey());
                        }
                        groupNames.add(jobKey.getGroup());
                    }
                    return groupNames;
                }
            });
    }
    
    /**
     * <p>
     * Pause all of the <code>{@link org.quartz.Trigger}s</code> matching the
     * given groupMatcher.
     * </p>
     * 
     * @see #resumeTriggerGroup(java.sql.Connection, org.quartz.impl.matchers.GroupMatcher)
     */
    @SuppressWarnings("unchecked")
    public Set<String> pauseTriggers(final GroupMatcher<TriggerKey> matcher)
        throws JobPersistenceException {
        return (Set<String>) executeInLock(
            LOCK_TRIGGER_ACCESS,
            new TransactionCallback() {
                public Set<String> execute(Connection conn) throws JobPersistenceException {
                    return pauseTriggerGroup(conn, matcher);
                }
            });
    }
    
    /**
     * <p>
     * Pause all of the <code>{@link org.quartz.Trigger}s</code> matching the
     * given groupMatcher.
     * </p>
     * 
     * @see #resumeTriggerGroup(java.sql.Connection, org.quartz.impl.matchers.GroupMatcher)
     */
    public Set<String> pauseTriggerGroup(Connection conn,
            GroupMatcher<TriggerKey> matcher) throws JobPersistenceException {

        try {

            getDelegate().updateTriggerGroupStateFromOtherStates(
                    conn, matcher, STATE_PAUSED, STATE_ACQUIRED,
                    STATE_WAITING, STATE_WAITING);

            getDelegate().updateTriggerGroupStateFromOtherState(
                    conn, matcher, STATE_PAUSED_BLOCKED, STATE_BLOCKED);

            List<String> groups = getDelegate().selectTriggerGroups(conn, matcher);
            
            // make sure to account for an exact group match for a group that doesn't yet exist
            StringMatcher.StringOperatorName operator = matcher.getCompareWithOperator();
            if (operator.equals(StringOperatorName.EQUALS) && !groups.contains(matcher.getCompareToValue())) {
              groups.add(matcher.getCompareToValue());
            }

            for (String group : groups) {
                if (!getDelegate().isTriggerGroupPaused(conn, group)) {
                    getDelegate().insertPausedTriggerGroup(conn, group);
                }
            }

            return new HashSet<String>(groups);

        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't pause trigger group '"
                    + matcher + "': " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public Set<String> getPausedTriggerGroups() 
        throws JobPersistenceException {
        return (Set<String>)executeWithoutLock( // no locks necessary for read...
            new TransactionCallback() {
                public Object execute(Connection conn) throws JobPersistenceException {
                    return getPausedTriggerGroups(conn);
                }
            });
    }    
    
    /**
     * <p>
     * Pause all of the <code>{@link org.quartz.Trigger}s</code> in the
     * given group.
     * </p>
     * 
     * @see #resumeTriggers(org.quartz.impl.matchers.GroupMatcher)
     */
    public Set<String> getPausedTriggerGroups(Connection conn) 
        throws JobPersistenceException {

        try {
            return getDelegate().selectPausedTriggerGroups(conn);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't determine paused trigger groups: " + e.getMessage(), e);
        }
    }
    
    /**
     * <p>
     * Resume (un-pause) all of the <code>{@link org.quartz.Trigger}s</code>
     * matching the given groupMatcher.
     * </p>
     * 
     * <p>
     * If any <code>Trigger</code> missed one or more fire-times, then the
     * <code>Trigger</code>'s misfire instruction will be applied.
     * </p>
     * 
     * @see #pauseTriggers(org.quartz.impl.matchers.GroupMatcher)
     */
    @SuppressWarnings("unchecked")
    public Set<String> resumeTriggers(final GroupMatcher<TriggerKey> matcher)
        throws JobPersistenceException {
        return (Set<String>) executeInLock(
            LOCK_TRIGGER_ACCESS,
            new TransactionCallback() {
                public Set<String> execute(Connection conn) throws JobPersistenceException {
                    return resumeTriggerGroup(conn, matcher);
                }
            });

    }
    
    /**
     * <p>
     * Resume (un-pause) all of the <code>{@link org.quartz.Trigger}s</code>
     * matching the given groupMatcher.
     * </p>
     * 
     * <p>
     * If any <code>Trigger</code> missed one or more fire-times, then the
     * <code>Trigger</code>'s misfire instruction will be applied.
     * </p>
     * 
     * @see #pauseTriggers(org.quartz.impl.matchers.GroupMatcher)
     */
    public Set<String> resumeTriggerGroup(Connection conn,
            GroupMatcher<TriggerKey> matcher) throws JobPersistenceException {

        try {

            getDelegate().deletePausedTriggerGroup(conn, matcher);
            HashSet<String> groups = new HashSet<String>();

            Set<TriggerKey> keys = getDelegate().selectTriggersInGroup(conn,
                    matcher);

            for (TriggerKey key: keys) {
                resumeTrigger(conn, key);
                groups.add(key.getGroup());
            }

            return groups;

            // FUTURE_TODO: find an efficient way to resume triggers (better than the
            // above)... logic below is broken because of
            // findTriggersToBeBlocked()
            /*
             * int res =
             * getDelegate().updateTriggerGroupStateFromOtherState(conn,
             * groupName, STATE_WAITING, PAUSED);
             * 
             * if(res > 0) {
             * 
             * long misfireTime = System.currentTimeMillis();
             * if(getMisfireThreshold() > 0) misfireTime -=
             * getMisfireThreshold();
             * 
             * Key[] misfires =
             * getDelegate().selectMisfiredTriggersInGroupInState(conn,
             * groupName, STATE_WAITING, misfireTime);
             * 
             * List blockedTriggers = findTriggersToBeBlocked(conn,
             * groupName);
             * 
             * Iterator itr = blockedTriggers.iterator(); while(itr.hasNext()) {
             * Key key = (Key)itr.next();
             * getDelegate().updateTriggerState(conn, key.getName(),
             * key.getGroup(), BLOCKED); }
             * 
             * for(int i=0; i < misfires.length; i++) {               String
             * newState = STATE_WAITING;
             * if(blockedTriggers.contains(misfires[i])) newState =
             * BLOCKED; updateMisfiredTrigger(conn,
             * misfires[i].getName(), misfires[i].getGroup(), newState, true); } }
             */

        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't pause trigger group '"
                    + matcher + "': " + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Pause all triggers - equivalent of calling <code>pauseTriggerGroup(group)</code>
     * on every group.
     * </p>
     * 
     * <p>
     * When <code>resumeAll()</code> is called (to un-pause), trigger misfire
     * instructions WILL be applied.
     * </p>
     * 
     * @see #resumeAll()
     * @see #pauseTriggerGroup(java.sql.Connection, org.quartz.impl.matchers.GroupMatcher)
     */
    public void pauseAll() throws JobPersistenceException {
        executeInLock(
            LOCK_TRIGGER_ACCESS,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    pauseAll(conn);
                }
            });
    }
    
    /**
     * <p>
     * Pause all triggers - equivalent of calling <code>pauseTriggerGroup(group)</code>
     * on every group.
     * </p>
     * 
     * <p>
     * When <code>resumeAll()</code> is called (to un-pause), trigger misfire
     * instructions WILL be applied.
     * </p>
     * 
     * @see #resumeAll(Connection)
     * @see #pauseTriggerGroup(java.sql.Connection, org.quartz.impl.matchers.GroupMatcher)
     */
    public void pauseAll(Connection conn)
        throws JobPersistenceException {

        List<String> names = getTriggerGroupNames(conn);

        for (String name: names) {
            pauseTriggerGroup(conn, GroupMatcher.triggerGroupEquals(name));
        }

        try {
            if (!getDelegate().isTriggerGroupPaused(conn, ALL_GROUPS_PAUSED)) {
                getDelegate().insertPausedTriggerGroup(conn, ALL_GROUPS_PAUSED);
            }

        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't pause all trigger groups: " + e.getMessage(), e);
        }

    }

    /**
     * <p>
     * Resume (un-pause) all triggers - equivalent of calling <code>resumeTriggerGroup(group)</code>
     * on every group.
     * </p>
     * 
     * <p>
     * If any <code>Trigger</code> missed one or more fire-times, then the
     * <code>Trigger</code>'s misfire instruction will be applied.
     * </p>
     * 
     * @see #pauseAll()
     */
    public void resumeAll()
        throws JobPersistenceException {
        executeInLock(
            LOCK_TRIGGER_ACCESS,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    resumeAll(conn);
                }
            });
    }
    
    /**
     * protected
     * <p>
     * Resume (un-pause) all triggers - equivalent of calling <code>resumeTriggerGroup(group)</code>
     * on every group.
     * </p>
     * 
     * <p>
     * If any <code>Trigger</code> missed one or more fire-times, then the
     * <code>Trigger</code>'s misfire instruction will be applied.
     * </p>
     * 
     * @see #pauseAll(Connection)
     */
    public void resumeAll(Connection conn)
        throws JobPersistenceException {

        List<String> names = getTriggerGroupNames(conn);

        for (String name: names) {
            resumeTriggerGroup(conn, GroupMatcher.triggerGroupEquals(name));
        }

        try {
            getDelegate().deletePausedTriggerGroup(conn, ALL_GROUPS_PAUSED);
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't resume all trigger groups: " + e.getMessage(), e);
        }
    }

    private static long ftrCtr = System.currentTimeMillis();

    protected synchronized String getFiredTriggerRecordId() {
        return getInstanceId() + ftrCtr++;
    }

    /**
     * <p>
     * Get a handle to the next N triggers to be fired, and mark them as 'reserved'
     * by the calling scheduler.
     * </p>
     * 
     * @see #releaseAcquiredTrigger(OperableTrigger)
     */
    @SuppressWarnings("unchecked")
    public List<OperableTrigger> acquireNextTriggers(final long noLaterThan, final int maxCount, final long timeWindow)
        throws JobPersistenceException {
        
        String lockName;
        if(isAcquireTriggersWithinLock() || maxCount > 1) { 
            lockName = LOCK_TRIGGER_ACCESS;
        } else {
            lockName = null;
        }
        return executeInNonManagedTXLock(lockName, 
                new TransactionCallback<List<OperableTrigger>>() {
                    public List<OperableTrigger> execute(Connection conn) throws JobPersistenceException {
                        return acquireNextTrigger(conn, noLaterThan, maxCount, timeWindow);
                    }
                },
                new TransactionValidator<List<OperableTrigger>>() {
                    public Boolean validate(Connection conn, List<OperableTrigger> result) throws JobPersistenceException {
                        try {
                            // 上面获取成功会插入已点火记录表的，如果acquireNextTrigger异常则捞该示例已点火的记录，看之前查到的触发器中含不含有效的
                            List<FiredTriggerRecord> acquired = getDelegate().selectInstancesFiredTriggerRecords(conn, getInstanceId());
                            Set<String> fireInstanceIds = new HashSet<String>();
                            for (FiredTriggerRecord ft : acquired) {
                                fireInstanceIds.add(ft.getFireInstanceId());
                            }
                            for (OperableTrigger tr : result) {
                                if (fireInstanceIds.contains(tr.getFireInstanceId())) {
                                    return true;
                                }
                            }
                            return false; // 不含有效的
                        } catch (SQLException e) {
                            throw new JobPersistenceException("error validating trigger acquisition", e);
                        }
                    }
                });
    }
    
    // FUTURE_TODO: this really ought to return something like a FiredTriggerBundle,
    // so that the fireInstanceId doesn't have to be on the trigger...
    protected List<OperableTrigger> acquireNextTrigger(Connection conn, long noLaterThan, int maxCount, long timeWindow)
        throws JobPersistenceException {
        if (timeWindow < 0) {
          throw new IllegalArgumentException();
        }
        
        List<OperableTrigger> acquiredTriggers = new ArrayList<OperableTrigger>();
        Set<JobKey> acquiredJobKeysForNoConcurrentExec = new HashSet<JobKey>();
        final int MAX_DO_LOOP_RETRY = 3; // 最大循环次数
        int currentLoopCount = 0;
        do {
            currentLoopCount ++;
            try {
                // next_fire_time <= ${now + idleWaitTime + timeWindow} and (misfire_instr = -1 // 若忽略失火策略，则默认预取30s内的待点火触发器/失火触发器
                //    or (misfire_instr != -1 and next_fire_time >= ${now - misfireThreshold})) // 若设置失火策略，则默认预取30s内已大于失火阈值的失火触发器
                List<TriggerKey> keys = getDelegate().selectTriggerToAcquire(conn, noLaterThan + timeWindow, getMisfireTime(), maxCount);
                
                // No trigger is ready to fire yet.
                if (keys == null || keys.size() == 0)
                    return acquiredTriggers; // 30s内没有待执行触发器则直接返回

                long batchEnd = noLaterThan;

                for(TriggerKey triggerKey: keys) {
                    // If our trigger is no longer available, try a new one.
                    OperableTrigger nextTrigger = retrieveTrigger(conn, triggerKey); // 查触发器完整信息
                    if(nextTrigger == null) {
                        continue; // next trigger
                    }
                    
                    // If trigger's job is set as @DisallowConcurrentExecution, and it has already been added to result, then
                    // put it back into the timeTriggers set and continue to search for next trigger.
                    JobKey jobKey = nextTrigger.getJobKey();
                    JobDetail job;
                    try {
                        job = retrieveJob(conn, jobKey); // 查触发器关联jobDetail详情
                    } catch (JobPersistenceException jpe) {
                        try {
                            getLog().error("Error retrieving job, setting trigger state to ERROR.", jpe);
                            getDelegate().updateTriggerState(conn, triggerKey, STATE_ERROR);
                        } catch (SQLException sqle) {
                            getLog().error("Unable to set trigger state to ERROR.", sqle);
                        }
                        continue;
                    }
                    
                    if (job.isConcurrentExectionDisallowed()) { // 如果job不允许并发执行，如果有多个触发器关联它，也只返回一个
                        if (acquiredJobKeysForNoConcurrentExec.contains(jobKey)) {
                            continue; // next trigger
                        } else {
                            acquiredJobKeysForNoConcurrentExec.add(jobKey);
                        }
                    }

                    Date nextFireTime = nextTrigger.getNextFireTime();// 触发器下次点火时间是null则忽略（真出现这种case，需要用户在db手动修复!）

                    // A trigger should not return NULL on nextFireTime when fetched from DB.
                    // But for whatever reason if we do have this (BAD trigger implementation or
                    // data?), we then should log a warning and continue to next trigger.
                    // User would need to manually fix these triggers from DB as they will not
                    // able to be clean up by Quartz since we are not returning it to be processed.
                    if (nextFireTime == null) {
                        log.warn("Trigger {} returned null on nextFireTime and yet still exists in DB!",
                            nextTrigger.getKey());
                        continue;
                    }
                    
                    if (nextFireTime.getTime() > batchEnd) { // 发现有大于批次窗口结束时间则直接退出，剩下的都不用看了，毕竟keys是按点火时间升序排的
                      break;
                    }
                    // We now have a acquired trigger, let's add to return list.
                    // If our trigger was no longer in the expected state, try a new one.
                    int rowsUpdated = getDelegate().updateTriggerStateFromOtherState(conn, triggerKey, STATE_ACQUIRED, STATE_WAITING);
                    if (rowsUpdated <= 0) { // 把这个等待状态的触发器状态改为已获取，若没改成功则忽略
                        continue; // next trigger
                    }
                    nextTrigger.setFireInstanceId(getFiredTriggerRecordId()); // 设置点火实例id（主机名+时间戳+时间戳+计数器）
                    getDelegate().insertFiredTrigger(conn, nextTrigger, STATE_ACQUIRED, null); // 插入已点火触发器记录

                    if(acquiredTriggers.isEmpty()) {
                        batchEnd = Math.max(nextFireTime.getTime(), System.currentTimeMillis()) + timeWindow; // TODO: 这什么意图？
                    }
                    acquiredTriggers.add(nextTrigger);
                }

                // if we didn't end up with any trigger to fire from that first
                // batch, try again for another batch. We allow with a max retry count.
                if(acquiredTriggers.size() == 0 && currentLoopCount < MAX_DO_LOOP_RETRY) { // 重试获取
                    continue;
                }
                
                // We are done with the while loop.
                break;
            } catch (Exception e) {
                throw new JobPersistenceException(
                          "Couldn't acquire next trigger: " + e.getMessage(), e);
            }
        } while (true);
        
        // Return the acquired trigger list
        return acquiredTriggers;
    }
    
    /**
     * <p>
     * Inform the <code>JobStore</code> that the scheduler no longer plans to
     * fire the given <code>Trigger</code>, that it had previously acquired
     * (reserved).
     * </p>
     */
    public void releaseAcquiredTrigger(final OperableTrigger trigger) {
        retryExecuteInNonManagedTXLock(
            LOCK_TRIGGER_ACCESS,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    releaseAcquiredTrigger(conn, trigger);
                }
            });
    }
    
    protected void releaseAcquiredTrigger(Connection conn,
            OperableTrigger trigger)
        throws JobPersistenceException {
        try {
            getDelegate().updateTriggerStateFromOtherState(conn,
                    trigger.getKey(), STATE_WAITING, STATE_ACQUIRED); // 将acquired状态的触发器状态变更为waiting
            getDelegate().updateTriggerStateFromOtherState(conn,
                    trigger.getKey(), STATE_WAITING, STATE_BLOCKED); // 将blocked状态的触发器状态变更为waiting
            getDelegate().deleteFiredTrigger(conn, trigger.getFireInstanceId()); // 删除已点火触发器记录
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't release acquired trigger: " + e.getMessage(), e);
        }
    }

    /**
     * <p>
     * Inform the <code>JobStore</code> that the scheduler is now firing the
     * given <code>Trigger</code> (executing its associated <code>Job</code>),
     * that it had previously acquired (reserved).
     * </p>
     * 
     * @return null if the trigger or its job or calendar no longer exist, or
     *         if the trigger was not successfully put into the 'executing'
     *         state.
     */
    @SuppressWarnings("unchecked")
    public List<TriggerFiredResult> triggersFired(final List<OperableTrigger> triggers) throws JobPersistenceException {
        return executeInNonManagedTXLock(LOCK_TRIGGER_ACCESS,
                new TransactionCallback<List<TriggerFiredResult>>() {
                    public List<TriggerFiredResult> execute(Connection conn) throws JobPersistenceException {
                        List<TriggerFiredResult> results = new ArrayList<TriggerFiredResult>();

                        TriggerFiredResult result;
                        for (OperableTrigger trigger : triggers) {
                            try {
                              TriggerFiredBundle bundle = triggerFired(conn, trigger); // 触发点火
                              result = new TriggerFiredResult(bundle);
                            } catch (JobPersistenceException jpe) {
                                result = new TriggerFiredResult(jpe);
                            } catch(RuntimeException re) {
                                result = new TriggerFiredResult(re);
                            }
                            results.add(result);
                        }

                        return results;
                    }
                },
                new TransactionValidator<List<TriggerFiredResult>>() {
                    @Override
                    public Boolean validate(Connection conn, List<TriggerFiredResult> result) throws JobPersistenceException {
                        try {
                            // 查询该实例已点火的触发器记录，查看触发点火的返回结果中是否包含点火成功且状态是执行中的点火记录
                            List<FiredTriggerRecord> acquired = getDelegate().selectInstancesFiredTriggerRecords(conn, getInstanceId());
                            Set<String> executingTriggers = new HashSet<String>();
                            for (FiredTriggerRecord ft : acquired) {
                                if (STATE_EXECUTING.equals(ft.getFireInstanceState())) {
                                    executingTriggers.add(ft.getFireInstanceId());
                                }
                            }
                            for (TriggerFiredResult tr : result) {
                                if (tr.getTriggerFiredBundle() != null && executingTriggers.contains(tr.getTriggerFiredBundle().getTrigger().getFireInstanceId())) {
                                    return true;
                                }
                            }
                            return false;
                        } catch (SQLException e) {
                            throw new JobPersistenceException("error validating trigger acquisition", e);
                        }
                    }
                });
    }

    protected TriggerFiredBundle triggerFired(Connection conn,
            OperableTrigger trigger)
        throws JobPersistenceException {
        JobDetail job;
        Calendar cal = null;

        // Make sure trigger wasn't deleted, paused, or completed...
        try { // if trigger was deleted, state will be STATE_DELETED
            String state = getDelegate().selectTriggerState(conn,
                    trigger.getKey());
            if (!state.equals(STATE_ACQUIRED)) { // 确保触发器未被删除、暂停或完成...
                return null;
            }
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't select trigger state: "
                    + e.getMessage(), e);
        }

        try {
            job = retrieveJob(conn, trigger.getJobKey()); // 检索触发器关联的job
            if (job == null) { return null; }
        } catch (JobPersistenceException jpe) {
            try {
                getLog().error("Error retrieving job, setting trigger state to ERROR.", jpe);
                getDelegate().updateTriggerState(conn, trigger.getKey(), // 检索job异常则更新触发器状态为error
                        STATE_ERROR);
            } catch (SQLException sqle) {
                getLog().error("Unable to set trigger state to ERROR.", sqle);
            }
            throw jpe;
        }

        if (trigger.getCalendarName() != null) { // 如果触发器关联了日历，则检索日历
            cal = retrieveCalendar(conn, trigger.getCalendarName());
            if (cal == null) { return null; }
        }

        try {
            getDelegate().updateFiredTrigger(conn, trigger, STATE_EXECUTING, job); // 更新点火触发器记录状态为executing
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't insert fired trigger: "
                    + e.getMessage(), e);
        }

        Date prevFireTime = trigger.getPreviousFireTime(); // 暂存上次触发时间

        // call triggered - to update the trigger's next-fire-time state...
        trigger.triggered(cal); // 模拟触发一次，为了更新触发器为下一次点火时间状态

        String state = STATE_WAITING;
        boolean force = true;
        
        if (job.isConcurrentExectionDisallowed()) { // 如果job不允许并发执行
            state = STATE_BLOCKED;
            force = false;
            try { // 更新该job关联的所有触发器状态为blocked/paused_blocked（其实当前触发器最终还是会返回，被主调度线程执行，也就满足了ConcurrentExecutionDisallowed语义）
                getDelegate().updateTriggerStatesForJobFromOtherState(conn, job.getKey(),
                        STATE_BLOCKED, STATE_WAITING);
                getDelegate().updateTriggerStatesForJobFromOtherState(conn, job.getKey(),
                        STATE_BLOCKED, STATE_ACQUIRED);
                getDelegate().updateTriggerStatesForJobFromOtherState(conn, job.getKey(),
                        STATE_PAUSED_BLOCKED, STATE_PAUSED);
            } catch (SQLException e) {
                throw new JobPersistenceException(
                        "Couldn't update states of blocked triggers: "
                                + e.getMessage(), e);
            }
        } 
            
        if (trigger.getNextFireTime() == null) { // 触发器下次点火时间为null,表示任务寿终正寝了
            state = STATE_COMPLETE;
            force = true;
        }

        storeTrigger(conn, trigger, job, true, state, force, false); // 存储触发器以及其扩展属性等

        job.getJobDataMap().clearDirtyFlag(); // 清空job jobDataMap脏标志，所以后续检测是否要持久化jobDataMap，完全看自定义任务中有没有改

        return new TriggerFiredBundle(job, trigger, cal, trigger.getKey().getGroup()
                .equals(Scheduler.DEFAULT_RECOVERY_GROUP), new Date(), trigger
                .getPreviousFireTime(), prevFireTime, trigger.getNextFireTime());
    }

    /**
     * <p>
     * Inform the <code>JobStore</code> that the scheduler has completed the
     * firing of the given <code>Trigger</code> (and the execution its
     * associated <code>Job</code>), and that the <code>{@link org.quartz.JobDataMap}</code>
     * in the given <code>JobDetail</code> should be updated if the <code>Job</code>
     * is stateful.
     * </p>
     */
    public void triggeredJobComplete(final OperableTrigger trigger,
            final JobDetail jobDetail, final CompletedExecutionInstruction triggerInstCode) {
        retryExecuteInNonManagedTXLock(
            LOCK_TRIGGER_ACCESS,
            new VoidTransactionCallback() {
                public void executeVoid(Connection conn) throws JobPersistenceException {
                    triggeredJobComplete(conn, trigger, jobDetail,triggerInstCode);
                }
            });    
    }
    
    protected void triggeredJobComplete(Connection conn,
            OperableTrigger trigger, JobDetail jobDetail,
            CompletedExecutionInstruction triggerInstCode) throws JobPersistenceException {
        try {
            if (triggerInstCode == CompletedExecutionInstruction.DELETE_TRIGGER) {
                if(trigger.getNextFireTime() == null) { 
                    // double check for possible reschedule within job 
                    // execution, which would cancel the need to delete...
                    TriggerStatus stat = getDelegate().selectTriggerStatus( // 再次检查触发器状态是否为真的需要删除，以防可能的重新调度
                            conn, trigger.getKey());
                    if(stat != null && stat.getNextFireTime() == null) {
                        removeTrigger(conn, trigger.getKey());
                    }
                } else{
                    removeTrigger(conn, trigger.getKey());
                    signalSchedulingChangeOnTxCompletion(0L);
                }
            } else if (triggerInstCode == CompletedExecutionInstruction.SET_TRIGGER_COMPLETE) { // 更新触发器状态为 complete
                getDelegate().updateTriggerState(conn, trigger.getKey(),
                        STATE_COMPLETE);
                signalSchedulingChangeOnTxCompletion(0L);
            } else if (triggerInstCode == CompletedExecutionInstruction.SET_TRIGGER_ERROR) { // 更新触发器状态为 error
                getLog().info("Trigger " + trigger.getKey() + " set to ERROR state.");
                getDelegate().updateTriggerState(conn, trigger.getKey(),
                        STATE_ERROR);
                signalSchedulingChangeOnTxCompletion(0L);
            } else if (triggerInstCode == CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_COMPLETE) { // 更新job关联的所有触发器状态为 complete
                getDelegate().updateTriggerStatesForJob(conn,
                        trigger.getJobKey(), STATE_COMPLETE);
                signalSchedulingChangeOnTxCompletion(0L);
            } else if (triggerInstCode == CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_ERROR) { // 更新job关联的所有触发器状态为 error
                getLog().info("All triggers of Job " + 
                        trigger.getKey() + " set to ERROR state.");
                getDelegate().updateTriggerStatesForJob(conn,
                        trigger.getJobKey(), STATE_ERROR);
                signalSchedulingChangeOnTxCompletion(0L);
            }

            if (jobDetail.isConcurrentExectionDisallowed()) { // 如果job不允许并行执行，则当前触发器完成还需更新job关联的其他触发器状态解除阻塞
                getDelegate().updateTriggerStatesForJobFromOtherState(conn,
                        jobDetail.getKey(), STATE_WAITING,
                        STATE_BLOCKED);

                getDelegate().updateTriggerStatesForJobFromOtherState(conn,
                        jobDetail.getKey(), STATE_PAUSED,
                        STATE_PAUSED_BLOCKED);

                signalSchedulingChangeOnTxCompletion(0L);
            }
            if (jobDetail.isPersistJobDataAfterExecution()) { // 如果job执行完毕需要持久化数据，则当前触发器完成还需更新jobData
                try {
                    if (jobDetail.getJobDataMap().isDirty()) {
                        getDelegate().updateJobData(conn, jobDetail);
                    }
                } catch (IOException e) {
                    throw new JobPersistenceException(
                            "Couldn't serialize job data: " + e.getMessage(), e);
                } catch (SQLException e) {
                    throw new JobPersistenceException(
                            "Couldn't update job data: " + e.getMessage(), e);
                }
            }
        } catch (SQLException e) {
            throw new JobPersistenceException(
                    "Couldn't update trigger state(s): " + e.getMessage(), e);
        }

        try {
            getDelegate().deleteFiredTrigger(conn, trigger.getFireInstanceId()); // 最后删除这个已点火的触发器记录
        } catch (SQLException e) {
            throw new JobPersistenceException("Couldn't delete fired trigger: "
                    + e.getMessage(), e);
        }
    }

    /**
     * <P>
     * Get the driver delegate for DB operations.
     * </p>
     */
    protected DriverDelegate getDelegate() throws NoSuchDelegateException { // 获取jdbc操作委托实例
        synchronized(this) {
            if(null == delegate) {
                try {
                    if(delegateClassName != null) {
                        delegateClass = getClassLoadHelper().loadClass(delegateClassName, DriverDelegate.class);
                    }

                    delegate = delegateClass.newInstance();
                    
                    delegate.initialize(getLog(), tablePrefix, instanceName, instanceId, getClassLoadHelper(), canUseProperties(), getDriverDelegateInitString());
                    
                } catch (InstantiationException e) {
                    throw new NoSuchDelegateException("Couldn't create delegate: "
                            + e.getMessage(), e);
                } catch (IllegalAccessException e) {
                    throw new NoSuchDelegateException("Couldn't create delegate: "
                            + e.getMessage(), e);
                } catch (ClassNotFoundException e) {
                    throw new NoSuchDelegateException("Couldn't load delegate class: "
                            + e.getMessage(), e);
                }
            }
            return delegate;
        }
    }

    protected Semaphore getLockHandler() {
        return lockHandler;
    }

    public void setLockHandler(Semaphore lockHandler) {
        this.lockHandler = lockHandler;
    }

    //---------------------------------------------------------------------------
    // Management methods
    //---------------------------------------------------------------------------

    protected RecoverMisfiredJobsResult doRecoverMisfires() throws JobPersistenceException {
        boolean transOwner = false;
        Connection conn = getNonManagedTXConnection();
        try {
            RecoverMisfiredJobsResult result = RecoverMisfiredJobsResult.NO_OP;
            
            // Before we make the potentially expensive call to acquire the 
            // trigger lock, peek ahead to see if it is likely we would find
            // misfired triggers requiring recovery.
            int misfireCount = (getDoubleCheckLockMisfireHandler()) ? // 获取失火的等待触发的触发器个数
                getDelegate().countMisfiredTriggersInState(
                    conn, STATE_WAITING, getMisfireTime()) : 
                Integer.MAX_VALUE;
            
            if (misfireCount == 0) {
                getLog().debug(
                    "Found 0 triggers that missed their scheduled fire-time.");
            } else {
                transOwner = getLockHandler().obtainLock(conn, LOCK_TRIGGER_ACCESS);
                
                result = recoverMisfiredJobs(conn, false); // 恢复失火任务
            }
            
            commitConnection(conn);
            return result;
        } catch (JobPersistenceException e) {
            rollbackConnection(conn);
            throw e;
        } catch (SQLException e) {
            rollbackConnection(conn);
            throw new JobPersistenceException("Database error recovering from misfires.", e);
        } catch (RuntimeException e) {
            rollbackConnection(conn);
            throw new JobPersistenceException("Unexpected runtime exception: "
                    + e.getMessage(), e);
        } finally {
            try {
                releaseLock(LOCK_TRIGGER_ACCESS, transOwner);
            } finally {
                cleanupConnection(conn);
            }
        }
    }

    protected ThreadLocal<Long> sigChangeForTxCompletion = new ThreadLocal<Long>();
    protected void signalSchedulingChangeOnTxCompletion(long candidateNewNextFireTime) { // 事务完成时发送调度更改信号
        Long sigTime = sigChangeForTxCompletion.get();
        if(sigTime == null && candidateNewNextFireTime >= 0L) // 是0则存
            sigChangeForTxCompletion.set(candidateNewNextFireTime);
        else {
            if(sigTime == null || candidateNewNextFireTime < sigTime) // 比我当前维护的还要早
                sigChangeForTxCompletion.set(candidateNewNextFireTime);
        }
    }
    
    protected Long clearAndGetSignalSchedulingChangeOnTxCompletion() {
        Long t = sigChangeForTxCompletion.get();
        sigChangeForTxCompletion.set(null);
        return t;
    }

    protected void signalSchedulingChangeImmediately(long candidateNewNextFireTime) {
        schedSignaler.signalSchedulingChange(candidateNewNextFireTime);
    }

    //---------------------------------------------------------------------------
    // Cluster management methods
    //---------------------------------------------------------------------------

    protected boolean firstCheckIn = true;

    protected long lastCheckin = System.currentTimeMillis();
    
    protected boolean doCheckin() throws JobPersistenceException {
        boolean transOwner = false;
        boolean transStateOwner = false;
        boolean recovered = false;

        Connection conn = getNonManagedTXConnection();
        try {
            // Other than the first time, always checkin first to make sure there is 
            // work to be done before we acquire the lock (since that is expensive, 
            // and is almost never necessary).  This must be done in a separate
            // transaction to prevent a deadlock under recovery conditions.
            // 除了第一次以外，总是先检查以确保有在我们获得锁之前有要做的工作（因为那很昂贵，
            //  并且几乎从不需要）。 这必须在单独的事务以防止在恢复条件下发生死锁。
            List<SchedulerStateRecord> failedRecords = null;
            if (!firstCheckIn) {
                failedRecords = clusterCheckIn(conn);
                commitConnection(conn);
            }
            
            if (firstCheckIn || (failedRecords.size() > 0)) {
                getLockHandler().obtainLock(conn, LOCK_STATE_ACCESS);
                transStateOwner = true;
    
                // Now that we own the lock, make sure we still have work to do. 
                // The first time through, we also need to make sure we update/create our state record
                // 现在我们拥有了锁，确保我们还有工作要做。第一次通过，我们还需要确保我们更新/创建我们的状态记录
                failedRecords = (firstCheckIn) ? clusterCheckIn(conn) : findFailedInstances(conn);
    
                if (failedRecords.size() > 0) {
                    getLockHandler().obtainLock(conn, LOCK_TRIGGER_ACCESS);
                    //getLockHandler().obtainLock(conn, LOCK_JOB_ACCESS);
                    transOwner = true;
    
                    clusterRecover(conn, failedRecords); // 集群恢复
                    recovered = true;
                }
            }
            
            commitConnection(conn);
        } catch (JobPersistenceException e) {
            rollbackConnection(conn);
            throw e;
        } finally {
            try {
                releaseLock(LOCK_TRIGGER_ACCESS, transOwner);
            } finally {
                try {
                    releaseLock(LOCK_STATE_ACCESS, transStateOwner);
                } finally {
                    cleanupConnection(conn);
                }
            }
        }

        firstCheckIn = false;

        return recovered;
    }

    /**
     * Get a list of all scheduler instances in the cluster that may have failed.
     * This includes this scheduler if it is checking in for the first time.
     */
    protected List<SchedulerStateRecord> findFailedInstances(Connection conn)
        throws JobPersistenceException {
        try {
            List<SchedulerStateRecord> failedInstances = new LinkedList<SchedulerStateRecord>();
            boolean foundThisScheduler = false;
            long timeNow = System.currentTimeMillis();
            
            List<SchedulerStateRecord> states = getDelegate().selectSchedulerStateRecords(conn, null); // 查询是有调度器状态记录

            for(SchedulerStateRecord rec: states) {
        
                // find own record...
                if (rec.getSchedulerInstanceId().equals(getInstanceId())) { // 找到自己，如果是第一次检入必检查（按失败实例算）
                    foundThisScheduler = true;
                    if (firstCheckIn) {
                        failedInstances.add(rec);
                    }
                } else {
                    // find failed instances...
                    if (calcFailedIfAfter(rec) < timeNow) { // 如果是其他实例则检查是否失败（已经有多久没检入了）
                        failedInstances.add(rec);
                    }
                }
            }
            
            // The first time through, also check for orphaned fired triggers.
            if (firstCheckIn) {
                failedInstances.addAll(findOrphanedFailedInstances(conn, states)); // 第一次检入，看一下是否有孤立实例（从未点过火）
            }
            
            // If not the first time but we didn't find our own instance, then
            // Someone must have done recovery for us.
            if ((!foundThisScheduler) && (!firstCheckIn)) { // 非第一次检入却没发现自己，那么肯定有人为我们完成了恢复（因为别人恢复完会删除我的所有状态记录）
                // FUTURE_TODO: revisit when handle self-failed-out impl'ed (see FUTURE_TODO in clusterCheckIn() below)
                getLog().warn(
                    "This scheduler instance (" + getInstanceId() + ") is still " + 
                    "active but was recovered by another instance in the cluster.  " +
                    "This may cause inconsistent behavior.");
            }
            
            return failedInstances;
        } catch (Exception e) {
            lastCheckin = System.currentTimeMillis();
            throw new JobPersistenceException("Failure identifying failed instances when checking-in: "
                    + e.getMessage(), e);
        }
    }
    
    /**
     * Create dummy <code>SchedulerStateRecord</code> objects for fired triggers
     * that have no scheduler state record.  Checkin timestamp and interval are
     * left as zero on these dummy <code>SchedulerStateRecord</code> objects.
     * 
     * @param schedulerStateRecords List of all current <code>SchedulerStateRecords</code>
     */
    private List<SchedulerStateRecord> findOrphanedFailedInstances(
            Connection conn, 
            List<SchedulerStateRecord> schedulerStateRecords) 
        throws SQLException, NoSuchDelegateException {
        List<SchedulerStateRecord> orphanedInstances = new ArrayList<SchedulerStateRecord>();
        
        Set<String> allFiredTriggerInstanceNames = getDelegate().selectFiredTriggerInstanceNames(conn); // 该实例所有的点火记录
        if (!allFiredTriggerInstanceNames.isEmpty()) {
            for (SchedulerStateRecord rec: schedulerStateRecords) {
                
                allFiredTriggerInstanceNames.remove(rec.getSchedulerInstanceId()); // 移除已点火过的实例
            }
            
            for (String inst: allFiredTriggerInstanceNames) {// 剩下的即为未点火过的实例
                
                SchedulerStateRecord orphanedInstance = new SchedulerStateRecord();
                orphanedInstance.setSchedulerInstanceId(inst);
                
                orphanedInstances.add(orphanedInstance);
                
                getLog().warn(
                    "Found orphaned fired triggers for instance: " + orphanedInstance.getSchedulerInstanceId());
            }
        }
        
        return orphanedInstances;
    }
    
    protected long calcFailedIfAfter(SchedulerStateRecord rec) {
        return rec.getCheckinTimestamp() +
            Math.max(rec.getCheckinInterval(), 
                    (System.currentTimeMillis() - lastCheckin)) +
            7500L;
    }
    
    protected List<SchedulerStateRecord> clusterCheckIn(Connection conn)
        throws JobPersistenceException {

        List<SchedulerStateRecord> failedInstances = findFailedInstances(conn);
        
        try {
            // FUTURE_TODO: handle self-failed-out

            // check in...
            lastCheckin = System.currentTimeMillis();
            if(getDelegate().updateSchedulerState(conn, getInstanceId(), lastCheckin) == 0) { // 更新检入
                getDelegate().insertSchedulerState(conn, getInstanceId(), // 更新不成功则插入
                        lastCheckin, getClusterCheckinInterval());
            }
            
        } catch (Exception e) {
            throw new JobPersistenceException("Failure updating scheduler state when checking-in: "
                    + e.getMessage(), e);
        }

        return failedInstances;
    }

    @SuppressWarnings("ConstantConditions")
    protected void clusterRecover(Connection conn, List<SchedulerStateRecord> failedInstances)
        throws JobPersistenceException {

        if (failedInstances.size() > 0) {

            long recoverIds = System.currentTimeMillis();

            logWarnIfNonZero(failedInstances.size(),
                    "ClusterManager: detected " + failedInstances.size()
                            + " failed or restarted instances.");
            try {
                for (SchedulerStateRecord rec : failedInstances) {
                    getLog().info(
                            "ClusterManager: Scanning for instance \""
                                    + rec.getSchedulerInstanceId()
                                    + "\"'s failed in-progress jobs.");

                    List<FiredTriggerRecord> firedTriggerRecs = getDelegate() // 该失败实例所有点火记录
                            .selectInstancesFiredTriggerRecords(conn,
                                    rec.getSchedulerInstanceId());

                    int acquiredCount = 0;
                    int recoveredCount = 0;
                    int otherCount = 0;

                    Set<TriggerKey> triggerKeys = new HashSet<TriggerKey>();

                    for (FiredTriggerRecord ftRec : firedTriggerRecs) {

                        TriggerKey tKey = ftRec.getTriggerKey();
                        JobKey jKey = ftRec.getJobKey();

                        triggerKeys.add(tKey); // 收集失败实例所有点过火的触发器

                        // release blocked triggers..
                        if (ftRec.getFireInstanceState().equals(STATE_BLOCKED)) { // 如果是阻塞态则改为waiting
                            getDelegate()
                                    .updateTriggerStatesForJobFromOtherState(
                                            conn, jKey,
                                            STATE_WAITING, STATE_BLOCKED);
                        } else if (ftRec.getFireInstanceState().equals(STATE_PAUSED_BLOCKED)) { // 如果是暂停阻塞态则更改为paused
                            getDelegate()
                                    .updateTriggerStatesForJobFromOtherState(
                                            conn, jKey,
                                            STATE_PAUSED, STATE_PAUSED_BLOCKED);
                        }

                        // release acquired triggers..
                        if (ftRec.getFireInstanceState().equals(STATE_ACQUIRED)) { // 如果是已获取态则改为waiting
                            getDelegate().updateTriggerStateFromOtherState(
                                    conn, tKey, STATE_WAITING,
                                    STATE_ACQUIRED);
                            acquiredCount++;
                        } else if (ftRec.isJobRequestsRecovery()) { // 如果该job执行恢复，则新建一个恢复触发器恢复调度job一次
                            // handle jobs marked for recovery that were not fully
                            // executed..
                            if (jobExists(conn, jKey)) {
                                @SuppressWarnings("deprecation")
                                SimpleTriggerImpl rcvryTrig = new SimpleTriggerImpl(
                                        "recover_"
                                                + rec.getSchedulerInstanceId()
                                                + "_"
                                                + String.valueOf(recoverIds++),
                                        Scheduler.DEFAULT_RECOVERY_GROUP,
                                        new Date(ftRec.getScheduleTimestamp()));
                                rcvryTrig.setJobName(jKey.getName());
                                rcvryTrig.setJobGroup(jKey.getGroup());
                                rcvryTrig.setMisfireInstruction(SimpleTrigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY);
                                rcvryTrig.setPriority(ftRec.getPriority());
                                JobDataMap jd = getDelegate().selectTriggerJobDataMap(conn, tKey.getName(), tKey.getGroup());
                                jd.put(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_NAME, tKey.getName());
                                jd.put(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_GROUP, tKey.getGroup());
                                jd.put(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_FIRETIME_IN_MILLISECONDS, String.valueOf(ftRec.getFireTimestamp()));
                                jd.put(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS, String.valueOf(ftRec.getScheduleTimestamp()));
                                rcvryTrig.setJobDataMap(jd);

                                rcvryTrig.computeFirstFireTime(null);
                                storeTrigger(conn, rcvryTrig, null, false,
                                        STATE_WAITING, false, true);
                                recoveredCount++;
                            } else {
                                getLog()
                                        .warn(
                                                "ClusterManager: failed job '"
                                                        + jKey
                                                        + "' no longer exists, cannot schedule recovery.");
                                otherCount++;
                            }
                        } else {
                            otherCount++;
                        }

                        // free up stateful job's triggers
                        if (ftRec.isJobDisallowsConcurrentExecution()) { // 如果job不允许并发执行，则也恢复不阻塞
                            getDelegate()
                                    .updateTriggerStatesForJobFromOtherState(
                                            conn, jKey,
                                            STATE_WAITING, STATE_BLOCKED);
                            getDelegate()
                                    .updateTriggerStatesForJobFromOtherState(
                                            conn, jKey,
                                            STATE_PAUSED, STATE_PAUSED_BLOCKED);
                        }
                    }

                    getDelegate().deleteFiredTriggers(conn, // 恢复完，删除该实例所有点火记录
                            rec.getSchedulerInstanceId());

                    // Check if any of the fired triggers we just deleted were the last fired trigger
                    // records of a COMPLETE trigger.
                    int completeCount = 0;
                    for (TriggerKey triggerKey : triggerKeys) { // 检查我们刚刚删除的任何已触发的触发器是否为COMPLETE触发器的最后一次触发记录。
                        if (getDelegate().selectTriggerState(conn, triggerKey).
                                equals(STATE_COMPLETE)) {
                            List<FiredTriggerRecord> firedTriggers =
                                    getDelegate().selectFiredTriggerRecords(conn, triggerKey.getName(), triggerKey.getGroup());
                            if (firedTriggers.isEmpty()) {

                                if (removeTrigger(conn, triggerKey)) {
                                    completeCount++;
                                }
                            }
                        }
                    }

                    logWarnIfNonZero(acquiredCount,
                            "ClusterManager: ......Freed " + acquiredCount
                                    + " acquired trigger(s).");
                    logWarnIfNonZero(completeCount,
                            "ClusterManager: ......Deleted " + completeCount
                                    + " complete triggers(s).");
                    logWarnIfNonZero(recoveredCount,
                            "ClusterManager: ......Scheduled " + recoveredCount
                                    + " recoverable job(s) for recovery.");
                    logWarnIfNonZero(otherCount,
                            "ClusterManager: ......Cleaned-up " + otherCount
                                    + " other failed job(s).");

                    if (!rec.getSchedulerInstanceId().equals(getInstanceId())) { // 是恢复的别人，则清空别人的调度器状态记录
                        getDelegate().deleteSchedulerState(conn,
                                rec.getSchedulerInstanceId());
                    }
                }
            } catch (Throwable e) {
                throw new JobPersistenceException("Failure recovering jobs: "
                        + e.getMessage(), e);
            }
        }
    }

    protected void logWarnIfNonZero(int val, String warning) {
        if (val > 0) {
            getLog().info(warning);
        } else {
            getLog().debug(warning);
        }
    }

    /**
     * <p>
     * Cleanup the given database connection.  This means restoring
     * any modified auto commit or transaction isolation connection
     * attributes, and then closing the underlying connection.
     * </p>
     * 
     * <p>
     * This is separate from closeConnection() because the Spring 
     * integration relies on being able to overload closeConnection() and
     * expects the same connection back that it originally returned
     * from the datasource. 
     * </p>
     * 
     * @see #closeConnection(Connection)
     */
    protected void cleanupConnection(Connection conn) {
        if (conn != null) {
            if (conn instanceof Proxy) {
                Proxy connProxy = (Proxy)conn;
                
                InvocationHandler invocationHandler = 
                    Proxy.getInvocationHandler(connProxy);
                if (invocationHandler instanceof AttributeRestoringConnectionInvocationHandler) {
                    AttributeRestoringConnectionInvocationHandler connHandler =
                        (AttributeRestoringConnectionInvocationHandler)invocationHandler;
                        
                    connHandler.restoreOriginalAtributes();
                    closeConnection(connHandler.getWrappedConnection());
                    return;
                }
            }
            
            // Wan't a Proxy, or was a Proxy, but wasn't ours.
            closeConnection(conn);
        }
    }
    
    
    /**
     * Closes the supplied <code>Connection</code>.
     * <p>
     * Ignores a <code>null Connection</code>.  
     * Any exception thrown trying to close the <code>Connection</code> is
     * logged and ignored.  
     * </p>
     * 
     * @param conn The <code>Connection</code> to close (Optional).
     */
    protected void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                getLog().error("Failed to close Connection", e);
            } catch (Throwable e) {
                getLog().error(
                    "Unexpected exception closing Connection." +
                    "  This is often due to a Connection being returned after or during shutdown.", e);
            }
        }
    }

    /**
     * Rollback the supplied connection.
     * 
     * <p>  
     * Logs any SQLException it gets trying to rollback, but will not propogate
     * the exception lest it mask the exception that caused the caller to 
     * need to rollback in the first place.
     * </p>
     *
     * @param conn (Optional)
     */
    protected void rollbackConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                getLog().error(
                    "Couldn't rollback jdbc connection. "+e.getMessage(), e);
            }
        }
    }
    
    /**
     * Commit the supplied connection
     *
     * @param conn (Optional)
     * @throws JobPersistenceException thrown if a SQLException occurs when the
     * connection is committed
     */
    protected void commitConnection(Connection conn)
        throws JobPersistenceException {

        if (conn != null) {
            try {
                conn.commit();
            } catch (SQLException e) {
                throw new JobPersistenceException(
                    "Couldn't commit jdbc connection. "+e.getMessage(), e);
            }
        }
    }
    
    /**
     * Implement this interface to provide the code to execute within
     * the a transaction template.  If no return value is required, execute
     * should just return null.
     * 
     * @see JobStoreSupport#executeInNonManagedTXLock(String, TransactionCallback, TransactionValidator)
     * @see JobStoreSupport#executeInLock(String, TransactionCallback)
     * @see JobStoreSupport#executeWithoutLock(TransactionCallback)
     */
    protected interface TransactionCallback<T> { // 执行事务
        T execute(Connection conn) throws JobPersistenceException;
    }

    protected interface TransactionValidator<T> { // 验证结果
        Boolean validate(Connection conn, T result) throws JobPersistenceException;
    }
    
    /**
     * Implement this interface to provide the code to execute within
     * the a transaction template that has no return value.
     * 
     * @see JobStoreSupport#executeInNonManagedTXLock(String, TransactionCallback, TransactionValidator)
     */
    protected abstract class VoidTransactionCallback implements TransactionCallback<Void> {
        public final Void execute(Connection conn) throws JobPersistenceException {
            executeVoid(conn);
            return null;
        }
        
        abstract void executeVoid(Connection conn) throws JobPersistenceException;
    }

    /**
     * Execute the given callback in a transaction. Depending on the JobStore, 
     * the surrounding transaction may be assumed to be already present 
     * (managed).  
     * 
     * <p>
     * This method just forwards to executeInLock() with a null lockName.
     * </p>
     * 
     * @see #executeInLock(String, TransactionCallback)
     */
    public <T> T executeWithoutLock(
        TransactionCallback<T> txCallback) throws JobPersistenceException {
        return executeInLock(null, txCallback);
    }

    /**
     * Execute the given callback having acquired the given lock.
     * Depending on the JobStore, the surrounding transaction may be 
     * assumed to be already present (managed).
     * 
     * @param lockName The name of the lock to acquire, for example
     * "TRIGGER_ACCESS".  If null, then no lock is acquired, but the
     * lockCallback is still executed in a transaction. 
     */
    protected abstract <T> T executeInLock(
        String lockName, 
        TransactionCallback<T> txCallback) throws JobPersistenceException;
    
    protected <T> T retryExecuteInNonManagedTXLock(String lockName, TransactionCallback<T> txCallback) {
        for (int retry = 1; !shutdown; retry++) {
            try {
                return executeInNonManagedTXLock(lockName, txCallback, null);
            } catch (JobPersistenceException jpe) {
                if(retry % 4 == 0) {
                    schedSignaler.notifySchedulerListenersError("An error occurred while " + txCallback, jpe);
                }
            } catch (RuntimeException e) {
                getLog().error("retryExecuteInNonManagedTXLock: RuntimeException " + e.getMessage(), e);
            }
            try {
                Thread.sleep(getDbRetryInterval()); // retry every N seconds (the db connection must be failed)
            } catch (InterruptedException e) {
                throw new IllegalStateException("Received interrupted exception", e);
            }
        }
        throw new IllegalStateException("JobStore is shutdown - aborting retry");
    }
    
    /**
     * Execute the given callback having optionally acquired the given lock.
     * This uses the non-managed transaction connection.
     * 
     * @param lockName The name of the lock to acquire, for example
     * "TRIGGER_ACCESS".  If null, then no lock is acquired, but the
     * lockCallback is still executed in a non-managed transaction. 
     */
    protected <T> T executeInNonManagedTXLock(
            String lockName, 
            TransactionCallback<T> txCallback, final TransactionValidator<T> txValidator) throws JobPersistenceException {
        boolean transOwner = false;
        Connection conn = null;
        try {
            if (lockName != null) { // 需要获取锁，则获取锁
                // If we aren't using db locks, then delay getting DB connection 
                // until after acquiring the lock since it isn't needed.
                if (getLockHandler().requiresConnection()) { // 要求有连接，其实就是db锁
                    conn = getNonManagedTXConnection();
                }
                
                transOwner = getLockHandler().obtainLock(conn, lockName); // 内存锁
            }
            
            if (conn == null) {
                conn = getNonManagedTXConnection();
            }
            
            final T result = txCallback.execute(conn); // 执行事务但不提交（数据不会持久化），这里抛异常其实直接退出了
            try {
                commitConnection(conn); // 提交事务，这里抛异常会执行验证器，看下结果能不能用
            } catch (JobPersistenceException e) {
                rollbackConnection(conn);
                if (txValidator == null || !retryExecuteInNonManagedTXLock(lockName, new TransactionCallback<Boolean>() { // 不验证结果或验证失败（重试验证直到验证执行成功）则抛异常
                    @Override
                    public Boolean execute(Connection conn) throws JobPersistenceException {
                        return txValidator.validate(conn, result); // 验证器抛JobPersistenceException异常会一直诱发重试验证
                    }
                })) {
                    throw e;
                }
            }

            Long sigTime = clearAndGetSignalSchedulingChangeOnTxCompletion(); // 清空上一次候选点火时间信号
            if(sigTime != null && sigTime >= 0) {
                signalSchedulingChangeImmediately(sigTime); // 通知调度线程调度发生更改
            }
            
            return result; // 返回事务执行结果
        } catch (JobPersistenceException e) {
            rollbackConnection(conn);
            throw e;
        } catch (RuntimeException e) {
            rollbackConnection(conn);
            throw new JobPersistenceException("Unexpected runtime exception: "
                    + e.getMessage(), e);
        } finally {
            try {
                releaseLock(lockName, transOwner);
            } finally {
                cleanupConnection(conn);
            }
        }
    }
    
    /////////////////////////////////////////////////////////////////////////////
    //
    // ClusterManager Thread
    //
    /////////////////////////////////////////////////////////////////////////////

    class ClusterManager extends Thread {

        private volatile boolean shutdown = false;

        private int numFails = 0;
        
        ClusterManager() {
            this.setPriority(Thread.NORM_PRIORITY + 2);
            this.setName("QuartzScheduler_" + instanceName + "-" + instanceId + "_ClusterManager");
            this.setDaemon(getMakeThreadsDaemons());
        }

        public void initialize() {
            this.manage(); // 做检入

            ThreadExecutor executor = getThreadExecutor();
            executor.execute(ClusterManager.this);
        }

        public void shutdown() {
            shutdown = true;
            this.interrupt();
        }

        private boolean manage() {
            boolean res = false;
            try {

                res = doCheckin();

                numFails = 0;
                getLog().debug("ClusterManager: Check-in complete.");
            } catch (Exception e) {
                if(numFails % 4 == 0) {
                    getLog().error(
                        "ClusterManager: Error managing cluster: "
                                + e.getMessage(), e);
                }
                numFails++;
            }
            return res;
        }

        @Override
        public void run() {
            while (!shutdown) {

                if (!shutdown) {
                    long timeToSleep = getClusterCheckinInterval();
                    long transpiredTime = (System.currentTimeMillis() - lastCheckin);
                    timeToSleep = timeToSleep - transpiredTime;
                    if (timeToSleep <= 0) {
                        timeToSleep = 100L;
                    }

                    if(numFails > 0) {
                        timeToSleep = Math.max(getDbRetryInterval(), timeToSleep);
                    }
                    
                    try {
                        Thread.sleep(timeToSleep); // 每过一段时间做一次集群检入
                    } catch (Exception ignore) {
                    }
                }

                if (!shutdown && this.manage()) {
                    signalSchedulingChangeImmediately(0L); // 检入成功后立即发一个调度更改信号
                }

            }//while !shutdown
        }
    }

    /////////////////////////////////////////////////////////////////////////////
    //
    // MisfireHandler Thread
    //
    /////////////////////////////////////////////////////////////////////////////

    class MisfireHandler extends Thread {

        private volatile boolean shutdown = false;

        private int numFails = 0;
        

        MisfireHandler() {
            this.setName("QuartzScheduler_" + instanceName + "-" + instanceId + "_MisfireHandler");
            this.setDaemon(getMakeThreadsDaemons());
        }

        public void initialize() {
            ThreadExecutor executor = getThreadExecutor();
            executor.execute(MisfireHandler.this);
        }

        public void shutdown() {
            shutdown = true;
            this.interrupt();
        }

        private RecoverMisfiredJobsResult manage() {
            try {
                getLog().debug("MisfireHandler: scanning for misfires...");

                RecoverMisfiredJobsResult res = doRecoverMisfires(); // 做失火恢复处理
                numFails = 0;
                return res;
            } catch (Exception e) {
                if(numFails % 4 == 0) {
                    getLog().error(
                        "MisfireHandler: Error handling misfires: "
                                + e.getMessage(), e);
                }
                numFails++;
            }
            return RecoverMisfiredJobsResult.NO_OP;
        }

        @Override
        public void run() {
            
            while (!shutdown) {

                long sTime = System.currentTimeMillis();

                RecoverMisfiredJobsResult recoverMisfiredJobsResult = manage();

                if (recoverMisfiredJobsResult.getProcessedMisfiredTriggerCount() > 0) { // 有失火的触发器
                    signalSchedulingChangeImmediately(recoverMisfiredJobsResult.getEarliestNewTime()); // 通知主调度程序，更早的新时间
                }

                if (!shutdown) {
                    long timeToSleep = 50l;  // At least a short pause to help balance threads
                    if (!recoverMisfiredJobsResult.hasMoreMisfiredTriggers()) {
                        timeToSleep = getMisfireThreshold() - (System.currentTimeMillis() - sTime); // 下一次检查失火等待时间
                        if (timeToSleep <= 0) {
                            timeToSleep = 50l;
                        }

                        if(numFails > 0) {
                            timeToSleep = Math.max(getDbRetryInterval(), timeToSleep);
                        }
                    }
                    
                    try {
                        Thread.sleep(timeToSleep); // 有更多的失火需要处理，则短暂的停顿一会（以帮助均衡线程）
                    } catch (Exception ignore) {
                    }
                }//while !shutdown
            }
        }
    }
}

// EOF
