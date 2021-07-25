package resortreservation.external;

import org.springframework.stereotype.Component;


@Component
public class PaymentServiceFallback implements PaymentService {

    @Override
    public Payment getPaymentStatus(Long id) {
        System.out.println("Circuit breaker has been opened. Fallback returned instead.");
        return null;
    }

}
