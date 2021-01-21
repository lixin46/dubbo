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
package org.apache.dubbo.rpc.cluster.governance;

import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.extension.SPI;

/**
 * 治理规则仓库
 *
 * 提供添加/删除配置监听器的能力,监听器监听指定的key,所有key都有分组的概念
 *
 * 可以通过group+key获取对应的规则
 */
@SPI("default")
public interface GovernanceRuleRepository {

    String DEFAULT_GROUP = "dubbo";

    /**
     * 添加配置监听器,监听指定键值的变化
     * 键属于默认分组
     * @param key 指定的键
     * @param listener 配置监听器
     */
    default void addListener(String key, ConfigurationListener listener) {
        addListener(key, DEFAULT_GROUP, listener);
    }

    /**
     * 添加配置监听器,监听指定分组内指定键值的变化
     * @param key 指定的键
     * @param group 指定的分组
     * @param listener 配置监听器
     */
    void addListener(String key, String group, ConfigurationListener listener);

    default void removeListener(String key, ConfigurationListener listener) {
        removeListener(key, DEFAULT_GROUP, listener);
    }

    void removeListener(String key, String group, ConfigurationListener listener);

    /**
     * 获取指定分组中键对应的规则
     * 阻塞等待,直到成功
     * @param key 指定的键
     * @param group 指定的分组
     * @return 值
     */
    default String getRule(String key, String group) {
        return getRule(key, group, -1L);
    }

    /**
     * 获取指定分组中键对应的规则
     * 阻塞等待,直到超时
     * @param key 指定的键
     * @param group 指定的分组
     * @param timeout 指定的超时时间
     * @return 值
     * @throws IllegalStateException 超时
     */
    String getRule(String key, String group, long timeout) throws IllegalStateException;
}
