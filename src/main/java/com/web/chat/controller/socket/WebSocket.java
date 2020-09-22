package com.web.chat.controller.socket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Grayxk
 * @date 2020/9/22 9:11 上午
 */
@Slf4j
@Component
@ServerEndpoint("/websocket/{username}")
public class WebSocket {

    /**
     * 在线人数
     */
    private static int onlineNumber = 0;
    /**
     * 以用户的姓名为key，WebSocket为对象保存起来
     */
    private static Map<String, WebSocket> clients = new ConcurrentHashMap<>();
    /**
     * 会话
     */
    private Session session;
    /**
     * 用户名称
     */
    private String username;

    /**
     * 建立连接
     *
     * @param username 用户名
     * @param session  会话
     */
    @OnOpen
    public void onOpen(@PathParam("username") String username, Session session) {
        onlineNumber++;
        log.info("现在来连接的客户id：{}，用户名：{}", session.getId(), username);
        this.session = session;
        this.username = username;
        log.info("有新连接加入！ 当前在线人数：{}", onlineNumber);
        try {
            //messageType 1代表上线 2代表下线 3代表在线名单 4代表普通消息
            //先给所有人发送通知，说我上线了
            Map<String, Object> map1 = new HashMap<>();
            map1.put("messageType", 1);
            map1.put("username", username);
            sendMessageAll(JSON.toJSONString(map1), username);

            //把自己的信息加入到map当中去
            clients.put(username, this);
            //给自己发一条消息：告诉自己现在都有谁在线
            Map<String, Object> map2 = new HashMap<>();
            map2.put("messageType", 3);
            //移除掉自己
            Set<String> set = clients.keySet();
            map2.put("onlineUsers", set);
            sendMessageTo(JSON.toJSONString(map2), username);
        } catch (IOException e) {
            log.error("用户：{}，上线的时候通知所有人发生了错误", username);
        }
    }

    /**
     * 连接发生错误
     *
     * @param session 会话
     * @param error   错误异常
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("服务端发生了错误" + error.getMessage());
        //error.printStackTrace();
    }

    /**
     * 连接关闭
     */
    @OnClose
    public void onClose() {
        onlineNumber--;
        //webSockets.remove(this);
        clients.remove(username);
        try {
            //messageType 1代表上线 2代表下线 3代表在线名单  4代表普通消息
            Map<String, Object> map1 = new HashMap<>();
            map1.put("messageType", 2);
            map1.put("onlineUsers", clients.keySet());
            map1.put("username", username);
            sendMessageAll(JSON.toJSONString(map1), username);

        } catch (IOException e) {
            log.error("用户：{}，下线的时候通知所有人发生了错误", username);
        }
        log.info("有连接关闭！ 当前在线人数：{}", onlineNumber);
    }

    /**
     * 接收消息
     *
     * @param message 消息，json的字符串
     * @param session 会话
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            log.info("来自客户端消息：{} 客户端的id是：{}", message, session.getId());
            JSONObject jsonObject = JSON.parseObject(message);
            String textMessage = jsonObject.getString("message");
            String fromUsername = jsonObject.getString("username");
            String toUsername = jsonObject.getString("to");
            //如果不是发给所有，那么就发给某一个人
            //messageType 1代表上线 2代表下线 3代表在线名单 4代表普通消息
            Map<String, Object> map1 = new HashMap<>();
            map1.put("messageType", 4);
            map1.put("textMessage", textMessage);
            map1.put("fromUsername", fromUsername);
            if (toUsername.equals("All")) {
                map1.put("toUsername", "所有人");
                sendMessageAll(JSON.toJSONString(map1), fromUsername);
            } else {
                map1.put("toUsername", toUsername);
                sendMessageTo(JSON.toJSONString(map1), toUsername);
            }
        } catch (Exception e) {
            log.info("发生了错误了");
        }
    }

    public void sendMessageTo(String message, String ToUserName) throws IOException {
        for (WebSocket item : clients.values()) {
            if (item.username.equals(ToUserName)) {
                item.session.getAsyncRemote().sendText(message);
                break;
            }
        }
    }

    public void sendMessageAll(String message, String ToUserName) throws IOException {
        for (WebSocket item : clients.values()) {
            item.session.getAsyncRemote().sendText(message);
        }
    }

    public static synchronized int getOnlineCount() {
        return onlineNumber;
    }

}
