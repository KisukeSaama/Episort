package com.episort.ui.scan;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Small JavaFX-only Markdown subset renderer for trusted local display, without HTML/WebView. */
public final class AiChatMarkdownRenderer {
    private AiChatMarkdownRenderer() {
    }

    public static VBox render(String markdown) {
        VBox box = new VBox(4);
        box.getStyleClass().add("ai-chat-markdown");
        for (Block block : blocks(markdown)) {
            Label label = new Label(block.text());
            label.setWrapText(true);
            label.getStyleClass().add("ai-chat-md-" + block.type());
            box.getChildren().add(label);
        }
        return box;
    }

    static List<Block> blocks(String markdown) {
        String text = markdown == null ? "" : markdown.strip();
        if (text.isBlank()) {
            return List.of();
        }
        List<Block> blocks = new ArrayList<>();
        boolean inCode = false;
        StringBuilder code = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            if (line.strip().startsWith("```")) {
                if (inCode) {
                    blocks.add(new Block("code", code.toString().stripTrailing()));
                    code.setLength(0);
                }
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                code.append(line).append('\n');
                continue;
            }
            String stripped = line.strip();
            if (stripped.isBlank()) {
                continue;
            }
            if (stripped.startsWith("#")) {
                blocks.add(new Block("heading", stripped.replaceFirst("^#{1,6}\\s*", "")));
            } else if (stripped.startsWith("- ") || stripped.startsWith("* ")) {
                blocks.add(new Block("bullet", "• " + inline(stripped.substring(2))));
            } else {
                blocks.add(new Block("paragraph", inline(stripped)));
            }
        }
        if (inCode && !code.isEmpty()) {
            blocks.add(new Block("code", code.toString().stripTrailing()));
        }
        return blocks;
    }

    private static String inline(String text) {
        return text.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "$1")
                .replaceAll("_(.+?)_", "$1");
    }

    public record Block(String type, String text) {
    }
}
