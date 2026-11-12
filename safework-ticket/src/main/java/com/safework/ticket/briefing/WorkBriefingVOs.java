package com.safework.ticket.briefing;

import java.time.LocalDateTime;
import java.util.List;

public final class WorkBriefingVOs {
    private WorkBriefingVOs() {}

    public record Row(WorkBriefing briefing, int attendeeCount, int signedCount) {}
    public record Attendee(Long id, Long participantId, String participantName, String participantRole,
                           LocalDateTime signedTime, String signImage, boolean mine) {}
    public record Detail(WorkBriefing briefing, List<Attendee> attendees, boolean canManage) {}
}