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
package org.apache.dubbo.remoting.transport.netty;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.transport.AbstractClient;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * NettyClient.
 */
public class NettyClient extends AbstractClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);

    // ChannelFactory's closure has a DirectMemory leak, using static to avoid
    // https://issues.jboss.org/browse/NETTY-424
    private static final ChannelFactory CHANNEL_FACTORY;
    static {
        // 按需产生线程的线程池
        ExecutorService nettyClientBoss = Executors.newCachedThreadPool(new NamedThreadFactory("NettyClientBoss", true));
        // 按需产生线程的线程池
        ExecutorService nettyClientWoker = Executors.newCachedThreadPool(new NamedThreadFactory("NettyClientWorker", true));

        CHANNEL_FACTORY = new NioClientSocketChannelFactory(
                nettyClientBoss,
                nettyClientWoker,
                Constants.DEFAULT_IO_THREADS// 可用核数+1,不高于32核
        );
    }
    // -----------------------------------------------------------------------------------------------------------------
    /**
     * 客户端启动逻辑,open时构建
     */
    private ClientBootstrap bootstrap;
    /**
     * 通道,用于通信,connect成功后获取
     */
    private volatile Channel channel; // volatile, please copy reference to use

    /**
     * 构造方法
     * @param url 配置
     * @param handler 通道处理器
     * @throws RemotingException
     */
    public NettyClient(final URL url, final ChannelHandler handler) throws RemotingException {
        super(url, wrapChannelHandler(url, handler));
    }

    @Override
    protected void doOpen() throws Throwable {
        //
        NettyHelper.setNettyLoggerFactory();
        // 客户端引导
        bootstrap = new ClientBootstrap(CHANNEL_FACTORY);
        // config
        // @see org.jboss.netty.channel.socket.SocketChannelConfig
        bootstrap.setOption("keepAlive", true);
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("connectTimeoutMillis", getConnectTimeout());
        // 通道处理器
        final NettyHandler nettyHandler = new NettyHandler(getUrl(), this);

        // 管道工厂
        ChannelPipelineFactory channelPipelineFactory = new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() {
                NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyClient.this);
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", adapter.getDecoder());
                pipeline.addLast("encoder", adapter.getEncoder());
                pipeline.addLast("handler", nettyHandler);
                return pipeline;
            }
        };
        //
        bootstrap.setPipelineFactory(channelPipelineFactory);
    }

    @Override
    protected void doConnect() throws Throwable {
        long start = System.currentTimeMillis();
        // 连接url中的主机端口
        ChannelFuture future = bootstrap.connect(getConnectAddress());
        try {
            // 等待超时
            boolean ret = future.awaitUninterruptibly(getConnectTimeout(), TimeUnit.MILLISECONDS);

            // 连接成功
            if (ret && future.isSuccess()) {
                // 获取通道
                Channel newChannel = future.getChannel();
                // 读写事件
                newChannel.setInterestOps(Channel.OP_READ_WRITE);
                try {
                    // Close old channel
                    Channel oldChannel = NettyClient.this.channel; // copy reference
                    // 已经存在通道,则关闭
                    if (oldChannel != null) {
                        try {
                            if (logger.isInfoEnabled()) {
                                logger.info("Close old netty channel " + oldChannel + " on create new netty channel " + newChannel);
                            }
                            oldChannel.close();
                        } finally {
                            NettyChannel.removeChannelIfDisconnected(oldChannel);
                        }
                    }
                } finally {
                    if (NettyClient.this.isClosed()) {
                        try {
                            if (logger.isInfoEnabled()) {
                                logger.info("Close new netty channel " + newChannel + ", because the client closed.");
                            }
                            newChannel.close();
                        } finally {
                            NettyClient.this.channel = null;
                            NettyChannel.removeChannelIfDisconnected(newChannel);
                        }
                    } else {
                        NettyClient.this.channel = newChannel;
                    }
                }
            }
            // 连接失败
            else if (future.getCause() != null) {
                throw new RemotingException(this, "client(url: " + getUrl() + ") failed to connect to server "
                        + getRemoteAddress() + ", error message is:" + future.getCause().getMessage(), future.getCause());
            }
            // 连接超时
            else {
                throw new RemotingException(this, "client(url: " + getUrl() + ") failed to connect to server "
                        + getRemoteAddress() + " client-side timeout "
                        + getConnectTimeout() + "ms (elapsed: " + (System.currentTimeMillis() - start) + "ms) from netty client "
                        + NetUtils.getLocalHost() + " using dubbo version " + Version.getVersion());
            }
        } finally {
            if (!isConnected()) {
                future.cancel();
            }
        }
    }

    @Override
    protected void doDisConnect() throws Throwable {
        try {
            NettyChannel.removeChannelIfDisconnected(channel);
        } catch (Throwable t) {
            logger.warn(t.getMessage());
        }
    }

    @Override
    protected void doClose() throws Throwable {
        /*try {
            bootstrap.releaseExternalResources();
        } catch (Throwable t) {
            logger.warn(t.getMessage());
        }*/
    }

    @Override
    protected org.apache.dubbo.remoting.Channel getChannel() {
        Channel c = channel;
        if (c == null || !c.isConnected()) {
            return null;
        }
        return NettyChannel.getOrAddChannel(c, getUrl(), this);
    }

    Channel getNettyChannel() {
        return channel;
    }

}
