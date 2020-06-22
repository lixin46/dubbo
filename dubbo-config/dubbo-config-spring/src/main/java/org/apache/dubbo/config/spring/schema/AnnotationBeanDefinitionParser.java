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
package org.apache.dubbo.config.spring.schema;

import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.apache.dubbo.config.spring.beans.factory.annotation.ServiceAnnotationBeanPostProcessor;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import static org.apache.dubbo.config.spring.util.DubboBeanUtils.registerCommonBeans;
import static org.springframework.util.StringUtils.commaDelimitedListToStringArray;
import static org.springframework.util.StringUtils.trimArrayElements;

/**
 * @link BeanDefinitionParser}
 * @see ServiceAnnotationBeanPostProcessor
 * @see ReferenceAnnotationBeanPostProcessor
 * @since 2.5.9
 */
public class AnnotationBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    /**
     * bean的类型为ServiceAnnotationBeanPostProcessor
     * parse
     * <prev>
     * &lt;dubbo:annotation package="" /&gt;
     * </prev>
     *
     * @param element 当前annotation元素
     * @param parserContext 解析剩下文
     * @param builder bean定义构建器,类型为ServiceClassPostProcessor,用于识别@DubboService注解
     */
    @Override
    protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
        // package属性
        String packageToScan = element.getAttribute("package");
        // 要扫描的包
        String[] packagesToScan = trimArrayElements(commaDelimitedListToStringArray(packageToScan));
        // 构造方法参数值
        builder.addConstructorArgValue(packagesToScan);
        //
        builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

        // @since 2.7.6 Register the common beans
        // 注册公共bean定义???
        registerCommonBeans(parserContext.getRegistry());
    }

    @Override
    protected boolean shouldGenerateIdAsFallback() {
        return true;
    }

    @Override
    protected Class<?> getBeanClass(Element element) {
        // bean类型
        return ServiceAnnotationBeanPostProcessor.class;
    }

}
