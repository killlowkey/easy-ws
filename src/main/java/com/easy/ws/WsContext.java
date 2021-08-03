package com.easy.ws;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

/**
 * @author Ray
 * @date created in 2021/8/1 16:11
 */
public interface WsContext {

    SocketChannel getSocketChannel();

    void write(WsPayload wsPayload) throws IOException;

    void handlerPayload() throws IOException;

    void close();

    WorkerThread getWorkerThread();

    default SocketAddress getRemoteAddress() {
        try {
            return getSocketChannel().getRemoteAddress();
        } catch (IOException e) {
            throw new RuntimeException("get remote address found a error");
        }
    }

}
