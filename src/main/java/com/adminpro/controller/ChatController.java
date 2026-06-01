package com.adminpro.controller;

import com.adminpro.model.ChatGroup;
import com.adminpro.model.ChatMessage;
import com.adminpro.model.GroupReadState;
import com.adminpro.model.User;
import com.adminpro.repository.ChatGroupRepository;
import com.adminpro.repository.ChatMessageRepository;
import com.adminpro.repository.GroupReadStateRepository;
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
    private final ChatGroupRepository chatGroupRepository;
    private final GroupReadStateRepository groupReadStateRepository;
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

        if (with.startsWith("GROUP_")) {
            Long groupId = Long.parseLong(with.substring(6));
            ChatGroup group = chatGroupRepository.findById(groupId).orElse(null);
            if (group == null) return List.of();

            List<ChatMessage> messages = chatMessageRepository.findByGroupOrderByTimestampAsc(group);

            User user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user != null) {
                GroupReadState state = groupReadStateRepository.findByGroupAndUser(group, user)
                    .orElse(GroupReadState.builder().group(group).user(user).build());
                state.setLastReadTime(LocalDateTime.now());
                groupReadStateRepository.save(state);
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (ChatMessage m : messages) {
                result.add(messageToMap(m));
            }
            return result;
        }

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

    @PostMapping("/chat/grupo/crear")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createGroup(
            @RequestBody Map<String, Object> body, Authentication auth) {
        String name = (String) body.get("name");
        List<String> memberUsernames = (List<String>) body.get("members");

        if (name == null || name.isBlank() || memberUsernames == null || memberUsernames.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        User creator = userRepository.findByUsername(auth.getName()).orElse(null);
        if (creator == null) return ResponseEntity.status(401).build();

        ChatGroup group = ChatGroup.builder()
                .name(name)
                .createdBy(creator)
                .build();
        group.getMembers().add(creator);

        for (String username : memberUsernames) {
            userRepository.findByUsername(username).ifPresent(group.getMembers()::add);
        }

        chatGroupRepository.save(group);

        for (User member : group.getMembers()) {
            GroupReadState state = GroupReadState.builder()
                    .group(group)
                    .user(member)
                    .lastReadTime(LocalDateTime.now())
                    .build();
            groupReadStateRepository.save(state);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", group.getId());
        result.put("name", group.getName());

        for (User member : group.getMembers()) {
            broadcastConversationUpdates(member.getUsername());
        }

        return ResponseEntity.ok(result);
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
        String recipientUsername = (String) payload.get("recipientUsername");

        if (recipientUsername != null && recipientUsername.startsWith("GROUP_")) {
            Long groupId = Long.parseLong(recipientUsername.substring(6));
            ChatGroup group = chatGroupRepository.findById(groupId).orElse(null);
            if (group == null) return;

            payload.put("groupId", groupId);
            Map<String, Object> saved = handleSendMessage(payload, auth, "GROUP");

            messagingTemplate.convertAndSend("/topic/group/" + groupId, saved);

            for (User member : group.getMembers()) {
                broadcastConversationUpdates(member.getUsername());
            }
            return;
        }

        Map<String, Object> saved = handleSendMessage(payload, auth, "PRIVATE");

        String sender = auth != null ? auth.getName() : (String) payload.get("sender");
        messagingTemplate.convertAndSendToUser(recipientUsername, "/queue/private", saved);
        messagingTemplate.convertAndSendToUser(sender, "/queue/private", saved);

        broadcastConversationUpdates(sender);
        broadcastConversationUpdates(recipientUsername);
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public Map<String, Object> addUser(@Payload Map<String, Object> payload, Authentication auth) {
        String sender = auth != null ? auth.getName() : (String) payload.get("sender");
        ChatMessage chatMessage = ChatMessage.builder()
                .sender(sender)
                .senderFullName(getUserFullName(sender))
                .content("se ha conectado")
                .type("JOIN")
                .timestamp(LocalDateTime.now())
                .build();
        chatMessageRepository.save(chatMessage);
        onlineUsers.put(sender, LocalDateTime.now());
        Map<String, Object> result = messageToMap(chatMessage);
        result.put("onlineUsers", onlineUsers.keySet());
        return result;
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload Map<String, Object> payload, Authentication auth) {
        String recipient = (String) payload.get("recipientUsername");
        String sender = auth != null ? auth.getName() : (String) payload.get("sender");
        Boolean isTyping = (Boolean) payload.get("isTyping");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("sender", sender);
        event.put("senderFullName", sender != null ? getUserFullName(sender) : "");
        event.put("isTyping", isTyping != null && isTyping);

        if (recipient != null) {
            messagingTemplate.convertAndSendToUser(recipient, "/queue/typing", event);
        }
    }

    @MessageMapping("/chat.status")
    public void status(@Payload Map<String, Object> payload, Authentication auth) {
        String username = auth != null ? auth.getName() : (String) payload.get("sender");
        String type = (String) payload.get("statusType");
        if ("online".equals(type)) {
            onlineUsers.put(username, LocalDateTime.now());
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("username", username);
            event.put("isOnline", true);
            messagingTemplate.convertAndSend("/topic/presence", event);
        } else if ("offline".equals(type)) {
            onlineUsers.remove(username);
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("username", username);
            event.put("isOnline", false);
            messagingTemplate.convertAndSend("/topic/presence", event);
        }
    }

    // ========== METODOS PRIVADOS ==========

    private Map<String, Object> handleSendMessage(Map<String, Object> payload, Authentication auth, String type) {
        String sender = auth != null ? auth.getName() : (String) payload.get("sender");
        String content = (String) payload.get("content");
        String recipientUsername = (String) payload.get("recipientUsername");
        Long replyToId = payload.get("replyToId") != null ? Long.valueOf(payload.get("replyToId").toString()) : null;

        User user = userRepository.findByUsername(sender).orElse(null);
        String replyContent = null;
        String replySenderName = null;

        if (replyToId != null) {
            ChatMessage replied = chatMessageRepository.findById(replyToId).orElse(null);
            if (replied != null && !replied.isDeleted()) {
                replyContent = replied.getContent();
                replySenderName = replied.getSenderFullName();
            }
        }

        ChatGroup group = null;
        if ("GROUP".equals(type)) {
            Object groupIdObj = payload.get("groupId");
            if (groupIdObj != null) {
                Long groupId = Long.valueOf(groupIdObj.toString());
                group = chatGroupRepository.findById(groupId).orElse(null);
            }
        }

        ChatMessage chatMessage = ChatMessage.builder()
                .content(content)
                .sender(sender)
                .senderFullName(getUserFullName(sender))
                .senderProfilePhoto(user != null ? user.getProfilePhoto() : null)
                .type(type)
                .recipientUsername(recipientUsername)
                .group(group)
                .replyToId(replyToId)
                .replyToContent(replyContent)
                .replyToSenderName(replySenderName)
                .timestamp(LocalDateTime.now())
                .build();

        chatMessageRepository.save(chatMessage);
        return messageToMap(chatMessage);
    }

    private String getUserFullName(String username) {
        return userRepository.findByUsername(username)
                .map(u -> u.getFullName() != null ? u.getFullName() : u.getUsername())
                .orElse(username);
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
        map.put("groupId", m.getGroup() != null ? m.getGroup().getId() : null);
        return map;
    }

    private List<Map<String, Object>> buildConversationList(Authentication auth) {
        User currentUser = userRepository.findByUsername(auth.getName()).orElse(null);
        Set<String> partners = new LinkedHashSet<>();
        chatMessageRepository.findConversationPartners(auth.getName()).forEach(partners::add);

        List<Map<String, Object>> conversations = new ArrayList<>();

        // Private conversations
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
            conv.put("isGroup", false);

            if (lastMsg.isPresent()) {
                ChatMessage lm = lastMsg.get();
                conv.put("lastMessage", lm.isDeleted() ? "Mensaje eliminado" : lm.getContent());
                conv.put("lastMessageTime", lm.getTimestamp().toString());
                conv.put("lastMessageSender", lm.getSender());
            }
            conversations.add(conv);
        }

        // Group conversations
        if (currentUser != null) {
            List<ChatGroup> groups = chatGroupRepository.findByMember(currentUser);
            for (ChatGroup group : groups) {
                Map<String, Object> conv = new LinkedHashMap<>();
                conv.put("username", "GROUP_" + group.getId());
                conv.put("fullName", group.getName());
                conv.put("profilePhoto", null);
                conv.put("isGroup", true);
                conv.put("isOnline", false);

                Optional<ChatMessage> lastMsg = chatMessageRepository.findFirstByGroupOrderByTimestampDesc(group);
                long unread = 0;
                GroupReadState readState = groupReadStateRepository.findByGroupAndUser(group, currentUser).orElse(null);
                if (readState != null && readState.getLastReadTime() != null) {
                    unread = chatMessageRepository.countGroupUnreadSince(group, readState.getLastReadTime(), auth.getName());
                }
                conv.put("unreadCount", unread);

                if (lastMsg.isPresent()) {
                    ChatMessage lm = lastMsg.get();
                    String preview = lm.isDeleted() ? "Mensaje eliminado" : lm.getContent();
                    if (!lm.getSender().equals(auth.getName())) {
                        preview = lm.getSenderFullName() + ": " + preview;
                    }
                    conv.put("lastMessage", preview);
                    conv.put("lastMessageTime", lm.getTimestamp().toString());
                    conv.put("lastMessageSender", lm.getSender());
                }
                conversations.add(conv);
            }
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
