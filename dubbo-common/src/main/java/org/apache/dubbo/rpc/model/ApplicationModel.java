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
package org.apache.dubbo.rpc.model;

import org.apache.dubbo.common.config.Environment;
import org.apache.dubbo.common.context.FrameworkExt;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.context.ConfigManager;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link ExtensionLoader}, {@code DubboBootstrap} and this class are at present designed to be
 * singleton or static (by itself totally static or uses some static fields). So the instances
 * returned from them are of process scope. If you want to support multiple dubbo servers in one
 * single process, you may need to refactor those three classes.
 * <p>
 * Represent a application which is using Dubbo and store basic metadata info for using
 * during the processing of RPC invoking.
 * <p>
 * ApplicationModel includes many ProviderModel which is about published services
 * and many Consumer Model which is about subscribed services.
 * <p>
 * 应用模型包含许多提供者模型,它们是关于发布服务和许多关于定义服务的消费者模型
 * <p>
 */
public class ApplicationModel {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModel.class);
    public static final String NAME = "application";

    private static AtomicBoolean INIT_FLAG = new AtomicBoolean(false);
    /**
     * 扩展加载器,加载框架扩展
     */
    private static final ExtensionLoader<FrameworkExt> LOADER = ExtensionLoader.getExtensionLoader(FrameworkExt.class);

    /**
     * 初始化应用初始监听器
     */
    public static void init() {
        if (INIT_FLAG.compareAndSet(false, true)) {
            // 获取ApplicationInitListener应用初始化监听器对应的扩展加载器
            ExtensionLoader<ApplicationInitListener> extensionLoader = ExtensionLoader.getExtensionLoader(ApplicationInitListener.class);
            // 获取支持的扩展组件名称
            Set<String> listenerNames = extensionLoader.getSupportedExtensions();
            for (String listenerName : listenerNames) {
                // 获取扩展实例
                ApplicationInitListener extensionInstance = extensionLoader.getExtension(listenerName);
                // 初始化
                extensionInstance.init();
            }
        }
    }

    /**
     * @return 所有的消费者模型
     */
    public static Collection<ConsumerModel> allConsumerModels() {
        ServiceRepository serviceRepository = getServiceRepository();
        return serviceRepository.getReferredServices();
    }

    /**
     * @return 所有的提供者模型
     */
    public static Collection<ProviderModel> allProviderModels() {
        ServiceRepository serviceRepository = getServiceRepository();
        return serviceRepository.getExportedServices();
    }

    public static ProviderModel getProviderModel(String serviceKey) {
        ServiceRepository serviceRepository = getServiceRepository();
        // 查找导出的服务
        return serviceRepository.lookupExportedService(serviceKey);
    }

    /**
     * @param serviceKey 指定的服务键
     * @return 指定服务键对应的消费者模型
     */
    public static ConsumerModel getConsumerModel(String serviceKey) {
        ServiceRepository serviceRepository = getServiceRepository();
        return serviceRepository.lookupReferredService(serviceKey);
    }


    /**
     * 初始化框架扩展???
     */
    public static void initFrameworkExts() {
        // 获取指定类型对应的扩展加载器
        ExtensionLoader<FrameworkExt> extensionLoader = ExtensionLoader.getExtensionLoader(FrameworkExt.class);
        // 获取支持的扩展实例
        Set<FrameworkExt> exts = extensionLoader.getSupportedExtensionInstances();
        // 遍历,并初始化
        for (FrameworkExt ext : exts) {
            ext.initialize();
        }
    }
    // ----------------------------------------------------
    // 以下为框架的扩展组件,均实现了FrameworkdExt接口

    /**
     * 环境实现了FrameworkdExt接口
     *
     * @return 获取环境
     */
    public static Environment getEnvironment() {
        return (Environment) LOADER.getExtension(Environment.NAME);
    }

    /**
     * @return 配置管理器
     */
    public static ConfigManager getConfigManager() {
        return (ConfigManager) LOADER.getExtension(ConfigManager.NAME);
    }

    /**
     * @return 服务仓库
     */
    public static ServiceRepository getServiceRepository() {
        return (ServiceRepository) LOADER.getExtension(ServiceRepository.NAME);
    }
    // ----------------------------------------------------

    /**
     * 配置管理器中包含应用配置
     *
     * @return 应用配置
     * @throws IllegalStateException 应用配置不存在
     */
    public static ApplicationConfig getApplicationConfig() {
        ConfigManager configManager = getConfigManager();
        return configManager.getApplicationOrElseThrow();
    }

    public static String getName() {
        return getApplicationConfig().getName();
    }


    // 原始有个application字段,当前已经删除,直接获取name
    @Deprecated
    public static String getApplication() {
        return getName();
    }

    // only for unit test

    /**
     * 删除3个框架扩展组件
     */
    public static void reset() {
        getServiceRepository().destroy();
        getConfigManager().destroy();
        getEnvironment().destroy();
    }

}
