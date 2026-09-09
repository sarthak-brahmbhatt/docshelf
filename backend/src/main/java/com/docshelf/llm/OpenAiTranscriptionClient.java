// TranscriptionClient over the OpenAI audio transcriptions API (gpt-4o-transcribe); audio bytes are sent as-is
package com.docshelf.llm;

import com.docshelf.common.UpstreamException;
import com.docshelf.config.DocshelfProperties;
import com.openai.client.OpenAIClient;
import com.openai.core.MultipartField;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenAiTranscriptionClient implements TranscriptionClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTranscriptionClient.class);
    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("audio/mpeg", "mp3"), Map.entry("audio/mp3", "mp3"), Map.entry("audio/mp4", "m4a"),
            Map.entry("audio/x-m4a", "m4a"), Map.entry("audio/m4a", "m4a"), Map.entry("audio/aac", "m4a"),
            Map.entry("audio/wav", "wav"), Map.entry("audio/x-wav", "wav"), Map.entry("audio/wave", "wav"),
            Map.entry("audio/webm", "webm"), Map.entry("video/webm", "webm"), Map.entry("audio/ogg", "ogg"),
            Map.entry("audio/opus", "ogg"), Map.entry("audio/flac", "flac"), Map.entry("audio/x-flac", "flac"),
            Map.entry("audio/mpga", "mpga"));

    private final OpenAIClient client;
    private final DocshelfProperties props;
    private final LlmCallLogService callLog;

    public OpenAiTranscriptionClient(OpenAIClient client, DocshelfProperties props, LlmCallLogService callLog) {
        this.client = client;
        this.props = props;
        this.callLog = callLog;
    }

    @Override
    public Transcript transcribe(byte[] audio, String mimeType, String languageHint) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("audio is empty");
        }
        String model = props.openai().transcriptionModel();
        String ext = extensionFor(mimeType);
        TranscriptionCreateParams.Builder b = TranscriptionCreateParams.builder()
                .file(MultipartField.<InputStream>builder()
                        .value(new ByteArrayInputStream(audio))
                        .filename("audio." + ext)
                        .contentType(mimeType == null ? "application/octet-stream" : mimeType)
                        .build())
                .model(model)
                .responseFormat(AudioResponseFormat.JSON);
        String lang = isoLanguage(languageHint);
        if (lang != null) {
            b.language(lang);
        }
        long t0 = System.nanoTime();
        try {
            TranscriptionCreateResponse response = client.audio().transcriptions().create(b.build());
            int latency = (int) ((System.nanoTime() - t0) / 1_000_000);
            String text;
            String language = lang;
            Double duration = null;
            if (response.isVerbose()) {
                text = response.asVerbose().text();
                language = response.asVerbose().language();
                duration = response.asVerbose().duration();
            } else {
                text = response.asTranscription().text();
            }
            callLog.log(new LlmCallLogService.Entry("transcribe", model, null, null, null, null, null, false,
                    latency, LlmCallLogService.STATUS_OK, null, null));
            return new Transcript(text, language, duration);
        } catch (RuntimeException e) {
            int latency = (int) ((System.nanoTime() - t0) / 1_000_000);
            UpstreamException mapped = OpenAiSupport.mapError("transcription", e);
            callLog.log(new LlmCallLogService.Entry("transcribe", model, null, null, null, null, null, false,
                    latency, LlmCallLogService.STATUS_ERROR, OpenAiSupport.safeMessage(mapped), null));
            log.warn("Transcription failed bytes={} retryable={}", audio.length, mapped.retryable());
            throw mapped;
        }
    }

    static String extensionFor(String mimeType) {
        if (mimeType == null) {
            return "webm";
        }
        String base = mimeType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return EXTENSIONS.getOrDefault(base, "webm");
    }

    /** "en-IN" -> "en"; null/blank -> null (model auto-detects). */
    static String isoLanguage(String hint) {
        if (hint == null || hint.isBlank()) {
            return null;
        }
        String h = hint.trim();
        int dash = h.indexOf('-');
        return (dash > 0 ? h.substring(0, dash) : h).toLowerCase(Locale.ROOT);
    }
}
