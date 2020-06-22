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
package org.apache.dubbo.remoting.transport;

import org.apache.dubbo.common.Resetable;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Codec;
import org.apache.dubbo.remoting.Codec2;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.transport.codec.CodecAdapter;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * AbstractEndpoint
 */
public abstract class AbstractEndpoint extends AbstractPeer implements Resetable {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEndpoint.class);
    protected static Codec2 getChannelCodec(URL url) {
        // codec参数,默认为telnet
        String codecName = url.getParameter(Constants.CODEC_KEY, "telnet");
        // 存在对应的Codec2编解码器
        if (ExtensionLoader.getExtensionLoader(Codec2.class).hasExtension(codecName)) {
            return ExtensionLoader.getExtensionLoader(Codec2.class).getExtension(codecName);
        }
        // 不存在,则获取老的接口
        else {
            Codec extension = ExtensionLoader.getExtensionLoader(Codec.class).getExtension(codecName);
            // 创建适配器
            return new CodecAdapter(extension);
        }
    }
    // -------------
    /**
     * 编解码器
     */
    private Codec2 codec;

    /**
     * 读写超时
     */
    private int timeout;
    /**
     * 握手超时
     */
    private int connectTimeout;

    /**
     * 构造方法
     * @param url 配置
     * @param handler 通道处理器
     */
    public AbstractEndpoint(URL url, ChannelHandler handler) {
        super(url, handler);
        // 根据url中的codec参数,获取编解码器实例,默认使用telnet
        this.codec = getChannelCodec(url);
        // 获取timeout参数,默认为1000
        this.timeout = url.getPositiveParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
        // 获取connect.timeout参数,默认为3000
        this.connectTimeout = url.getPositiveParameter(Constants.CONNECT_TIMEOUT_KEY, Constants.DEFAULT_CONNECT_TIMEOUT);
    }



    @Override
    public void reset(URL url) {
        if (isClosed()) {
            throw new IllegalStateException("Failed to reset parameters "
                    + url + ", cause: Channel closed. channel: " + getLocalAddress());
        }
        try {
            if (url.hasParameter(TIMEOUT_KEY)) {
                int t = url.getParameter(TIMEOUT_KEY, 0);
                if (t > 0) {
                    this.timeout = t;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        try {
            if (url.hasParameter(Constants.CONNECT_TIMEOUT_KEY)) {
                int t = url.getParameter(Constants.CONNECT_TIMEOUT_KEY, 0);
                if (t > 0) {
                    this.connectTimeout = t;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        try {
            if (url.hasParameter(Constants.CODEC_KEY)) {
                this.codec = getChannelCodec(url);
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }

    @Deprecated
    public void reset(org.apache.dubbo.common.Parameters parameters) {
        reset(getUrl().addParameters(parameters.getParameters()));
    }

    protected Codec2 getCodec() {
        return codec;
    }

    protected int getTimeout() {
        return timeout;
    }

    protected int getConnectTimeout() {
        return connectTimeout;
    }

}
