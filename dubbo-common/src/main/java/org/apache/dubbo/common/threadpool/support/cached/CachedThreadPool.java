/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.threadpool.support.cached;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.threadlocal.NamedInternalThreadFactory;
import org.apache.dubbo.common.threadpool.ThreadPool;
import org.apache.dubbo.common.threadpool.support.AbortPolicyWithReport;

import java.util.AbstractQueue;
import java.util.Queue;
import java.util.concurrent.*;

import static org.apache.dubbo.common.constants.CommonConstants.ALIVE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CORE_THREADS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_ALIVE;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_CORE_THREADS;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_QUEUES;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_THREAD_NAME;
import static org.apache.dubbo.common.constants.CommonConstants.QUEUES_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.THREADS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.THREAD_NAME_KEY;

/**
 * This thread pool is self-tuned. Thread will be recycled after idle for one minute, and new thread will be created for
 * the upcoming request.
 *
 * @see java.util.concurrent.Executors#newCachedThreadPool()
 */

/**
 * 可扩容,可缩容
 */
public class CachedThreadPool implements ThreadPool {

    @Override
    public Executor getExecutor(URL url) {
        // threadname=Dubbo,线程名
        String name = url.getParameter(THREAD_NAME_KEY, DEFAULT_THREAD_NAME);
        // corethreads=0
        int cores = url.getParameter(CORE_THREADS_KEY, DEFAULT_CORE_THREADS);
        // threads=最大值
        int threads = url.getParameter(THREADS_KEY, Integer.MAX_VALUE);
        // queues=0
        int queues = url.getParameter(QUEUES_KEY, DEFAULT_QUEUES);
        // alive=60000
        int alive = url.getParameter(ALIVE_KEY, DEFAULT_ALIVE);

        BlockingQueue<Runnable> queue = queues == 0 ? new SynchronousQueue<Runnable>() :// 无容量
                (queues < 0 ? new LinkedBlockingQueue<Runnable>()// 无界队列
                        : new LinkedBlockingQueue<Runnable>(queues));// 有界队列
        return new ThreadPoolExecutor(
                cores,
                threads,
                alive,
                TimeUnit.MILLISECONDS,
                queue,
                new NamedInternalThreadFactory(name, true),//
                new AbortPolicyWithReport(name, url)// 拒绝
        );
    }
}
