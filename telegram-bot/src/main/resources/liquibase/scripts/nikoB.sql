-- liquibase formatted sql

-- changeSet nikoB:1
create table notification_task(
    id serial not null primary key,
    chat_id serial not null,
    notification text not null,
    date_time timestamp not null
)