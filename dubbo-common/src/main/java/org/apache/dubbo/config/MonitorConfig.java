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

import org.apache.dubbo.common.constants.RegistryConstants;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.support.Parameter;

import java.util.Map;

/**
 * MonitorConfig
 * 监听器配置
 *
 * @export
 */
public class MonitorConfig extends AbstractConfig {

    private static final long serialVersionUID = -1184681514659198203L;

    /**
     * The protocol of the monitor, if the value is registry, it will search the monitor address from the registry center,
     * otherwise, it will directly connect to the monitor center
     */
    private String protocol;

    /**
     * The monitor address
     * 地址
     */
    private String address;

    /**
     * The monitor user name
     */
    private String username;

    /**
     * The password
     */
    private String password;

    private String group;

    private String version;

    private String interval;

    /**
     * customized parameters
     */
    private Map<String, String> parameters;

    /**
     * If it's default
     */
    private Boolean isDefault;

    /**
     * 构造方法
     */
    public MonitorConfig() {
    }

    /**
     * 构造方法
     *
     * @param address
     */
    public MonitorConfig(String address) {
        this.address = address;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可导出getter
    public String getGroup() {
        return group;
    }

    public String getVersion() {
        return version;
    }

    public Boolean isDefault() {
        return isDefault;
    }

    public String getInterval() {
        return interval;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可注入setter
    public void setAddress(String address) {
        this.address = address;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public void setInterval(String interval) {
        this.interval = interval;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 普通
    @Parameter(excluded = true)
    public String getAddress() {
        return address;
    }

    @Parameter(excluded = true)
    public String getProtocol() {
        return protocol;
    }

    @Parameter(excluded = true)
    public String getUsername() {
        return username;
    }

    @Parameter(excluded = true)
    public String getPassword() {
        return password;
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
        return StringUtils.isNotEmpty(address) || RegistryConstants.REGISTRY_PROTOCOL.equals(protocol);
    }
    // -----------------------------------------------------------------------------------------------------------------


}
