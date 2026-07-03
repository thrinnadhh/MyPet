import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  Image,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
  Switch,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Colors, Radius, Spacing } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { appConfig } from '@/utils/app-config';
import {
  fetchConversation,
  fetchMessages,
  markConversationRead,
  openConversation,
  sendImageMessage,
  sendTextMessage,
  uploadChatImage,
  type ChatMessage,
  type Conversation,
} from '@/services/chat';

const POLL_MS = 5000;

const DEMO_CONVERSATION: Conversation = {
  conversationId: 'demo-chat',
  customerId: 'demo-customer',
  providerId: 'demo-provider',
  providerName: 'Happy Paws Clinic',
  providerType: 'VET_HOSPITAL',
  contextType: 'APPOINTMENT',
  contextId: 'demo-appointment',
  customer: { userId: 'demo-customer', displayName: 'You', phoneNumber: null, phoneHidden: false },
  merchant: { userId: 'demo-merchant', displayName: 'Happy Paws Clinic', phoneNumber: null, phoneHidden: true },
  doctor: { userId: 'demo-doctor', displayName: 'Dr. Anita Rao', phoneNumber: null, phoneHidden: true },
  privacy: {
    customerPhoneVisible: false,
    doctorPhoneVisible: false,
    assignedDoctorUserId: null,
    canManagePrivacy: false,
  },
  lastMessagePreview: 'Please share Bruno\'s vaccination record.',
  lastMessageAt: new Date().toISOString(),
  unreadCount: 0,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

const DEMO_MESSAGES: ChatMessage[] = [
  {
    messageId: 'm1',
    conversationId: 'demo-chat',
    senderId: 'demo-merchant',
    senderRole: 'MERCHANT',
    senderName: 'Happy Paws Clinic',
    messageType: 'TEXT',
    body: 'Hi! Please share Bruno\'s vaccination record before tomorrow\'s visit.',
    imageUrl: null,
    imageMimeType: null,
    sentAt: new Date(Date.now() - 3600_000).toISOString(),
    readAt: new Date().toISOString(),
  },
];

export default function ChatScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<{
    conversationId?: string;
    contextType?: 'ORDER' | 'APPOINTMENT';
    contextId?: string;
    providerId?: string;
    title?: string;
  }>();
  const { user, session } = useAuth();
  const accessToken = session?.access_token;

  const [conversation, setConversation] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRef = useRef<FlatList<ChatMessage>>(null);

  const colors = Colors.light;

  const loadChat = useCallback(async () => {
    if (appConfig.allowDemoMode) {
      setConversation(DEMO_CONVERSATION);
      setMessages(DEMO_MESSAGES);
      setError(null);
      setLoading(false);
      return;
    }

    try {
      let activeConversation: Conversation;
      if (params.conversationId) {
        activeConversation = await fetchConversation(params.conversationId, accessToken);
      } else if (params.contextType && params.contextId && params.providerId) {
        activeConversation = await openConversation({
          contextType: params.contextType,
          contextId: params.contextId,
          providerId: params.providerId,
          accessToken,
        });
      } else {
        throw new Error('Missing chat context.');
      }

      const loadedMessages = await fetchMessages(activeConversation.conversationId, accessToken);
      setConversation(activeConversation);
      setMessages(loadedMessages);
      await markConversationRead(activeConversation.conversationId, accessToken);
      setError(null);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : 'Could not load chat.';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [accessToken, params.contextId, params.contextType, params.conversationId, params.providerId]);

  useEffect(() => {
    void loadChat();
  }, [loadChat]);

  useEffect(() => {
    if (appConfig.allowDemoMode || !conversation?.conversationId) return undefined;

    const interval = setInterval(async () => {
      try {
        const latest = await fetchMessages(conversation.conversationId, accessToken);
        setMessages(latest);
        await markConversationRead(conversation.conversationId, accessToken);
        const refreshed = await fetchConversation(conversation.conversationId, accessToken);
        setConversation(refreshed);
      } catch {
        // Keep polling quietly; transient network errors should not break the screen.
      }
    }, POLL_MS);

    return () => clearInterval(interval);
  }, [accessToken, conversation?.conversationId]);

  const headerTitle = useMemo(
    () => params.title ?? conversation?.providerName ?? 'Chat',
    [conversation?.providerName, params.title],
  );

  const handleSend = useCallback(async () => {
    const trimmed = draft.trim();
    if (!trimmed || !conversation) return;

    if (appConfig.allowDemoMode) {
      setMessages((current) => [
        ...current,
        {
          messageId: `local-${Date.now()}`,
          conversationId: conversation.conversationId,
          senderId: user?.id ?? 'demo-customer',
          senderRole: 'CUSTOMER',
          senderName: 'You',
          messageType: 'TEXT',
          body: trimmed,
          imageUrl: null,
          imageMimeType: null,
          sentAt: new Date().toISOString(),
          readAt: null,
        },
      ]);
      setDraft('');
      return;
    }

    setSending(true);
    try {
      const sent = await sendTextMessage(conversation.conversationId, trimmed, accessToken);
      setMessages((current) => [...current, sent]);
      setDraft('');
    } catch (sendError) {
      Alert.alert('Send failed', sendError instanceof Error ? sendError.message : 'Could not send message.');
    } finally {
      setSending(false);
    }
  }, [accessToken, conversation, draft, user?.id]);

  const handlePickImage = useCallback(async () => {
    if (!conversation) return;

    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Permission needed', 'Allow photo access to share images in chat.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.85,
    });
    if (result.canceled || result.assets.length === 0) return;

    const asset = result.assets[0];
    const mimeType = asset.mimeType ?? 'image/jpeg';
    const fileName = asset.fileName ?? `chat-${Date.now()}.jpg`;

    if (appConfig.allowDemoMode) {
      setMessages((current) => [
        ...current,
        {
          messageId: `img-${Date.now()}`,
          conversationId: conversation.conversationId,
          senderId: user?.id ?? 'demo-customer',
          senderRole: 'CUSTOMER',
          senderName: 'You',
          messageType: 'IMAGE',
          body: null,
          imageUrl: asset.uri,
          imageMimeType: mimeType,
          sentAt: new Date().toISOString(),
          readAt: null,
        },
      ]);
      return;
    }

    setSending(true);
    try {
      const uploaded = await uploadChatImage(asset.uri, mimeType, fileName, accessToken);
      const sent = await sendImageMessage(
        conversation.conversationId,
        uploaded.imageUrl,
        uploaded.imageMimeType,
        undefined,
        accessToken,
      );
      setMessages((current) => [...current, sent]);
    } catch (uploadError) {
      Alert.alert('Upload failed', uploadError instanceof Error ? uploadError.message : 'Could not send image.');
    } finally {
      setSending(false);
    }
  }, [accessToken, conversation, user?.id]);

  const renderMessage = useCallback(
    ({ item }: { item: ChatMessage }) => {
      const isMine = item.senderRole === 'CUSTOMER';
      return (
        <View style={[styles.bubbleRow, isMine ? styles.bubbleRowMine : styles.bubbleRowOther]}>
          <View style={[styles.bubble, { backgroundColor: isMine ? colors.cta : colors.backgroundElement }]}>
            {!isMine ? (
              <ThemedText type="small" style={{ color: colors.textSecondary, fontWeight: '700' }}>
                {item.senderName}
              </ThemedText>
            ) : null}
            {item.messageType === 'IMAGE' && item.imageUrl ? (
              <Image source={{ uri: item.imageUrl }} style={styles.imagePreview} resizeMode="cover" />
            ) : null}
            {item.body ? (
              <ThemedText style={{ color: isMine ? '#ffffff' : colors.text }}>{item.body}</ThemedText>
            ) : null}
          </View>
        </View>
      );
    },
    [colors.backgroundElement, colors.cta, colors.text, colors.textSecondary],
  );

  if (loading) {
    return (
      <ThemedView style={styles.centered}>
        <ActivityIndicator size="large" color={colors.cta} />
      </ThemedView>
    );
  }

  if (error || !conversation) {
    return (
      <ThemedView style={styles.centered}>
        <ThemedText style={{ color: colors.danger }}>{error ?? 'Chat unavailable'}</ThemedText>
        <TouchableOpacity onPress={() => router.back()} style={[styles.backButton, { borderColor: colors.border }]}>
          <ThemedText style={{ fontWeight: '800' }}>Back</ThemedText>
        </TouchableOpacity>
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <View style={[styles.header, { borderBottomColor: colors.border }]}>
          <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
            <ThemedText style={{ fontWeight: '900' }}>Back</ThemedText>
          </TouchableOpacity>
          <View style={styles.headerCopy}>
            <ThemedText style={styles.headerTitle}>{headerTitle}</ThemedText>
            {conversation.doctor ? (
              <ThemedText type="small" style={{ color: colors.textSecondary }}>
                {conversation.doctor.displayName}
                {conversation.doctor.phoneHidden ? ' · contact hidden' : conversation.doctor.phoneNumber ? ` · ${conversation.doctor.phoneNumber}` : ''}
              </ThemedText>
            ) : null}
            {conversation.merchant.phoneNumber ? (
              <ThemedText type="small" style={{ color: colors.textSecondary }}>
                Clinic: {conversation.merchant.phoneNumber}
              </ThemedText>
            ) : null}
          </View>
        </View>

        <FlatList
          ref={listRef}
          data={messages}
          keyExtractor={(item) => item.messageId}
          renderItem={renderMessage}
          contentContainerStyle={styles.messageList}
          onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: true })}
        />

        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <View style={[styles.composer, { borderTopColor: colors.border, backgroundColor: colors.background }]}>
            <TouchableOpacity
              onPress={() => void handlePickImage()}
              disabled={sending}
              style={[styles.attachButton, { borderColor: colors.border }]}
              accessibilityLabel="Attach image"
            >
              <ThemedText style={{ fontWeight: '900' }}>IMG</ThemedText>
            </TouchableOpacity>
            <TextInput
              value={draft}
              onChangeText={setDraft}
              placeholder="Type a message"
              placeholderTextColor={colors.textSecondary}
              style={[styles.input, { color: colors.text, borderColor: colors.border }]}
              multiline
            />
            <TouchableOpacity
              onPress={() => void handleSend()}
              disabled={sending || !draft.trim()}
              style={[styles.sendButton, { backgroundColor: colors.cta, opacity: sending || !draft.trim() ? 0.5 : 1 }]}
            >
              <ThemedText style={styles.sendLabel}>Send</ThemedText>
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  safeArea: { flex: 1 },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: Spacing.three, padding: Spacing.four },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingBottom: Spacing.two,
    borderBottomWidth: 1,
  },
  headerCopy: { flex: 1, gap: 2 },
  headerTitle: { fontSize: 18, fontWeight: '900' },
  backButton: {
    minHeight: 40,
    justifyContent: 'center',
    paddingHorizontal: Spacing.two,
    borderRadius: Radius.md,
    borderWidth: 1,
    borderColor: 'transparent',
  },
  messageList: { padding: Spacing.three, gap: Spacing.two, flexGrow: 1 },
  bubbleRow: { flexDirection: 'row' },
  bubbleRowMine: { justifyContent: 'flex-end' },
  bubbleRowOther: { justifyContent: 'flex-start' },
  bubble: {
    maxWidth: '82%',
    borderRadius: Radius.lg,
    padding: Spacing.three,
    gap: Spacing.one,
  },
  imagePreview: {
    width: 220,
    height: 220,
    borderRadius: Radius.md,
  },
  composer: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: Spacing.two,
    padding: Spacing.three,
    borderTopWidth: 1,
  },
  attachButton: {
    minWidth: 44,
    minHeight: 44,
    borderRadius: Radius.md,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.two,
  },
  input: {
    flex: 1,
    minHeight: 44,
    maxHeight: 120,
    borderWidth: 1,
    borderRadius: Radius.md,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  sendButton: {
    minHeight: 44,
    borderRadius: Radius.md,
    paddingHorizontal: Spacing.three,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendLabel: { color: '#ffffff', fontWeight: '800' },
});
