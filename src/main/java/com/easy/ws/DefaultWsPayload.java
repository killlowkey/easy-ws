package com.easy.ws;

import java.util.Objects;

/**
 * @author Ray
 * @date created in 2021/8/2 11:30
 */
public class DefaultWsPayload implements WsPayload {

    private final FrameType frameType;
    private final byte[] data;

    public DefaultWsPayload(FrameType frameType, byte[] data) {
        Objects.requireNonNull(frameType, "frameType is empty");
        Objects.requireNonNull(data, "data is empty");

        this.frameType = frameType;
        this.data = data;
    }

    @Override
    public FrameType getFrameType() {
        return this.frameType;
    }

    @Override
    public byte[] getData() {
        return this.data;
    }

}
