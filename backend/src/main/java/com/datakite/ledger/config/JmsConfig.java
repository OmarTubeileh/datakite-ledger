package com.datakite.ledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * Applied to both JmsTemplate (producer) and the default @JmsListener container
 * factory (consumer) by Spring Boot's Artemis/JMS autoconfiguration.
 */
@Configuration
public class JmsConfig {

    @Bean
    public MessageConverter jmsMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    /**
     * Explicit retry/DLQ policy for the embedded broker: if TransactionListener
     * throws (e.g. a transient DB error on save), the JMS session rolls back and
     * Artemis redelivers the message with exponential backoff, up to
     * max-delivery-attempts, before routing it to the dead-letter address
     * instead of retrying forever or dropping it.
     */
    @Bean
    public ArtemisConfigurationCustomizer artemisRedeliveryPolicyCustomizer(
            @Value("${datakite.ledger.jms.max-delivery-attempts}") int maxDeliveryAttempts,
            @Value("${datakite.ledger.jms.redelivery-delay-ms}") long redeliveryDelayMs,
            @Value("${datakite.ledger.jms.max-redelivery-delay-ms}") long maxRedeliveryDelayMs,
            @Value("${datakite.ledger.jms.dead-letter-address}") String deadLetterAddress) {
        return configuration -> configuration.addAddressSetting("#", new AddressSettings()
                .setMaxDeliveryAttempts(maxDeliveryAttempts)
                .setRedeliveryDelay(redeliveryDelayMs)
                .setRedeliveryMultiplier(2.0)
                .setMaxRedeliveryDelay(maxRedeliveryDelayMs)
                .setDeadLetterAddress(new SimpleString(deadLetterAddress)));
    }
}
