package com.episort.ai;

import com.episort.ai.embedded.LlamaServerClient;
import com.episort.ui.AppLanguage;
import com.episort.workflow.AiWorkflowGate;
import com.episort.workflow.AiWorkflowGateResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streaming chat backend tied to the bundled local Qwen3 model. Replies in the UI language
 * carried by the turn, with a strict system prompt that constrains the model to discuss
 * episode-naming patterns and emit tool calls only via {@code <tool_call>{...}</tool_call>} blocks.
 */
public final class AiChatService implements AiChatBackend {
    private static final int MAX_TOKENS = 2048;
    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("<tool_call>\\s*(\\{.*?\\})\\s*(?:</tool_call>|\\z)", Pattern.DOTALL);
    private static final Pattern TOOL_CALL_STRIP =
            Pattern.compile("<tool_call>.*?(?:</tool_call>|\\z)", Pattern.DOTALL);
    private static final Pattern THINK_PATTERN =
            Pattern.compile("<think>.*?(?:</think>|\\z)\\s*", Pattern.DOTALL);

    private final AiWorkflowGate gate;
    private final Supplier<java.util.Optional<LlamaServerClient>> clientSupplier;
    private final ExecutorService executor;

    public AiChatService(AiWorkflowGate gate, Supplier<java.util.Optional<LlamaServerClient>> clientSupplier) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.clientSupplier = Objects.requireNonNull(clientSupplier, "clientSupplier");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "episort-ai-chat");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public boolean isAvailable() {
        // The strict prerequisite gate (GPU/VRAM/etc.) guards advisory pattern refinement.
        // For the chat we trust the embedded runtime: if the local server is reachable,
        // chat is usable — the same machine that just answered "Lancer un test" can chat.
        return clientSupplier.get().isPresent();
    }

    @Override
    public void send(AiChatTurn turn, AiChatStreamSink sink) {
        executor.submit(() -> runTurn(turn, sink));
    }

    private void runTurn(AiChatTurn turn, AiChatStreamSink sink) {
        try {
            LlamaServerClient client = clientSupplier.get().orElse(null);
            if (client == null) {
                sink.onError(new IllegalStateException("No runtime client"));
                return;
            }
            if (!client.isHealthy()) {
                AiWorkflowGateResult gated = gate.requireAiAvailable();
                String detail = gated.error().map(e -> e.safeMessage()).orElse("Local AI unavailable");
                sink.onError(new IllegalStateException(detail));
                return;
            }
            String prompt = buildPrompt(turn);
            StringBuilder visible = new StringBuilder();
            StringBuilder rawBuffer = new StringBuilder();
            int[] cursor = {0};
            boolean[] swallowLeadingWs = {true};
            String full = client.completeStream("chat", prompt, MAX_TOKENS, chunk -> {
                rawBuffer.append(chunk);
                int newLen = stripAndEmit(rawBuffer, cursor, swallowLeadingWs, sink);
                if (newLen > 0) {
                    visible.append(rawBuffer, cursor[0] - newLen, cursor[0]);
                }
            });
            // Flush the tail buffer the streaming pass deliberately held back
            // (it reserves the last ~11 chars in case a marker is split).
            int tailLen = flushTail(rawBuffer, cursor, swallowLeadingWs, sink);
            if (tailLen > 0) {
                visible.append(rawBuffer, cursor[0] - tailLen, cursor[0]);
            }
            // Finalize: parse all tool calls in the full response, emit each.
            Matcher matcher = TOOL_CALL_PATTERN.matcher(full);
            while (matcher.find()) {
                String json = matcher.group(1);
                try {
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    String name = obj.has("name") ? obj.get("name").getAsString() : "";
                    JsonObject args = obj.has("args") && obj.get("args").isJsonObject()
                            ? obj.getAsJsonObject("args")
                            : new JsonObject();
                    if (!name.isEmpty()) {
                        sink.onToolCall(new AiChatToolCall(name, args, json));
                    }
                } catch (JsonSyntaxException ignored) {
                    // Bad JSON inside tool_call — skip silently.
                }
            }
            sink.onComplete(stripToolCalls(full));
        } catch (RuntimeException ex) {
            sink.onError(ex);
        }
    }

    private static int stripAndEmit(StringBuilder raw, int[] cursor, boolean[] swallowLeadingWs, AiChatStreamSink sink) {
        // Forward chars up to any suppressed block (<think> or <tool_call>); skip
        // through their close tags. Reserve a tail buffer so a marker split
        // across chunks isn't mis-emitted.
        int len = raw.length();
        int start = cursor[0];
        if (start >= len) {
            return 0;
        }
        int emitted = 0;
        int idx = start;
        while (idx < len) {
            int nextThink = raw.indexOf("<think>", idx);
            int nextTool = raw.indexOf("<tool_call>", idx);
            int next = -1;
            String openTag = null;
            String closeTag = null;
            if (nextThink >= 0 && (nextTool < 0 || nextThink < nextTool)) {
                next = nextThink;
                openTag = "<think>";
                closeTag = "</think>";
            } else if (nextTool >= 0) {
                next = nextTool;
                openTag = "<tool_call>";
                closeTag = "</tool_call>";
            }
            if (next < 0) {
                int safeEnd = Math.max(idx, len - 11);
                if (safeEnd > idx) {
                    String piece = raw.substring(idx, safeEnd);
                    piece = maybeTrimLeading(piece, swallowLeadingWs);
                    if (!piece.isEmpty()) {
                        sink.onToken(piece);
                        emitted += piece.length();
                    }
                    idx = safeEnd;
                }
                break;
            }
            if (next > idx) {
                String piece = raw.substring(idx, next);
                piece = maybeTrimLeading(piece, swallowLeadingWs);
                if (!piece.isEmpty()) {
                    sink.onToken(piece);
                    emitted += piece.length();
                }
            }
            int close = raw.indexOf(closeTag, next + openTag.length());
            if (close < 0) {
                idx = next;
                break;
            }
            idx = close + closeTag.length();
            // After a suppressed block closes, swallow the trailing
            // whitespace/newlines the model usually emits before its real reply.
            swallowLeadingWs[0] = true;
        }
        cursor[0] = idx;
        return emitted;
    }

    private static String stripToolCalls(String text) {
        String withoutThink = THINK_PATTERN.matcher(text).replaceAll("");
        return TOOL_CALL_STRIP.matcher(withoutThink).replaceAll("").trim();
    }

    private static int flushTail(StringBuilder raw, int[] cursor, boolean[] swallowLeadingWs, AiChatStreamSink sink) {
        int len = raw.length();
        int idx = cursor[0];
        int emitted = 0;
        while (idx < len) {
            int nextThink = raw.indexOf("<think>", idx);
            int nextTool = raw.indexOf("<tool_call>", idx);
            int next = -1;
            String openTag = null;
            String closeTag = null;
            if (nextThink >= 0 && (nextTool < 0 || nextThink < nextTool)) {
                next = nextThink; openTag = "<think>"; closeTag = "</think>";
            } else if (nextTool >= 0) {
                next = nextTool; openTag = "<tool_call>"; closeTag = "</tool_call>";
            }
            if (next < 0) {
                String piece = maybeTrimLeading(raw.substring(idx, len), swallowLeadingWs);
                if (!piece.isEmpty()) {
                    sink.onToken(piece);
                    emitted += piece.length();
                }
                idx = len;
                break;
            }
            if (next > idx) {
                String piece = maybeTrimLeading(raw.substring(idx, next), swallowLeadingWs);
                if (!piece.isEmpty()) {
                    sink.onToken(piece);
                    emitted += piece.length();
                }
            }
            int close = raw.indexOf(closeTag, next + openTag.length());
            if (close < 0) {
                // Unclosed block at end of stream — drop the rest.
                idx = len;
                break;
            }
            idx = close + closeTag.length();
            swallowLeadingWs[0] = true;
        }
        cursor[0] = idx;
        return emitted;
    }

    private static String maybeTrimLeading(String piece, boolean[] swallowLeadingWs) {
        if (!swallowLeadingWs[0]) {
            return piece;
        }
        int i = 0;
        while (i < piece.length() && Character.isWhitespace(piece.charAt(i))) {
            i++;
        }
        if (i == piece.length()) {
            return "";
        }
        swallowLeadingWs[0] = false;
        return piece.substring(i);
    }

    private static String buildPrompt(AiChatTurn turn) {
        AppLanguage language = turn.language();
        StringBuilder sb = new StringBuilder();
        sb.append("<|im_start|>system\n");
        sb.append(systemPrompt(language));
        sb.append(language == AppLanguage.FRENCH
                ? "\n\nContexte de la sélection courante :\n"
                : "\n\nContext for the current selection:\n");
        sb.append(turn.targetContext());
        sb.append("<|im_end|>\n");
        for (AiChatTurn.Message msg : turn.history()) {
            String role = msg.role() == AiChatTurn.Role.USER ? "user" : "assistant";
            sb.append("<|im_start|>").append(role).append('\n')
              .append(msg.content()).append("<|im_end|>\n");
        }
        sb.append("<|im_start|>user\n").append(turn.userMessage()).append("\n/no_think<|im_end|>\n");
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }

    private static String systemPrompt(AppLanguage language) {
        if (language == AppLanguage.FRENCH) {
            return """
                Tu es l'assistant Episort de détection de patterns de nommage d'épisodes.
                Tu réponds toujours en français, de manière concise et factuelle.
                Raisonnement désactivé : n'émets jamais de bloc <think> et ne décris pas ton raisonnement interne.

                Périmètre strict — tu ne parles QUE de :
                - l'association des fichiers du contexte avec leur série/film, saison et épisode ;
                - l'identification et l'application d'un pattern de nommage (ex: SxxExx, 1x01, absolu) ;
                - les corrections de nom proposé, de correspondance TVDB, d'ordre saison/épisode.

                Toute autre demande (culture générale, code, math, conseils, opinions, conversation,
                autres logiciels, autres médias non listés dans le contexte, etc.) doit être refusée
                poliment en une seule phrase, par exemple :
                « Je ne peux discuter que de l'association des fichiers du groupe courant. »
                Ne tente pas d'y répondre même partiellement, et ne propose pas de sujet alternatif
                hors de ce périmètre.

                Ton rôle :
                - Aider à identifier le pattern de nommage (ex: SxxExx, 1x01, absolu) à partir des fichiers
                  du groupe courant.
                - Proposer des corrections de nom proposé, de correspondance TVDB, d'ordre saison/épisode
                  ou de pattern à appliquer au groupe entier.
                - Toujours justifier brièvement chaque suggestion en t'appuyant sur les fichiers réels
                  listés dans le contexte.

                Règles strictes :
                - Réponds en 8 phrases maximum, sauf si l'utilisateur demande explicitement plus de détails.
                - N'invente jamais un titre TVDB hors des candidats fournis dans le contexte.
                - Quand le contexte fournit des « Dossiers parents » et que le segment série du nom
                  de fichier est abrégé, manquant ou n'est qu'un tag de release (ex. « sgi-hkyu »,
                  « sgi », initiales d'équipe de release), considère le titre nettoyé du dossier
                  parent comme la série de référence. Retire les tags de release/résolution/codec/
                  langue (ex. « Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi » → « Haikyu ») avant
                  de proposer un nom corrigé ou une correspondance TVDB.
                - Dans ce cas, quand tu appelles applyPatternToGroup, fournis OBLIGATOIREMENT
                  l'argument "series" avec le titre nettoyé issu du dossier parent (ex. "Haikyu"),
                  sinon le moteur réutilisera le segment du nom de fichier (« sgi hkyu ») et le
                  renommage sera incorrect.
                - Format de destination des séries : sous-dossier
                  « <Nom de la série en anglais>/Season XX » (ou « Specials » pour la saison 0),
                  fichier nommé « <Nom de la série en anglais> - SXXEXX - <Titre de l'épisode en anglais>.<extension> ».
                - Format de destination des films : à la racine du workspace (PAS de sous-dossier
                  série/saison), fichier nommé « <Nom du film en anglais> (Année).<extension> ».
                - Quand tu veux qu'une modification soit appliquée, émets exactement un bloc
                  <tool_call>{"name":"<tool>","args":{...}}</tool_call> à la fin du message.
                  L'utilisateur devra confirmer avant que la mutation soit appliquée.
                - Tu n'as PAS le pouvoir d'écrire sur le disque ni de valider le plan ; toutes tes actions
                  sont consultatives.

                Outils disponibles :
                - selectTvdbMatch : args { "candidate": "<titre exact>" }
                  Choisit la correspondance TVDB de la ligne active parmi les candidats du contexte.
                - adjustProposedName : args { "newName": "<nouveau nom complet sans extension>" }
                  Modifie le nom proposé pour la ligne active.
                - setOrder : args { "order": "S01E02" }
                  Ajuste la saison/épisode de la ligne active.
                - applyPatternToGroup : args { "pattern": "SxxExx", "series": "<titre série propre, optionnel>", "explanation": "<courte raison>" }
                  Applique le pattern à toutes les lignes du groupe parent de la ligne active.
                  Si "series" est fourni, il remplace le segment série déduit du nom de fichier
                  pour toutes les lignes du groupe.

                N'émets jamais d'autre type de balise. Tu ne dois pas inventer d'autres outils.
                """;
        }
        return """
            You are the Episort assistant for episode-naming pattern detection.
            Always reply in English, concisely and factually.
            Reasoning is disabled: never emit <think> blocks and do not describe hidden reasoning.

            Strict scope — you ONLY discuss:
            - associating the files in the context with their series/movie, season and episode;
            - identifying and applying a naming pattern (e.g. SxxExx, 1x01, absolute);
            - corrections to the proposed name, TVDB match, or season/episode order.

            Any other request (general knowledge, code, math, advice, opinions, small talk,
            other software, other media not listed in the context, etc.) must be politely
            refused in a single sentence, for example:
            "I can only discuss file associations for the current group."
            Do not attempt a partial answer and do not suggest alternative topics outside
            this scope.

            Your role:
            - Help identify the naming pattern (e.g. SxxExx, 1x01, absolute) from the files in the
              current group.
            - Propose corrections to the proposed name, the TVDB match, the season/episode order,
              or the pattern to apply to the whole group.
            - Always briefly justify each suggestion using the actual files listed in the context.

            Strict rules:
            - Reply in 8 sentences maximum unless the user explicitly asks for more detail.
            - Never invent a TVDB title outside the candidates provided in the context.
            - When the context provides "Parent folders" and the filename's series segment is
              abbreviated, missing, or only a release tag (e.g. "sgi-hkyu", "sgi", scene-group
              initials), treat the cleaned parent-folder title as the authoritative series name.
              Strip release/resolution/codec/language tags (e.g.
              "Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi" -> "Haikyu") before suggesting a
              corrected name or a TVDB match.
            - In that case, when calling applyPatternToGroup you MUST pass the "series" argument
              with the cleaned parent-folder title (e.g. "Haikyu"), otherwise the engine reuses
              the filename segment ("sgi hkyu") and the rename will be wrong.
            - For a batch selection, account for every selected file listed in the context.
            - Series destination layout: subfolder
              "<English Series Name>/Season XX" (or "Specials" for season 0), file named
              "<English Series Name> - SXXEXX - <English Episode Title>.<extension>".
            - Movie destination: directly at the workspace root (NO series/season subfolder),
              file named "<English Movie Title> (Year).<extension>".
            - The original extension must be preserved.
            - When you want a change to be applied, emit exactly one block
              <tool_call>{"name":"<tool>","args":{...}}</tool_call> at the end of the message.
              The user must confirm before any mutation is applied.
            - You do NOT have the power to write to disk or validate the plan; all your actions
              are advisory.

            Available tools:
            - selectTvdbMatch: args { "candidate": "<exact title>" }
              Picks the TVDB match for the active row among the candidates in the context.
            - adjustProposedName: args { "newName": "<new full name without extension>" }
              Updates the proposed name for the active row.
            - setOrder: args { "order": "S01E02" }
              Adjusts the season/episode of the active row.
            - applyPatternToGroup: args { "pattern": "SxxExx", "series": "<clean series title, optional>", "explanation": "<short reason>" }
              Applies the pattern to every currently selected row when a batch is selected.
              If "series" is provided, it overrides the filename-derived series segment for
              every row in the group.

            Never emit any other kind of tag. Do not invent any other tools.
            """;
    }
}
