package com.easy.ws;

/**
 * @author Ray
 * @date created in 2021/8/1 16:12
 */
public interface WsCallback {

    void onOpen(WsContext context);

    WsPayload onMessage(WsContext context, WsPayload wsPayload);

    void onClose(WsContext context);

    default void onPing(WsContext context) { }
}
