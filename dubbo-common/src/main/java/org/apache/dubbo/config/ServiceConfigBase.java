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
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ServiceMetadata;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO;

/**
 * ServiceConfig
 * <p>
 * 服务配置的基类
 *
 * @export
 */
public abstract class ServiceConfigBase<T> extends AbstractServiceConfig {

    private static final long serialVersionUID = 3033787999037024738L;

    // -----------------------------------------------------------------------------------------------------------------
    // static

    @Deprecated
    private static List<ProtocolConfig> convertProviderToProtocol(List<ProviderConfig> providers) {
        if (CollectionUtils.isEmpty(providers)) {
            return null;
        }
        List<ProtocolConfig> protocols = new ArrayList<ProtocolConfig>(providers.size());
        for (ProviderConfig provider : providers) {
            protocols.add(convertProviderToProtocol(provider));
        }
        return protocols;
    }

    @Deprecated
    private static List<ProviderConfig> convertProtocolToProvider(List<ProtocolConfig> protocols) {
        if (CollectionUtils.isEmpty(protocols)) {
            return null;
        }
        List<ProviderConfig> providers = new ArrayList<ProviderConfig>(protocols.size());
        for (ProtocolConfig provider : protocols) {
            providers.add(convertProtocolToProvider(provider));
        }
        return providers;
    }

    @Deprecated
    private static ProtocolConfig convertProviderToProtocol(ProviderConfig provider) {
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName(provider.getProtocol().getName());
        protocol.setServer(provider.getServer());
        protocol.setClient(provider.getClient());
        protocol.setCodec(provider.getCodec());
        protocol.setHost(provider.getHost());
        protocol.setPort(provider.getPort());
        protocol.setPath(provider.getPath());
        protocol.setPayload(provider.getPayload());
        protocol.setThreads(provider.getThreads());
        protocol.setParameters(provider.getParameters());
        return protocol;
    }

    @Deprecated
    private static ProviderConfig convertProtocolToProvider(ProtocolConfig protocol) {
        ProviderConfig provider = new ProviderConfig();
        provider.setProtocol(protocol);
        provider.setServer(protocol.getServer());
        provider.setClient(protocol.getClient());
        provider.setCodec(protocol.getCodec());
        provider.setHost(protocol.getHost());
        provider.setPort(protocol.getPort());
        provider.setPath(protocol.getPath());
        provider.setPayload(protocol.getPayload());
        provider.setThreads(protocol.getThreads());
        provider.setParameters(protocol.getParameters());
        return provider;
    }
    // -----------------------------------------------------------------------------------------------------------------
    /**
     * The provider configuration
     * 提供者配置
     */
    protected ProviderConfig provider;
    /**
     * The providerIds
     */
    protected String providerIds;
    /**
     * The interface name of the exported service
     * 接口名称
     */
    protected String interfaceName;
    /**
     * The interface class of the exported service
     * 接口类
     */
    protected Class<?> interfaceClass;

    /**
     * The reference of the interface implementation
     * 接口实现类,对象的引用
     */
    protected T ref;
    /**
     * The service name
     * 服务名称,或叫路径
     */
    protected String path;

    /**
     * whether it is a GenericService
     */
    protected volatile String generic;
    /**
     * 服务元数据???
     */
    protected ServiceMetadata serviceMetadata;

    /**
     * 构造方法
     */
    public ServiceConfigBase() {
        serviceMetadata = new ServiceMetadata();
        serviceMetadata.addAttribute("ORIGIN_CONFIG", this);
    }

//    /**
//     * 构造方法
//     *
//     * @param service
//     */
//    public ServiceConfigBase(Service service) {
//        serviceMetadata = new ServiceMetadata();
//        serviceMetadata.addAttribute("ORIGIN_CONFIG", this);
//        appendAnnotation(Service.class, service);
//        setMethods(MethodConfig.constructMethodConfig(service.methods()));
//    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可导出getter
    public String getInterface() {
        return interfaceName;
    }

    public String getGeneric() {
        return generic;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可自动注入setter
    public void setRef(T ref) {
        this.ref = ref;
    }

    /**
     * @param interfaceClass
     * @see #setInterface(Class)
     * @deprecated
     */
    public void setInterfaceClass(Class<?> interfaceClass) {
        setInterface(interfaceClass);
    }

    public void setInterface(String interfaceName) {
        this.interfaceName = interfaceName;
        if (StringUtils.isEmpty(id)) {
            id = interfaceName;
        }
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setProvider(ProviderConfig provider) {
        ApplicationModel.getConfigManager().addProvider(provider);
        this.provider = provider;
    }

    public void setProviderIds(String providerIds) {
        this.providerIds = providerIds;
    }

    public void setGeneric(String generic) {
        if (StringUtils.isEmpty(generic)) {
            return;
        }
        if (ProtocolUtils.isValidGenericValue(generic)) {
            this.generic = generic;
        } else {
            throw new IllegalArgumentException("Unsupported generic type " + generic);
        }
    }

    /**
     * @deprecated Replace to setProtocols()
     */
    @Deprecated
    public void setProviders(List<ProviderConfig> providers) {
        this.protocols = convertProviderToProtocol(providers);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 普通
    // -----------------------------------------------------------------------------------------------------------------
    public T getRef() {
        return ref;
    }

    public Class<?> getInterfaceClass() {
        if (interfaceClass != null) {
            return interfaceClass;
        }
        if (ref instanceof GenericService) {
            return GenericService.class;
        }
        try {
            if (interfaceName != null && interfaceName.length() > 0) {
                this.interfaceClass = Class.forName(interfaceName, true, Thread.currentThread()
                        .getContextClassLoader());
            }
        } catch (ClassNotFoundException t) {
            throw new IllegalStateException(t.getMessage(), t);
        }
        return interfaceClass;
    }


    public boolean shouldExport() {
        Boolean export = getExport();
        // default value is true
        return export == null ? true : export;
    }

    @Override
    public Boolean getExport() {
        return (export == null && provider != null) ? provider.getExport() : export;
    }

    public boolean shouldDelay() {
        Integer delay = getDelay();
        return delay != null && delay > 0;
    }

    @Override
    public Integer getDelay() {
        return (delay == null && provider != null) ? provider.getDelay() : delay;
    }

    /**
     * 检查是否存在真实实现类对象的引用
     * 1.不存在则报错
     * 2.类型不匹配则报错
     */
    public void checkRef() {
        // reference should not be null, and is the implementation of the given interface
        if (ref == null) {
            throw new IllegalStateException("ref not allow null!");
        }
        if (!interfaceClass.isInstance(ref)) {
            throw new IllegalStateException("The class "
                    + ref.getClass().getName() + " unimplemented interface "
                    + interfaceClass + "!");
        }
    }

    public Optional<String> getContextPath(ProtocolConfig protocolConfig) {
        String contextPath = protocolConfig.getContextpath();
        if (StringUtils.isEmpty(contextPath) && provider != null) {
            contextPath = provider.getContextpath();
        }
        return Optional.ofNullable(contextPath);
    }

    protected Class getServiceClass(T ref) {
        return ref.getClass();
    }

    public void checkDefault() throws IllegalStateException {
        if (provider == null) {
            provider = ApplicationModel.getConfigManager()
                    .getDefaultProvider()
                    .orElse(new ProviderConfig());
        }
    }

    public void checkProtocol() {
        // 协议配置列表为空,且provider非null
        // 则设置
        if (CollectionUtils.isEmpty(protocols) && provider != null) {
            setProtocols(provider.getProtocols());
        }
        // 根据id获取协议配置对象
        convertProtocolIdsToProtocols();
    }

    public void completeCompoundConfigs() {
        //
        super.completeCompoundConfigs(provider);
        // 存在提供者,则填充未设置的属性
        if (provider != null) {
            // 当前协议配置不存在,则设置
            if (protocols == null) {
                setProtocols(provider.getProtocols());
            }
            // 配置中心不存在,则设置
            if (configCenter == null) {
                setConfigCenter(provider.getConfigCenter());
            }
            // 注册中心id为空,则取provider
            if (StringUtils.isEmpty(registryIds)) {
                setRegistryIds(provider.getRegistryIds());
            }
            // 协议id为空,则获取provider
            if (StringUtils.isEmpty(protocolIds)) {
                setProtocolIds(provider.getProtocolIds());
            }
        }
    }

    private void convertProtocolIdsToProtocols() {
        computeValidProtocolIds();
        if (StringUtils.isEmpty(protocolIds)) {
            if (CollectionUtils.isEmpty(protocols)) {
                List<ProtocolConfig> protocolConfigs = ApplicationModel.getConfigManager().getDefaultProtocols();
                if (protocolConfigs.isEmpty()) {
                    protocolConfigs = new ArrayList<>(1);
                    ProtocolConfig protocolConfig = new ProtocolConfig();
                    protocolConfig.setDefault(true);
                    protocolConfig.refresh();
                    protocolConfigs.add(protocolConfig);
                    ApplicationModel.getConfigManager().addProtocol(protocolConfig);
                }
                setProtocols(protocolConfigs);
            }
        } else {
            String[] arr = COMMA_SPLIT_PATTERN.split(protocolIds);
            List<ProtocolConfig> tmpProtocols = new ArrayList<>();
            Arrays.stream(arr).forEach(id -> {
                if (tmpProtocols.stream().noneMatch(prot -> prot.getId().equals(id))) {
                    Optional<ProtocolConfig> globalProtocol = ApplicationModel.getConfigManager().getProtocol(id);
                    if (globalProtocol.isPresent()) {
                        tmpProtocols.add(globalProtocol.get());
                    } else {
                        ProtocolConfig protocolConfig = new ProtocolConfig();
                        protocolConfig.setId(id);
                        protocolConfig.refresh();
                        tmpProtocols.add(protocolConfig);
                    }
                }
            });
            if (tmpProtocols.size() > arr.length) {
                throw new IllegalStateException("Too much protocols found, the protocols comply to this service are :" + protocolIds + " but got " + protocols
                        .size() + " registries!");
            }
            setProtocols(tmpProtocols);
        }
    }


    public void setInterface(Class<?> interfaceClass) {
        if (interfaceClass != null && !interfaceClass.isInterface()) {
            throw new IllegalStateException("The interface class " + interfaceClass + " is not a interface!");
        }
        this.interfaceClass = interfaceClass;
        setInterface(interfaceClass == null ? null : interfaceClass.getName());
    }

    @Parameter(excluded = true)
    public String getPath() {
        return path;
    }

    public ProviderConfig getProvider() {
        return provider;
    }


    @Parameter(excluded = true)
    public String getProviderIds() {
        return providerIds;
    }


    @Override
    public void setMock(String mock) {
        throw new IllegalArgumentException("mock doesn't support on provider side");
    }

    @Override
    public void setMock(Object mock) {
        throw new IllegalArgumentException("mock doesn't support on provider side");
    }

    public ServiceMetadata getServiceMetadata() {
        return serviceMetadata;
    }

    /**
     * @deprecated Replace to getProtocols()
     */
    @Deprecated
    public List<ProviderConfig> getProviders() {
        return convertProtocolToProvider(protocols);
    }


    @Override
    @Parameter(excluded = true)
    public String getPrefix() {
        return DUBBO + ".service." + interfaceName;
    }

    @Parameter(excluded = true)
    public String getUniqueServiceName() {
        String group = StringUtils.isEmpty(this.group) ? provider.getGroup() : this.group;
        String version = StringUtils.isEmpty(this.version) ? provider.getVersion() : this.version;
        return URL.buildKey(interfaceName, group, version);
    }

    private void computeValidProtocolIds() {
        if (StringUtils.isEmpty(getProtocolIds())) {
            if (getProvider() != null && StringUtils.isNotEmpty(getProvider().getProtocolIds())) {
                setProtocolIds(getProvider().getProtocolIds());
            }
        }
    }

    @Override
    protected void computeValidRegistryIds() {
        super.computeValidRegistryIds();
        if (StringUtils.isEmpty(getRegistryIds())) {
            if (getProvider() != null && StringUtils.isNotEmpty(getProvider().getRegistryIds())) {
                setRegistryIds(getProvider().getRegistryIds());
            }
        }
    }

    public abstract void export();

    public abstract void unexport();

    @Parameter(excluded = true)
    public abstract boolean isExported();

    @Parameter(excluded = true)
    public abstract boolean isUnexported();

}