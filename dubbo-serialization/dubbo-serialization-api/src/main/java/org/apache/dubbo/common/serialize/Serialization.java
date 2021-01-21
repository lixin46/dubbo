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
package org.apache.dubbo.common.serialize;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 序列化和反序列化协议
 * 单例,线程安全
 * 默认使用hession2,
 * 对应实现为: org.apache.dubbo.common.serialize.hessian2.Hessian2Serialization
 */
@SPI("hessian2")
public interface Serialization {

    /**
     * 建议自定义实现使用与{@link Constants}中定义不同的值,且不要大于ExchangeCodec.SERIALIZATION_MASK (31)
     * 因为dubbo协议在头中,使用5位记录序列化协议(最大值31)
     *
     * @return 内容类型id???
     */
    byte getContentTypeId();

    /**
     * MediaType格式
     * @return 内容类型
     */
    String getContentType();

    /**
     * 获取序列化器
     *
     * @param url    远程服务地址,带配置
     * @param output 底层输出流,序列化时写入的目的地
     * @return 序列化器
     * @throws IOException 异常
     */
    @Adaptive
    ObjectOutput serialize(URL url, OutputStream output) throws IOException;

    /**
     * 获取反序列化器
     *
     * @param url   远程服务地址,带配置
     * @param input 底层输入流,反序列化数据读取的来源
     * @return 反序列化器
     * @throws IOException
     */
    @Adaptive
    ObjectInput deserialize(URL url, InputStream input) throws IOException;

}
