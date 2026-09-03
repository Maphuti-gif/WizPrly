import SwiftUI

struct ChatItemData: Identifiable, Hashable {
    let id: String
    let name: String
    let lastMessage: String
    let time: String
    let isOnline: Bool
    let unreadCount: Int
    let role: String
}

struct MessageData: Identifiable {
    let id: String
    let content: String
    let isUser: Bool
    let time: String
    let isVoiceNote: Bool
}

struct ContentView: View {
    @State private var chats: [ChatItemData] = [
        ChatItemData(id: "1", name: "AI Assistant", lastMessage: "Welcome to WizPrly!", time: "12:00 PM", isOnline: true, unreadCount: 0, role: "assistant"),
        ChatItemData(id: "2", name: "Travel Buddy", lastMessage: "Where to next?", time: "11:45 AM", isOnline: true, unreadCount: 1, role: "travel_buddy")
    ]
    
    @State private var selectedChat: ChatItemData? = nil
    @State private var showNewChat = false
    @State private var showProfile = false
    
    var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 11/255, green: 11/255, blue: 21/255)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header
                    HStack {
                        Image(systemName: "sparkles")
                            .font(.title2)
                            .foregroundColor(.purple)
                        Text("WizPrly")
                            .font(.system(size: 26, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        
                        Button(action: { showProfile = true }) {
                            Image(systemName: "person.circle.fill")
                                .font(.title)
                                .foregroundColor(.gray)
                        }
                    }
                    .padding()
                    
                    // Search Bar
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.gray)
                        Text("Search conversations...")
                            .foregroundColor(.gray)
                        Spacer()
                    }
                    .padding()
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(16)
                    .padding(.horizontal)
                    
                    // Chat List
                    List {
                        ForEach(chats) { chat in
                            Button(action: { selectedChat = chat }) {
                                HStack(spacing: 16) {
                                    ZStack(alignment: .bottomTrailing) {
                                        Circle()
                                            .fill(LinearGradient(colors: [.purple, .blue], startPoint: .topLeading, endPoint: .bottomTrailing))
                                            .frame(width: 52, height: 52)
                                            .overlay(
                                                Text(String(chat.name.prefix(1)))
                                                    .font(.title2.bold())
                                                    .foregroundColor(.white)
                                            )
                                        
                                        if chat.isOnline {
                                            Circle()
                                                .fill(Color.green)
                                                .frame(width: 14, height: 14)
                                                .overlay(Circle().stroke(Color.black, lineWidth: 2))
                                        }
                                    }
                                    
                                    VStack(alignment: .leading, spacing: 4) {
                                        HStack {
                                            Text(chat.name)
                                                .font(.headline)
                                                .foregroundColor(.white)
                                            Spacer()
                                            Text(chat.time)
                                                .font(.caption)
                                                .foregroundColor(.gray)
                                        }
                                        
                                        Text(chat.lastMessage)
                                            .font(.subheadline)
                                            .foregroundColor(.gray)
                                            .lineLimit(1)
                                    }
                                }
                                .padding(.vertical, 8)
                            }
                            .listRowBackground(Color.clear)
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationDestination(for: ChatItemData.self) { chat in
                IOSChatDetailView(chat: chat)
            }
            .sheet(isPresented: $showNewChat) {
                IOSNewChatView(onCreated: { newChat in
                    chats.append(newChat)
                    showNewChat = false
                })
            }
            .sheet(isPresented: $showProfile) {
                IOSProfileView()
            }
            .overlay(alignment: .bottomTrailing) {
                Button(action: { showNewChat = true }) {
                    Image(systemName: "plus")
                        .font(.title.bold())
                        .foregroundColor(.white)
                        .frame(width: 60, height: 60)
                        .background(Color.purple)
                        .clipShape(Circle())
                        .shadow(radius: 8)
                }
                .padding(24)
            }
        }
    }
}

struct IOSChatDetailView: View {
    let chat: ChatItemData
    @State private var inputText = ""
    @State private var messages: [MessageData] = [
        MessageData(id: "1", content: "Welcome to WizPrly! How can I help you today?", isUser: false, time: "12:00 PM", isVoiceNote: false)
    ]
    
    var body: some View {
        ZStack {
            Color(red: 11/255, green: 11/255, blue: 21/255).ignoresSafeArea()
            
            VStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(messages) { msg in
                            HStack {
                                if msg.isUser { Spacer() }
                                
                                VStack(alignment: msg.isUser ? .trailing : .leading) {
                                    Text(msg.content)
                                        .padding(12)
                                        .background(msg.isUser ? Color.purple : Color.white.opacity(0.15))
                                        .foregroundColor(.white)
                                        .cornerRadius(16)
                                    
                                    Text(msg.time)
                                        .font(.caption2)
                                        .foregroundColor(.gray)
                                }
                                
                                if !msg.isUser { Spacer() }
                            }
                        }
                    }
                    .padding()
                }
                
                HStack(spacing: 12) {
                    TextField("Type a message...", text: $inputText)
                        .padding(12)
                        .background(Color.white.opacity(0.1))
                        .cornerRadius(20)
                        .foregroundColor(.white)
                    
                    Button(action: {
                        if !inputText.isEmpty {
                            let newMsg = MessageData(id: UUID().uuidString, content: inputText, isUser: true, time: "Just now", isVoiceNote: false)
                            messages.append(newMsg)
                            let sent = inputText
                            inputText = ""
                            
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                                messages.append(MessageData(id: UUID().uuidString, content: "Received: \(sent)", isUser: false, time: "Just now", isVoiceNote: false))
                            }
                        }
                    }) {
                        Image(systemName: "paperplane.fill")
                            .font(.title2)
                            .foregroundColor(.purple)
                    }
                }
                .padding()
            }
        }
        .navigationTitle(chat.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct IOSNewChatView: View {
    var onCreated: (ChatItemData) -> Void
    @State private var name = ""
    @State private var selectedRole = "assistant"
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Companion Name")) {
                    TextField("e.g. Luna", text: $name)
                }
                
                Section(header: Text("Personality Role")) {
                    Picker("Role", selection: $selectedRole) {
                        Text("AI Assistant").tag("assistant")
                        Text("Friend").tag("friend")
                        Text("Therapist").tag("therapist")
                        Text("Mentor").tag("mentor")
                        Text("Dating").tag("dating")
                    }
                }
            }
            .navigationTitle("New AI Companion")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        if !name.isEmpty {
                            let chat = ChatItemData(id: UUID().uuidString, name: name, lastMessage: "New chat created", time: "Just now", isOnline: true, unreadCount: 0, role: selectedRole)
                            onCreated(chat)
                        }
                    }
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}

struct IOSProfileView: View {
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationStack {
            List {
                Section(header: Text("Account")) {
                    HStack {
                        Image(systemName: "person.circle.fill")
                            .font(.largeTitle)
                            .foregroundColor(.purple)
                        VStack(alignment: .leading) {
                            Text("WizPrly User").font(.headline)
                            Text("Pro Subscription").font(.subheadline).foregroundColor(.gray)
                        }
                    }
                }
                
                Section(header: Text("App Info")) {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text("1.0.0 (KMP iOS)")
                            .foregroundColor(.gray)
                    }
                }
            }
            .navigationTitle("Profile & Settings")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
