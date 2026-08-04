ALTER TABLE content.guide_articles
    ADD COLUMN IF NOT EXISTS author_name TEXT NOT NULL DEFAULT 'MyPet Expert',
    ADD COLUMN IF NOT EXISTS company_name TEXT NOT NULL DEFAULT 'MyPet',
    ADD COLUMN IF NOT EXISTS like_count BIGINT NOT NULL DEFAULT 0 CHECK (like_count >= 0);

ALTER TABLE content.guide_writers
    ADD COLUMN IF NOT EXISTS author_name TEXT NOT NULL DEFAULT 'MyPet Expert',
    ADD COLUMN IF NOT EXISTS company_name TEXT NOT NULL DEFAULT 'MyPet Partner',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS content.guide_likes (
    like_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id  UUID NOT NULL REFERENCES content.guide_articles(article_id) ON DELETE CASCADE,
    user_id     UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_guide_like_article_user UNIQUE (article_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_guide_likes_article_id
    ON content.guide_likes(article_id);

CREATE INDEX IF NOT EXISTS idx_guide_articles_author_user_id
    ON content.guide_articles(author_user_id);
