package com.easy.ws;

/**
 * @author Ray
 * @date created in 2021/8/1 23:41
 */
public interface WsPayload {

    FrameType getFrameType();

    byte[] getData();

    default long getDataLength() {
        return getData().length;
    }

}
