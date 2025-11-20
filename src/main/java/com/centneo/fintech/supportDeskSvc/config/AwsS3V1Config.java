//package com.centneo.fintech.supportDeskSvc.config;
//
//import com.amazonaws.auth.AWSStaticCredentialsProvider;
//import com.amazonaws.auth.BasicAWSCredentials;
//import com.amazonaws.services.s3.AmazonS3;
//import com.amazonaws.services.s3.AmazonS3ClientBuilder;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class AwsS3V1Config {
//
//    @Value("${app.aws.access-key}")
//    private String accessKey;
//
//    @Value("${app.aws.secret-key}")
//    private String secretKey;
//
//    @Value("${app.aws.region}")
//    private String region;
//
//    @Bean
//    public AmazonS3 amazonS3() {
//        BasicAWSCredentials basicAWSCredentials = new BasicAWSCredentials(accessKey, secretKey);
//        return AmazonS3ClientBuilder.standard()
//                .withRegion(region)
//                .withCredentials(new AWSStaticCredentialsProvider(basicAWSCredentials))
//                .build();
//    }
//}
