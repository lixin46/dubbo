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
package org.apache.dubbo.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ServiceMetadata;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static org.apache.dubbo.common.constants.CommonConstants.DUBBO;

/**
 * ReferenceConfig
 * 引用配置
 *
 *
 * @export
 */
public abstract class ReferenceConfigBase<T> extends AbstractReferenceConfig {

    private static final long serialVersionUID = -5864351140409987595L;

    /**
     * 服务接口名,通过<dubbo:reference interface="">配置
     */
    protected String interfaceName;

    /**
     * 服务接口类,setter注入,<dubbo:reference interfaceClass="">配置
     */
    protected Class<?> interfaceClass;

    /**
     * client type
     * 客户端类型???
     */
    protected String client;

    /**
     * xml配置,用于直连访问,可选
     */
    protected String url;

    /**
     * 消费者配置
     * <dubbo:consumer>下配置<dubbo:reference>子元素时,会有此对象
     */
    protected ConsumerConfig consumer;

    /**
     * 协议
     * 只有特定的服务提供者协议被调用,其他协议会被忽略
     * 注意与RegistryConfig的protocol的区别
     * 当前是与服务端通信的协议,
     * 而RegistryConfig配置的是与注册中心通信的协议
     */
    protected String protocol;

    /**
     * 服务元数据,用于服务发现注册表的
     */
    protected ServiceMetadata serviceMetadata;

    /**
     * 构造方法
     */
    public ReferenceConfigBase() {
        serviceMetadata = new ServiceMetadata();
        serviceMetadata.addAttribute("ORIGIN_CONFIG", this);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可导出getter
    public String getInterface() {
        return interfaceName;
    }
    public String getClient() {
        return client;
    }
    public String getProtocol() {
        return protocol;
    }
    // -----------------------------------------------------------------------------------------------------------------
    // 可注入setter
    /**
     * @param interfaceClass
     * @see #setInterface(Class)
     * @deprecated
     */
    @Deprecated
    public void setInterfaceClass(Class<?> interfaceClass) {
        setInterface(interfaceClass);
    }

    public void setInterface(String interfaceName) {
        this.interfaceName = interfaceName;
        if (StringUtils.isEmpty(id)) {
            id = interfaceName;
        }
    }
    public void setClient(String client) {
        this.client = client;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public void setConsumer(ConsumerConfig consumer) {
        this.consumer = consumer;
    }
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    // -----------------------------------------------------------------------------------------------------------------
    // 普通
    public boolean shouldCheck() {
        Boolean shouldCheck = isCheck();
        if (shouldCheck == null && getConsumer() != null) {
            shouldCheck = getConsumer().isCheck();
        }
        if (shouldCheck == null) {
            // default true
            shouldCheck = true;
        }
        return shouldCheck;
    }

    public boolean shouldInit() {
        Boolean shouldInit = isInit();
        if (shouldInit == null && getConsumer() != null) {
            shouldInit = getConsumer().isInit();
        }
        if (shouldInit == null) {
            // default is true, spring will still init lazily by setting init's default value to false,
            // the def default setting happens in {@link ReferenceBean#afterPropertiesSet}.
            return true;
        }
        return shouldInit;
    }

    public void checkDefault() throws IllegalStateException {
        if (consumer == null) {
            consumer = ApplicationModel.getConfigManager()
                    .getDefaultConsumer()
                    .orElse(new ConsumerConfig());
        }
    }

    public Class<?> getActualInterface() {
        Class actualInterface = interfaceClass;
        if (interfaceClass == GenericService.class) {
            try {
                actualInterface = Class.forName(interfaceName);
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        return actualInterface;
    }
    public Class<?> getInterfaceClass() {
        if (interfaceClass != null) {
            return interfaceClass;
        }
        if (ProtocolUtils.isGeneric(getGeneric())
                || (getConsumer() != null && ProtocolUtils.isGeneric(getConsumer().getGeneric()))) {
            return GenericService.class;
        }
        try {
            if (interfaceName != null && interfaceName.length() > 0) {
                interfaceClass = Class.forName(interfaceName, true, ClassUtils.getClassLoader());
            }
        } catch (ClassNotFoundException t) {
            throw new IllegalStateException(t.getMessage(), t);
        }

        return interfaceClass;
    }
    public void setInterface(Class<?> interfaceClass) {
        if (interfaceClass != null && !interfaceClass.isInterface()) {
            throw new IllegalStateException("The interface class " + interfaceClass + " is not a interface!");
        }
        this.interfaceClass = interfaceClass;
        setInterface(interfaceClass == null ? null : interfaceClass.getName());
    }
    @Parameter(excluded = true)
    public String getUrl() {
        return url;
    }
    public ConsumerConfig getConsumer() {
        return consumer;
    }
    public ServiceMetadata getServiceMetadata() {
        return serviceMetadata;
    }
    @Override
    @Parameter(excluded = true)
    public String getPrefix() {
        return DUBBO + ".reference." + interfaceName;
    }
    public void resolveFile() {
        String resolve = System.getProperty(interfaceName);
        String resolveFile = null;
        if (StringUtils.isEmpty(resolve)) {
            resolveFile = System.getProperty("dubbo.resolve.file");
            if (StringUtils.isEmpty(resolveFile)) {
                File userResolveFile = new File(new File(System.getProperty("user.home")), "dubbo-resolve.properties");
                if (userResolveFile.exists()) {
                    resolveFile = userResolveFile.getAbsolutePath();
                }
            }
            if (resolveFile != null && resolveFile.length() > 0) {
                Properties properties = new Properties();
                try (FileInputStream fis = new FileInputStream(new File(resolveFile))) {
                    properties.load(fis);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to load " + resolveFile + ", cause: " + e.getMessage(), e);
                }

                resolve = properties.getProperty(interfaceName);
            }
        }
        if (resolve != null && resolve.length() > 0) {
            url = resolve;
            if (logger.isWarnEnabled()) {
                if (resolveFile != null) {
                    logger.warn("Using default dubbo resolve file " + resolveFile + " replace " + interfaceName + "" + resolve + " to p2p invoke remote service.");
                } else {
                    logger.warn("Using -D" + interfaceName + "=" + resolve + " to p2p invoke remote service.");
                }
            }
        }
    }

    @Override
    protected void computeValidRegistryIds() {
        super.computeValidRegistryIds();
        if (StringUtils.isEmpty(getRegistryIds())) {
            if (getConsumer() != null && StringUtils.isNotEmpty(getConsumer().getRegistryIds())) {
                setRegistryIds(getConsumer().getRegistryIds());
            }
        }
    }
    @Parameter(excluded = true)
    public String getUniqueServiceName() {
        String group = StringUtils.isEmpty(this.group) ? consumer.getGroup() : this.group;
        String version = StringUtils.isEmpty(this.version) ? consumer.getVersion() : this.version;
        return URL.buildKey(interfaceName, group, version);
    }

    public abstract T get();

    public abstract void destroy();
    // -----------------------------------------------------------------------------------------------------------------














}
