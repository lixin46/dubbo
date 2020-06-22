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
package org.apache.dubbo.metadata;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.metadata.store.InMemoryWritableMetadataService;
import org.apache.dubbo.rpc.model.ApplicationModel;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;


/**
 * 可写的元数据服务
 * 默认使用名称为local的扩展实例
 * @since 2.7.5
 */
@SPI(DEFAULT_METADATA_STORAGE_TYPE)
public interface WritableMetadataService extends MetadataService {
    /**
     * Gets the current Dubbo Service name
     *
     * @return non-null
     */
    @Override
    default String serviceName() {
        return ApplicationModel.getApplication();
    }

    /**
     * 到处指定的信息
     * @param url 指定的url
     * @return 是否成功
     */
    boolean exportURL(URL url);

    /**
     * 取消导出
     * @param url 指定的信息
     * @return 是否成功
     */
    boolean unexportURL(URL url);

    /**
     * fresh Exports
     *
     * @return If success , return <code>true</code>
     */
    default boolean refreshMetadata(String exportedRevision, String subscribedRevision) {
        return true;
    }

    /**
     * 订阅
     * @param url 指定的信息
     * @return 是否成功
     */
    boolean subscribeURL(URL url);

    /**
     * 取消订阅
     * @param url 指定的信息
     * @return 是否成功
     */
    boolean unsubscribeURL(URL url);

    /**
     * 发布服务定义
     *
     * @param providerUrl url
     */
    void publishServiceDefinition(URL providerUrl);

    /**
     * Get {@link ExtensionLoader#getDefaultExtension() the defautl extension} of {@link WritableMetadataService}
     *
     * @return non-null
     * @see InMemoryWritableMetadataService
     */
    static WritableMetadataService getDefaultExtension() {
        return getExtensionLoader(WritableMetadataService.class).getDefaultExtension();
    }

    static WritableMetadataService getExtension(String name) {
        ExtensionLoader<WritableMetadataService> extensionLoader = getExtensionLoader(WritableMetadataService.class);
        return extensionLoader.getOrDefaultExtension(name);
    }
}
