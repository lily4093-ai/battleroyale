package com.example.battleroyale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Converts legacy '&sect;'-code strings (as used throughout the original plugin's messages)
 * into a proper vanilla Component, and provides small chat/actionbar/title helpers.
 * Vanilla no longer auto-renders raw section-sign codes inside a JSON text component,
 * so this does the same job Bukkit's ChatColor/TextComponent#fromLegacyText did.
 */
public final class BRText {

    private BRText() {
    }

    public static MutableComponent of(String legacy) {
        MutableComponent result = Component.literal("");
        if (legacy == null || legacy.isEmpty()) {
            return result;
        }

        StringBuilder buffer = new StringBuilder();
        ChatFormatting color = null;
        boolean bold = false, italic = false, underline = false, strikethrough = false, obfuscated = false;

        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                char code = Character.toLowerCase(legacy.charAt(i + 1));
                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    if (buffer.length() > 0) {
                        result.append(applyStyle(Component.literal(buffer.toString()), color, bold, italic, underline, strikethrough, obfuscated));
                        buffer.setLength(0);
                    }
                    if (fmt == ChatFormatting.RESET) {
                        color = null;
                        bold = italic = underline = strikethrough = obfuscated = false;
                    } else if (fmt.isColor()) {
                        color = fmt;
                        bold = italic = underline = strikethrough = obfuscated = false;
                    } else {
                        switch (fmt) {
                            case BOLD -> bold = true;
                            case ITALIC -> italic = true;
                            case UNDERLINE -> underline = true;
                            case STRIKETHROUGH -> strikethrough = true;
                            case OBFUSCATED -> obfuscated = true;
                            default -> {
                            }
                        }
                    }
                    i++;
                    continue;
                }
            }
            buffer.append(c);
        }
        if (buffer.length() > 0) {
            result.append(applyStyle(Component.literal(buffer.toString()), color, bold, italic, underline, strikethrough, obfuscated));
        }
        return result;
    }

    private static Component applyStyle(MutableComponent component, ChatFormatting color, boolean bold, boolean italic,
                                         boolean underline, boolean strikethrough, boolean obfuscated) {
        if (color != null) component = component.withStyle(color);
        if (bold) component = component.withStyle(ChatFormatting.BOLD);
        if (italic) component = component.withStyle(ChatFormatting.ITALIC);
        if (underline) component = component.withStyle(ChatFormatting.UNDERLINE);
        if (strikethrough) component = component.withStyle(ChatFormatting.STRIKETHROUGH);
        if (obfuscated) component = component.withStyle(ChatFormatting.OBFUSCATED);
        return component;
    }

    public static void broadcast(MinecraftServer server, String legacy) {
        server.getPlayerList().broadcastSystemMessage(of(legacy), false);
    }

    public static void send(ServerPlayer player, String legacy) {
        player.sendSystemMessage(of(legacy));
    }

    public static void actionBar(ServerPlayer player, String legacy) {
        player.displayClientMessage(of(legacy), true);
    }
}
