package com.centneo.fintech.supportDeskSvc.repository.read.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PrimaryReadDao {

    @Autowired
    @Qualifier("jdbcTemplatePrimaryRead")
    private transient JdbcTemplate jdbcTemplate;

    protected JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
}
