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
package org.apache.dubbo.rpc.cluster.directory;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.RouterChain;

import java.util.Collections;
import java.util.List;


/**
 * 静态目录
 * @param <T> 服务接口
 */
public class StaticDirectory<T> extends AbstractDirectory<T> {
    private static final Logger logger = LoggerFactory.getLogger(StaticDirectory.class);

    /**
     * 调用器列表
     */
    private final List<Invoker<T>> invokers;

    /**
     * 构造方法
     * @param invokers 调用器列表
     */
    public StaticDirectory(List<Invoker<T>> invokers) {
        this(null, invokers, null);
    }

    /**
     * 构造方法
     * @param invokers 调用器列表
     * @param routerChain 路由器链
     */
    public StaticDirectory(List<Invoker<T>> invokers, RouterChain<T> routerChain) {
        this(null, invokers, routerChain);
    }

    /**
     * 构造方法
     * @param url 信息???
     * @param invokers 调用器列表
     */
    public StaticDirectory(URL url, List<Invoker<T>> invokers) {
        this(url, invokers, null);
    }

    /**
     * 核心构造方法
     * @param url 信息
     * @param invokers 调用器列表
     * @param routerChain 路由器链
     */
    public StaticDirectory(URL url, List<Invoker<T>> invokers, RouterChain<T> routerChain) {
        super(url == null && CollectionUtils.isNotEmpty(invokers) ? invokers.get(0).getUrl() : url, routerChain);
        if (CollectionUtils.isEmpty(invokers)) {
            throw new IllegalArgumentException("invokers == null");
        }
        this.invokers = invokers;
    }
    // -----------------------------------------------------------------------------------------------------------------
    // Directory接口实现

    @Override
    public Class<T> getInterface() {
        return invokers.get(0).getInterface();
    }

    @Override
    public List<Invoker<T>> getAllInvokers() {
        return invokers;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // AbstractDirectory实现
    @Override
    protected List<Invoker<T>> doList(Invocation invocation) throws RpcException {
        List<Invoker<T>> finalInvokers = invokers;
        // 存在路由链
        if (routerChain != null) {
            try {
                URL consumerUrl = getConsumerUrl();
                // 路由,获取最终的调用器
                finalInvokers = routerChain.route(consumerUrl, invocation);
            } catch (Throwable t) {
                logger.error("Failed to execute router: " + getUrl() + ", cause: " + t.getMessage(), t);
            }
        }
        return finalInvokers == null ? Collections.emptyList() : finalInvokers;
    }
    // -----------------------------------------------------------------------------------------------------------------
    // Node接口实现
    @Override
    public boolean isAvailable() {
        if (isDestroyed()) {
            return false;
        }
        for (Invoker<T> invoker : invokers) {
            if (invoker.isAvailable()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        if (isDestroyed()) {
            return;
        }
        super.destroy();
        for (Invoker<T> invoker : invokers) {
            invoker.destroy();
        }
        invokers.clear();
    }

    // -----------------------------------------------------------------------------------------------------------------

    public void buildRouterChain() {
        // 根据url,构建路由器链
        RouterChain<T> routerChain = RouterChain.buildChain(getUrl());
        // 路由器链引用调用器
        routerChain.setInvokers(invokers);
        // 保存路由器链
        this.setRouterChain(routerChain);
    }



}
