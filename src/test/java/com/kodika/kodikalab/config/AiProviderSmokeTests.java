package com.kodika.kodikalab.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;

/**
 * Real calls to the selected provider: they use quota and may cost money, so they only run on demand with
 * {@code -Dai.smoke=ai-groq} or {@code -Dai.smoke=ai-openai}. Keys are read from the environment or .env.
 * Transcription also needs {@code -Dai.smoke.audio=<file>} with Spanish speech about "programacion competitiva".
 * No database is involved.
 */
@EnabledIfSystemProperty(named = "ai.smoke", matches = "ai-(groq|openai)")
class AiProviderSmokeTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(
                    RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
                    SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class,
                    OpenAiChatAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class,
                    OpenAiAudioTranscriptionAutoConfiguration.class))
            .withSystemProperties("spring.profiles.active=" + System.getProperty("ai.smoke"));

    @Test
    void chatAnswers() {
        runner.run(context -> {
            String answer = context.getBean(ChatModel.class).call("Reply with the single word: ready");
            assertThat(answer).isNotBlank();
        });
    }

    @Test
    void embeddingsReturnVectors() {
        runner.run(context -> {
            float[] vector = context.getBean(EmbeddingModel.class).embed("competitive programming");
            System.out.println("Embedding dimensions: " + vector.length);
            // Both profiles share this size, which fits the 2000-dimension limit of a pgvector HNSW index.
            assertThat(vector).hasSize(1536);
        });
    }

    @Test
    @EnabledIfSystemProperty(named = "ai.smoke.audio", matches = ".+")
    void spanishSpeechIsTranscribed() {
        runner.run(context -> {
            var prompt = new AudioTranscriptionPrompt(new FileSystemResource(System.getProperty("ai.smoke.audio")),
                    OpenAiAudioTranscriptionOptions.builder().language("es").build());
            String text = context.getBean(OpenAiAudioTranscriptionModel.class).call(prompt).getResult().getOutput();
            System.out.println("Transcription: " + text);
            assertThat(text).containsIgnoringCase("competitiva");
        });
    }
}
