package com.adminpro.controller;

import com.adminpro.model.ChatMessage;
import com.adminpro.model.User;
import com.adminpro.repository.ChatMessageRepository;
import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, LocalDateTime> onlineUsers = new ConcurrentHashMap<>();
    private final Map<String, String> typingUsers = new ConcurrentHashMap<>();

    // ========== PAGINA PRINCIPAL ==========

    @GetMapping("/chat")
    public String chat(Model model, Authentication auth) {
        model.addAttribute("pageTitle", "Chat Corporativo");
        model.addAttribute("pageSubtitle", "Comunicacion en tiempo real con el equipo");
        model.addAttribute("activePage", "chat");

        List<ChatMessage> publicMessages = chatMessageRepository.findRecentPublicMessages();
        Collections.reverse(publicMessages);
        model.addAttribute("messages", publicMessages);

        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        model.addAttribute("currentUser", user);

        List<Map<String, Object>> conversations = buildConversationList(auth);
        model.addAttribute("conversations", conversations);

        List<User> allUsers = userRepository.findAll().stream()
                .filter(u -> u.isEnabled() && !u.getUsername().equals(auth.getName()))
                .collect(Collectors.toList());
        model.addAttribute("allUsers", allUsers);

        onlineUsers.put(auth.getName(), LocalDateTime.now());

        return "chat/index";
    }

    // ========== CONVERSACIONES ==========

    @GetMapping("/chat/privado")
    @ResponseBody
    public List<Map<String, Object>> getPrivateMessages(
            @RequestParam String with, Authentication auth) {
        List<ChatMessage> messages = chatMessageRepository.findPrivateMessagesBetween(auth.getName(), with);
        chatMessageRepository.markPrivateMessagesAsRead(auth.getName(), with);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage m : messages) {
            result.add(messageToMap(m));
        }
        return result;
    }

    @GetMapping("/chat/usuarios")
    @ResponseBody
    public List<Map<String, Object>> getUsers(Authentication auth) {
        return userRepository.findAll().stream()
                .filter(u -> u.isEnabled() && !u.getUsername().equals(auth.getName()))
                .map(u -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("username", u.getUsername());
                    map.put("fullName", u.getFullName() != null ? u.getFullName() : u.getUsername());
                    map.put("profilePhoto", u.getProfilePhoto());
                    map.put("email", u.getEmail());
                    map.put("isOnline", onlineUsers.containsKey(u.getUsername()));
                    return map;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/chat/conversaciones")
    @ResponseBody
    public List<Map<String, Object>> getConversations(Authentication auth) {
        return buildConversationList(auth);
    }

    @GetMapping("/chat/search")
    @ResponseBody
    public List<Map<String, Object>> searchMessages(
            @RequestParam String q, Authentication auth) {
        return chatMessageRepository.searchMessages(auth.getName(), q)
                .stream()
                .map(this::messageToMap)
                .collect(Collectors.toList());
    }

    @GetMapping("/chat/starred")
    @ResponseBody
    public List<Map<String, Object>> getStarredMessages(Authentication auth) {
        return chatMessageRepository.findStarredMessages(auth.getName())
                .stream()
                .map(this::messageToMap)
                .collect(Collectors.toList());
    }

    // ========== ACCIONES DE MENSAJES ==========

    @PostMapping("/chat/message/{id}/star")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleStar(
            @PathVariable Long id, Authentication auth) {
        return chatMessageRepository.findById(id)
                .map(m -> {
                    m.setStarred(!m.isStarred());
                    chatMessageRepository.save(m);
                    return ResponseEntity.ok(messageToMap(m));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/chat/message/{id}/react")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> react(
            @PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        return chatMessageRepository.findById(id)
                .map(m -> {
                    String emoji = body.get("reaction");
                    if (emoji != null && !emoji.isEmpty()) {
                        m.setReaction(emoji);
                    } else {
                        m.setReaction(null);
                    }
                    chatMessageRepository.save(m);

                    Map<String, Object> payload = messageToMap(m);
                    payload.put("action", "reaction");
                    if (m.getType() != null && m.getType().equals("PRIVATE")) {
                        messagingTemplate.convertAndSendToUser(m.getSender(), "/queue/chat-updates", payload);
                        if (m.getRecipientUsername() != null) {
                            messagingTemplate.convertAndSendToUser(m.getRecipientUsername(), "/queue/chat-updates", payload);
                        }
                    } else {
                        messagingTemplate.convertAndSend("/topic/chat-updates", payload);
                    }
                    return ResponseEntity.ok(messageToMap(m));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/chat/message/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> editMessage(
            @PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        return chatMessageRepository.findById(id)
                .filter(m -> m.getSender().equals(auth.getName()) && !m.isDeleted())
                .map(m -> {
                    m.setContent(body.get("content"));
                    m.setEdited(true);
                    chatMessageRepository.save(m);

                    Map<String, Object> payload = messageToMap(m);
                    payload.put("action", "edited");
                    if (m.getType() != null && m.getType().equals("PRIVATE")) {
                        messagingTemplate.convertAndSendToUser(m.getSender(), "/queue/chat-updates", payload);
                        if (m.getRecipientUsername() != null) {
                            messagingTemplate.convertAndSendToUser(m.getRecipientUsername(), "/queue/chat-updates", payload);
                        }
                    } else {
                        messagingTemplate.convertAndSend("/topic/chat-updates", payload);
                    }
                    return ResponseEntity.ok(messageToMap(m));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @DeleteMapping("/chat/message/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteMessage(
            @PathVariable Long id, Authentication auth) {
        return chatMessageRepository.findById(id)
                .filter(m -> m.getSender().equals(auth.getName()))
                .map(m -> {
                    m.setDeleted(true);
                    m.setContent("Este mensaje fue eliminado");
                    m.setReaction(null);
                    chatMessageRepository.save(m);

                    Map<String, Object> payload = messageToMap(m);
                    payload.put("action", "deleted");
                    if (m.getType() != null && m.getType().equals("PRIVATE")) {
                        messagingTemplate.convertAndSendToUser(m.getSender(), "/queue/chat-updates", payload);
                        if (m.getRecipientUsername() != null) {
                            messagingTemplate.convertAndSendToUser(m.getRecipientUsername(), "/queue/chat-updates", payload);
                        }
                    } else {
                        messagingTemplate.convertAndSend("/topic/chat-updates", payload);
                    }
                    return ResponseEntity.ok(payload);
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PostMapping("/chat/mark-read")
    @ResponseBody
    public ResponseEntity<Void> markAsRead(
            @RequestBody Map<String, String> body, Authentication auth) {
        String partner = body.get("partner");
        if (partner != null) {
            chatMessageRepository.markPrivateMessagesAsRead(auth.getName(), partner);
        }
        return ResponseEntity.ok().build();
    }

    // ========== STOMP WEBSOCKET ==========

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public Map<String, Object> sendMessage(@Payload Map<String, Object> payload, Authentication auth) {
        return handleSendMessage(payload, auth, "CHAT");
    }

    @MessageMapping("/chat.sendPrivate")
    public void sendPrivateMessage(@Payload Map<String, Object> payload, Authentication auth) {
        Map<String, Object> saved = handleSendMessage(payload, auth, "PRIVATE");
        String recipientUsername = (String) payload.get("recipientUsername");

        messagingTemplate.convertAndSendToUser(recipientUsername, "/queue/private", saved);
        messagingTemplate.convertAndSendToUser(auth.getName(), "/queue/private", saved);

        broadcastConversationUpdates(auth.getName());
        broadcastConversationUpdates(recipientUsername);
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public Map<String, Object> addUser(@Payload Map<String, Object> payload, Authentication auth) {
        ChatMessage chatMessage = ChatMessage.builder()
                .sender(auth.getName())
                .senderFullName(getUserFullName(auth))
                .content("se ha conectado")
                .type("JOIN")
                .timestamp(LocalDateTime.now())
                .build();
        chatMessageRepository.save(chatMessage);
        onlineUsers.put(auth.getName(), LocalDateTime.now());
        Map<String, Object> result = messageToMap(chatMessage);
        result.put("onlineUsers", onlineUsers.keySet());
        return result;
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload Map<String, Object> payload, Authentication auth) {
        String recipient = (String) payload.get("recipientUsername");
        Boolean isTyping = (Boolean) payload.get("isTyping");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("sender", auth.getName());
        event.put("senderFullName", getUserFullName(auth));
        event.put("isTyping", isTyping != null && isTyping);

        if (recipient != null) {
            messagingTemplate.convertAndSendToUser(recipient, "/queue/typing", event);
        }
    }

    @MessageMapping("/chat.status")
    public void status(@Payload Map<String, Object> payload, Authentication auth) {
        String type = (String) payload.get("statusType");
        if ("online".equals(type)) {
            onlineUsers.put(auth.getName(), LocalDateTime.now());
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("username", auth.getName());
            event.put("isOnline", true);
            messagingTemplate.convertAndSend("/topic/presence", event);
        } else if ("offline".equals(type)) {
            onlineUsers.remove(auth.getName());
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("username", auth.getName());
            event.put("isOnline", false);
            messagingTemplate.convertAndSend("/topic/presence", event);
        }
    }

    // ========== METODOS PRIVADOS ==========

    private Map<String, Object> handleSendMessage(Map<String, Object> payload, Authentication auth, String type) {
        String content = (String) payload.get("content");
        String recipientUsername = (String) payload.get("recipientUsername");
        Long replyToId = payload.get("replyToId") != null ? Long.valueOf(payload.get("replyToId").toString()) : null;

        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        String replyContent = null;
        String replySenderName = null;

        if (replyToId != null) {
            ChatMessage replied = chatMessageRepository.findById(replyToId).orElse(null);
            if (replied != null && !replied.isDeleted()) {
                replyContent = replied.getContent();
                replySenderName = replied.getSenderFullName();
            }
        }

        ChatMessage chatMessage = ChatMessage.builder()
                .content(content)
                .sender(auth.getName())
                .senderFullName(getUserFullName(auth))
                .senderProfilePhoto(user != null ? user.getProfilePhoto() : null)
                .type(type)
                .recipientUsername(recipientUsername)
                .replyToId(replyToId)
                .replyToContent(replyContent)
                .replyToSenderName(replySenderName)
                .timestamp(LocalDateTime.now())
                .build();

        chatMessageRepository.save(chatMessage);
        return messageToMap(chatMessage);
    }

    private String getUserFullName(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .map(u -> u.getFullName() != null ? u.getFullName() : u.getUsername())
                .orElse(auth.getName());
    }

    private Map<String, Object> messageToMap(ChatMessage m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("content", m.getContent());
        map.put("sender", m.getSender());
        map.put("senderFullName", m.getSenderFullName());
        map.put("senderProfilePhoto", m.getSenderProfilePhoto());
        map.put("timestamp", m.getTimestamp() != null ? m.getTimestamp().toString() : null);
        map.put("type", m.getType());
        map.put("recipientUsername", m.getRecipientUsername());
        map.put("isRead", m.isRead());
        map.put("isEdited", m.isEdited());
        map.put("isDeleted", m.isDeleted());
        map.put("isStarred", m.isStarred());
        map.put("replyToId", m.getReplyToId());
        map.put("replyToContent", m.getReplyToContent());
        map.put("replyToSenderName", m.getReplyToSenderName());
        map.put("reaction", m.getReaction());
        return map;
    }

    private List<Map<String, Object>> buildConversationList(Authentication auth) {
        Set<String> partners = new LinkedHashSet<>();
        chatMessageRepository.findConversationPartners(auth.getName()).forEach(partners::add);

        List<Map<String, Object>> conversations = new ArrayList<>();
        for (String partner : partners) {
            Optional<ChatMessage> lastMsg = chatMessageRepository.findLastPrivateMessage(auth.getName(), partner);
            long unread = chatMessageRepository.countUnreadFrom(auth.getName(), partner);
            User partnerUser = userRepository.findByUsername(partner).orElse(null);

            Map<String, Object> conv = new LinkedHashMap<>();
            conv.put("username", partner);
            conv.put("fullName", partnerUser != null && partnerUser.getFullName() != null
                    ? partnerUser.getFullName() : partner);
            conv.put("profilePhoto", partnerUser != null ? partnerUser.getProfilePhoto() : null);
            conv.put("isOnline", onlineUsers.containsKey(partner));
            conv.put("unreadCount", unread);
            conv.put("isPinned", false);

            if (lastMsg.isPresent()) {
                ChatMessage lm = lastMsg.get();
                conv.put("lastMessage", lm.isDeleted() ? "Mensaje eliminado" : lm.getContent());
                conv.put("lastMessageTime", lm.getTimestamp().toString());
                conv.put("lastMessageSender", lm.getSender());
            }
            conversations.add(conv);
        }

        conversations.sort((a, b) -> {
            Object ta = a.get("lastMessageTime");
            Object tb = b.get("lastMessageTime");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return ((String) tb).compareTo((String) ta);
        });

        return conversations;
    }

    private void broadcastConversationUpdates(String username) {
        messagingTemplate.convertAndSendToUser(username, "/queue/conversation-update", Collections.singletonMap("update", true));
    }
}
