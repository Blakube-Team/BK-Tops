package com.blakube.bktops.plugin.message;

import com.blakube.bktops.api.config.ConfigContainer;
import com.blakube.bktops.api.config.ConfigType;
import com.blakube.bktops.plugin.service.config.ConfigService;

import java.util.List;

public class MessageRepository {

    private final ConfigContainer messages;

    public MessageRepository(ConfigService configService) {
        this.messages = configService.provide(ConfigType.LANG);
    }

    public String getMessage(String path) {
        return messages.getString(path, "<red>Message not found: " + path);
    }

    public List<String> getMessageList(String path) {
        return messages.getStringList(path, List.of("<gray>Empty message list: " + path));
    }

}
