package resortreservation;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

import resortreservation.external.Payment;

import java.util.List;
import java.util.Date;

@Entity
@Table(name="Reservation_table")
public class Reservation {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long resortId;
    private String resortName;
    private String resortStatus;
    private String resortType;
    private String resortPeriod;
    private Float resortPrice;
    private String memberName;

    private Long test=1L;

    @PostUpdate
    public void onPostUpdate(){
        ReservationCanceled reservationCanceled = new ReservationCanceled();
        BeanUtils.copyProperties(this, reservationCanceled);
        reservationCanceled.publishAfterCommit();

    }

    @PrePersist
    public void onPrePersist() throws Exception {
        resortreservation.external.Resort resort = new resortreservation.external.Resort();
        resortreservation.external.Payment payment = new resortreservation.external.Payment();
       
        System.out.print("#######resortId="+resort);
        //Resort 서비스에서 Resort의 상태를 가져옴
        resort = ReservationApplication.applicationContext.getBean(resortreservation.external.ResortService.class)
            .getResortStatus(resortId);

        // fallback 시 resort null return
        // if (resort == null){ 
        //     throw new Exception("The resort is not in a usable status.");
        // }            
        
       // System.out.print("#######paymentId="+payment);
        //Payment 서비스에서 Payment의 상태를 가져옴
       // payment = ReservationApplication.applicationContext.getBean(resortreservation.external.PaymentService.class).getPaymentStatus(test);
        
        // fallback 시 payment null return
        //   if (payment == null){ 
        //       throw new Exception("The payment is not in a usable status.");
        //   }   

        
        // 예약 가능상태 여부에 따라 처리
        if ("Available".equals(resort.getResortStatus())){
            this.setResortName(resort.getResortName());
            this.setResortPeriod(resort.getResortPeriod());
            this.setResortPrice(resort.getResortPrice());
            this.setResortType(resort.getResortType());
            this.setResortStatus("Confirmed");
        } else {
            throw new Exception("The resort is not in a usable status.");  
        }


    }

    @PostPersist
    public void onPostPersist() throws Exception {
    

        ReservationRegistered reservationRegistered = new ReservationRegistered();
        BeanUtils.copyProperties(this, reservationRegistered);
        reservationRegistered.publishAfterCommit();
     

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getResortId() {
        return resortId;
    }

    public void setResortId(Long resortId) {
        this.resortId = resortId;
    }
    public String getResortName() {
        return resortName;
    }

    public void setResortName(String resortName) {
        this.resortName = resortName;
    }
    public String getResortStatus() {
        return resortStatus;
    }

    public void setResortStatus(String resortStatus) {
        this.resortStatus = resortStatus;
    }
    public String getResortType() {
        return resortType;
    }

    public void setResortType(String resortType) {
        this.resortType = resortType;
    }
    public String getResortPeriod() {
        return resortPeriod;
    }

    public void setResortPeriod(String resortPeriod) {
        this.resortPeriod = resortPeriod;
    }
    public Float getResortPrice() {
        return resortPrice;
    }

    public void setResortPrice(Float resortPrice) {
        this.resortPrice = resortPrice;
    }
    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }




}
