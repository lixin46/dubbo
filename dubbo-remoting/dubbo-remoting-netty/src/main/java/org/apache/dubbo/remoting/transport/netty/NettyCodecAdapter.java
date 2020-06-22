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
import org.apache.dubbo.remoting.Codec2;
import org.apache.dubbo.remoting.buffer.DynamicChannelBuffer;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import java.io.IOException;

import static org.apache.dubbo.remoting.Constants.BUFFER_KEY;
import static org.apache.dubbo.remoting.Constants.DEFAULT_BUFFER_SIZE;
import static org.apache.dubbo.remoting.Constants.MAX_BUFFER_SIZE;
import static org.apache.dubbo.remoting.Constants.MIN_BUFFER_SIZE;

/**
 * NettyCodecAdapter.
 * netty序列化编解码适配器
 */
final class NettyCodecAdapter {

    /**
     * netty的编码器
     */
    private final ChannelHandler encoder = new InternalEncoder();
    /**
     * netty的解码器
     */
    private final ChannelHandler decoder = new InternalDecoder();


    /**
     * dubbo定义的编解码器
     */
    private final Codec2 codec;
    /**
     * 配置
     */
    private final URL url;
    /**
     * 缓冲大小
     */
    private final int bufferSize;
    /**
     * dubbo定义的通道处理器
     */
    private final org.apache.dubbo.remoting.ChannelHandler handler;

    /**
     * 构造方法
     * @param codec dubbo的接口定义
     * @param url 配置
     * @param handler dubbo定义的通道处理器
     */
    public NettyCodecAdapter(Codec2 codec, URL url, org.apache.dubbo.remoting.ChannelHandler handler) {
        this.codec = codec;
        this.url = url;
        this.handler = handler;
        // buffer,默认8kb
        int b = url.getPositiveParameter(BUFFER_KEY, DEFAULT_BUFFER_SIZE);
        // 1~16kb,超出范围则默认8kb
        this.bufferSize = b >= MIN_BUFFER_SIZE && b <= MAX_BUFFER_SIZE ? b : DEFAULT_BUFFER_SIZE;
    }

    public ChannelHandler getEncoder() {
        return encoder;
    }

    public ChannelHandler getDecoder() {
        return decoder;
    }

    @Sharable
    private class InternalEncoder extends OneToOneEncoder {

        @Override
        protected Object encode(ChannelHandlerContext ctx, Channel ch, Object msg) throws Exception {
            org.apache.dubbo.remoting.buffer.ChannelBuffer buffer = org.apache.dubbo.remoting.buffer.ChannelBuffers.dynamicBuffer(1024);
            //
            NettyChannel channel = NettyChannel.getOrAddChannel(ch, url, handler);
            try {
                // 编码
                codec.encode(channel, buffer, msg);
            } finally {
                NettyChannel.removeChannelIfDisconnected(ch);
            }
            return ChannelBuffers.wrappedBuffer(buffer.toByteBuffer());
        }
    }

    private class InternalDecoder extends SimpleChannelUpstreamHandler {

        private org.apache.dubbo.remoting.buffer.ChannelBuffer buffer =
                org.apache.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {
            // 消息???
            Object o = event.getMessage();
            // ???
            if (!(o instanceof ChannelBuffer)) {
                ctx.sendUpstream(event);
                return;
            }

            ChannelBuffer input = (ChannelBuffer) o;
            // 可读取字节数
            int readable = input.readableBytes();
            if (readable <= 0) {
                return;
            }
            //
            org.apache.dubbo.remoting.buffer.ChannelBuffer message;
            // 内部保存的缓冲区可读
            if (buffer.readable()) {
                // 动态通道缓冲区
                if (buffer instanceof DynamicChannelBuffer) {
                    // 写入刚刚读到的缓存,处理发送拆包
                    buffer.writeBytes(input.toByteBuffer());
                    message = buffer;
                }
                // 非动态
                else {
                    // 总字节
                    int size = buffer.readableBytes() + input.readableBytes();
                    // 创建动态缓冲
                    message = org.apache.dubbo.remoting.buffer.ChannelBuffers.dynamicBuffer(
                            size > bufferSize ? size : bufferSize);
                    message.writeBytes(buffer, buffer.readableBytes());
                    message.writeBytes(input.toByteBuffer());
                }
            }
            // 不可读
            else {
                message = org.apache.dubbo.remoting.buffer.ChannelBuffers.wrappedBuffer(
                        input.toByteBuffer());
            }

            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.getChannel(), url, handler);
            Object msg;
            int saveReaderIndex;

            try {
                // decode object.
                do {
                    // 暂存当前读取位置
                    saveReaderIndex = message.readerIndex();
                    try {
                        msg = codec.decode(channel, message);
                    } catch (IOException e) {
                        buffer = org.apache.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                        throw e;
                    }
                    // 需要更多输入,说明遇到了tcp拆包
                    if (msg == Codec2.DecodeResult.NEED_MORE_INPUT) {
                        // 恢复读取位置
                        message.readerIndex(saveReaderIndex);
                        // 退出循环
                        break;
                    }
                    // 跳过一些输入,说明已经解析了完整的消息,剩余了一些字节没有使用,并且已经记录了读取位置
                    else {
                        if (saveReaderIndex == message.readerIndex()) {
                            buffer = org.apache.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                            throw new IOException("Decode without read data.");
                        }
                        if (msg != null) {
                            // 接收消息回调
                            Channels.fireMessageReceived(ctx, msg, event.getRemoteAddress());
                        }
                    }
                } while (message.readable());
            } finally {
                if (message.readable()) {
                    message.discardReadBytes();
                    buffer = message;
                } else {
                    buffer = org.apache.dubbo.remoting.buffer.ChannelBuffers.EMPTY_BUFFER;
                }
                NettyChannel.removeChannelIfDisconnected(ctx.getChannel());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            ctx.sendUpstream(e);
        }
    }
}