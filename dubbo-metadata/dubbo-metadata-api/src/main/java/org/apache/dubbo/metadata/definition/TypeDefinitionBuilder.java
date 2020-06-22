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

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.metadata.definition.builder.DefaultTypeBuilder;
import org.apache.dubbo.metadata.definition.builder.TypeBuilder;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.dubbo.common.utils.ClassUtils.isSimpleType;

/**
 * 2015/1/27.
 */
public class TypeDefinitionBuilder {
    private static final Logger logger = LoggerFactory.getLogger(TypeDefinitionBuilder.class);
    private static final List<TypeBuilder> BUILDERS;

    static {
        List<TypeBuilder> builders = new ArrayList<>();
        ExtensionLoader<TypeBuilder> extensionLoader = ExtensionLoader.getExtensionLoader(TypeBuilder.class);
        for (String extensionName : extensionLoader.getSupportedExtensions()) {
            builders.add(extensionLoader.getExtension(extensionName));
        }
        BUILDERS = builders;
    }

    /**
     *
     * @param type 通用类型
     * @param clazz 类
     * @param typeCache 类型定义缓存
     * @return
     */
    public static TypeDefinition build(Type type, Class<?> clazz, Map<Class<?>, TypeDefinition> typeCache) {
        //
        TypeBuilder builder = getGenericTypeBuilder(type, clazz);
        TypeDefinition td;
        // 存在,则调用构建IQ构建
        if (builder != null) {
            td = builder.build(type, clazz, typeCache);
            td.setTypeBuilderName(builder.getClass().getName());
        }
        // 不存在,则使用默认的构建器构建
        else {
            td = DefaultTypeBuilder.build(clazz, typeCache);
            td.setTypeBuilderName(DefaultTypeBuilder.class.getName());
        }
        if (isSimpleType(clazz)) { // changed since 2.7.6
            td.setProperties(null);
        }
        return td;
    }

    private static TypeBuilder getGenericTypeBuilder(Type type, Class<?> clazz) {
        // 遍历构建器
        for (TypeBuilder builder : BUILDERS) {
            try {
                // 接受则返回
                if (builder.accept(type, clazz)) {
                    return builder;
                }
            } catch (NoClassDefFoundError cnfe) {
                //ignore
                logger.info("Throw classNotFound (" + cnfe.getMessage() + ") in " + builder.getClass());
            }
        }
        return null;
    }

    private Map<Class<?>, TypeDefinition> typeCache = new HashMap<>();

    /**
     * 例如:如果形参是List<String>,则type为ParameterizedType,而具体类为List.class
     * @param type 指定的通用类型,可能是Class,也可能是参数化类型,通配符类型,类型变量等
     * @param clazz 指定的具体类
     * @return 构建的类型定义
     */
    public TypeDefinition build(Type type, Class<?> clazz) {
        return build(type, clazz, typeCache);
    }

    public List<TypeDefinition> getTypeDefinitions() {
        return new ArrayList<>(typeCache.values());
    }

}
