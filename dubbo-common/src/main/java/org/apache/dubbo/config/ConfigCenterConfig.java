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
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.config.support.Parameter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.CONFIG_CONFIGFILE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CONFIG_ENABLE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROTOCOL_KEY;
import static org.apache.dubbo.config.Constants.CONFIG_APP_CONFIGFILE_KEY;
import static org.apache.dubbo.config.Constants.ZOOKEEPER_PROTOCOL;

/**
 * ConfigCenterConfig
 * 配置中心配置
 */
public class ConfigCenterConfig extends AbstractConfig {

    private AtomicBoolean inited = new AtomicBoolean(false);

    private String protocol;
    private String address;
    private Integer port;

    /* The config center cluster, it's real meaning may very on different Config Center products. */
    private String cluster;

    /* The namespace of the config center, generally it's used for multi-tenant,
    but it's real meaning depends on the actual Config Center you use.
    */

    private String namespace = CommonConstants.DUBBO;
    /* The group of the config center, generally it's used to identify an isolated space for a batch of config items,
    but it's real meaning depends on the actual Config Center you use.
    */
    private String group = CommonConstants.DUBBO;
    private String username;
    private String password;
    private Long timeout = 3000L;

    // If the Config Center is given the highest priority, it will override all the other configurations
    private Boolean highestPriority = true;

    // Decide the behaviour when initial connection try fails, 'true' means interrupt the whole process once fail.
    private Boolean check = true;

    /* Used to specify the key that your properties file mapping to, most of the time you do not need to change this parameter.
    Notice that for Apollo, this parameter is meaningless, set the 'namespace' is enough.
    */
    /**
     * 全局属性配置文件路径
     * 默认为dubbo.properties
     */
    private String configFile = CommonConstants.DEFAULT_DUBBO_PROPERTIES;

    /* the .properties file under 'configFile' is global shared while .properties under this one is limited only to this application
     */
    /**
     * 应用属性配置文件路径
     */
    private String appConfigFile;

    /* If the Config Center product you use have some special parameters that is not covered by this class, you can add it to here.
    For example, with XML:
      <dubbo:config-center>
           <dubbo:parameter key="{your key}" value="{your value}" />
      </dubbo:config-center>
     */
    private Map<String, String> parameters;

    /**
     * 全局属性配置
     */
    private Map<String, String> externalConfiguration;

    /**
     * 应用属性配置
     */
    private Map<String, String> appExternalConfiguration;

    /**
     * 构造方法
     */
    public ConfigCenterConfig() {
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可导出getter
    public String getProtocol() {
        return protocol;
    }

    public Integer getPort() {
        return port;
    }

    public String getCluster() {
        return cluster;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getGroup() {
        return group;
    }

    public Boolean isCheck() {
        return check;
    }

    @Parameter(key = CONFIG_ENABLE_KEY)
    public Boolean isHighestPriority() {
        return highestPriority;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Long getTimeout() {
        return timeout;
    }

    @Parameter(key = CONFIG_CONFIGFILE_KEY)
    public String getConfigFile() {
        return configFile;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可注入setter
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public void setAddress(String address) {
        this.address = address;
        if (address != null) {
            try {
                URL url = URL.valueOf(address);
                setUsername(url.getUsername());
                setPassword(url.getPassword());
                updateIdIfAbsent(url.getProtocol());
                updateProtocolIfAbsent(url.getProtocol());
                updatePortIfAbsent(url.getPort());
                updateParameters(url.getParameters());
            } catch (Exception ignored) {
            }
        }
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setCheck(Boolean check) {
        this.check = check;
    }

    public void setHighestPriority(Boolean highestPriority) {
        this.highestPriority = highestPriority;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public void setAppConfigFile(String appConfigFile) {
        this.appConfigFile = appConfigFile;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 普通
    public URL toUrl() {
        Map<String, String> parameters = new HashMap<>();
        // getter获取参数
        appendParameters(parameters, this);
        // 地址为空则默认为0.0.0.0
        if (StringUtils.isEmpty(address)) {
            address = ANYHOST_VALUE;
        }
        // path参数为ConfigCenterConfig类名
        parameters.put(PATH_KEY, ConfigCenterConfig.class.getSimpleName());
        // use 'zookeeper' as the default configcenter.
        // protocol参数为空,则默认使用zookeeper
        if (StringUtils.isEmpty(parameters.get(PROTOCOL_KEY))) {
            parameters.put(PROTOCOL_KEY, ZOOKEEPER_PROTOCOL);
        }
        return UrlUtils.parseURL(address, parameters);
    }

    public boolean checkOrUpdateInited() {
        return inited.compareAndSet(false, true);
    }

    public Map<String, String> getExternalConfiguration() {
        return externalConfiguration;
    }

    public Map<String, String> getAppExternalConfiguration() {
        return appExternalConfiguration;
    }

    public void setExternalConfig(Map<String, String> externalConfiguration) {
        this.externalConfiguration = externalConfiguration;
    }

    public void setAppExternalConfig(Map<String, String> appExternalConfiguration) {
        this.appExternalConfiguration = appExternalConfiguration;
    }

    @Parameter(excluded = true)
    public String getAddress() {
        return address;
    }

    @Parameter(excluded = true, key = CONFIG_APP_CONFIGFILE_KEY)
    public String getAppConfigFile() {
        return appConfigFile;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    @Override
    @Parameter(excluded = true)
    public boolean isValid() {
        // address为空则无效
        if (StringUtils.isEmpty(address)) {
            return false;
        }
        // 包含:// 或协议非空则有效
        return address.contains("://") || StringUtils.isNotEmpty(protocol);
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
    // -----------------------------------------------------------------------------------------------------------------

}
