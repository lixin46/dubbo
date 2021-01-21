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

package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.Collections;
import java.util.List;

/**
 * 组件名称为wrapper
 * 注册中心工厂的包装器,用于装饰注册中心工厂
 */
public class RegistryFactoryWrapper implements RegistryFactory {

    /**
     * 被装饰的注册中心工厂
     */
    private RegistryFactory registryFactory;

    /**
     * 构造方法
     * @param registryFactory 注册中心工厂
     */
    public RegistryFactoryWrapper(RegistryFactory registryFactory) {
        this.registryFactory = registryFactory;
    }

    @Override
    public Registry getRegistry(URL url) {
        // 获取注册中心
        Registry registry = registryFactory.getRegistry(url);

        // 获取注册中心服务监听器
        ExtensionLoader<RegistryServiceListener> extensionLoader = ExtensionLoader.getExtensionLoader(RegistryServiceListener.class);
        // 获取激活的监听器实例
        List<RegistryServiceListener> listeners = extensionLoader.getActivateExtension(url, "registry.listeners");

        // 创建注册中心的包装器
        return new ListenerRegistryWrapper(registry,Collections.unmodifiableList(listeners));
    }
}
