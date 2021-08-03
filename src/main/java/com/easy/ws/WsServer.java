package com.easy.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web Socket Server
 *
 * @author Ray
 * @date created in 2021/8/1 13:08
 */
public class WsServer {

    public static final int WORKER_NUM = 10;
    private final Logger logger  = LoggerFactory.getLogger(WsServer.class);

    private final WorkerThread[] worker = new WorkerThread[WORKER_NUM];
    private final AtomicInteger count = new AtomicInteger();
    private final List<Plugin> wsPlugins = new ArrayList<>();
    private final List<ServerCustomer> serverCustomers = new ArrayList<>();
    private final List<ClientCustomer> clientCustomers = new ArrayList<>();
    private boolean running = true;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private WsCallback wsCallback;
    private final int port;

    public WsServer(int port) {
        this.port = port;
    }


    public void initServer() {
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);

            // call ServerCustomer
            serverCustomers.forEach(serverCustomer -> serverCustomer.customer(serverSocketChannel));

            // bind listener port
            serverSocketChannel.socket().bind(new InetSocketAddress(port));
            // register selector
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            for (int i = 0; i < WORKER_NUM; i++) {
                WorkerThread workerThread = new WorkerThread();
                worker[i] = workerThread;
                workerThread.setWsCallback(wsCallback);
                workerThread.setPlugin(wsPlugins);
                workerThread.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addPlugin(Plugin plugin) {
        this.wsPlugins.add(plugin);
    }

    public void addServerCustomer(ServerCustomer serverCustomer) {
        this.serverCustomers.add(serverCustomer);
    }

    public void addClientCustomer(ClientCustomer clientCustomer) {
        this.clientCustomers.add(clientCustomer);
    }

    private void registerSocketChannel(SocketChannel socketChannel) {
        this.worker[count.getAndIncrement() % WORKER_NUM].register(socketChannel);
    }

    public void close() {
        this.running = false;
        try {
            for (int i = 0; i < WORKER_NUM; i++) {
                worker[i].close();
            }
            serverSocketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() throws IOException {

        // init ServerSocketChannel、WorkerThread
        initServer();

        logger.info("start webSocket service success");

        while (running) {
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                iterator.remove();

                if (!selectionKey.isValid()) {
                    continue;
                }

                if (selectionKey.isAcceptable()) {
                    // accept connection from client
                    ServerSocketChannel ssc = (ServerSocketChannel) selectionKey.channel();
                    SocketChannel socketChannel = ssc.accept();
                    socketChannel.configureBlocking(false);
                    socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, Boolean.TRUE);

                    clientCustomers.forEach(clientCustomer -> clientCustomer.customer(socketChannel));

                    // handler ws handshake packet
                    if (handlerHandshake(socketChannel)) {
                        registerSocketChannel(socketChannel);
                    }
                }
            }

        }
    }

    private boolean handlerHandshake(SocketChannel socketChannel) {
        ByteBuffer data = ByteBuffer.allocate(1024);
        Pattern pattern = Pattern.compile("Sec-WebSocket-Key: (.*)");

        try {
            socketChannel.read(data);
            // obtain Sec-WebSocket-Key value from request header
            Matcher matcher = pattern.matcher(new String(data.array(), StandardCharsets.UTF_8));
            if (matcher.find()) {
                String key = matcher.group(1).trim();
                // https://blog.51cto.com/shuxiayeshou/1762152
                // https://blog.csdn.net/weixin_34080951/article/details/91915908
                String response = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: "
                        + "websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                        + getSecWebSocketAccept(key) + "\r\n\r\n";
                // write handshake packet
                socketChannel.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
            } else {
                logger.warn(socketChannel.getRemoteAddress() + " not found ws handshake");
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            try {
                socketChannel.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            return false;
        } finally {
            data.clear();
        }

        return true;
    }


    // 服务端先获取 Sec-WebSocket-Key 请求头的值，之后在该值后加上 GUID
    // 然后对该字符串进行 SHA1 加密，得到一个 byte 数组，最终将 byte 数组进行base64加密即可
    private String getSecWebSocketAccept(String key) throws NoSuchAlgorithmException {
        String guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        key += guid;
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(key.getBytes(StandardCharsets.UTF_8), 0, key.length());
        byte[] shaHash = md.digest();
        return Base64.getEncoder().encodeToString(shaHash);
    }

    public void setWsCallback(WsCallback wsCallback) {
        this.wsCallback = wsCallback;
    }

}
