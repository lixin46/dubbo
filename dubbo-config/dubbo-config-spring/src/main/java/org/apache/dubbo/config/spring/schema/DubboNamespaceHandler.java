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

import org.apache.dubbo.common.Version;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.MetricsConfig;
import org.apache.dubbo.config.ModuleConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.SslConfig;
import org.apache.dubbo.config.spring.ConfigCenterBean;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.config.spring.beans.factory.config.ConfigurableSourceBeanMetadataElement;
import org.apache.dubbo.config.spring.context.DubboBootstrapApplicationListener;
import org.apache.dubbo.config.spring.context.DubboLifecycleComponentApplicationListener;

import com.alibaba.spring.util.AnnotatedBeanDefinitionRegistryUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.w3c.dom.Element;

import static com.alibaba.spring.util.AnnotatedBeanDefinitionRegistryUtils.registerBeans;

/**
 * DubboNamespaceHandler
 *
 * @export
 */
public class DubboNamespaceHandler extends NamespaceHandlerSupport implements ConfigurableSourceBeanMetadataElement {

    static {
        Version.checkDuplicate(DubboNamespaceHandler.class);
    }

    /**
     * 应用,注册表和监听器是服务端和客户端共有的配置
     *
     */
    @Override
    public void init() {
        registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));
        registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));

        registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));
        registerBeanDefinitionParser("config-center", new DubboBeanDefinitionParser(ConfigCenterBean.class, true));

        registerBeanDefinitionParser("metadata-report", new DubboBeanDefinitionParser(MetadataReportConfig.class, true));
        // 监听器服务,共享配置
        registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));

        registerBeanDefinitionParser("metrics", new DubboBeanDefinitionParser(MetricsConfig.class, true));
        registerBeanDefinitionParser("ssl", new DubboBeanDefinitionParser(SslConfig.class, true));

        // 服务端配置
        registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));
        // 消费端配置
        registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));
        // 协议,属于服务端
        registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));
        //  服务端配置
        registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));
        // 消费端配置
        registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));
        // bean定义注册表处理器,用于识别dubbo注解
        registerBeanDefinitionParser("annotation", new AnnotationBeanDefinitionParser());
    }

    /**
     * Override {@link NamespaceHandlerSupport#parse(Element, ParserContext)} method
     *
     * @param element       {@link Element}
     * @param parserContext {@link ParserContext}
     * @return
     * @since 2.7.5
     */
    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        BeanDefinitionRegistry registry = parserContext.getRegistry();
        registerAnnotationConfigProcessors(registry);
        registerApplicationListeners(registry);
        BeanDefinition beanDefinition = super.parse(element, parserContext);
        setSource(beanDefinition);
        return beanDefinition;
    }

    /**
     * Register {@link ApplicationListener ApplicationListeners} as a Spring Bean
     *
     * @param registry {@link BeanDefinitionRegistry}
     * @see ApplicationListener
     * @see AnnotatedBeanDefinitionRegistryUtils#registerBeans(BeanDefinitionRegistry, Class[])
     * @since 2.7.5
     */
    private void registerApplicationListeners(BeanDefinitionRegistry registry) {
        registerBeans(registry, DubboLifecycleComponentApplicationListener.class);
        registerBeans(registry, DubboBootstrapApplicationListener.class);
    }

    /**
     * Register the processors for the Spring Annotation-Driven features
     *
     * @param registry {@link BeanDefinitionRegistry}
     * @see AnnotationConfigUtils
     * @since 2.7.5
     */
    private void registerAnnotationConfigProcessors(BeanDefinitionRegistry registry) {
        AnnotationConfigUtils.registerAnnotationConfigProcessors(registry);
    }
}
