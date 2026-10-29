-- 媒体文件与数据库登记无法原子提交：配额先原子预占，文件随后落盘，
-- 只有完整写入后才标记 ACTIVE；中断或失败由状态和清理任务收敛。
CREATE TABLE media_quota (
  user_id BIGINT UNSIGNED NOT NULL,
  used_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0,
  asset_count INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id),
  CONSTRAINT fk_media_quota_user FOREIGN KEY (user_id) REFERENCES creator_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 路径由服务端生成并唯一；状态与文件存在性分开记录，避免把文件系统当作事务。
CREATE TABLE media_asset (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  path VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  original_name VARCHAR(128) NOT NULL,
  content_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  size_bytes BIGINT UNSIGNED NOT NULL,
  state VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_media_asset_path (path),
  KEY idx_media_asset_user (user_id, state, id),
  KEY idx_media_asset_state (state, created_at, id),
  CONSTRAINT fk_media_asset_user FOREIGN KEY (user_id) REFERENCES creator_user (id),
  CONSTRAINT chk_media_asset_size CHECK (size_bytes > 0),
  CONSTRAINT chk_media_asset_type CHECK (
    content_type IN ('image/jpeg','image/png','image/webp')
  ),
  CONSTRAINT chk_media_asset_state CHECK (
    state IN ('PENDING','ACTIVE','DELETING','DELETED','FAILED')
  ),
  CONSTRAINT chk_media_asset_deleted CHECK (
    (state = 'DELETED' AND deleted_at IS NOT NULL)
    OR (state <> 'DELETED' AND deleted_at IS NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 作品与应用评测都关联可信应用；公开状态与发布时间共同决定是否可被匿名读取。
CREATE TABLE creation (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  application_id BIGINT UNSIGNED NOT NULL,
  kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  title VARCHAR(128) NOT NULL,
  content TEXT NOT NULL,
  state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  like_count INT UNSIGNED NOT NULL DEFAULT 0,
  published_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_creation_public (state, published_at, id),
  KEY idx_creation_author (user_id, id),
  KEY idx_creation_application (application_id, state, published_at, id),
  CONSTRAINT fk_creation_user FOREIGN KEY (user_id) REFERENCES creator_user (id),
  CONSTRAINT fk_creation_application FOREIGN KEY (application_id) REFERENCES ai_application (id),
  CONSTRAINT chk_creation_kind CHECK (kind IN ('WORK','REVIEW')),
  CONSTRAINT chk_creation_state CHECK (state IN ('PUBLISHED','PRIVATE')),
  CONSTRAINT chk_creation_published CHECK (
    state <> 'PUBLISHED' OR published_at IS NOT NULL
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 引用行是删除保护的事实；同一素材可被多个发布选择，任一引用存在时不得删除文件。
CREATE TABLE creation_media (
  creation_id BIGINT UNSIGNED NOT NULL,
  media_id BIGINT UNSIGNED NOT NULL,
  position TINYINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (creation_id, media_id),
  UNIQUE KEY uk_creation_media_position (creation_id, position),
  KEY idx_creation_media_media (media_id, creation_id),
  CONSTRAINT fk_creation_media_creation FOREIGN KEY (creation_id)
    REFERENCES creation (id) ON DELETE CASCADE,
  CONSTRAINT fk_creation_media_asset FOREIGN KEY (media_id) REFERENCES media_asset (id),
  CONSTRAINT chk_creation_media_position CHECK (position < 9)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE creation_like (
  creation_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (creation_id, user_id),
  KEY idx_creation_like_user (user_id, creation_id),
  CONSTRAINT fk_creation_like_creation FOREIGN KEY (creation_id)
    REFERENCES creation (id) ON DELETE CASCADE,
  CONSTRAINT fk_creation_like_user FOREIGN KEY (user_id) REFERENCES creator_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 自增 id 是粉丝分批游标；唯一键保证关注关系幂等，不能重复累计。
CREATE TABLE follow_edge (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  follow_user_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_follow_edge (user_id, follow_user_id),
  KEY idx_follow_edge_fan (follow_user_id, id),
  KEY idx_follow_edge_following (user_id, follow_user_id),
  CONSTRAINT fk_follow_edge_user FOREIGN KEY (user_id) REFERENCES creator_user (id),
  CONSTRAINT fk_follow_edge_target FOREIGN KEY (follow_user_id) REFERENCES creator_user (id),
  CONSTRAINT chk_follow_edge_self CHECK (user_id <> follow_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Feed 续传沿用原任务游标；新列为 0 表示尚未分批推进。
ALTER TABLE business_outbox
  ADD COLUMN cursor_id BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER user_id;
