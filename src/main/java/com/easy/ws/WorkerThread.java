package com.easy.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Ray
 * @date created in 2021/8/1 14:04
 */
public class WorkerThread extends Thread {

    public final Logger logger = LoggerFactory.getLogger(WorkerThread.class);
    public static final AtomicInteger COUNTER = new AtomicInteger();

    // 当前线程处理 SocketChannel 个数
    private final AtomicInteger wsCounter = new AtomicInteger();
    private Selector selector;
    private boolean running = true;
    private WsCallback wsCallback;
    private List<Plugin> plugins;

    public WorkerThread() {
        try {
            super.setName("worker-thread-" + COUNTER.getAndIncrement());
            this.selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected AtomicInteger getWsCounter() {
        return this.wsCounter;
    }

    public int getCurrentWsNum() {
        return this.wsCounter.get();
    }

    public void setWsCallback(WsCallback wsCallback) {
        this.wsCallback = wsCallback;
    }

    public void setPlugin(List<Plugin> plugins) {
        this.plugins = plugins;
    }

    public void register(SocketChannel socketChannel) {
        try {
            SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            if (logger.isDebugEnabled()) {
                try {
                    logger.debug("register {} to {} thread", socketChannel.getRemoteAddress().toString(),
                            this.getName());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            WsContextImpl wsContext = new WsContextImpl(this, socketChannel, wsCallback, plugins);
            selectionKey.attach(wsContext);

            // 回调
            this.wsCallback.onOpen(wsContext);
            // 统计 socketChannel 个数
            this.wsCounter.incrementAndGet();
        } catch (ClosedChannelException e) {
            logger.error("ws server died");
            e.printStackTrace();
        }
    }

    public Selector getSelector() {
        return this.selector;
    }

    public void close() {
        this.running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                // 不加上 timout，channel 注册之后无法读取消息
                this.selector.select(1000L);
                Iterator<SelectionKey> iterator = this.selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();

                    if (!selectionKey.isValid()) {
                        continue;
                    }

                    if (selectionKey.isReadable()) {
                        WsContext wsContext = (WsContext) selectionKey.attachment();
                        // 处理 ws body
                        wsContext.handlerPayload();
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
