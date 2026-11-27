package com.kodika.kodikalab.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Checks which AI models each profile enables. No request is sent to any provider. */
class AiProviderProfilesTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(
                    RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
                    SpringAiRetryAutoConfiguration.class, ToolCallingAutoConfiguration.class,
                    OpenAiChatAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class,
                    OpenAiAudioTranscriptionAutoConfiguration.class, OpenAiAudioSpeechAutoConfiguration.class,
                    OpenAiImageAutoConfiguration.class, OpenAiModerationAutoConfiguration.class))
            // System properties override any real key that a local .env file may define.
            .withSystemProperties("GROQ_API_KEY=test-groq", "COHERE_API_KEY=test-cohere",
                    "OPENAI_API_KEY=test-openai");

    @Test
    void withoutAiProfileNoModelIsCreatedAndNoKeyIsRequired() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
            assertThat(context).doesNotHaveBean(EmbeddingModel.class);
            assertThat(context).doesNotHaveBean(OpenAiAudioTranscriptionModel.class);
            assertThat(context).doesNotHaveBean(OpenAiAudioSpeechModel.class);
            assertThat(context).doesNotHaveBean(ImageModel.class);
            assertThat(context).doesNotHaveBean(ModerationModel.class);
        });
    }

    @Test
    void groqProfileUsesGroqForChatAndTranscriptionAndCohereForEmbeddingsWithoutSpeech() {
        runner.withSystemProperties("spring.profiles.active=ai-groq").run(context -> {
            assertThat(context).hasSingleBean(OpenAiChatModel.class);
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context).hasSingleBean(OpenAiAudioTranscriptionModel.class);
            assertThat(context).doesNotHaveBean(OpenAiAudioSpeechModel.class);
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.ai.openai.base-url")).isEqualTo("https://api.groq.com/openai");
            assertThat(env.getProperty("spring.ai.openai.api-key")).isEqualTo("test-groq");
            assertThat(env.getProperty("spring.ai.openai.embedding.base-url"))
                    .isEqualTo("https://api.cohere.ai/compatibility/v1");
            assertThat(env.getProperty("spring.ai.openai.embedding.embeddings-path")).isEqualTo("/embeddings");
            assertThat(env.getProperty("spring.ai.openai.embedding.api-key")).isEqualTo("test-cohere");
        });
    }

    @Test
    void openAiProfileEnablesChatEmbeddingsTranscriptionAndSpeech() {
        runner.withSystemProperties("spring.profiles.active=ai-openai").run(context -> {
            assertThat(context).hasSingleBean(OpenAiChatModel.class);
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context).hasSingleBean(OpenAiAudioTranscriptionModel.class);
            assertThat(context).hasSingleBean(OpenAiAudioSpeechModel.class);
            assertThat(context).doesNotHaveBean(ImageModel.class);
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.ai.openai.api-key")).isEqualTo("test-openai");
            assertThat(env.getProperty("spring.ai.openai.embedding.options.model"))
                    .isEqualTo("text-embedding-3-small");
        });
    }
}
