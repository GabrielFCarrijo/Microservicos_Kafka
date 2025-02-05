package br.com.microservices.orchestrated.paymentservice.core.service;

import br.com.microservices.orchestrated.paymentservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.paymentservice.core.dto.Event;
import br.com.microservices.orchestrated.paymentservice.core.dto.History;
import br.com.microservices.orchestrated.paymentservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.paymentservice.core.enums.EPaymentStatus;
import br.com.microservices.orchestrated.paymentservice.core.model.Payment;
import br.com.microservices.orchestrated.paymentservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.paymentservice.core.repository.PaymentRepository;
import br.com.microservices.orchestrated.paymentservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static br.com.microservices.orchestrated.paymentservice.core.enums.ESagaStatus.*;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentValidationService {

    private static final String CURRENT_SOURCE = "PAYMENT_SERVICE";
    public static final Double REDUCE_SUM_VALUE = 0.0;
    public static final double MIN_AMOUNT_VALUE = 0.1;

    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final PaymentRepository paymentRepository;

    public void realizedPayment(Event event) {
        try {
            checkCurrentValidation(event);
            createPendingPayment(event);
            var payment = findByOrderIdAndTransactionId(event);
            validateAmount(payment.getTotalAmount());
            changePaymentToSuccess(payment);
            handleValidationSuccess(event);
        } catch (Exception e) {
            log.error("Error trying to validate payment: ", e);
            handleFailureCurrentNotExecuted(event, e.getMessage());
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void createPendingPayment(Event event) {

        var totalAmount = calculateTotalAmount(event);
        var totalItems = calculateTotalItems(event);

        var payment = Payment.builder()
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .totalAmount(totalAmount)
                .totalItems(totalItems)
                .build();

        save(payment);
        setEventAmountItems(event, payment);
    }

    private void save(Payment payment) {
        paymentRepository.save(payment);
    }

    private void setEventAmountItems(Event event,Payment payment) {
        event.getPayload().setTotalAmount(payment.getTotalAmount());
        event.getPayload().setTotalItems(payment.getTotalItems());
    }

    private int calculateTotalItems(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(OrderProducts::getQuantity)
                .reduce(REDUCE_SUM_VALUE.intValue(), Integer::sum);
    }

    private double calculateTotalAmount(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(product -> product.getQuantity() * product.getProduct().getUnitValue())
                .reduce(REDUCE_SUM_VALUE, Double::sum);
    }

    private void checkCurrentValidation(Event event) {
        if (paymentRepository.existsByOrderIdAndTransactionId(
                event.getOrderId(), event.getTransactionId())) {
            throw new ValidationException("Validation already executed for order " + event.getOrderId());
        }
    }

    private Payment findByOrderIdAndTransactionId(Event event) {
        return paymentRepository
                .findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .orElseThrow(() -> new ValidationException("Payment not found OrderID and TransactionalID"));
    }

    private void validateAmount(double amount) {
        if (amount < MIN_AMOUNT_VALUE) {
            throw new ValidationException("Total amount must be greater than zero ");
        }
    }

    private void changePaymentToSuccess(Payment payment) {
        payment.setStatus(EPaymentStatus.SUCCESS);
        paymentRepository.save(payment);
    }

    private void handleValidationSuccess(Event event) {
        event.setStatus(SUCCESS);
        event.setSource(CURRENT_SOURCE);
        assHistory(event, "Payment realized successfully");
    }

    public void rollBackPayment(Event event) {
        event.setStatus(FAIL);
        event.setSource(CURRENT_SOURCE);
        try {
            changePaymentStatusToRefund(event);
            assHistory(event, "Rollback executed for payment!");
        } catch (Exception e) {
            assHistory(event, "Rollback not executed for payment!".concat(e.getMessage()));
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void changePaymentStatusToRefund(Event event) {
        var payment = findByOrderIdAndTransactionId(event);
        payment.setStatus(EPaymentStatus.REFUNDED);
        setEventAmountItems(event, payment);
        save(payment);
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
    private void handleFailureCurrentNotExecuted(Event event, String message) {
        event.setStatus(ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        assHistory(event, "Fail to realized payment: ".concat(message));
    }
}
