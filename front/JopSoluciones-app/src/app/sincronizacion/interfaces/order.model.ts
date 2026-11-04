export interface OrderBilling {
  first_name: string;
  last_name: string;
  email: string;
  phone: string;
  address_1: string;
  city: string;
}

export interface OrderLineItem {
  id: number;
  name: string;
  sku: string;
  quantity: number;
  total: string;
}

export interface Order {
  id: number;
  number: string;
  status: string;
  date_created: string;
  date_modified: string;
  total: string;
  currency: string;
  customer_note: string;
  billing: OrderBilling;
  line_items: OrderLineItem[];
}
