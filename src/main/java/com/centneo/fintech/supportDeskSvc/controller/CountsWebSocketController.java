package com.centneo.fintech.supportDeskSvc.controller;

import com.centneo.fintech.supportDeskSvc.dto.MenuCountDto;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/counts")
public class CountsWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    // simulate broadcast (can be triggered by service when DB updates)
    @PostMapping("/broadcast/{username}")
    public void broadcastCounts(@PathVariable String username, @RequestBody MenuCountDto counts) {
        messagingTemplate.convertAndSend("/topic/counts/" + username, counts);
    }

    // optional STOMP entrypoint if clients send messages
    @MessageMapping("/update-counts")
    public void receiveUpdate(MenuCountDto counts) {
        // handle incoming client messages if needed
    }
}
