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
package org.apache.dubbo.metadata.definition;

import org.apache.dubbo.metadata.definition.model.FullServiceDefinition;
import org.apache.dubbo.metadata.definition.model.MethodDefinition;
import org.apache.dubbo.metadata.definition.model.ServiceDefinition;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;
import org.apache.dubbo.metadata.definition.util.ClassUtils;

import com.google.gson.Gson;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * 2015/1/27.
 */
public final class ServiceDefinitionBuilder {

    /**
     * Describe a Java interface in {@link ServiceDefinition}.
     *
     * @return Service description
     */
    public static ServiceDefinition build(final Class<?> interfaceClass) {
        ServiceDefinition serviceDefinition = new ServiceDefinition();
        build(serviceDefinition, interfaceClass);
        return serviceDefinition;
    }

    public static FullServiceDefinition buildFullDefinition(final Class<?> interfaceClass) {
        FullServiceDefinition sd = new FullServiceDefinition();
        build(sd, interfaceClass);
        return sd;
    }

    public static FullServiceDefinition buildFullDefinition(final Class<?> interfaceClass, Map<String, String> params) {
        FullServiceDefinition sd = new FullServiceDefinition();
        build(sd, interfaceClass);
        sd.setParameters(params);
        return sd;
    }

    public static <T extends ServiceDefinition> void build(T serviceDefinition, final Class<?> interfaceClass) {
        // 接口全限定类名
        serviceDefinition.setCanonicalName(interfaceClass.getCanonicalName());
        // 类的来源位置
        serviceDefinition.setCodeSource(ClassUtils.getCodeSource(interfaceClass));

        TypeDefinitionBuilder builder = new TypeDefinitionBuilder();
        // 获取所有public非static方法
        List<Method> methods = ClassUtils.getPublicNonStaticMethods(interfaceClass);
        // 遍历
        for (Method method : methods) {
            MethodDefinition methodDefinition = new MethodDefinition();
            //
            methodDefinition.setName(method.getName());

            // Process parameter types.
            // 形参声明类
            Class<?>[] paramTypes = method.getParameterTypes();
            // 参数泛型
            Type[] genericParamTypes = method.getGenericParameterTypes();

            String[] parameterTypes = new String[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                // 构建类型定义
                TypeDefinition td = builder.build(genericParamTypes[i], paramTypes[i]);
                parameterTypes[i] = td.getType();
            }
            methodDefinition.setParameterTypes(parameterTypes);

            // Process return type.
            TypeDefinition td = builder.build(method.getGenericReturnType(), method.getReturnType());
            methodDefinition.setReturnType(td.getType());

            serviceDefinition.getMethods().add(methodDefinition);
        }

        serviceDefinition.setTypes(builder.getTypeDefinitions());
    }

    /**
     * Describe a Java interface in Json schema.
     *
     * @return Service description
     */
    public static String schema(final Class<?> clazz) {
        ServiceDefinition sd = build(clazz);
        Gson gson = new Gson();
        return gson.toJson(sd);
    }

    private ServiceDefinitionBuilder() {
    }
}


