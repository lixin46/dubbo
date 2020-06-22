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
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.support.Parameter;

import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.DUBBO;
import static org.apache.dubbo.common.constants.CommonConstants.PROPERTIES_CHAR_SEPERATOR;

/**
 * MetadataReportConfig
 * 元数据报告配置
 * <dubbo:metadata-report></dubbo:metadata-report>
 *
 * @export
 */
public class MetadataReportConfig extends AbstractConfig {

    private static final long serialVersionUID = 55233L;
    /**
     * the value is : metadata-report
     */
    private static final String PREFIX_TAG = StringUtils.camelToSplitName(
            MetadataReportConfig.class.getSimpleName().substring(0, MetadataReportConfig.class.getSimpleName().length() - 6), PROPERTIES_CHAR_SEPERATOR);

    // Register center address
    private String address;

    // Username to login register center
    private String username;

    // Password to login register center
    private String password;

    // Request timeout in milliseconds for register center
    private Integer timeout;

    /**
     * The group the metadata in . It is the same as registry
     */
    private String group;

    // Customized parameters
    private Map<String, String> parameters;

    private Integer retryTimes;

    private Integer retryPeriod;
    /**
     * By default the metadatastore will store full metadata repeatedly every day .
     */
    private Boolean cycleReport;

    /**
     * Sync report, default async
     */
    private Boolean syncReport;

    /**
     * cluster
     */
    private Boolean cluster;

    /**
     * 构造方法
     */
    public MetadataReportConfig() {
    }

    /**
     * 构造方法
     *
     * @param address
     */
    public MetadataReportConfig(String address) {
        setAddress(address);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可导出getter
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Integer getTimeout() {
        return timeout;
    }

    @Parameter(key = "retry-times")
    public Integer getRetryTimes() {
        return retryTimes;
    }

    @Parameter(key = "retry-period")
    public Integer getRetryPeriod() {
        return retryPeriod;
    }

    @Parameter(key = "cycle-report")
    public Boolean getCycleReport() {
        return cycleReport;
    }

    @Parameter(key = "sync-report")
    public Boolean getSyncReport() {
        return syncReport;
    }

    public String getGroup() {
        return group;
    }

    public Boolean getCluster() {
        return cluster;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可注入setter
    public void setAddress(String address) {
        this.address = address;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public void setRetryTimes(Integer retryTimes) {
        this.retryTimes = retryTimes;
    }

    public void setRetryPeriod(Integer retryPeriod) {
        this.retryPeriod = retryPeriod;
    }

    public void setCycleReport(Boolean cycleReport) {
        this.cycleReport = cycleReport;
    }

    public void setSyncReport(Boolean syncReport) {
        this.syncReport = syncReport;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setCluster(Boolean cluster) {
        this.cluster = cluster;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 普通
    public URL toUrl() {
        String address = this.getAddress();
        if (StringUtils.isEmpty(address)) {
            return null;
        }
        Map<String, String> map = new HashMap<String, String>();
        appendParameters(map, this);
        if (!StringUtils.isEmpty(address)) {
            URL url = URL.valueOf(address);
            map.put("metadata", url.getProtocol());
            return new URL("metadata", url.getUsername(), url.getPassword(), url.getHost(),
                    url.getPort(), url.getPath(), map);
        }
        throw new IllegalArgumentException("The address of metadata report is invalid.");
    }

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

    @Override
    @Parameter(excluded = true)
    public String getPrefix() {
        return StringUtils.isNotEmpty(prefix) ? prefix : (DUBBO + "." + PREFIX_TAG);
    }

    @Override
    @Parameter(excluded = true)
    public boolean isValid() {
        return StringUtils.isNotEmpty(address);
    }
    // -----------------------------------------------------------------------------------------------------------------


}
