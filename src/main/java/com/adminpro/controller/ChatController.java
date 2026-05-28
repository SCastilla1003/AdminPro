package com.adminpro.controller;

import com.adminpro.model.ChatMessage;
import com.adminpro.model.User;
import com.adminpro.repository.ChatMessageRepository;
import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/chat")
    public String chat(Model model, Authentication auth) {
        model.addAttribute("pageTitle", "Chat Corporativo");
        model.addAttribute("pageSubtitle", "Comunicación en tiempo real con el equipo");
        model.addAttribute("activePage", "chat");
        model.addAttribute("messages", chatMessageRepository.findTop50ByOrderByTimestampAsc());

        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        model.addAttribute("currentUser", user);

        List<User> allUsers = userRepository.findAll().stream()
            .filter(User::isEnabled)
            .collect(Collectors.toList());
        model.addAttribute("allUsers", allUsers);

        return "chat/index";
    }

    @GetMapping("/chat/privado")
    @ResponseBody
    public List<ChatMessage> getPrivateMessages(@RequestParam String with, Authentication auth) {
        return chatMessageRepository.findPrivateMessagesBetween(auth.getName(), with);
    }

    @GetMapping("/chat/usuarios")
    @ResponseBody
    public List<Map<String, Object>> getUsers(Authentication auth) {
        return userRepository.findAll().stream()
            .filter(User::isEnabled)
            .filter(u -> !u.getUsername().equals(auth.getName()))
            .map(u -> Map.<String, Object>of(
                "username", u.getUsername(),
                "fullName", u.getFullName() != null ? u.getFullName() : u.getUsername()
            ))
            .collect(Collectors.toList());
    }

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(ChatMessage chatMessage) {
        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessageRepository.save(chatMessage);
        return chatMessage;
    }

    @MessageMapping("/chat.sendPrivate")
    public void sendPrivateMessage(ChatMessage chatMessage) {
        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setType("PRIVATE");
        chatMessageRepository.save(chatMessage);

        messagingTemplate.convertAndSendToUser(
            chatMessage.getRecipientUsername(),
            "/queue/private",
            chatMessage
        );
        messagingTemplate.convertAndSendToUser(
            chatMessage.getSender(),
            "/queue/private",
            chatMessage
        );
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(ChatMessage chatMessage) {
        chatMessage.setTimestamp(LocalDateTime.now());
        chatMessage.setType("JOIN");
        return chatMessage;
    }
}
