CREATE SCHEMA IF NOT EXISTS content;

CREATE TABLE IF NOT EXISTS content.promo_banners (
    banner_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           TEXT NOT NULL,
    subtitle        TEXT NOT NULL,
    accent_color    TEXT NOT NULL DEFAULT '#F97316',
    duration_sec    INT NOT NULL DEFAULT 5 CHECK (duration_sec BETWEEN 1 AND 30),
    sort_order      INT NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS content.guide_articles (
    article_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category        TEXT NOT NULL,
    title           TEXT NOT NULL,
    summary         TEXT NOT NULL,
    body            TEXT,
    read_minutes    INT NOT NULL DEFAULT 3,
    published       BOOLEAN NOT NULL DEFAULT true,
    author_user_id  UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_guide_articles_category ON content.guide_articles(category);

CREATE TABLE IF NOT EXISTS content.guide_writers (
    writer_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE,
    email           TEXT NOT NULL,
    access_status   TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
