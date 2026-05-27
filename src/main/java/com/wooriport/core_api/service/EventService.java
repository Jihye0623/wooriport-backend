package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.event.EventDetailResponseDto;
import com.wooriport.core_api.base.dto.event.EventListResponseDto;
import com.wooriport.core_api.domain.Event;
import com.wooriport.core_api.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    // GET /events
    @Transactional(readOnly = true)
    public EventListResponseDto getEvents(UUID userId) {
        List<Event> events = eventRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<EventListResponseDto.EventItem> items = events.stream()
                .map(e -> EventListResponseDto.EventItem.builder()
                        .id(e.getId())
                        .title(e.getTitle())
                        .targetAmount(e.getTargetAmount())
                        .deadline(e.getDeadline().toString())
                        .status(e.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        return EventListResponseDto.builder()
                .events(items)
                .totalCount(items.size())
                .build();
    }

    // GET /events/{id}
    @Transactional(readOnly = true)
    public EventDetailResponseDto getEvent(UUID userId, UUID eventId) {
        Event event = eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다."));

        return EventDetailResponseDto.builder()
                .id(event.getId())
                .title(event.getTitle())
                .targetAmount(event.getTargetAmount())
                .deadline(event.getDeadline().toString())
                .status(event.getStatus().name())
                .summaryMessage(event.getSummaryMessage())
                .eventDescription(event.getEventDescription())
                .build();
    }
}