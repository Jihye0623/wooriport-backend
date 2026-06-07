package com.wooriport.core_api.base.exception;

/**
 * 이미 처리한 event_id 의 거래가 다시 들어왔을 때(Kafka at-least-once 중복 전달) 던진다.
 * 멱등 적재를 위해 컨슈머가 잡아서 중복 카운트 후 스킵한다.
 */
public class DuplicateEventException extends RuntimeException {
    public DuplicateEventException(String eventId) {
        super("중복 이벤트(이미 적재됨): " + eventId);
    }
}
