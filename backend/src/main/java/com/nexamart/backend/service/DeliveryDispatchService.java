package com.nexamart.backend.service;

import com.nexamart.backend.domain.*;
import com.nexamart.backend.repository.DeliveryPartnerProfileRepository;
import com.nexamart.backend.repository.NotificationRepository;
import com.nexamart.backend.repository.OrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Dispatches new customer orders to nearby delivery partners.
 *
 * Phase 1: first two minutes -> 5 km radius.
 * Phase 2: after two minutes without acceptance -> 8 km radius.
 *
 * Notifications are stored in the existing notifications table, so the partner
 * application can receive them through its existing notification API.
 */
@Service
public class DeliveryDispatchService {
  private static final double INITIAL_RADIUS_KM = 5.0;
  private static final double EXTENDED_RADIUS_KM = 8.0;
  private static final long EXTENSION_AFTER_MINUTES = 2L;
  private static final long LOCATION_FRESHNESS_MINUTES = 3L;

  private final OrderRepository orders;
  private final DeliveryPartnerProfileRepository profiles;
  private final NotificationRepository notifications;

  public DeliveryDispatchService(OrderRepository orders,
                                 DeliveryPartnerProfileRepository profiles,
                                 NotificationRepository notifications) {
    this.orders = orders;
    this.profiles = profiles;
    this.notifications = notifications;
  }

  @Scheduled(fixedDelay = 15000, initialDelay = 5000)
  @Transactional
  public void dispatchOpenOrders() {
    Instant now = Instant.now();
    // Keep the polling set bounded. Orders older than 15 minutes should normally
    // have been accepted, cancelled, or handled manually by an admin.
    Instant cutoff = now.minus(Duration.ofMinutes(15));
    List<Order> openOrders = orders.findByDeliveryPartnerIsNullAndStatusInAndCreatedAtAfter(
        List.of(OrderStatus.PENDING, OrderStatus.READY), cutoff);

    for (Order order : openOrders) {
      dispatch(order, now);
    }
  }

  private void dispatch(Order order, Instant now) {
    Address address = order.getDeliveryAddress();
    if (address == null || address.getLatitude() == null || address.getLongitude() == null) {
      return;
    }

    long ageSeconds = Math.max(0L, Duration.between(order.getCreatedAt(), now).getSeconds());
    boolean extended = ageSeconds >= EXTENSION_AFTER_MINUTES * 60L;
    double radiusKm = extended ? EXTENDED_RADIUS_KM : INITIAL_RADIUS_KM;

    profiles.findAll().stream()
        .filter(DeliveryPartnerProfile::isAvailable)
        .filter(p -> p.getUser() != null && p.getUser().getStatus() == AccountStatus.ACTIVE)
        .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
        .filter(p -> p.getLocationUpdatedAt() != null
            && Duration.between(p.getLocationUpdatedAt(), now).toMinutes() <= LOCATION_FRESHNESS_MINUTES)
        .forEach(profile -> {
          double distanceKm = distanceKm(
              address.getLatitude(), address.getLongitude(),
              profile.getLatitude(), profile.getLongitude());
          if (distanceKm > radiusKm) {
            return;
          }

          Long partnerId = profile.getUser().getId();
          if (notifications.existsByUserIdAndOrderIdAndType(
              partnerId, order.getId(), NotificationType.ORDER_ASSIGNED)) {
            return;
          }

          Notification n = new Notification();
          n.setUser(profile.getUser());
          n.setTitle(extended ? "Delivery order available nearby" : "New delivery order nearby");
          n.setMessage(String.format(
              "Order #%d is %.1f km away. Delivery request is open within %d km.",
              order.getId(), distanceKm, (int) radiusKm));
          n.setType(NotificationType.ORDER_ASSIGNED);
          n.setOrderId(order.getId());
          n.setActionUrl("/delivery/orders/" + order.getId());
          notifications.save(n);
        });
  }

  /** Haversine distance between two WGS84 coordinates. */
  static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
    double earthRadiusKm = 6371.0088;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
}
