// Delivery state of an outbound message (outbound_message.status)
package com.docshelf.messaging.entity;

public enum MessageStatus { QUEUED, SENDING, SENT, FAILED, SIMULATED, UNKNOWN }
