import { z } from "zod";

export const quantityTypeEnum = z.enum(["WEIGHTED", "PACKED"]);
export type QuantityType = z.infer<typeof quantityTypeEnum>;

export const userRoleEnum = z.enum(["customer", "staff", "manager", "delivery"]);
export type UserRole = z.infer<typeof userRoleEnum>;

export const orderStatusEnum = z.enum([
  "pending_review",
  "accepted",
  "preparing",
  "ready_for_dispatch",
  "out_for_delivery",
  "delivered",
  "rejected",
  "cancelled",
  "delivery_failed",
]);
export type OrderStatus = z.infer<typeof orderStatusEnum>;

export const paymentMethodEnum = z.enum(["cod", "online"]);
export type PaymentMethod = z.infer<typeof paymentMethodEnum>;

export const paymentStatusEnum = z.enum([
  "unpaid",
  "pending",
  "paid",
  "failed",
  "refund_pending",
  "partially_refunded",
  "refunded",
]);
export type PaymentStatus = z.infer<typeof paymentStatusEnum>;

export const serviceAreaKindEnum = z.enum(["pincode", "neighbourhood"]);
export type ServiceAreaKind = z.infer<typeof serviceAreaKindEnum>;

export const billSourceEnum = z.enum(["ORDER", "POS"]);
export type BillSource = z.infer<typeof billSourceEnum>;

export const billStatusEnum = z.enum(["ISSUED", "VOID"]);
export type BillStatus = z.infer<typeof billStatusEnum>;

export const inventoryReasonEnum = z.enum([
  "order_placed",
  "order_cancelled",
  "order_rejected",
  "item_removed",
  "pos_sale",
  "pos_void",
  "manual_adjustment",
  "correction",
]);
export type InventoryReason = z.infer<typeof inventoryReasonEnum>;

export const taxModeEnum = z.enum(["none", "inclusive", "exclusive"]);
export type TaxMode = z.infer<typeof taxModeEnum>;

export const storeSettingsSchema = z
  .object({
    store_open: z.boolean(),
    timezone: z.string(),
    weekly_hours: z.record(z.array(z.string())),
    contact_phone: z.string().min(10).max(15),
    estimated_window_text: z.string().min(1),
    delivery_fee_paise: z.number().int().min(0),
    delivery_charge_threshold_paise: z.number().int().min(0),
    free_delivery_threshold_paise: z.number().int().min(0).nullable(),
    mid_tier_fee_paise: z.number().int().min(0).nullable(),
    max_order_paise: z.number().int().min(0).nullable(),
    cancellation_policy_text: z.string().min(1),
    tax_mode: taxModeEnum.default("none"),
    tax_rate_bps: z.number().int().min(0).max(10000).default(0),
    online_payment_enabled: z.boolean().default(false),
    pos_price_override_enabled: z.boolean().default(false),
  })
  .refine(
    (data) =>
      data.free_delivery_threshold_paise === null ||
      data.free_delivery_threshold_paise >= data.delivery_charge_threshold_paise,
    {
      message: "Free-delivery threshold must be ≥ delivery-charge threshold",
      path: ["free_delivery_threshold_paise"],
    },
  );

export type StoreSettings = z.infer<typeof storeSettingsSchema>;

export const categorySchema = z.object({
  name: z.string().min(1).max(100),
  slug: z
    .string()
    .min(1)
    .max(50)
    .regex(/^[a-z0-9-]+$/),
  sort_order: z.number().int().min(0),
  active: z.boolean().default(true),
});

export type Category = z.infer<typeof categorySchema>;

export const categoryWithIdSchema = categorySchema.extend({
  id: z.string().uuid(),
});

export type CategoryWithId = z.infer<typeof categoryWithIdSchema>;

export const productSchema = z
  .object({
    sku: z.string().min(1).max(50),
    name: z.string().min(1).max(200),
    description: z.string().optional(),
    category_id: z.string().uuid(),
    image_path: z.string().optional(),
    quantity_type: quantityTypeEnum,
    price_paise: z.number().int().min(0),
    price_basis_qty: z.number().int().positive(),
    unit_label: z.string().min(1).max(20),
    pack_size_label: z.string().max(50).nullable(),
    step_qty: z.number().int().positive(),
    min_qty: z.number().int().positive(),
    max_qty: z.number().int().positive().nullable(),
    stock_qty: z.number().int().min(0),
    low_stock_threshold: z.number().int().min(0),
    active: z.boolean().default(true),
    available: z.boolean().default(true),
  })
  .refine(
    (data) =>
      data.quantity_type === "PACKED"
        ? data.price_basis_qty === 1 && data.step_qty === 1
        : data.price_basis_qty === 100 || data.price_basis_qty === 1000,
    {
      message:
        "PACKED: price_basis_qty must be 1 and step_qty must be 1. WEIGHTED: price_basis_qty must be 100 or 1000.",
      path: ["quantity_type"],
    },
  )
  .refine((data) => data.max_qty === null || data.max_qty >= data.min_qty, {
    message: "max_qty must be ≥ min_qty",
    path: ["max_qty"],
  });

export type Product = z.infer<typeof productSchema>;

export const serviceAreaSchema = z.object({
  kind: serviceAreaKindEnum,
  value: z.string().min(1).max(50),
  active: z.boolean().default(true),
});

export type ServiceArea = z.infer<typeof serviceAreaSchema>;

export const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
});

export type LoginInput = z.infer<typeof loginSchema>;

export const createStaffSchema = z.object({
  login_id: z
    .string()
    .min(3)
    .max(20)
    .regex(/^[A-Z0-9-]+$/),
  display_name: z.string().min(1).max(100),
  phone: z.string().min(10).max(15),
  role: z.enum(["staff", "delivery"]),
});

export type CreateStaffInput = z.infer<typeof createStaffSchema>;
