function whatsappChat() {
    return {
        username: '',
        fullName: '',
        stompClient: null,
        conversations: [],
        allUsers: [],
        filteredConversations: [],
        filteredUsers: [],
        dynamicMessages: [],
        selectedUser: null,
        newMessage: '',
        replyTo: null,
        searchQuery: '',
        newChatSearch: '',
        typingIndicator: false,
        typingTimeout: null,
        showNewChat: false,
        showStarred: false,
        showContactInfo: false,
        showEmoji: false,
        starredMessages: [],
        soundEnabled: true,
        messageMenuOpen: -1,
        contextMessage: null,
        menuStyle: {},

        showNewGroupModal: false,
        newGroupName: '',
        selectedGroupMembers: [],
        groupSubscriptions: {},

        initChat(el) {
            this.username = el.dataset.username || '';
            this.fullName = el.dataset.fullname || '';
            this.fetchConversations();
            this.fetchAllUsers();
            this.connectWebSocket();
            this.scrollToBottom();

            window.addEventListener('beforeunload', () => {
                if (this.stompClient) {
                    this.stompClient.send('/app/chat.status', {}, JSON.stringify({statusType: 'offline'}));
                }
            });
        },

        // ========== CONVERSATIONS ==========

        async fetchConversations() {
            try {
                const res = await fetch('/chat/conversaciones');
                if (res.ok) {
                    this.conversations = await res.json();
                    this.filteredConversations = [...this.conversations];
                }
            } catch(e) { console.error('Error loading conversations:', e); }
        },

        async fetchAllUsers() {
            try {
                const res = await fetch('/chat/usuarios');
                if (res.ok) {
                    this.allUsers = await res.json();
                    this.filteredUsers = [...this.allUsers];
                }
            } catch(e) { console.error('Error loading users:', e); }
        },

        filterConversations() {
            if (!this.searchQuery) {
                this.filteredConversations = [...this.conversations];
                return;
            }
            const q = this.searchQuery.toLowerCase();
            this.filteredConversations = this.conversations.filter(c =>
                c.fullName.toLowerCase().includes(q)
            );
        },

        filterUsers() {
            if (!this.newChatSearch) {
                this.filteredUsers = [...this.allUsers];
                return;
            }
            const q = this.newChatSearch.toLowerCase();
            this.filteredUsers = this.allUsers.filter(u =>
                u.fullName.toLowerCase().includes(q) ||
                (u.email && u.email.toLowerCase().includes(q))
            );
        },

        // ========== GROUPS ==========

        async createGroup() {
            if (!this.newGroupName.trim() || this.selectedGroupMembers.length === 0) return;
            try {
                const csrf = document.querySelector('meta[name="_csrf"]').content;
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
                const res = await fetch('/chat/grupo/crear', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', [csrfHeader]: csrf },
                    body: JSON.stringify({ name: this.newGroupName.trim(), members: this.selectedGroupMembers })
                });
                if (res.ok) {
                    this.showNewGroupModal = false;
                    this.newGroupName = '';
                    this.selectedGroupMembers = [];
                    await this.fetchConversations();
                }
            } catch(e) { console.error('Error creating group:', e); }
        },

        toggleGroupMember(username) {
            const idx = this.selectedGroupMembers.indexOf(username);
            if (idx >= 0) {
                this.selectedGroupMembers.splice(idx, 1);
            } else {
                this.selectedGroupMembers.push(username);
            }
        },

        // ========== CHAT NAVIGATION ==========

        async openConversation(conv) {
            this.selectedUser = conv;
            this.dynamicMessages = [];
            this.replyTo = null;
            this.messageMenuOpen = -1;
            this.showStarred = false;
            this.showContactInfo = false;

            try {
                const res = await fetch('/chat/privado?with=' + conv.username);
                if (res.ok) {
                    this.dynamicMessages = await res.json();
                }
            } catch(e) { console.error('Error loading messages:', e); }

            if (conv.isGroup && this.stompClient) {
                const groupId = conv.username.replace('GROUP_', '');
                if (!this.groupSubscriptions[groupId]) {
                    this.groupSubscriptions[groupId] = this.stompClient.subscribe('/topic/group/' + groupId, (payload) => {
                        const msg = JSON.parse(payload.body);
                        if (this.selectedUser && this.selectedUser.username === 'GROUP_' + msg.groupId) {
                            this.dynamicMessages.push(msg);
                            this.$nextTick(() => this.scrollToBottom());
                        }
                        this.refreshConversationList();
                        if (msg.sender !== this.username && this.soundEnabled) {
                            this.playNotificationSound();
                        }
                    });
                }
            }

            await this.markAsRead();
            await this.$nextTick();
            this.scrollToBottom();
            this.refreshConversationList();
        },

        closeConversation() {
            this.selectedUser = null;
            this.dynamicMessages = [];
            this.replyTo = null;
        },

        // ========== MESSAGES ==========

        async sendMessage() {
            const text = this.newMessage.trim();
            if (!text || !this.stompClient) return;
            if (!this.selectedUser) return;

            const payload = {
                content: text,
                sender: this.username,
                senderFullName: this.fullName,
                type: 'PRIVATE',
                recipientUsername: this.selectedUser.username,
                replyToId: this.replyTo ? this.replyTo.id : null
            };

            this.stompClient.send('/app/chat.sendPrivate', {}, JSON.stringify(payload));
            this.newMessage = '';
            this.replyTo = null;

            const textarea = document.querySelector('textarea[x-model="newMessage"]');
            if (textarea) {
                textarea.style.height = 'auto';
            }
        },

        handleKeydown(event) {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                this.sendMessage();
            }
            // Auto-resize
            const el = event.target;
            setTimeout(() => {
                el.style.height = 'auto';
                el.style.height = el.scrollHeight + 'px';
            }, 0);
        },

        handleTyping() {
            if (!this.stompClient || !this.selectedUser) return;

            this.stompClient.send('/app/chat.typing', {}, JSON.stringify({
                recipientUsername: this.selectedUser.username,
                isTyping: true
            }));

            if (this.typingTimeout) clearTimeout(this.typingTimeout);
            this.typingTimeout = setTimeout(() => {
                this.stompClient.send('/app/chat.typing', {}, JSON.stringify({
                    recipientUsername: this.selectedUser.username,
                    isTyping: false
                }));
            }, 2000);
        },

        async markAsRead() {
            if (!this.selectedUser) return;
            try {
                const csrf = document.querySelector('meta[name="_csrf"]').content;
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
                await fetch('/chat/mark-read', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json', [csrfHeader]: csrf},
                    body: JSON.stringify({partner: this.selectedUser.username})
                });
            } catch(e) {}
        },

        async refreshConversationList() {
            await this.fetchConversations();
        },

        // ========== MESSAGE ACTIONS ==========

        replyAction() {
            this.replyTo = this.contextMessage;
        },

        async toggleStar(msgId) {
            msgId = msgId || (this.contextMessage ? this.contextMessage.id : null);
            if (!msgId) return;
            try {
                await fetch('/chat/message/' + msgId + '/star', {method: 'POST'});
                this.refreshMessages();
            } catch(e) { console.error(e); }
        },

        async editAction() {
            if (!this.contextMessage || this.contextMessage.sender !== this.username) return;
            const newContent = prompt('Editar mensaje:', this.contextMessage.content);
            if (newContent && newContent.trim()) {
                try {
                    await fetch('/chat/message/' + this.contextMessage.id, {
                        method: 'PUT',
                        headers: {'Content-Type': 'application/json'},
                        body: JSON.stringify({content: newContent})
                    });
                } catch(e) { console.error(e); }
            }
        },

        async deleteAction() {
            if (!this.contextMessage || this.contextMessage.sender !== this.username) return;
            if (!confirm('¿Eliminar este mensaje?')) return;
            try {
                await fetch('/chat/message/' + this.contextMessage.id, {method: 'DELETE'});
            } catch(e) { console.error(e); }
        },

        async reactToMessage(msg, emoji) {
            const id = msg ? msg.id : (this.contextMessage ? this.contextMessage.id : null);
            if (!id) return;
            try {
                await fetch('/chat/message/' + id + '/react', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({reaction: emoji || ''})
                });
            } catch(e) { console.error(e); }
        },

        async loadStarred() {
            try {
                const res = await fetch('/chat/starred');
                if (res.ok) this.starredMessages = await res.json();
            } catch(e) { console.error(e); }
        },

        scrollToMessage(id) {
            const el = document.querySelector('[data-msg-id="' + id + '"]');
            if (el) el.scrollIntoView({behavior: 'smooth', block: 'center'});
        },

        // ========== MESSAGE MENU ==========

        openMessageMenu(event, msg) {
            this.contextMessage = msg;
            this.messageMenuOpen = msg.id;
            this.menuStyle = {
                top: Math.min(event.clientY, window.innerHeight - 350) + 'px',
                left: Math.min(event.clientX, window.innerWidth - 200) + 'px'
            };
        },

        // ========== WEBSOCKET ==========

        connectWebSocket() {
            const socket = new SockJS('/ws-chat');
            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = null;

            this.stompClient.connect({login: this.username}, (frame) => {
                this.stompClient.send('/app/chat.addUser', {}, JSON.stringify({
                    sender: this.username,
                    senderFullName: this.fullName,
                    type: 'JOIN'
                }));

                // Public messages
                this.stompClient.subscribe('/topic/public', (payload) => {
                    const msg = JSON.parse(payload.body);
                    if (msg.type === 'CHAT' || msg.type === 'JOIN') {
                        this.dynamicMessages.push(msg);
                        if (!this.selectedUser) this.$nextTick(() => this.scrollToBottom());
                    }
                });

                // Private messages
                this.stompClient.subscribe('/user/queue/private', (payload) => {
                    const msg = JSON.parse(payload.body);
                    if (this.selectedUser && (msg.sender === this.selectedUser.username || msg.recipientUsername === this.selectedUser.username)) {
                        this.dynamicMessages.push(msg);
                        this.$nextTick(() => this.scrollToBottom());
                    }
                    this.refreshConversationList();
                    if (msg.sender !== this.username && this.soundEnabled) {
                        this.playNotificationSound();
                    }
                });

                // Chat updates (edit, delete, reaction)
                this.stompClient.subscribe('/user/queue/chat-updates', (payload) => {
                    const update = JSON.parse(payload.body);
                    if (!this.selectedUser) return;
                    const action = update.action;
                    const idx = this.dynamicMessages.findIndex(m => m.id === update.id);

                    if (action === 'deleted' || action === 'edited') {
                        if (idx >= 0) this.dynamicMessages[idx] = update;
                    } else if (action === 'reaction') {
                        if (idx >= 0) this.dynamicMessages[idx].reaction = update.reaction;
                    }
                });

                // Typing indicator
                this.stompClient.subscribe('/user/queue/typing', (payload) => {
                    const data = JSON.parse(payload.body);
                    if (this.selectedUser && data.sender === this.selectedUser.username) {
                        this.typingIndicator = data.isTyping;
                        if (data.isTyping) {
                            this.$nextTick(() => this.scrollToBottom());
                        }
                    }
                });

                // Presence updates
                this.stompClient.subscribe('/topic/presence', (payload) => {
                    const data = JSON.parse(payload.body);
                    this.conversations.forEach(c => {
                        if (c.username === data.username) c.isOnline = data.isOnline;
                    });
                    this.allUsers.forEach(u => {
                        if (u.username === data.username) u.isOnline = data.isOnline;
                    });
                    if (this.selectedUser && this.selectedUser.username === data.username) {
                        this.selectedUser.isOnline = data.isOnline;
                    }
                });

                // Conversation updates
                this.stompClient.subscribe('/user/queue/conversation-update', () => {
                    this.refreshConversationList();
                });
            }, (error) => {
                console.error('WebSocket error, retrying in 5s...');
                setTimeout(() => this.connectWebSocket(), 5000);
            });
        },

        // ========== UTILITY ==========

        get visibleMessages() {
            return this.dynamicMessages.filter(m => m.type === 'PRIVATE' || m.type === 'GROUP');
        },

        shouldShowAvatar(msg, idx) {
            if (idx === 0) return true;
            const prev = this.visibleMessages[idx - 1];
            return prev.sender !== msg.sender;
        },

        shouldShowDate(idx) {
            if (idx === 0) return true;
            const curr = new Date(this.visibleMessages[idx].timestamp);
            const prev = new Date(this.visibleMessages[idx - 1].timestamp);
            return curr.toDateString() !== prev.toDateString();
        },

        formatTime(ts) {
            if (!ts) return '';
            const d = new Date(ts);
            return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0');
        },

        formatDate(ts) {
            if (!ts) return '';
            const d = new Date(ts);
            const today = new Date();
            const yesterday = new Date(today);
            yesterday.setDate(yesterday.getDate() - 1);

            if (d.toDateString() === today.toDateString()) return 'Hoy';
            if (d.toDateString() === yesterday.toDateString()) return 'Ayer';

            const days = ['Domingo', 'Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes', 'Sabado'];
            const months = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
            return days[d.getDay()] + ', ' + d.getDate() + ' de ' + months[d.getMonth()];
        },

        formatConvTime(ts) {
            if (!ts) return '';
            const d = new Date(ts);
            const now = new Date();
            const diff = now - d;
            if (diff < 86400000 && d.getDate() === now.getDate()) {
                return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0');
            }
            if (diff < 172800000 && d.getDate() === now.getDate() - 1) return 'Ayer';
            const days = ['Dom', 'Lun', 'Mar', 'Mie', 'Jue', 'Vie', 'Sab'];
            return days[d.getDay()] + ' ' + d.getDate() + '/' + (d.getMonth() + 1);
        },

        scrollToBottom() {
            const area = document.getElementById('chat-messages');
            if (area) area.scrollTop = area.scrollHeight;
        },

        onScroll() {
            // Future: load older messages on scroll to top
        },

        toggleSound() {
            this.soundEnabled = !this.soundEnabled;
        },

        playNotificationSound() {
            try {
                const ctx = new (window.AudioContext || window.webkitAudioContext)();
                const osc = ctx.createOscillator();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(880, ctx.currentTime);
                osc.connect(ctx.destination);
                osc.start();
                osc.stop(ctx.currentTime + 0.1);
            } catch(e) {}
        },

        refreshMessages() {
            if (this.selectedUser) {
                fetch('/chat/privado?with=' + this.selectedUser.username)
                    .then(r => r.json())
                    .then(data => { this.dynamicMessages = data; });
            }
        }
    };
}
