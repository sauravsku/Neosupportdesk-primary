INSERT INTO PRIMARY_CARD (
    PID,
    CREATED_AT,
    CREATED_BY,
    UPDATED_AT,
    UPDATED_BY,
    DESCRIPTION,
    JOURNEY_ID,
    METADATA,
    NAME,
    SUB_COUNT
) VALUES (
    1,                                  -- PID
    SYSTIMESTAMP,                        -- CREATED_AT
    'Admin',                              -- CREATED_BY
    SYSTIMESTAMP,                        -- UPDATED_AT
    'Admin',                              -- UPDATED_BY
    'This is a sample primary record',    -- DESCRIPTION
    101,                                  -- JOURNEY_ID
    '{"key":"value"}',                     -- METADATA
    'Primary Record 1',                    -- NAME
    5                                     -- SUB_COUNT
);


INSERT INTO CENTNEOSUPPORT.SECONDARY_CARD (
    CREATED_AT,
    PID,
    SUB_COUNT,
    UPDATED_AT,
    CREATED_BY,
    DESCRIPTION,
    METADATA,
    NAME,
    UPDATED_BY
) VALUES (
    SYSTIMESTAMP,       -- CREATED_AT
    1,                  -- PID (must exist in PRIMARY_CARD)
    3,                  -- SUB_COUNT
    SYSTIMESTAMP,       -- UPDATED_AT
    'Admin',            -- CREATED_BY
    'This is a sample secondary record',  -- DESCRIPTION
    '{"key":"value"}',  -- METADATA
    'Secondary Record 1', -- NAME
    'Admin'             -- UPDATED_BY
);

INSERT INTO CENTNEOSUPPORT.ISSUE_DETAIL (
    ISSUE_ID,       -- if auto-generated, you can skip this
    SID,
    ISSUE_NAME,
    ISSUE_DESC,
    ISSUE_EXT1,
    ISSUE_EXT2,
    ISSUE_EXT3,
    ISSUE_EXT4,
    ISSUE_EXT5,
    CAT_ID,
    CREATED_AT,
    CREATED_BY,
    UPDATED_AT,
    UPDATED_BY
) VALUES (
    ISSUE_DETAIL_SEQ.NEXTVAL,  -- if ISSUE_ID is generated via a sequence
    2,                         -- SID, foreign key to SECONDARY_CARD
    'Sample Issue Name',       -- ISSUE_NAME
    'Description of the issue',-- ISSUE_DESC
    'Ext1 value',              -- ISSUE_EXT1
    'Ext2 value',              -- ISSUE_EXT2
    'Ext3 value',              -- ISSUE_EXT3
    'Ext4 value',              -- ISSUE_EXT4
    'Ext5 value',              -- ISSUE_EXT5
    101,                       -- CAT_ID
    SYSTIMESTAMP,              -- CREATED_AT
    'Admin',                   -- CREATED_BY
    SYSTIMESTAMP,              -- UPDATED_AT
    'Admin'                    -- UPDATED_BY
);


INSERT INTO CENTNEOSUPPORT.ISSUE_SUB_DETAIL (
    ISSUE_SUB_TYPE_ID,   -- Primary key / auto-generated if applicable
    ISSUE_ID,            -- Foreign key to ISSUE_DETAIL
    ISSUE_NAME,
    ISSUE_DESC,
    ISSUE_EXT1,
    ISSUE_EXT2,
    ISSUE_EXT3,
    ISSUE_EXT4,
    ISSUE_EXT5,
    CREATED_AT,
    CREATED_BY,
    UPDATED_AT,
    UPDATED_BY
) VALUES (
    ISSUE_SUB_DETAIL_SEQ.NEXTVAL,  -- Use sequence if ISSUE_SUB_TYPE_ID is auto-generated
    1,                             -- ISSUE_ID (must exist in ISSUE_DETAIL)
    'Sample Sub Issue Name',       -- ISSUE_NAME
    'Description of the sub issue',-- ISSUE_DESC
    'Ext1 value',                  -- ISSUE_EXT1
    'Ext2 value',                  -- ISSUE_EXT2
    'Ext3 value',                  -- ISSUE_EXT3
    'Ext4 value',                  -- ISSUE_EXT4
    'Ext5 value',                  -- ISSUE_EXT5
    SYSTIMESTAMP,                  -- CREATED_AT
    'Admin',                       -- CREATED_BY
    SYSTIMESTAMP,                  -- UPDATED_AT
    'Admin'                        -- UPDATED_BY
);

