package com.nexamart.backend.api;

import com.nexamart.backend.api.ApiModels.*;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.service.AuthService;
import com.nexamart.backend.service.PartnerOtpService;
import com.nexamart.backend.service.UserLookupService;
import com.nexamart.backend.domain.*;
import com.nexamart.backend.repository.DeliveryPartnerProfileRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth; private final PartnerOtpService otp; private final DeliveryPartnerProfileRepository profiles; private final UserLookupService userLookup;
  public AuthController(AuthService auth, PartnerOtpService otp, DeliveryPartnerProfileRepository profiles, UserLookupService userLookup) { this.auth = auth; this.otp = otp; this.profiles = profiles; this.userLookup = userLookup; }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = auth.login(request);
    requirePartnerRole(response);
    return response;
  }

  @PostMapping("/register")
  public RegistrationResponse registerDeliveryPartner(@Valid @RequestBody RegisterRequest request) {
    return auth.register(request);
  }

  @PostMapping("/partner/send-otp") public OtpSendResponse sendPartnerOtp(@Valid @RequestBody OtpRequest request){return otp.send(request.phone());}
  @PostMapping("/partner/verify-otp") public LoginResponse verifyPartnerOtp(@Valid @RequestBody VerifyOtpRequest request){return otp.verify(request.phone(),request.otp());}

  @PostMapping(value="/partner/register",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public RegistrationResponse registerPartner(@RequestParam String name,@RequestParam String email,@RequestParam String phone,@RequestParam String password,@RequestParam String confirmPassword,@RequestParam(required=false) String dateOfBirth,@RequestParam(required=false) String vehicleType,@RequestParam(required=false) String vehicleNumber,@RequestParam(required=false) String drivingLicenseNumber,@RequestParam(required=false) String aadhaarNumber,@RequestPart("aadhaarPhoto") MultipartFile aadhaarPhoto) throws java.io.IOException {
    var response=auth.register(new RegisterRequest(name,email,phone,password,confirmPassword));
    String normalized=com.nexamart.backend.util.PhoneNumbers.requireIndianMobile(phone);
    var user=userLookup.findByPhonePreferringRole(normalized, Role.DELIVERY_PARTNER)
        .orElseThrow(()->new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"Partner account could not be loaded after registration."));
    var profile=profiles.findById(user.getId())
        .orElseThrow(()->new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"Partner profile could not be loaded after registration."));
    profile.setDateOfBirth(dateOfBirth); profile.setVehicleType(vehicleType); profile.setVehicleNumber(vehicleNumber); profile.setDrivingLicenseNumber(drivingLicenseNumber); profile.setAadhaarNumber(aadhaarNumber); profile.setAadhaarPhotoData(aadhaarPhoto.getBytes()); profile.setAadhaarPhotoContentType(aadhaarPhoto.getContentType()); profiles.save(profile);
    return response;
  }

  @PostMapping("/admin/login")
  public LoginResponse adminLogin(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = auth.login(request);
    if (!"ADMIN".equals(response.user().role())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Admin access required.");
    }
    return response;
  }

  @PostMapping("/refresh")
  public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
    LoginResponse response = auth.refresh(request);
    requirePartnerRole(response);
    return response;
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() { return ResponseEntity.noContent().build(); }

  private void requirePartnerRole(LoginResponse response) {
    String role = response.user().role();
    if (!"ADMIN".equals(role) && !"DELIVERY_PARTNER".equals(role)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Admin or delivery partner access required.");
    }
  }
}
