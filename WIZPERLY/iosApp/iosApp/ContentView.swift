import SwiftUI

struct ChatItemData: Identifiable, Hashable {
    let id: String
    let name: String
    let lastMessage: String
    let time: String
    let isOnline: Bool
    let unreadCount: Int
    let role: String
    let gender: String
}

struct MessageData: Identifiable {
    let id: String
    let content: String
    let isUser: Bool
    let time: String
    let isVoiceNote: Bool
    var reaction: String? = nil
}

struct ContentView: View {
    @State private var chats: [ChatItemData] = [
        ChatItemData(id: "1", name: "AI Assistant", lastMessage: "Welcome to WizPrly!", time: "12:00 PM", isOnline: true, unreadCount: 0, role: "assistant", gender: "neutral"),
        ChatItemData(id: "2", name: "Travel Buddy", lastMessage: "Where to next?", time: "11:45 AM", isOnline: true, unreadCount: 1, role: "travel_buddy", gender: "female")
    ]

    @State private var showNewChat = false
    @State private var showProfile = false

    var body: some View {
        NavigationView {
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
                            NavigationLink(destination: IOSChatDetailView(chat: chat)) {
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
            .navigationBarHidden(true)
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
        .navigationViewStyle(.stack)
    }
}

struct IOSChatDetailView: View {
    let chat: ChatItemData
    @State private var inputText = ""
    @State private var isCalling = false
    @State private var selectedMessageForMenu: MessageData? = nil
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
                                        .contextMenu {
                                            Button(action: { UIPasteboard.general.string = msg.content }) {
                                                Label("Copy Text", systemImage: "doc.on.doc")
                                            }
                                            Button(action: { inputText = "Replying to: \(msg.content.prefix(15))... " }) {
                                                Label("Reply", systemImage: "arrow.uturn.backward")
                                            }
                                            Button(role: .destructive, action: {
                                                messages.removeAll { $0.id == msg.id }
                                            }) {
                                                Label("Delete", systemImage: "trash")
                                            }
                                        }

                                    if let rx = msg.reaction {
                                        Text(rx)
                                            .font(.caption)
                                            .padding(4)
                                            .background(Color.black.opacity(0.4))
                                            .cornerRadius(8)
                                    }

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
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { isCalling = true }) {
                    Image(systemName: "phone.fill")
                        .foregroundColor(.purple)
                }
            }
        }
        .sheet(isPresented: $isCalling) {
            IOSVoiceCallView(chatName: chat.name, onEndCall: { isCalling = false })
        }
    }
}

struct IOSVoiceCallView: View {
    let chatName: String
    var onEndCall: () -> Void
    @State private var isMuted = false
    @State private var isSpeaker = true

    var body: some View {
        ZStack {
            Color(red: 11/255, green: 20/255, blue: 27/255).ignoresSafeArea()

            VStack(spacing: 32) {
                Spacer()
                Text(chatName)
                    .font(.largeTitle.bold())
                    .foregroundColor(.white)
                Text("In Voice Call...")
                    .foregroundColor(.gray)

                Spacer()

                HStack(spacing: 40) {
                    Button(action: { isMuted.toggle() }) {
                        Image(systemName: isMuted ? "mic.slash.fill" : "mic.fill")
                            .font(.title)
                            .foregroundColor(.white)
                            .padding()
                            .background(isMuted ? Color.red : Color.white.opacity(0.2))
                            .clipShape(Circle())
                    }

                    Button(action: onEndCall) {
                        Image(systemName: "phone.down.fill")
                            .font(.title)
                            .foregroundColor(.white)
                            .padding(24)
                            .background(Color.red)
                            .clipShape(Circle())
                    }

                    Button(action: { isSpeaker.toggle() }) {
                        Image(systemName: isSpeaker ? "speaker.wave.3.fill" : "speaker.slash.fill")
                            .font(.title)
                            .foregroundColor(.white)
                            .padding()
                            .background(isSpeaker ? Color.purple : Color.white.opacity(0.2))
                            .clipShape(Circle())
                    }
                }
                .padding(.bottom, 48)
            }
        }
    }
}

struct IOSNewChatView: View {
    var onCreated: (ChatItemData) -> Void
    @State private var name = ""
    @State private var selectedRole = "assistant"
    @State private var selectedGender = "neutral"
    @Environment(\.dismiss) var dismiss

    let roles = [
        ("assistant", "🤖 AI Assistant"),
        ("friend", "💛 Friend"),
        ("therapist", "🧠 Therapist"),
        ("mentor", "🌟 Mentor"),
        ("coach", "🔥 Coach"),
        ("teacher", "📚 Teacher"),
        ("tech_support", "💻 Tech Support"),
        ("dating", "❤️ Dating")
    ]

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Companion Name")) {
                    TextField("e.g. Luna", text: $name)
                }

                Section(header: Text("Personality Role")) {
                    Picker("Role", selection: $selectedRole) {
                        ForEach(roles, id: \.0) { role, label in
                            Text(label).tag(role)
                        }
                    }
                }

                Section(header: Text("Companion's Gender / Voice")) {
                    Picker("Gender", selection: $selectedGender) {
                        Text("⚪ Neutral").tag("neutral")
                        Text("♂️ Male").tag("male")
                        Text("♀️ Female").tag("female")
                    }
                    .pickerStyle(.segmented)
                }
            }
            .navigationTitle("New AI Companion")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        if !name.isEmpty {
                            let chat = ChatItemData(id: UUID().uuidString, name: name, lastMessage: "New chat created", time: "Just now", isOnline: true, unreadCount: 0, role: selectedRole, gender: selectedGender)
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
        NavigationView {
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

                Section(header: Text("WizPrly Pro")) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Upgrade to Pro").font(.headline)
                            Text("Unlimited companions & elite speed").font(.caption).foregroundColor(.gray)
                        }
                        Spacer()
                        Text("R99.99/mo")
                            .font(.subheadline.bold())
                            .foregroundColor(.purple)
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
