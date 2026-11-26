
package com.bondhubazar.order.bondhuorder;

import com.google.appinventor.components.annotations.Extension;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

@Extension(
    description = "Bondhu Bazar Order Management Extension"
)
public class BondhuOrder extends AndroidNonvisibleComponent {

    // সব Order এখানে রাখা হবে
    private final LinkedHashMap<String, JSONObject> orders =
        new LinkedHashMap<String, JSONObject>();

    public BondhuOrder(ComponentContainer container) {
        super(container.$form());
    }

    // =========================================
    // ১. ইউনিক Order ID তৈরি
    // =========================================

    @SimpleFunction(
        description = "নতুন ইউনিক Order ID তৈরি করবে"
    )
    public String GenerateOrderID() {

        String randomId = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10)
            .toUpperCase(Locale.US);

        return "BB-" + randomId;
    }

    // =========================================
    // ২. Order তৈরির সময়
    // =========================================

    private String getCurrentDate() {

        SimpleDateFormat format =
            new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.US
            );

        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        return format.format(new Date());
    }

    // =========================================
    // ৩. নতুন Order তৈরি
    // =========================================

    @SimpleFunction(
        description = "Checkout JSON এবং Cart Items থেকে Order তৈরি করবে"
    )
    public String CreateOrder(
        String userId,
        String checkoutJson,
        String itemsJson
    ) {

        try {

            // Checkout-এর তথ্য পড়া
            JSONObject checkout =
                new JSONObject(checkoutJson);

            // Cart-এর পণ্যগুলির JSON Array
            JSONArray items =
                new JSONArray(itemsJson);

            // প্রয়োজনীয় তথ্য যাচাই
            String addressId =
                checkout.optString("addressId", "");

            String address =
                checkout.optString("address", "");

            String phone =
                checkout.optString("phone", "");

            long total =
                checkout.optLong("grandTotalPaise", 0);

            if (userId == null || userId.trim().isEmpty()) {
                OrderError("User ID পাওয়া যায়নি");
                return "";
            }

            if (addressId.trim().isEmpty() ||
                address.trim().isEmpty()) {

                OrderError("Delivery Address পাওয়া যায়নি");
                return "";
            }

            if (items.length() == 0) {
                OrderError("Cart Items পাওয়া যায়নি");
                return "";
            }

            if (total <= 0) {
                OrderError("Order Total সঠিক নয়");
                return "";
            }

            // নতুন Order ID
            String orderId = GenerateOrderID();

            // Order-এর সম্পূর্ণ তথ্য
            JSONObject order = new JSONObject();

            order.put("id", orderId);
            order.put("user_id", userId);

            order.put("address_id", addressId);
            order.put("delivery_address", address);
            order.put("phone", phone);

            // Cart Items
            order.put("items", items);

            // টাকার হিসাব পয়সায়
            order.put(
                "item_count",
                checkout.optInt("itemCount", items.length())
            );

            order.put(
                "subtotal_paise",
                checkout.optLong("subtotalPaise", 0)
            );

            order.put(
                "delivery_fee_paise",
                checkout.optLong("deliveryFeePaise", 0)
            );

            order.put(
                "discount_paise",
                checkout.optLong("discountPaise", 0)
            );

            order.put("grand_total_paise", total);

            order.put("currency", "INR");

            // নতুন Order-এর Status
            order.put("status", "Pending");

            // Order তৈরির সময়
            order.put("created_at", getCurrentDate());

            // প্রথমে মেমোরিতে রাখা
            orders.put(orderId, order);

            // Order তৈরি হওয়ার Event
            OrderCreated(
                orderId,
                order.toString()
            );

            return orderId;

        } catch (Exception e) {

            OrderError(
                "Order তৈরি করা যায়নি: " + e.getMessage()
            );

            return "";
        }
    }

    // =========================================
    // ৪. নির্দিষ্ট Order-এর সম্পূর্ণ তথ্য
    // =========================================

    @SimpleFunction(
        description = "Order ID দিয়ে Order-এর JSON তথ্য দেবে"
    )
    public String GetOrderDetails(String orderId) {

        JSONObject order = orders.get(orderId);

        if (order == null) {
            return "{}";
        }

        return order.toString();
    }

    // =========================================
    // ৫. Order Status দেখা
    // =========================================

    @SimpleFunction(
        description = "Order-এর বর্তমান Status দেবে"
    )
    public String GetOrderStatus(String orderId) {

        JSONObject order = orders.get(orderId);

        if (order == null) {
            return "Not Found";
        }

        return order.optString("status", "Pending");
    }

    // =========================================
    // ৬. Status সঠিক কি না যাচাই
    // =========================================

    private boolean isValidStatus(String status) {

        return status.equals("Pending") ||
               status.equals("Confirmed") ||
               status.equals("Processing") ||
               status.equals("OutForDelivery") ||
               status.equals("Delivered") ||
               status.equals("Cancelled");
    }

    // =========================================
    // ৭. Order Status পরিবর্তন
    // =========================================

    @SimpleFunction(
        description = "Order-এর Status পরিবর্তন করবে"
    )
    public boolean UpdateOrderStatus(
        String orderId,
        String newStatus
    ) {

        JSONObject order = orders.get(orderId);

        if (order == null) {
            OrderError("Order পাওয়া যায়নি");
            return false;
        }

        if (newStatus == null ||
            !isValidStatus(newStatus)) {

            OrderError("সঠিক Order Status দাও");
            return false;
        }

        String oldStatus =
            order.optString("status", "Pending");

        // Delivered বা Cancelled Order আর পরিবর্তন হবে না
        if (oldStatus.equals("Delivered") ||
            oldStatus.equals("Cancelled")) {

            OrderError("এই Order-এর Status আর পরিবর্তন করা যাবে না");
            return false;
        }

        try {

            order.put("status", newStatus);

            // পরিবর্তনের সময়
            order.put("updated_at", getCurrentDate());

            OrderStatusUpdated(
                orderId,
                oldStatus,
                newStatus
            );

            return true;

        } catch (Exception e) {

            OrderError("Status Update করা যায়নি");
            return false;
        }
    }

    // =========================================
    // ৮. Order Cancel
    // =========================================

    @SimpleFunction(
        description = "Order Cancel করবে"
    )
    public boolean CancelOrder(String orderId) {

        JSONObject order = orders.get(orderId);

        if (order == null) {
            OrderError("Order পাওয়া যায়নি");
            return false;
        }

        String status =
            order.optString("status", "");

        if (status.equals("Delivered") ||
            status.equals("Cancelled")) {

            OrderError("এই Order Cancel করা যাবে না");
            return false;
        }

        return UpdateOrderStatus(
            orderId,
            "Cancelled"
        );
    }

    // =========================================
    // ৯. সব Order History পাওয়া
    // =========================================

    @SimpleFunction(
        description = "সব Order-এর JSON History দেবে"
    )
    public String GetOrderHistory() {

        JSONArray history = new JSONArray();

        for (Map.Entry<String, JSONObject> entry :
             orders.entrySet()) {

            history.put(entry.getValue());
        }

        return history.toString();
    }

    // =========================================
    // ১০. মোট Order সংখ্যা
    // =========================================

    @SimpleFunction(
        description = "মোট Order সংখ্যা দেবে"
    )
    public int GetOrderCount() {

        return orders.size();
    }

    // =========================================
    // ১১. History থেকে Order লোড
    // =========================================

    @SimpleFunction(
        description = "JSON Array থেকে আগের Order History লোড করবে"
    )
    public boolean LoadOrderHistory(String historyJson) {

        try {

            JSONArray history =
                new JSONArray(historyJson);

            // আগের মেমোরি পরিষ্কার
            orders.clear();

            for (int i = 0; i < history.length(); i++) {

                JSONObject order =
                    history.getJSONObject(i);

                String orderId =
                    order.optString("id", "");

                if (!orderId.isEmpty()) {

                    // আগের Order আবার মেমোরিতে রাখা
                    orders.put(orderId, order);
                }
            }

            OrderHistoryLoaded(orders.size());

            return true;

        } catch (Exception e) {

            OrderError("Order History লোড করা যায়নি");
            return false;
        }
    }

    // =========================================
    // ১২. সব Order মেমোরি থেকে পরিষ্কার
    // =========================================

    @SimpleFunction(
        description = "মেমোরিতে থাকা সব Order পরিষ্কার করবে"
    )
    public void ClearOrderHistory() {

        orders.clear();

        OrderHistoryCleared();
    }

    // =========================================
    // EVENTS
    // =========================================

    @SimpleEvent(
        description = "নতুন Order তৈরি হলে চলবে"
    )
    public void OrderCreated(
        String orderId,
        String orderJson
    ) {

        EventDispatcher.dispatchEvent(
            this,
            "OrderCreated",
            orderId,
            orderJson
        );
    }

    @SimpleEvent(
        description = "Order Status পরিবর্তন হলে চলবে"
    )
    public void OrderStatusUpdated(
        String orderId,
        String oldStatus,
        String newStatus
    ) {

        EventDispatcher.dispatchEvent(
            this,
            "OrderStatusUpdated",
            orderId,
            oldStatus,
            newStatus
        );
    }

    @SimpleEvent(
        description = "Order History লোড হলে চলবে"
    )
    public void OrderHistoryLoaded(int count) {

        EventDispatcher.dispatchEvent(
            this,
            "OrderHistoryLoaded",
            count
        );
    }

    @SimpleEvent(
        description = "Order History পরিষ্কার হলে চলবে"
    )
    public void OrderHistoryCleared() {

        EventDispatcher.dispatchEvent(
            this,
            "OrderHistoryCleared"
        );
    }

    @SimpleEvent(
        description = "কোনো সমস্যা হলে চলবে"
    )
    public void OrderError(String message) {

        EventDispatcher.dispatchEvent(
            this,
            "OrderError",
            message
        );
    }
}
