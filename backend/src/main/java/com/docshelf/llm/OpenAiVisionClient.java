// VisionClient over the Responses API with an input_image data URL and strict JSON schema output; audited as VISION_CALL
package com.docshelf.llm;

import com.docshelf.audit.AuditEvent;
import com.docshelf.audit.AuditService;
import com.docshelf.audit.entity.AuditAction;
import com.docshelf.audit.entity.AuditOrigin;
import com.docshelf.common.UpstreamException;
import com.docshelf.config.DocshelfProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenAiVisionClient implements VisionClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiVisionClient.class);
    private static final int MAX_OUTPUT_TOKENS = 2000;

    private final OpenAIClient client;
    private final DocshelfProperties props;
    private final LlmCallLogService callLog;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public OpenAiVisionClient(OpenAIClient client, DocshelfProperties props, LlmCallLogService callLog,
                              AuditService audit, ObjectMapper objectMapper) {
        this.client = client;
        this.props = props;
        this.callLog = callLog;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T extractFromImage(byte[] image, String mimeType, String instructions, Class<T> schemaType) {
        if (!props.openai().visionEnabled()) {
            throw new UpstreamException("Vision path is disabled (docshelf.openai.vision-enabled=false)");
        }
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("image is empty");
        }
        String model = props.openai().chatModel();
        String dataUrl = "data:" + (mimeType == null ? "image/jpeg" : mimeType) + ";base64,"
                + Base64.getEncoder().encodeToString(image);
        ResponseInputItem.Message message = ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER)
                .addContent(ResponseInputText.builder().text(instructions == null ? "" : instructions).build())
                .addContent(ResponseInputImage.builder()
                        .detail(ResponseInputImage.Detail.HIGH)
                        .imageUrl(dataUrl)
                        .build())
                .build();
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .inputOfResponse(List.of(ResponseInputItem.ofMessage(message)))
                .maxOutputTokens((long) MAX_OUTPUT_TOKENS)
                .temperature(0.0)
                .store(false)
                .text(OpenAiSupport.strictJsonFormat(schemaType))
                .build();
        long t0 = System.nanoTime();
        try {
            Response response = client.responses().create(params);
            int latency = (int) ((System.nanoTime() - t0) / 1_000_000);
            String json = OpenAiSupport.outputText(response);
            callLog.log(new LlmCallLogService.Entry("vision", model, null, null,
                    OpenAiSupport.inputTokens(response), OpenAiSupport.outputTokens(response), null, true,
                    latency, LlmCallLogService.STATUS_OK, null, response.id()));
            audit.record(AuditEvent.of(AuditAction.VISION_CALL, AuditOrigin.SYSTEM)
                    .detail("model", model).detail("schema", schemaType.getSimpleName())
                    .detail("imageBytes", image.length)
                    .detail("inputTokens", OpenAiSupport.inputTokens(response))
                    .detail("outputTokens", OpenAiSupport.outputTokens(response)));
            try {
                return objectMapper.readValue(json, schemaType);
            } catch (IOException e) {
                throw new UpstreamException("OpenAI vision returned JSON that does not match "
                        + schemaType.getSimpleName(), e);
            }
        } catch (RuntimeException e) {
            int latency = (int) ((System.nanoTime() - t0) / 1_000_000);
            UpstreamException mapped = OpenAiSupport.mapError("vision", e);
            callLog.log(new LlmCallLogService.Entry("vision", model, null, null, null, null, null, true,
                    latency, LlmCallLogService.STATUS_ERROR, OpenAiSupport.safeMessage(mapped), null));
            log.warn("Vision call failed bytes={} retryable={}", image.length, mapped.retryable());
            throw mapped;
        }
    }
}
