package com.centneo.fintech.supportDeskSvc.config;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.models.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitLabConfig {

    @Bean
    public GitLabApi gitLabDlpApi(
            @Value("${gitlab.url:https://gitlab.com}") String gitlabUrl,
            @Value("${gitlab.dlp.token:}") String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing required property: gitlab.dlp.token");
        }
        return createGitLabApi(gitlabUrl, token);
    }

    @Bean
    public GitLabApi gitLabOmniApi(
            @Value("${gitlab.url:https://gitlab.com}") String gitlabUrl,
            @Value("${gitlab.omni.token:}") String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing required property: gitlab.omni.token");
        }
        return createGitLabApi(gitlabUrl, token);
    }

    @Bean
    public GitLabApi gitLabKycApi(
            @Value("${gitlab.url:https://gitlab.com}") String gitlabUrl,
            @Value("${gitlab.kyc.token:}") String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing required property: gitlab.kyc.token");
        }
        return createGitLabApi(gitlabUrl, token);
    }

    private GitLabApi createGitLabApi(String url, String token) {
        return new GitLabApi(url, Constants.TokenType.PRIVATE, token);
    }
}
