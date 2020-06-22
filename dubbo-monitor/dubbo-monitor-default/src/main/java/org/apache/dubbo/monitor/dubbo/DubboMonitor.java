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
package org.apache.dubbo.monitor.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ExecutorUtil;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.monitor.Monitor;
import org.apache.dubbo.monitor.MonitorService;
import org.apache.dubbo.rpc.Invoker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_PROTOCOL;

/**
 * DubboMonitor
 */
public class DubboMonitor implements Monitor {

    private static final Logger logger = LoggerFactory.getLogger(DubboMonitor.class);

    /**
     * The length of the array which is a container of the statistics
     */
    private static final int LENGTH = 10;

    /**
     * The timer for sending statistics
     * 调度执行器线程,用于定时上报
     */
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3, new NamedThreadFactory("DubboMonitorSendTimer", true));

    /**
     * The future that can cancel the <b>scheduledExecutorService</b>
     * 调度结果
     */
    private final ScheduledFuture<?> sendFuture;

    private final Invoker<MonitorService> monitorInvoker;

    private final MonitorService monitorService;

    /**
     * key为统计信息,value为原子引用
     */
    private final ConcurrentMap<Statistics, AtomicReference<long[]>> statisticsMap = new ConcurrentHashMap<Statistics, AtomicReference<long[]>>();

    /**
     * 构造方法
     *
     * @param monitorInvoker 监视器调用器
     * @param monitorService 监视器服务
     */
    public DubboMonitor(Invoker<MonitorService> monitorInvoker, MonitorService monitorService) {
        this.monitorInvoker = monitorInvoker;
        this.monitorService = monitorService;
        // The time interval for timer <b>scheduledExecutorService</b> to send data
        // interval参数,默认1分钟
        final long monitorInterval = monitorInvoker.getUrl().getPositiveParameter("interval", 60000);
        // collect timer for collecting statistics data
        sendFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                // collect data
                send();
            } catch (Throwable t) {
                logger.error("Unexpected error occur at send statistic, cause: " + t.getMessage(), t);
            }
        }, monitorInterval, monitorInterval, TimeUnit.MILLISECONDS);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Node接口实现
    @Override
    public URL getUrl() {
        return monitorInvoker.getUrl();
    }

    @Override
    public boolean isAvailable() {
        return monitorInvoker.isAvailable();
    }

    @Override
    public void destroy() {
        try {
            ExecutorUtil.cancelScheduledFuture(sendFuture);
        } catch (Throwable t) {
            logger.error("Unexpected error occur at cancel sender timer, cause: " + t.getMessage(), t);
        }
        monitorInvoker.destroy();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // MonitorService接口实现
    @Override
    public void collect(URL url) {
        // data to collect from url
        // success参数,默认为0
        int success = url.getParameter(MonitorService.SUCCESS, 0);
        // failure参数,默认为0
        int failure = url.getParameter(MonitorService.FAILURE, 0);
        // input参数,默认为0
        int input = url.getParameter(MonitorService.INPUT, 0);
        // output参数,默认为0
        int output = url.getParameter(MonitorService.OUTPUT, 0);
        // elapsed参数,默认为0
        int elapsed = url.getParameter(MonitorService.ELAPSED, 0);
        // concurrent参数,默认为0
        int concurrent = url.getParameter(MonitorService.CONCURRENT, 0);
        // init atomic reference
        // 创建统计
        Statistics statistics = new Statistics(url);
        // 原子引用
        AtomicReference<long[]> reference = statisticsMap.computeIfAbsent(statistics, k -> new AtomicReference<>());
        // use CompareAndSet to sum
        long[] current;
        // 要更新的目标值,需要CAS更新
        long[] update = new long[LENGTH];
        do {
            current = reference.get();
            // 不存在
            if (current == null) {
                update[0] = success;
                update[1] = failure;
                update[2] = input;
                update[3] = output;
                update[4] = elapsed;
                update[5] = concurrent;
                update[6] = input;
                update[7] = output;
                update[8] = elapsed;
                update[9] = concurrent;
            }
            // 存在,则累加
            else {
                update[0] = current[0] + success;
                update[1] = current[1] + failure;
                update[2] = current[2] + input;
                update[3] = current[3] + output;
                update[4] = current[4] + elapsed;
                update[5] = (current[5] + concurrent) / 2;
                update[6] = current[6] > input ? current[6] : input;
                update[7] = current[7] > output ? current[7] : output;
                update[8] = current[8] > elapsed ? current[8] : elapsed;
                update[9] = current[9] > concurrent ? current[9] : concurrent;
            }
        } while (!reference.compareAndSet(current, update));
    }

    @Override
    public List<URL> lookup(URL query) {
        return monitorService.lookup(query);
    }
    // -----------------------------------------------------------------------------------------------------------------


    public void send() {
        if (logger.isDebugEnabled()) {
            logger.debug("Send statistics to monitor " + getUrl());
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        // 遍历统计信息
        for (Map.Entry<Statistics, AtomicReference<long[]>> entry : statisticsMap.entrySet()) {
            // get statistics data
            Statistics statistics = entry.getKey();
            AtomicReference<long[]> reference = entry.getValue();
            long[] numbers = reference.get();
            long success = numbers[0];
            long failure = numbers[1];
            long input = numbers[2];
            long output = numbers[3];
            long elapsed = numbers[4];
            long concurrent = numbers[5];
            long maxInput = numbers[6];
            long maxOutput = numbers[7];
            long maxElapsed = numbers[8];
            long maxConcurrent = numbers[9];
            // dubbo参数
            String protocol = getUrl().getParameter(DEFAULT_PROTOCOL);

            // send statistics data
            URL url = statistics.getUrl()
                    .addParameters(
                            MonitorService.TIMESTAMP, timestamp,
                            MonitorService.SUCCESS, String.valueOf(success),
                            MonitorService.FAILURE, String.valueOf(failure),
                            MonitorService.INPUT, String.valueOf(input),
                            MonitorService.OUTPUT, String.valueOf(output),
                            MonitorService.ELAPSED, String.valueOf(elapsed),
                            MonitorService.CONCURRENT, String.valueOf(concurrent),
                            MonitorService.MAX_INPUT, String.valueOf(maxInput),
                            MonitorService.MAX_OUTPUT, String.valueOf(maxOutput),
                            MonitorService.MAX_ELAPSED, String.valueOf(maxElapsed),
                            MonitorService.MAX_CONCURRENT, String.valueOf(maxConcurrent),
                            DEFAULT_PROTOCOL, protocol
                    );
            // 调用监视器服务的rpc,上报信息
            monitorService.collect(url);

            // 清空上报的数据
            // reset
            long[] current;
            long[] update = new long[LENGTH];
            do {
                current = reference.get();
                if (current == null) {
                    update[0] = 0;
                    update[1] = 0;
                    update[2] = 0;
                    update[3] = 0;
                    update[4] = 0;
                    update[5] = 0;
                } else {
                    update[0] = current[0] - success;
                    update[1] = current[1] - failure;
                    update[2] = current[2] - input;
                    update[3] = current[3] - output;
                    update[4] = current[4] - elapsed;
                    update[5] = current[5] - concurrent;
                }
            } while (!reference.compareAndSet(current, update));
        }
    }


}
