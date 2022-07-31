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

package org.quartz.simpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.quartz.SchedulerConfigException;
import org.quartz.spi.ThreadPool;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>简单的线程池实现
 *
 * This is class is a simple implementation of a thread pool, based on the
 * <code>{@link org.quartz.spi.ThreadPool}</code> interface.
 * </p>
 * 
 * <p>
 * <CODE>Runnable</CODE> objects are sent to the pool with the <code>{@link #runInThread(Runnable)}</code>
 * method, which blocks until a <code>Thread</code> becomes available.
 * </p>
 * 
 * <p>
 * The pool has a fixed number of <code>Thread</code>s, and does not grow or
 * shrink based on demand.
 * </p>
 * 
 * @author James House
 * @author Juergen Donnerstag
 */
public class SimpleThreadPool implements ThreadPool {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Data members.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    private int count = -1; // 线程数量

    private int prio = Thread.NORM_PRIORITY; // 线程优先级

    private boolean isShutdown = false; // 是否关闭
    private boolean handoffPending = false; // 是否有切换待处理（其实就是正在往线程池扔 Runnable 运行）

    private boolean inheritLoader = false; // 继承类加载器

    private boolean inheritGroup = true; // 继承线程组

    private boolean makeThreadsDaemons = false; // 是否是后台线程

    private ThreadGroup threadGroup; // 线程组

    private final Object nextRunnableLock = new Object(); // 扔下一个任务锁

    private List<WorkerThread> workers; // 所有工作线程
    private LinkedList<WorkerThread> availWorkers = new LinkedList<WorkerThread>(); // 可用工作线程链表
    private LinkedList<WorkerThread> busyWorkers = new LinkedList<WorkerThread>(); // 繁忙工作线程链表

    private String threadNamePrefix; // 线程名前缀

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private String schedulerInstanceName; // 调度器实例名

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Constructors.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    /**
     * <p>
     * Create a new (unconfigured) <code>SimpleThreadPool</code>.
     * </p>
     * 
     * @see #setThreadCount(int)
     * @see #setThreadPriority(int)
     */
    public SimpleThreadPool() {
    }

    /**
     * <p>
     * Create a new <code>SimpleThreadPool</code> with the specified number
     * of <code>Thread</code> s that have the given priority.
     * </p>
     * 
     * @param threadCount
     *          the number of worker <code>Threads</code> in the pool, must
     *          be > 0.
     * @param threadPriority
     *          the thread priority for the worker threads.
     * 
     * @see java.lang.Thread
     */
    public SimpleThreadPool(int threadCount, int threadPriority) {
        setThreadCount(threadCount);
        setThreadPriority(threadPriority);
    }

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Interface.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    public Logger getLog() {
        return log;
    }

    public int getPoolSize() {
        return getThreadCount();
    }

    /**
     * <p>
     * Set the number of worker threads in the pool - has no effect after
     * <code>initialize()</code> has been called.
     * </p>
     */
    public void setThreadCount(int count) {
        this.count = count;
    }

    /**
     * <p>
     * Get the number of worker threads in the pool.
     * </p>
     */
    public int getThreadCount() {
        return count;
    }

    /**
     * <p>
     * Set the thread priority of worker threads in the pool - has no effect
     * after <code>initialize()</code> has been called.
     * </p>
     */
    public void setThreadPriority(int prio) {
        this.prio = prio;
    }

    /**
     * <p>
     * Get the thread priority of worker threads in the pool.
     * </p>
     */
    public int getThreadPriority() {
        return prio;
    }

    public void setThreadNamePrefix(String prfx) {
        this.threadNamePrefix = prfx;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    /**
     * @return Returns the
     *         threadsInheritContextClassLoaderOfInitializingThread.
     */
    public boolean isThreadsInheritContextClassLoaderOfInitializingThread() {
        return inheritLoader;
    }

    /**
     * @param inheritLoader
     *          The threadsInheritContextClassLoaderOfInitializingThread to
     *          set.
     */
    public void setThreadsInheritContextClassLoaderOfInitializingThread(
            boolean inheritLoader) {
        this.inheritLoader = inheritLoader;
    }

    public boolean isThreadsInheritGroupOfInitializingThread() {
        return inheritGroup;
    }

    public void setThreadsInheritGroupOfInitializingThread(
            boolean inheritGroup) {
        this.inheritGroup = inheritGroup;
    }


    /**
     * @return Returns the value of makeThreadsDaemons.
     */
    public boolean isMakeThreadsDaemons() {
        return makeThreadsDaemons;
    }

    /**
     * @param makeThreadsDaemons
     *          The value of makeThreadsDaemons to set.
     */
    public void setMakeThreadsDaemons(boolean makeThreadsDaemons) {
        this.makeThreadsDaemons = makeThreadsDaemons;
    }
    
    public void setInstanceId(String schedInstId) {
    }

    public void setInstanceName(String schedName) {
        schedulerInstanceName = schedName;
    }

    public void initialize() throws SchedulerConfigException {

        if(workers != null && workers.size() > 0) // already initialized...
            return;
        
        if (count <= 0) {
            throw new SchedulerConfigException(
                    "Thread count must be > 0");
        }
        if (prio <= 0 || prio > 9) {
            throw new SchedulerConfigException(
                    "Thread priority must be > 0 and <= 9");
        }

        if(isThreadsInheritGroupOfInitializingThread()) {
            threadGroup = Thread.currentThread().getThreadGroup(); // 继承线程组
        } else {
            // follow the threadGroup tree to the root thread group.
            threadGroup = Thread.currentThread().getThreadGroup();
            ThreadGroup parent = threadGroup;
            while ( !parent.getName().equals("main") ) {
                threadGroup = parent;
                parent = threadGroup.getParent();
            }
            threadGroup = new ThreadGroup(parent, schedulerInstanceName + "-SimpleThreadPool"); // 使用根线程组
            if (isMakeThreadsDaemons()) {
                threadGroup.setDaemon(true);
            }
        }


        if (isThreadsInheritContextClassLoaderOfInitializingThread()) {
            getLog().info(
                    "Job execution threads will use class loader of thread: "
                            + Thread.currentThread().getName());
        }

        // create the worker threads and start them
        Iterator<WorkerThread> workerThreads = createWorkerThreads(count).iterator(); // 创建工作线程
        while(workerThreads.hasNext()) {
            WorkerThread wt = workerThreads.next();
            wt.start(); // 启动工作线程
            availWorkers.add(wt);
        }
    }

    protected List<WorkerThread> createWorkerThreads(int createCount) {
        workers = new LinkedList<WorkerThread>();
        for (int i = 1; i<= createCount; ++i) {
            String threadPrefix = getThreadNamePrefix();
            if (threadPrefix == null) {
                threadPrefix = schedulerInstanceName + "_Worker";
            }
            WorkerThread wt = new WorkerThread(this, threadGroup,
                threadPrefix + "-" + i,
                getThreadPriority(),
                isMakeThreadsDaemons());
            if (isThreadsInheritContextClassLoaderOfInitializingThread()) {
                wt.setContextClassLoader(Thread.currentThread()
                        .getContextClassLoader());
            }
            workers.add(wt);
        }

        return workers;
    }

    /**
     * <p>
     * Terminate any worker threads in this thread group.
     * </p>
     * 
     * <p>
     * Jobs currently in progress will complete.
     * </p>
     */
    public void shutdown() {
        shutdown(true);
    }

    /**
     * <p>
     * Terminate any worker threads in this thread group.
     * </p>
     * 
     * <p>
     * Jobs currently in progress will complete.
     * </p>
     */
    public void shutdown(boolean waitForJobsToComplete) {

        synchronized (nextRunnableLock) {
            getLog().debug("Shutting down threadpool...");

            isShutdown = true;

            if(workers == null) // case where the pool wasn't even initialize()ed
                return;

            // signal each worker thread to shut down
            Iterator<WorkerThread> workerThreads = workers.iterator();
            while(workerThreads.hasNext()) {
                WorkerThread wt = workerThreads.next();
                wt.shutdown(); // 遍历终止掉所有的worker
                availWorkers.remove(wt);
            }

            // Give waiting (wait(1000)) worker threads a chance to shut down.
            // Active worker threads will shut down after finishing their
            // current job.
            nextRunnableLock.notifyAll(); // 给worker线程一个机会去完成他们的工作（怎么感觉这里是在唤醒runInThread以便处理最后一个任务？？？）

            if (waitForJobsToComplete == true) {

                boolean interrupted = false;
                try {
                    // wait for hand-off in runInThread to complete...
                    while(handoffPending) { // 等待切换中的线程执行完成
                        try {
                            nextRunnableLock.wait(100);
                        } catch(InterruptedException _) {
                            interrupted = true;
                        }
                    }

                    // Wait until all worker threads are shut down
                    while (busyWorkers.size() > 0) { // 等待繁忙中的线程执行完成
                        WorkerThread wt = (WorkerThread) busyWorkers.getFirst();
                        try {
                            getLog().debug(
                                    "Waiting for thread " + wt.getName()
                                            + " to shut down");

                            // note: with waiting infinite time the
                            // application may appear to 'hang'.
                            nextRunnableLock.wait(2000);
                        } catch (InterruptedException _) {
                            interrupted = true;
                        }
                    }

                    workerThreads = workers.iterator();
                    while(workerThreads.hasNext()) { // 当前shutdown线程等待所有的worker线程执行结束
                        WorkerThread wt = (WorkerThread) workerThreads.next();
                        try {
                            wt.join();
                            workerThreads.remove();
                        } catch (InterruptedException _) {
                            interrupted = true;
                        }
                    }
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt(); // shutdown过程中发生中断，继续传播中断信号
                    }
                }

                getLog().debug("No executing jobs remaining, all threads stopped.");
            }
            getLog().debug("Shutdown of threadpool complete.");
        }
    }

    /**
     * <p>
     * Run the given <code>Runnable</code> object in the next available
     * <code>Thread</code>. If while waiting the thread pool is asked to
     * shut down, the Runnable is executed immediately within a new additional
     * thread.
     * </p>
     * 
     * @param runnable
     *          the <code>Runnable</code> to be added.
     */
    public boolean runInThread(Runnable runnable) {
        if (runnable == null) {
            return false;
        }

        synchronized (nextRunnableLock) {

            handoffPending = true; // 指示在切换中，如果后续有shutdown就等一等

            // Wait until a worker thread is available
            while ((availWorkers.size() < 1) && !isShutdown) { // 如果没有空闲线程且线程池未关闭则等一等
                try {
                    nextRunnableLock.wait(500);
                } catch (InterruptedException ignore) {
                }
            }

            if (!isShutdown) { // 线程池未关闭，则占用分配一个worker运行
                WorkerThread wt = (WorkerThread)availWorkers.removeFirst();
                busyWorkers.add(wt);
                wt.run(runnable);
            } else { // 线程池已关闭，则新建一个worker运行
                // If the thread pool is going down, execute the Runnable
                // within a new additional worker thread (no thread from the pool).
                WorkerThread wt = new WorkerThread(this, threadGroup,
                        "WorkerThread-LastJob", prio, isMakeThreadsDaemons(), runnable);
                busyWorkers.add(wt);
                workers.add(wt);
                wt.start();
            }
            nextRunnableLock.notifyAll();
            handoffPending = false;
        }

        return true;
    }

    public int blockForAvailableThreads() {
        synchronized(nextRunnableLock) {

            while((availWorkers.size() < 1 || handoffPending) && !isShutdown) { // 无空闲worker或正在安排任务中，就等一会
                try {
                    nextRunnableLock.wait(500);
                } catch (InterruptedException ignore) {
                }
            }

            return availWorkers.size(); // 返回了就表示有空闲worker可继续安排新工作了
        }
    }

    protected void makeAvailable(WorkerThread wt) {
        synchronized(nextRunnableLock) {
            if(!isShutdown) {
                availWorkers.add(wt);
            }
            busyWorkers.remove(wt);
            nextRunnableLock.notifyAll();
        }
    }

    protected void clearFromBusyWorkersList(WorkerThread wt) {
        synchronized(nextRunnableLock) {
            busyWorkers.remove(wt);
            nextRunnableLock.notifyAll();
        }
    }

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * WorkerThread Class.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    /**
     * <p>
     * A Worker loops, waiting to execute tasks.
     * </p>
     */
    class WorkerThread extends Thread {

        private final Object lock = new Object(); // 任务执行锁

        // A flag that signals the WorkerThread to terminate.
        private AtomicBoolean run = new AtomicBoolean(true); // 一个 WorkerThread 终止标志

        private SimpleThreadPool tp; // 所属线程池

        private Runnable runnable = null; // 要执行的任务
        
        private boolean runOnce = false; // 是否仅运行一次

        /**
         * <p>
         * Create a worker thread and start it. Waiting for the next Runnable,
         * executing it, and waiting for the next Runnable, until the shutdown
         * flag is set.
         * </p>
         */
        WorkerThread(SimpleThreadPool tp, ThreadGroup threadGroup, String name,
                     int prio, boolean isDaemon) {

            this(tp, threadGroup, name, prio, isDaemon, null);
        }

        /**
         * <p>创建一个工作线程，启动它执行Runnable后终止线程（一次执行）。
         *
         * Create a worker thread, start it, execute the runnable and terminate
         * the thread (one time execution).
         * </p>
         */
        WorkerThread(SimpleThreadPool tp, ThreadGroup threadGroup, String name,
                     int prio, boolean isDaemon, Runnable runnable) {

            super(threadGroup, name);
            this.tp = tp;
            this.runnable = runnable;
            if(runnable != null)
                runOnce = true;
            setPriority(prio);
            setDaemon(isDaemon);
        }

        /**
         * <p>
         * Signal the thread that it should terminate.
         * </p>
         */
        void shutdown() {
            run.set(false); // 标志要终止运行
        }

        public void run(Runnable newRunnable) { // 扔一个新任务，并唤醒处理
            synchronized(lock) {
                if(runnable != null) { // 这个线程被占用着，还没工作结束
                    throw new IllegalStateException("Already running a Runnable!");
                }

                runnable = newRunnable;
                lock.notifyAll();
            }
        }

        /**
         * <p>
         * Loop, executing targets as they are received.
         * </p>
         */
        @Override
        public void run() {
            boolean ran = false;
            
            while (run.get()) {
                try {
                    synchronized(lock) {
                        while (runnable == null && run.get()) { // 没有任务则等待
                            lock.wait(500);
                        }

                        if (runnable != null) { // 有任务则运行
                            ran = true;
                            runnable.run();
                        }
                    }
                } catch (InterruptedException unblock) {
                    // do nothing (loop will terminate if shutdown() was called
                    try {
                        getLog().error("Worker thread was interrupt()'ed.", unblock);
                    } catch(Exception e) {
                        // ignore to help with a tomcat glitch
                    }
                } catch (Throwable exceptionInRunnable) {
                    try {
                        getLog().error("Error while executing the Runnable: ",
                            exceptionInRunnable);
                    } catch(Exception e) {
                        // ignore to help with a tomcat glitch
                    }
                } finally {
                    synchronized(lock) {
                        runnable = null; // 线程工作结束，重置runnable为空，可再次接任务
                    }
                    // repair the thread in case the runnable mucked it up...
                    if(getPriority() != tp.getThreadPriority()) { // 修复线程优先级，以防被Runnable对象设置
                        setPriority(tp.getThreadPriority());
                    }

                    if (runOnce) { // 如果是一次性线程，则设置不可再运行，并从繁忙worker中移除
                           run.set(false);
                        clearFromBusyWorkersList(this);
                    } else if(ran) { // 运行成功，则从繁忙worker中移除，并添加到可用worker中，以便使用者再次往池中扔任务
                        ran = false;
                        makeAvailable(this);
                    }
                    // 如果本次唤醒没任务做，则什么也不用做，因为本来就在可用worker中
                }
            }

            //if (log.isDebugEnabled())
            try {
                getLog().debug("WorkerThread is shut down.");
            } catch(Exception e) {
                // ignore to help with a tomcat glitch
            }
        }
    }
}
