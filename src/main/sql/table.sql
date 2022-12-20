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

