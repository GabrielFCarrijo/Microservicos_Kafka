package br.com.microservices.orchestrated.orderservice.core.repository;

import br.com.microservices.orchestrated.orderservice.core.document.Event;
import br.com.microservices.orchestrated.orderservice.core.dto.EventFilters;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.List;

public interface EventRepository extends MongoRepository<Event, String> {

    @Query(value = "{ 'orderId': ?0 }", sort = "{ 'createdAt': -1 }")
    Optional<Event> findTop1ByOrderIdOrderByCreatedAtDesc(String orderID);

    @Query(value = "{ 'transactionId': ?0 }", sort = "{ 'createdAt': -1 }")
    Optional<Event> findTop1ByTransactionIdOrderByCreatedAtDesc(String transactionID);

    @Query(value = "{}", sort = "{ 'createdAt': -1 }")
    List<Event> findAllByOrderByCreatedAtDesc();
}
