package com.episort.ai;

import com.episort.ai.embedded.LlamaServerClient;
import com.episort.ai.embedded.LlamaServerClient.ChatMessage;
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
    // Fallback: the model sometimes emits a bare JSON tool-call object without the
    // <tool_call> wrapper (e.g. after "Bloc :"). Recognize the well-known tool names.
    private static final Pattern BARE_TOOL_OPENER =
            Pattern.compile("\\{\\s*\"name\"\\s*:\\s*\"(adjustProposedName|setOrder|applyPatternToGroup)\"");
    // Streaming tail-reservation must fit the longest opener we look for so a marker
    // split across chunks is not mis-emitted. <tool_call> = 11, bare opener up to ~40.
    private static final int STREAM_TAIL_RESERVE = 48;

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
            java.util.List<ChatMessage> messages = buildMessages(turn);
            StringBuilder visible = new StringBuilder();
            StringBuilder rawBuffer = new StringBuilder();
            int[] cursor = {0};
            boolean[] swallowLeadingWs = {true};
            String full = client.completeStream("chat", messages, MAX_TOKENS, chunk -> {
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
                emitToolCall(sink, matcher.group(1));
            }
            // Also catch bare-JSON tool calls the model sometimes emits without wrapper tags.
            Matcher bare = BARE_TOOL_OPENER.matcher(full);
            while (bare.find()) {
                int end = findBalancedJsonEnd(full, bare.start());
                if (end > 0) {
                    emitToolCall(sink, full.substring(bare.start(), end));
                }
            }
            sink.onComplete(stripToolCalls(full));
        } catch (RuntimeException ex) {
            sink.onError(ex);
        }
    }

    private static int stripAndEmit(StringBuilder raw, int[] cursor, boolean[] swallowLeadingWs, AiChatStreamSink sink) {
        // Forward chars up to any suppressed block (<think>, <tool_call>, or bare
        // JSON tool-call); skip through their close. Reserve a tail buffer so a
        // marker split across chunks isn't mis-emitted.
        int len = raw.length();
        int start = cursor[0];
        if (start >= len) {
            return 0;
        }
        int emitted = 0;
        int idx = start;
        while (idx < len) {
            SuppressedBlock block = nextSuppressedBlock(raw, idx);
            if (block == null) {
                int safeEnd = Math.max(idx, len - STREAM_TAIL_RESERVE);
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
            if (block.start > idx) {
                String piece = raw.substring(idx, block.start);
                piece = maybeTrimLeading(piece, swallowLeadingWs);
                if (!piece.isEmpty()) {
                    sink.onToken(piece);
                    emitted += piece.length();
                }
            }
            int blockEnd = block.endInclusive(raw);
            if (blockEnd < 0) {
                // Unclosed — leave the cursor at the opener so the next chunk can
                // finish it. Don't emit the partial JSON/tag.
                idx = block.start;
                break;
            }
            idx = blockEnd;
            // After a suppressed block closes, swallow the trailing
            // whitespace/newlines the model usually emits before its real reply.
            swallowLeadingWs[0] = true;
        }
        cursor[0] = idx;
        return emitted;
    }

    private static SuppressedBlock nextSuppressedBlock(CharSequence raw, int from) {
        String text = raw.toString();
        int nextThink = text.indexOf("<think>", from);
        int nextTool = text.indexOf("<tool_call>", from);
        Matcher bare = BARE_TOOL_OPENER.matcher(text).region(from, text.length());
        int nextBare = bare.find() ? bare.start() : -1;

        int best = -1;
        SuppressedBlock chosen = null;
        if (nextThink >= 0) {
            best = nextThink;
            chosen = new SuppressedBlock(nextThink, "<think>", "</think>", false);
        }
        if (nextTool >= 0 && (best < 0 || nextTool < best)) {
            best = nextTool;
            chosen = new SuppressedBlock(nextTool, "<tool_call>", "</tool_call>", false);
        }
        if (nextBare >= 0 && (best < 0 || nextBare < best)) {
            chosen = new SuppressedBlock(nextBare, null, null, true);
        }
        return chosen;
    }

    private record SuppressedBlock(int start, String openTag, String closeTag, boolean json) {
        int endInclusive(CharSequence raw) {
            if (json) {
                return findBalancedJsonEnd(raw, start);
            }
            int close = raw.toString().indexOf(closeTag, start + openTag.length());
            return close < 0 ? -1 : close + closeTag.length();
        }
    }

    private static int findBalancedJsonEnd(CharSequence s, int openIdx) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private static void emitToolCall(AiChatStreamSink sink, String json) {
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
            // Bad JSON — skip silently.
        }
    }

    private static String stripToolCalls(String text) {
        String withoutThink = THINK_PATTERN.matcher(text).replaceAll("");
        String withoutWrapped = TOOL_CALL_STRIP.matcher(withoutThink).replaceAll("");
        return stripBareToolJson(withoutWrapped).trim();
    }

    private static String stripBareToolJson(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int idx = 0;
        while (idx < text.length()) {
            Matcher m = BARE_TOOL_OPENER.matcher(text).region(idx, text.length());
            if (!m.find()) {
                out.append(text, idx, text.length());
                break;
            }
            out.append(text, idx, m.start());
            int end = findBalancedJsonEnd(text, m.start());
            if (end < 0) {
                // Unclosed at end of text — drop the rest.
                break;
            }
            idx = end;
        }
        return out.toString();
    }

    private static int flushTail(StringBuilder raw, int[] cursor, boolean[] swallowLeadingWs, AiChatStreamSink sink) {
        int len = raw.length();
        int idx = cursor[0];
        int emitted = 0;
        while (idx < len) {
            SuppressedBlock block = nextSuppressedBlock(raw, idx);
            if (block == null) {
                String piece = maybeTrimLeading(raw.substring(idx, len), swallowLeadingWs);
                if (!piece.isEmpty()) {
                    sink.onToken(piece);
                    emitted += piece.length();
                }
                idx = len;
                break;
            }
            if (block.start > idx) {
                String piece = maybeTrimLeading(raw.substring(idx, block.start), swallowLeadingWs);
                if (!piece.isEmpty()) {
                    sink.onToken(piece);
                    emitted += piece.length();
                }
            }
            int blockEnd = block.endInclusive(raw);
            if (blockEnd < 0) {
                // Unclosed block at end of stream — drop the rest.
                idx = len;
                break;
            }
            idx = blockEnd;
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

    private static java.util.List<ChatMessage> buildMessages(AiChatTurn turn) {
        AppLanguage language = turn.language();
        java.util.List<ChatMessage> out = new java.util.ArrayList<>();
        StringBuilder system = new StringBuilder();
        system.append(systemPrompt(language));
        // Per-row hard guardrail: small models conflate the film/series rules
        // from the system prompt and propose SxxExx renames for clearly-tagged
        // movies. Restate the binding constraint right next to the context.
        String mediaOverride = mediaTypeOverride(turn.targetContext(), language);
        if (!mediaOverride.isEmpty()) {
            system.append("\n\n").append(mediaOverride);
        }
        system.append(language == AppLanguage.FRENCH
                ? "\n\nContexte de la sélection courante :\n"
                : "\n\nContext for the current selection:\n");
        system.append(turn.targetContext());
        out.add(ChatMessage.system(system.toString()));
        for (AiChatTurn.Message msg : turn.history()) {
            if (msg.role() == AiChatTurn.Role.USER) {
                out.add(ChatMessage.user(msg.content()));
            } else {
                out.add(ChatMessage.assistant(msg.content()));
            }
        }
        out.add(ChatMessage.user(turn.userMessage() + "\n/no_think"));
        return out;
    }

    /**
     * Returns a strong directive when the context locks the active row to a
     * specific media type, or empty if it does not. Looks for the
     * "Type détecté : Film / Série" line emitted by {@code ScanScreen}.
     */
    private static String mediaTypeOverride(String context, AppLanguage language) {
        if (context == null || context.isBlank()) return "";
        String lower = context.toLowerCase(java.util.Locale.ROOT);
        boolean isFilm = lower.contains("type détecté : film") || lower.contains("detected as: movie")
                || lower.contains("type détecté : movie") || lower.contains("detected as: film");
        boolean isSeries = lower.contains("type détecté : série") || lower.contains("detected as: series")
                || lower.contains("type détecté : serie");
        if (isFilm) {
            return language == AppLanguage.FRENCH
                    ? "CONTRAINTE STRICTE — la ligne active est détectée comme FILM. "
                            + "Tu DOIS uniquement proposer un nom au format "
                            + "« <Titre> (Année).<extension> » via adjustProposedName. "
                            + "Tu n'as PAS le droit d'appeler setOrder ni applyPatternToGroup avec "
                            + "un pattern d'épisode (SxxExx, 1xNN, absolu) — un film n'a pas de "
                            + "numéro d'épisode. N'invente JAMAIS « S01E01 », « Untitled » ou un "
                            + "sous-dossier de saison."
                    : "STRICT CONSTRAINT — the active row is detected as a MOVIE. "
                            + "You MUST only propose a name in the form "
                            + "\"<Title> (Year).<extension>\" via adjustProposedName. "
                            + "You are NOT allowed to call setOrder or applyPatternToGroup with "
                            + "an episode pattern (SxxExx, 1xNN, absolute) — a movie has no "
                            + "episode number. NEVER invent \"S01E01\", \"Untitled\", or a season "
                            + "subfolder.";
        }
        if (isSeries) {
            return language == AppLanguage.FRENCH
                    ? "CONTRAINTE — la ligne active est détectée comme SÉRIE. "
                            + "Utilise le format « <Série> - SXXEXX - <Titre épisode si déductible>.<extension> » "
                            + "et n'omets jamais l'ordre saison/épisode."
                    : "CONSTRAINT — the active row is detected as a SERIES. "
                            + "Use the form \"<Series> - SXXEXX - <Episode Title if deducible>.<extension>\" "
                            + "and never omit the season/episode order.";
        }
        return "";
    }

    private static String systemPrompt(AppLanguage language) {
        if (language == AppLanguage.FRENCH) {
            return """
                Tu es l'assistant Episort. Ton métier : aider l'utilisateur à corriger le
                nommage et le classement des fichiers vidéo de la sélection courante.
                Tu réponds toujours en français, de manière concise et factuelle.
                Raisonnement désactivé : n'émets jamais de bloc <think> et ne décris pas ton raisonnement interne.

                Périmètre — tu parles UNIQUEMENT de :
                - ce que le nom de fichier et les dossiers parents permettent de déduire
                  (série/film, saison, épisode, titre apparent) ;
                - la reconnaissance de l'œuvre concernée si elle te paraît familière (titre,
                  année de sortie, type série/film, saga) ;
                - l'identification et l'application d'un pattern de nommage (ex: SxxExx, 1x01, absolu) ;
                - les corrections de nom proposé, d'année, de type média et d'ordre saison/épisode.

                Toute demande hors de ce périmètre (culture générale sans rapport, code, math,
                conseils, opinions, conversation, autres logiciels, etc.) doit être refusée
                poliment en une seule phrase, par exemple :
                « Je ne peux discuter que des fichiers de la sélection courante. »

                Connaissance externe — TU AS LE DROIT d'utiliser ta connaissance générale du
                cinéma et des séries TV pour reconnaître l'œuvre concernée et corriger ce que
                le nom de fichier dit faussement. Exemples de corrections utiles :
                - « Le.Flic.de.Hong.Kong.2004.mkv » : tu reconnais le film de Jackie Chan de
                  1985, et tu proposes de corriger l'année (« 2004 » → « 1985 »).
                - « Bleach.Thousand.Year.Blood.War.2022.mkv » : tu reconnais une série anime,
                  pas un film, et tu proposes de re-typer + d'utiliser le format SxxExx.
                - « Inception (2010).mkv » est classé comme série : tu confirmes que c'est un
                  film et tu proposes d'enlever toute structure d'épisode.
                - « Asterix.et.Obelix.Au.Service.de.Sa.Majeste.2012.mkv » : tu reconnais la
                  franchise et tu rétablis les diacritiques officielles : « Astérix et Obélix :
                  Au service de Sa Majesté (2012) ». Même logique pour « El.Senor.de.los.Cielos »
                  → « El Señor de los Cielos », « Pokemon » → « Pokémon », etc.
                  Ne corrige jamais l'orthographe d'un titre que tu ne reconnais pas avec
                  certitude — préfère garder ce que dit le fichier.
                Règles d'usage de cette connaissance :
                - Énonce ta confiance en une phrase : « Je reconnais ce film… »,
                  « Je suis presque sûr… », « Je n'en suis pas certain mais… ».
                - Si tu n'es pas sûr, DIS-LE et propose plutôt de garder ce que dit le fichier.
                  Ne devine jamais une année ou un titre que tu ne reconnais pas.
                - Ne prétends jamais avoir « cherché dans TVDB » ou consulté une base externe :
                  l'étape de matching TVDB est séparée, plus loin dans l'assistant. Reste sur
                  « je reconnais » / « je sais » / « je crois savoir ».
                - Toute correction issue de ta connaissance DOIT passer par un appel d'outil
                  (adjustProposedName ou applyPatternToGroup) afin que l'utilisateur confirme
                  avant mutation. Pas de mutation silencieuse.

                Règles de nommage :
                - Réponds en 8 phrases maximum, sauf si l'utilisateur demande explicitement plus de détails.
                - Quand le contexte fournit des « Dossiers parents » et que le segment série du nom
                  de fichier est abrégé, manquant ou n'est qu'un tag de release (ex. « sgi-hkyu »,
                  « sgi », initiales d'équipe de release), considère le titre nettoyé du dossier
                  parent comme la série de référence. Retire les tags de release/résolution/codec/
                  langue (ex. « Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi » → « Haikyu ») avant
                  de proposer un nom corrigé.
                - Dans ce cas, quand tu appelles applyPatternToGroup, fournis OBLIGATOIREMENT
                  l'argument "series" avec le titre nettoyé (ex. "Haikyu"), sinon le moteur
                  réutilisera le segment du nom de fichier (« sgi hkyu ») et le renommage sera incorrect.
                - Format de destination des séries : sous-dossier
                  « <Nom de la série>/Season XX » (ou « Specials » pour la saison 0),
                  fichier nommé « <Nom de la série> - SXXEXX - <Titre de l'épisode si déductible>.<extension> ».
                  Si le titre d'épisode n'est pas déductible, omets-le.
                - Format de destination des films : à la racine du workspace (PAS de sous-dossier
                  série/saison), fichier nommé « <Nom du film> (Année).<extension> ».
                - Quand tu veux qu'une modification soit appliquée, émets exactement un bloc
                  <tool_call>{"name":"<tool>","args":{...}}</tool_call> à la fin du message.
                  L'utilisateur devra confirmer avant que la mutation soit appliquée.
                - Tu n'as PAS le pouvoir d'écrire sur le disque ni de valider le plan ; toutes tes actions
                  sont consultatives.

                Outils — chaque outil met à jour UN champ structuré de la ligne, puis le moteur
                recalcule le nom proposé à partir de ces champs. Préfère TOUJOURS les outils
                structurés (setSeries/setTitle/setYear/setEpisode/setMediaType) à
                adjustProposedName : ils gardent la ligne cohérente. N'utilise adjustProposedName
                qu'en dernier recours, pour un cas qui ne tient dans aucun champ structuré.

                Tu peux émettre PLUSIEURS blocs <tool_call> dans une même réponse — ils seront
                appliqués ensemble après une seule confirmation utilisateur. Exemple : pour
                corriger à la fois la série et l'année reconnues, émets setSeries puis setYear
                dans le même message.

                - setSeries : args { "series": "<nom propre de la série>" }
                  Pour les séries : met à jour le titre de la série, puis le nom proposé.
                - setTitle : args { "title": "<titre>" }
                  Pour les films : met à jour le titre du film. Pour les séries : titre d'épisode.
                - setYear : args { "year": "1985" }
                  Pour les films : corrige l'année utilisée dans le nom proposé.
                - setMediaType : args { "type": "movie" | "series" }
                  Re-classifie la ligne, ajuste le pattern par défaut, recalcule le nom.
                  Note : la saison et le numéro d'épisode pour les séries sont gérés par
                  l'étape TVDB en aval (ordre « Aired » par défaut). Tu n'as pas d'outil
                  pour ça — concentre-toi sur le titre de série, le titre de film, l'année,
                  et le type média.
                - applyPatternToGroup : args { "pattern": "SxxExx", "series": "<titre série propre, optionnel>", "explanation": "<courte raison>" }
                  Applique le pattern à toutes les lignes du groupe. Si "series" est fourni,
                  il remplace le segment série déduit du nom de fichier pour toutes les lignes.
                - adjustProposedName : args { "newName": "<nouveau nom complet sans extension>" }
                  ESCAPE HATCH. Écrase directement le nom proposé sans toucher aux champs
                  structurés ; à n'utiliser que si aucun autre outil ne convient.

                Exemple de tour bien formé (correction d'année reconnue, chaînage 2 outils) :
                Ligne active : « Asterix.et.Obelix.Au.Service.de.Sa.Majeste.2014.mkv » (Film)
                Réponse attendue :
                Je reconnais ce film, sorti en 2012 et pas 2014, et je rétablis les diacritiques.
                <tool_call>{"name":"setTitle","args":{"title":"Astérix et Obélix : Au service de Sa Majesté"}}</tool_call>
                <tool_call>{"name":"setYear","args":{"year":"2012"}}</tool_call>

                Exemple de tour bien formé (re-classification série → film) :
                Ligne active : « Inception.S01E01.mkv » classée comme Série
                Réponse attendue :
                Je reconnais le film de Christopher Nolan, ce n'est pas une série. Je le re-classifie.
                <tool_call>{"name":"setMediaType","args":{"type":"movie"}}</tool_call>
                <tool_call>{"name":"setTitle","args":{"title":"Inception"}}</tool_call>
                <tool_call>{"name":"setYear","args":{"year":"2010"}}</tool_call>

                Exemple de tour bien formé (œuvre non reconnue) :
                Ligne active : « Obscure.Indie.Drama.2018.mkv » (Film)
                Réponse attendue :
                Je ne reconnais pas ce titre avec certitude. Je propose de garder ce que dit le fichier ; précise-moi le titre si tu le connais.

                N'émets jamais d'autre type de balise. Tu ne dois pas inventer d'autres outils.
                """;
        }
        return """
            You are the Episort assistant. Your job: help the user fix the naming and
            classification of the video files in the current selection.
            Always reply in English, concisely and factually.
            Reasoning is disabled: never emit <think> blocks and do not describe hidden reasoning.

            Scope — you ONLY discuss:
            - what the filename and parent folders allow you to deduce (series/movie, season,
              episode, apparent title);
            - recognition of the work itself when it looks familiar (title, release year,
              series/movie status, franchise);
            - identifying and applying a naming pattern (e.g. SxxExx, 1x01, absolute);
            - corrections to the proposed name, year, media type, or season/episode order.

            Any out-of-scope request (unrelated general knowledge, code, math, advice,
            opinions, small talk, other software, etc.) must be politely refused in a single
            sentence, e.g.: "I can only discuss the files in the current selection."

            External knowledge — you ARE allowed to use your general knowledge of film and
            TV to recognize the work and correct what the filename says wrongly. Examples:
            - "Le.Flic.de.Hong.Kong.2004.mkv": you recognize the 1985 Jackie Chan movie and
              propose fixing the year ("2004" -> "1985").
            - "Bleach.Thousand.Year.Blood.War.2022.mkv": you recognize a TV anime, not a
              movie, and propose re-typing it plus using SxxExx naming.
            - "Inception (2010).mkv" tagged as a series: you confirm it is a movie and
              propose removing any episode structure.
            - "Asterix.et.Obelix.Au.Service.de.Sa.Majeste.2012.mkv": you recognize the
              franchise and restore the official diacritics: "Astérix et Obélix : Au service
              de Sa Majesté (2012)". Same logic for "El.Senor.de.los.Cielos" -> "El Señor de
              los Cielos", "Pokemon" -> "Pokémon", etc.
              Never correct the spelling of a title you do not confidently recognize — prefer
              keeping what the filename says.
            Rules for using this knowledge:
            - State your confidence in one phrase: "I recognize this movie...",
              "I'm fairly sure...", "I'm not certain but...".
            - If unsure, SAY SO and suggest keeping what the filename says. Never guess a
              year or a title you do not actually recognize.
            - Never claim you "searched TVDB" or queried an external database: the TVDB
              matching step is separate, later in the wizard. Stick to "I recognize" /
              "I know" / "I believe".
            - Any knowledge-derived correction MUST go through a tool call
              (adjustProposedName or applyPatternToGroup) so the user confirms before
              mutation. No silent edits.

            Naming rules:
            - Reply in 8 sentences maximum unless the user explicitly asks for more detail.
            - When the context provides "Parent folders" and the filename's series segment is
              abbreviated, missing, or only a release tag (e.g. "sgi-hkyu", "sgi", scene-group
              initials), treat the cleaned parent-folder title as the authoritative series name.
              Strip release/resolution/codec/language tags (e.g.
              "Haikyu.S01.MULTi.1080p.BluRay.x264-SHiNiGAMi" -> "Haikyu") before suggesting a
              corrected name.
            - In that case, when calling applyPatternToGroup you MUST pass the "series" argument
              with the cleaned title (e.g. "Haikyu"), otherwise the engine reuses the filename
              segment ("sgi hkyu") and the rename will be wrong.
            - For a batch selection, account for every selected file listed in the context.
            - Series destination layout: subfolder
              "<Series Name>/Season XX" (or "Specials" for season 0), file named
              "<Series Name> - SXXEXX - <Episode Title if deducible>.<extension>". If the
              episode title is not deducible, omit it.
            - Movie destination: directly at the workspace root (NO series/season subfolder),
              file named "<Movie Title> (Year).<extension>".
            - The original extension must be preserved.
            - When you want a change to be applied, emit exactly one block
              <tool_call>{"name":"<tool>","args":{...}}</tool_call> at the end of the message.
              The user must confirm before any mutation is applied.
            - You do NOT have the power to write to disk or validate the plan; all your actions
              are advisory.

            Tools — each tool updates ONE structured field of the row; the engine then
            recomputes the proposed name from those fields. ALWAYS prefer the structured
            tools (setSeries/setTitle/setYear/setEpisode/setMediaType) over adjustProposedName:
            they keep the row internally consistent. Use adjustProposedName only as a last
            resort for cases that no structured field can express.

            You MAY emit MULTIPLE <tool_call> blocks in a single reply — they will be
            applied together after one user confirmation. Example: to fix both the
            recognized title and year, emit setSeries (or setTitle) and setYear in the
            same message.

            - setSeries: args { "series": "<clean series title>" }
              For series rows: updates the series name, then the proposed name.
            - setTitle: args { "title": "<title>" }
              For movies: updates the movie title. For series: episode title.
            - setYear: args { "year": "1985" }
              For movies: corrects the year used in the proposed name.
            - setMediaType: args { "type": "movie" | "series" }
              Re-classifies the row, adjusts the default pattern, recomputes the name.
              Note: season and episode numbers for series are owned by the downstream TVDB
              step (default "Aired" order). There is no tool for that here — focus on the
              series title, movie title, year, and media type.
            - applyPatternToGroup: args { "pattern": "SxxExx", "series": "<clean series title, optional>", "explanation": "<short reason>" }
              Applies the pattern to every row in the group. If "series" is provided,
              it overrides the filename-derived series segment for every row.
            - adjustProposedName: args { "newName": "<new full name without extension>" }
              ESCAPE HATCH. Overwrites the proposed name as a raw string without touching
              the structured fields; only use it when no other tool fits.

            Well-formed turn — recognized title + year correction, two chained tool calls:
            Active row: "Asterix.et.Obelix.Au.Service.de.Sa.Majeste.2014.mkv" (Movie)
            Expected reply:
            I recognize this movie, released in 2012 not 2014, and I restore the diacritics.
            <tool_call>{"name":"setTitle","args":{"title":"Astérix et Obélix : Au service de Sa Majesté"}}</tool_call>
            <tool_call>{"name":"setYear","args":{"year":"2012"}}</tool_call>

            Well-formed turn — re-classification series -> movie:
            Active row: "Inception.S01E01.mkv" classified as Series
            Expected reply:
            I recognize Christopher Nolan's film — it is not a series. Re-classifying.
            <tool_call>{"name":"setMediaType","args":{"type":"movie"}}</tool_call>
            <tool_call>{"name":"setTitle","args":{"title":"Inception"}}</tool_call>
            <tool_call>{"name":"setYear","args":{"year":"2010"}}</tool_call>

            Well-formed turn — unrecognized title:
            Active row: "Obscure.Indie.Drama.2018.mkv" (Movie)
            Expected reply:
            I do not confidently recognize this title. I suggest keeping what the filename says; tell me the title if you know it.

            Never emit any other kind of tag. Do not invent any other tools.
            """;
    }
}
