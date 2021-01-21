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

import org.apache.dubbo.common.BaseServiceMetadata;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.ServiceConfigBase;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ProviderModel is about published services
 */
public class ProviderModel {

    /**
     * 服务实例的唯一标识
     */
    private String serviceKey;
    /**
     * 服务实例
     */
    private final Object serviceInstance;
    /**
     * 服务描述符,有什么用???
     */
    private final ServiceDescriptor serviceModel;
    /**
     * 服务配置
     */
    private final ServiceConfigBase<?> serviceConfig;
    /**
     * 注册状态URL???
     */
    private final List<RegisterStatedURL> urls;

    /**
     * 构造方法
     * @param serviceKey 服务键,服务实例的唯一标识
     * @param serviceInstance 服务实例
     * @param serviceModel 服务描述符???
     * @param serviceConfig 服务配置???
     */
    public ProviderModel(String serviceKey,
                         Object serviceInstance,
                         ServiceDescriptor serviceModel,
                         ServiceConfigBase<?> serviceConfig) {
        if (null == serviceInstance) {
            throw new IllegalArgumentException("Service[" + serviceKey + "]Target is NULL.");
        }

        this.serviceKey = serviceKey;
        this.serviceInstance = serviceInstance;
        this.serviceModel = serviceModel;
        this.serviceConfig = serviceConfig;
        this.urls = new ArrayList<>(1);
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public Class<?> getServiceInterfaceClass() {
        return serviceModel.getServiceInterfaceClass();
    }

    public Object getServiceInstance() {
        return serviceInstance;
    }

    public Set<MethodDescriptor> getAllMethods() {
        return serviceModel.getAllMethods();
    }

    public ServiceDescriptor getServiceModel() {
        return serviceModel;
    }

    public ServiceConfigBase getServiceConfig() {
        return serviceConfig;
    }

    public List<RegisterStatedURL> getStatedUrl() {
        return urls;
    }

    public void addStatedUrl(RegisterStatedURL url) {
        this.urls.add(url);
    }

    public static class RegisterStatedURL {
        /**
         * 注册表地址???
         */
        private volatile URL registryUrl;
        /**
         * 提供者地址???
         */
        private volatile URL providerUrl;
        /**
         * 是否已注册???
         */
        private volatile boolean registered;

        /**
         * 构造方法
         * @param providerUrl 提供者在注册中心的节点内容
         * @param registryUrl 注册中心的信息
         * @param registered 是否已注册
         */
        public RegisterStatedURL(URL providerUrl,
                                 URL registryUrl,
                                 boolean registered) {
            this.providerUrl = providerUrl;
            this.registered = registered;
            this.registryUrl = registryUrl;
        }

        public URL getProviderUrl() {
            return providerUrl;
        }

        public void setProviderUrl(URL providerUrl) {
            this.providerUrl = providerUrl;
        }

        public boolean isRegistered() {
            return registered;
        }

        public void setRegistered(boolean registered) {
            this.registered = registered;
        }

        public URL getRegistryUrl() {
            return registryUrl;
        }

        public void setRegistryUrl(URL registryUrl) {
            this.registryUrl = registryUrl;
        }
    }

    /* *************** Start, metadata compatible **************** */

    /**
     * 服务元数据???
     */
    private ServiceMetadata serviceMetadata;
    /**
     * key为方法名,value为方法模型列表,因为重载所以同步方法不止一个
     */
    private final Map<String, List<ProviderMethodModel>> methods = new HashMap<String, List<ProviderMethodModel>>();

    /**
     * 老的构造方法???
     * @param serviceKey 服务键
     * @param serviceInstance 服务实例
     * @param serviceModel 服务描述符
     * @param serviceConfig 服务配置
     * @param serviceMetadata 服务元数据,老的配置项???
     */
    public ProviderModel(String serviceKey,
                         Object serviceInstance,
                         ServiceDescriptor serviceModel,
                         ServiceConfigBase<?> serviceConfig,
                         ServiceMetadata serviceMetadata) {
        this(serviceKey, serviceInstance, serviceModel, serviceConfig);

        this.serviceMetadata = serviceMetadata;
        Class<?> serviceInterfaceClass = serviceModel.getServiceInterfaceClass();
        // 初始化方法
        initMethod(serviceInterfaceClass);
    }

    private void initMethod(Class<?> serviceInterfaceClass) {
        Method[] methodsToExport;
        // 获取所有public方法
        methodsToExport = serviceInterfaceClass.getMethods();
        // 遍历public方法
        for (Method method : methodsToExport) {
            method.setAccessible(true);
            // 获取名称对应的方法模型列表
            List<ProviderMethodModel> methodModels = methods.get(method.getName());
            // 不存在,则创建后保存
            if (methodModels == null) {
                methodModels = new ArrayList<ProviderMethodModel>();
                methods.put(method.getName(), methodModels);
            }
            // 封装方法模型后追加
            methodModels.add(new ProviderMethodModel(method));
        }
    }


    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
        if (serviceMetadata != null) {
            serviceMetadata.setServiceKey(serviceKey);
            serviceMetadata.setGroup(BaseServiceMetadata.groupFromServiceKey(serviceKey));
        }
    }

    public String getServiceName() {
        return this.serviceMetadata.getServiceKey();
    }

    public List<ProviderMethodModel> getAllMethodModels() {
        List<ProviderMethodModel> result = new ArrayList<ProviderMethodModel>();
        for (List<ProviderMethodModel> models : methods.values()) {
            result.addAll(models);
        }
        return result;
    }

    public ProviderMethodModel getMethodModel(String methodName, String[] argTypes) {
        List<ProviderMethodModel> methodModels = methods.get(methodName);
        if (methodModels != null) {
            for (ProviderMethodModel methodModel : methodModels) {
                if (Arrays.equals(argTypes, methodModel.getMethodArgTypes())) {
                    return methodModel;
                }
            }
        }
        return null;
    }

    public List<ProviderMethodModel> getMethodModelList(String methodName) {
        List<ProviderMethodModel> resultList = methods.get(methodName);
        return resultList == null ? Collections.emptyList() : resultList;
    }



    /**
     * @return serviceMetadata
     */
    public ServiceMetadata getServiceMetadata() {
        return serviceMetadata;
    }
}
