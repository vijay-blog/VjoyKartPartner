package com.nexamart.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="delivery_partner_profiles")
public class DeliveryPartnerProfile {
  @Id
  private Long userId;

  @OneToOne
  @MapsId
  @JoinColumn(name="user_id")
  private UserAccount user;

  private String verificationStatus="PENDING";
  private String vehicleType;
  private String vehicleNumber;
  private String licenseReference;
  private String dateOfBirth;
  private String drivingLicenseNumber;
  private String aadhaarNumber;
  @Lob @Column(name="aadhaar_photo_data", columnDefinition="LONGBLOB") private byte[] aadhaarPhotoData;
  private String aadhaarPhotoContentType;
  private boolean available=false;
  private Double latitude;
  private Double longitude;
  private Instant locationUpdatedAt;
  private Instant updatedAt=Instant.now();

  public Long getUserId(){return userId;}
  public UserAccount getUser(){return user;}
  public void setUser(UserAccount v){user=v;}
  public String getVerificationStatus(){return verificationStatus;}
  public void setVerificationStatus(String v){verificationStatus=v;}
  public String getVehicleType(){return vehicleType;}
  public void setVehicleType(String v){vehicleType=v;}
  public String getVehicleNumber(){return vehicleNumber;}
  public void setVehicleNumber(String v){vehicleNumber=v;}
  public String getLicenseReference(){return licenseReference;}
  public void setLicenseReference(String v){licenseReference=v;}
  public boolean isAvailable(){return available;}
  public void setAvailable(boolean v){available=v;updatedAt=Instant.now();}
  public Double getLatitude(){return latitude;}
  public void setLatitude(Double v){latitude=v;}
  public Double getLongitude(){return longitude;}
  public void setLongitude(Double v){longitude=v;}
  public Instant getLocationUpdatedAt(){return locationUpdatedAt;}
  public void setLocationUpdatedAt(Instant v){locationUpdatedAt=v;}
  public Instant getUpdatedAt(){return updatedAt;}
}
