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
package org.apache.dubbo.config.spring.context;


import org.apache.dubbo.common.context.Lifecycle;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

/**
 * A {@link ApplicationListener listener} for the {@link Lifecycle Dubbo Lifecycle} components
 * 生命周期组件应用监听器
 * 处理实现了LifeCycle接口的bean实例
 *
 * @see {@link Lifecycle Dubbo Lifecycle}
 * @see SmartApplicationListener
 * @since 2.7.5
 */
public class DubboLifecycleComponentApplicationListener extends OneTimeExecutionApplicationContextEventListener {

    /**
     * The bean name of {@link DubboLifecycleComponentApplicationListener}
     *
     * @since 2.7.6
     */
    public static final String BEAN_NAME = "dubboLifecycleComponentApplicationListener";

    /**
     * 生命周期组件,从spring上下文中获取
     */
    private List<Lifecycle> lifecycleComponents = emptyList();

    @Override
    protected void onApplicationContextEvent(ApplicationContextEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            onContextRefreshedEvent((ContextRefreshedEvent) event);
        } else if (event instanceof ContextClosedEvent) {
            onContextClosedEvent((ContextClosedEvent) event);
        }
    }

    /**
     *
     * @param event 上下文刷新事件
     */
    protected void onContextRefreshedEvent(ContextRefreshedEvent event) {
        // 从spring上下文中获取所有Lifecycle接口类型的bean,保存到当前list中
        initLifecycleComponents(event);
        // 遍历生命周期组件,调用start启动
        startLifecycleComponents();
    }

    /**
     *
     * @param event 上下文关闭事件
     */
    protected void onContextClosedEvent(ContextClosedEvent event) {
        destroyLifecycleComponents();
    }

    private void initLifecycleComponents(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        ClassLoader classLoader = context.getClassLoader();
        lifecycleComponents = new LinkedList<>();
        // load the Beans of Lifecycle from ApplicationContext
        // 加载声明周期组件
        loadLifecycleComponents(lifecycleComponents, context);
    }

    private void loadLifecycleComponents(List<Lifecycle> lifecycleComponents, ApplicationContext context) {
        // 从上下文中获取对应类型的bean实例
        Map<String, Lifecycle> lifecycleBeans = beansOfTypeIncludingAncestors(context, Lifecycle.class);
        lifecycleComponents.addAll(lifecycleBeans.values());
    }

    private void startLifecycleComponents() {
        lifecycleComponents.forEach(Lifecycle::start);
    }

    private void destroyLifecycleComponents() {
        lifecycleComponents.forEach(Lifecycle::destroy);
    }
}
