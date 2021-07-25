
package resortreservation.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Date;

@FeignClient(name="payment",contextId = "feignClientForPayment", url="${feign.payment.url}",  fallback = PaymentServiceFallback.class)
public interface PaymentService {

    @RequestMapping(method= RequestMethod.GET, value="/payments/{id}", consumes = "application/json")
    public Payment getPaymentStatus(@PathVariable("id") Long id);

}