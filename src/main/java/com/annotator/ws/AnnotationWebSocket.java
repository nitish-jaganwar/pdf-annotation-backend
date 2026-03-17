package com.annotator.ws;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

// URL banega: ws://localhost:8080/AapkaProjectName/ws-annotator/DOC-123
@ServerEndpoint("/ws-annotator/{docId}")
public class AnnotationWebSocket {

    private static final ConcurrentHashMap<String, Set<Session>> rooms = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("docId") String docId) {
        rooms.computeIfAbsent(docId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(session);
        System.out.println("✅ User joined Room: " + docId);
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("docId") String docId) {
        // Ek user se JSON aaya, baaki sabko forward kar do
        Set<Session> room = rooms.get(docId);
        if (room != null) {
            for (Session s : room) {
                if (s.isOpen() && !s.getId().equals(session.getId())) {
                    try {
                        s.getBasicRemote().sendText(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("docId") String docId) {
        Set<Session> room = rooms.get(docId);
        if (room != null) {
            room.remove(session);
            if (room.isEmpty()) rooms.remove(docId);
        }
    }
}
