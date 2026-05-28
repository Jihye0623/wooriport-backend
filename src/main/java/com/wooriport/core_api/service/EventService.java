package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.event.EventCreateRequestDto;
import com.wooriport.core_api.base.dto.event.EventDetailResponseDto;
import com.wooriport.core_api.base.dto.event.EventListResponseDto;
import com.wooriport.core_api.base.exception.UserNotFoundException;
import com.wooriport.core_api.domain.Event;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.EventRepository;
import com.wooriport.core_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    // POST /events
    @Transactional
    public UUID createEvent(UUID userId, EventCreateRequestDto request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        Event event = Event.builder()
                .user(user)
                .title(request.getTitle())
                .targetAmount(Long.parseLong(request.getTargetAmount()))
                .deadline(LocalDate.parse(request.getDeadline()))
                .eventDescription(request.getUserInput())
                .build();

        return eventRepository.save(event).getId();
    }

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
                .eventDescription(event.getEventDescription())
                .build();
    }
}