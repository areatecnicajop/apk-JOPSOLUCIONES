package com.jop.sincronizador.webSockets;

import com.google.gson.JsonObject;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component // 🔥 Asegura que Spring maneje esta clase
public class WebSocket extends TextWebSocketHandler {

    private static final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }


    // 🔥 Método para enviar JSON válido
    public static void sendMessageToAll(String logMessage) {
        JsonObject json = new JsonObject();
        json.addProperty("message", logMessage);  // 🔥 Envuelve el mensaje en un JSON válido
    
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json.toString())); // 🔥 Enviar JSON
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
}
