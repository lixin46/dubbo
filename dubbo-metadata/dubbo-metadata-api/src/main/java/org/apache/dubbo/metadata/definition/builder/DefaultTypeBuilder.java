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
package org.apache.dubbo.metadata.definition.builder;

import org.apache.dubbo.metadata.definition.TypeDefinitionBuilder;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;
import org.apache.dubbo.metadata.definition.util.ClassUtils;
import org.apache.dubbo.metadata.definition.util.JaketConfigurationUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * 2015/1/27.
 */
public final class DefaultTypeBuilder {

    public static TypeDefinition build(Class<?> clazz, Map<Class<?>, TypeDefinition> typeCache) {
        // 全限定类名
        final String name = clazz.getName();

        TypeDefinition typeDefinition = new TypeDefinition(name);
        // Try to get a cached definition
        // 获取缓存返回
        if (typeCache.containsKey(clazz)) {
            return typeCache.get(clazz);
        }

        // Primitive type
        if (!JaketConfigurationUtils.needAnalyzing(clazz)) {
            return typeDefinition;
        }

        // Custom type
        TypeDefinition ref = new TypeDefinition(name);
        ref.set$ref(name);
        typeCache.put(clazz, ref);
        // 获取所有非static且没有transient修饰的字段
        // 遍历
        List<Field> fields = ClassUtils.getNonStaticFields(clazz);
        for (Field field : fields) {
            // 名称
            String fieldName = field.getName();
            // 声明类型
            Class<?> fieldClass = field.getType();
            // 通用类型
            Type fieldType = field.getGenericType();

            // 构建类型定义
            TypeDefinition fieldTd = TypeDefinitionBuilder.build(fieldType, fieldClass, typeCache);
            Map<String, TypeDefinition> properties = typeDefinition.getProperties();
            properties.put(fieldName, fieldTd);
        }

        typeCache.put(clazz, typeDefinition);
        return typeDefinition;
    }

    private DefaultTypeBuilder() {
    }
}
