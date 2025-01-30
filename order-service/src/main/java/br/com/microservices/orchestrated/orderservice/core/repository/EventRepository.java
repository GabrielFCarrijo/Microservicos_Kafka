package br.com.microservices.orchestrated.orderservice.core.repository;

import br.com.microservices.orchestrated.orderservice.core.document.Event;
import br.com.microservices.orchestrated.orderservice.core.dto.EventFilters;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends MongoRepository<Event, String> {

    Optional<Event>findTop1ByOrderIdOrderByCreatedAtDesc(String orderID);

    Optional<Event>findTop1ByTransactionIdOrderByCreatedAtDesc(String transactionID);

    List<Event> findAllByOrderByCreatedAuDesc();
}
