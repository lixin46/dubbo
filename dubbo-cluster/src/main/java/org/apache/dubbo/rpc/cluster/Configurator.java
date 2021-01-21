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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.dubbo.rpc.cluster.Constants.PRIORITY_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;

/**
 * Configurator. (SPI, Prototype, ThreadSafe)
 * 配置器???
 */
public interface Configurator extends Comparable<Configurator> {

    /**
     * 解析url,转换override url,生成配置器列表
     * url协议如下:
     * 1.override://0.0.0.0/...(或者override://ip:port...?anyhost=true)&para1=value1...代表全局规则(所有服务提供者都会生效)
     * 2.override://ip:port...?anyhost=false,特定规则(只有某些的服务提供者)
     * 3.override:// 规则不支持,需要通过注册中心计算
     * 4.override://0.0.0.0/ 没有参数代表清除重写规则
     * @param configuratorUrls
     * @return
     */
    static Optional<List<Configurator>> toConfigurators(List<URL> configuratorUrls) {
        if (CollectionUtils.isEmpty(configuratorUrls)) {
            return Optional.empty();
        }

        // 配置器工厂适配器实例
        // 根据url协议进行动态映射
        ConfiguratorFactory configuratorFactoryAdapter = ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                .getAdaptiveExtension();

        List<Configurator> configurators = new ArrayList<>(configuratorUrls.size());
        // 遍历url
        for (URL url : configuratorUrls) {
            // empty://则清空列表
            if (EMPTY_PROTOCOL.equals(url.getProtocol())) {
                configurators.clear();
                break;
            }
            // 获取重写的参数
            Map<String, String> override = new HashMap<>(url.getParameters());
            //The anyhost parameter of override may be added automatically, it can't change the judgement of changing url
            // 删除anyhost参数
            override.remove(ANYHOST_KEY);
            // 没有重写参数,则清空列表
            if (override.isEmpty()) {
                configurators.clear();
                continue;
            }
            // 调用工厂适配器,创建配置器
            Configurator configurator = configuratorFactoryAdapter.getConfigurator(url);
            configurators.add(configurator);
        }
        // 排序
        Collections.sort(configurators);
        return Optional.of(configurators);
    }

    // -----------------------------------------------------------------------------------------------------------------
    /**
     * Sort by host, then by priority
     * 1. the url with a specific host ip should have higher priority than 0.0.0.0
     * 2. if two url has the same host, compare by priority value；
     */
    @Override
    default int compareTo(Configurator o) {
        if (o == null) {
            return -1;
        }

        int ipCompare = getUrl().getHost().compareTo(o.getUrl().getHost());
        // host is the same, sort by priority
        if (ipCompare == 0) {
            int i = getUrl().getParameter(PRIORITY_KEY, 0);
            int j = o.getUrl().getParameter(PRIORITY_KEY, 0);
            return Integer.compare(i, j);
        } else {
            return ipCompare;
        }
    }
    // -----------------------------------------------------------------------------------------------------------------

    /**
     *
     * @return 配置器url
     */
    URL getUrl();

    /**
     * 配置提供者url
     * @param url 老的提供者url
     * @return 新的提供者url
     */
    URL configure(URL url);

}
