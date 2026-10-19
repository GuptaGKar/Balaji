import { z } from "zod";

export const quantityTypeEnum = z.enum(["weighted", "packed", "WEIGHTED", "PACKED"]);
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

export const storeSettingsSchema = z.object({
  store_open: z.boolean().default(true),
  timezone: z.string().default("Asia/Kolkata"),
  weekly_hours: z.any().optional(),
  contact_phone: z.string().optional().nullable(),
  estimated_window_text: z.string().optional().nullable(),
  delivery_fee_paise: z.number().optional().nullable(),
  delivery_charge_threshold_paise: z.number().optional().nullable(),
  free_delivery_threshold_paise: z.number().optional().nullable(),
  mid_tier_fee_paise: z.number().optional().nullable(),
  max_order_paise: z.number().optional().nullable(),
  cancellation_policy_text: z.string().optional().nullable(),
  tax_mode: taxModeEnum.optional().default("none"),
  tax_rate_bps: z.number().optional().default(0),
  online_payment_enabled: z.boolean().optional().default(false),
  pos_price_override_enabled: z.boolean().optional().default(false),
});

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
    sku: z.string().optional(),
    name: z.string().min(1, "Name is required").max(200),
    description: z.string().optional().nullable(),
    category_id: z.string().min(1, "Category is required"),
    image_path: z.string().optional().nullable(),
    quantity_type: quantityTypeEnum,
    price_paise: z.number().int().min(0),
    price_basis_qty: z.number().int().positive(),
    unit_label: z.string().min(1).max(20),
    pack_size_label: z.string().max(50).optional().nullable(),
    step_qty: z.number().int().positive(),
    min_qty: z.number().int().positive(),
    max_qty: z.number().int().positive().optional().nullable(),
    stock_qty: z.number().int().min(0),
    low_stock_threshold: z.number().int().min(0),
    active: z.boolean().default(true),
    available: z.boolean().default(true),
  })
  .refine(
    (data) => {
      const qType = data.quantity_type.toUpperCase();
      return qType === "PACKED"
        ? data.price_basis_qty === 1 && data.step_qty === 1
        : data.price_basis_qty === 100 || data.price_basis_qty === 1000;
    },
    {
      message:
        "PACKED: price_basis_qty must be 1 and step_qty must be 1. WEIGHTED: price_basis_qty must be 100 or 1000.",
      path: ["quantity_type"],
    },
  )
  .refine((data) => data.max_qty === null || data.max_qty === undefined || data.max_qty >= data.min_qty, {
    message: "max_qty must be ≥ min_qty",
    path: ["max_qty"],
  });

export type Product = z.infer<typeof productSchema>;

export const loginSchema = z.object({
  email: z.string().email("Invalid email address"),
  password: z.string().min(6, "Password must be at least 6 characters"),
});

export type LoginInput = z.infer<typeof loginSchema>;

export const serviceAreaSchema = z.object({
  kind: serviceAreaKindEnum,
  value: z.string().min(1, "Value is required"),
  active: z.boolean().default(true),
});
export type ServiceArea = z.infer<typeof serviceAreaSchema>;

export const createStaffSchema = z.object({
  display_name: z.string().min(1, "Name is required"),
  phone: z.string().min(10, "Valid phone required"),
  role: z.enum(["staff", "manager", "delivery"]),
  login_id: z.string().min(3, "Login ID required"),
});
export type CreateStaffInput = z.infer<typeof createStaffSchema>;
