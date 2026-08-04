import { CameraView, useCameraPermissions, type BarcodeScanningResult, type BarcodeType } from 'expo-camera';
import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, StyleSheet, Vibration, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { useTheme } from '@/hooks/use-theme';
import { barcodeValidationMessage, normalizeBarcode } from '@/utils/barcode';

const BARCODE_TYPES: BarcodeType[] = [
  'ean13',
  'ean8',
  'upc_a',
  'upc_e',
  'code128',
  'code39',
  'code93',
  'itf14',
  'codabar',
];

function isConfiguredBarcodeType(value: string): value is BarcodeType {
  return BARCODE_TYPES.includes(value as BarcodeType);
}

type BarcodeScannerModalProps = {
  visible: boolean;
  title?: string;
  instruction?: string;
  onClose: () => void;
  onScanned: (barcode: string, type: BarcodeType) => void | Promise<void>;
};

export function BarcodeScannerModal({
  visible,
  title = 'Scan product barcode',
  instruction = 'Align the barcode inside the frame. The scanner closes after one valid read.',
  onClose,
  onScanned,
}: BarcodeScannerModalProps) {
  const theme = useTheme();
  const [permission, requestPermission] = useCameraPermissions();
  const [scanLocked, setScanLocked] = useState(false);
  const [torchEnabled, setTorchEnabled] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) return;
    setScanLocked(false);
    setTorchEnabled(false);
    setCameraError(null);
  }, [visible]);

  const handleScan = useCallback(async (result: BarcodeScanningResult) => {
    if (scanLocked) return;

    const barcode = normalizeBarcode(result.data ?? '');
    const validationMessage = barcodeValidationMessage(barcode);
    if (!barcode || validationMessage) {
      setCameraError(validationMessage ?? 'The scanned barcode was empty. Try again.');
      return;
    }
    if (!isConfiguredBarcodeType(result.type)) {
      setCameraError(`Unsupported barcode format: ${result.type}.`);
      return;
    }

    setScanLocked(true);
    Vibration.vibrate(45);
    try {
      await onScanned(barcode, result.type);
      onClose();
    } catch (error) {
      setCameraError(error instanceof Error ? error.message : 'Could not process the scanned barcode.');
      setScanLocked(false);
    }
  }, [onClose, onScanned, scanLocked]);

  const requestCamera = useCallback(async () => {
    const response = await requestPermission();
    if (!response.granted) {
      setCameraError('Camera permission is required to scan product barcodes.');
    }
  }, [requestPermission]);

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose} presentationStyle="fullScreen">
      <View style={styles.root} accessibilityViewIsModal>
        {permission?.granted ? (
          <CameraView
            active={visible}
            facing="back"
            enableTorch={torchEnabled}
            style={StyleSheet.absoluteFill}
            barcodeScannerSettings={{ barcodeTypes: BARCODE_TYPES }}
            onBarcodeScanned={scanLocked ? undefined : handleScan}
            onMountError={(event) => setCameraError(event.message || 'Camera could not start.')}
          />
        ) : (
          <View style={[styles.permissionState, { backgroundColor: theme.background }]}>
            {!permission ? <ActivityIndicator size="large" color={theme.primary} /> : null}
            <ThemedText type="title">Camera permission</ThemedText>
            <ThemedText style={[styles.centeredText, { color: theme.textSecondary }]}>
              Allow camera access to scan EAN, UPC, Code 39 and Code 128 labels.
            </ThemedText>
            {permission ? (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Allow camera access"
                onPress={() => void requestCamera()}
                style={[styles.primaryButton, { backgroundColor: theme.primary }]}
              >
                <ThemedText style={styles.primaryButtonText}>Allow camera</ThemedText>
              </Pressable>
            ) : null}
            <Pressable accessibilityRole="button" onPress={onClose} style={styles.secondaryButton}>
              <ThemedText type="smallBold">Cancel</ThemedText>
            </Pressable>
          </View>
        )}

        {permission?.granted ? (
          <View style={styles.overlay} pointerEvents="box-none">
            <View style={styles.header}>
              <View style={styles.headerCopy}>
                <ThemedText style={styles.headerTitle}>{title}</ThemedText>
                <ThemedText style={styles.headerInstruction}>{instruction}</ThemedText>
              </View>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Close barcode scanner"
                onPress={onClose}
                style={styles.headerButton}
              >
                <ThemedText style={styles.headerButtonText}>Close</ThemedText>
              </Pressable>
            </View>

            <View style={styles.frame} accessibilityLabel="Barcode scanning frame" />

            <View style={styles.footer}>
              {cameraError ? (
                <ThemedText accessibilityLiveRegion="polite" style={styles.errorText}>
                  {cameraError}
                </ThemedText>
              ) : (
                <ThemedText style={styles.tipText}>Hold the label steady and avoid glare.</ThemedText>
              )}
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={torchEnabled ? 'Turn scanner light off' : 'Turn scanner light on'}
                onPress={() => setTorchEnabled((current) => !current)}
                style={styles.torchButton}
              >
                <ThemedText style={styles.headerButtonText}>{torchEnabled ? 'Light off' : 'Light on'}</ThemedText>
              </Pressable>
            </View>
          </View>
        ) : null}
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#000' },
  permissionState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
    paddingHorizontal: 28,
  },
  centeredText: { textAlign: 'center', maxWidth: 420 },
  primaryButton: {
    minHeight: 50,
    minWidth: 180,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 14,
    paddingHorizontal: 22,
  },
  primaryButtonText: { color: '#fff', fontWeight: '700' },
  secondaryButton: { minHeight: 48, justifyContent: 'center', paddingHorizontal: 20 },
  overlay: {
    ...StyleSheet.absoluteFill,
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 54,
    paddingBottom: 42,
    backgroundColor: 'rgba(0,0,0,0.28)',
  },
  header: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 16,
  },
  headerCopy: { flex: 1, gap: 6 },
  headerTitle: { color: '#fff', fontSize: 22, lineHeight: 28, fontWeight: '800' },
  headerInstruction: { color: '#fff', fontSize: 14, lineHeight: 20 },
  headerButton: {
    minHeight: 48,
    justifyContent: 'center',
    borderRadius: 24,
    backgroundColor: 'rgba(0,0,0,0.62)',
    paddingHorizontal: 18,
  },
  headerButtonText: { color: '#fff', fontWeight: '700' },
  frame: {
    width: '88%',
    maxWidth: 420,
    height: 190,
    borderWidth: 3,
    borderColor: '#f59e0b',
    borderRadius: 16,
    backgroundColor: 'transparent',
  },
  footer: { width: '100%', alignItems: 'center', gap: 14 },
  tipText: { color: '#fff', textAlign: 'center', fontWeight: '600' },
  errorText: {
    color: '#fff',
    textAlign: 'center',
    fontWeight: '700',
    backgroundColor: 'rgba(186,26,26,0.88)',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  torchButton: {
    minHeight: 48,
    minWidth: 120,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 24,
    backgroundColor: 'rgba(0,0,0,0.62)',
    paddingHorizontal: 18,
  },
});
