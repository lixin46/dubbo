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
package org.apache.dubbo.rpc.cluster.router;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.cluster.Router;
import org.apache.dubbo.rpc.cluster.governance.GovernanceRuleRepository;

/**
 * 路由器抽象实现
 *
 * 子类只需要实现route()方法,或者按需重写
 */
public abstract class AbstractRouter implements Router {
    /**
     * 优先级,默认最低
     */
    protected int priority = DEFAULT_PRIORITY;
    /**
     * 是否强制
     */
    protected boolean force = false;
    /**
     * 路由器的url
     */
    protected URL url;

    /**
     * 规则治理仓库(内部依赖配置中心)
     *
     */
    protected GovernanceRuleRepository ruleRepository;

    /**
     * 构造方法
     * @param url 信息
     */
    public AbstractRouter(URL url) {
        this.ruleRepository = ExtensionLoader.getExtensionLoader(GovernanceRuleRepository.class).getDefaultExtension();
        this.url = url;
    }

    /**
     * 构造方法
     */
    public AbstractRouter() {
    }

    @Override
    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    @Override
    public boolean isRuntime() {
        return true;
    }

    @Override
    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

}
