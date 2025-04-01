package com.lufin.server.websocket.listener;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

	/**
	 * 클라이언트가 웹소켓에 연결될 때 호출됩니다.
	 *
	 * @param event 세션 연결 이벤트
	 */
	@EventListener
	public void handleWebSocketConnectListener(SessionConnectedEvent event) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
		Integer userId = (Integer) headerAccessor.getSessionAttributes().get("userId");
		log.info("💡Received a new web socket connection: {}, User ID: {}", headerAccessor.getSessionId(), userId);
	}

	/**
	 * 클라이언트의 웹소켓 연결이 해제될 때 호출됩니다.
	 *
	 * @param event 세션 연결 해제 이벤트
	 */
	@EventListener
	public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
		Integer userId = (Integer) headerAccessor.getSessionAttributes().get("userId");
		log.info("💡User disconnected: {}, User ID: {}", headerAccessor.getSessionId(), userId);

		// 추후 필요시 사용자 연결 해제에 따른 추가 처리 구현
		// 예: 사용자가 구독한 모든 클래스에서 구독 해제
	}
}
