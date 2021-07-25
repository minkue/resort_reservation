package resortreservation;

import resortreservation.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @Autowired PaymentRepository paymentRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReservationRegistered_PaymentRequestPolicy(@Payload ReservationRegistered reservationRegistered){

        if(!reservationRegistered.validate()) return;

        System.out.println("\n\n##### listener PaymentRequestPolicy : " + reservationRegistered.toJson() + "\n\n");
        
            Payment payment = new Payment();
            payment.setReservId(reservationRegistered.getId());
            payment.setReservStatus("Waiting for payment"); 
            payment.setResortPrice(reservationRegistered.getResortPrice());
            paymentRepository.save(payment);
            
    } 
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReservationCanceled_PaymentCancelPolicy(@Payload ReservationCanceled reservationCanceled){

        if(!reservationCanceled.validate()) return;

        System.out.println("\n\n##### listener PaymentCancelPolicy : " + reservationCanceled.toJson() + "\n\n");

        // 결제완료 상태를 결제취소 상태로 변경
        paymentRepository.findById(reservationCanceled.getId())
        .ifPresent(
            payment -> {
                payment.setReservStatus("Canceled");
                paymentRepository.save(payment);
            }    
        );
            
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}
