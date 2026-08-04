import React, { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import {
  ActionButton,
  AppBar,
  FeedbackBanner,
  FilterChip,
  SectionHeader,
  StateView,
  StatusBadge,
} from '@/components/foundation/primitives';
import { ScreenShell } from '@/components/foundation/screen-shell';
import { ThemedText } from '@/components/themed-text';
import { AppCard } from '@/components/ui/app-card';
import { TextField } from '@/components/ui/text-field';
import { useAuth } from '@/context/AuthContext';
import { spacing, typography } from '@/design/tokens';
import {
  createGuideArticle,
  fetchMyGuideArticles,
  fetchMyGuideWriterAccess,
  type GuideWriterAccess,
  type MerchantGuideArticle,
} from '@/services/guide-publishing';

const CATEGORIES = [
  { id: 'puppy-kitten', label: 'Puppy & kitten' },
  { id: 'skin', label: 'Skin health' },
  { id: 'ticks-odor', label: 'Ticks & hygiene' },
] as const;

export default function MerchantGuidesScreen() {
  const { activeRole, session } = useAuth();
  const token = session?.access_token ?? null;
  const [writer, setWriter] = useState<GuideWriterAccess | null>(null);
  const [articles, setArticles] = useState<MerchantGuideArticle[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({
    category: 'puppy-kitten',
    title: '',
    summary: '',
    body: '',
    readMinutes: '3',
  });

  const load = useCallback(async () => {
    if (!token || activeRole !== 'PROVIDER') {
      setLoading(false);
      return;
    }
    setError(null);
    try {
      const nextWriter = await fetchMyGuideWriterAccess(token);
      setWriter(nextWriter);
      if (nextWriter.accessStatus === 'ACTIVE') {
        setArticles(await fetchMyGuideArticles(token));
      }
    } catch (nextError) {
      setWriter(null);
      setError(nextError instanceof Error ? nextError.message : 'Guide publishing permission is unavailable');
    } finally {
      setLoading(false);
    }
  }, [activeRole, token]);

  useEffect(() => {
    void load();
  }, [load]);

  const publish = useCallback(async () => {
    if (!token || !writer || writer.accessStatus !== 'ACTIVE') return;
    const readMinutes = Number(form.readMinutes);
    if (!form.title.trim() || !form.summary.trim() || !form.body.trim() || !Number.isFinite(readMinutes)) {
      setError('Title, summary, article body and a valid read time are required.');
      return;
    }

    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const saved = await createGuideArticle({
        category: form.category,
        title: form.title.trim(),
        summary: form.summary.trim(),
        body: form.body.trim(),
        readMinutes,
      }, token);
      setArticles((current) => [saved, ...current]);
      setForm((current) => ({ ...current, title: '', summary: '', body: '', readMinutes: '3' }));
      setNotice('Health guide published with the admin-approved author and company attribution.');
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Could not publish health guide');
    } finally {
      setBusy(false);
    }
  }, [form, token, writer]);

  if (activeRole !== 'PROVIDER') {
    return (
      <ScreenShell header={<AppBar eyebrow="CONTENT" title="Health guides" />}>
        <StateView kind="unauthorized" title="Merchant access required" message="Only verified merchant workspaces can create health guides." />
      </ScreenShell>
    );
  }

  if (loading) {
    return (
      <ScreenShell header={<AppBar eyebrow="CONTENT" title="Health guides" />}>
        <StateView kind="loading" title="Checking publishing permission" message="Reading the admin-controlled writer access record." />
      </ScreenShell>
    );
  }

  if (!writer || writer.accessStatus !== 'ACTIVE') {
    return (
      <ScreenShell header={<AppBar eyebrow="CONTENT" title="Health guides" subtitle="Merchant educational content" />}>
        <StateView
          kind="unauthorized"
          title="Publishing permission is off"
          message="A MyPet administrator must enable Health Guide Writer access and approve the author name and company before articles can be published."
          actionLabel="Check again"
          onAction={() => void load()}
        />
        {error ? <FeedbackBanner title="Permission status" message={error} tone="warning" /> : null}
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      header={
        <AppBar
          eyebrow="MERCHANT CONTENT"
          title="Write a health guide"
          subtitle={`${writer.authorName} · ${writer.companyName}`}
        />
      }
    >
      {notice ? <FeedbackBanner title="Published" message={notice} tone="success" /> : null}
      {error ? <FeedbackBanner title="Guide not published" message={error} tone="danger" /> : null}

      <AppCard style={styles.formCard}>
        <View style={styles.rowBetween}>
          <View style={styles.flex}>
            <ThemedText style={styles.cardTitle}>{writer.authorName}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">{writer.companyName} · {writer.email}</ThemedText>
          </View>
          <StatusBadge label="Writer enabled" tone="success" />
        </View>

        <SectionHeader title="Article category" subtitle="Choose the customer-facing guide group" />
        <View style={styles.wrapRow}>
          {CATEGORIES.map((category) => (
            <FilterChip
              key={category.id}
              label={category.label}
              selected={form.category === category.id}
              onPress={() => setForm((current) => ({ ...current, category: category.id }))}
            />
          ))}
        </View>

        <TextField label="Article title" value={form.title} onChangeText={(title) => setForm((current) => ({ ...current, title }))} />
        <TextField label="Short summary" value={form.summary} onChangeText={(summary) => setForm((current) => ({ ...current, summary }))} multiline />
        <TextField label="Full article" value={form.body} onChangeText={(body) => setForm((current) => ({ ...current, body }))} multiline />
        <TextField label="Estimated read time (minutes)" keyboardType="number-pad" value={form.readMinutes} onChangeText={(readMinutes) => setForm((current) => ({ ...current, readMinutes }))} />
        <ActionButton label="Publish health guide" icon="check" loading={busy} onPress={() => void publish()} />
      </AppCard>

      <SectionHeader title="Your published guides" subtitle={`${articles.length} articles`} />
      {articles.length === 0 ? (
        <StateView kind="empty" title="No guides published" message="Your first approved article will appear here." />
      ) : articles.map((article) => (
        <AppCard key={article.id} style={styles.articleCard}>
          <View style={styles.rowBetween}>
            <View style={styles.flex}>
              <ThemedText style={styles.cardTitle}>{article.title}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">{article.summary}</ThemedText>
            </View>
            <StatusBadge label={`${article.likeCount} likes`} tone="info" />
          </View>
          <ThemedText type="small" themeColor="textSecondary">
            Written by {article.authorName} · {article.companyName} · {article.readMinutes} min read
          </ThemedText>
        </AppCard>
      ))}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  formCard: { gap: spacing.x4 },
  articleCard: { gap: spacing.x2 },
  rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.x3 },
  wrapRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.x2 },
  flex: { flex: 1 },
  cardTitle: { ...typography.title },
});
