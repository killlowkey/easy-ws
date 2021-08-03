package com.easy.ws;

/**
 * @author Ray
 * @date created in 2021/8/2 0:18
 */
public enum FrameType {

    CONTINUATION(0),
    TEXT(1),
    BINARY(2),
    NON_CONTROL(3),
    CLOSE_CONNECTION(8),
    PING(9),
    PONG(10),
    FURTHER_CONTROL(11);

    private final int opCode;

    FrameType(int opCode) {
        this.opCode = opCode;
    }

    public int getOpCode() {
        return this.opCode;
    }

    public static FrameType parseFrame(int opCode) {
        if (opCode == 0) {
            return CONTINUATION;
        } else if (opCode == 1) {
            return TEXT;
        } else if (opCode == 2) {
            return BINARY;
        } else if (opCode >= 3 && opCode <= 7) {
            return NON_CONTROL;
        } else if (opCode == 8) {
            return CLOSE_CONNECTION;
        } else if (opCode == 9) {
            return PING;
        } else if (opCode == 10) {
            return PONG;
        } else if (opCode >= 11 && opCode <= 15) {
            return FURTHER_CONTROL;
        } else {
            throw new RuntimeException(opCode + "unknown frame");
        }
    }
}
