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

import org.apache.dubbo.config.support.Parameter;

import java.util.ArrayList;
import java.util.Arrays;



/**
 * 服务提供者的默认配置
 *
 * 配置信息都在这里,
 * ServiceConfig没有这些配置,但是有发布服务的逻辑.
 */
public class ProviderConfig extends AbstractServiceConfig {

    private static final long serialVersionUID = 6913423882496634749L;

    // ======== protocol default values, it'll take effect when protocol's attributes are not set ========

    /**
     * Service ip addresses (used when there are multiple network cards available)
     * 当存在多个网卡时可用
     */
    private String host;

    /**
     * Service port
     * 服务端口
     */
    private Integer port;

    /**
     * Context path
     * 上下文路径
     */
    private String contextpath;

    /**
     * Thread pool
     * 线程池???
     */
    private String threadpool;

    /**
     * Thread pool name
     * 线程池名称???
     */
    private String threadname;

    /**
     * Thread pool size (fixed size)
     * 线程池固定大小
     */
    private Integer threads;

    /**
     * IO thread pool size (fixed size)
     * io线程池固定大小
     */
    private Integer iothreads;

    /**
     * Thread pool queue length
     * 线程池队列大小
     */
    private Integer queues;

    /**
     * Max acceptable connections
     * 最大可接收连接数
     */
    private Integer accepts;

    /**
     * Protocol codec
     * 协议编解码???
     */
    private String codec;

    /**
     * The serialization charset
     * 序列化字符集
     */
    private String charset;

    /**
     * Payload max length
     * 最大载体长度
     */
    private Integer payload;

    /**
     * The network io buffer size
     * 网络io缓冲区大小
     */
    private Integer buffer;

    /**
     * Transporter
     * 传输器???
     */
    private String transporter;

    /**
     * How information gets exchanged
     */
    private String exchanger;

    /**
     * Thread dispatching mode
     */
    private String dispatcher;

    /**
     * Networker
     */
    private String networker;

    /**
     * The server-side implementation model of the protocol
     */
    private String server;

    /**
     * The client-side implementation model of the protocol
     */
    private String client;

    /**
     * Supported telnet commands, separated with comma.
     */
    private String telnet;

    /**
     * Command line prompt
     */
    private String prompt;

    /**
     * Status check
     */
    private String status;

    /**
     * Wait time when stop
     */
    private Integer wait;

    /**
     * Whether to use the default protocol
     */
    private Boolean isDefault;

    // -----------------------------------------------------------------------------------------------------------------
    // 可导出getter
    public String getThreadpool() {
        return threadpool;
    }

    public String getThreadname() {
        return threadname;
    }

    public Integer getThreads() {
        return threads;
    }

    public Integer getIothreads() {
        return iothreads;
    }

    public Integer getQueues() {
        return queues;
    }

    public Integer getAccepts() {
        return accepts;
    }

    public String getCodec() {
        return codec;
    }

    public String getCharset() {
        return charset;
    }

    public Integer getPayload() {
        return payload;
    }

    public Integer getBuffer() {
        return buffer;
    }

    public String getServer() {
        return server;
    }

    public String getClient() {
        return client;
    }

    public String getTelnet() {
        return telnet;
    }

    @Parameter(escaped = true)
    public String getPrompt() {
        return prompt;
    }

    public String getStatus() {
        return status;
    }

    public String getTransporter() {
        return transporter;
    }

    public String getExchanger() {
        return exchanger;
    }

    public String getDispatcher() {
        return dispatcher;
    }

    public String getNetworker() {
        return networker;
    }

    public Integer getWait() {
        return wait;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可注入setter
    @Deprecated
    public void setDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public void setHost(String host) {
        this.host = host;
    }

    @Deprecated
    public void setPort(Integer port) {
        this.port = port;
    }

    @Deprecated
    public void setPath(String path) {
        setContextpath(path);
    }

    public void setContextpath(String contextpath) {
        this.contextpath = contextpath;
    }

    public void setThreadpool(String threadpool) {
        this.threadpool = threadpool;
    }

    public void setThreadname(String threadname) {
        this.threadname = threadname;
    }

    public void setThreads(Integer threads) {
        this.threads = threads;
    }

    public void setIothreads(Integer iothreads) {
        this.iothreads = iothreads;
    }

    public void setQueues(Integer queues) {
        this.queues = queues;
    }

    public void setAccepts(Integer accepts) {
        this.accepts = accepts;
    }

    public void setCodec(String codec) {
        this.codec = codec;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public void setPayload(Integer payload) {
        this.payload = payload;
    }

    public void setBuffer(Integer buffer) {
        this.buffer = buffer;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public void setTelnet(String telnet) {
        this.telnet = telnet;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTransporter(String transporter) {
        this.transporter = transporter;
    }

    public void setExchanger(String exchanger) {
        this.exchanger = exchanger;
    }

    public void setDispatcher(String dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void setNetworker(String networker) {
        this.networker = networker;
    }

    public void setWait(Integer wait) {
        this.wait = wait;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 普通方法
//    @Deprecated
//    public void setProtocol(String protocol) {
//        this.protocols = new ArrayList<>(Arrays.asList(new ProtocolConfig(protocol)));
//    }
    @Parameter(excluded = true)
    public Boolean isDefault() {
        return isDefault;
    }

    @Parameter(excluded = true)
    public String getHost() {
        return host;
    }

    @Parameter(excluded = true)
    public Integer getPort() {
        return port;
    }

    @Deprecated
    @Parameter(excluded = true)
    public String getPath() {
        return getContextpath();
    }

    @Parameter(excluded = true)
    public String getContextpath() {
        return contextpath;
    }

    @Override
    public String getCluster() {
        return super.getCluster();
    }

    @Override
    public Integer getConnections() {
        return super.getConnections();
    }

    @Override
    public Integer getTimeout() {
        return super.getTimeout();
    }

    @Override
    public Integer getRetries() {
        return super.getRetries();
    }

    @Override
    public String getLoadbalance() {
        return super.getLoadbalance();
    }

    @Override
    public Boolean isAsync() {
        return super.isAsync();
    }

    @Override
    public Integer getActives() {
        return super.getActives();
    }
    // -----------------------------------------------------------------------------------------------------------------

}
