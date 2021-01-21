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

package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.SPI;

/**
 * 注册中心服务监听器
 * 用于监听RegistryService的方法调用,包括:
 * 1.register
 * 2.unregister
 * 3.subscribe
 * 4.unsubscribe
 */
@SPI
public interface RegistryServiceListener {
    default void onRegister(URL url) {

    }


    default void onUnregister(URL url) {

    }

    default void onSubscribe(URL url) {

    }

    default void onUnsubscribe(URL url) {

    }
}
