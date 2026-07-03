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
  updateConversationPrivacy,
  uploadChatImage,
  type ChatMessage,
  type Conversation,
} from '@/services/chat';

const POLL_MS = 5000;

export default function MerchantChatScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<{
    conversationId?: string;
    contextType?: 'ORDER' | 'APPOINTMENT';
    contextId?: string;
    providerId?: string;
    customerId?: string;
    title?: string;
    providerType?: string;
  }>();
  const { session } = useAuth();
  const accessToken = session?.access_token;

  const [conversation, setConversation] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [privacyBusy, setPrivacyBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showPrivacy, setShowPrivacy] = useState(false);
  const listRef = useRef<FlatList<ChatMessage>>(null);

  const colors = Colors.light;
  const isVet = (params.providerType ?? conversation?.providerType) === 'VET_HOSPITAL';

  const loadChat = useCallback(async () => {
    try {
      let activeConversation: Conversation;
      if (params.conversationId) {
        activeConversation = await fetchConversation(params.conversationId, accessToken);
      } else if (params.contextType && params.contextId && params.providerId && params.customerId) {
        activeConversation = await openConversation({
          contextType: params.contextType,
          contextId: params.contextId,
          providerId: params.providerId,
          customerId: params.customerId,
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
  }, [accessToken, params.contextId, params.contextType, params.conversationId, params.customerId, params.providerId]);

  useEffect(() => {
    void loadChat();
  }, [loadChat]);

  useEffect(() => {
    if (!conversation?.conversationId) return undefined;

    const interval = setInterval(async () => {
      try {
        const latest = await fetchMessages(conversation.conversationId, accessToken);
        setMessages(latest);
        await markConversationRead(conversation.conversationId, accessToken);
        const refreshed = await fetchConversation(conversation.conversationId, accessToken);
        setConversation(refreshed);
      } catch {
        // Ignore transient polling errors.
      }
    }, POLL_MS);

    return () => clearInterval(interval);
  }, [accessToken, conversation?.conversationId]);

  const headerTitle = useMemo(
    () => params.title ?? conversation?.customer.displayName ?? 'Customer chat',
    [conversation?.customer.displayName, params.title],
  );

  const togglePrivacy = useCallback(
    async (field: 'customerPhoneVisible' | 'doctorPhoneVisible', value: boolean) => {
      if (!conversation) return;
      setPrivacyBusy(true);
      try {
        const updated = await updateConversationPrivacy(
          conversation.conversationId,
          { [field]: value },
          accessToken,
        );
        setConversation(updated);
      } catch (toggleError) {
        Alert.alert('Update failed', toggleError instanceof Error ? toggleError.message : 'Could not update privacy.');
      } finally {
        setPrivacyBusy(false);
      }
    },
    [accessToken, conversation],
  );

  const handleSend = useCallback(async () => {
    const trimmed = draft.trim();
    if (!trimmed || !conversation) return;

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
  }, [accessToken, conversation, draft]);

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
  }, [accessToken, conversation]);

  const renderMessage = useCallback(
    ({ item }: { item: ChatMessage }) => {
      const isMine = item.senderRole === 'MERCHANT';
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
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              {conversation.customer.phoneHidden
                ? 'Customer phone hidden'
                : conversation.customer.phoneNumber ?? 'Customer phone hidden'}
            </ThemedText>
          </View>
          <TouchableOpacity onPress={() => setShowPrivacy((current) => !current)} style={[styles.privacyButton, { borderColor: colors.border }]}>
            <ThemedText style={{ fontWeight: '800' }}>Privacy</ThemedText>
          </TouchableOpacity>
        </View>

        {showPrivacy ? (
          <View style={[styles.privacyPanel, { backgroundColor: colors.backgroundElement, borderColor: colors.border }]}>
            <View style={styles.privacyRow}>
              <View style={styles.privacyCopy}>
                <ThemedText style={{ fontWeight: '800' }}>Show customer phone</ThemedText>
                <ThemedText type="small" style={{ color: colors.textSecondary }}>
                  Reveal customer number to your team in this chat.
                </ThemedText>
              </View>
              <Switch
                value={conversation.privacy.customerPhoneVisible}
                onValueChange={(value) => void togglePrivacy('customerPhoneVisible', value)}
                disabled={privacyBusy}
              />
            </View>
            {isVet ? (
              <View style={styles.privacyRow}>
                <View style={styles.privacyCopy}>
                  <ThemedText style={{ fontWeight: '800' }}>Show doctor phone to customer</ThemedText>
                  <ThemedText type="small" style={{ color: colors.textSecondary }}>
                    Let the customer see the assigned doctor&apos;s contact number.
                  </ThemedText>
                </View>
                <Switch
                  value={conversation.privacy.doctorPhoneVisible}
                  onValueChange={(value) => void togglePrivacy('doctorPhoneVisible', value)}
                  disabled={privacyBusy}
                />
              </View>
            ) : null}
          </View>
        ) : null}

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
              placeholder="Reply to customer"
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
  },
  privacyButton: {
    minHeight: 40,
    borderWidth: 1,
    borderRadius: Radius.md,
    paddingHorizontal: Spacing.two,
    justifyContent: 'center',
  },
  privacyPanel: {
    marginHorizontal: Spacing.three,
    marginTop: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.three,
    gap: Spacing.three,
  },
  privacyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  privacyCopy: { flex: 1, gap: 2 },
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
