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
package org.apache.dubbo.config;

import org.apache.dubbo.common.utils.StringUtils;

/**
 * The service consumer default configuration
 *
 * 客户端的默认配置
 * @export
 */
public class ConsumerConfig extends AbstractReferenceConfig {

    private static final long serialVersionUID = 2827274711143680600L;

    /**
     * Whether to use the default protocol
     */
    private Boolean isDefault;

    /**
     * Networking framework client uses: netty, mina, etc.
     */
    private String client;

    /**
     * Consumer thread pool type: cached, fixed, limit, eager
     */
    private String threadpool;

    /**
     * Consumer threadpool core thread size
     */
    private Integer corethreads;

    /**
     * Consumer threadpool thread size
     */
    private Integer threads;

    /**
     * Consumer threadpool queue size
     */
    private Integer queues;

    /**
     * By default, a TCP long-connection communication is shared between the consumer process and the provider process.
     * This property can be set to share multiple TCP long-connection communications. Note that only the dubbo protocol takes effect.
     */
    private Integer shareconnections;
    // -----------------------------------------------------------------------------------------------------------------
    // 可导出getter
    public Boolean isDefault() {
        return isDefault;
    }
    public Boolean getDefault() {
        return isDefault;
    }
    public String getClient() {
        return client;
    }
    public String getThreadpool() {
        return threadpool;
    }
    public Integer getCorethreads() {
        return corethreads;
    }
    public Integer getThreads() {
        return threads;
    }
    public Integer getQueues() {
        return queues;
    }
    public Integer getShareconnections() {
        return shareconnections;
    }
    // -----------------------------------------------------------------------------------------------------------------
    // 可注入setter
    public void setDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }
    public void setClient(String client) {
        this.client = client;
    }
    public void setThreadpool(String threadpool) {
        this.threadpool = threadpool;
    }
    public void setCorethreads(Integer corethreads) {
        this.corethreads = corethreads;
    }
    public void setThreads(Integer threads) {
        this.threads = threads;
    }
    public void setQueues(Integer queues) {
        this.queues = queues;
    }
    public void setShareconnections(Integer shareconnections) {
        this.shareconnections = shareconnections;
    }
    // -----------------------------------------------------------------------------------------------------------------
    // 普通
    @Override
    public void setTimeout(Integer timeout) {
        super.setTimeout(timeout);
        String rmiTimeout = System.getProperty("sun.rmi.transport.tcp.responseTimeout");
        if (timeout != null && timeout > 0
                && (StringUtils.isEmpty(rmiTimeout))) {
            System.setProperty("sun.rmi.transport.tcp.responseTimeout", String.valueOf(timeout));
        }
    }
    // -----------------------------------------------------------------------------------------------------------------






}
