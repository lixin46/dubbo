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

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.unmodifiableSortedSet;
import static java.util.stream.StreamSupport.stream;


/**
 * 元数据服务
 * <p>
 * 远程的元数据服务,依赖全局的MetadataReport的能力
 *
 * @since 2.7.5
 */
public interface MetadataService {

    //FIXME the value is default, it was used by testing temporarily
    static final String DEFAULT_EXTENSION = "default";

    /**
     * The value of all service names
     */
    String ALL_SERVICE_NAMES = "*";

    /**
     * The value of All service instances
     */
    String ALL_SERVICE_INTERFACES = "*";

    /**
     * The service interface name of {@link MetadataService}
     */
    String SERVICE_INTERFACE_NAME = MetadataService.class.getName();

    /**
     * The contract version of {@link MetadataService}, the future update must make sure compatible.
     */
    String VERSION = "1.0.0";

    /**
     * @return 当前服务的名称
     */
    String serviceName();

    /**
     * @return 元数据服务的版本
     */
    default String version() {
        return VERSION;
    }

    /**
     * @return 所有订阅的服务
     */
    default SortedSet<String> getSubscribedURLs() {
        throw new UnsupportedOperationException("This operation is not supported for consumer.");
    }

    /**
     * @return 所有暴露的服务
     */
    default SortedSet<String> getExportedURLs() {
        return getExportedURLs(ALL_SERVICE_INTERFACES);
    }

    /**
     * @param serviceInterface 指定的服务接口
     * @return 指定接口暴露的服务
     */
    default SortedSet<String> getExportedURLs(String serviceInterface) {
        return getExportedURLs(serviceInterface, null);
    }

    /**
     * @param serviceInterface 服务接口
     * @param group            服务分组
     * @return 指定接口指定分组暴露的服务
     */
    default SortedSet<String> getExportedURLs(String serviceInterface, String group) {
        return getExportedURLs(serviceInterface, group, null);
    }

    /**
     * Get the {@link SortedSet sorted set} of String that presents the specified Dubbo exported {@link URL urls} by the
     * <code>serviceInterface</code>, <code>group</code> and <code>version</code>
     *
     * @param serviceInterface The class name of Dubbo service interface
     * @param group            the Dubbo Service Group (optional)
     * @param version          the Dubbo Service Version (optional)
     * @return the non-null read-only {@link SortedSet sorted set} of {@link URL#toFullString() strings} presenting the {@link URL URLs}
     * @see #toSortedStrings(Stream)
     * @see URL#toFullString()
     */
    default SortedSet<String> getExportedURLs(String serviceInterface, String group, String version) {
        return getExportedURLs(serviceInterface, group, version, null);
    }

    /**
     * Get the sorted set of String that presents the specified Dubbo exported {@link URL urls} by the
     * <code>serviceInterface</code>, <code>group</code>, <code>version</code> and <code>protocol</code>
     *
     * @param serviceInterface The class name of Dubbo service interface
     * @param group            the Dubbo Service Group (optional)
     * @param version          the Dubbo Service Version (optional)
     * @param protocol         the Dubbo Service Protocol (optional)
     * @return the non-null read-only {@link SortedSet sorted set} of {@link URL#toFullString() strings} presenting the {@link URL URLs}
     * @see #toSortedStrings(Stream)
     * @see URL#toFullString()
     */
    SortedSet<String> getExportedURLs(String serviceInterface, String group, String version, String protocol);

    /**
     * @param interfaceName
     * @param version
     * @param group
     * @return 服务接口定义
     */
    String getServiceDefinition(String interfaceName, String version, String group);

    /**
     * Interface definition.
     *
     * @return
     */
    String getServiceDefinition(String serviceKey);

    /**
     * Is the {@link URL} for the {@link MetadataService} or not?
     *
     * @param url {@link URL url}
     * @return
     */
    static boolean isMetadataServiceURL(URL url) {
        String serviceInterface = url.getServiceInterface();
        return SERVICE_INTERFACE_NAME.equals(serviceInterface);
    }

    /**
     * Convert the multiple {@link URL urls} to a {@link List list} of {@link URL urls}
     *
     * @param urls the strings presents the {@link URL Dubbo URLs}
     * @return non-null
     */
    static List<URL> toURLs(Iterable<String> urls) {
        return stream(urls.spliterator(), false)
                .map(URL::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * Convert the specified {@link Iterable} of {@link URL URLs} to be the {@link URL#toFullString() strings} presenting
     * the {@link URL URLs}
     *
     * @param iterable {@link Iterable} of {@link URL}
     * @return the non-null read-only {@link SortedSet sorted set} of {@link URL#toFullString() strings} presenting
     * @see URL#toFullString()
     */
    static SortedSet<String> toSortedStrings(Iterable<URL> iterable) {
        return toSortedStrings(StreamSupport.stream(iterable.spliterator(), false));
    }

    /**
     * Convert the specified {@link Stream} of {@link URL URLs} to be the {@link URL#toFullString() strings} presenting
     * the {@link URL URLs}
     *
     * @param stream {@link Stream} of {@link URL}
     * @return the non-null read-only {@link SortedSet sorted set} of {@link URL#toFullString() strings} presenting
     * @see URL#toFullString()
     */
    static SortedSet<String> toSortedStrings(Stream<URL> stream) {
        return unmodifiableSortedSet(stream.map(URL::toFullString).collect(TreeSet::new, Set::add, Set::addAll));
    }
}
