# easy-ws
> 本项目对应的文章：[基于 Java NIO 实现 WebSocket 协议](https://killlowkey.github.io/2021/08/03/%E5%9F%BA%E4%BA%8E-Java-NIO-%E5%AE%9E%E7%8E%B0-WebSocket-%E5%8D%8F%E8%AE%AE/#more)

easy-ws 是基于 Java NIO 实现的 WebSocket 协议，项目架构采用 Netty 的 Boss-Worker 机制，实现一个高性能 WS 服务。

## 项目技术栈

1. JDK11
2. Maven

## easy-ws 特性
1. 采用主从线程处理机制：主线程接收客户端连接；从线程处理业务
2. 插件机制：用于拦截 WS 数据包和响应
3. 可定制化服务端（ServerSocketChannel）和客户端（SocketChannel）
4. 采用回调机制（WsCallback）处理客户端消息
5. 通过 WsContext 对连接进行处理

## 启动 WebSocket 服务
进入 src 目录，找到 WsServerExample 启动即可，然后通过 [WebSocket 在线测试工具](http://www.easyswoole.com/wstool.html)进行发送消息