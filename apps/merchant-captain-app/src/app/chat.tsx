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

import { AppIcon } from '@/components/app-icon';
import { PrimaryButton } from '@/components/ui/primary-button';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Radius, Spacing } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/hooks/use-theme';
import { useTranslation } from '@/i18n';
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

  const theme = useTheme();
  const { t } = useTranslation();
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
    () => params.title ?? conversation?.customer.displayName ?? t('chat.title'),
    [conversation?.customer, params.title, t],
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
        Alert.alert(t('chat.updateFailed'), toggleError instanceof Error ? toggleError.message : t('chat.updateFailedBody'));
      } finally {
        setPrivacyBusy(false);
      }
    },
    [accessToken, conversation, t],
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
      Alert.alert(t('chat.sendFailed'), sendError instanceof Error ? sendError.message : t('chat.sendFailedBody'));
    } finally {
      setSending(false);
    }
  }, [accessToken, conversation, draft, t]);

  const handlePickImage = useCallback(async () => {
    if (!conversation) return;

    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert(t('chat.permissionNeeded'), t('chat.photoPermission'));
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
      Alert.alert(t('chat.uploadFailed'), uploadError instanceof Error ? uploadError.message : t('chat.uploadFailedBody'));
    } finally {
      setSending(false);
    }
  }, [accessToken, conversation, t]);

  const renderMessage = useCallback(
    ({ item }: { item: ChatMessage }) => {
      const isMine = item.senderRole === 'MERCHANT';
      return (
        <View style={[styles.bubbleRow, isMine ? styles.bubbleRowMine : styles.bubbleRowOther]}>
          <View style={[styles.bubble, { backgroundColor: isMine ? theme.cta : theme.backgroundElement, borderColor: theme.border, borderWidth: isMine ? 0 : 1 }]}>
            {!isMine ? (
              <ThemedText type="small" style={{ color: theme.textSecondary, fontWeight: '700' }}>
                {item.senderName}
              </ThemedText>
            ) : null}
            {item.messageType === 'IMAGE' && item.imageUrl ? (
              <Image source={{ uri: item.imageUrl }} style={styles.imagePreview} resizeMode="cover" />
            ) : null}
            {item.body ? (
              <ThemedText style={{ color: isMine ? '#ffffff' : theme.text }}>{item.body}</ThemedText>
            ) : null}
          </View>
        </View>
      );
    },
    [theme.backgroundElement, theme.cta, theme.text, theme.textSecondary],
  );

  if (loading) {
    return (
      <ThemedView style={styles.centered}>
        <ActivityIndicator size="large" color={theme.cta} />
      </ThemedView>
    );
  }

  if (error || !conversation) {
    return (
      <ThemedView style={styles.centered}>
        <ThemedText style={{ color: theme.danger }}>{error ?? t('chat.unavailable')}</ThemedText>
        <TouchableOpacity onPress={() => router.back()} style={[styles.backButton, { borderColor: theme.border }]}>
          <ThemedText style={{ fontWeight: '800' }}>{t('common.back')}</ThemedText>
        </TouchableOpacity>
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <View style={[styles.header, { borderBottomColor: theme.border, backgroundColor: theme.backgroundElement }]}>
          <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
            <ThemedText style={{ fontWeight: '900' }}>{t('common.back')}</ThemedText>
          </TouchableOpacity>
          <View style={styles.headerCopy}>
            <ThemedText style={styles.headerTitle}>{headerTitle}</ThemedText>
            <ThemedText type="small" style={{ color: theme.textSecondary }}>
              {conversation.customer.phoneHidden
                ? t('chat.customerPhoneHidden')
                : conversation.customer.phoneNumber ?? t('chat.customerPhoneHidden')}
            </ThemedText>
          </View>
          <TouchableOpacity onPress={() => setShowPrivacy((current) => !current)} style={[styles.privacyButton, { borderColor: theme.border }]}>
            <ThemedText style={{ fontWeight: '800' }}>{t('chat.privacy')}</ThemedText>
          </TouchableOpacity>
        </View>

        {showPrivacy ? (
          <View style={[styles.privacyPanel, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <View style={styles.privacyRow}>
              <View style={styles.privacyCopy}>
                <ThemedText style={{ fontWeight: '800' }}>{t('chat.showCustomerPhone')}</ThemedText>
                <ThemedText type="small" style={{ color: theme.textSecondary }}>
                  {t('chat.showCustomerPhoneHint')}
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
                  <ThemedText style={{ fontWeight: '800' }}>{t('chat.showDoctorPhone')}</ThemedText>
                  <ThemedText type="small" style={{ color: theme.textSecondary }}>
                    {t('chat.showDoctorPhoneHint')}
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
          <View style={[styles.composer, { borderTopColor: theme.border, backgroundColor: theme.background }]}>
            <TouchableOpacity
              onPress={() => void handlePickImage()}
              disabled={sending}
              style={[styles.attachButton, { borderColor: theme.border, backgroundColor: theme.muted }]}
              accessibilityLabel="Attach image"
            >
              <AppIcon name="sparkle" color={theme.primary} size={18} />
            </TouchableOpacity>
            <TextInput
              value={draft}
              onChangeText={setDraft}
              placeholder={t('chat.replyPlaceholder')}
              placeholderTextColor={theme.textSecondary}
              style={[styles.input, { color: theme.text, borderColor: theme.border, backgroundColor: theme.backgroundElement }]}
              multiline
            />
            <PrimaryButton
              label={t('common.send')}
              onPress={() => void handleSend()}
              disabled={sending || !draft.trim()}
              loading={sending}
              style={styles.sendButton}
            />
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
    minWidth: 88,
    minHeight: 44,
  },
});
