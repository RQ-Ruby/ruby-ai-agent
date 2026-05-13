-- ============================================
-- 用户表（行旅 AI · user 模块）
-- 参考鱼皮项目风格：逻辑删除、角色字段、时间戳
-- ============================================

USE `ruby-ai-agent`;

CREATE TABLE IF NOT EXISTS `user`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `userAccount`  VARCHAR(256) NOT NULL COMMENT '账号（登录用，唯一）',
    `userPassword` VARCHAR(512) NOT NULL COMMENT '密码（MD5(SALT + password)）',
    `userName`     VARCHAR(256) NULL COMMENT '用户昵称',
    `userAvatar`   VARCHAR(1024) NULL COMMENT '用户头像',
    `userProfile`  VARCHAR(512) NULL COMMENT '用户简介',
    `userRole`     VARCHAR(256) NOT NULL DEFAULT 'user' COMMENT '用户角色：user / admin / ban',
    `editTime`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
    `createTime`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除（逻辑删除：0 否 1 是）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_userAccount` (`userAccount`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';
