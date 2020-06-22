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
package org.apache.dubbo.config.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于辅助配置对象的注入以及参数Map的整合行为,标记在getter方法上.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Parameter {

    /**
     * 显示指定的参数名,
     * 不指定默认为方法名单词切分点分隔
     *
     * @return 参数名
     */
    String key() default "";

    /**
     * 如果配置为true,则getter方法返回了null或空串,则会抛异常
     *
     * @return 是否必须
     */
    boolean required() default false;

    /**
     * 是否排除当前参数,不追加到map中
     *
     * @return 是否
     */
    boolean excluded() default false;

    /**
     * 是否对value进行url编码
     *
     * @return 是否
     */
    boolean escaped() default false;

    /**
     * 已废弃
     *
     * @return 废弃
     */
    boolean attribute() default false;

    /**
     * 如果为true,则当key重复时,被追加到value尾部,逗号分隔
     * 如果为false,则采用替换侧乱
     *
     * @return 是否追加
     */
    boolean append() default false;

    /**
     * 当从Configuration对象中查询属性值时,是否把key当做属性名,
     * 如果为true则使用key获取;
     * 如果为false则使用setter方法对应的属性名.
     *
     * @return 是否
     */
    boolean useKeyAsProperty() default true;

}