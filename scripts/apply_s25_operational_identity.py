from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if old not in text:
        raise RuntimeError(f"Expected block not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


inventory = Path("apps/merchant-captain-app/src/app/inventory.tsx")

replace_once(
    inventory,
    "import { appConfig } from '@/utils/app-config';\n",
    "import { apiClient } from '@/services/api-client';\nimport { appConfig } from '@/utils/app-config';\n",
)

replace_once(
    inventory,
    """  const { user, providerId } = useAuth();
  const activeBusinessId = providerId || user?.id || 'd3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e';
  
  // Provider / business context — derived dynamically from session context
  const [providers, setProviders] = useState([
    {
      id: activeBusinessId,
      label: '🏬 My Business',
      fulfillmentType: 'DELIVERY' as const,
    }
  ]);
  const [selectedProvider, setSelectedProvider] = useState(providers[0]);
  const selectedProviderId = selectedProvider?.id || activeBusinessId;
  const selectedProviderFulfillment = selectedProvider?.fulfillmentType || 'DELIVERY';

  const fetchProviders = useCallback(async () => {
    if (!user) return;
    try {
      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/providers?ownerUserId=${user.id}`);
      if (response.ok) {
        const data = await response.json();
        if (Array.isArray(data) && data.length > 0) {
          const mapped = data.map((p: any) => ({
            id: p.providerId,
            label: p.providerType === 'PET_STORE' ? `🏬 ${p.name}` : p.providerType === 'VET_HOSPITAL' ? `🏥 ${p.name}` : `✂️ ${p.name}`,
            fulfillmentType: p.fulfillmentType || 'DELIVERY',
          }));
          setProviders(mapped);
          setSelectedProvider(mapped[0]);
        }
      }
    } catch (err) {
      console.log("Failed to fetch dynamic providers list:", err);
    }
  }, [user]);

  useEffect(() => {
    fetchProviders();
  }, [fetchProviders]);
""",
    """  const { providerId } = useAuth();

  type ProviderOption = {
    id: string;
    label: string;
    fulfillmentType: 'DELIVERY' | 'APPOINTMENT';
  };

  const [providers, setProviders] = useState<ProviderOption[]>([]);
  const [selectedProvider, setSelectedProvider] = useState<ProviderOption | null>(null);
  const selectedProviderId = selectedProvider?.id ?? providerId;
  const selectedProviderFulfillment = selectedProvider?.fulfillmentType ?? 'DELIVERY';

  const fetchProviders = useCallback(async () => {
    if (!providerId) {
      setProviders([]);
      setSelectedProvider(null);
      return;
    }
    try {
      const data = await apiClient.get<any[]>('/api/v1/providers/me');
      const mapped: ProviderOption[] = data.map((provider) => ({
        id: provider.providerId,
        label:
          provider.providerType === 'PET_STORE'
            ? `🏬 ${provider.name}`
            : provider.providerType === 'VET_HOSPITAL'
              ? `🏥 ${provider.name}`
              : `✂️ ${provider.name}`,
        fulfillmentType: provider.fulfillmentType || 'DELIVERY',
      }));
      setProviders(mapped);
      setSelectedProvider(
        mapped.find((provider) => provider.id === providerId) ?? mapped[0] ?? null,
      );
    } catch (error) {
      console.warn('Failed to resolve provider businesses', error);
      setProviders([]);
      setSelectedProvider(null);
    }
  }, [providerId]);

  useEffect(() => {
    void fetchProviders();
  }, [fetchProviders]);
""",
)

replace_once(
    inventory,
    """  const fetchCatalog = useCallback(async () => {
    setLoading(true);
    setIsOffline(false);
    try {
      const response = await fetch(
        `${appConfig.apiBaseUrl}/api/v1/catalog/offerings?providerId=${selectedProviderId}`,
        { headers: { 'Accept': 'application/json' } }
      );
      if (!response.ok) throw new Error('API request failed');
      const data = await response.json();
      setOfferings(data);
    } catch (err) {
      console.warn('Catalog API unreachable.');
      setIsOffline(appConfig.allowDemoMode);
      setOfferings(appConfig.allowDemoMode ? OFFLINE_MOCK_OFFERINGS[selectedProviderId] || [] : []);
    } finally {
      setLoading(false);
    }
  }, [selectedProviderId]);
""",
    """  const fetchCatalog = useCallback(async () => {
    setLoading(true);
    setIsOffline(false);
    if (!selectedProviderId) {
      setOfferings([]);
      setLoading(false);
      return;
    }
    try {
      const data = await apiClient.get<Offering[]>(
        `/api/v1/catalog/offerings?providerId=${encodeURIComponent(selectedProviderId)}`,
      );
      setOfferings(data);
    } catch (error) {
      console.warn('Catalog API unreachable.', error);
      setIsOffline(appConfig.allowDemoMode);
      setOfferings(
        appConfig.allowDemoMode ? OFFLINE_MOCK_OFFERINGS[selectedProviderId] || [] : [],
      );
    } finally {
      setLoading(false);
    }
  }, [selectedProviderId]);
""",
)

replace_once(
    inventory,
    """  const handleAddOffering = useCallback(async () => {
    if (!formName.trim() || !formPrice.trim()) {
""",
    """  const handleAddOffering = useCallback(async () => {
    if (!selectedProviderId) {
      Alert.alert('Provider Required', 'Complete provider onboarding before managing inventory.');
      return;
    }
    if (!formName.trim() || !formPrice.trim()) {
""",
)

replace_once(
    inventory,
    """        const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/catalog/offerings`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });

        if (response.ok) {
          Alert.alert('Success', 'Offering created successfully!');
          fetchCatalog();
          setShowAddForm(false);
          resetForm();
        } else {
          const errData = await response.json();
          Alert.alert('Backend Error', errData.error || 'Failed to create offering.');
        }
""",
    """        await apiClient.post('/api/v1/catalog/offerings', payload);
        Alert.alert('Success', 'Offering created successfully!');
        void fetchCatalog();
        setShowAddForm(false);
        resetForm();
""",
)

replace_once(
    inventory,
    """        const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/catalog/offerings/${item.offeringId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ...item, status: newStatus }),
        });
        if (response.ok) {
          fetchCatalog();
        } else {
          Alert.alert('Error', 'Failed to update offering status.');
        }
""",
    """        await apiClient.put(
          `/api/v1/catalog/offerings/${item.offeringId}`,
          { ...item, status: newStatus },
        );
        void fetchCatalog();
""",
)

replace_once(
    inventory,
    """      const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/catalog/slots?offeringId=${offering.offeringId}`);
      if (!response.ok) throw new Error('API failure');
      const data = await response.json();
      setSlots(data);
""",
    """      const data = await apiClient.get<Slot[]>(
        `/api/v1/catalog/slots?offeringId=${encodeURIComponent(offering.offeringId || '')}`,
      );
      setSlots(data);
""",
)

replace_once(
    inventory,
    """        const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/catalog/slots`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });

        if (response.ok) {
          Alert.alert('Success', 'Time slot created.');
          setNewSlotStart('');
          setNewSlotEnd('');
          // reload slots
          openSlotsManager(selectedOffering);
        } else {
          const errData = await response.json();
          Alert.alert('Error', errData.error || 'Failed to create slot.');
        }
""",
    """        await apiClient.post('/api/v1/catalog/slots', payload);
        Alert.alert('Success', 'Time slot created.');
        setNewSlotStart('');
        setNewSlotEnd('');
        void openSlotsManager(selectedOffering);
""",
)

replace_once(
    inventory,
    """        const response = await fetch(
          `${appConfig.apiBaseUrl}/api/v1/catalog/slots/${slotId}/status?status=${nextStatus}`,
          { method: 'PUT' }
        );
        if (response.ok && selectedOffering) {
          openSlotsManager(selectedOffering);
        } else {
          Alert.alert('Error', 'Failed to update slot status.');
        }
""",
    """        await apiClient.put(
          `/api/v1/catalog/slots/${slotId}/status?status=${nextStatus}`,
        );
        if (selectedOffering) {
          void openSlotsManager(selectedOffering);
        }
""",
)

print("Sprint 25 operational identity codemod applied")
