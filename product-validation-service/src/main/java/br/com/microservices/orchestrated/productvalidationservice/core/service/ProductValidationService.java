package br.com.microservices.orchestrated.productvalidationservice.core.service;

import br.com.microservices.orchestrated.productvalidationservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.Event;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.History;
import br.com.microservices.orchestrated.productvalidationservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.productvalidationservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.productvalidationservice.core.model.Validation;
import br.com.microservices.orchestrated.productvalidationservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.productvalidationservice.core.repository.ProductRepository;
import br.com.microservices.orchestrated.productvalidationservice.core.repository.ValidationRepository;
import br.com.microservices.orchestrated.productvalidationservice.core.utils.JsonUtil;
import com.sun.net.httpserver.Authenticator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;

import static br.com.microservices.orchestrated.productvalidationservice.core.enums.ESagaStatus.*;
import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
@Service
@AllArgsConstructor
public class ProductValidationService {

    private static final String CURRENT_SOURCE = "PRODUCT_VALIDATION_SERVICE";

    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final ProductRepository productRepository;
    private final ValidationRepository validationRepository;

    public void validateExistingProducts(Event event) {
        try {
            checkCurrentValidation(event);
            createValidation(event, true);
            handleValidationSuccess(event);
        } catch (Exception e) {
            log.error("Error trying to validate products: ", e);
            handleFailureCurrentNotExecuted(event, e.getMessage());
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void handleFailureCurrentNotExecuted(Event event, String message) {
        event.setStatus(ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        assHistory(event, "Fail to validate products: ".concat(message));
    }

    public void rollbackEvent(Event event) {
        changeValidationToFail(event);
        event.setStatus(FAIL);
        event.setSource(CURRENT_SOURCE);
        assHistory(event, "Rollback executed on product validation!");
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void changeValidationToFail(Event event) {
        validationRepository
                .findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .ifPresentOrElse(validation -> {
                    validation.setSuccess(false);
                    validationRepository.save(validation);
                }, () -> createValidation(event, false));
    }

    private void validationProductsInformed(Event event) {
        if (isEmpty(event.getPayload()) || isEmpty(event.getPayload().getProducts())) {
           throw new ValidationException("Payload or products are null.");
        }
        if (isEmpty(event.getPayload().getId()) || isEmpty(event.getPayload().getTransactionId())) {
            throw new ValidationException("OrderID and TransactionalID must be informed");
        }
    }

    private void checkCurrentValidation(Event event) {
        validationProductsInformed(event);
        if (validationRepository.existsByOrderIdAndTransactionId(
                event.getOrderId(), event.getTransactionId())) {
            throw new ValidationException("Validation already executed for order " + event.getOrderId());
        }

        event.getPayload().getProducts().forEach(product -> {
            validateProductInformed(product);
            validateExistingProduct(product.getProduct().getCode());
        });
    }

    private void validateProductInformed(OrderProducts product) {
        if (isEmpty(product.getProduct()) || isEmpty(product.getProduct().getCode())) {
            throw new ValidationException("Product must be informed!");
        }
    }

    private void validateExistingProduct(String code) {
        if (!productRepository.existsByCode(code)) {
            throw new ValidationException("Product does not exists in database!");
        }
    }

    private void createValidation(Event event, Boolean succees) {
        var validation = Validation
           .builder()
           .orderId(event.getPayload().getId())
           .transactionId(event.getTransactionId())
           .success(succees)
           .build();

        validationRepository.save(validation);
    }

    private void handleValidationSuccess(Event event) {
        event.setStatus(SUCCESS);
        event.setSource(CURRENT_SOURCE);
        assHistory(event, "Products are validated successfully");
    }

    private void assHistory(Event event, String message) {
        var history = History.builder()
                .source(event.getSource())
                .status(event.getStatus())
                .mensage(message)
                .createdAt(LocalDateTime.now())
                .build();

        event.addToHistory(history);
    }
}
