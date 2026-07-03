import React, { useState, useEffect, useCallback } from 'react';
import { 
  StyleSheet, 
  View, 
  TouchableOpacity, 
  TextInput, 
  Alert, 
  ActivityIndicator, 
  useColorScheme, 
  Platform,
  ScrollView,
  Modal,
  Linking
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { AppIcon } from '@/components/app-icon';
import { Spacing, Colors } from '@/constants/theme';
import { useAuth } from '@/context/AuthContext';
import { appConfig } from '@/utils/app-config';

interface DispatchOffer {
  offerId: string;
  jobId: string;
  captainId: string;
  offeredAt: string;
  response: string | null;
  offerRank: number;
  orderId: string;
}

interface ActiveDelivery {
  jobId: string;
  orderId: string;
  storeName: string;
  storeAddress: string;
  storeLat: number | null;
  storeLng: number | null;
  customerName: string;
  customerAddress: string;
  customerLat: number | null;
  customerLng: number | null;
  deliveryFee: number | null;
}

export default function DeliveryScreen() {
  const scheme = useColorScheme() === 'dark' ? 'dark' : 'light';
  const colors = Colors[scheme];
  const { user, session } = useAuth();

  const [isOnline, setIsOnline] = useState(false);
  const [loading, setLoading] = useState(false);
  const [activeOffer, setActiveOffer] = useState<DispatchOffer | null>(null);
  const [offerCountdown, setOfferCountdown] = useState(30);
  const [activeDelivery, setActiveDelivery] = useState<ActiveDelivery | null>(null);
  
  // Delivery Stepper State: 1 = En Route to Store, 2 = Arrived at Store/Pickup verification, 3 = En Route to Customer, 4 = Handover verification
  const [deliveryStep, setDeliveryStep] = useState(1);
  const [pickupOtp, setPickupOtp] = useState('');
  const [deliveryOtp, setDeliveryOtp] = useState('');
  const [verifyingOtp, setVerifyingOtp] = useState(false);

  const authHeaders = useCallback((contentType = false) => {
    const headers: Record<string, string> = {};
    if (contentType) {
      headers['Content-Type'] = 'application/json';
    }
    if (user?.id) {
      headers['X-User-Id'] = user.id;
    }
    if (session?.access_token) {
      headers.Authorization = `Bearer ${session.access_token}`;
    }
    return headers;
  }, [session, user]);

  const shortOrderId = (orderId: string) => orderId.slice(0, 8).toUpperCase();

  const buildActiveDelivery = (offer: DispatchOffer): ActiveDelivery => ({
    jobId: offer.jobId,
    orderId: offer.orderId,
    storeName: `Pickup for order ${shortOrderId(offer.orderId)}`,
    storeAddress: 'Pickup details are linked to the accepted order.',
    storeLat: null,
    storeLng: null,
    customerName: `Customer order ${shortOrderId(offer.orderId)}`,
    customerAddress: 'Customer address is managed by the order workflow.',
    customerLat: null,
    customerLng: null,
    deliveryFee: null
  });

  // --- Toggle Online/Offline ---
  const toggleOnline = useCallback(async () => {
    if (!user) return;
    setLoading(true);
    try {
      const nextOnline = !isOnline;
      // Coordinates for provider/delivery center location reporting (Delhi center in this case)
      const lat = 28.6139;
      const lng = 77.2090;

      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/captains/status`, {
        method: 'PUT',
        headers: authHeaders(true),
        body: JSON.stringify({
          online: nextOnline,
          longitude: lng,
          latitude: lat
        })
      });
      const data = await response.json();
      if (response.ok) {
        setIsOnline(nextOnline);
        if (!nextOnline) {
          setActiveOffer(null);
        }
      } else {
        Alert.alert('Status Change Failed', data.error || 'Check internet connection');
      }
    } catch (err) {
      console.warn("Online status toggle error:", err);
      if (appConfig.allowDemoMode) {
        setIsOnline(!isOnline);
      } else {
        Alert.alert('Status Change Failed', 'Could not reach captain service. Please retry when the service is available.');
      }
    } finally {
      setLoading(false);
    }
  }, [authHeaders, isOnline, user]);

  // --- Periodically Poll Location Coordinate Updates ---
  useEffect(() => {
    if (!isOnline || !user) return;

    const interval = setInterval(async () => {
      try {
        // Mock slight movements around Delhi center
        const randomShift = (Math.random() - 0.5) * 0.005;
        const lat = 28.6139 + randomShift;
        const lng = 77.2090 + randomShift;

        await fetch(`${appConfig.apiBaseUrl}/api/v1/captains/location`, {
          method: 'PUT',
          headers: authHeaders(true),
          body: JSON.stringify({
            longitude: lng,
            latitude: lat
          })
        });
      } catch (err) {
        console.log("Failed to report periodic location update:", err);
      }
    }, 20000);

    return () => clearInterval(interval);
  }, [authHeaders, isOnline, user]);

  // --- Periodically Check for Incoming Job Offers ---
  useEffect(() => {
    if (!isOnline || !user || activeDelivery || activeOffer) return;

    const offerPoll = setInterval(async () => {
      try {
        const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/dispatch/offers`, {
          headers: authHeaders()
        });
        const data: DispatchOffer[] = await response.json();
        if (response.ok && data.length > 0) {
          const offer = data[0];
          setActiveOffer(offer);
          setOfferCountdown(30);
        }
      } catch (err) {
        // Silent catch
      }
    }, 4000);

    return () => clearInterval(offerPoll);
  }, [authHeaders, isOnline, user, activeDelivery, activeOffer]);

  // --- Job Offer Countdown Timer ---
  useEffect(() => {
    if (!activeOffer) return;

    const timer = setInterval(() => {
      setOfferCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          setActiveOffer(null);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [activeOffer]);

  // --- Respond to Dispatch Offer ---
  const respondToOffer = useCallback(async (responseType: 'ACCEPTED' | 'REJECTED') => {
    if (!activeOffer) return;
    setLoading(true);
    try {
      const res = await fetch(`${appConfig.apiBaseUrl}/api/v1/dispatch/offers/${activeOffer.offerId}/respond?response=${responseType}`, {
        method: 'POST',
        headers: authHeaders()
      });
      if (res.ok) {
        if (responseType === 'ACCEPTED') {
          setActiveDelivery(buildActiveDelivery(activeOffer));
          setDeliveryStep(1);
        }
        setActiveOffer(null);
      } else {
        Alert.alert('Response Failed', 'Offer might have timed out');
        setActiveOffer(null);
      }
    } catch (err) {
      console.warn("Response failed:", err);
      if (responseType === 'ACCEPTED' && appConfig.allowDemoMode) {
        setActiveDelivery(buildActiveDelivery(activeOffer));
        setDeliveryStep(1);
      } else if (!appConfig.allowDemoMode) {
        Alert.alert('Response Failed', 'Could not reach dispatch. Please retry when the service is available.');
      }
      setActiveOffer(null);
    } finally {
      setLoading(false);
    }
  }, [activeOffer, authHeaders]);

  // --- Map Deep Linking Navigator ---
  const handleNavigate = useCallback((lat: number, lng: number, label: string) => {
    const scheme = Platform.select({ ios: 'maps:0,0?q=', android: 'geo:0,0?q=', default: 'https://maps.google.com/?q=' });
    const url = `${scheme}${lat},${lng}(${label})`;
    Linking.openURL(url);
  }, []);

  // --- Confirm Store Arrival ---
  const handleArriveAtStore = useCallback(() => {
    setDeliveryStep(2);
  }, []);

  // --- Verify Store Pickup ---
  const handleVerifyPickup = useCallback(async () => {
    if (!activeDelivery) return;
    if (pickupOtp !== '1234') {
      Alert.alert('Verification Error', 'Invalid pickup verification OTP.');
      return;
    }
    setVerifyingOtp(true);
    try {
      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/dispatch/jobs/${activeDelivery.jobId}/pickup`, {
        method: 'POST',
        headers: authHeaders(true),
        body: JSON.stringify({ proofCode: pickupOtp })
      });
      if (response.ok) {
        setDeliveryStep(3);
      } else {
        Alert.alert('Error', 'Failed to update order status to Picked Up.');
      }
    } catch (err) {
      if (appConfig.allowDemoMode) {
        setDeliveryStep(3);
      } else {
        Alert.alert('Error', 'Could not confirm pickup. Please retry when the service is available.');
      }
    } finally {
      setVerifyingOtp(false);
    }
  }, [activeDelivery, authHeaders, pickupOtp]);

  // --- Confirm Customer Arrival ---
  const handleArriveAtCustomer = useCallback(() => {
    setDeliveryStep(4);
  }, []);

  // --- Verify Handover and Complete Delivery ---
  const handleCompleteDelivery = useCallback(async () => {
    if (!activeDelivery) return;
    if (deliveryOtp !== '5678') {
      Alert.alert('Verification Error', 'Invalid handover verification OTP.');
      return;
    }
    setVerifyingOtp(true);
    try {
      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/dispatch/jobs/${activeDelivery.jobId}/deliver`, {
        method: 'POST',
        headers: authHeaders(true),
        body: JSON.stringify({ proofCode: deliveryOtp })
      });
      if (response.ok) {
        Alert.alert('Success', 'Order delivered successfully! Earnings added.');
        setActiveDelivery(null);
      } else {
        Alert.alert('Error', 'Failed to finalize delivery.');
      }
    } catch (err) {
      if (appConfig.allowDemoMode) {
        Alert.alert('Success', 'Offline Sandbox: Order delivered successfully!');
        setActiveDelivery(null);
      } else {
        Alert.alert('Error', 'Could not finalize delivery. Please retry when the service is available.');
      }
    } finally {
      setVerifyingOtp(false);
    }
  }, [activeDelivery, authHeaders, deliveryOtp]);

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <ScrollView contentContainerStyle={styles.scrollContent}>
          {/* Header */}
          <View style={styles.header}>
            <ThemedText type="subtitle">Delivery Operations</ThemedText>
            <ThemedText type="small" style={{ color: colors.textSecondary }}>
              Ride and earn with PawsNearMe Captains 🚴
            </ThemedText>
          </View>

          {/* Stepper View if Active Delivery exists */}
          {activeDelivery ? (
            <View style={[styles.card, { backgroundColor: colors.backgroundElement }]}>
                <View style={styles.cardHeader}>
                  <ThemedText style={{ fontWeight: '700', fontSize: 16 }}>Active Delivery Job</ThemedText>
                <ThemedText style={{ color: colors.cta, fontWeight: '700' }}>
                  {activeDelivery.deliveryFee === null ? 'Earning pending' : `₹${activeDelivery.deliveryFee.toFixed(2)}`}
                </ThemedText>
              </View>

              <View style={{ marginVertical: Spacing.two, gap: Spacing.two }}>
                <View style={styles.stepIndicator}>
                  <View style={[styles.stepDot, { backgroundColor: deliveryStep >= 1 ? colors.primary : '#ccc' }]} />
                  <View style={[styles.stepLine, { backgroundColor: deliveryStep >= 2 ? colors.primary : '#ccc' }]} />
                  <View style={[styles.stepDot, { backgroundColor: deliveryStep >= 2 ? colors.primary : '#ccc' }]} />
                  <View style={[styles.stepLine, { backgroundColor: deliveryStep >= 3 ? colors.primary : '#ccc' }]} />
                  <View style={[styles.stepDot, { backgroundColor: deliveryStep >= 3 ? colors.primary : '#ccc' }]} />
                  <View style={[styles.stepLine, { backgroundColor: deliveryStep >= 4 ? colors.primary : '#ccc' }]} />
                  <View style={[styles.stepDot, { backgroundColor: deliveryStep >= 4 ? colors.primary : '#ccc' }]} />
                </View>

                {deliveryStep === 1 && (
                  <View style={styles.stepContent}>
                    <ThemedText style={{ fontWeight: '600' }}>Step 1: Ride to Pet Store</ThemedText>
                    <ThemedText type="small" style={{ marginVertical: Spacing.one }}>{activeDelivery.storeName}</ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>{activeDelivery.storeAddress}</ThemedText>
                    <View style={styles.actionRow}>
                      <TouchableOpacity 
                        style={[styles.btnSecondary, { borderColor: colors.primary }]}
                        onPress={() => {
                          if (activeDelivery.storeLat !== null && activeDelivery.storeLng !== null) {
                            handleNavigate(activeDelivery.storeLat, activeDelivery.storeLng, activeDelivery.storeName);
                          }
                        }}
                        disabled={activeDelivery.storeLat === null || activeDelivery.storeLng === null}
                      >
                        <View style={styles.navButtonContent}>
                          <AppIcon name="location" color={colors.primary} size={15} />
                          <ThemedText type="smallBold" style={{ color: colors.primary }}>Navigate</ThemedText>
                        </View>
                      </TouchableOpacity>
                      <TouchableOpacity 
                        style={[styles.btnPrimary, { backgroundColor: colors.primary }]}
                        onPress={handleArriveAtStore}
                      >
                        <ThemedText type="smallBold" style={{ color: '#fff' }}>I Have Arrived</ThemedText>
                      </TouchableOpacity>
                    </View>
                  </View>
                )}

                {deliveryStep === 2 && (
                  <View style={styles.stepContent}>
                    <ThemedText style={{ fontWeight: '600' }}>Step 2: Collect & Verify Items</ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary, marginVertical: Spacing.one }}>
                      Ask the merchant for the pickup verification code (Dev OTP: 1234)
                    </ThemedText>
                    <TextInput
                      placeholder="Enter Pickup OTP"
                      placeholderTextColor={colors.textSecondary}
                      keyboardType="number-pad"
                      maxLength={4}
                      style={[styles.input, { backgroundColor: colors.backgroundSelected, color: colors.text }]}
                      value={pickupOtp}
                      onChangeText={setPickupOtp}
                    />
                    <TouchableOpacity 
                      style={[styles.btnPrimary, { backgroundColor: colors.cta, width: '100%', marginTop: Spacing.two }]}
                      onPress={handleVerifyPickup}
                      disabled={verifyingOtp}
                    >
                      {verifyingOtp ? (
                        <ActivityIndicator color="#fff" />
                      ) : (
                        <ThemedText type="smallBold" style={{ color: '#fff' }}>Confirm Pickup</ThemedText>
                      )}
                    </TouchableOpacity>
                  </View>
                )}

                {deliveryStep === 3 && (
                  <View style={styles.stepContent}>
                    <ThemedText style={{ fontWeight: '600' }}>Step 3: Deliver to Customer</ThemedText>
                    <ThemedText type="small" style={{ marginVertical: Spacing.one }}>{activeDelivery.customerName}</ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary }}>{activeDelivery.customerAddress}</ThemedText>
                    <View style={styles.actionRow}>
                      <TouchableOpacity 
                        style={[styles.btnSecondary, { borderColor: colors.primary }]}
                        onPress={() => {
                          if (activeDelivery.customerLat !== null && activeDelivery.customerLng !== null) {
                            handleNavigate(activeDelivery.customerLat, activeDelivery.customerLng, activeDelivery.customerName);
                          }
                        }}
                        disabled={activeDelivery.customerLat === null || activeDelivery.customerLng === null}
                      >
                        <View style={styles.navButtonContent}>
                          <AppIcon name="location" color={colors.primary} size={15} />
                          <ThemedText type="smallBold" style={{ color: colors.primary }}>Navigate</ThemedText>
                        </View>
                      </TouchableOpacity>
                      <TouchableOpacity 
                        style={[styles.btnPrimary, { backgroundColor: colors.primary }]}
                        onPress={handleArriveAtCustomer}
                      >
                        <ThemedText type="smallBold" style={{ color: '#fff' }}>I Have Arrived</ThemedText>
                      </TouchableOpacity>
                    </View>
                  </View>
                )}

                {deliveryStep === 4 && (
                  <View style={styles.stepContent}>
                    <ThemedText style={{ fontWeight: '600' }}>Step 4: Verify Handover</ThemedText>
                    <ThemedText type="small" style={{ color: colors.textSecondary, marginVertical: Spacing.one }}>
                      Ask the customer for the handover verification code (Dev OTP: 5678)
                    </ThemedText>
                    <TextInput
                      placeholder="Enter Handover OTP"
                      placeholderTextColor={colors.textSecondary}
                      keyboardType="number-pad"
                      maxLength={4}
                      style={[styles.input, { backgroundColor: colors.backgroundSelected, color: colors.text }]}
                      value={deliveryOtp}
                      onChangeText={setDeliveryOtp}
                    />
                    <TouchableOpacity 
                      style={[styles.btnPrimary, { backgroundColor: colors.cta, width: '100%', marginTop: Spacing.two }]}
                      onPress={handleCompleteDelivery}
                      disabled={verifyingOtp}
                    >
                      {verifyingOtp ? (
                        <ActivityIndicator color="#fff" />
                      ) : (
                        <ThemedText type="smallBold" style={{ color: '#fff' }}>Complete Delivery</ThemedText>
                      )}
                    </TouchableOpacity>
                  </View>
                )}
              </View>
            </View>
          ) : (
            // Offline / Idle View
            <View style={styles.idleContainer}>
              <View style={[styles.statusCard, { backgroundColor: colors.backgroundElement }]}>
                <View style={styles.statusRow}>
                  <View style={[styles.indicatorDot, { backgroundColor: isOnline ? colors.cta : '#ff3b30' }]} />
                  <ThemedText style={{ fontWeight: '700' }}>
                    {isOnline ? 'Online & Waiting' : 'Offline'}
                  </ThemedText>
                </View>
                <ThemedText type="small" style={{ color: colors.textSecondary, marginTop: Spacing.one }}>
                  {isOnline 
                    ? 'Your current GPS coordinates are periodically reported to Dispatch Service. Please keep the app open to receive local delivery jobs.' 
                    : 'Switch online to start getting notifications for nearby orders ready for delivery.'}
                </ThemedText>
              </View>

              <TouchableOpacity
                style={[
                  styles.toggleBtn, 
                  { backgroundColor: isOnline ? '#ff3b30' : colors.primary }
                ]}
                onPress={toggleOnline}
                disabled={loading}
                activeOpacity={0.8}
              >
                {loading ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <ThemedText style={styles.btnText}>
                    {isOnline ? 'Go Offline ⛔' : 'Go Online 🚴'}
                  </ThemedText>
                )}
              </TouchableOpacity>
            </View>
          )}
        </ScrollView>
      </SafeAreaView>

      {/* Incoming Job offer Modal */}
      <Modal
        animationType="fade"
        transparent={true}
        visible={activeOffer !== null}
        onRequestClose={() => setActiveOffer(null)}
      >
        <View style={styles.modalBackdrop}>
          <View style={[styles.modalContent, { backgroundColor: colors.backgroundElement }]}>
            <View style={styles.modalHeader}>
              <ThemedText style={{ fontWeight: 'bold', fontSize: 18 }}>Incoming Job Offer! 🚨</ThemedText>
              <ThemedText style={styles.countdownTimer}>{offerCountdown}s</ThemedText>
            </View>
            
            <View style={{ marginVertical: Spacing.three, gap: Spacing.two }}>
              <ThemedText style={{ fontWeight: '600' }}>
                Order: {activeOffer ? shortOrderId(activeOffer.orderId) : 'Pending'}
              </ThemedText>
              <ThemedText type="small" style={{ color: colors.textSecondary }}>Pickup and delivery details unlock after accepting.</ThemedText>
              <ThemedText style={{ fontWeight: '600', marginTop: Spacing.one }}>Earning records after delivery completion</ThemedText>
            </View>

            <View style={styles.offerProgressBar}>
              <View style={[styles.offerProgressFill, { width: `${(offerCountdown / 30) * 100}%`, backgroundColor: colors.primary }]} />
            </View>

            <View style={styles.modalActionRow}>
              <TouchableOpacity 
                style={[styles.modalBtnReject, { borderColor: '#ff3b30' }]}
                onPress={() => respondToOffer('REJECTED')}
              >
                <ThemedText style={{ color: '#ff3b30', fontWeight: 'bold' }}>Decline</ThemedText>
              </TouchableOpacity>
              <TouchableOpacity 
                style={[styles.modalBtnAccept, { backgroundColor: colors.cta }]}
                onPress={() => respondToOffer('ACCEPTED')}
              >
                <ThemedText style={{ color: '#fff', fontWeight: 'bold' }}>Accept Job</ThemedText>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.three,
    paddingBottom: Spacing.five,
  },
  header: {
    marginBottom: Spacing.four,
  },
  idleContainer: {
    gap: Spacing.three,
    marginTop: Spacing.two,
  },
  statusCard: {
    padding: Spacing.three,
    borderRadius: Spacing.two,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
  indicatorDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  toggleBtn: {
    height: 52,
    borderRadius: Spacing.two,
    justifyContent: 'center',
    alignItems: 'center',
  },
  btnText: {
    color: '#fff',
    fontWeight: '700',
    fontSize: 16,
  },
  card: {
    padding: Spacing.three,
    borderRadius: Spacing.two,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0,0,0,0.1)',
    paddingBottom: Spacing.two,
    marginBottom: Spacing.two,
  },
  stepIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginVertical: Spacing.two,
  },
  stepDot: {
    width: 14,
    height: 14,
    borderRadius: 7,
  },
  stepLine: {
    flex: 1,
    height: 3,
  },
  stepContent: {
    gap: Spacing.one,
    paddingTop: Spacing.one,
  },
  actionRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.three,
  },
  btnPrimary: {
    flex: 1,
    height: 48,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },
  btnSecondary: {
    flex: 1,
    height: 48,
    borderRadius: 8,
    borderWidth: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  navButtonContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.one,
  },
  input: {
    height: 48,
    borderRadius: 8,
    paddingHorizontal: Spacing.two,
    fontSize: 14,
    marginTop: Spacing.one,
  },
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: Spacing.four,
  },
  modalContent: {
    width: '100%',
    maxWidth: 400,
    borderRadius: Spacing.three,
    padding: Spacing.four,
    elevation: 5,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  countdownTimer: {
    fontWeight: 'bold',
    fontSize: 18,
    color: '#ff9500',
  },
  offerProgressBar: {
    height: 4,
    backgroundColor: 'rgba(0,0,0,0.1)',
    borderRadius: 2,
    overflow: 'hidden',
    marginBottom: Spacing.three,
  },
  offerProgressFill: {
    height: '100%',
  },
  modalActionRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  modalBtnAccept: {
    flex: 1,
    height: 48,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalBtnReject: {
    flex: 1,
    height: 48,
    borderRadius: 8,
    borderWidth: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
