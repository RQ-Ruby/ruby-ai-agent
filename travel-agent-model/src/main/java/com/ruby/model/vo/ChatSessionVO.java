package com.ruby.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ChatSessionVO implements Serializable {

    private String chatId;

    private String title;

    private String lastMessagePreview;

    private LocalDateTime updatedAt;
}
