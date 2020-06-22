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
import org.apache.dubbo.config.support.Parameter;

import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_VERSION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SSL_ENABLED_KEY;
import static org.apache.dubbo.config.Constants.PROTOCOLS_SUFFIX;

/**
 * 协议配置,只与服务端配置相关,与客户端无关
 */
public class ProtocolConfig extends AbstractConfig {

    private static final long serialVersionUID = 6913423882496634749L;

    /**
     * Protocol name
     * 协议名称
     */
    private String name;

    /**
     * Service ip address (when there are multiple network cards available)
     * 主机
     * 服务ip地址,多网卡时可用
     */
    private String host;

    /**
     * Service port
     * 端口
     */
    private Integer port;

    /**
     * Context path
     * 上下文路径
     */
    private String contextpath;

    /**
     * Thread pool
     * 线程池
     */
    private String threadpool;

    /**
     * Thread pool name
     * 线程池名称
     */
    private String threadname;

    /**
     * Thread pool core thread size
     * 核心线程数
     */
    private Integer corethreads;

    /**
     * Thread pool size (fixed size)
     * 固定池数量
     */
    private Integer threads;

    /**
     * IO thread pool size (fixed size)
     * io线程池大小
     */
    private Integer iothreads;

    /**
     * Thread pool's queue length
     * 队列长度
     */
    private Integer queues;

    /**
     * Max acceptable connections
     */
    private Integer accepts;

    /**
     * Protocol codec
     */
    private String codec;

    /**
     * Serialization
     */
    private String serialization;

    /**
     * Charset
     */
    private String charset;

    /**
     * Payload max length
     */
    private Integer payload;

    /**
     * Buffer size
     */
    private Integer buffer;

    /**
     * Heartbeat interval
     */
    private Integer heartbeat;

    /**
     * Access log
     */
    private String accesslog;

    /**
     * Transporter
     */
    private String transporter;

    /**
     * How information is exchanged
     */
    private String exchanger;

    /**
     * Thread dispatch mode
     */
    private String dispatcher;

    /**
     * Networker
     */
    private String networker;

    /**
     * Sever impl
     */
    private String server;

    /**
     * Client impl
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
     * Whether to register
     */
    private Boolean register;

    /**
     * whether it is a persistent connection
     */
    //TODO add this to provider config
    private Boolean keepAlive;

    // TODO add this to provider config
    private String optimizer;

    /**
     * The extension
     */
    private String extension;

    /**
     * The customized parameters
     */
    private Map<String, String> parameters;

    /**
     * If it's default
     */
    private Boolean isDefault;

    private Boolean sslEnabled;

    /**
     * 构造方法
     */
    public ProtocolConfig() {
    }

    /**
     * 构造方法
     *
     * @param name 扩展实现名称
     */
    public ProtocolConfig(String name) {
        setName(name);
    }

    /**
     * 构造方法
     *
     * @param name 扩展实现名称
     * @param port 端口
     */
    public ProtocolConfig(String name, int port) {
        setName(name);
        setPort(port);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可导出getter
    public String getThreadpool() {
        return threadpool;
    }

    public String getThreadname() {
        return threadname;
    }

    public Integer getCorethreads() {
        return corethreads;
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

    public String getSerialization() {
        return serialization;
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

    public Integer getHeartbeat() {
        return heartbeat;
    }

    public String getServer() {
        return server;
    }

    public String getClient() {
        return client;
    }

    public String getAccesslog() {
        return accesslog;
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

    public Boolean isRegister() {
        return register;
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

    public Boolean isDefault() {
        return isDefault;
    }

    @Parameter(key = SSL_ENABLED_KEY)
    public Boolean getSslEnabled() {
        return sslEnabled;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    public String getOptimizer() {
        return optimizer;
    }

    public String getExtension() {
        return extension;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可注入setter
    public final void setName(String name) {
        this.name = name;
        this.updateIdIfAbsent(name);
    }

    public void setHost(String host) {
        this.host = host;
    }

    public final void setPort(Integer port) {
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

    public void setCorethreads(Integer corethreads) {
        this.corethreads = corethreads;
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

    public void setSerialization(String serialization) {
        this.serialization = serialization;
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

    public void setHeartbeat(Integer heartbeat) {
        this.heartbeat = heartbeat;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public void setAccesslog(String accesslog) {
        this.accesslog = accesslog;
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

    public void setRegister(Boolean register) {
        this.register = register;
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

    public void setDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public void setSslEnabled(Boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public void setKeepAlive(Boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public void setOptimizer(String optimizer) {
        this.optimizer = optimizer;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 普通
    @Parameter(excluded = true)
    public String getName() {
        return name;
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

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    @Override
    public void refresh() {
        if (StringUtils.isEmpty(this.getName())) {
            this.setName(DUBBO_VERSION_KEY);
        }
        super.refresh();
        if (StringUtils.isNotEmpty(this.getId())) {
            this.setPrefix(PROTOCOLS_SUFFIX);
            super.refresh();
        }
    }

    @Override
    @Parameter(excluded = true)
    public boolean isValid() {
        return StringUtils.isNotEmpty(name);
    }
    // -----------------------------------------------------------------------------------------------------------------

}
