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

import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import static org.apache.dubbo.common.constants.CommonConstants.INVOKER_LISTENER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REFERENCE_FILTER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.STUB_EVENT_KEY;

/**
 * AbstractConsumerConfig
 * 抽象的客户端引用配置
 * <p>
 * 引用配置
 *
 * @export
 * @see ReferenceConfigBase
 */
public abstract class AbstractReferenceConfig extends AbstractInterfaceConfig {

    private static final long serialVersionUID = -2786526984373031126L;

    // ======== Reference config default values, will take effect if reference's attribute is not set  ========

    /**
     * Check if service provider exists, if not exists, it will be fast fail
     */
    protected Boolean check;

    /**
     * Whether to eagle-init
     */
    protected Boolean init;

    /**
     * Whether to use generic interface
     */
    protected String generic;


    /**
     * 是否延迟创建连接
     * Lazy create connection
     */
    protected Boolean lazy;
    /**
     * ???
     */
    protected String reconnect;
    /**
     * ???
     */
    protected Boolean sticky = false;

    /**
     * Whether to support event in stub.
     * 是否支持事件桩
     */
    //TODO solve merge problem
    protected Boolean stubevent;//= Constants.DEFAULT_STUB_EVENT;

    /**
     * The remote service version the customer side will reference
     * setter注入,服务默认版本
     */
    protected String version;

    /**
     * The remote service group the customer side will reference
     */
    protected String group;

    /**
     * declares which app or service this interface belongs to
     * 声明这个接口属于哪个应用或服务
     */
    protected String providedBy;

    /**
     * 路由器
     */
    protected String router;

    // -----------------------------------------------------------------------------------------------------------------
    // 可导出getter
    public Boolean isCheck() {
        return check;
    }

    public Boolean isInit() {
        return init;
    }

    public String getGeneric() {
        return generic;
    }

//    /**
//     * @return
//     * @deprecated instead, use the parameter <b>scope</> to judge if it's in jvm, scope=local
//     */
//    @Deprecated
//    public Boolean isInjvm() {
//        return injvm;
//    }

    public Boolean getLazy() {
        return lazy;
    }

    @Parameter(key = STUB_EVENT_KEY)
    public Boolean getStubevent() {
        return stubevent;
    }

    public String getReconnect() {
        return reconnect;
    }

    public Boolean getSticky() {
        return sticky;
    }

    public String getVersion() {
        return version;
    }

    public String getGroup() {
        return group;
    }

    @Parameter(key = "provided-by")
    public String getProvidedBy() {
        return providedBy;
    }

    @Parameter(key = "router", append = true)
    public String getRouter() {
        return router;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可注入setter
    public void setCheck(Boolean check) {
        this.check = check;
    }

    public void setInit(Boolean init) {
        this.init = init;
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

//    /**
//     * @param injvm
//     * @deprecated instead, use the parameter <b>scope</b> to judge if it's in jvm, scope=local
//     */
//    @Deprecated
//    public void setInjvm(Boolean injvm) {
//        this.injvm = injvm;
//    }

    public void setLazy(Boolean lazy) {
        this.lazy = lazy;
    }

    public void setReconnect(String reconnect) {
        this.reconnect = reconnect;
    }

    public void setSticky(Boolean sticky) {
        this.sticky = sticky;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setProvidedBy(String providedBy) {
        this.providedBy = providedBy;
    }

    public void setRouter(String router) {
        this.router = router;
    }


    // -----------------------------------------------------------------------------------------------------------------
    // 普通
    @Override
    @Parameter(key = REFERENCE_FILTER_KEY, append = true)
    public String getFilter() {
        return super.getFilter();
    }

    @Override
    @Parameter(key = INVOKER_LISTENER_KEY, append = true)
    public String getListener() {
        return super.getListener();
    }

    @Override
    public void setListener(String listener) {
        super.setListener(listener);
    }

    @Override
    public void setOnconnect(String onconnect) {
        if (onconnect != null && onconnect.length() > 0) {
            this.stubevent = true;
        }
        super.setOnconnect(onconnect);
    }

    @Override
    public void setOndisconnect(String ondisconnect) {
        if (ondisconnect != null && ondisconnect.length() > 0) {
            this.stubevent = true;
        }
        super.setOndisconnect(ondisconnect);
    }
    // -----------------------------------------------------------------------------------------------------------------


}
