package com.centneo.fintech.supportDeskSvc.repository.write.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PrimaryWriteDao {

    @Autowired
    @Qualifier("jdbcTemplatePrimaryWrite")
    private transient JdbcTemplate jdbcTemplate;

    protected JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
}