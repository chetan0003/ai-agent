package com.ossom.monitoring.channel;

import com.ossom.monitoring.model.AlertPayload;
import com.ossom.monitoring.model.Severity;

/**
 * Strategy interface for alert delivery channels.
 * Each Spring bean implementing this interface is auto-discovered by AlertDispatcherService.
 */
public interface AlertChannel {
    /** Returns true if this channel should handle the given severity level */
    boolean supports(Severity severity);

    /** Deliver the alert payload */
    void send(AlertPayload payload);
}
