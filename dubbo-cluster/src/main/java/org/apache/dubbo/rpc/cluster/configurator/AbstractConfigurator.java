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
package org.apache.dubbo.rpc.cluster.configurator;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.cluster.Configurator;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER;
import static org.apache.dubbo.common.constants.CommonConstants.ENABLED_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACES;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.COMPATIBLE_CONFIG_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.CONFIG_VERSION_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.OVERRIDE_PROVIDERS_KEY;

/**
 * 配置器基类
 */
public abstract class AbstractConfigurator implements Configurator {

    /**
     * 配置器url,override://
     */
    private final URL configuratorUrl;

    /**
     * 构造方法
     *
     * @param configuratorUrl 配置器url
     */
    public AbstractConfigurator(URL configuratorUrl) {
        if (configuratorUrl == null) {
            throw new IllegalArgumentException("configurator url == null");
        }
        this.configuratorUrl = configuratorUrl;
    }

    @Override
    public URL getUrl() {
        return configuratorUrl;
    }

    @Override
    public URL configure(URL url) {
        // If override url is not enabled or is invalid, just return.
        // 配置器配置了enable=false参数,或者配置器url或给定的url无效(主机为null则无效),则不进行配置
        if (!configuratorUrl.getParameter(ENABLED_KEY, true) || configuratorUrl.getHost() == null || url == null || url.getHost() == null) {
            return url;
        }
        /*
         * This if branch is created since 2.7.0.
         */
        // configVersion参数
        String apiVersion = configuratorUrl.getParameter(CONFIG_VERSION_KEY);
        // 非空
        if (StringUtils.isNotEmpty(apiVersion)) {
            // side
            String currentSide = url.getParameter(SIDE_KEY);
            // 配置器的side
            String configuratorSide = configuratorUrl.getParameter(SIDE_KEY);
            if (currentSide.equals(configuratorSide) && CONSUMER.equals(configuratorSide) && 0 == configuratorUrl.getPort()) {
                url = configureIfMatch(NetUtils.getLocalHost(), url);
            } else if (currentSide.equals(configuratorSide) && PROVIDER.equals(configuratorSide) && url.getPort() == configuratorUrl.getPort()) {
                url = configureIfMatch(url.getHost(), url);
            }
        }
        /*
         * This else branch is deprecated and is left only to keep compatibility with versions before 2.7.0
         */
        // 老的逻辑分支,已经废弃,为了兼容2.7.0以前的逻辑
        else {
            url = configureDeprecated(url);
        }
        return url;
    }

    @Deprecated
    private URL configureDeprecated(URL url) {
        // If override url has port, means it is a provider address. We want to control a specific provider with this override url, it may take effect on the specific provider instance or on consumers holding this provider instance.
        // 配置器存在port
        if (configuratorUrl.getPort() != 0) {
            if (url.getPort() == configuratorUrl.getPort()) {
                return configureIfMatch(url.getHost(), url);
            }
        }
        // 配置器不存在port
        else {
            /*
             *  override url don't have a port, means the ip override url specify is a consumer address or 0.0.0.0.
             *  1.If it is a consumer ip address, the intention is to control a specific consumer instance, it must takes effect at the consumer side, any provider received this override url should ignore.
             *  2.If the ip is 0.0.0.0, this override url can be used on consumer, and also can be used on provider.
             */
            // 给定的url的side参数为consumer
            if (url.getParameter(SIDE_KEY, PROVIDER).equals(CONSUMER)) {
                // NetUtils.getLocalHost is the ip address consumer registered to registry.
                return configureIfMatch(NetUtils.getLocalHost(), url);
            }
            // 给定的url的side参数为provider
            else if (url.getParameter(SIDE_KEY, CONSUMER).equals(PROVIDER)) {
                // take effect on all providers, so address must be 0.0.0.0, otherwise it won't flow to this if branch
                return configureIfMatch(ANYHOST_VALUE, url);
            }
        }
        return url;
    }

    /**
     * @param host 主机
     * @param url  要重写的url
     * @return
     */
    private URL configureIfMatch(String host, URL url) {
        // 配置器主机为0.0.0.0或给定主机与配置器一致
        if (ANYHOST_VALUE.equals(configuratorUrl.getHost()) || host.equals(configuratorUrl.getHost())) {
            // TODO, to support wildcards
            // providerAddresses参数
            String providers = configuratorUrl.getParameter(OVERRIDE_PROVIDERS_KEY);
            // 为空或包含给定url的地址或包含0.0.0.0
            if (StringUtils.isEmpty(providers) || providers.contains(url.getAddress()) || providers.contains(ANYHOST_VALUE)) {
                // 获取application参数
                String configApplication = configuratorUrl.getParameter(APPLICATION_KEY, configuratorUrl.getUsername());
                // 当前应用
                String currentApplication = url.getParameter(APPLICATION_KEY, url.getUsername());
                //
                if (configApplication == null || ANY_VALUE.equals(configApplication)
                        || configApplication.equals(currentApplication)) {
                    /*
                     * 不允许配置的参数
                     */
                    Set<String> conditionKeys = new HashSet<String>();
                    // category
                    conditionKeys.add(CATEGORY_KEY);
                    // check
                    conditionKeys.add(Constants.CHECK_KEY);
                    // dynamic
                    conditionKeys.add(DYNAMIC_KEY);
                    // enable
                    conditionKeys.add(ENABLED_KEY);
                    // group
                    conditionKeys.add(GROUP_KEY);
                    // version
                    conditionKeys.add(VERSION_KEY);
                    // application
                    conditionKeys.add(APPLICATION_KEY);
                    // side
                    conditionKeys.add(SIDE_KEY);
                    // configVersion
                    conditionKeys.add(CONFIG_VERSION_KEY);
                    // compatible_config
                    conditionKeys.add(COMPATIBLE_CONFIG_KEY);
                    // interfaces
                    conditionKeys.add(INTERFACES);
                    // 遍历配置器参数
                    for (Map.Entry<String, String> entry : configuratorUrl.getParameters().entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        // "~"开头或application参数或side参数
                        if (key.startsWith("~") || APPLICATION_KEY.equals(key) || SIDE_KEY.equals(key)) {
                            conditionKeys.add(key);
                            // ???
                            if (value != null && !ANY_VALUE.equals(value)
                                    && !value.equals(url.getParameter(key.startsWith("~") ? key.substring(1) : key))) {
                                return url;
                            }
                        }
                    }
                    // 指定的条件参数,不允许配置
                    return doConfigure(url, configuratorUrl.removeParameters(conditionKeys));
                }
            }
        }
        return url;
    }

    protected abstract URL doConfigure(URL currentUrl, URL configUrl);

}
