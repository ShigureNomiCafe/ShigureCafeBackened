CREATE TABLE articles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    author_id BIGINT NOT NULL,
    assignee_id BIGINT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    CONSTRAINT fk_articles_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_articles_assignee FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE article_reactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    CONSTRAINT fk_article_reactions_article FOREIGN KEY (article_id) REFERENCES articles (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_reactions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    UNIQUE KEY uk_article_user_type (article_id, user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Migrate data from notices to articles
INSERT INTO articles (id, title, content, type, pinned, author_id, created_at, updated_at)
SELECT id, title, content, 'ANNOUNCEMENT', pinned, author_id, created_at, updated_at FROM notices;

-- Migrate data from notice_reactions to article_reactions
INSERT INTO article_reactions (id, article_id, user_id, type)
SELECT id, notice_id, user_id, type FROM notice_reactions;

-- Drop old tables
DROP TABLE notice_reactions;
DROP TABLE notices;
