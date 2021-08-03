package com.easy.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;

/**
 * https://datatracker.ietf.org/doc/html/rfc6455#section-5.2
 * https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_servers
 *
 * @author Ray
 * @date created in 2021/8/1 16:14
 */
public class WsContextImpl implements WsContext {

    private final Logger logger = LoggerFactory.getLogger(WsContextImpl.class);
    private final WorkerThread workerThread;
    private final SocketChannel socketChannel;
    private final WsCallback wsCallback;
    private final List<Plugin> plugins;

    public WsContextImpl(WorkerThread workerThread, SocketChannel socketChannel,
                         WsCallback wsCallback, List<Plugin> plugins) {
        this.workerThread = workerThread;
        this.socketChannel = socketChannel;
        this.wsCallback = wsCallback;
        this.plugins = plugins;
    }

    @Override
    public SocketChannel getSocketChannel() {
        return this.socketChannel;
    }

    @Override
    public void write(WsPayload wsPayload) throws IOException {

        // https://datatracker.ietf.org/doc/html/rfc6455#section-5.7
        ByteBuffer metadata = ByteBuffer.allocate(10);

        // 写入flag
        int opCode = wsPayload.getFrameType().getOpCode();
        // 0x80 => 1000 0000
        // text frame type => 0000 0001
        // 0x80 | text frame type  => 1000 0001
        // (byte)(1000 0001) => -127
        metadata.put((byte) (0x80 | opCode));

        // write payload length
        long length = wsPayload.getDataLength();
        if (length <= 125) {
            metadata.put((byte) length);
        } else if (length <= 0xFFFF) {
            // data range 126 - 0xFFFF
            metadata.put((byte)  0x7E);
            // write unsigned short
            metadata.putShort((short) length);
        } else {
            // data range > 0xFFFF
            metadata.put((byte)  0x7F);
            // write unsigned long
            metadata.putLong(length);
        }

        // flip 一下才能写出数据
        metadata.flip();

        this.socketChannel.write(metadata);
        this.socketChannel.write(ByteBuffer.wrap(wsPayload.getData()));

    }

    private byte[] decode(ByteBuffer data) {

        // 获取消息长度
        long payloadLen = getPayloadLen(data);

        // 获取密匙
        // key：[79, 43, -40, -15]
        byte[] key = new byte[4];
        data.get(key);

        // 获取消息体
        // payload：[46, 73, -69, -107]
        byte[] payload = new byte[(int) payloadLen];
        data.get(payload);

        // key：[79, 43, -40, -15]   (& 0xFF)=> [79, 43, 216, 241]
        // payload：[46, 73, -69, -107] (& 0xFF)=> [46, 73, 187, 149]
        // decode payload: => (byte) (payload[i] ^ key[i & 0x3])
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ((payload[i] & 0xFF) ^ (key[i & 0x3] & 0xFF));
        }

        return payload;
    }

    @Override
    public void handlerPayload() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        int len;
        // -1 or 0 => EOF
        while ((len = socketChannel.read(byteBuffer)) > 0) {
            bos.write(byteBuffer.array(), 0, len);
            byteBuffer.clear();
        }

        ByteBuffer data = ByteBuffer.wrap(bos.toByteArray());
        if (logger.isDebugEnabled()) {
            logger.debug("message body：{}", Arrays.toString(data.array()));
        }

        // 解析数据帧
        switch (parseFrame(data.get())) {
            // 消息
            case TEXT:
                // 解码消息
                WsPayload request = new DefaultWsPayload(FrameType.TEXT, decode(data));
                WsPayload response = wsCallback.onMessage(this, callInterceptRequest(request));
                write(callInterceptResponse(response));
                break;
            case BINARY:
                // 解码消息
                request = new DefaultWsPayload(FrameType.BINARY, decode(data));
                response = wsCallback.onMessage(this, callInterceptRequest(request));
                write(callInterceptResponse(response));
                break;
            case PING:
                wsCallback.onPing(this);
                break;
            // 用户关闭 ws 链接
            case CLOSE_CONNECTION:
                wsCallback.onClose(this);
                close();
                break;
            default:
        }

    }

    private WsPayload callInterceptRequest(WsPayload wsPayload) {

        if (plugins.isEmpty()) {
            return wsPayload;
        }

        WsPayload result = wsPayload;
        for (Plugin plugin : plugins) {
            result = plugin.interceptRequest(result);
        }

        return result;
    }

    private WsPayload callInterceptResponse(WsPayload wsPayload) {

        if (plugins.isEmpty()) {
            return wsPayload;
        }

        WsPayload result = wsPayload;
        for (Plugin plugin : plugins) {
            result = plugin.interceptResponse(result);
        }

        return result;
    }

    private FrameType parseFrame(byte code) {
        // abcd message body：[-127, -124, 79, 43, -40, -15, 46, 73, -69, -107]
        // data[0] & 0xFF => 129（Unsigned Byte）
        // 129 & 0xF => 1（OpCode：最低四位）
        return FrameType.parseFrame((code & 0xFF) & 0xF);
    }

    private long getPayloadLen(ByteBuffer data) {
        // abcd message body：[-127, -124, 79, 43, -40, -15, 46, 73, -69, -107]
        // data[1] & 0xFF => 132（Unsigned Byte）
        // 132 ^ 128 => 4
        int flag = (data.get() & 0xFF) ^ 128;
        if (flag <= 125) {
            return flag;
        } else if (flag == 126) {
            // convert to unsigned short
            return data.getShort() & 0xFFFF;
        } else if (flag == 127) {
            return data.getLong();
        }

        return 0;
    }

    @Override
    public void close() {
        try {
            if (socketChannel.isOpen()) {
                this.socketChannel.close();
                this.workerThread.getWsCounter().decrementAndGet();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public WorkerThread getWorkerThread() {
        return this.workerThread;
    }
}
