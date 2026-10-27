-- 用户身份沿用手机号唯一约束，时间点统一按 UTC 连接约定保存。
CREATE TABLE creator_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  phone VARCHAR(11) NOT NULL,
  password VARCHAR(128) NOT NULL DEFAULT '',
  nick_name VARCHAR(32) NOT NULL DEFAULT '',
  icon VARCHAR(255) NOT NULL DEFAULT '',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_creator_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE creator_role (
  code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 角色关系不可重复；不允许通过删除身份记录隐式清除权限关系。
CREATE TABLE creator_user_role (
  user_id BIGINT UNSIGNED NOT NULL,
  role_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, role_code),
  CONSTRAINT fk_creator_user_role_user FOREIGN KEY (user_id) REFERENCES creator_user (id),
  CONSTRAINT fk_creator_user_role_role FOREIGN KEY (role_code) REFERENCES creator_role (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO creator_role(code) VALUES ('USER'), ('ADMIN');
