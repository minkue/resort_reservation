# 휴양소_예약시스템
<img width="994" alt="image" src="https://user-images.githubusercontent.com/85722729/125247482-f6e26e00-e32d-11eb-8ccd-cf83ee9cae62.jpg">

# Table of contents
- [휴양소_예약시스템](#---)
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
    - [AS-IS 조직 (Horizontally-Aligned)](#AS-IS-조직-Horizontally-Aligned)
    - [TO-BE 조직 (Vertically-Aligned)](#TO-BE-조직-Vertically-Aligned)
    - [Event Storming 결과](#Event-Storming-결과)
  - [구현](#구현)
    - [시나리오 흐름 테스트](#시나리오-흐름-테스트)
    - [DDD 의 적용](#ddd-의-적용)
    - [Gateway 적용](#Gateway-적용)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [CQRS & Kafka](#CQRS--Kafka)
    - [동기식 호출과 Fallback 처리](#동기식-호출과-Fallback-처리)
    - [비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트](#비동기식-호출--시간적-디커플링--장애격리--최종-Eventual-일관성-테스트)
  - [운영](#운영)
    - [CI/CD 설정](#CICD-설정)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출--서킷-브레이킹--장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [Zero-Downtime deploy (Readiness Probe)](#Zero-Downtime-deploy-Readiness-Probe)
    - [Self-healing (Liveness Probe)](#Self-healing-Liveness-Probe)
    - [ConfigMap 사용](#ConfigMap-사용)

    
# 서비스 시나리오
- 기능적 요구사항(전체)
1. 휴양소 관리자는 휴양소를 등록한다.
2. 고객이 휴양소를 선택하여 예약한다.
3. 고객이 예약한 휴양소를 결제한다.(팀과제에서 제외했던 부분 구현)
4. 예약이 확정되어 휴양소는 예약불가 상태로 바뀐다.
5. 바우처를 고객에게 발송한다. (팀과제에서 제외했던 부분 구현)
6. 고객이 확정된 예약을 취소할 수 있다.
7. 예약이 취소되면 바우처가 비활성화 된다.(팀과제에서 제외했던 부분 구현)
8. 예약이 취소되면 결제가 취소된다. (팀과제에서 제외했던 부분 구현)
9. 휴양소는 예약가능 상태로 바뀐다.
10. 고객은 휴양소 예약 정보를 확인 할 수 있다. (팀과제에서 제외했던 부분 구현추가 mypage CQRS)

- 비기능적 요구사항
1. 트랜잭션
    1. 리조트 상태가 예약 가능상태가 아니면 아예 예약이 성립되지 않아야 한다  Sync 호출 
1. 장애격리
    1. 결제/바우처/마이페이지 기능이 수행되지 않더라도 예약은 365일 24시간 받을 수 있어야 한다.  Async (event-driven), Eventual Consistency
    1. 예약시스템이 과중되면 사용자를 잠시동안 받지 않고 잠시후에 하도록 유도한다.  Circuit breaker, fallback
1. 성능
    1. 고객이 자신의 예약 상태를 확인할 수 있도록 마이페이지가 제공 되어야 한다.  CQRS

# 분석/설계

## AS-IS 조직 (Horizontally-Aligned)

  ![image](https://user-images.githubusercontent.com/487999/79684144-2a893200-826a-11ea-9a01-79927d3a0107.png)

## TO-BE 조직 (Vertically-Aligned)

  ![image](https://user-images.githubusercontent.com/85722729/125151796-b9da7800-e183-11eb-8aa1-75206e01d5d1.png)


## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과: http://www.msaez.io/#/storming/yyBCMACQzEZNjgdLspgi0doyuXR2/916a728a8761f24fcc35f19189f086ab


### 이벤트 도출
<img width="994" alt="image" src="https://user-images.githubusercontent.com/85722729/125240557-3c4e6d80-e325-11eb-9115-3aab0c542df2.png">

### 이벤트 도출-부적격삭제
<img width="994" alt="image" src="https://user-images.githubusercontent.com/85722729/125240558-3c4e6d80-e325-11eb-9fb7-7e4ee3403089.png">

### 액터, 커맨드 부착하여 읽기 좋게
<img width="994" alt="image" src="https://user-images.githubusercontent.com/85722729/125240560-3ce70400-e325-11eb-826d-8c16ca723bc4.png">

### 어그리게잇으로 묶기
<img width="994" alt="image" src="https://user-images.githubusercontent.com/85722729/125240563-3ce70400-e325-11eb-88b7-ae0f673b22c0.png">

### 바운디드 컨텍스트로 묶기
<img width="994" alt="image" src="https://user-images.githubusercontent.com/85722729/125240566-3d7f9a80-e325-11eb-9602-65894469d7e8.png">

### 폴리시 부착 (괄호는 수행주체, 폴리시 부착을 둘째단계에서 해놔도 상관 없음. 전체 연계가 초기에 드러남)

<img width="994" alt="image" src="https://user-images.githubusercontent.com/85722729/125240549-3a84aa00-e325-11eb-96e1-7019c68498ae.png">

### 폴리시의 이동과 컨텍스트 매핑 (점선은 Pub/Sub, 실선은 Req/Resp)

<img width="994" alt="image" src="https://user-images.githubusercontent.com/85722729/125240556-3bb5d700-e325-11eb-9bcf-d9c4d4d08e32.png">

### 추가 과제 구현시 수정된 커맨드 추가 (결제시스템 상태조회)
![image](https://user-images.githubusercontent.com/58622901/126890676-b2b1b931-b146-4896-9da5-bff07a677144.png)

### 완성된 모형 (마이페이지 결제,바우처 상태조회 추가)
![image](https://user-images.githubusercontent.com/58622901/126923684-bc84f88b-7447-4f48-a5ef-61b6e12fab02.png)

- View Model 추가
- 도메인 서열
  - Core : reservation
  - Supporting : resort, mypage
  - General : payment, voucher

## 헥사고날 아키텍처 다이어그램 도출

- 팀과제 수행 시
![image](https://user-images.githubusercontent.com/85722729/125151798-bcd56880-e183-11eb-876b-074a02d94116.png)

- 개인Final 수행 시 아키텍처 
![image](https://user-images.githubusercontent.com/58622901/126894359-45426bab-99a0-4fb2-9290-345078b1b948.png)


    - Chris Richardson, MSA Patterns 참고하여 Inbound adaptor와 Outbound adaptor를 구분함
    - 호출관계에서 PubSub 과 Req/Resp 를 구분함
    - 서브 도메인과 바운디드 컨텍스트의 분리:  각 팀의 KPI 별로 아래와 같이 관심 구현 스토리를 나눠가짐


# 구현

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 808n 이다)

## 시나리오 흐름 테스트
1. 휴양소 관리자는 휴양소를 등록한다.
```sh
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/resorts resortName="Jeju" resortType="Hotel" resortPrice=100000 resortStatus="Available" resortPeriod="7/23~25"
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/resorts
```
![image](https://user-images.githubusercontent.com/58622901/126924118-9fe2177a-029a-4e3d-8110-4faeeb5587cc.png)

2. 고객이 휴양소를 선택하여 예약한다.
```sh
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/reservations resortId=1 memberName="MK"
```
![image](https://user-images.githubusercontent.com/58622901/126924229-8698a632-4479-47c5-b713-87757d542785.png)

3. 예약이 확정되어 휴양소는 예약불가 상태로 바뀐다.
```sh
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/resorts/1
```
![image](https://user-images.githubusercontent.com/58622901/126925036-bc87d704-95ac-4d0a-b922-f17cbaaf65e3.png)

4. 고객이 마이페이지에 예약 및 결제대기상태를 조회한다. (개인 Final 과제 수행 시 추가)
```sh
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/myPages
```
![image](https://user-images.githubusercontent.com/58622901/126924387-29937a56-040c-47e1-9850-19f01fa7ebde.png)

5. 고객이 결제서비스를 통해 결제한다. (개인 Final 과제 수행 시 추가)
```sh
http PATCH a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/payments/1 reservStatus="Paid"
```
![image](https://user-images.githubusercontent.com/58622901/126924657-c383b26b-274b-495c-a342-1773fc85a41b.png)

6. 고객이 마이페이지에 결제상태를 조회한다. (개인 Final 과제 수행 시 추가)
```sh
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/myPages
```
![image](https://user-images.githubusercontent.com/58622901/126924833-f21e3d1b-e658-44b9-acf9-626958bd8f7f.png)

7. 바우처를 고객에게 발송한다. (개인 Final 과제 수행 시 추가)
```sh
http PATCH a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/vouchers/1 voucherStatus="Send" 
```
![image](https://user-images.githubusercontent.com/58622901/126929882-c3cb6555-f43f-4719-b21d-2c43dc7f675b.png)

8. 고객이 마이페이지에서 바우처를 조회한다. (개인 Final 과제 수행 시 추가)
```sh
http PATCH a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/vouchers/1 voucherStatus="Send" 
```
![image](https://user-images.githubusercontent.com/58622901/126929948-4a2e897a-677b-4071-82b5-987d5fc2b217.png)

9. 고객이 확정된 예약을 취소할 수 있다.
```sh
http PATCH a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/reservations/1 resortStatus="Cancelled"
```
![image](https://user-images.githubusercontent.com/58622901/126930046-173614b7-cf22-46c0-8d54-07055d35ce2e.png)

10. 예약이 취소되면 바우처가 비활성화 된다. (개인 Final 과제 수행 시 추가)
```sh
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/vouchers
```
![image](https://user-images.githubusercontent.com/58622901/126930150-6f4f0f51-1175-4df7-9303-f3cb91c25be2.png)

11. 예약이 취소되면 결제가 취소된다. (개인 Final 과제 수행 시 추가)
```sh
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/payments 
```
![image](https://user-images.githubusercontent.com/58622901/126930222-0239318a-ed79-4296-9e89-14ebf92c5f33.png)

12. 휴양소는 예약 가능상태로 변경된다.
```sh
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/resorts
```
![image](https://user-images.githubusercontent.com/58622901/126930300-f6dacc7a-f611-4e47-8211-b097df6fdcf6.png)

13. 고객이 마이페이지를 통해 예약정보를 확인한다. (개인 Final 과제 수행 시 추가)
```sh
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/myPages
```
![image](https://user-images.githubusercontent.com/58622901/126930372-c7ccdb94-2dd7-4354-a4e8-09d1ae5f2f91.png)


## DDD 의 적용
- 팀 과제 시 이벤트 스토밍을 통해 식별된 Micro Service 전체 5개 중 3개를 구현하였으며 그 중 mypage는 CQRS를 위한 서비스이다.
- 개인 과제 시 payment, voucher 서비스를 추가로 구현하였으며 CQRS를 위한 mypages에도 해당 서비스의 상태값을 추가하여 조회 할 수 있다. 

|MSA|기능|port|URL|
| :--: | :--: | :--: | :--: |
|reservation| 예약정보 관리 |8081|http://localhost:8081/reservations|
|resort| 리조트 관리 |8082|http://localhost:8082/resorts|
|mypage| 예약내역 조회 |8083|http://localhost:8083/mypages|
|payment| 예약내역 결제 |8084|http://localhost:8084/payments|
|voucher| 바우처 전송 |8085|http://localhost:8085/vouchers|
|gateway| gateway |8088|http://localhost:8088|

## Gateway 적용
- API GateWay를 통하여 마이크로 서비스들의 진입점을 통일할 수 있다. 
다음과 같이 GateWay를 적용하였다.
- 개인과제 구현 시 추가된 payment, voucher 서비스를 yaml 파일에 추가하여 구현 하였다. 

```yaml
- gateway 서비스의 application.yml

server:
  port: 8088

---

spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: reservation
          uri: http://localhost:8081
          predicates:
            - Path=/reservations/** 
        - id: resort
          uri: http://localhost:8082
          predicates:
            - Path=/resorts/** 
        - id: mypage
          uri: http://localhost:8083
          predicates:
            - Path= /myPages/**
        - id: payment
          uri: http://localhost:8084
          predicates:
            - Path=/payments/** 
        - id: voucher
          uri: http://localhost:8085
          predicates:
            - Path=/vouchers/** 
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true


---

spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: reservation
          uri: http://reservation:8080
          predicates:
            - Path=/reservations/** 
        - id: resort
          uri: http://resort:8080
          predicates:
            - Path=/resorts/** 
        - id: mypage
          uri: http://mypage:8080
          predicates:
            - Path= /myPages/**
        - id: payment
          uri: http://payment:8080
          predicates:
            - Path=/payments/** 
        - id: voucher
          uri: http://voucher:8080
          predicates:
            - Path=/vouchers/** 
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080
```
## 폴리글랏 퍼시스턴스
- 팀과제 구현 시 : CQRS 를 위한 mypage 서비스만 DB를 구분하여 적용함. 인메모리 DB인 hsqldb 사용.
- 개인과제 구현 시 : voucher 전송을 위한 voucher 서비스만 DB를 구분하여 적용하였다. 인메모리 DB인 hsqldb를 사용하였고 mypage는 h2 db로 변경하였다.
```
- voucher 서비스의 pom.xml
<!-- 
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
-->
    <dependency>
        <groupId>org.hsqldb</groupId>
        <artifactId>hsqldb</artifactId>
        <version>2.4.0</version>
        <scope>runtime</scope>
    </dependency>
```


## CQRS & Kafka
- 타 마이크로서비스의 데이터 원본에 접근없이 내 서비스의 화면 구성과 잦은 조회가 가능하게 mypage에 CQRS 구현하였다.
- 모든 정보는 비동기 방식으로 발행된 이벤트(예약, 예약취소)를 수신하여 처리된다.
- 개인과제 구현 시 결제, 바우처서비스의 이벤트(결제, 전송, 취소)를 수신하도록 추가하였다.

예약 실행
![image](https://user-images.githubusercontent.com/58622901/126924229-8698a632-4479-47c5-b713-87757d542785.png)

결제 실행(개인 Final 과제 수행)
![image](https://user-images.githubusercontent.com/58622901/126924657-c383b26b-274b-495c-a342-1773fc85a41b.png)

바우처 전송 실행 (개인 Final 과제 수행)
![image](https://user-images.githubusercontent.com/58622901/126929882-c3cb6555-f43f-4719-b21d-2c43dc7f675b.png)

예약 취소 (개인 Final 과제 수행)
![image](https://user-images.githubusercontent.com/58622901/126930046-173614b7-cf22-46c0-8d54-07055d35ce2e.png)

카프카 메시지
![image](https://user-images.githubusercontent.com/58622901/126932495-91409dda-4fbe-4906-9a5d-8ca83fb85fcd.png)
```bash
{"eventType":"ResortRegistrated","timestamp":"20210726021154","id":1,"resortName":"Jeju","resortStatus":"Available","resortType":"Hotel","resortPeriod":"7/23~25","resortPrice":100000.0}
{"eventType":"ReservationRegistered","timestamp":"20210726021651","id":1,"resortId":1,"resortName":"Jeju","resortStatus":"Confirmed","resortType":"Hotel","resortPeriod":"7/23~25","resortPrice":100000.0,"memberName":"MK"}
{"eventType":"ResortStatusChanged","timestamp":"20210726021651","id":1,"resortName":"Jeju","resortStatus":"Not Available","resortType":"Hotel","resortPeriod":"7/23~25","resortPrice":100000.0}
{"eventType":"PaymentRequested","timestamp":"20210726021652","id":1,"reservId":1,"resortPrice":100000.0,"reservStatus":"Waiting for payment"}
{"eventType":"PaymentApproved","timestamp":"20210726022239","id":1,"reservId":1,"resortPrice":100000.0,"reservStatus":"Paid"}
{"eventType":"PaymentCancelled","timestamp":"20210726022239","id":1,"reservId":1,"resortPrice":100000.0,"reservStatus":"Paid"}
{"eventType":"VoucherRequested","timestamp":"20210726022240","id":1,"reservId":1,"voucherCode":"1code","voucherStatus":"Approved"}
{"eventType":"VoucherSend","timestamp":"20210726033803","id":1,"reservId":1,"voucherCode":"1code","voucherStatus":"Send"}
{"eventType":"VoucherCancelled","timestamp":"20210726033803","id":1,"reservId":1,"voucherCode":"1code","voucherStatus":"Send"}
{"eventType":"ReservationCanceled","timestamp":"20210726034107","id":1,"resortId":1,"resortName":"Jeju","resortStatus":"Cancelled","resortType":"Hotel","resortPeriod":"7/23~25","resortPrice":100000.0,"memberName":"MK"}
{"eventType":"ResortStatusChanged","timestamp":"20210726034107","id":1,"resortName":"Jeju","resortStatus":"Available","resortType":"Hotel","resortPeriod":"7/23~25","resortPrice":100000.0}
{"eventType":"PaymentApproved","timestamp":"20210726034107","id":1,"reservId":1,"resortPrice":100000.0,"reservStatus":"Canceled"}
{"eventType":"PaymentCancelled","timestamp":"20210726034107","id":1,"reservId":1,"resortPrice":100000.0,"reservStatus":"Canceled"}
{"eventType":"VoucherSend","timestamp":"20210726034107","id":1,"reservId":1,"voucherCode":"1code","voucherStatus":"Canceled"}
{"eventType":"VoucherCancelled","timestamp":"20210726034107","id":1,"reservId":1,"voucherCode":"1code","voucherStatus":"Canceled"}
```
- mypage에 이벤트가 발생할때마다 모두 구독하지만 서비스별로 상태값을 체크하여 수행되도록 구현하였다. 

- 결제 후 mypage화면 
![image](https://user-images.githubusercontent.com/58622901/126924833-f21e3d1b-e658-44b9-acf9-626958bd8f7f.png)

- 바우처 전송 후 mypage화면 
![image](https://user-images.githubusercontent.com/58622901/126929948-4a2e897a-677b-4071-82b5-987d5fc2b217.png)

- 예약취소 후 mypage 화면 (개인 Final 과제 수행)
![image](https://user-images.githubusercontent.com/58622901/126930372-c7ccdb94-2dd7-4354-a4e8-09d1ae5f2f91.png)

## 동기식 호출과 Fallback 처리

- 분석단계에서의 조건 중 하나로 예약(reservation)->리조트상태확인(resort) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient를 이용하여 호출하였다

- 리조트서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 

```java
# (reservation) ResortService.java

package resortreservation.external;

@FeignClient(name="resort", url="${feign.resort.url}")
public interface ResortService {
    
    @RequestMapping(method= RequestMethod.GET, value="/resorts/{id}", consumes = "application/json")
    public Resort getResortStatus(@PathVariable("id") Long id);

}
```

- 예약을 처리 하기 직전(@PrePersist)에 ResortSevice를 호출하여 서비스 상태와 Resort 세부정보도 가져온다.
```java
# Reservation.java (Entity)

    @PrePersist
    public void onPrePersist() throws Exception {
        resortreservation.external.Resort resort = new resortreservation.external.Resort();
       
        //Resort 서비스에서 Resort의 상태를 가져옴
        resort = ReservationApplication.applicationContext.getBean(resortreservation.external.ResortService.class)
            .getResortStatus(resortId);

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
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 시스템이 장애로 예약을 못받는다는 것을 확인
<img width="1019" alt="image" src="https://user-images.githubusercontent.com/85722851/125232225-2174fc80-e317-11eb-9186-98995cf27f97.png">




- 개인 Final 과제 수행 시에는 예약(reservation)->결제서비스상태확인(payment) 호출을 추가하여 동기식 일관성을 유지하는 트랜잭션을 구현하여 처리하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient를 이용하여 호출하였다

- 결제서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현하였다
- 기존에 resort 서비스의 feignClient와 충돌이 나지 않기위해 각각 contextId를 부여하였다 (resortService의 contextId는 feignClientForResort이다.)

```java
# (reservation) PaymentService.java

package resortreservation.external;

@FeignClient(name="payment",contextId = "feignClientForPayment", url="${feign.payment.url}",  fallback = PaymentServiceFallback.class)
public interface PaymentService {

    @RequestMapping(method= RequestMethod.GET, value="/payments/{id}", consumes = "application/json")
    public Payment getPaymentStatus(@PathVariable("id") Long id);

}
```
- 예약을 처리 하기 직전(@PrePersist)에 PaymentSevice를 호출하여 서비스 상태를 가져온다.
```java
# Reservation.java (Entity)

    @PrePersist
    public void onPrePersist() throws Exception {
        resortreservation.external.Payment payment = new resortreservation.external.Payment();
        
        System.out.print("#######paymentId="+payment);
        //Payment 서비스에서 Payment의 상태를 가져옴
        payment = ReservationApplication.applicationContext.getBean(resortreservation.external.PaymentService.class).getPaymentStatus(test);
        
        // fallback 시 payment null return
           if (payment == null){ 
               throw new Exception("The payment is not in a usable status.");
           }   
    }
```
- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 시스템이 장애로 예약을 못받는다는 것을 확인
![image](https://user-images.githubusercontent.com/58622901/126894995-c594adcf-9889-427a-8204-d058ef9941eb.png)

- 또한 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다. (서킷브레이커, 폴백 처리는 운영단계에서 설명한다.)

## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

- 예약기록을 남긴 후에 곧바로 예약완료가 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
 
```java
@Entity
@Table(name="Reservation_table")
public class Reservation {
 ...
    @PostPersist
    public void onPostPersist() throws Exception {
        ...
        ReservationRegistered reservationRegistered = new ReservationRegistered();
        BeanUtils.copyProperties(this, reservationRegistered);
        reservationRegistered.publishAfterCommit();
    }
}
```
- 결제시스템과 바우처시스템, 마이페이지시스템에서는 예약완료,결제완료 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다

결제시스템 (개인 Final 과제 수행 시 구현)
- 예약완료 후 결제대기 상태로 payment 객체를 생성한다.
- 예약취소 후 결제상태를 Canceled로 변경 한다
- 예약ID별 결제상태를 변경해야 하므로 paymentRepository에 findByReservId 인터페이스를 추가하였다. 

```java

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
        paymentRepository.findByReservId(reservationCanceled.getId())
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
```

바우처시스템(개인 Final 과제 수행 시 구현)  
- 결제완료 후 해당 예약의 바우처를 Approved 상태로 변경
- 예약취소 시 결제취소 처리되며 해당 바우처 또한 Canceled 상태로 변경한다 
- 예약ID별 바우처상태를 변경해야 하므로 voucherRepository에 findByReservId 인터페이스를 추가하였다. 


```java
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

```


마이페이지시스템 (개인 Final 과제 수행 시 추가)
- 개인 Final 과제 수행 시 마이페이지에 결제상태와 바우처상태를 구독하여 조회 할 수 있도록 추가하였다. 
- 예약완료 시 결제대기 상태를 조회할 수 있도록 추가하였다.
- 결제완료 시 결제완료 상태를 조회할 수 있도록 추가하였다.
- 바우처 전송 시 바우처 상태를 조회할 수 있도록 추가하였다. 
- 예약취소 시 예약,결제,바우처 모두 취소됨을 조회할 수 있게 구현하였다.

```java
@Service
public class MyPageViewHandler {

    @Autowired
    private MyPageRepository myPageRepository;

     @StreamListener(KafkaProcessor.INPUT)
    public void whenReservationRegistered_then_CREATE_1 (@Payload ReservationRegistered reservationRegistered) {
        try {

            if (!reservationRegistered.validate()) return;

            // view 객체 생성
            MyPage myPage = new MyPage();
            // view 객체에 이벤트의 Value 를 set 함
            myPage.setId(reservationRegistered.getId());
            myPage.setMemberName(reservationRegistered.getMemberName());
            myPage.setResortId(reservationRegistered.getResortId());
            myPage.setResortName(reservationRegistered.getResortName());
            myPage.setResortStatus(reservationRegistered.getResortStatus());
            myPage.setResortType(reservationRegistered.getResortType());
            myPage.setResortPeriod(reservationRegistered.getResortPeriod());
            myPage.setResortPrice(reservationRegistered.getResortPrice());
            myPage.setReservStatus(reservationRegistered.getResortStatus());
            // view 레파지 토리에 save
            myPageRepository.save(myPage);
        
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenPaymentRequested_then_UPDATE(@Payload PaymentRequested paymentRequested){
        try{
            if(!paymentRequested.validate()) return;

            Optional<MyPage> myPageOptional = myPageRepository.findById(paymentRequested.getReservId());

            if( myPageOptional.isPresent()){
                 MyPage myPage = myPageOptional.get();
                 myPage.setReservStatus(paymentRequested.getReservStatus());
                 myPageRepository.save(myPage);
            }

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenReservationCanceled_then_UPDATE_1(@Payload ReservationCanceled reservationCanceled) {
        try {
            if (!reservationCanceled.validate()) return;
                // view 객체 조회
            Optional<MyPage> myPageOptional = myPageRepository.findById(reservationCanceled.getId());
            if( myPageOptional.isPresent()) {
                MyPage myPage = myPageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                    myPage.setResortStatus(reservationCanceled.getResortStatus());
                // view 레파지 토리에 save
                myPageRepository.save(myPage);
            }
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenPaymentCancelled_then_UPDATE(@Payload PaymentCancelled paymentCancelled){
        try{
            if (!paymentCancelled.validate()) return;
            
            Optional<MyPage> myPageOptional = myPageRepository.findById(paymentCancelled.getReservId());

            if( myPageOptional.isPresent()) {
                MyPage myPage = myPageOptional.get();
                myPage.setReservStatus(paymentCancelled.getReservStatus());
                myPageRepository.save(myPage);
            }

        }catch (Exception e){
            e.printStackTrace();
        }
    }

     @StreamListener(KafkaProcessor.INPUT)
     public void whenVoucherSend_then_UPDATE(@Payload VoucherSend voucherSend){
         try{
             if(!voucherSend.validate()) return;
             
             Optional<MyPage> myPageOptional = myPageRepository.findById(voucherSend.getReservId());

             if( myPageOptional.isPresent()) {
                MyPage myPage = myPageOptional.get();
                myPage.setVoucherStatus(voucherSend.getVoucherStatus());
                myPageRepository.save(myPage);
             }   

         }catch (Exception e){
            e.printStackTrace();    
         }
     }
}
```
- 예약 시스템은 바우처시스템,마이페이지 등 시스템과 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에 해당 시스템들이 유지보수로 인해 잠시 내려간 상태라도 예약을 받는데 문제가 없다

```bash
# 마이페이지,바우처 서비스는 잠시 셧다운 시키고 예약이력 및 바우처 전송내용 확인 가능 

1.리조트입력
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/resorts resortName="Jeju" resortType="Hotel" resortPrice=100000 resortStatus="Available" resortPeriod="7/23~25"
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/resorts resortName="Seoul" resortType="Hotel" resortPrice=100000 resortStatus="Available" resortPeriod="7/23~25"

2.예약입력
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/reservations resortId=1 memberName="MK" 
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/reservations #예약 정상 처리 확인

3.결제 
http PATCH a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/payments/1 reservStatus="Paid"

4.마이페이지서비스 기동

5.바우처서비스 기동

6.마이페이지, 바우처서비스 확인
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/myPages #정상적으로 마이페이지에서 예약 이력이 확인 됨
http a8531a7e562514fde999e2f6e73663da-2013166645.ca-central-1.elb.amazonaws.com:8080/vouchers #정상적으로 바우처이력이 확인 됨 

```


# 운영

## CI/CD 설정

각 구현체들은 각자의 source repository 에 구성되었고, 각 서비스별로 Docker로 빌드를 하여, AWS ECR에 등록 후 deployment.yaml, service.yml을 통해 EKS에 배포하였다.
(팀 과제 구현시에는 docker hub를 사용했으나 ECR로 변경하였다.)

- git에서 소스 가져오기
```bash
git clone https://github.com/minkue/resort_reservation.git
```
- 각서비스별 packege, build, github push 실행
- 신규 payment, voucher 또한 아래와 같이 해당 경로에서 명령어를 수행하였고 각각 deployment.yml , service.yaml 파일을 구현하였다.
```bash
cd resort #서비스별 폴더로 이동
mvn package -B -Dmaven.test.skip=true #패키지

docker build -t 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user24-resort:latest . #docker build
docker push 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user24-resort:latest       #ECR push

kubectl apply -f resort/kubernetes/deployment.yml #AWS deploy 수행
kubectl apply -f resort/kubernetes/service.yaml.  #AWS service 등록

```
- AWS ECR Image
![image](https://user-images.githubusercontent.com/58622901/126935208-82673dff-1602-4b86-b7c0-5abc637e1f83.png)

- 최종 Deploy완료
![image](https://user-images.githubusercontent.com/58622901/126935290-f50cc6d6-3263-48d4-b1a2-4fa99fbd8bcf.png)

## 동기식 호출 / 서킷 브레이킹 / 장애격리

* 서킷 브레이크 프레임워크 : Spring FeignClient + Hystrix 옵션을 사용

- 시나리오 : 예약(reservation) -> 휴양소(resort) 예약 시 RESTful Request/Response 로 구현이 하였고, 예약 요청이 과도할 경우 circuit breaker 를 통하여 장애격리.
Hystrix 설정: 요청처리 쓰레드에서 처리시간이 610 밀리초가 넘어서기 시작하여 어느정도 유지되면 circuit breaker 수행됨

```yaml
# application.yml
feign:
  hystrix:
    enabled: true
    
hystrix:
  command:
    # 전역설정
    default:
      execution.isolation.thread.timeoutInMilliseconds: 610

```

피호출 서비스(휴양소:resort) 의 임의 부하 처리 - 400 밀리초 ~ 620밀리초의 지연시간 부여
```java
# (resort) ResortController.java 

    @RequestMapping(method= RequestMethod.GET, value="/resorts/{id}")
        public Resort getResortStatus(@PathVariable("id") Long id){

            //hystix test code
             try {
                 Thread.currentThread().sleep((long) (400 + Math.random() * 220));
             } catch (InterruptedException e) { }

            return repository.findById(id).get();
        }
```

부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인 : 동시사용자 100명, 10초 동안 실시

```bash
$ siege -v -c100 -t10S -r10 --content-type "application/json" 'http://localhost:8081/reservations POST {"resortId":1, "memberName":"MK"}'

** SIEGE 4.0.5
** Preparing 100 concurrent users for battle.
The server is now under siege...

HTTP/1.1 201     3.64 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     3.64 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     3.70 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     3.94 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     3.98 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.00 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.05 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.05 secs:     343 bytes ==> POST http://localhost:8081/reservations

* 요청이 과도하여 CB를 동작함 요청을 차단

HTTP/1.1 500     4.07 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.07 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.07 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.07 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.07 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.08 secs:     183 bytes ==> POST http://localhost:8081/reservations

* 요청을 어느정도 돌려보내고나니, 기존에 밀린 일들이 처리되었고, 회로를 닫아 요청을 다시 받기 시작

HTTP/1.1 201     4.12 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.18 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.25 secs:     343 bytes ==> POST http://localhost:8081/reservations

* 다시 요청이 쌓이기 시작하여 건당 처리시간이 610 밀리를 살짝 넘기기 시작 => 회로 열기 => 요청 실패처리

HTTP/1.1 500     4.32 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.32 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.32 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.32 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.32 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.33 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.33 secs:     183 bytes ==> POST http://localhost:8081/reservations

* 다시 요청 처리 - (건당 (쓰레드당) 처리시간이 610 밀리 미만으로 회복) => 요청 수락

HTTP/1.1 201     4.45 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.48 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.53 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.54 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.58 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.66 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.70 secs:     343 bytes ==> POST http://localhost:8081/reservations

* 이후 이러한 패턴이 계속 반복되면서 시스템은 도미노 현상이나 자원 소모의 폭주 없이 잘 운영됨

HTTP/1.1 201     4.39 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.50 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.64 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.65 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.66 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.67 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.38 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.83 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.46 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.08 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     3.92 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     3.91 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.46 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.47 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.57 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.58 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.65 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.68 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.68 secs:     343 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.66 secs:     345 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.69 secs:     345 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.40 secs:     345 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.40 secs:     345 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.34 secs:     345 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.50 secs:     345 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.42 secs:     345 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.54 secs:     345 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.52 secs:     345 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.21 secs:     345 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.52 secs:     345 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 500     4.36 secs:     183 bytes ==> POST http://localhost:8081/reservations
HTTP/1.1 201     4.35 secs:     345 bytes ==> POST http://localhost:8081/reservations

Lifting the server siege...
Transactions:                    152 hits
Availability:                  80.85 %
Elapsed time:                   9.66 secs
Data transferred:               0.06 MB
Response time:                  4.92 secs
Transaction rate:              15.73 trans/sec
Throughput:                     0.01 MB/sec
Concurrency:                   77.34
Successful transactions:         152
Failed transactions:              36
Longest transaction:            5.63
Shortest transaction:           1.33

```
- siege 수행 결과

![image](https://user-images.githubusercontent.com/58622901/125236603-40778c80-e31f-11eb-81a7-eeaa4863239d.png)

![image](https://user-images.githubusercontent.com/58622901/125236641-4ff6d580-e31f-11eb-8659-6886b5cfacc5.png)

* 서킷 브레이크 프레임워크 : Spring FeignClient + Hystrix 옵션을 사용 (개인 Final 과제시 구현) 

- 시나리오 : 예약(reservation) -> 결제(payment) 예약 시 RESTful Request/Response 로 구현 하였고, 예약 요청이 과도할 경우 circuit breaker 를 통하여 장애격리 하였다.
- Hystrix 설정: 요청처리 쓰레드에서 처리시간이 610 밀리초가 넘어서기 시작하여 어느정도 유지되면 circuit breaker가 수행 된다.

```yaml
#reservation 서비스 application.yml
feign:
  resort:
    url: localhost:8082
  payment:
    url: localhost:8084
  hystrix:
    enabled: true 
    
hystrix:
  command:
    # 전역설정
    default:
      execution.isolation.thread.timeoutInMilliseconds: 610

```

- 피호출 서비스(결제:payment) 의 임의 부하 처리 - 400 밀리초 ~ 620밀리초의 지연시간 부여
- circuit breaker 발동 시 문구와 함께 null을 리턴함 
```java
# (payment) PaymentController.java 

@RequestMapping(method= RequestMethod.GET, value="/payments/{id}")
public Payment getPaymentStatus(@PathVariable("id") Long id){
       
     //hystix test code
       try {
           Thread.currentThread().sleep((long) (400 + Math.random() * 220));
       } catch (InterruptedException e) { }

   return repository.findById(id).get();
}

# (reservation) PaymentServiceFallback.java 

@Component
public class PaymentServiceFallback implements PaymentService {

    @Override
    public Payment getPaymentStatus(Long id) {
        System.out.println("Circuit breaker has been opened. Fallback returned instead.");
        return null;
    }

}

```
부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인 : 동시사용자 100명, 10초 동안 실시

```bash
$ http http://localhost:8082/resorts resortName="Jeju" resortType="Hotel" resortPrice=100000 resortStatus="Available" resortPeriod="7/23~25" -- 리조트등록
$ http http://localhost:8084/payments reservStatus="Waiting for payment" -- 결제서비스 확인을 위한 생성  

$ siege -v -c100 -t10S -r10 --content-type "application/json" 'http://localhost:8081/reservations/ POST {"resortId":1, "memberName":"MK"}' 

** SIEGE 4.0.4
** Preparing 100 concurrent users for battle.
The server is now under siege...


HTTP/1.1 201     3.55 secs:     362 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     3.57 secs:     362 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     3.75 secs:     364 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     3.87 secs:     364 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.10 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.11 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.12 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.16 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.20 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.20 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.28 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.46 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.63 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.70 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.74 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.83 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     4.82 secs:     343 bytes ==> POST http://localhost:8081/reservations/

* 요청이 과도하여 Circuit breaker를 동작함 요청을 차단
HTTP/1.1 500     4.94 secs:     255 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 500     4.95 secs:     255 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 500     5.14 secs:     255 bytes ==> POST http://localhost:8081/reservations/

* 요청을 어느정도 돌려보내고나니, 기존에 밀린 일들이 처리되었고, 회로를 닫아 요청을 다시 받기 시작
HTTP/1.1 201     5.25 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     5.30 secs:     343 bytes ==> POST http://localhost:8081/reservations/

* 다시 요청이 쌓이기 시작하여 건당 처리시간이 지연됨 => 회로 열기 => 요청 실패처리
HTTP/1.1 500     5.33 secs:     255 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 500     5.38 secs:     255 bytes ==> POST http://localhost:8081/reservations/

* 이후 이러한 패턴이 계속 반복되면서 시스템은 도미노 현상이나 자원 소모의 폭주 없이 잘 운영됨
HTTP/1.1 201     5.46 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     5.45 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     5.48 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     5.49 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 500     5.51 secs:     255 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     5.65 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     5.80 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     5.85 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     5.90 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 500     5.99 secs:     255 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     6.06 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     6.07 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     6.06 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     6.07 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     6.15 secs:     343 bytes ==> POST http://localhost:8081/reservations/
HTTP/1.1 201     6.24 secs:     343 bytes ==> POST http://localhost:8081/reservations/

Lifting the server siege...
Transactions:                     93 hits
Availability:                  80.87 %
Elapsed time:                   9.86 secs
Data transferred:               0.04 MB
Response time:                  7.54 secs
Transaction rate:               9.43 trans/sec
Throughput:                     0.00 MB/sec
Concurrency:                   71.08
Successful transactions:          93
Failed transactions:              22
Longest transaction:            9.70
Shortest transaction:           0.75

```

- siege 수행 결과

![image](https://user-images.githubusercontent.com/58622901/126892772-2474f46c-ed49-4a65-a588-1053988b3f1f.png)

![image](https://user-images.githubusercontent.com/58622901/126892825-0f881dfc-9879-43ad-83bc-8cd37054c9f9.png)



## 오토스케일 아웃
- 앞서 Circuit Breaker는 시스템을 안정되게 운영할 수 있게 하지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 
- payment 서비스에 대한 pod를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 20프로를 넘어서면 pod를 3개까지 늘려준다

```java 
paymentservice deployment.yml
containers:
        - name: payment
          image: 879772956301.dkr.ecr.ca-central-1.amazonaws.com/user24-payment:latest
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: "200m"
```

```bash
kubectl autoscale deployment payment --cpu-percent=20 --min=1 --max=3
```
- Circuit Breaker 에서 했던 방식대로 워크로드를 60초 동안 걸어준다.
```bash
siege -c40 -t60S -v http://payment:8080/payments
```
![image](https://user-images.githubusercontent.com/58622901/126938909-0fcc3f70-fb11-4b2a-bef7-c90f73480b8b.png)

- seige 수행 후 payment 시스템의 pod가 3개까지 scale out 된 것을 확인 할 수 있다.
![image](https://user-images.githubusercontent.com/58622901/126938755-9d68a751-fa2c-4247-9f59-08b28f1cfaa9.png)


## Zero-Downtime deploy (Readiness Probe)
- 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 Circuit Breaker 설정을 제거하고 테스트함
- seige로 배포중에 부하를 발생과 재배포 실행
```bash
root@siege:/# siege -c1 -t30S -v http://payment:8080/payments
kubectl apply -f  kubernetes/deployment.yml 
```
- seige 의 화면으로 넘어가서 Availability 가 100% 미만으로 떨어졌는지 확인
![image](https://user-images.githubusercontent.com/58622901/126941881-840072bd-e60b-41db-add8-40fc6a22476e.png)

배포기간중 Availability 가 평소 100%에서 36%로 떨어지는 것을 확인. 원인은 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행한 것이기 때문. 

- 이를 막기위해 Readiness Probe 를 설정함: deployment.yaml 의 readiness probe 추가
```yml
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
```

- 동일한 시나리오로 재배포 한 후 Availability 확인
![image](https://user-images.githubusercontent.com/58622901/126942146-bdbd101b-e715-4b25-b65d-f5c2c6ece607.png)
배포기간 동안 Availability 가 100%를 유지하기 때문에 무정지 재배포가 성공한 것으로 확인됨.

## Self-healing (Liveness Probe)
- Pod는 정상적으로 작동하지만 내부의 어플리케이션이 반응이 없다면, 컨테이너는 의미가 없다.
- 위와 같은 경우는 어플리케이션의 Liveness probe는 Pod의 상태를 체크하다가, Pod의 상태가 비정상인 경우 kubelet을 통해서 재시작한다.
- 임의대로 Liveness probe에서 path를 잘못된 값으로 변경 후, retry 시도 확인
```yml
          livenessProbe:
            httpGet:
              path: '/actuator/fakehealth' <-- path를 잘못된 값으로 변경
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```
- resort Pod가 여러차례 재시작 한것을 확인할 수 있다.
<img width="757" alt="image" src="https://user-images.githubusercontent.com/85722851/125048777-3cf3c380-e0db-11eb-99cd-97c7ebead85f.png">

## ConfigMap 사용
- 시스템별로 또는 운영중에 동적으로 변경 가능성이 있는 설정들을 ConfigMap을 사용하여 관리합니다. Application에서 특정 도메일 URL을 ConfigMap 으로 설정하여 운영/개발등 목적에 맞게 변경가능합니다.
configMap 생성
```bash
kubectl apply -f - <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: resort-cm
data:
    api.resort.url: resort:8080
EOF
```
configmap 생성 후 조회
<img width="881" alt="image" src="https://user-images.githubusercontent.com/85722851/125245232-470c0100-e32b-11eb-9db1-54f35d1b2e4c.png">
deployment.yml 변경
```yml
      containers:
          ...
          env:
            - name: feign.resort.url
              valueFrom:
                configMapKeyRef:
                  name: resort-cm
                  key: api.resort.url
```
ResortService.java내용
```java
@FeignClient(name="resort", url="${feign.resort.url}")
public interface ResortService {

    @RequestMapping(method= RequestMethod.GET, value="/resorts/{id}", consumes = "application/json")
    public Resort getResortStatus(@PathVariable("id") Long id);

}
```
생성된 Pod 상세 내용 확인
<img width="1036" alt="image" src="https://user-images.githubusercontent.com/85722851/125245075-162bcc00-e32b-11eb-80ab-81fa57e774d8.png">

