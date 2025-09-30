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
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(entityManagerFactoryRef = DataSourceConfigConstants.PRIMARY_WRITE_ENTITY_MANAGER_FACTORY,
        transactionManagerRef = DataSourceConfigConstants.PRIMARY_WRITE_TRANSACTION_MANAGER,
        basePackages = {DataSourceConfigConstants.PRIMARY_WRITE_BASE_PACKAGE})
public class PrimaryWriteConfig {


    /**
     * MasterDataSource Properties from yml.
     *
     * @return properties.
     */
    @Primary
    @Bean(name = DataSourceConfigConstants.PRIMARY_WRITE_DS_PROPERTIES)
    @ConfigurationProperties(prefix = "spring.datasource.write")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * DataSource for master node.
     *
     * @return datasource.
     */
    @Primary
    @Bean(name = DataSourceConfigConstants.PRIMARY_WRITE_DS)
    @ConfigurationProperties(prefix = "spring.datasource.write.hikari")
    public DataSource dataSource(@Qualifier(DataSourceConfigConstants.PRIMARY_WRITE_DS_PROPERTIES)
                                 DataSourceProperties masterDataSourceProperties) {
        return masterDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /**
     * Primary Write Entity Manager Factory.
     *
     * @param builder    builder for entity manger.
     * @param dataSource data source.
     * @return factory.
     */
    @Primary
    @Bean(name = DataSourceConfigConstants.PRIMARY_WRITE_ENTITY_MANAGER_FACTORY)
    public LocalContainerEntityManagerFactoryBean primaryWriteEntityManagerFactory(
            EntityManagerFactoryBuilder builder, @Qualifier(DataSourceConfigConstants.PRIMARY_WRITE_DS) DataSource dataSource) {
        return builder.dataSource(dataSource).packages(DataSourceConfigConstants.PRIMARY_MODEL_PACKAGE)
                .persistenceUnit("primary_write_pu").properties(DataSourceConfigConstants.ADDITIONAL_PROPERTIES).build();
    }


    @Primary
    @Bean(name = "primaryWriteTransactionManager")
    public PlatformTransactionManager transactionManager(
            @Qualifier(DataSourceConfigConstants.PRIMARY_WRITE_ENTITY_MANAGER_FACTORY)
            EntityManagerFactory primaryWriteEntityManagerFactory) {
        return new JpaTransactionManager(primaryWriteEntityManagerFactory);
    }


    @Primary
    @Bean(name = "jdbcTemplatePrimaryWrite")
    public JdbcTemplate jdbcTemplatePrimaryWrite(
            @Qualifier(DataSourceConfigConstants.PRIMARY_WRITE_DS) DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Primary
    @Bean(name = "paramJdbcTemplatePrimaryWrite")
    public NamedParameterJdbcTemplate parameterJdbcTemplatePrimaryWrite(
            @Qualifier(DataSourceConfigConstants.PRIMARY_WRITE_DS) DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}