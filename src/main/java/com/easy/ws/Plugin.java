package com.easy.ws;

/**
 * easy-ws 插件拦截请求和响应
 *
 * @author Ray
 * @date created in 2021/8/1 18:44
 */
public interface Plugin {

    WsPayload interceptRequest(WsPayload wsPayload);

    WsPayload interceptResponse(WsPayload wsPayload);

}
