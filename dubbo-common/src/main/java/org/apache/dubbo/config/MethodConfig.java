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

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.annotation.Method;
import org.apache.dubbo.config.support.Parameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.dubbo.config.Constants.ON_INVOKE_INSTANCE_KEY;
import static org.apache.dubbo.config.Constants.ON_INVOKE_METHOD_KEY;
import static org.apache.dubbo.config.Constants.ON_RETURN_INSTANCE_KEY;
import static org.apache.dubbo.config.Constants.ON_RETURN_METHOD_KEY;
import static org.apache.dubbo.config.Constants.ON_THROW_INSTANCE_KEY;
import static org.apache.dubbo.config.Constants.ON_THROW_METHOD_KEY;

/**
 * The method configuration
 * 方法配置
 *
 *
 * @export
 */
public class MethodConfig extends AbstractMethodConfig {

    private static final long serialVersionUID = 884908855422675941L;

    public static List<MethodConfig> constructMethodConfig(Method[] methods) {
        if (methods != null && methods.length != 0) {
            List<MethodConfig> methodConfigs = new ArrayList<MethodConfig>(methods.length);
            for (int i = 0; i < methods.length; i++) {
                MethodConfig methodConfig = new MethodConfig(methods[i]);
                methodConfigs.add(methodConfig);
            }
            return methodConfigs;
        }
        return Collections.emptyList();
    }
    // ---------------------------------------------------------------------
    // 不会导出的参数
    /**
     * The method name
     * 方法名
     */
    private String name;
    /**
     * The method arguments
     *
     */
    private List<ArgumentConfig> arguments;
    /**
     * Callback instance when async-call is returned
     * 调用的实例对象
     * xml中,通过onreturn属性配置,值格式为ref.method
     */
    private Object onreturn;
    /**
     * Callback method when async-call is returned
     * 调用实例对象的方法名
     */
    private String onreturnMethod;
    /**
     * Callback instance when async-call has exception thrown
     */
    private Object onthrow;

    /**
     * Callback method when async-call has exception thrown
     */
    private String onthrowMethod;
    /**
     * Callback instance when async-call is invoked
     */
    private Object oninvoke;

    /**
     * Callback method when async-call is invoked
     */
    private String oninvokeMethod;
    /**
     * These properties come from MethodConfig's parent Config module, they will neither be collected directly from xml or API nor be delivered to url
     * 接口名
     */
    private String service;
    /**
     * 接口配置的id字段
     */
    private String serviceId;

    @Parameter(excluded = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        if (StringUtils.isEmpty(id)) {
            id = name;
        }
    }


    public List<ArgumentConfig> getArguments() {
        return arguments;
    }

    @SuppressWarnings("unchecked")
    public void setArguments(List<? extends ArgumentConfig> arguments) {
        this.arguments = (List<ArgumentConfig>) arguments;
    }
    @Parameter(key = ON_RETURN_INSTANCE_KEY, excluded = true, attribute = true)
    public Object getOnreturn() {
        return onreturn;
    }

    public void setOnreturn(Object onreturn) {
        this.onreturn = onreturn;
    }

    @Parameter(key = ON_RETURN_METHOD_KEY, excluded = true, attribute = true)
    public String getOnreturnMethod() {
        return onreturnMethod;
    }

    public void setOnreturnMethod(String onreturnMethod) {
        this.onreturnMethod = onreturnMethod;
    }

    @Parameter(key = ON_THROW_INSTANCE_KEY, excluded = true, attribute = true)
    public Object getOnthrow() {
        return onthrow;
    }

    public void setOnthrow(Object onthrow) {
        this.onthrow = onthrow;
    }

    @Parameter(key = ON_THROW_METHOD_KEY, excluded = true, attribute = true)
    public String getOnthrowMethod() {
        return onthrowMethod;
    }

    public void setOnthrowMethod(String onthrowMethod) {
        this.onthrowMethod = onthrowMethod;
    }

    @Parameter(key = ON_INVOKE_INSTANCE_KEY, excluded = true, attribute = true)
    public Object getOninvoke() {
        return oninvoke;
    }

    public void setOninvoke(Object oninvoke) {
        this.oninvoke = oninvoke;
    }

    @Parameter(key = ON_INVOKE_METHOD_KEY, excluded = true, attribute = true)
    public String getOninvokeMethod() {
        return oninvokeMethod;
    }

    public void setOninvokeMethod(String oninvokeMethod) {
        this.oninvokeMethod = oninvokeMethod;
    }
    @Parameter(excluded = true)
    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    @Parameter(excluded = true)
    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    /**
     * service and name must not be null.
     *
     * @return
     */
    @Override
    @Parameter(excluded = true)
    public String getPrefix() {
        // 默认为 dubbo.{service}.{serviceId}.{name}
        return CommonConstants.DUBBO + "." + service
                + (StringUtils.isEmpty(serviceId) ? "" : ("." + serviceId))
                + "." + getName();
    }
    // ---------------------------------------------------------------------
    // 会导出参数
    /**
     * Stat
     * ???
     */
    private Integer stat;

    /**
     * Whether to retry
     * 是否重试
     */
    private Boolean retry;

    /**
     * If it's reliable
     * 是否可靠
     */
    private Boolean reliable;

    /**
     * Thread limits for method invocations
     * 方法调用的线程数限制
     */
    private Integer executes;

    /**
     * If it's deprecated
     * 是否废弃
     */
    private Boolean deprecated;

    /**
     * Whether to enable sticky
     * ???
     */
    private Boolean sticky;

    /**
     * Whether need to return
     * 是否需要返回
     */
    private Boolean isReturn;

    /**
     * 构造方法
     */
    public MethodConfig() {
    }


    /**
     * 构造方法
     * @param method
     */
    public MethodConfig(Method method) {
        appendAnnotation(Method.class, method);

        this.setReturn(method.isReturn());

        if(!"".equals(method.oninvoke())){
            this.setOninvoke(method.oninvoke());
        }
        if(!"".equals(method.onreturn())){
            this.setOnreturn(method.onreturn());
        }
        if(!"".equals(method.onthrow())){
            this.setOnthrow(method.onthrow());
        }

        if (method.arguments() != null && method.arguments().length != 0) {
            List<ArgumentConfig> argumentConfigs = new ArrayList<ArgumentConfig>(method.arguments().length);
            this.setArguments(argumentConfigs);
            for (int i = 0; i < method.arguments().length; i++) {
                ArgumentConfig argumentConfig = new ArgumentConfig(method.arguments()[i]);
                argumentConfigs.add(argumentConfig);
            }
        }
    }

    public Integer getStat() {
        return stat;
    }

    @Deprecated
    public void setStat(Integer stat) {
        this.stat = stat;
    }

    @Deprecated
    public Boolean isRetry() {
        return retry;
    }

    @Deprecated
    public void setRetry(Boolean retry) {
        this.retry = retry;
    }

    @Deprecated
    public Boolean isReliable() {
        return reliable;
    }

    @Deprecated
    public void setReliable(Boolean reliable) {
        this.reliable = reliable;
    }

    public Integer getExecutes() {
        return executes;
    }

    public void setExecutes(Integer executes) {
        this.executes = executes;
    }

    public Boolean getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(Boolean deprecated) {
        this.deprecated = deprecated;
    }

    public Boolean getSticky() {
        return sticky;
    }

    public void setSticky(Boolean sticky) {
        this.sticky = sticky;
    }

    public Boolean isReturn() {
        return isReturn;
    }

    public void setReturn(Boolean isReturn) {
        this.isReturn = isReturn;
    }



}
