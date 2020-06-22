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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.support.Parameter;

import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.EXTRA_KEYS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SHUTDOWN_WAIT_KEY;
import static org.apache.dubbo.config.Constants.REGISTRIES_SUFFIX;

/**
 * RegistryConfig
 * 注册中心配置
 *
 * @export
 */
public class RegistryConfig extends AbstractConfig {

    public static final String NO_AVAILABLE = "N/A";
    private static final long serialVersionUID = 5508512956753757169L;

    /**
     * 注册中心的原始地址
     * 通过address属性配置,可以是完整的url,也可以不是
     * 框架会使用默认参数装填
     * url中必要的信息包括:protocol,host,port,path,parameters
     */
    private String address;

    /**
     * Username to login register center
     * 用户名
     */
    private String username;

    /**
     * Password to login register center
     * 密码
     */
    private String password;

    /**
     * Default port for register center
     * 端口
     */
    private Integer port;

    /**
     * Protocol for register center
     * 协议
     */
    private String protocol;

    /**
     * Network transmission type
     * 网络传输类型???
     */
    private String transporter;
    /**
     * 服务器???
     */
    private String server;
    /**
     * 客户端???
     */
    private String client;

    /**
     * Affects how traffic distributes among registries, useful when subscribing multiple registries, available options:
     * 1. zone-aware, a certain type of traffic always goes to one Registry according to where the traffic is originated.
     */
    private String cluster;

    /**
     * The region where the registry belongs, usually used to isolate traffics
     */
    private String zone;

    /**
     * The group the services registry in
     */
    private String group;

    private String version;

    /**
     * Request timeout in milliseconds for register center
     */
    private Integer timeout;

    /**
     * Session timeout in milliseconds for register center
     */
    private Integer session;

    /**
     * File for saving register center dynamic list
     */
    private String file;

    /**
     * Wait time before stop
     */
    private Integer wait;

    /**
     * Whether to check if register center is available when boot up
     */
    private Boolean check;

    /**
     * Whether to allow dynamic service to register on the register center
     */
    private Boolean dynamic;

    /**
     * Whether to export service on the register center
     */
    private Boolean register;

    /**
     * Whether allow to subscribe service on the register center
     */
    private Boolean subscribe;

    /**
     * The customized parameters
     */
    private Map<String, String> parameters;

    /**
     * Whether it's default
     */
    private Boolean isDefault;

    /**
     * Simple the registry. both useful for provider and consumer
     *
     * @since 2.7.0
     */
    private Boolean simplified;
    /**
     * After simplify the registry, should add some parameter individually. just for provider.
     * <p>
     * such as: extra-keys = A,b,c,d
     *
     * @since 2.7.0
     */
    private String extraKeys;

    /**
     * the address work as config center or not
     * 是否作为配置中心运行
     */
    private Boolean useAsConfigCenter;

    /**
     * the address work as remote metadata center or not
     */
    private Boolean useAsMetadataCenter;

    /**
     * list of rpc protocols accepted by this registry, for example, "dubbo,rest"
     */
    private String accepts;

    /**
     * Always use this registry first if set to true, useful when subscribe to multiple registries
     */
    private Boolean preferred;

    /**
     * Affects traffic distribution among registries, useful when subscribe to multiple registries
     * Take effect only when no preferred registry is specified.
     */
    private Integer weight;


    /**
     * 构造方法
     */
    public RegistryConfig() {
    }

    /**
     * 构造方法
     *
     * @param address 地址???
     */
    public RegistryConfig(String address) {
        setAddress(address);
    }

    /**
     * 构造方法
     *
     * @param address  地址???
     * @param protocol 协议
     */
    public RegistryConfig(String address, String protocol) {
        setAddress(address);
        setProtocol(protocol);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可导出getter
    public String getProtocol() {
        return protocol;
    }

    public Integer getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Boolean isCheck() {
        return check;
    }

    public String getFile() {
        return file;
    }

    public String getTransporter() {
        return transporter;
    }

    public String getServer() {
        return server;
    }

    public String getClient() {
        return client;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public Integer getSession() {
        return session;
    }

    public Boolean isDynamic() {
        return dynamic;
    }

    public Boolean isRegister() {
        return register;
    }

    public Boolean isSubscribe() {
        return subscribe;
    }

    public String getCluster() {
        return cluster;
    }

    public String getZone() {
        return zone;
    }

    public String getGroup() {
        return group;
    }

    public String getVersion() {
        return version;
    }

    public Boolean isDefault() {
        return isDefault;
    }

    public Boolean getSimplified() {
        return simplified;
    }

    @Parameter(key = EXTRA_KEYS_KEY)
    public String getExtraKeys() {
        return extraKeys;
    }

    public String getAccepts() {
        return accepts;
    }

    public Boolean getPreferred() {
        return preferred;
    }

    public Integer getWeight() {
        return weight;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可注入setter
    public void setProtocol(String protocol) {
        this.protocol = protocol;
        this.updateIdIfAbsent(protocol);
    }

    public void setAddress(String address) {
        this.address = address;
        if (address != null) {
            try {
                URL url = URL.valueOf(address);
                // 用户名
                setUsername(url.getUsername());
                // 密码
                setPassword(url.getPassword());
                // 协议
                updateProtocolIfAbsent(url.getProtocol());
                // 端口
                updatePortIfAbsent(url.getPort());
                // 参数
                updateParameters(url.getParameters());
            } catch (Exception ignored) {
            }
        }
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setCheck(Boolean check) {
        this.check = check;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public void setTransporter(String transporter) {
        /*if(transporter != null && transporter.length() > 0 && ! ExtensionLoader.getExtensionLoader(Transporter.class).hasExtension(transporter)){
            throw new IllegalStateException("No such transporter type : " + transporter);
        }*/
        this.transporter = transporter;
    }

    public void setServer(String server) {
        /*if(server != null && server.length() > 0 && ! ExtensionLoader.getExtensionLoader(Transporter.class).hasExtension(server)){
            throw new IllegalStateException("No such server type : " + server);
        }*/
        this.server = server;
    }

    public void setClient(String client) {
        /*if(client != null && client.length() > 0 && ! ExtensionLoader.getExtensionLoader(Transporter.class).hasExtension(client)){
            throw new IllegalStateException("No such client type : " + client);
        }*/
        this.client = client;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public void setSession(Integer session) {
        this.session = session;
    }

    public void setDynamic(Boolean dynamic) {
        this.dynamic = dynamic;
    }

    public void setRegister(Boolean register) {
        this.register = register;
    }

    public void setSubscribe(Boolean subscribe) {
        this.subscribe = subscribe;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public void setSimplified(Boolean simplified) {
        this.simplified = simplified;
    }

    public void setExtraKeys(String extraKeys) {
        this.extraKeys = extraKeys;
    }

    public void setUseAsConfigCenter(Boolean useAsConfigCenter) {
        this.useAsConfigCenter = useAsConfigCenter;
    }

    public void setUseAsMetadataCenter(Boolean useAsMetadataCenter) {
        this.useAsMetadataCenter = useAsMetadataCenter;
    }

    public void setAccepts(String accepts) {
        this.accepts = accepts;
    }

    public void setPreferred(Boolean preferred) {
        this.preferred = preferred;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 普通
    @Parameter(excluded = true)
    public String getAddress() {
        return address;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public void updateParameters(Map<String, String> parameters) {
        if (CollectionUtils.isEmptyMap(parameters)) {
            return;
        }
        if (this.parameters == null) {
            this.parameters = parameters;
        } else {
            this.parameters.putAll(parameters);
        }
    }

    @Parameter(excluded = true)
    public Boolean getUseAsConfigCenter() {
        return useAsConfigCenter;
    }

    @Parameter(excluded = true)
    public Boolean getUseAsMetadataCenter() {
        return useAsMetadataCenter;
    }

    @Override
    public void refresh() {
        super.refresh();
        if (StringUtils.isNotEmpty(this.getId())) {
            this.setPrefix(REGISTRIES_SUFFIX);
            super.refresh();
        }
    }

    @Override
    @Parameter(excluded = true)
    public boolean isValid() {
        // empty protocol will default to 'dubbo'
        return !StringUtils.isEmpty(address);
    }

    protected void updatePortIfAbsent(Integer value) {
        if (value != null && value > 0 && port == null) {
            this.port = value;
        }
    }

    protected void updateProtocolIfAbsent(String value) {
        if (StringUtils.isNotEmpty(value) && StringUtils.isEmpty(protocol)) {
            this.protocol = value;
        }
    }
    // -----------------------------------------------------------------------------------------------------------------

//    /**
//     * @return wait
//     * @see org.apache.dubbo.config.ProviderConfig#getWait()
//     * @deprecated
//     */
//    @Deprecated
//    public Integer getWait() {
//        return wait;
//    }
//
//    /**
//     * @param wait
//     * @see org.apache.dubbo.config.ProviderConfig#setWait(Integer)
//     * @deprecated
//     */
//    @Deprecated
//    public void setWait(Integer wait) {
//        this.wait = wait;
//        if (wait != null && wait > 0) {
//            System.setProperty(SHUTDOWN_WAIT_KEY, String.valueOf(wait));
//        }
//    }

}
