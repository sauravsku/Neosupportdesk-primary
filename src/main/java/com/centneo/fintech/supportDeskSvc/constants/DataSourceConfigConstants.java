package com.centneo.fintech.supportDeskSvc.constants;

import java.util.HashMap;
import java.util.Map;

public class DataSourceConfigConstants {

    public static final String PRIMARY_WRITE_ENTITY_MANAGER_FACTORY = "primaryWriteEntityManagerFactory";

    public static final String PRIMARY_READ_ENTITY_MANAGER_FACTORY = "primaryReadEntityManagerFactory";

    public static final String PRIMARY_READ_TRANSACTION_MANAGER = "primaryReadTransactionManager";

    public static final String PRIMARY_WRITE_TRANSACTION_MANAGER = "primaryWriteTransactionManager";

    public static final String PRIMARY_WRITE_DS_PROPERTIES = "primaryWriteDsProperties";

    public static final String PRIMARY_READ_DS_PROPERTIES = "primaryReadDsProperties";

    public static final Map<String, String> ADDITIONAL_PROPERTIES = additionalJpaProperties();

    public static final String PRIMARY_WRITE_DS = "primaryWriteDataSource";

    public static final String PRIMARY_MODEL_PACKAGE = "com.centneo.fintech.supportDeskSvc.model.primary";

    public static final String PRIMARY_WRITE_BASE_PACKAGE = "com.centneo.fintech.supportDeskSvc.repository.primary.write";

    public static final String PRIMARY_READ_BASE_PACKAGE = "com.centneo.fintech.supportDeskSvc.repository.read";


    private static Map<String, String> additionalJpaProperties() {
        Map<String, String> map = new HashMap<>();
        map.put("hibernate.dialect", "org.hibernate.dialect.OracleDialect");
        return map;
    }
}


