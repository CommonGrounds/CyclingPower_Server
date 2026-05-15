package dev.java4now;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MyWebSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session); // Додај сесију у листу
        System.out.println("ConnectionEstablished: " + session.getUri());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session); // Уклони сесију из листе
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        System.out.println("Server Received: " + message.getPayload() + " - from: " + session.getUri());
    }

    // Метод за слање порука свим клијентима
    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                synchronized (session) {  // ← ključna izmena - Ako je Upload slike spor (velika slika → duže procesiranje), pa mora se ovo sinhronizirati:
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        sessions.remove(session); // ukloni pokvarenu sesiju
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}