create table order_table
(
    id         int auto_increment,
    player     varchar(16)            null,
    uuid       varchar(36)            null,
    item_id    varchar(32)            not null,
    price      double                 null,
    buy        boolean  default 0     null,
    sell       boolean  default 0     null,
    lot        int      default 0     null,
    entry_date datetime default now() not null,
    constraint order_table_pk
        primary key (id)
);

create index order_table_index
    on order_table (uuid, item_id, buy, sell, entry_date);

create table tick_table
(
    id      int auto_increment,
    item_id varchar(32)            null,
    date    datetime default now() not null,
    bid     double                 null,
    ask     double                 null,
    volume  int                    null,
    constraint tick_table_pk
        primary key (id)
);

create index tick_table_item_id_index
    on tick_table (item_id);

create table execution_log
(
    id       int auto_increment,
    player   varchar(16)            null,
    uuid     varchar(36)            null,
    item_id  varchar(32)            null,
    amount   int                    null,
    price    double                 null,
    exe_type varchar(16)            null,
    datetime datetime default now() null,
    constraint execution_log_pk
        primary key (id)
);

-- create table stock_table
-- (
--     id                 int auto_increment,
--     player             varchar(16) null,
--     uuid               varchar(36) null,
--     stock_name         varchar(64) null,
--     total_issued_stock int         null,
--     last_issue_date    datetime    null,
--     constraint Stock_table_pk
--         primary key (id)
-- );
-- 
-- create index Stock_table_uuid_stock_name_index
--     on Stock_table (uuid, stock_name);


create table hour_table
(
    id      int auto_increment
        primary key,
    item_id varchar(32) null,
    open    double      null,
    high    double      null,
    low     double      null,
    close   double      null,
    year    int         null,
    month   int         null,
    day     int         null,
    hour    int         null,
    date    datetime    null,
    volume  int         null
);

create index hour_table_item_id_year_month_day_hour_index
    on hour_table (item_id, year, month, day, hour, date);

create table day_table
(
    id      int auto_increment
        primary key,
    item_id varchar(32) null,
    open    double      null,
    high    double      null,
    low     double      null,
    close   double      null,
    year    int         null,
    month   int         null,
    day     int         null,
    date    datetime    null,
    volume  int         null
);

create index day_table_item_id_year_month_day_index
    on day_table (item_id, year, month, day, date);



SELECT
    DATE_FORMAT(date, '%Y-%m-%d %H:00:00') as 日付,
    SUBSTRING_INDEX(GROUP_CONCAT(CAST(bid AS CHAR) ORDER BY date), ',', 1) AS 始値,
    MIN(bid) AS 安値,
    MAX(bid) AS 高値,
    SUBSTRING_INDEX(GROUP_CONCAT(CAST(bid AS CHAR) ORDER BY date DESC), ',', 1) AS 終値,
    SUM(volume) AS 出来高,
    DATE(date) AS day,
    HOUR(date) AS hour
FROM
    tick_table
where
    item_id='投票パール'
GROUP BY
    day,
    hour
ORDER BY
    day,
    hour;

-- ItemBank

create table item_index
(
    id int auto_increment,
    item_key varchar(128) not null comment 'アイテムの登録名称',
    item_name varchar(128) not null comment 'バニラのアイテム名',
    price double not null comment '現在値(Bid/Askの中間)',
    bid double not null,
    ask double not null,
    tick double not null,
    time datetime default now() not null comment '更新日時',
    disabled boolean default 0 not null comment 'このアイテムを禁止にするか',
    base64 longtext not null,
    constraint item_index_pk
        primary key (id)
);

create index item_index_item_key_disabled_index
    on item_index (item_key, disabled);

create table item_storage
(
    id int auto_increment,
    player varchar(16) not null,
    uuid varchar(36) not null,
    item_id int not null,
    item_key varchar(128) not null,
    amount int default 0 not null,
    time datetime default now() null,
    constraint item_storage_pk
        primary key (id)
);

create index item_storage_uuid_item_id_index
    on item_storage (uuid, item_id);

create table storage_log
(
    id int auto_increment,
    item_id int not null,
    item_key varchar(128) null,
    order_player varchar(16) null comment '倉庫を編集したプレイヤー(nullはコンソール)',
    order_uuid varchar(36) null,
    target_player varchar(16) not null,
    target_uuid varchar(36) null,
    action varchar(64) null,
    edit_amount int null comment '操作をした量',
    storage_amount int null comment '編集後の倉庫の量',
    world varchar(16) null,
    x double null,
    y double null,
    z double null,
    time datetime default now() null,
    constraint storage_log_pk
        primary key (id)
);

create table system_log
(
    id int auto_increment,
    player varchar(16) null,
    uuid varchar(36) null,
    action varchar(128) null,
    time datetime default now() null,
    constraint system_log_pk
        primary key (id)
);
