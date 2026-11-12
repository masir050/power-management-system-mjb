package com.power.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 备用模型工厂（主模型挂了切硅基流动，不能也指 DeepSeek，否则等于没备）。
 *
 * <p>关键一点：备胎不能标 @Bean。OpenAiChatAutoConfiguration 的 openAiChatModel 带
 * @ConditionalOnMissingBean，用户配置先注册，容器里一旦有 OpenAiChatModel 类型的 bean，
 * 主模型就不创建了，会被静默换成备胎。同理也不能注册第二个 ChatClient。</p>
 */
@Configuration
public class BackupConfig {

    /** 优先 backup.api-key；没配就复用 embedding 那把（同一硅基流动账号） */
    @Value("${backup.api-key:${spring.ai.openai.embedding.api-key:}}")
    private String backupApiKey;

    @Value("${backup.base-url:https://api.siliconflow.cn}")
    private String backupBaseUrl;

    @Value("${backup.model:Qwen/Qwen2.5-7B-Instruct}")
    private String backupModel;

    /** 故意不加 @Bean */
    public ChatClient buildBackupClient() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(backupBaseUrl)
                .apiKey(backupApiKey)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory()))
                .build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(backupModel)
                        .temperature(0.0)
                        .build())
                .build();

        return ChatClient.builder(model).build();
    }

    /**
     * 主模型超时：Spring AI 1.0.0 没有 yml 超时项（配置不生效），只能改自动装配的 RestClient。
     * 30s 是单次读取的超时；流式接口走 WebClient，不受影响。
     */
    @Bean
    public RestClientCustomizer timeoutCustomizer() {
        return builder -> builder.requestFactory(requestFactory());
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }
}
