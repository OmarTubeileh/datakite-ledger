package com.datakite.ledger.messaging;

import com.datakite.ledger.dto.TransactionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes ingested transaction payloads to the JMS queue for async processing.
 */
@Component
public class TransactionProducer {

    private final JmsTemplate jmsTemplate;
    private final String destination;

    public TransactionProducer(
            JmsTemplate jmsTemplate,
            @Value("${datakite.ledger.jms.transactions-queue}") String destination) {
        this.jmsTemplate = jmsTemplate;
        this.destination = destination;
    }

    public void publish(TransactionRequest request) {
        jmsTemplate.convertAndSend(destination, request);
    }
}
