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
    @Autowired VoucherRepository voucherRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentApproved_VoucherRequestPolicy(@Payload PaymentApproved paymentApproved){

        if(!paymentApproved.validate()) return;

        System.out.println("\n\n##### listener VoucherRequestPolicy : " + paymentApproved.toJson() + "\n\n");

        // 결제 후 바우처 발송
        if (paymentApproved.getReservStatus().equals("Paid")){
            
            System.out.println("Paid accept");
            Voucher voucher = new Voucher();
            voucher.setId(paymentApproved.getReservId());
            voucher.setReservId(paymentApproved.getReservId());
            voucher.setVoucherCode(paymentApproved.getReservId()+"code");
            voucher.setVoucherStatus("Approved");
            voucherRepository.save(voucher);
        }
        
            
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentCancelled_VoucherCancelPolicy(@Payload PaymentCancelled paymentCancelled){

        if(!paymentCancelled.validate()) return;

        System.out.println("\n\n##### listener VoucherCancelPolicy : " + paymentCancelled.toJson() + "\n\n");

        // 결제취소 시 바우처 취소        
        
        voucherRepository.findByReservId(paymentCancelled.getReservId()) 
        .ifPresent(
            voucher -> {
                if(paymentCancelled.getReservStatus().equals("Canceled")){
                voucher.setVoucherStatus("Canceled");
                voucherRepository.save(voucher);
                }
            }    
        );
            
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}
