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
package org.apache.dubbo.registry.integration;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigChangeType;
import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.cluster.Configurator;
import org.apache.dubbo.rpc.cluster.configurator.parser.ConfigParser;
import org.apache.dubbo.rpc.cluster.governance.GovernanceRuleRepository;

import java.util.Collections;
import java.util.List;


/**
 * 抽象的配置器监听器
 */
public abstract class AbstractConfiguratorListener implements ConfigurationListener {
    private static final Logger logger = LoggerFactory.getLogger(AbstractConfiguratorListener.class);

    /**
     * 解析得到的配置器
     */
    protected List<Configurator> configurators = Collections.emptyList();
    /**
     * 规则治理仓库默认扩展实例,负责操作DynamicConfiguration动态配置
     */
    protected GovernanceRuleRepository ruleRepository = ExtensionLoader.getExtensionLoader(GovernanceRuleRepository.class).getDefaultExtension();


    /**
     * 配置变化回调
     * @param event 配置变更事件
     */
    @Override
    public void process(ConfigChangedEvent event) {
        if (logger.isInfoEnabled()) {
            logger.info("Notification of overriding rule, change type is: " + event.getChangeType() +
                    ", raw config content is:\n " + event.getContent());
        }

        // key被删除,则清空配置器
        if (event.getChangeType().equals(ConfigChangeType.DELETED)) {
            configurators.clear();
        }
        // 新增或修改
        else {
            // 生成配置器失败,则返回
            String value = event.getContent();
            // 从原始规则解析生成配置器失败,则返回
            if (!genConfiguratorsFromRawRule(value)) {
                return;
            }
        }
        // 通知配置器列表被重写
        notifyOverrides();
    }

    /**
     * 通知配置器列表被重写
     */
    protected abstract void notifyOverrides();


    /**
     * 监听指定key的变化,同时进行规则的首次获取
     * @param key 应用名.configurators
     */
    protected final void initWith(String key) {
        // 调用规则仓库,添加监听器,监听指定的key
        // 内部向动态配置追加配置监听器
        ruleRepository.addListener(key, this);
        // 追加完监听器之后,需要进行配置的首次获取
        String rawConfig = ruleRepository.getRule(key, DynamicConfiguration.DEFAULT_GROUP);
        // 非空
        if (!StringUtils.isEmpty(rawConfig)) {
            // 根据原始规则,生成配置器
            genConfiguratorsFromRawRule(rawConfig);
        }
    }

    private boolean genConfiguratorsFromRawRule(String rawConfig) {
        boolean parseSuccess = true;
        try {
            // parseConfigurators will recognize app/service config automatically.
            List<URL> urls = ConfigParser.parseConfigurators(rawConfig);
            configurators = Configurator.toConfigurators(urls).orElse(configurators);
        } catch (Exception e) {
            logger.error("Failed to parse raw dynamic config and it will not take effect, the raw config is: " +
                    rawConfig, e);
            parseSuccess = false;
        }
        return parseSuccess;
    }


    protected final void stopListen(String key) {
        ruleRepository.removeListener(key, this);
    }



    public List<Configurator> getConfigurators() {
        return configurators;
    }

    public void setConfigurators(List<Configurator> configurators) {
        this.configurators = configurators;
    }
}
