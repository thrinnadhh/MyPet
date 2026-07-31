export type OrderStatus =
  | 'ORDER_PLACED'
  | 'CONFIRMED'
  | 'PREPARING'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED';

export type OrderTab = 'active' | 'past' | 'subscription';

export interface OrderItem {
  id: string;
  name: string;
  quantity: number;
  price: number;
  imageUrl: string;
  variant?: string;
}

export interface CaptainInfo {
  name: string;
  phone: string;
  vehicleNumber: string;
  rating: number;
  currentLat?: number;
  currentLng?: number;
}

export interface Order {
  id: string;
  orderNumber: string;
  providerId: string;
  providerName: string;
  providerLogoUrl: string;
  status: OrderStatus;
  statusText: string;
  tab: OrderTab;
  placedAt: string;
  estimatedDelivery: string;
  totalAmount: number;
  deliveryFee: number;
  discount: number;
  tax: number;
  paymentMethod: string;
  items: OrderItem[];
  deliveryAddress: {
    name: string;
    street: string;
    city: string;
    pincode: string;
  };
  captain?: CaptainInfo;
  canCancel: boolean;
  canReorder: boolean;
  invoiceUrl?: string;
}

export const TRACKING_STEPS: { key: OrderStatus; title: string; subtitle: string }[] = [
  { key: 'ORDER_PLACED', title: 'Order Placed', subtitle: 'Store notified of your order' },
  { key: 'CONFIRMED', title: 'Order Confirmed', subtitle: 'Merchant accepted & packing' },
  { key: 'PREPARING', title: 'Preparing Items', subtitle: 'Quality check & gift packaging' },
  { key: 'OUT_FOR_DELIVERY', title: 'Out for Delivery', subtitle: 'Captain on the way to your door' },
  { key: 'DELIVERED', title: 'Delivered', subtitle: 'Received safely' },
];

export const MOCK_ORDERS: Order[] = [
  {
    id: 'ord-8821',
    orderNumber: '#ORD-8821',
    providerId: 'the-posh-paws',
    providerName: 'The Posh Paws Boutique',
    providerLogoUrl: 'https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=150',
    status: 'OUT_FOR_DELIVERY',
    statusText: 'Arriving in 15 mins',
    tab: 'active',
    placedAt: 'Today, 2:45 PM',
    estimatedDelivery: 'Today, 3:30 PM',
    totalAmount: 1649,
    deliveryFee: 49,
    discount: 100,
    tax: 80,
    paymentMethod: 'UPI (Razorpay)',
    items: [
      {
        id: 'item-1',
        name: 'Royal Canin Medium Adult Dry Dog Food (3kg)',
        quantity: 1,
        price: 1450,
        imageUrl: 'https://images.unsplash.com/photo-1589924691995-40055ed18a29?w=300',
        variant: '3 kg Pack',
      },
      {
        id: 'item-2',
        name: 'Pedigree Dentastix Medium Dog Chews',
        quantity: 1,
        price: 249,
        imageUrl: 'https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=300',
        variant: 'Pack of 7',
      },
    ],
    deliveryAddress: {
      name: 'Trinadh Kumar',
      street: 'Flat 402, Royal Palms Heights, KT Road',
      city: 'Tirupati',
      pincode: '517501',
    },
    captain: {
      name: 'Ramesh V.',
      phone: '+91 98765 43210',
      vehicleNumber: 'AP 39 BK 4821',
      rating: 4.9,
    },
    canCancel: false,
    canReorder: true,
    invoiceUrl: 'https://mypet.app/invoices/ord-8821.pdf',
  },
  {
    id: 'ord-9412',
    orderNumber: '#ORD-9412',
    providerId: 'the-healthy-hound',
    providerName: 'The Healthy Hound Nutrition Hub',
    providerLogoUrl: 'https://images.unsplash.com/photo-1583511655857-d19b40a7a54e?w=150',
    status: 'PREPARING',
    statusText: 'Packing fresh treats',
    tab: 'active',
    placedAt: 'Today, 3:10 PM',
    estimatedDelivery: 'Today, 4:15 PM',
    totalAmount: 890,
    deliveryFee: 0,
    discount: 50,
    tax: 40,
    paymentMethod: 'Cash on Delivery (COD)',
    items: [
      {
        id: 'item-3',
        name: 'Drools Real Chicken & Egg Adult Dog Food',
        quantity: 1,
        price: 900,
        imageUrl: 'https://images.unsplash.com/photo-1568640347023-a616a30bc3bd?w=300',
        variant: '1.2 kg',
      },
    ],
    deliveryAddress: {
      name: 'Trinadh Kumar',
      street: 'Flat 402, Royal Palms Heights, KT Road',
      city: 'Tirupati',
      pincode: '517501',
    },
    canCancel: true,
    canReorder: true,
  },
  {
    id: 'ord-7301',
    orderNumber: '#ORD-7301',
    providerId: 'petcare-pharmacy',
    providerName: 'PetCare Pharmacy & Supplies',
    providerLogoUrl: 'https://images.unsplash.com/photo-1576201836106-db1758fd1c97?w=150',
    status: 'DELIVERED',
    statusText: 'Delivered yesterday',
    tab: 'past',
    placedAt: 'Yesterday, 11:20 AM',
    estimatedDelivery: 'Yesterday, 12:05 PM',
    totalAmount: 1250,
    deliveryFee: 30,
    discount: 0,
    tax: 60,
    paymentMethod: 'Credit Card',
    items: [
      {
        id: 'item-4',
        name: 'Simparica Trio Deworming & Tick Tablets',
        quantity: 1,
        price: 1160,
        imageUrl: 'https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?w=300',
        variant: '10-20 kg (3 Chews)',
      },
    ],
    deliveryAddress: {
      name: 'Trinadh Kumar',
      street: 'Flat 402, Royal Palms Heights, KT Road',
      city: 'Tirupati',
      pincode: '517501',
    },
    canCancel: false,
    canReorder: true,
    invoiceUrl: 'https://mypet.app/invoices/ord-7301.pdf',
  },
  {
    id: 'ord-sub-101',
    orderNumber: '#SUB-101',
    providerId: 'the-healthy-hound',
    providerName: 'The Healthy Hound Nutrition Hub',
    providerLogoUrl: 'https://images.unsplash.com/photo-1583511655857-d19b40a7a54e?w=150',
    status: 'CONFIRMED',
    statusText: 'Monthly Auto-Refill (Next: Aug 15)',
    tab: 'subscription',
    placedAt: 'Recurring Monthly',
    estimatedDelivery: 'Every 15th of month',
    totalAmount: 2200,
    deliveryFee: 0,
    discount: 250,
    tax: 100,
    paymentMethod: 'Razorpay Auto-Debit',
    items: [
      {
        id: 'item-5',
        name: 'Farmina N&D Ancestral Grain Chicken & Pomegranate',
        quantity: 1,
        price: 2350,
        imageUrl: 'https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=300',
        variant: '2.5 kg',
      },
    ],
    deliveryAddress: {
      name: 'Trinadh Kumar',
      street: 'Flat 402, Royal Palms Heights, KT Road',
      city: 'Tirupati',
      pincode: '517501',
    },
    canCancel: true,
    canReorder: true,
  },
];

export async function fetchOrdersData(tab: OrderTab = 'active', searchQuery: string = ''): Promise<Order[]> {
  await new Promise((resolve) => setTimeout(resolve, 150));
  return MOCK_ORDERS.filter((order) => {
    const matchesTab = order.tab === tab;
    const matchesQuery =
      !searchQuery ||
      order.orderNumber.toLowerCase().includes(searchQuery.toLowerCase()) ||
      order.providerName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      order.items.some((i) => i.name.toLowerCase().includes(searchQuery.toLowerCase()));
    return matchesTab && matchesQuery;
  });
}

export async function fetchOrderByIdData(orderId: string): Promise<Order | null> {
  await new Promise((resolve) => setTimeout(resolve, 100));
  return MOCK_ORDERS.find((o) => o.id === orderId || o.orderNumber.toLowerCase() === orderId.toLowerCase()) || null;
}
