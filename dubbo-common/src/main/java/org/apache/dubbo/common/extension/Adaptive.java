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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * 为ExtensionLoader扩展加载器提供有帮助的信息,用来给扩展实例注入依赖
 * 标记在SPI扩展接口上,也可以标记在接口方法上,具有不同的作用.
 * 如果标记在类上,则说明该类为适配器类的实现,框架将不再自动生成适配器类.
 * 如果标记在方法上,则说明该方法需要适配,框架生成的适配器会动态识别入参进行映射.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Adaptive {

    /**
     * 要使用的扩展实例的名称值,在url中对应的参数名.
     * 适配器类会从URL对象中,根据名称获取extName扩展实例名称(通过getParameter()或getMethodParameter()),
     * 之后通过扩展加载器获取名称对应的的扩展实例,并返回.
     *
     * @return 扩展实例名称在url中的参数名
     */
    String[] value() default {};

}