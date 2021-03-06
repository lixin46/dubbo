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

import org.apache.dubbo.common.utils.CollectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ServiceModel and ServiceMetadata are to some extend duplicated with each other.
 * We should merge them in the future.
 */
public class ServiceDescriptor {
    /**
     * 服务名称,默认为服务接口名称
     */
    private final String serviceName;
    /**
     * 服务接口类
     */
    private final Class<?> serviceInterfaceClass;
    // to accelerate search
    // 为了加速搜索
    /**
     * 方法描述符映射
     * key为方法名,value为同名方法列表
     */
    private final Map<String, List<MethodDescriptor>> methods = new HashMap<>();
    /**
     * 一级key为方法名,二级key为参数描述,value为方法描述符
     */
    private final Map<String, Map<String, MethodDescriptor>> descToMethods = new HashMap<>();

    /**
     * 唯一构造方法
     * @param interfaceClass 接口类
     */
    public ServiceDescriptor(Class<?> interfaceClass) {
        this.serviceInterfaceClass = interfaceClass;
        this.serviceName = interfaceClass.getName();
        initMethods();
    }

    private void initMethods() {
        // 获取public方法
        Method[] methodsToExport = this.serviceInterfaceClass.getMethods();
        // 遍历
        for (Method method : methodsToExport) {
            method.setAccessible(true);
            // 获取方法名称对应的方法描述符列表
            List<MethodDescriptor> methodModels = methods.computeIfAbsent(method.getName(), (k) -> new ArrayList<>(1));
            // 封装方法描述符,追加到列表
            methodModels.add(new MethodDescriptor(method));
        }

        methods.forEach((methodName, methodList) -> {
            // 获取方法名对应的Map,不存在则创建保存
            Map<String, MethodDescriptor> descMap = descToMethods.computeIfAbsent(methodName, k -> new HashMap<>());
            // 保存
            methodList.forEach(methodModel -> descMap.put(methodModel.getParamDesc(), methodModel));
        });
    }

    public String getServiceName() {
        return serviceName;
    }

    public Class<?> getServiceInterfaceClass() {
        return serviceInterfaceClass;
    }

    public Set<MethodDescriptor> getAllMethods() {
        Set<MethodDescriptor> methodModels = new HashSet<>();
        methods.forEach((k, v) -> methodModels.addAll(v));
        return methodModels;
    }

    /**
     * Does not use Optional as return type to avoid potential performance decrease.
     *
     * @param methodName
     * @param params
     * @return
     */
    public MethodDescriptor getMethod(String methodName, String params) {
        Map<String, MethodDescriptor> methods = descToMethods.get(methodName);
        if (CollectionUtils.isNotEmptyMap(methods)) {
            return methods.get(params);
        }
        return null;
    }

    /**
     * Does not use Optional as return type to avoid potential performance decrease.
     *
     * @param methodName
     * @param paramTypes
     * @return
     */
    public MethodDescriptor getMethod(String methodName, Class<?>[] paramTypes) {
        List<MethodDescriptor> methodModels = methods.get(methodName);
        if (CollectionUtils.isNotEmpty(methodModels)) {
            for (int i = 0; i < methodModels.size(); i++) {
                MethodDescriptor descriptor = methodModels.get(i);
                if (Arrays.equals(paramTypes, descriptor.getParameterClasses())) {
                    return descriptor;
                }
            }
        }
        return null;
    }

    public List<MethodDescriptor> getMethods(String methodName) {
        return methods.get(methodName);
    }

}
