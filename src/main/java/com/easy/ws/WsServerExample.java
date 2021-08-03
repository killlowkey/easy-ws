package com.easy.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * @author Ray
 * @date created in 2021/8/1 16:58
 */
public class WsServerExample {

    private static final Logger logger = LoggerFactory.getLogger(WsServerExample.class);

    public static void main(String[] args) throws Exception {
        WsServer wsServer = new WsServer(8080);
        wsServer.setWsCallback(new WsCallback() {
            @Override
            public void onOpen(WsContext context) {
                System.out.println("成功与 " + context.getRemoteAddress() + "建立起 ws 链接");
            }

            @Override
            public WsPayload onMessage(WsContext context, WsPayload wsPayload) {
                System.out.println("接收到 " + context.getRemoteAddress() + " 消息：" + new String(wsPayload.getData(),
                        StandardCharsets.UTF_8));
                if (wsPayload.getDataLength() > 2) {
                    // send binary data
                    return new DefaultWsPayload(FrameType.BINARY, "hello world".getBytes(StandardCharsets.UTF_8));
                } else {
                    // send text data
                    return new DefaultWsPayload(FrameType.TEXT, "hello".getBytes(StandardCharsets.UTF_8));
                }
            }

            @Override
            public void onClose(WsContext context) {
                System.out.println(context.getRemoteAddress() + " 断开了 ws 链接");
                context.close();
            }

        });
        // 插件
        wsServer.addPlugin(new Plugin() {
            @Override
            public WsPayload interceptRequest(WsPayload wsPayload) {
                logger.info("拦截到请求");
                return wsPayload;
            }

            @Override
            public WsPayload interceptResponse(WsPayload wsPayload) {
                logger.info("拦截到响应");
                return wsPayload;
            }
        });
        wsServer.addServerCustomer(serverSocketChannel -> {
            logger.info("ServerSocketChannel Customer");
        });
        wsServer.addClientCustomer(socketChannel -> {
            logger.info("SocketChannel Customer");
        });
        wsServer.start();
    }
}
