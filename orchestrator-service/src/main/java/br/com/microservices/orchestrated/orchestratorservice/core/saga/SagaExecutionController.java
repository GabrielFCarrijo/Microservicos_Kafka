package br.com.microservices.orchestrated.orchestratorservice.core.saga;

import br.com.microservices.orchestrated.orchestratorservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.orchestratorservice.core.dto.Event;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ETopics;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

@Slf4j
@AllArgsConstructor
@Component
public class SagaExecutionController {

    private static final String SAGA_LOG_ID = "ORDER ID: %s | TRANSACTION ID %s | EVENT ID %s";

    public ETopics getNextTopics ( Event event ) {
        if (ObjectUtils.isEmpty( event.getStatus() ) || ObjectUtils.isEmpty( event.getStatus() )) {
            throw new ValidationException( "Source and status must be specified" );
        }

        var topic = findTopicBySourceAndStatus( event );
        logCurrentSaga( event,topic );

        return ETopics.FINISH_SUCCESS;
    }

    private ETopics findTopicBySourceAndStatus ( Event event ) {
        return ( ETopics ) ( Arrays.stream( SagaHandler.SAGA_HANDLER )
                .filter( row -> isEventSourceAndStatus( event,row ) )
                .map( i -> i[SagaHandler.TOPIC_INDEX] )
                .findFirst()
                .orElseThrow( () -> new ValidationException( "Topic not found!" ) ) );
    }

    private boolean isEventSourceAndStatus ( Event event,Object[] row ) {
        var source = row[SagaHandler.EVENT_SOURCE_INDEX];
        var status = row[SagaHandler.SAGA_STATUS_INDEX];

        return event.getSource().equals( source ) && event.getStatus().equals( status );
    }

    private void logCurrentSaga ( Event event,ETopics topic ) {
        var sagaId = createSagaId( event );
        var source = event.getSource();
        switch (event.getStatus()) {
            case SUCCESS -> log.info( "### CURRENT SAGA: {} | SUCCESS | NEXT TOPIC {} | {}",
                    source,topic,sagaId );
            case ROLLBACK_PENDING ->
                    log.info( "### CURRENT SAGA: {} | SENDING TO ROLLBACK CURRENT SERVICE | NEXT TOPIC {} | {}",
                            source,topic,sagaId );
            case FAIL -> log.info( "### CURRENT SAGA: {} | SENDING TO ROLLBACK PREVIOUS SERVICE | NEXT TOPIC {} | {}",
                    source,topic,sagaId );
        }
    }

    private String createSagaId ( Event event ) {
        return String.format( SAGA_LOG_ID,
                event.getPayload().getId(),
                event.getTransactionId(),
                event.getId()
        );
    }
}
