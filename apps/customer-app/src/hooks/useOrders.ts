import { useCallback, useEffect, useState } from 'react';
import { fetchOrdersData, type Order, type OrderTab } from '@/services/orders-data';

export function useOrders(initialTab: OrderTab = 'active') {
  const [tab, setTab] = useState<OrderTab>(initialTab);
  const [searchQuery, setSearchQuery] = useState('');
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchOrdersData(tab, searchQuery);
      setOrders(data);
    } finally {
      setLoading(false);
    }
  }, [tab, searchQuery]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  const onRefresh = async () => {
    setRefreshing(true);
    try {
      const data = await fetchOrdersData(tab, searchQuery);
      setOrders(data);
    } finally {
      setRefreshing(false);
    }
  };

  const cancelOrder = (orderId: string) => {
    setOrders((prev) =>
      prev.map((o) => (o.id === orderId ? { ...o, status: 'CANCELLED', statusText: 'Cancelled by customer', canCancel: false } : o))
    );
  };

  return {
    tab,
    setTab,
    searchQuery,
    setSearchQuery,
    orders,
    loading,
    refreshing,
    onRefresh,
    cancelOrder,
  };
}
