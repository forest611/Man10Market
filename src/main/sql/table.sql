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

create table stock_table
(
    id                 int auto_increment,
    player             varchar(16) null,
    uuid               varchar(36) null,
    stock_name         varchar(64) null,
    total_issued_stock int         null,
    last_issue_date    datetime    null,
    constraint Stock_table_pk
        primary key (id)
);

create index Stock_table_uuid_stock_name_index
    on Stock_table (uuid, stock_name);


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

create index hour_table_item_id_year_month_day_hour_index
    on hour_table (item_id, year, month, day, hour, date);



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