package com.centneo.fintech.supportDeskSvc.config.dbConfig;

import com.centneo.fintech.supportDeskSvc.constants.DataSourceConfigConstants;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(entityManagerFactoryRef = DataSourceConfigConstants.PRIMARY_READ_ENTITY_MANAGER_FACTORY,
        transactionManagerRef = DataSourceConfigConstants.PRIMARY_READ_TRANSACTION_MANAGER,
        basePackages = {DataSourceConfigConstants.PRIMARY_READ_BASE_PACKAGE})
public class PrimaryReadConfig {

    @Bean(name = "primaryReadDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.read")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "primaryReadDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.read.hikari")
    public DataSource dataSource(@Qualifier("primaryReadDataSourceProperties")
                                 DataSourceProperties primaryReadDataSourceProperties) {
        return primaryReadDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
    }

    @Bean(name = DataSourceConfigConstants.PRIMARY_READ_ENTITY_MANAGER_FACTORY)
    public LocalContainerEntityManagerFactoryBean localContainerEntityManagerFactoryBean(
            EntityManagerFactoryBuilder builder,
            @Qualifier("primaryReadDataSource") DataSource dataSource) {
        return builder.dataSource(dataSource).packages(DataSourceConfigConstants.PRIMARY_MODEL_PACKAGE).
                persistenceUnit("primary_read_pu").properties(DataSourceConfigConstants.ADDITIONAL_PROPERTIES).build();
    }

    @Bean(name = "primaryReadTransactionManager")
    public PlatformTransactionManager platformTransactionManager(
            @Qualifier("primaryReadEntityManagerFactory") EntityManagerFactory primaryReadEntityManagerFactory) {
        return new JpaTransactionManager(primaryReadEntityManagerFactory);
    }

    @Bean(name = "jdbcTemplatePrimaryRead")
    public JdbcTemplate jdbcTemplatePrimaryRead(
            @Qualifier("primaryReadDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}